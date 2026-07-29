#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import logging

class Type4Tag:
    """Emulates a Type 4 Tag (NFC-A or NFC-B) with NDEF support."""

    def __init__(self, ndef_message=None):
        self.log = logging.getLogger(__name__)
        # Default NDEF: URL https://google.com/
        # D1 (MB=1, ME=1, CF=0, SR=1, IL=0, TNF=1)
        # 01 (Type Length)
        # 0C (Payload Length)
        # 55 (Type: 'U' -> URI)
        # 04 (URI Identifier Code: https://www.)
        # 67 6F 6F 67 6C 65 2E 63 6F 6D 2F (google.com/)
        if ndef_message:
            self.ndef_message = ndef_message
        else:
            # "https://google.com/"
            self.ndef_message = bytearray.fromhex(
                "D1010C5504676F6F676C652E636F6D2F"
            )

        # Capability Container (CC) file
        # 00 0F (CCLEN)
        # 20 (Mapping Version 2.0)
        # 00 FF (MLe)
        # 00 FF (MLc)
        # 04 (TLV T=NDEF File Control)
        # 06 (TLV L)
        # E1 04 (File ID)
        # 00 14 (Max NDEF size) -> 0x0014 = 20 bytes? Need to be big enough.
        # 00 (Read access: allowed)
        # 00 (Write access: allowed)
        self.cc_file = bytearray.fromhex(
            "000F2000FF00FF0406E10400FF0000"
        )

        self.selected_file = None

        # APDU Constants
        self.INS_SELECT = 0xA4
        self.INS_READ_BINARY = 0xB0
        self.INS_UPDATE_BINARY = 0xD6

        # File IDs
        self.FID_NDEF_APP = bytearray.fromhex("D2760000850101")
        self.FID_CC = bytearray.fromhex("E103")
        self.FID_NDEF = bytearray.fromhex("E104")

    def process_apdu(self, apdu):
        """Processes an APDU and returns the response bytes."""
        if not apdu or len(apdu) < 4:
            self.log.warning("Invalid APDU length: %s", apdu.hex() if apdu else "None")
            return bytearray.fromhex("6F00") # Unknown error

        cla = apdu[0]
        ins = apdu[1]
        p1 = apdu[2]
        p2 = apdu[3]

        # SELECT
        if ins == self.INS_SELECT:
            if len(apdu) > 5:
                # Lc = apdu[4]
                data = apdu[5:]
                # Check for strip of Le if present at the end (but usually Select by name has Lc)
                # If command ends with 00 (Le), and data len == Lc + 1, strip it.
                # Simplified parsing:

                if self.FID_NDEF_APP in data:
                    self.log.info("Selected NDEF Application")
                    return bytearray.fromhex("9000")
                elif self.FID_CC in data:
                    self.log.info("Selected CC File")
                    self.selected_file = self.FID_CC
                    return bytearray.fromhex("9000")
                elif self.FID_NDEF in data:
                    self.log.info("Selected NDEF File")
                    self.selected_file = self.FID_NDEF
                    return bytearray.fromhex("9000")
            elif p1 == 0x00 and p2 == 0x0C and len(apdu) >= 7:
                 # Select by File ID (P1=00, P2=0C means First or only occurrence, File ID in data)
                 # Wait, typical Select File ID: CL INS P1 P2 Lc Data
                 # E.g. 00 A4 00 0C 02 E1 03
                 file_id = apdu[5:7]
                 if file_id == self.FID_CC:
                     self.log.info("Selected CC File (Direct)")
                     self.selected_file = self.FID_CC
                     return bytearray.fromhex("9000")
                 elif file_id == self.FID_NDEF:
                     self.log.info("Selected NDEF File (Direct)")
                     self.selected_file = self.FID_NDEF
                     return bytearray.fromhex("9000")

            self.log.warning("Unknown Select: %s", apdu.hex())
            return bytearray.fromhex("6A82") # File not found

        # READ BINARY
        if ins == self.INS_READ_BINARY:
            offset = (p1 << 8) | p2
            le = apdu[4] if len(apdu) > 4 else 0

            if self.selected_file == self.FID_CC:
                if offset >= len(self.cc_file):
                    return bytearray.fromhex("6B00") # Wrong parameters
                # If Le is 0, it might mean "all" or 256, but typically implementation specific.
                # Assuming typical usage request.
                length = le if le > 0 else len(self.cc_file) - offset
                data = self.cc_file[offset : offset + length]
                self.log.info("Reading CC File: offset=%d, len=%d", offset, len(data))
                return data + bytearray.fromhex("9000")

            elif self.selected_file == self.FID_NDEF:
                # NDEF File structure: [Len (2 bytes)] [NDEF Message]
                file_content = len(self.ndef_message).to_bytes(2, 'big') + self.ndef_message
                if offset >= len(file_content):
                    return bytearray.fromhex("6B00")

                length = le if le > 0 else len(file_content) - offset
                data = file_content[offset : offset + length]
                self.log.info("Reading NDEF File: offset=%d, len=%d", offset, len(data))
                return data + bytearray.fromhex("9000")

            self.log.warning("Read Binary on unknown file")
            return bytearray.fromhex("6982") # Security status not satisfied or file valid

        self.log.warning("Unknown Command: %s", apdu.hex())
        return bytearray.fromhex("6D00") # Instruction code not supported or invalid
