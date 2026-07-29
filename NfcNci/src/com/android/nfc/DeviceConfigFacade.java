/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.Context;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;

/**
 * This class allows getting all configurable flags from DeviceConfig.
 */
public class DeviceConfigFacade {
    // TODO: Temporary hack to copy string from DeviceConfig.NAMESPACE_NFC. Use API constant
    // once build problems are resolved.
    private static final String DEVICE_CONFIG_NAMESPACE_NFC = "nfc";

    private final Context mContext;

    // Cached values of fields updated via updateDeviceConfigFlags()
    private boolean mAntennaBlockedAlertEnabled;

    public static final String KEY_READER_OPTION_DEFAULT = "reader_option_default";
    public static final String KEY_ENABLE_NFC_DEFAULT = "enable_nfc_default";
    public static final String KEY_ENABLE_READER_OPTION_SUPPORT = "enable_reader_option_support";
    public static final String KEY_SECURE_NFC_CAPABLE = "enable_secure_nfc_support";
    public static final String KEY_SECURE_NFC_DEFAULT = "secure_nfc_default";
    public static final String KEY_SLOW_TAP_THRESHOLD_MILLIS = "slow_tap_threshold_millis";

    private boolean mNfcDefaultState;
    private boolean mReaderOptionSupport;
    private boolean mReaderOptionDefault;
    private boolean mSecureNfcCapable;
    private boolean mSecureNfcDefault;
    private boolean mEnableAutoPlay;
    private boolean mPollingDisableAllowed;
    private boolean mNfccAlwaysOnAllowed;
    private boolean mEnableServiceOther;
    private boolean mTagIntentAppPrefSupported;
    private boolean mProprietaryGetcapsSupported;
    private boolean mEnableOemExtension;
    private boolean mEnableDeveloperNotification;
    private boolean mCheckDisplayStateForScreenState;
    private boolean mIndicateUserActivityForHce;
    private String mDefaultRoute;
    private String mDefaultIsoDepRoute;
    private String mDefaultOffHostRoute;
    private String mDefaultFelicaRoute;
    private String mDefaultScRoute;
    private int mSlowTapThresholdMillis;
    private int mUnknownTagPollingDelay;
    private int mUnknownTagPollingDelayMax;
    private int mUnknownTagPollingDelayLong;
    private boolean mCeDisableOtherServicesOnManagedProfiles;
    private int mCeWakeLockTimeoutMillis;
    private String[] mOverwriteRoutingTableAllowListPkgs;
    private boolean mDefaultPreferredSubscriptionToUicc;
    private boolean mSeparateOffhostFelicaRouting;

    private static DeviceConfigFacade sInstance;
    public static DeviceConfigFacade getInstance(Context context, Handler handler) {
        if (sInstance == null) {
            sInstance = new DeviceConfigFacade(context, handler);
        }
        return sInstance;
    }

    @VisibleForTesting
    public DeviceConfigFacade(Context context, Handler handler) {
        mContext = context;
        updateDeviceConfigFlags();
        DeviceConfig.addOnPropertiesChangedListener(
                DEVICE_CONFIG_NAMESPACE_NFC,
                command -> handler.post(command),
                properties -> {
                    updateDeviceConfigFlags();
                });

        sInstance = this;
    }

    private void updateDeviceConfigFlags() {
        mAntennaBlockedAlertEnabled = DeviceConfig.getBoolean(DEVICE_CONFIG_NAMESPACE_NFC,
                "enable_antenna_blocked_alert",
                mContext.getResources().getBoolean(R.bool.enable_antenna_blocked_alert));

        mNfcDefaultState = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
            KEY_ENABLE_NFC_DEFAULT,
            mContext.getResources().getBoolean(R.bool.enable_nfc_default));

