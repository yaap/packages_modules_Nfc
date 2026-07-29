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

package com.android.nfc.reader;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class NdefUrlReaderActivity extends BaseReaderActivity {
    private static final String TAG = "NdefUrlReaderActivity";
    public static String sReceivedUrl = null;

    /** Gets the URL received by the NDEF URL reader activity. */
    public static String getReceivedUrl() {
        String url = sReceivedUrl;
        sReceivedUrl = null;
        return url;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0,
                intent, android.app.PendingIntent.FLAG_MUTABLE);
        mAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: intent=" + getIntent());
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent: intent=" + intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)
                || android.content.Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                sReceivedUrl = uri.toString();
                Log.d(TAG, "Received URL: " + sReceivedUrl);
                setTestPassed();
                finish();
            }
        }
    }

}
