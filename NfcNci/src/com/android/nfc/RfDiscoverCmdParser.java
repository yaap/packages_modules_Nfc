/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.nfc.RfDiscoverConfig;
import android.sysprop.NfcProperties;
import android.util.Log;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parse current RF DISCOVERY Config and dump it with a clear typography
 */
public class RfDiscoverCmdParser {
    static final boolean DBG = NfcProperties.debug_enabled().orElse(true);
    private static final String TAG = "RfDiscoverCmdParser";

    private int mNumOfConfigs = 0;
    private String mRawDataRfDiscoverCmd = "";
    private final List<DiscoverConfigInfo> mDiscoverConfiguration = new ArrayList<>();

    // Entry technology mode
    static final int NFC_A_PASSIVE_POLL_MODE = 0x0;
    static final int NFC_B_PASSIVE_POLL_MODE = 0x1;
    static final int NFC_F_PASSIVE_POLL_MODE = 0x2;
    static final int NFC_ACTIVE_POLL_MODE = 0x3;
    static final int NFC_V_PASSIVE_POLL_MODE = 0x6;
    static final int NFC_A_KOVIO_POLL_MODE = 0x77;
    static final int NFC_A_PASSIVE_LISTEN_MODE = 0x80;
    static final int NFC_B_PASSIVE_LISTEN_MODE = 0x81;
    static final int NFC_F_PASSIVE_LISTEN_MODE = 0x82;
    static final int NFC_ACTIVE_LISTEN_MODE = 0x83;

    private String formatRow(String techMode, String frequency) {
        return String.format("\t%-36s\t%-18s", techMode, frequency);
    }

    private static class DiscoverConfigInfo {
        final byte mTechnologyMode;
        final byte mFrequency;

        private DiscoverConfigInfo(byte techMode, byte frequency) {
            mTechnologyMode = techMode;
            mFrequency = frequency;
        }

        private void dump(PrintWriter pw, RfDiscoverCmdParser parser) {
            String techMode = parser.getDiscoverTechModeStr(mTechnologyMode);
            String frequency = String.format("0x%02X", mFrequency);
            pw.println(parser.formatRow(techMode, frequency));
        }
    }

    private String getDiscoverTechModeStr(byte techMode) {
        int mode = techMode & 0xFF;
        return switch (mode) {
            case NFC_A_PASSIVE_POLL_MODE -> "NFC_A_PASSIVE_POLL_MODE";
            case NFC_B_PASSIVE_POLL_MODE -> "NFC_B_PASSIVE_POLL_MODE";
            case NFC_F_PASSIVE_POLL_MODE -> "NFC_F_PASSIVE_POLL_MODE";
            case NFC_ACTIVE_POLL_MODE -> "NFC_ACTIVE_POLL_MODE";
            case NFC_V_PASSIVE_POLL_MODE -> "NFC_V_PASSIVE_POLL_MODE";
            case NFC_A_KOVIO_POLL_MODE -> "NFC_A_KOVIO_POLL_MODE";
            case NFC_A_PASSIVE_LISTEN_MODE -> "NFC_A_PASSIVE_LISTEN_MODE";
            case NFC_B_PASSIVE_LISTEN_MODE -> "NFC_B_PASSIVE_LISTEN_MODE";
            case NFC_F_PASSIVE_LISTEN_MODE -> "NFC_F_PASSIVE_LISTEN_MODE";
            case NFC_ACTIVE_LISTEN_MODE -> "NFC_ACTIVE_LISTEN_MODE";
            default -> {
                if ((mode >= 0x04 && mode <= 0x05) || (mode >= 0x07 && mode <= 0x6F)
                        || (mode >= 0x84 && mode <= 0xEF)) {
                    yield "RFU (Reserved for Future Use)";
                } else if (mode >= 0x70 && mode <= 0x7F) {
                    yield "Reserved for Proprietary Technologies in Poll Mode";
                } else if (mode >= 0xF0 && mode <= 0xFF) {
                    yield "Reserved for Proprietary Technologies in Listen Mode";
                } else {
                    yield "UNKNOWN_RF_MODE";
                }
            }
        };
    }

    /**
     * Parse the raw data of routing table
     */
    public void parse(byte[] config) {
        mDiscoverConfiguration.clear();
        mNumOfConfigs = 0;
        mRawDataRfDiscoverCmd = "";

        if (config == null || config.length == 0) {
            Log.i(TAG, "parse: No RF Discover config enabled");
            return;
        }
        logRfDiscoverConfigRawData(config);

        int offset = 0;
        while (offset < config.length) {
            if (offset + 1 >= config.length) {
                Log.e(TAG, "parse: incorrect length in packet format, stop parsing");
                return;
            }

            byte techMode = config[offset++];
            byte frequency = config[offset++];
            mDiscoverConfiguration.add(new DiscoverConfigInfo(techMode, frequency));
        }
    }

    /**
     * Get current Rf discover configuration and parse it
     */
    public void update(DeviceHost dh) {
        if (dh != null) {
            byte[] discoverConfig = dh.getRfDiscoverConfig();
            parse(discoverConfig);
        }
    }

    /**
     * Return current Rf discover configuration
     */
    public List<RfDiscoverConfig> getRfDiscoverConfigurations(DeviceHost dh) {
        update(dh);
        List<RfDiscoverConfig> configs = new ArrayList<>();
        for (DiscoverConfigInfo info : mDiscoverConfiguration) {
            configs.add(new RfDiscoverConfig(info.mTechnologyMode, info.mFrequency));
        }
        return configs;
    }

    /**
     * Get current Rf discover configuration and dump it
     */
    public void dump(DeviceHost dh, PrintWriter pw) {
        update(dh);

        pw.println("--- dumpRfDiscoverConfigurations: start ---");
        pw.println(String.format(Locale.US, "Rf Discover Configs=%s", mRawDataRfDiscoverCmd));
        pw.println(String.format(Locale.US, "Number of Discover Configs=%d", mNumOfConfigs));
        pw.println(formatRow("Technology", "Frequency"));

        for (DiscoverConfigInfo discoverConfig : mDiscoverConfiguration) {
            discoverConfig.dump(pw, this);
        }

        pw.println("--- dumpRfDiscoverConfigurations:  end  ---");
    }

    private void logRfDiscoverConfigRawData(byte[] rfDiscoverCmd) {
        if (!DBG) return;
        StringBuilder sb = new StringBuilder();
        for (byte b : rfDiscoverCmd) {
            sb.append(String.format("%02X ", b));
        }
        mNumOfConfigs = rfDiscoverCmd.length / 2;
        mRawDataRfDiscoverCmd = sb.toString();
        Log.i(TAG, String.format("NumOfConfigs=%d", mNumOfConfigs));
        Log.i(TAG, String.format("RfDiscoverCmd=%s", mRawDataRfDiscoverCmd));
    }
}
