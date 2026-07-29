import fcntl
import logging
import os
import re
import time
import serial
import serial.tools.list_ports

from mobly.controllers.android_device_lib import adb

# --- Constants ---
# PN532 ACK command (clear cache)
PN532_ACK = bytes([0x00, 0x00, 0xFF, 0x00, 0xFF, 0x00])

# PN532 Poll command (InListPassiveTarget) used to activate the RF field
# Frame: PREAMBLE + LEN + LCS + TFI + CMD + ...
PN532_POLL_COMMAND = bytes(
    [0x00, 0x00, 0xFF, 0x04, 0xFC, 0xD4, 0x4A, 0x01, 0x00, 0xE1, 0x00]
)

PN532_BAUD_RATE = 115200

_PN532_ACK_WAIT_TIME_S = 0.1
_PN532_RF_FIELD_WAIT_TIME_S = 1.5
_PORT_DISCOVERY_COOLDOWN_S = 1.0

# Known PN532 USB-to-Serial Chip IDs (Vendor ID, Product ID)
_KNOWN_IDS = frozenset({(0x0403, 0x6001), (0x10C4, 0xEA60)})

# Logcat keywords indicating NFC activation
NFC_LOGCAT_PATTERN = re.compile(r"RF_FIELD_ACTIVATED|NFA_ACTIVATED_EVT")

_LOG = logging.getLogger("Pn532Utils")

def _discover_candidate_ports() -> list[str]:
    """Discovers all potential PN532 serial ports."""
    ports = serial.tools.list_ports.comports()
    candidates = []

    for port in ports:
        # Strategy 1: Check known VID/PID
        if (port.vid, port.pid) in _KNOWN_IDS:
            candidates.append(port.device)
            continue

        # Strategy 2: Check all USB/ACM ports (fallback for unknown chips)
        if "USB" in port.device or "ACM" in port.device:
            candidates.append(port.device)

    return sorted(list(set(candidates)))


def _activate_rf_field(serial_path: str):
    """Sends commands to the specified serial port to activate the RF field."""
    ser = None
    try:
        _LOG.debug("Attempting to wake up PN532 at %s...", serial_path)
        ser = serial.Serial(serial_path, PN532_BAUD_RATE, timeout=0.5, exclusive=True)
        ser.write(PN532_ACK)
        time.sleep(_PN532_ACK_WAIT_TIME_S)
        ser.read_all()
        _LOG.debug("Sending Poll command to %s...", serial_path)
        ser.write(PN532_POLL_COMMAND)
        time.sleep(_PN532_RF_FIELD_WAIT_TIME_S)

    except serial.SerialException as e:
        _LOG.warning("Could not talk to %s: %s", serial_path, e)
    finally:
        if ser and ser.is_open:
            ser.close()


def _check_logcat_for_nfc(ad) -> bool:
    """Checks device logs for NFC activation events."""
    try:
        output = ad.adb.shell(f"logcat -d | grep -E '{NFC_LOGCAT_PATTERN.pattern}'")
        if output:
            return True
    except Exception:
        pass
    return False


def discover_active_pair(android_devices: list) -> tuple[str, str]:
    """
    Finds a working pair of (PN532 Serial Path, Android Device Serial).

    Mechanism:
    1. Iterate through ALL candidate PN532 ports.
    2. For each port, activate the RF field.
    3. Check ALL connected Android devices to see which one detected the field.
    4. Returns the first matching pair found.

    Returns:
        tuple: (pn532_serial_path, android_device_serial)
    """
    candidates = _discover_candidate_ports()
    _LOG.info("Found candidate PN532 ports: %s", candidates)
    _LOG.info("Checking against %d Android devices: %s",
              len(android_devices), [d.serial for d in android_devices])

    if not candidates:
        raise Exception("No USB serial ports found on host!")

    for ad in android_devices:
        try:
            ad.adb.shell(["svc", "nfc", "enable"])
            ad.adb.shell(["logcat", "-c"])
        except Exception as e:
            _LOG.warning("Failed to prep device %s: %s", ad.serial, e)

    for port in candidates:
        _LOG.info("Activating RF field on port: %s...", port)

        for ad in android_devices:
            ad.adb.shell(["logcat", "-c"])

        _activate_rf_field(port)

        for ad in android_devices:
            if _check_logcat_for_nfc(ad):
                _LOG.info("✅ PAIR FOUND! PN532(%s) <==> Android(%s)", port, ad.serial)
                locked_ser = serial.Serial(port, PN532_BAUD_RATE, exclusive=True)
                locked_ser.reset_input_buffer()
                return locked_ser, port, ad.serial

        _LOG.info("No devices responded to port %s", port)
        # Cooldown prevents signal overlap
        time.sleep(_PORT_DISCOVERY_COOLDOWN_S)

    raise Exception("Discovery failed: No PN532 detected by any connected Android device.")
