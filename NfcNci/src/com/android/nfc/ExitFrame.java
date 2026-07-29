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

import android.annotation.NonNull;

import java.util.HexFormat;

/** Class representing an exit frame for firmware autotransactions. */
public class ExitFrame {
    private final byte[] mData;
    private final boolean mAllowPrefixMatching;

    private final int mNfcTech;
    private final int mPowerState;
    private final byte[] mDataMask;

    public ExitFrame(@NonNull String pollingLoopFilter) {
        // Don't allow ?, otherwise the length is ambiguous
        if (pollingLoopFilter.contains("?")) {
            throw new IllegalArgumentException(
                    "Firmware exit frames don't support '?' in pattern.");
        }
        // Special case of no data, match everything
        if (pollingLoopFilter.equals("*")) {
            mAllowPrefixMatching = true;
            pollingLoopFilter = "";
        } else if (pollingLoopFilter.indexOf('*') == pollingLoopFilter.length() - 1 &&
                pollingLoopFilter.charAt(pollingLoopFilter.length() - 2) == '.') {
            // If .* is the last two characters, we can use this as a prefix
            mAllowPrefixMatching = true;
            // Remove the .* from the filter
            pollingLoopFilter = pollingLoopFilter.substring(0, pollingLoopFilter.length() - 2);

            // If we have an odd number of characters, append a . to match any character in this
            // nibble
            if (pollingLoopFilter.length() % 2 != 0) {
                pollingLoopFilter += '.';
            }
        } else if (pollingLoopFilter.contains("*")) {
            throw new IllegalArgumentException(
                    "Firmware exit frames only support * when \".*\" are the last two characters.");
        } else {
            mAllowPrefixMatching = false;
        }

        if (pollingLoopFilter.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Firmware exit frames must have an even number of characters.");
        }


        // Replace all '.' with 0 in the filter and mask. Otherwise set mask to F.
        char[] filterChars = pollingLoopFilter.toCharArray();
        char[] maskChars = new char[filterChars.length];
        for (int i = 0; i < filterChars.length; ++i) {
            if (filterChars[i] == '.') {
                filterChars[i] = '0';
                maskChars[i] = '0';
            } else {
                maskChars[i] = 'F';
            }
        }
        mData = HexFormat.of().parseHex(String.valueOf(filterChars));
        if (mData.length > 16) {
            throw new IllegalArgumentException(
                    "Filter too long, firmware exit frames only support 16 byte filters.");
        }
        mDataMask = HexFormat.of().parseHex(String.valueOf(maskChars));

        mNfcTech = 0b00;
        // Screen OFF/LOCKED Sub-State 3: SUPPORTED
        // Screen ON/LOCKED Sub-State 2: SUPPORTED
        // Screen OFF/UNLOCKED Sub-State 1: SUPPORTED
        // Switched OFF State: NOT SUPPORTED
        // Screen ON/UNLOCKED State: SUPPORTED
        mPowerState = 0b00111001;
    }

    public byte[] getData() {
        return mData;
    }

    public boolean isPrefixMatchingAllowed() {
        return mAllowPrefixMatching;
    }

    public int getNfcTech() {
        return mNfcTech;
    }

    public int getPowerState() {
        return mPowerState;
    }

    public byte[] getDataMask() {
        return mDataMask;
    }

    @Override
    public String toString() {
        return "ExitFrame{data: " + HexFormat.of().formatHex(mData) + ", mask: "
                + HexFormat.of().formatHex(mDataMask)
                + ", isPrefixMatchingAllowed: " + isPrefixMatchingAllowed() + "}";
    }
}