        mReaderOptionSupport = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
            KEY_ENABLE_READER_OPTION_SUPPORT,
            mContext.getResources().getBoolean(R.bool.enable_reader_option_support));

        mReaderOptionDefault = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
            KEY_READER_OPTION_DEFAULT,
            mContext.getResources().getBoolean(R.bool.reader_option_default));

        mSecureNfcCapable = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
            KEY_SECURE_NFC_CAPABLE, isSecureNfcCapableDefault());

        mSecureNfcDefault = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
            KEY_SECURE_NFC_DEFAULT,
            mContext.getResources().getBoolean(R.bool.secure_nfc_default));

        mEnableAutoPlay = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "enable_auto_play",
                mContext.getResources().getBoolean(R.bool.enable_auto_play));

        mPollingDisableAllowed = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "polling_disable_allowed",
                mContext.getResources().getBoolean(R.bool.polling_disable_allowed));

        mNfccAlwaysOnAllowed = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "nfcc_always_on_allowed",
                mContext.getResources().getBoolean(R.bool.nfcc_always_on_allowed));

        mEnableServiceOther = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "enable_service_for_category_other",
                mContext.getResources().getBoolean(R.bool.enable_service_for_category_other));

        mTagIntentAppPrefSupported = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "tag_intent_app_pref_supported",
                mContext.getResources().getBoolean(R.bool.tag_intent_app_pref_supported));

        mProprietaryGetcapsSupported = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "nfc_proprietary_getcaps_supported",
                mContext.getResources().getBoolean(R.bool.nfc_proprietary_getcaps_supported));

        mEnableOemExtension = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "enable_oem_extension",
                mContext.getResources().getBoolean(R.bool.enable_oem_extension));

        mEnableDeveloperNotification = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "enable_developer_option_notification",
                mContext.getResources().getBoolean(R.bool.enable_developer_option_notification));

        mCheckDisplayStateForScreenState = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "check_display_state_for_screen_state",
                mContext.getResources().getBoolean(R.bool.check_display_state_for_screen_state));

        mIndicateUserActivityForHce = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "indicate_user_activity_for_hce",
                mContext.getResources().getBoolean(R.bool.indicate_user_activity_for_hce));

        mDefaultRoute = DeviceConfig.getString(DEVICE_CONFIG_NAMESPACE_NFC,
                "nfc_default_route",
                mContext.getResources().getString(R.string.nfc_default_route));

        mDefaultIsoDepRoute = DeviceConfig.getString(DEVICE_CONFIG_NAMESPACE_NFC,
                "nfc_default_isodep_route",
                mContext.getResources().getString(R.string.nfc_default_isodep_route));

        mDefaultOffHostRoute = DeviceConfig.getString(DEVICE_CONFIG_NAMESPACE_NFC,
                "nfc_default_offhost_route",
                mContext.getResources().getString(R.string.nfc_default_offhost_route));

        mDefaultFelicaRoute = DeviceConfig.getString(DEVICE_CONFIG_NAMESPACE_NFC,
                "nfc_default_felica_route",
                mContext.getResources().getString(R.string.nfc_default_felica_route));

        mDefaultScRoute = DeviceConfig.getString(DEVICE_CONFIG_NAMESPACE_NFC,
                "nfc_default_sc_route",
                mContext.getResources().getString(R.string.nfc_default_sc_route));

        mSlowTapThresholdMillis = DeviceConfig.getInt(DEVICE_CONFIG_NAMESPACE_NFC,
                KEY_SLOW_TAP_THRESHOLD_MILLIS,
                mContext.getResources().getInteger(R.integer.slow_tap_threshold_millis));

        mUnknownTagPollingDelay = DeviceConfig.getInt(DEVICE_CONFIG_NAMESPACE_NFC,
                "unknown_tag_polling_delay",
                mContext.getResources().getInteger(R.integer.unknown_tag_polling_delay));

        mUnknownTagPollingDelayMax = DeviceConfig.getInt(DEVICE_CONFIG_NAMESPACE_NFC,
                "unknown_tag_polling_delay_count_max",
                mContext.getResources().getInteger(R.integer.unknown_tag_polling_delay_count_max));

        mUnknownTagPollingDelayLong = DeviceConfig.getInt(DEVICE_CONFIG_NAMESPACE_NFC,
                "unknown_tag_polling_delay_long",
                mContext.getResources().getInteger(R.integer.unknown_tag_polling_delay_long));
        mCeDisableOtherServicesOnManagedProfiles = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_NFC,
                "ce_disable_other_services_on_managed_profiles",
                mContext.getResources().getBoolean(R.bool.ce_disable_other_services_on_managed_profiles));
        mCeWakeLockTimeoutMillis = DeviceConfig.getInt(DEVICE_CONFIG_NAMESPACE_NFC,
                "ce_wake_lock_timeout_millis",
                mContext.getResources().getInteger(R.integer.ce_wake_lock_timeout_millis));
        // device config override with array is not supported, so just read the resource.
        mOverwriteRoutingTableAllowListPkgs = mContext.getResources()
            .getStringArray(R.array.overwrite_routing_table_allow_list_pkgs);
        mDefaultPreferredSubscriptionToUicc = DeviceConfig.getBoolean(DEVICE_CONFIG_NAMESPACE_NFC,
                "default_preferred_subscription_to_uicc",
                mContext.getResources().getBoolean(R.bool.default_preferred_subscription_to_uicc));
        mSeparateOffhostFelicaRouting = DeviceConfig.getBoolean(
                DEVICE_CONFIG_NAMESPACE_NFC, "separate_offhost_felica_routing",
                mContext.getResources().getBoolean(R.bool.separate_offhost_felica_routing));
    }

    private boolean isSecureNfcCapableDefault() {
        if (mContext.getResources().getBoolean(R.bool.enable_secure_nfc_support)) {
            return true;
        }
        String[] skuList = mContext.getResources().getStringArray(
                R.array.config_skuSupportsSecureNfc);
        String sku = SystemProperties.get("ro.boot.hardware.sku");
        if (TextUtils.isEmpty(sku) || !Utils.arrayContains(skuList, sku)) {
            return false;
        }
        return true;
    }


    /**
     * Get whether antenna blocked alert is enabled or not.
     */
    public boolean isAntennaBlockedAlertEnabled() {
        return mAntennaBlockedAlertEnabled;
    }

    public boolean getNfcDefaultState() {
        return mNfcDefaultState;
    }
    public boolean isReaderOptionCapable() {
        return mReaderOptionSupport;
    }
    public boolean getDefaultReaderOption() {
        return mReaderOptionDefault;
    }
    public boolean isSecureNfcCapable() {
        return mSecureNfcCapable;
    }
    public boolean getDefaultSecureNfcState() {
        return mSecureNfcDefault;
    }
    public void setDefaultSecureNfcState(boolean SecureNfcDefault) {
        mSecureNfcDefault = SecureNfcDefault;
    }
    public boolean getEnableAutoPlay() { return mEnableAutoPlay; }
    public boolean getPollingDisableAllowed() { return mPollingDisableAllowed; }
    public boolean getNfccAlwaysOnAllowed() { return mNfccAlwaysOnAllowed; }
    public boolean getEnableServiceOther() { return mEnableServiceOther; }
    public boolean getTagIntentAppPrefSupported() { return mTagIntentAppPrefSupported; }
    public boolean getProprietaryGetcapsSupported() { return mProprietaryGetcapsSupported; }
    public boolean getEnableOemExtension() { return mEnableOemExtension; }
    public boolean getEnableDeveloperNotification() { return mEnableDeveloperNotification; }
    public boolean getCheckDisplayStateForScreenState() { return mCheckDisplayStateForScreenState; }
    public boolean getIndicateUserActivityForHce() { return mIndicateUserActivityForHce; }
    public boolean shouldDefaultPreferredSubscriptionToUicc() {
        return mDefaultPreferredSubscriptionToUicc;
    }
    /**
     * Checks separate_offhost_felica_routing overlay value
     */
    public boolean shouldSeparateOffhostFelicaRouting() {
        return mSeparateOffhostFelicaRouting;
    }
    public String getDefaultRoute() {
        return mDefaultRoute;
    }
    public String getDefaultIsoDepRoute() {
        return mDefaultIsoDepRoute;
    }
    public String getDefaultOffHostRoute() {
        return mDefaultOffHostRoute;
    }
    public String getDefaultFelicaRoute() {
        return mDefaultFelicaRoute;
    }
    public String getDefaultScRoute() {
        return mDefaultScRoute;
    }
    public int getSlowTapThresholdMillis() {
        return mSlowTapThresholdMillis;
    }
    public int getUnknownTagPollingDelay() { return mUnknownTagPollingDelay; }
    public int getUnknownTagPollingDelayMax() { return mUnknownTagPollingDelayMax; }
    public int getUnknownTagPollingDelayLong() { return mUnknownTagPollingDelayLong; }

    public boolean getCeDisableOtherServicesOnManagedProfiles() {
        return mCeDisableOtherServicesOnManagedProfiles;
    }

    public int getCeWakeLockTimeoutMillis() {
        return mCeWakeLockTimeoutMillis;
    }

    public String[] getOverwriteRoutingTableAllowListPkgs() {
        return mOverwriteRoutingTableAllowListPkgs;
    }
}
