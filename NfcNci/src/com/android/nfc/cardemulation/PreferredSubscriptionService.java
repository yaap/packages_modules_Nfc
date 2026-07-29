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
package com.android.nfc.cardemulation;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.util.Log;

import com.android.nfc.DeviceConfigFacade;
import com.android.nfc.R;
import com.android.nfc.cardemulation.util.TelephonyUtils;

import java.util.List;
import java.util.stream.Collectors;

public class PreferredSubscriptionService implements TelephonyUtils.Callback {
    static final String TAG = "NFCPreferredSubscriptionService";
    static final String PREF_SUBSCRIPTION = "SubscriptionPref";
    static final String PREF_PREFERRED_SUB_ID = "pref_sub_id";
    private SharedPreferences mSubscriptionPrefs = null;

    Context mContext;
    Callback mCallback;

    int mDefaultSubscriptionId = TelephonyUtils.SUBSCRIPTION_ID_UNKNOWN;
    boolean mIsEuiccCapable;
    boolean mIsUiccCapable;
    TelephonyUtils mTelephonyUtils;
    int mActiveSubscriptoinState = TelephonyUtils.SUBSCRIPTION_STATE_UNKNOWN;
    List<SubscriptionInfo> mActiveSubscriptions = null;
    boolean mTelephonySubscriptionRouting = true;

    public interface Callback {
        void onPreferredSubscriptionChanged(int subscriptionId, boolean isActive);
    }

    public PreferredSubscriptionService(
            Context context, DeviceConfigFacade deviceConfigFacade, Callback callback
    ) {
        mContext = context;
        mCallback = callback;

        mIsUiccCapable = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_UICC);
        mIsEuiccCapable = mContext.getResources().getBoolean(R.bool.enable_euicc_support);

        mTelephonyUtils = TelephonyUtils.getInstance(context);
        mSubscriptionPrefs = mContext.getSharedPreferences(
                PREF_SUBSCRIPTION, Context.MODE_PRIVATE);

        // Initialize default subscription to UICC if there is no preference
        if (mIsUiccCapable || mIsEuiccCapable) {
            mDefaultSubscriptionId = getPreferredSubscriptionId();
            if (deviceConfigFacade.shouldDefaultPreferredSubscriptionToUicc()
                    && mDefaultSubscriptionId == TelephonyUtils.SUBSCRIPTION_ID_UNKNOWN
            ) {
                Log.d(TAG, "Set preferred subscription to UICC forcely, because currently unknown"
                    + " state");
                setPreferredSubscriptionId(TelephonyUtils.SUBSCRIPTION_ID_UICC, false);
            }
        }
    }

    public void initialize() {
        mTelephonySubscriptionRouting = mContext.getResources().getBoolean(
                R.bool.telephony_subscription_routing_enabled);
        if (mIsUiccCapable || mIsEuiccCapable) {
            onDefaultSubscriptionChanged();
            if (mTelephonySubscriptionRouting) {
                Log.d(TAG, "initialize: Registering telephony subscription callback");
                mTelephonyUtils.registerSubscriptionChangedCallback(this);
            } else {
                Log.d(TAG, "initialize: Skip registering telephony subscription callback");
            }
        }
    }

    public int getPreferredSubscriptionId() {
        Log.d(TAG, "getPreferredSubscriptionId: " + mDefaultSubscriptionId);
        return mSubscriptionPrefs.getInt(
                PREF_PREFERRED_SUB_ID, TelephonyUtils.SUBSCRIPTION_ID_UNKNOWN);
    }

    public void setPreferredSubscriptionId(int subscriptionId, boolean force) {
        Log.d(TAG, "setPreferredSubscriptionId: " + subscriptionId);
        if (mDefaultSubscriptionId != subscriptionId) {
            mDefaultSubscriptionId = subscriptionId;
            mSubscriptionPrefs.edit().putInt(PREF_PREFERRED_SUB_ID, subscriptionId).commit();
            if (force) {
                onDefaultSubscriptionChanged();
            }
        }
    }

    public void onDefaultSubscriptionChanged() {
        Log.d(TAG, "onDefaultSubscriptionChanged: ");
        boolean isSubscriptionActivated = isSubscriptionActivated(mDefaultSubscriptionId);
        mActiveSubscriptoinState = isSubscriptionActivated ?
                TelephonyUtils.SUBSCRIPTION_STATE_ACTIVATE
                : TelephonyUtils.SUBSCRIPTION_STATE_INACTIVATE;
        mCallback.onPreferredSubscriptionChanged(mDefaultSubscriptionId, isSubscriptionActivated);
    }

    @Override
    public void onActiveSubscriptionsUpdated(List<SubscriptionInfo> activeSubscriptionList) {
        if (checkSubscriptionStateChanged(activeSubscriptionList)) {
            mCallback.onPreferredSubscriptionChanged(mDefaultSubscriptionId,
                    mActiveSubscriptoinState == TelephonyUtils.SUBSCRIPTION_STATE_ACTIVATE);
        } else {
            Log.i(TAG, "onActiveSubscriptionsUpdated: Active Subscription is not changed");
        }
    }

    private boolean isSubscriptionActivated(int subscriptionId) {
        if (mActiveSubscriptions == null) {
            Log.d(TAG, "isSubscriptionActivated: Get active subscriptions because list is empty");
            mActiveSubscriptions = mTelephonyUtils.getActiveSubscriptions().stream()
                    .filter(TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_UICC
                            .or(TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_EUICC))
                    .collect(Collectors.toList());
        }

        if (mTelephonyUtils.isUiccSubscription(subscriptionId)) {
            Log.d(TAG, "isSubscriptionActivated: Check uicc subscription activated status"
                    + " with SWP supported physical slot");
            return mActiveSubscriptions.stream()
                    .filter(TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_UICC)
                    .anyMatch(subscriptionInfo ->
                                  mTelephonyUtils.findPhysicalSlotIndex(subscriptionInfo)
                                  == TelephonyUtils.SWP_SUPPORTED_PHYSICAL_SIM_SLOT);
        } else {
            return mActiveSubscriptions.stream()
                    .filter(TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_EUICC)
                    .anyMatch(subscriptionInfo ->
                                  subscriptionInfo.getSubscriptionId() == subscriptionId);
        }
    }

    private boolean checkSubscriptionStateChanged(List<SubscriptionInfo> activeSubscriptionList) {
        // filtered subscriptions
        mActiveSubscriptions = activeSubscriptionList.stream()
                .filter(TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_UICC
                        .or(TelephonyUtils.SUBSCRIPTION_ACTIVE_CONDITION_FOR_EUICC))
                .collect(Collectors.toList());
        int previousActiveSubscriptionState = mActiveSubscriptoinState;
        int currentActiveSubscriptionState = isSubscriptionActivated(mDefaultSubscriptionId) ?
                TelephonyUtils.SUBSCRIPTION_STATE_ACTIVATE :
                TelephonyUtils.SUBSCRIPTION_STATE_INACTIVATE;
        if (previousActiveSubscriptionState != currentActiveSubscriptionState) {
            Log.d(TAG, "checkSubscriptionStateChanged: active subscription state changed "
                    + previousActiveSubscriptionState + " to " + currentActiveSubscriptionState);
            mActiveSubscriptoinState = currentActiveSubscriptionState;
            return true;
        }
        return false;
    }
}
