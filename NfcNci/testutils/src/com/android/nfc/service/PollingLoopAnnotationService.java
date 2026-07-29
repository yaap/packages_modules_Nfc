/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.nfc.service;

import android.content.ComponentName;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.HostApduService;
import android.nfc.cardemulation.PollingFrame;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public class PollingLoopAnnotationService extends HostApduService {
    static final String TAG = "PollingLoopAnnotationService";

    public static final String POLLING_FRAME_ACTION =
            "com.android.nfc.service.POLLING_FRAME_ACTION";
    public static final String POLLING_FRAME_EXTRA = "POLLING_FRAME_EXTRA";

    public static final ComponentName COMPONENT =
            new ComponentName("com.android.nfc.emulator",
                    PollingLoopAnnotationService.class.getName());

    private boolean mSeenAnnotation = false;

    final HexFormat mHexFormat = HexFormat.of();
    final byte[] mAnnotation = mHexFormat.parseHex("DEADBEEF");
    private int mFrameCounter = 0;
    private Handler mHandler;

    public PollingLoopAnnotationService() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public byte[] processCommandApdu(byte[] arg0, Bundle arg1) {
        byte[] ret = {(byte) (mSeenAnnotation ? 0x01 : 0x02)};
        mSeenAnnotation = false;
        mHandler.postDelayed(
                () -> {
                    setObserveMode(true);
                    mSeenAnnotation = false;
                    mFrameCounter = 0;
                },
                500);
        return ret;
    }

    @Override
    public void processPollingFrames(List<PollingFrame> frames) {
        Log.d(TAG, "Polling frames: " + frames);
        for (PollingFrame frame : frames) {
            mFrameCounter++;
            if (frame.getType() == PollingFrame.POLLING_LOOP_TYPE_UNKNOWN) {
                if (Arrays.equals(frame.getData(), mAnnotation)) {
                    Log.d(TAG, "Polling frame matched: " + frame);
                    mSeenAnnotation = true;
                    setObserveMode(false);
                    Intent pollingFrameIntent = new Intent(POLLING_FRAME_ACTION);
                    pollingFrameIntent.putParcelableArrayListExtra(
                            POLLING_FRAME_EXTRA, new ArrayList<PollingFrame>(frames));
                    sendBroadcast(pollingFrameIntent);
                }
            }
        }
        if (mFrameCounter > 15) {
            setObserveMode(false);
        }
    }

    void setObserveMode(boolean enabled) {
        NfcAdapter.getDefaultAdapter(this).setObserveModeEnabled(enabled);
    }

    @Override
    public void onDeactivated(int reason) {}
}
