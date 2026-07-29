/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.sysprop.NfcProperties;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.nfc.DeviceConfigFacade;
import com.android.nfc.NfcService;
import com.android.nfc.R;
import com.android.nfc.cardemulation.util.TelephonyUtils;
import com.android.nfc.dhimpl.NativeNfcManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RoutingOptionManager {
    static final String TAG = "NfcRoutingOptionManager";
    static final boolean DBG = NfcProperties.debug_enabled().orElse(true);

    static final int ROUTE_UNKNOWN = -1;
    static final int ROUTE_DEFAULT = -2;

    public static final String DEVICE_HOST = "DH";
    public static final String SE_NDEF_NFCEE = "NDEF-NFCEE";
    public static final String SE_PREFIX_SIM = "SIM";
    public static final String SE_PREFIX_ESE = "eSE";

    public static final String PREF_ROUTING_OPTIONS = "RoutingOptionPrefs";
    public static final String KEY_DEFAULT_ROUTE = "default_route";
    public static final String KEY_DEFAULT_ISO_DEP_ROUTE = "default_iso_dep_route";
    public static final String KEY_DEFAULT_OFFHOST_ROUTE = "default_offhost_route";
    public static final String KEY_DEFAULT_FELICA_ROUTE = "default_felica_route";
    public static final String KEY_DEFAULT_SC_ROUTE = "default_sc_route";
    public static final String KEY_AUTO_CHANGE_CAPABLE = "allow_auto_routing_changed";
    Context mContext;
    private SharedPreferences mPrefs;

    int mDefaultRoute;
    int mDefaultIsoDepRoute;
    int mDefaultOffHostRoute;
    int mDefaultFelicaRoute;
    int mDefaultScRoute;
    int mNdefNfceeRoute;
    final byte[] mOffHostRouteUicc;
    final byte[] mOffHostRouteEse;
    final int mAidMatchingSupport;

    int mOverrideDefaultRoute = ROUTE_UNKNOWN;
    int mOverrideDefaultIsoDepRoute = ROUTE_UNKNOWN;
    int mOverrideDefaultOffHostRoute = ROUTE_UNKNOWN;
    int mOverrideDefaultFelicaRoute = ROUTE_UNKNOWN;
    int mOverrideDefaultScRoute = ROUTE_UNKNOWN;

    boolean mIsRoutingTableOverrided = false;

    boolean mIsAutoChangeCapable = true;
    boolean mIsUiccCapable = false;
    boolean mIsEseCapable = false;

    // Look up table for secure element name to route id
    HashMap<String, Integer> mRouteForSecureElement = new HashMap<>();

    // Look up table for route id to secure element name
    HashMap<Integer, String> mSecureElementForRoute = new HashMap<>();

    // Helper class for SIM routing values
    static class SimSettings {
        int type;
        int index;
        // Up to two eUICCs can be supported depending on whether MEP is supported or not.
        int[] eUiccIndexs = new int[2];
        SimSettings(int numberOfUicc, int startIndexOfEuicc) {
            type = TelephonyUtils.SIM_TYPE_UNKNOWN;
            index = 0;
            if (numberOfUicc != 0 && numberOfUicc > startIndexOfEuicc) {
                eUiccIndexs[0] = startIndexOfEuicc;
                eUiccIndexs[1] = (numberOfUicc > startIndexOfEuicc + 1) ?
                        startIndexOfEuicc + 1 : startIndexOfEuicc;
            }
        }
        String getName() {
            return SE_PREFIX_SIM + (index + 1);
        }
        void setType(int type) {
            this.type = type;
            setIndex();
        }
        private void setIndex() {
            switch (type) {
                case TelephonyUtils.SIM_TYPE_UICC:
                    index = 0;
                    break;
                case TelephonyUtils.SIM_TYPE_EUICC_1:
                    index = eUiccIndexs[0];
                    break;
                case TelephonyUtils.SIM_TYPE_EUICC_2:
                    index = eUiccIndexs[1];
                    break;
                default:
                    break;
            }
        }
    }

    SimSettings mPreferredSimSettings;

    int mMepMode;
    @VisibleForTesting
    native int doGetDefaultRouteDestination();
    @VisibleForTesting
    native int doGetDefaultIsoDepRouteDestination();
    @VisibleForTesting
    native int doGetDefaultOffHostRouteDestination();
    @VisibleForTesting
    native int doGetDefaultFelicaRouteDestination();
    @VisibleForTesting
    native int doGetDefaultScRouteDestination();
    @VisibleForTesting
    native byte[] doGetOffHostUiccDestination();
    @VisibleForTesting
    native byte[] doGetOffHostEseDestination();
    @VisibleForTesting
    native int doGetAidMatchingMode();
    native int doGetEuiccMepMode();

    private static RoutingOptionManager sInstance;

    public static RoutingOptionManager getInstance() {
        if (sInstance == null) {
            sInstance = new RoutingOptionManager();
        }
        return sInstance;
    }

    @VisibleForTesting
    RoutingOptionManager() {
        mDefaultRoute = doGetDefaultRouteDestination();
        if (DBG) {
            Log.d(TAG, "mDefaultRoute=0x" + Integer.toHexString(mDefaultRoute));
        }
        mDefaultIsoDepRoute = doGetDefaultIsoDepRouteDestination();
        if (DBG) {
            Log.d(TAG, "mDefaultIsoDepRoute=0x" + Integer.toHexString(mDefaultIsoDepRoute));
        }
        mDefaultOffHostRoute = doGetDefaultOffHostRouteDestination();
        if (DBG) {
            Log.d(TAG, "mDefaultOffHostRoute=0x" + Integer.toHexString(mDefaultOffHostRoute));
        }
        mDefaultFelicaRoute = doGetDefaultFelicaRouteDestination();
        if (DBG) {
            Log.d(TAG, "mDefaultFelicaRoute=0x" + Integer.toHexString(mDefaultFelicaRoute));
        }
        mDefaultScRoute = doGetDefaultScRouteDestination();
        if (DBG) {
            Log.d(TAG, "mDefaultScRoute=0x" + Integer.toHexString(mDefaultScRoute));
        }
        mOffHostRouteUicc = doGetOffHostUiccDestination();
        if (DBG) {
            Log.d(TAG, "mOffHostRouteUicc=" + Arrays.toString(mOffHostRouteUicc));
        }
        mOffHostRouteEse = doGetOffHostEseDestination();
        if (DBG) {
            Log.d(TAG, "mOffHostRouteEse=" + Arrays.toString(mOffHostRouteEse));
        }
        mAidMatchingSupport = doGetAidMatchingMode();
        if (DBG) {
            Log.d(TAG, "mAidMatchingSupport=0x" + Integer.toHexString(mAidMatchingSupport));
        }
        mNdefNfceeRoute = NativeNfcManager.getInstance().getNdefNfceeRouteId();
        if (DBG) {
            Log.d(TAG, "mNdefNfceeRoute=0x" + Integer.toHexString(mNdefNfceeRoute));
        }

        mPreferredSimSettings = new SimSettings((mOffHostRouteUicc != null) ?
                mOffHostRouteUicc.length : 0, 1);
        mMepMode = doGetEuiccMepMode();
        createLookUpTable();
    }

    public void overwriteRoutingTable() {
        Log.d(TAG, "overwriteRoutingTable()");
        if (mOverrideDefaultRoute != ROUTE_UNKNOWN) {
            if (mOverrideDefaultRoute == ROUTE_DEFAULT) {
                Log.i(TAG, "overwriteRoutingTable: overwrite mDefaultRoute with "
                        + "default config value");
                mDefaultRoute = doGetDefaultRouteDestination();
            } else {
                Log.d(TAG, "overwriteRoutingTable: mDefaultRoute : "
                    + Integer.toHexString(mOverrideDefaultRoute));
                mDefaultRoute = mOverrideDefaultRoute;
            }
            writeRoutingOption(KEY_DEFAULT_ROUTE, getSecureElementForRoute(mDefaultRoute));
        }

        if (mOverrideDefaultIsoDepRoute != ROUTE_UNKNOWN) {
            if (mOverrideDefaultIsoDepRoute == ROUTE_DEFAULT) {
                Log.i(TAG, "overwriteRoutingTable: overwrite mDefaultIsoDepRoute "
                        + "with default config value");
                mDefaultIsoDepRoute = doGetDefaultIsoDepRouteDestination();
            } else {
                Log.d(TAG, "overwriteRoutingTable: mDefaultIsoDepRoute : "
                        + Integer.toHexString(mOverrideDefaultIsoDepRoute));
                mDefaultIsoDepRoute = mOverrideDefaultIsoDepRoute;
            }
            writeRoutingOption(
                    KEY_DEFAULT_ISO_DEP_ROUTE, getSecureElementForRoute(mDefaultIsoDepRoute));
        }

        if (mOverrideDefaultOffHostRoute != ROUTE_UNKNOWN) {
            if (mOverrideDefaultOffHostRoute == ROUTE_DEFAULT) {
                Log.i(TAG, "overwriteRoutingTable: overwrite mDefaultOffHostRoute with "
                        + "default config value");
                mDefaultOffHostRoute = doGetDefaultOffHostRouteDestination();
            } else {
                Log.d(TAG, "overwriteRoutingTable: mDefaultOffHostRoute : "
                        + Integer.toHexString(mOverrideDefaultOffHostRoute));
                mDefaultOffHostRoute = mOverrideDefaultOffHostRoute;
            }
            writeRoutingOption(
                    KEY_DEFAULT_OFFHOST_ROUTE, getSecureElementForRoute(mDefaultOffHostRoute));
        }

        if (mOverrideDefaultFelicaRoute != ROUTE_UNKNOWN) {
            if (mOverrideDefaultFelicaRoute == ROUTE_DEFAULT) {
                Log.i(TAG, "overwriteRoutingTable: overwrite mDefaultFelicaRoute with "
                        + "default config value");
                mDefaultFelicaRoute = doGetDefaultFelicaRouteDestination();
            } else {
                Log.d(TAG, "overwriteRoutingTable: mDefaultFelicaRoute : "
                        + Integer.toHexString(mOverrideDefaultFelicaRoute));
                mDefaultFelicaRoute = mOverrideDefaultFelicaRoute;
            }
            writeRoutingOption(
                    KEY_DEFAULT_FELICA_ROUTE, getSecureElementForRoute(mDefaultFelicaRoute));
        }

        if (mOverrideDefaultScRoute != ROUTE_UNKNOWN) {
            if (mOverrideDefaultScRoute == ROUTE_DEFAULT) {
                Log.i(TAG, "overwriteRoutingTable: overwrite mDefaultScRoute with "
                        + "default config value");
                mDefaultScRoute = doGetDefaultScRouteDestination();
            } else {
                Log.d(TAG, "overwriteRoutingTable: mDefaultScRoute : "
                            + Integer.toHexString(mOverrideDefaultScRoute));
                mDefaultScRoute = mOverrideDefaultScRoute;
            }
            writeRoutingOption(
                    KEY_DEFAULT_SC_ROUTE, getSecureElementForRoute(mDefaultScRoute));
        }

        mOverrideDefaultRoute = mOverrideDefaultIsoDepRoute = mOverrideDefaultOffHostRoute =
                mOverrideDefaultScRoute = mOverrideDefaultFelicaRoute = ROUTE_UNKNOWN;
    }

    public void overrideDefaultRoute(int defaultRoute) {
        mOverrideDefaultRoute = defaultRoute;
    }

    public void overrideDefaultIsoDepRoute(int isoDepRoute) {
        mOverrideDefaultIsoDepRoute = isoDepRoute;
        if (isoDepRoute == ROUTE_DEFAULT) {
            isoDepRoute = doGetDefaultIsoDepRouteDestination();
        }
        NfcService.getInstance().setIsoDepProtocolRoute(isoDepRoute);
    }

    public void overrideDefaultOffHostRoute(int offHostRoute) {
        mOverrideDefaultOffHostRoute = offHostRoute;
        mOverrideDefaultFelicaRoute = offHostRoute;
        if (offHostRoute == ROUTE_DEFAULT) {
            offHostRoute = doGetDefaultOffHostRouteDestination();
        }
        NfcService.getInstance().setTechnologyABFRoute(offHostRoute, offHostRoute);
    }

    /**
     * Overwrite the default technolygy route destinations in the routing table
     *
     */
    public void overrideDefaultTechRoute(int abRoute, int fRoute) {
        mOverrideDefaultOffHostRoute = abRoute;
        mOverrideDefaultFelicaRoute = fRoute;
        if (abRoute == ROUTE_DEFAULT) {
            abRoute = doGetDefaultOffHostRouteDestination();
        }
        if (fRoute == ROUTE_DEFAULT) {
            fRoute = doGetDefaultFelicaRouteDestination();
        }
        NfcService.getInstance().setTechnologyABFRoute(abRoute, fRoute);
    }

    public void overrideDefaultScRoute(int scRoute) {
        mOverrideDefaultScRoute = scRoute;
        if (scRoute == ROUTE_DEFAULT) {
            scRoute = doGetDefaultScRouteDestination();
        }
        NfcService.getInstance().setSystemCodeRoute(scRoute);
    }

    public void recoverOverridedRoutingTable() {
        NfcService.getInstance().setIsoDepProtocolRoute(mDefaultIsoDepRoute);
        NfcService.getInstance().setTechnologyABFRoute(mDefaultOffHostRoute, mDefaultFelicaRoute);
        mOverrideDefaultRoute = mOverrideDefaultIsoDepRoute = mOverrideDefaultOffHostRoute =
            mOverrideDefaultFelicaRoute = ROUTE_UNKNOWN;
    }

    public int getOverrideDefaultRoute() {
        return mOverrideDefaultRoute;
    }

    public int getDefaultRoute() {
        return getAlternativeRouteIfSimIsInvalid(mDefaultRoute);
    }

    public int getOverrideDefaultIsoDepRoute() {
        return mOverrideDefaultIsoDepRoute;
    }

    public int getDefaultIsoDepRoute() {
        return getAlternativeRouteIfSimIsInvalid(mDefaultIsoDepRoute);
    }

    public int getOverrideDefaultOffHostRoute() {
        return mOverrideDefaultOffHostRoute;
    }

    public int getDefaultOffHostRoute() {
        return getAlternativeRouteIfSimIsInvalid(mDefaultOffHostRoute);
    }

    public int getDefaultFelicaRoute() {
        return mDefaultFelicaRoute;
    }

    public int getOverrideDefaultFelicaRoute() {
        return mOverrideDefaultFelicaRoute;
    }

    public int getOverrideDefaultScRoute() {
        return mOverrideDefaultScRoute;
    }

    public int getDefaultScRoute() {
        return mDefaultScRoute;
    }

    public byte[] getOffHostRouteUicc() {
        return mOffHostRouteUicc;
    }

    public byte[] getOffHostRouteEse() {
        return mOffHostRouteEse;
    }

    public int getAidMatchingSupport() {
        return mAidMatchingSupport;
    }

    public int getMepMode() { return mMepMode;}

    public boolean isRoutingTableOverrided() {
        return mOverrideDefaultRoute != ROUTE_UNKNOWN
            || mOverrideDefaultIsoDepRoute != ROUTE_UNKNOWN
            || mOverrideDefaultOffHostRoute != ROUTE_UNKNOWN
            || mOverrideDefaultFelicaRoute != ROUTE_UNKNOWN
            || mOverrideDefaultScRoute != ROUTE_UNKNOWN;
    }

    private void createLookUpTable() {
        mRouteForSecureElement.putIfAbsent(DEVICE_HOST, 0);
        mSecureElementForRoute.put(0, DEVICE_HOST);

        mRouteForSecureElement.putIfAbsent("UNKNOWN", ROUTE_UNKNOWN);
        mSecureElementForRoute.put(ROUTE_UNKNOWN, "UNKNOWN");

        mRouteForSecureElement.putIfAbsent("default", ROUTE_DEFAULT);
        mSecureElementForRoute.put(ROUTE_DEFAULT, "default");

        mRouteForSecureElement.putIfAbsent(SE_NDEF_NFCEE, mNdefNfceeRoute);
        mSecureElementForRoute.put(mNdefNfceeRoute, SE_NDEF_NFCEE);

        addOrUpdateTableItems(SE_PREFIX_SIM, mOffHostRouteUicc);
        addOrUpdateTableItems(SE_PREFIX_ESE, mOffHostRouteEse);

        for (Map.Entry<String, Integer> entry : mRouteForSecureElement.entrySet()) {
            Log.d(TAG, "createLookUpTable: route=" + entry.getKey() + ", nfceeId="
                    + Integer.toHexString(entry.getValue()));
        }
        for (Map.Entry<Integer, String> entry : mSecureElementForRoute.entrySet()) {
            Log.d(TAG, "createLookUpTable: nfceeId=" + Integer.toHexString(entry.getKey())
                    + ", route=" + entry.getValue());
        }
    }

    boolean isRoutingTableOverwrittenOrOverlaid(
            DeviceConfigFacade deviceConfigFacade, SharedPreferences prefs) {
        return !TextUtils.isEmpty(deviceConfigFacade.getDefaultRoute())
                || !TextUtils.isEmpty(deviceConfigFacade.getDefaultIsoDepRoute())
                || !TextUtils.isEmpty(deviceConfigFacade.getDefaultOffHostRoute())
                || !TextUtils.isEmpty(deviceConfigFacade.getDefaultFelicaRoute())
                || !TextUtils.isEmpty(deviceConfigFacade.getDefaultScRoute())
                || !prefs.getAll().isEmpty();
    }

    public void readRoutingOptionsFromPrefs(
            Context context, DeviceConfigFacade deviceConfigFacade) {
        Log.d(TAG, "readRoutingOptionsFromPrefs");
        if (mPrefs == null) {
            Log.d(TAG, "readRoutingOptionsFromPrefs: create mPrefs in readRoutingOptions");
            mContext = context;
            mPrefs = context.getSharedPreferences(PREF_ROUTING_OPTIONS, Context.MODE_PRIVATE);
            mIsUiccCapable = context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_UICC);
            mIsEseCapable = context.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_ESE);
        }

        // If the OEM does not set default routes in the overlay and if no app has overwritten
        // the routing table using `overwriteRoutingTable`, skip this preference reading.
        if (!isRoutingTableOverwrittenOrOverlaid(deviceConfigFacade, mPrefs)) {
            Log.d(TAG, "readRoutingOptionsFromPrefs: Routing table not overwritten or overlaid");
            return;
        }

        // read default route
        if (!mPrefs.contains(KEY_DEFAULT_ROUTE) || prefsIsEmptyStr(KEY_DEFAULT_ROUTE)) {
            writeRoutingOption(KEY_DEFAULT_ROUTE, deviceConfigFacade.getDefaultRoute());
        }
        mDefaultRoute = getRouteForSecureElement(mPrefs.getString(KEY_DEFAULT_ROUTE, null));

        // read default iso dep route
        if (!mPrefs.contains(KEY_DEFAULT_ISO_DEP_ROUTE)
                || prefsIsEmptyStr(KEY_DEFAULT_ISO_DEP_ROUTE)) {
            writeRoutingOption(
                KEY_DEFAULT_ISO_DEP_ROUTE, deviceConfigFacade.getDefaultIsoDepRoute());
        }
        mDefaultIsoDepRoute =
                getRouteForSecureElement(mPrefs.getString(KEY_DEFAULT_ISO_DEP_ROUTE, null));

        // read default offhost route
        if (!mPrefs.contains(KEY_DEFAULT_OFFHOST_ROUTE)
                || prefsIsEmptyStr(KEY_DEFAULT_OFFHOST_ROUTE)) {
            writeRoutingOption(
                KEY_DEFAULT_OFFHOST_ROUTE, deviceConfigFacade.getDefaultOffHostRoute());
        }
        mDefaultOffHostRoute =
                getRouteForSecureElement(mPrefs.getString(KEY_DEFAULT_OFFHOST_ROUTE, null));

        // read default felica route
        if (!mPrefs.contains(KEY_DEFAULT_FELICA_ROUTE)
                || prefsIsEmptyStr(KEY_DEFAULT_FELICA_ROUTE)) {
            writeRoutingOption(
                    KEY_DEFAULT_FELICA_ROUTE, deviceConfigFacade.getDefaultFelicaRoute());
        }
        mDefaultFelicaRoute =
                getRouteForSecureElement(mPrefs.getString(KEY_DEFAULT_FELICA_ROUTE, null));

        // read default system code route
        if (!mPrefs.contains(KEY_DEFAULT_SC_ROUTE) || prefsIsEmptyStr(KEY_DEFAULT_SC_ROUTE)) {
            writeRoutingOption(
                KEY_DEFAULT_SC_ROUTE, deviceConfigFacade.getDefaultScRoute());
        }
        mDefaultScRoute = getRouteForSecureElement(mPrefs.getString(KEY_DEFAULT_SC_ROUTE, null));

        // read auto change capable
        if (!mPrefs.contains(KEY_AUTO_CHANGE_CAPABLE)) {
            writeRoutingOption(KEY_AUTO_CHANGE_CAPABLE, true);
        }
        mIsAutoChangeCapable = mPrefs.getBoolean(KEY_AUTO_CHANGE_CAPABLE, true);
        Log.d(TAG, "readRoutingOptionsFromPrefs: ReadOptions - " + toString());
    }

    public void setAutoChangeStatus(boolean status) {
        mIsAutoChangeCapable = status;
        writeRoutingOption(KEY_AUTO_CHANGE_CAPABLE, mIsAutoChangeCapable);
    }

    public boolean isAutoChangeEnabled() {
        return mIsAutoChangeCapable;
    }

    private void writeRoutingOption(String key, String name) {
        // Fallback to default if the string is empty
        if (!TextUtils.isEmpty(name)) {
            mPrefs.edit().putString(key, name).apply();
            Log.d(TAG, "writeRoutingOption: Add " + key + ":" + name + " to mPrefs.");
            return;
        }
        int route = switch (key) {
            case KEY_DEFAULT_ROUTE -> doGetDefaultRouteDestination();
            case KEY_DEFAULT_ISO_DEP_ROUTE -> doGetDefaultIsoDepRouteDestination();
            case KEY_DEFAULT_OFFHOST_ROUTE -> doGetDefaultOffHostRouteDestination();
            case KEY_DEFAULT_FELICA_ROUTE -> doGetDefaultFelicaRouteDestination();
            case KEY_DEFAULT_SC_ROUTE -> doGetDefaultScRouteDestination();
            default -> {
                Log.e(TAG, "writeRoutingOption: Unexpected key:" + key);
                yield ROUTE_UNKNOWN;
            }
        };
        if (route == ROUTE_UNKNOWN) {
            return;
        }
        mPrefs.edit().putString(key, getSecureElementForRoute(route)).apply();
        Log.d(TAG, "writeRoutingOption: Add default " + key + ":"
                + getSecureElementForRoute(route) + " to mPrefs.");
    }

    private void writeRoutingOption(String key, boolean value) {
        mPrefs.edit().putBoolean(key, value).apply();
    }

    public int getRouteForSecureElement(String se) {
        boolean telephonySubscriptionEnabled = mContext.getResources().getBoolean(
                R.bool.telephony_subscription_routing_enabled);
        if (telephonySubscriptionEnabled) {
            return Optional.ofNullable(mRouteForSecureElement.get(renameSecureElementIfSimType(se)))
                    .orElseGet(() -> 0x00);
        } else {
            if (se == null || se.length() <= 3) {
                Log.e(TAG, "getRouteForSecureElement invalid input, fallback to DEVICE_HOST");
                return 0;
            }
            try {
                if (se.startsWith("eSE") && mOffHostRouteEse != null) {
                    int index = Integer.parseInt(se.substring(3));
                    if (mOffHostRouteEse.length >= index && index > 0) {
                        return mOffHostRouteEse[index - 1] & 0xFF;
                    }
                } else if (se.startsWith("SIM") && mOffHostRouteUicc != null) {
                    int index = Integer.parseInt(se.substring(3));
                    if (mOffHostRouteUicc.length >= index && index > 0) {
                        return mOffHostRouteUicc[index - 1] & 0xFF;
                    }
                } else if (se.equals(SE_NDEF_NFCEE)) {
                    return Optional.ofNullable(mRouteForSecureElement.get(se))
                            .orElseGet(() -> 0x00);
                }
                if (mOffHostRouteEse == null && mOffHostRouteUicc == null) {
                    return mDefaultOffHostRoute;
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "NumberFormatException while parsing secure element index", e);
            }
            Log.e(TAG, "getRouteForSecureElement no match, fallback to DEVICE_HOST");
            return 0;
        }
    }

    public String getSecureElementForRoute(int route) {
        return Optional.ofNullable(mSecureElementForRoute.get(route)).orElseGet(()->DEVICE_HOST);
    }

    // Implement eSIM support
    public void onPreferredSimChanged(int simType) {
        mPreferredSimSettings.setType(simType);
    }
    public String getPreferredSim() {
        return mPreferredSimSettings.getName();
    }
    public String renameSecureElementIfSimType(String seName) {
        return seName.startsWith(SE_PREFIX_SIM) ? mPreferredSimSettings.getName() : seName;
    }
    private int getAlternativeRouteIfSimIsInvalid(int route) {
        // TODO - Implement
        if (getSecureElementForRoute(route).startsWith(SE_PREFIX_SIM)) {
            boolean telephonySubscriptionEnabled = mContext.getResources().getBoolean(
                    R.bool.telephony_subscription_routing_enabled);
            if (telephonySubscriptionEnabled) {
                if (mPreferredSimSettings.type == TelephonyUtils.SIM_TYPE_UNKNOWN) {
                    Log.e(TAG, "getAlternativeRouteIfSimIsInvalid: sim " + route + " is invalid");
                    return getRouteForSecureElement(mIsEseCapable
                            ? (SE_PREFIX_ESE + 1) : DEVICE_HOST);
                }
            }
        }
        return route;
    }
    // Implement eSIM support
    private void addOrUpdateTableItems(String prefix, byte[] routes) {
        if (routes != null && routes.length != 0) {
            for (int index = 1; index <= routes.length; index++) {
                int route = routes[index - 1] & 0xFF;
                String name = prefix + index;
                mRouteForSecureElement.putIfAbsent(name, route);
                mSecureElementForRoute.putIfAbsent(route, name);
            }
        }
    }

    private boolean prefsIsEmptyStr(String key) {
        return TextUtils.isEmpty(mPrefs.getString(key, null));
    }
}
