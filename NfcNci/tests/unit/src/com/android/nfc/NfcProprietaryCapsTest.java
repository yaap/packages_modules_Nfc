/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.nfc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NfcProprietaryCapsTest {

    private static final int PASSIVE_OBSERVE_MODE = 0;
    private static final int POLLING_FRAME_NTF = 1;
    private static final int POWER_SAVING_MODE = 2;
    private static final int AUTOTRANSACT_POLLING_LOOP_FILTER = 3;
    private static final int NUMBER_OF_EXIT_FRAMES_SUPPORTED = 4;
    private static final int READER_MODE_ANNOTATIONS_SUPPORTED = 5;

    @Test
    public void testCreateFromByteArraySupportWithoutRfDeactivation() {
        byte[] inputCaps = {
                (byte) PASSIVE_OBSERVE_MODE, 1, 2,
                (byte) POLLING_FRAME_NTF, 1, 1,
                (byte) POWER_SAVING_MODE, 1, 0,
                (byte) AUTOTRANSACT_POLLING_LOOP_FILTER, 1, 1,
                (byte) NUMBER_OF_EXIT_FRAMES_SUPPORTED, 1, 5
        };

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(inputCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.SUPPORT_WITHOUT_RF_DEACTIVATION,
                result.getPassiveObserveMode());
        assertTrue(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertTrue(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(5, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArraySupportWithRfDeactivation() {
        byte[] inputCaps = {
                (byte) PASSIVE_OBSERVE_MODE, 1, 1,
                (byte) POLLING_FRAME_NTF, 1, 1,
                (byte) POWER_SAVING_MODE, 1, 0,
                (byte) AUTOTRANSACT_POLLING_LOOP_FILTER, 1, 1,
                (byte) NUMBER_OF_EXIT_FRAMES_SUPPORTED, 1, 5
        };

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(inputCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.SUPPORT_WITH_RF_DEACTIVATION,
                result.getPassiveObserveMode());
        assertTrue(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertTrue(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(5, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArrayNotSupported() {
        byte[] inputCaps = {
                (byte) PASSIVE_OBSERVE_MODE, 1, 0,
                (byte) POLLING_FRAME_NTF, 1, 1,
                (byte) POWER_SAVING_MODE, 1, 0,
                (byte) AUTOTRANSACT_POLLING_LOOP_FILTER, 1, 1,
                (byte) NUMBER_OF_EXIT_FRAMES_SUPPORTED, 1, 5
        };

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(inputCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
        assertTrue(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertTrue(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(5, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArrayWithInvalidData() {
        byte[] invalidCaps = {(byte) PASSIVE_OBSERVE_MODE, 2, 3}; // Invalid length

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(invalidCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
        assertFalse(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertFalse(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(0, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArrayWithEmptyArray() {
        byte[] emptyCaps = {};

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(emptyCaps);
        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
        assertFalse(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertFalse(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(0, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArrayWithNullArray() {
        // Verifies that passing a null byte array does not cause a crash and returns a default
        // NfcProprietaryCaps object. This is the primary test for the added null check.
        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(null);

        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
        assertFalse(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertFalse(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(0, result.getNumberOfExitFramesSupported());
        assertFalse(result.isReaderModeAnnotationSupported());
    }

    @Test
    public void testCreateFromByteArrayReaderModeAnnotationSupported() {
        // Verifies parsing for the READER_MODE_ANNOTATIONS_SUPPORTED capability.
        byte[] inputCaps = {
                (byte) READER_MODE_ANNOTATIONS_SUPPORTED, 1, 1
        };

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(inputCaps);

        assertNotNull(result);
        assertTrue(result.isReaderModeAnnotationSupported());
        // Verify other properties have their default values.
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
        assertFalse(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertFalse(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(0, result.getNumberOfExitFramesSupported());
    }

    @Test
    public void testCreateFromByteArrayWithZeroLengthValue() {
        // Verifies that a capability with a value length of 0 is handled gracefully.
        // The parsing loop should break, and default values should be returned.
        byte[] invalidCaps = {(byte) PASSIVE_OBSERVE_MODE, 0}; // Length is 0

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(invalidCaps);

        assertNotNull(result);
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
        assertFalse(result.isPollingFrameNotificationSupported());
        assertFalse(result.isPowerSavingModeSupported());
        assertFalse(result.isAutotransactPollingLoopFilterSupported());
        assertEquals(0, result.getNumberOfExitFramesSupported());
        assertFalse(result.isReaderModeAnnotationSupported());
    }

    @Test
    public void testCreateFromByteArrayPassiveObserveModeInvalidValue() {
        // Verifies that an invalid value for PASSIVE_OBSERVE_MODE is handled correctly.
        // The default value should be retained.
        byte[] inputCaps = {
                (byte) PASSIVE_OBSERVE_MODE, 1, 99 // Invalid value for passive observe mode
        };

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(inputCaps);

        assertNotNull(result);
        // The value should be ignored, and the default should be kept.
        assertEquals(NfcProprietaryCaps.PassiveObserveMode.NOT_SUPPORTED,
                result.getPassiveObserveMode());
    }

    @Test
    public void testCreateFromByteArrayNumberOfExitFramesFallthrough() {
        // Verifies the behavior of the fall-through from NUMBER_OF_EXIT_FRAMES_SUPPORTED
        // to READER_MODE_ANNOTATIONS_SUPPORTED due to a missing break statement in the source.
        byte[] inputCaps = {
                (byte) NUMBER_OF_EXIT_FRAMES_SUPPORTED, 1, 1
        };

        NfcProprietaryCaps result = NfcProprietaryCaps.createFromByteArray(inputCaps);

        assertNotNull(result);
        assertEquals(1, result.getNumberOfExitFramesSupported());
        // Due to fall-through, isReaderModeAnnotationSupported should be true (1 == 0x1)
        assertTrue(result.isReaderModeAnnotationSupported());
    }

    @Test
    public void testToString() {
        NfcProprietaryCaps caps = new NfcProprietaryCaps(
                NfcProprietaryCaps.PassiveObserveMode.SUPPORT_WITHOUT_RF_DEACTIVATION,
                true,
                false,
                true,
                5,
                false
        );
        String expected = "NfcProprietaryCaps{" +
                "passiveObserveMode=SUPPORT_WITHOUT_RF_DEACTIVATION, " +
                "isPollingFrameNotificationSupported=true, " +
                "isPowerSavingModeSupported=false, " +
                "isAutotransactPollingLoopFilterSupported=true, " +
                "numberOfExitFramesSupported=5, " +
                "mIsReaderModeAnnotationSupported=false}";

        assertEquals(expected, caps.toString());
    }
}
