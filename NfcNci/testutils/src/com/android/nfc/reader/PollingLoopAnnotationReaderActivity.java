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

package com.android.nfc.reader;

import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.util.Log;

import com.android.nfc.utils.CommandApdu;
import com.android.nfc.utils.HceUtils;

import java.io.IOException;
import java.util.HexFormat;

public class PollingLoopAnnotationReaderActivity extends BaseReaderActivity
        implements ReaderCallback {
    static final String TAG = "PollingLoopAnnotationReaderActivity";
    public static final String EXTRA_AID = "aid";
    private boolean mTestingAnnotation = true;
    private boolean mPassedAnnotateedTest = false;
    private boolean mPassedUnannotatedTest = false;
    private String mAid = "F00506070A";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().hasExtra(EXTRA_AID)) {
            mAid = getIntent().getStringExtra(EXTRA_AID);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpReaderMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.disableReaderMode(this);
    }

    private void setUpReaderMode() {
        mAdapter.disableReaderMode(this);
        Bundle extras = new Bundle();
        extras.putByteArray(
                NfcAdapter.EXTRA_READER_TECH_A_POLLING_LOOP_ANNOTATION,
                mTestingAnnotation ? HexFormat.of().parseHex("DEADBEEF") : new byte[0]);
        mAdapter.enableReaderMode(
                this,
                this,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                extras);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            return;
        }
        try {
            isoDep.connect();
            isoDep.setTimeout(5000);
            CommandApdu selectCommand = HceUtils.buildSelectApdu(mAid, true);
            byte[] command = HexFormat.of().parseHex(selectCommand.getApdu());
            byte[] resp = isoDep.transceive(command);
            isoDep.close();
            if (mAid.equals("F00506070A")) {
                if (resp.length != 1) {
                    return;
                }
                if (mTestingAnnotation) {
                    if (resp[0] == 0x01) {
                        mPassedAnnotateedTest = true;
                        mTestingAnnotation = false;
                    }
                } else {
                    if (resp[0] == 0x02) {
                        mPassedUnannotatedTest = true;
                        mTestingAnnotation = true;
                    }
                }
            } else {
                if (resp.length < 2) {
                    return;
                }
                if (resp[resp.length - 2] != (byte) 0x90 || resp[resp.length - 1] != 0x00) {
                    return;
                }
                mPassedAnnotateedTest = true;
                mPassedUnannotatedTest = true;
            }
            setUpReaderMode();

            if (mPassedUnannotatedTest && mPassedAnnotateedTest) {
                setTestPassed();
            }
        } catch (IOException ioe) {
            Log.i(TAG, "IO exception", ioe);
        }
    }
}
