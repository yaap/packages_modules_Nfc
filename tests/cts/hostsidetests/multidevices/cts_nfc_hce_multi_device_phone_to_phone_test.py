#  Copyright (C) 2026 The Android Open Source Project
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

# Lint as: python3
"""CTS Tests that verify NFC HCE features.

These tests require two phones, one acting as
a card emulator and the other acting as an NFC reader. The devices should be
placed back to back.
"""

from http.client import HTTPSConnection
import json
import logging
import ssl
import sys
import time

from android.platform.test.annotations import CddTest
from android.platform.test.annotations import ApiTest
from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers import android_device_lib
from mobly.controllers.android_device_lib import adb
from mobly.snippet import errors


_LOG = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)

# Timeout to give the NFC service time to perform async actions such as
# discover tags.
_TRANSPORT_AID = "F001020304"
_GESTURE_EXCHANGE_AID = "A00000047609"
_NFC_TIMEOUT_SEC = 30
_NFC_TECH_A_POLLING_ON = (0x1 #NfcAdapter.FLAG_READER_NFC_A
                          | 0x10 #NfcAdapter.FLAG_READER_NFC_BARCODE
                          | 0x80 #NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                          )
_NFC_TECH_A_POLLING_OFF = (0x10 #NfcAdapter.FLAG_READER_NFC_BARCODE
                           | 0x80 #NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                           )
_NFC_TECH_A_LISTEN_ON = 0x1 #NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_A
_NFC_TECH_F_LISTEN_ON = 0x4 #NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_F
_NFC_LISTEN_OFF = 0x0 #NfcAdapter.FLAG_LISTEN_DISABLE
_SERVICE_PACKAGE = "com.android.nfc.service"
_ACCESS_SERVICE = _SERVICE_PACKAGE + ".AccessService"
_OFFHOST_SERVICE = _SERVICE_PACKAGE + ".OffHostService"
_LARGE_NUM_AIDS_SERVICE = _SERVICE_PACKAGE + ".LargeNumAidsService"
_PAYMENT_SERVICE_1 = _SERVICE_PACKAGE + ".PaymentService1"
_PAYMENT_SERVICE_2 = _SERVICE_PACKAGE + ".PaymentService2"
_PAYMENT_SERVICE_DYNAMIC_AIDS = _SERVICE_PACKAGE + ".PaymentServiceDynamicAids"
_PREFIX_ACCESS_SERVICE = _SERVICE_PACKAGE + ".PrefixAccessService"
_PREFIX_PAYMENT_SERVICE_1 = _SERVICE_PACKAGE + ".PrefixPaymentService1"
_PREFIX_TRANSPORT_SERVICE_2 = _SERVICE_PACKAGE + ".PrefixTransportService2"
_SCREEN_OFF_PAYMENT_SERVICE = _SERVICE_PACKAGE + ".ScreenOffPaymentService"
_SCREEN_ON_ONLY_OFF_HOST_SERVICE = _SERVICE_PACKAGE + ".ScreenOnOnlyOffHostService"
_THROUGHPUT_SERVICE = _SERVICE_PACKAGE + ".ThroughputService"
_TRANSPORT_SERVICE_1 = _SERVICE_PACKAGE + ".TransportService1"
_TRANSPORT_SERVICE_2 = _SERVICE_PACKAGE + ".TransportService2"
_POLLING_LOOP_SERVICE_1 = _SERVICE_PACKAGE + ".PollingLoopService"
_POLLING_LOOP_SERVICE_2 = _SERVICE_PACKAGE + ".PollingLoopService2"
_NDEF_SERVICE = _SERVICE_PACKAGE + ".NdefService"

_NUM_POLLING_LOOPS = 50
_FAILED_TAG_MSG =  "Reader did not detect tag, transaction not attempted."
_FAILED_TRANSACTION_MSG = "Transaction failed, check device logs for more information."

_FRAME_EVENT_TIMEOUT_SEC = 1
_POLLING_FRAME_TIMESTAMP_TOLERANCE_MS = 5
_POLLING_FRAME_TIMESTAMP_EXCEED_COUNT_TOLERANCE_ = 3
_FAILED_MISSING_POLLING_FRAMES_MSG = "Device did not receive all polling frames"
_FAILED_TIMESTAMP_TOLERANCE_EXCEEDED_MSG = "Polling frame timestamp tolerance exceeded"
_FAILED_VENDOR_GAIN_VALUE_DROPPED_ON_POWER_INCREASE = """
Polling frame vendor specific gain value dropped on power increase
"""
_FAILED_FRAME_TYPE_INVALID = "Polling frame type is invalid"
_FAILED_FRAME_DATA_INVALID = "Polling frame data is invalid"



class CtsNfcHceMultiDevicePhone2PhoneTestCases(base_test.BaseTestClass):

    def _set_up_emulator(self, *args, start_emulator_fun=None, service_list=[],
                 expected_service=None, is_payment=False, preferred_service=None,
                 payment_default_service=None):
        """
        Sets up emulator device for multidevice tests.
        :param is_payment: bool
            Whether test is setting up payment services. If so, this function will register
            this app as the default wallet.
        :param start_emulator_fun: fun
            Custom function to start the emulator activity. If not present,
            startSimpleEmulatorActivity will be used.
        :param service_list: list
            List of services to set up. Only used if a custom function is not called.
        :param expected_service: String
            Class name of the service expected to handle the APDUs.
        :param preferred_service: String
            Service to set as preferred service, if any.
        :param payment_default_service: String
            For payment tests only: the default payment service that is expected to handle APDUs.
        :param args: arguments for start_emulator_fun, if any

        :return:
        """
        role_held_handler = self.emulator.nfc_emulator.asyncWaitForRoleHeld(
            'RoleHeld')
        if start_emulator_fun is not None:
            start_emulator_fun(*args)
        else:
            if preferred_service is None:
                self.emulator.nfc_emulator.startSimpleEmulatorActivity(
                    service_list, expected_service, is_payment, True
                )
            else:
                self.emulator.nfc_emulator.startSimpleEmulatorActivityWithPreferredService(
                    service_list, expected_service, preferred_service, is_payment
                )

        if is_payment:
            role_held_handler.waitAndGet('RoleHeld', _NFC_TIMEOUT_SEC)
            if payment_default_service is None:
                raise Exception("Must define payment_default_service for payment tests.")
            self.emulator.nfc_emulator.waitForService(payment_default_service)

    def _set_up_reader_and_assert_transaction(self, start_reader_fun=None, expected_service=None,
                                              is_offhost=False):
        """
        Sets up reader device, and asserts successful APDU transaction
        :param start_reader_fun: function
                Function to start reader activity on reader phone.
        :param expected_service: string
                Class name of the service expected to handle the APDUs on the emulator device.
        :param is_offhost: bool
                Whether service to handle APDUs is offhost or not.
        :return:
        """
        handler_snippet = self.reader.nfc_reader if is_offhost else (
            self.emulator.nfc_emulator)

        test_pass_handler = handler_snippet.asyncWaitForTestPass('ApduSuccess')
        if start_reader_fun is None:
            raise Exception('start_reader_fun must be defined.')
        start_reader_fun()
        test_pass_handler.waitAndGet('ApduSuccess', _NFC_TIMEOUT_SEC)

    def _is_cuttlefish_device(self, ad: android_device.AndroidDevice) -> bool:
        product_name = ad.adb.getprop("ro.product.name")
        return "cf_x86" in product_name

    def _get_casimir_id_for_device(self):
        host = "localhost"
        conn = HTTPSConnection(host, 1443, context=ssl._create_unverified_context())
        path = '/devices'
        headers = {'Content-type': 'application/json'}
        conn.request("GET", path, {}, headers)
        response = conn.getresponse()
        json_obj = json.loads(response.read())
        first_device = json_obj[0]
        return first_device["device_id"]

    def setup_class(self):
        """
        Sets up class by registering an emulator device, enabling NFC, and loading snippets.

        Sets up a
        second phone as a reader device.
        """

        # This tracks the error message for a setup failure.
        # It is set to None only if the entire setup_class runs successfully.
        self._setup_failure_reason = 'Failed to find Android device(s).'

        # Indicates if the setup failure should block (FAIL) or not block (SKIP) test cases.
        # Blocking failures indicate that something unexpectedly went wrong during test setup,
        # and the user should have it fixed.
        # Non-blocking failures indicate that the device(s) did not meet the test requirements,
        # and the test does not need to be run.
        self._setup_failure_should_block_tests = True

        try:
            devices = self.register_controller(android_device)[:2]
            if len(devices) < 2:
                self._setup_failure_reason = 'Two devices are not present.'
                return
            self.emulator, self.reader = devices

            self._setup_failure_reason = (
                'Cannot load emulator snippet. Is NfcEmulatorTestApp.apk '
                'installed on the emulator?'
            )
            self.emulator.load_snippet(
                'nfc_emulator', 'com.android.nfc.emulator'
            )
            self.emulator.debug_tag = 'emulator'
            if (
                not self.emulator.nfc_emulator.isNfcSupported() or
                not self.emulator.nfc_emulator.isNfcHceSupported()
            ):
                self._setup_failure_reason = f'NFC is not supported on {self.emulator}'
                self._setup_failure_should_block_tests = False
                return
            try:
                self.emulator.adb.shell(['svc', 'nfc', 'enable'])
            except adb.AdbError:
                _LOG.info("Could not enable nfc through adb.")
                self.emulator.nfc_emulator.setNfcState(True)

            self._setup_failure_reason = (
                'Cannot load reader snippet. Is NfcReaderTestApp.apk '
                'installed on the reader?'
            )
            self.reader.load_snippet('nfc_reader', 'com.android.nfc.reader')
            try:
                self.reader.adb.shell(['svc', 'nfc', 'enable'])
            except adb.AdbError:
                _LOG.info("Could not enable nfc through adb.")
                self.reader.nfc_reader.setNfcState(True)
            self.reader.debug_tag = 'reader'
            if (
                not self.reader.nfc_reader.isNfcSupported() or
                not self.reader.nfc_reader.isNfcHceSupported()
            ):
                self._setup_failure_reason = f'NFC is not supported on {self.reader}'
                self._setup_failure_should_block_tests = False
                return

        except Exception as e:
            _LOG.warning('setup_class failed with error %s', e)
            return
        self._setup_failure_reason = None

    def setup_test(self):
        """
        Turns emulator/reader screen on and unlocks between tests as some tests will
        turn the screen off.
        """
        if self._setup_failure_should_block_tests:
            asserts.assert_true(
                self._setup_failure_reason is None, self._setup_failure_reason
            )
        else:
            asserts.skip_if(
                self._setup_failure_reason is not None, self._setup_failure_reason
            )

        self.emulator.nfc_emulator.logInfo("*** TEST START: " + self.current_test_info.name +
                                           " ***")
        self.emulator.nfc_emulator.turnScreenOn()
        self.emulator.nfc_emulator.pressMenu()
        self.reader.nfc_reader.turnScreenOn()
        self.reader.nfc_reader.pressMenu()

    def on_fail(self, record):
        if self.user_params.get('take_bug_report_on_fail', False):
            test_name = record.test_name
            if hasattr(self, 'emulator') and hasattr(self.emulator, 'nfc_emulator'):
                self.emulator.take_bug_report(
                    test_name=self.emulator.debug_tag + "_" + test_name,
                    destination=self.current_test_info.output_path,
                )
            if hasattr(self, 'reader') and hasattr(self.reader, 'nfc_reader'):
                self.reader.take_bug_report(
                    test_name=self.reader.debug_tag + "_" + test_name,
                    destination=self.current_test_info.output_path,
                )

    @CddTest(requirements = ["7.4.4/C-2-2", "7.4.4/C-1-2"])
    def test_single_non_payment_service(self):
        """Tests successful APDU exchange between non-payment service and
        reader.

        Test Steps:
        1. Start emulator activity and set up non-payment HCE Service.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and
        Transport Service after
        _NFC_TIMEOUT_SEC.
        """
        self._set_up_emulator(
            service_list=[_TRANSPORT_SERVICE_1],
            expected_service=_TRANSPORT_SERVICE_1
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_TRANSPORT_SERVICE_1,
            start_reader_fun=self.reader.nfc_reader.startSingleNonPaymentReaderActivity
        )

    @CddTest(requirements = ["7.4.4/C-2-2", "7.4.4/C-1-2", "9.1/C-0-1"])
    def test_single_payment_service(self):
        """Tests successful APDU exchange between payment service and
        reader.

        Test Steps:
        1. Set callback handler on emulator for when the instrumentation app is
        set to default wallet app.
        2. Start emulator activity and wait for the role to be set.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies emulator device sets the instrumentation emulator app to the
        default wallet app.
        2. Verifies a successful APDU exchange between the emulator and
        Transport Service after _NFC_TIMEOUT_SEC.
        """
        self._set_up_emulator(
            service_list=[_PAYMENT_SERVICE_1],
            expected_service=_PAYMENT_SERVICE_1,
            is_payment=True,
            payment_default_service=_PAYMENT_SERVICE_1
        )

        self._set_up_reader_and_assert_transaction(
            expected_service=_PAYMENT_SERVICE_1,
            start_reader_fun=self.reader.nfc_reader.startSinglePaymentReaderActivity)

    @CddTest(requirements = ["7.4.4/C-2-2", "7.4.4/C-1-2"])
    def test_ndef_url_tag(self):
        """Tests that an emulated NFC tag with a URL can be read using the reader mode API.

        Test Steps:
        1. Start emulator activity to emulate a tag with an NDEF message containing a URL.
        2. Set callback handler on emulator for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and
        NDEF Service after _NFC_TIMEOUT_SEC.
        """
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startNdefEmulatorActivity
        )
        self._set_up_reader_and_assert_transaction(
            expected_service=_NDEF_SERVICE,
            start_reader_fun=self.reader.nfc_reader.startNdefReaderActivity
        )


    @CddTest(requirements = ["7.4.4/C-2-2", "7.4.4/C-1-2"])
    def test_action_view_ndef_url_tag(self):
        """Tests that an emulated NFC tag with a URL can be read using an ACTION_VIEW intent.

        Test Steps:
        1. Start emulator activity to emulate a tag with an NDEF message containing a URL.
        2. The reader device will detect the tag and launch the NdefUrlReaderActivity via an
           ACTION_VIEW intent filter.
        3. The NdefUrlReaderActivity will write the received URL to a file.
        4. The test will read the file and verify that the URL is correct.

        Verifies:
        1. Verifies that the reader device correctly receives the URL from the emulated tag.
        """

        test_pass_handler = self.reader.nfc_reader.asyncWaitForTestPass('TestPass')
        self.reader.nfc_reader.startNdefActionViewReaderActivity()
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startNdefEmulatorActivity
        )
        test_pass_handler.waitAndGet('TestPass', _NFC_TIMEOUT_SEC)

        received_url = self.reader.nfc_reader.getReceivedUrl()
        asserts.assert_equal(received_url, "https://android.com",
                               "Received URL does not match the expected URL.")

    @CddTest(requirements = ["7.4.4/C-2-2", "7.4.4/C-1-2"])
    def test_gesture_exchange(self):
        """Tests that gesture exchange callback is triggered.

        Test Steps:
        1. Get the gesture exchange AID from the reader device.
        2. Start emulator activity to emulate a tag with the gesture exchange AID.
        3. Register the gesture exchange callback on the reader device.
        4. The reader device will detect the tag and trigger the callback.
        5. The callback will transceive an APDU and send a broadcast on success.
        6. The test will wait for the broadcast and verify that the test passed.

        Verifies:
        1. Verifies that the gesture exchange callback is triggered and the APDU exchange is successful.
        """
        self.emulator.nfc_emulator.startGestureExchangeEmulatorActivity(_GESTURE_EXCHANGE_AID)
        time.sleep(10)  # Add delay for HCE service to start

        test_pass_handler = self.reader.nfc_reader.asyncWaitForTestPass('TestPass')
        self.reader.nfc_reader.startGestureExchangeReaderActivity()

        test_pass_handler.waitAndGet('TestPass', _NFC_TIMEOUT_SEC)

    @CddTest(requirements = ["7.4.4/C-2-2", "7.4.4/C-1-2"])
    def test_gesture_exchange_and_ndef_read(self):
        """Tests that registering gesture exchange callback does not block NDEF tag read.

        Test Steps:
        1. Register the gesture exchange callback on the reader device.
        2. Start NDEF emulator activity on the emulator device.
        3. The reader device should be able to detect the NDEF tag and receive the URL.

        Verifies:
        1. Verifies that the reader device correctly receives the URL from the emulated tag
           even when the gesture exchange callback is registered.
        """
        self.reader.nfc_reader.startGestureExchangeReaderActivity()
        time.sleep(2)  # Give it some time to register the callback

        test_pass_handler = self.reader.nfc_reader.asyncWaitForTestPass('TestPass')
        self.reader.nfc_reader.startNdefActionViewReaderActivity()
        self._set_up_emulator(
            start_emulator_fun=self.emulator.nfc_emulator.startNdefEmulatorActivity
        )
        test_pass_handler.waitAndGet('TestPass', _NFC_TIMEOUT_SEC)

        received_url = self.reader.nfc_reader.getReceivedUrl()
        asserts.assert_equal(received_url, "https://android.com",
                               "Received URL does not match the expected URL.")



    @CddTest(requirements = ["7.4.4/C-2-2", "7.4.4/C-1-2"])
    def test_polling_loop_annotation(self):
        """Tests successful APDU exchange between polling loop annotation service and
        reader.

        Test Steps:
        1. Start emulator activity and set up polling loop annotation HCE Service.
        2. Set callback handler on reader for when a TestPass event is
        received.
        3. Start reader activity, which should trigger APDU exchange between
        reader and emulator and verify polling loop annotation.

        Verifies:
        1. Verifies a successful APDU exchange between the emulator and
        PollingLoopAnnotationService after _NFC_TIMEOUT_SEC.
        """
        asserts.skip_if(not self.emulator.nfc_emulator.isObserveModeSupported(),
                        "Observe mode is not supported on the emulator device.")
        asserts.skip_if(not self.reader.nfc_reader.isReaderModeAnnotationSupported(),
                        "Reader mode annotation is not supported on the reader device.")

        self.emulator.nfc_emulator.startPollingLoopAnnotationEmulatorActivity()

        test_pass_handler = self.reader.nfc_reader.asyncWaitForTestPass("TestPass")
        self.reader.nfc_reader.startPollingLoopAnnotationReaderActivity()

        test_pass_handler.waitAndGet("TestPass", _NFC_TIMEOUT_SEC)

    def teardown_test(self):
        if hasattr(self, 'emulator') and hasattr(self.emulator, 'nfc_emulator'):
            self.emulator.nfc_emulator.closeActivity()
            self.emulator.nfc_emulator.logInfo(
                "*** TEST END: " + self.current_test_info.name + " ***")
        param_list = []
        if hasattr(self, 'reader') and hasattr(self.reader, 'nfc_reader'):
            self.reader.nfc_reader.closeActivity()
            self.reader.nfc_reader.logInfo(
                "*** TEST END: " + self.current_test_info.name + " ***")
            param_list = [[self.emulator], [self.reader]]
        utils.concurrent_exec(lambda d: d.services.create_output_excerpts_all(
            self.current_test_info),
                              param_list=param_list,
                              raise_on_exception=True)

if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]
    test_runner.main()
