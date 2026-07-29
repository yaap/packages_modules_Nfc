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


import android.app.Instrumentation;
import android.content.Intent;
import android.nfc.NfcAdapter;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.nfc.service.NdefService;
import com.android.nfc.service.PaymentService1;
import com.android.nfc.service.TransportService1;
import com.android.nfc.utils.CommandApdu;
import com.android.nfc.utils.HceUtils;
import com.android.nfc.utils.NfcSnippet;

import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

public class NfcReaderDeviceSnippet extends NfcSnippet {
    protected static final String TAG = "NfcSnippet";

    private BaseReaderActivity mActivity;

    /** Opens NFC reader for single non-payment test */
    @Rpc(description = "Open simple reader activity for single non-payment test")
    public void startSingleNonPaymentReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(TransportService1.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(TransportService1.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Opens simple reader activity for NDEF test */
    @Rpc(description = "Open simple reader activity for NDEF test")
    public void startNdefReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(NdefService.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(NdefService.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Open simple reader activity for polling loop annotation test */
    @Rpc(description = "Open simple reader activity for polling loop annotation test")
    public void startPollingLoopAnnotationReaderActivity() {
        startPollingLoopAnnotationReaderActivityWithAid(null);
    }

    /** Open simple reader activity for polling loop annotation test with AID */
    @Rpc(description = "Open simple reader activity for polling loop annotation test with AID")
    public void startPollingLoopAnnotationReaderActivityWithAid(String aid) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(),
                PollingLoopAnnotationReaderActivity.class.getName());
        if (aid != null) {
            intent.putExtra(PollingLoopAnnotationReaderActivity.EXTRA_AID, aid);
        }
        mActivity = (PollingLoopAnnotationReaderActivity) instrumentation.startActivitySync(intent);
    }
    /** Open simple reader activity for single payment test */
    @Rpc(description = "Open simple reader activity for single payment test")
    public void startSinglePaymentReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        HceUtils.COMMAND_APDUS_BY_SERVICE.get(PaymentService1.class.getName()),
                        HceUtils.RESPONSE_APDUS_BY_SERVICE.get(PaymentService1.class.getName()));
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Registers receiver for Test Pass event */
    @AsyncRpc(description = "Waits for Test Pass event")
    public void asyncWaitForTestPass(String callbackId, String eventName) {
        registerSnippetBroadcastReceiver(
                callbackId, eventName, BaseReaderActivity.ACTION_TEST_PASSED);
    }

    /** Opens reader activity for gesture exchange test */
    @Rpc(description = "Open reader activity for gesture exchange test")
    public void startGestureExchangeReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent =
                buildReaderIntentWithApduSequence(
                        instrumentation,
                        new CommandApdu[] {
                                HceUtils.buildSelectApdu(HceUtils.GESTURE_EXCHANGE_AID, true)
                        },
                        new String[] {"9000"});
        mActivity = (SimpleReaderActivity) instrumentation.startActivitySync(intent);
    }

    /** Closes reader activity between tests */
    @Rpc(description = "Close activity if one was opened.")
    public void closeActivity() {
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    /** Gets the URL received by the NDEF URL reader activity. */
    @Rpc(description = "Gets the URL received by the NDEF URL reader activity.")
    public String getReceivedUrl() {
        return NdefUrlReaderActivity.getReceivedUrl();
    }

    /** Checks if reader mode annotation is supported. */
    @Rpc(description = "Checks if reader mode annotation is supported.")
    public boolean isReaderModeAnnotationSupported() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        return adapter.isReaderModeAnnotationSupported();
    }

    /** Gets the gesture exchange AID. */
    @Rpc(description = "Gets the gesture exchange AID.")
    public String getGestureExchangeAid() {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        return adapter.getGestureExchangeAid();
    }

    /** Opens reader activity for NDEF ACTION_VIEW test */
    @Rpc(description = "Open reader activity for NDEF ACTION_VIEW test")
    public void startNdefActionViewReaderActivity() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), NdefUrlReaderActivity.class.getName());
        mActivity = (NdefUrlReaderActivity) instrumentation.startActivitySync(intent);
    }

    private Intent buildReaderIntentWithApduSequence(
            Instrumentation instrumentation, CommandApdu[] commandApdus, String[] responseApdus) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClassName(
                instrumentation.getTargetContext(), SimpleReaderActivity.class.getName());
        intent.putExtra(SimpleReaderActivity.EXTRA_APDUS, commandApdus);
        intent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES, responseApdus);
        return intent;
    }
}
