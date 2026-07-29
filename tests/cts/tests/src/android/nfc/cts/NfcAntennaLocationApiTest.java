/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nfc.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.AvailableNfcAntenna;
import android.nfc.NfcAdapter;
import android.nfc.NfcAntennaInfo;
import android.os.SystemProperties;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

public class NfcAntennaLocationApiTest {
    private static final String REPORT_LOG_NAME = "CtsNfcTestCases";

    private static final int ANTENNA_X = 12;
    private static final int ANTENNA_Y = 13;

    private boolean supportsHardware() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    private int getVendorApiLevel() {
        return SystemProperties.getInt("ro.board.api_level", 0);
    }

    private static boolean isGsi() {
        final File initGsiRc = new File("/system/system_ext/etc/init/init.gsi.rc");
        return initGsiRc.exists();
    }

    private NfcAdapter mAdapter;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Device must support NFC", supportsHardware());
        mContext = InstrumentationRegistry.getContext();
        mAdapter = NfcAdapter.getDefaultAdapter(mContext);
        assertNotNull("NFC Adapter is null", mAdapter);
    }

    @After
    public void tearDown() throws Exception {
    }

    private void logAntennaInfo(String testName, NfcAntennaInfo info) {
        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, testName);
        log.addValue("deviceWidth",
                info.getDeviceWidth(), ResultType.NEUTRAL, ResultUnit.MILLIMETERS);
        log.addValue("deviceHeight",
                info.getDeviceHeight(), ResultType.NEUTRAL, ResultUnit.MILLIMETERS);
        log.addValue("isDeviceFoldable",
                info.isDeviceFoldable(), ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValues("antennaLocations",
                info.getAvailableNfcAntennas().stream()
                        .map(antenna -> antenna.getLocationX() + ", " + antenna.getLocationY())
                        .toList(),
                ResultType.NEUTRAL,
                ResultUnit.MILLIMETERS);

        log.submit(InstrumentationRegistry.getInstrumentation());
    }

    /** Tests getNfcAntennaInfo API */
    @Test
    public void testGetNfcAntennaInfo() {
        assumeTrue(getVendorApiLevel() > 202504);
        assumeFalse(isGsi());
        NfcAntennaInfo nfcAntennaInfo = mAdapter.getNfcAntennaInfo();

        assertNotNull(nfcAntennaInfo);
        assertTrue(nfcAntennaInfo.getDeviceWidth() > 0);
        assertTrue(nfcAntennaInfo.getDeviceHeight() > 0);
        logAntennaInfo(Thread.currentThread().getStackTrace()[1].getMethodName(), nfcAntennaInfo);
    }

    @Test
    public void testNfcAntennaInfoConstructor() {
        int deviceWidth = 0;
        int deviceHeight = 0;
        boolean deviceFoldable = false;
        AvailableNfcAntenna availableAntenna = new AvailableNfcAntenna(ANTENNA_X, ANTENNA_Y);

        NfcAntennaInfo nfcAntennaInfo = new NfcAntennaInfo(
                deviceWidth, deviceHeight, deviceFoldable, List.of(availableAntenna));

        assertEquals("Device widths do not match", deviceWidth,
                nfcAntennaInfo.getDeviceWidth());
        assertEquals("Device heights do not match", deviceHeight,
                nfcAntennaInfo.getDeviceHeight());
        assertEquals("Device foldable do not match", deviceFoldable,
                nfcAntennaInfo.isDeviceFoldable());
        assertEquals("Wrong available antennas", availableAntenna,
                Iterables.getOnlyElement(nfcAntennaInfo.getAvailableNfcAntennas()));
    }
}
