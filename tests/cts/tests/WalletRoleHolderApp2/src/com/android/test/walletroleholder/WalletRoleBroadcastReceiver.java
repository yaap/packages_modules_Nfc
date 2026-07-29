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

package com.android.test.walletroleholder2;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;

public class WalletRoleBroadcastReceiver extends BroadcastReceiver
        implements CardEmulation.NfcEventCallback {
    Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        String action = intent.getAction();
        switch (action) {
            case "com.cts.RegisterEventListener":
                cardEmulation.registerNfcEventCallback(context.getMainExecutor(), this);
                break;
            case "com.cts.UnregisterEventListener":
                cardEmulation.unregisterNfcEventCallback(this);
                break;
            case "com.cts.SetShouldDefaultToObserveModeForService":
                cardEmulation.setShouldDefaultToObserveModeForService(
                    new ComponentName(mContext, WalletRoleHolderApduService.class), true);
                break;
            case "com.cts.UnsetShouldDefaultToObserveModeForService":
                cardEmulation.setShouldDefaultToObserveModeForService(
                    new ComponentName(mContext, WalletRoleHolderApduService.class), false);
                break;
        }
    }

    @Override
    public void onObserveModeStateChanged(boolean isEnabled) {
        final Intent intent = new Intent();
        intent.setAction("com.cts.ObserveModeChanged");
        intent.putExtra("class_name", this.getClass().getPackageName());
        intent.putExtra("enabled", isEnabled);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setComponent(
                new ComponentName(
                        "android.nfc.cts", "android.nfc.cts.PollingLoopBroadcastReceiver"));
        mContext.sendBroadcast(intent);
    }

    @Override
    public void onPreferredServiceChanged(boolean isPreferred) {
        final Intent intent = new Intent();
        intent.setAction("com.cts.PreferredServiceChanged");
        intent.putExtra("class_name", this.getClass().getPackageName());
        intent.putExtra("preferred", isPreferred);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setComponent(
                new ComponentName(
                        "android.nfc.cts", "android.nfc.cts.PollingLoopBroadcastReceiver"));
        mContext.sendBroadcast(intent);
    }
}
