/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.nfc.tech.Ndef.EXTRA_NDEF_MSG;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProtoEnums;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.nfc.INfcOemExtensionCallback;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcBarcode;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.util.proto.ProtoOutputStream;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.nfc.handover.HandoverDataParser;
import com.android.nfc.handover.PeripheralHandoverService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


@RunWith(AndroidJUnit4.class)
public final class NfcDispatcherTest {

    private static final String TAG = NfcDispatcherTest.class.getSimpleName();
    @Mock
    private NfcInjector mNfcInjector;
    private MockitoSession mStaticMockSession;
    private NfcDispatcher mNfcDispatcher;
    TestLooper mLooper;

    @Mock
    private Context mockContext;
    @Mock
    private Resources mResources;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private PowerManager mPowerManager;
    @Mock
    KeyguardManager mKeyguardManager;
    @Mock
    DisplayManager mDisplayManager;
    @Mock
    UserManager mUserManager;
    @Mock
    BluetoothManager mBluetoothManager;
    @Mock
    BluetoothAdapter mBluetoothAdapter;
    @Mock
    ActivityManager mActivityManager;
    @Mock
    NfcAdapter mNfcAdapter;
    @Mock
    ForegroundUtils mForegroundUtils;
    @Mock
    AtomicBoolean mAtomicBoolean;
    @Mock
    DeviceConfigFacade mDeviceConfigFacade;
    @Mock
    NfcTagAllowNotification mNfcTagAllowNotification;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        mLooper = new TestLooper();
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .mockStatic(NfcStatsLog.class)
                .mockStatic(android.nfc.Flags.class)
                .mockStatic(com.android.nfc.module.flags.Flags.class)
                .mockStatic(NfcAdapter.class)
                .mockStatic(Ndef.class)
                .mockStatic(ForegroundUtils.class)
                .mockStatic(NfcWifiProtectedSetup.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        MockitoAnnotations.initMocks(this);

        when(mPowerManager.isInteractive()).thenReturn(false);
        when(mockContext.getSystemService(PowerManager.class)).thenReturn(mPowerManager);
        when(mockContext.getSystemService(KeyguardManager.class)).thenReturn(mKeyguardManager);
        when(mockContext.getSystemService(DisplayManager.class)).thenReturn(mDisplayManager);
        when(mockContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mockContext.getSystemService(BluetoothManager.class)).thenReturn(mBluetoothManager);
        when(mBluetoothManager.getAdapter()).thenReturn(mBluetoothAdapter);
        when(mockContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        when(ForegroundUtils.getInstance(mActivityManager)).thenReturn(mForegroundUtils);
        when(mockContext.createPackageContextAsUser(anyString(), anyInt(), any()))
                .thenReturn(mockContext);
        when(mockContext.createContextAsUser(any(), anyInt())).thenReturn(mockContext);
        when(mockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getApplicationLabel(any())).thenReturn("");
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mResources.getBoolean(R.bool.tag_intent_app_pref_supported)).thenReturn(true);
        when(mockContext.getResources()).thenReturn(mResources);
        when(NfcAdapter.getDefaultAdapter(mockContext)).thenReturn(mNfcAdapter);
        when(mNfcInjector.createAtomicBoolean()).thenReturn(mAtomicBoolean);
        when(mNfcInjector.createNfcTagAllowNotification(any(), any(), eq(true)))
                .thenReturn(mNfcTagAllowNotification);
        when(com.android.nfc.module.flags.Flags.nfcstack26q2Updates()).thenReturn(false);

        mNfcDispatcher = new NfcDispatcher(mockContext,
                new HandoverDataParser(), mNfcInjector, true, mDeviceConfigFacade);
        mLooper.dispatchAll();
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testLogOthers() {
        Tag tag = Tag.createMockTag(null, new int[0], new Bundle[0], 0L);
        mNfcDispatcher.dispatchTag(tag);
        ExtendedMockito.verify(() -> NfcStatsLog.write(
                NfcStatsLog.NFC_TAG_OCCURRED,
                NfcStatsLog.NFC_TAG_OCCURRED__TYPE__PROVISION,
                -1,
                tag.getTechCodeList(),
                BluetoothProtoEnums.MAJOR_CLASS_UNCATEGORIZED,
                ""));
    }

    @Test
    public void testSetForegroundDispatchForWifiConnect() {
        PendingIntent pendingIntent = mock(PendingIntent.class);
        mNfcDispatcher.setForegroundDispatch(pendingIntent, new IntentFilter[]{},
                new String[][]{});
        Bundle bundle = mock(Bundle.class);
        when(bundle.getParcelable(EXTRA_NDEF_MSG, android.nfc.NdefMessage.class)).thenReturn(
                mock(
                        NdefMessage.class));
        Tag tag = Tag.createMockTag(null, new int[]{1}, new Bundle[]{bundle}, 0L);
        Ndef ndef = mock(Ndef.class);
        when(Ndef.get(tag)).thenReturn(ndef);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        when(ndef.getCachedNdefMessage()).thenReturn(ndefMessage);
        NdefRecord ndefRecord = mock(NdefRecord.class);
        NdefRecord[] records = {ndefRecord};
        when(ndefMessage.getRecords()).thenReturn(records);
        when(NfcWifiProtectedSetup.tryNfcWifiSetup(ndef, mockContext)).thenReturn(true);
        mNfcDispatcher.dispatchTag(tag);
        ExtendedMockito.verify(() -> NfcStatsLog.write(
                NfcStatsLog.NFC_TAG_OCCURRED,
                NfcStatsLog.NFC_TAG_OCCURRED__TYPE__WIFI_CONNECT,
                -1,
                tag.getTechCodeList(),
                BluetoothProtoEnums.MAJOR_CLASS_UNCATEGORIZED,
                ""));
    }

    @Test
    public void testPeripheralHandoverBTParing() {
        String btOobPayload = "00060E4C00520100000000000000000000000000000000000000000001";
        Bundle bundle = mock(Bundle.class);
        when(bundle.getParcelable(EXTRA_NDEF_MSG, android.nfc.NdefMessage.class)).thenReturn(
                mock(
                        NdefMessage.class));
        Tag tag = Tag.createMockTag(null, new int[]{1}, new Bundle[]{bundle}, 0L);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = mock(NdefRecord.class);
        when(ndefRecord.getType()).thenReturn("application/vnd.bluetooth.ep.oob"
                .getBytes(StandardCharsets.US_ASCII));
        when(ndefRecord.getTnf()).thenReturn(NdefRecord.TNF_MIME_MEDIA);
        when(ndefRecord.getPayload()).thenReturn(btOobPayload.getBytes(StandardCharsets.US_ASCII));
        NdefRecord[] records = {ndefRecord};
        when(ndefMessage.getRecords()).thenReturn(records);
        mNfcDispatcher.tryPeripheralHandover(ndefMessage, tag);
        ExtendedMockito.verify(() -> NfcStatsLog.write(
                NfcStatsLog.NFC_TAG_OCCURRED,
                NfcStatsLog.NFC_TAG_OCCURRED__TYPE__BT_PAIRING,
                -1,
                tag.getTechCodeList(),
                BluetoothProtoEnums.MAJOR_CLASS_UNCATEGORIZED,
                ""));
    }

    @Test
    public void testCheckForAar() {
        NdefRecord ndefRecord = mock(NdefRecord.class);
        when(ndefRecord.getTnf()).thenReturn(NdefRecord.TNF_EXTERNAL_TYPE);
        when(ndefRecord.getType()).thenReturn(NdefRecord.RTD_ANDROID_APP);
        when(ndefRecord.getPayload()).thenReturn("test".getBytes(StandardCharsets.US_ASCII));
        String result = NfcDispatcher.checkForAar(ndefRecord);
        assertThat(result).isEqualTo("test");
    }

    @Test
    public void testCreateNfcResolverIntent() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported))).thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        ResolveInfo activity = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        activityInfo.packageName = "com.android.nfc";
        activityInfo.name = "test";
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.uid = 0;
        activityInfo.applicationInfo = applicationInfo;
        activity.activityInfo = activityInfo;

        ResolveInfo activity2 = mock(ResolveInfo.class);
        ActivityInfo activityInfo2 = mock(ActivityInfo.class);
        activityInfo2.packageName = "com.android.nfc2";
        activityInfo2.name = "test2";
        ApplicationInfo applicationInfo2 = mock(ApplicationInfo.class);
        applicationInfo2.uid = 1;
        activityInfo2.applicationInfo = applicationInfo2;
        activity2.activityInfo = activityInfo2;

        List<ResolveInfo> activities = new ArrayList<>();
        activities.add(activity);
        activities.add(activity2);
        Map<String, Boolean> prefList = new HashMap<>();
        prefList.put("com.android.nfc", false);
        when(mNfcAdapter.getTagIntentAppPreferenceForUser(0)).thenReturn(prefList);
        Assert.assertNotNull(dispatchInfo.intent);
        dispatchInfo.intent.setAction(NfcAdapter.ACTION_TECH_DISCOVERED);
        when(android.nfc.Flags.enableNfcMainline()).thenReturn(true);
        dispatchInfo.checkPrefList(activities, 0);

        assertThat(dispatchInfo.rootIntent).isNotNull();
        assertThat(dispatchInfo.rootIntent.getAction()).isNotNull();
        assertThat(dispatchInfo.rootIntent.getAction())
                .isEqualTo(NfcAdapter.ACTION_SHOW_NFC_RESOLVER);
    }

    @Test
    public void testDecodeNfcBarcodeUri() {
        PendingIntent pendingIntent = mock(PendingIntent.class);
        IntentFilter[] intentFilters = {};
        String[][] techLists = {{"Ndef"}};
        mNfcDispatcher.setForegroundDispatch(pendingIntent, intentFilters, techLists);
        ArgumentCaptor<Integer> callingUid = ArgumentCaptor.forClass(Integer.class);
        verify(mForegroundUtils).registerUidToBackgroundCallback(any(), callingUid.capture());
        Tag tag = mock(Tag.class);
        Bundle bundle = new Bundle();
        bundle.putInt(NfcBarcode.EXTRA_BARCODE_TYPE, NfcBarcode.TYPE_KOVIO);
        when(tag.getTechExtras(TagTechnology.NFC_BARCODE)).thenReturn(bundle);
        when(tag.hasTech(TagTechnology.NFC_BARCODE)).thenReturn(true);
        when(tag.getId()).thenReturn(new byte[]{0x04, 0x01, 0x64, 0x0C});
        when(tag.getTechList()).thenReturn(new String[]{"Ndef"});
        mNfcDispatcher.dispatchTag(tag);
        ExtendedMockito.verify(() -> NfcStatsLog.write(
                NfcStatsLog.NFC_TAG_OCCURRED,
                NfcStatsLog.NFC_TAG_OCCURRED__TYPE__FOREGROUND_DISPATCH,
                callingUid.getValue(),
                tag.getTechCodeList(),
                BluetoothProtoEnums.MAJOR_CLASS_UNCATEGORIZED,
                ""));
    }

    @Test
    public void testExtractAarPackages() {
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = mock(NdefRecord.class);
        when(ndefRecord.getTnf()).thenReturn(NdefRecord.TNF_EXTERNAL_TYPE);
        when(ndefRecord.getType()).thenReturn(NdefRecord.RTD_ANDROID_APP);
        when(ndefRecord.getPayload())
                .thenReturn("com.android.test".getBytes(StandardCharsets.US_ASCII));
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        List<String> aarPackages = NfcDispatcher.extractAarPackages(ndefMessage);
        assertThat(aarPackages).isNotNull();
        assertThat(aarPackages.size()).isGreaterThan(0);
        assertThat(aarPackages.get(0)).isEqualTo("com.android.test");
    }

    @Test
    public void testFinalize() throws Throwable {
        mNfcDispatcher.finalize();
        ArgumentCaptor<BroadcastReceiver> receiverArgumentCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mockContext).unregisterReceiver(receiverArgumentCaptor.capture());
        BroadcastReceiver broadcastReceiver = receiverArgumentCaptor.getValue();
        assertThat(broadcastReceiver).isNotNull();
    }

    @Test
    public void testGetAppSearchIntent() {
        Intent intent = NfcDispatcher.getAppSearchIntent("com.android.test");
        assertThat(intent).isNotNull();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_VIEW);
    }

    @Test
    public void testGetOemAppSearchIntent() throws RemoteException {
        INfcOemExtensionCallback nfcOemExtensionCallback = mock(INfcOemExtensionCallback.class);
        mNfcDispatcher.setOemExtension(nfcOemExtensionCallback);
        Intent intent = mNfcDispatcher.getOemAppSearchIntent("com.android.test");
        ArgumentCaptor<NfcCallbackResultReceiver> argumentCaptor = ArgumentCaptor.forClass(
                NfcCallbackResultReceiver.class);
        verify(nfcOemExtensionCallback).onGetOemAppSearchIntent(any(), argumentCaptor.capture());
        NfcCallbackResultReceiver nfcCallbackResultReceiver = argumentCaptor.getValue();
        assertThat(nfcCallbackResultReceiver).isNotNull();
    }

    @Test
    public void testIsComponentEnabled() throws PackageManager.NameNotFoundException {
        PackageManager packageManager = mock(PackageManager.class);
        ResolveInfo resolveInfo = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        activityInfo.packageName = "com.android.test";
        activityInfo.name = "test";
        resolveInfo.activityInfo = activityInfo;
        when(packageManager.getActivityInfo(any(), anyInt())).thenReturn(activityInfo);
        boolean result = NfcDispatcher.isComponentEnabled(packageManager, resolveInfo);
        assertThat(result).isTrue();
    }

    @Test
    public void testQueryNfcIntentActivitiesAsUser() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        UserHandle userHandle = mock(UserHandle.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        when(mUserManager.getEnabledProfiles()).thenReturn(luh);
        when(mUserManager.isQuietModeEnabled(userHandle)).thenReturn(true);
        dispatchInfo.hasIntentReceiver();
        verify(mPackageManager).queryIntentActivitiesAsUser(any(), any(), any());
    }

    @Test
    public void testReceiveOemCallbackResult() throws RemoteException {
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        INfcOemExtensionCallback nfcOemExtensionCallback = mock(INfcOemExtensionCallback.class);
        mNfcDispatcher.setOemExtension(nfcOemExtensionCallback);
        mNfcDispatcher.receiveOemCallbackResult(tag, ndefMessage);
        verify(nfcOemExtensionCallback).onNdefMessage(any(), any(), any());
    }

    @Test
    public void testTryNdef() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        UserHandle userHandle = mock(UserHandle.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        when(mUserManager.getEnabledProfiles()).thenReturn(luh);
        when(mUserManager.isQuietModeEnabled(userHandle)).thenReturn(false);
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        ResolveInfo ri = mock(ResolveInfo.class);
        when(mPackageManager.resolveActivity(any(), anyInt())).thenReturn(ri);
        mNfcDispatcher.tryNdef(dispatchInfo, ndefMessage);
        verify(mPackageManager).resolveActivity(any(), anyInt());
    }

    @Test
    public void testGetUri() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        String uri = dispatchInfo.getUri();
        assertThat(uri).isNotNull();
        assertThat(uri).isEqualTo("https://www.example.com");
    }

    @Test
    public void testIsWebIntent() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        boolean webIntent = dispatchInfo.isWebIntent();
        assertThat(webIntent).isTrue();
    }

    @Test
    public void testSetViewIntent() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        Intent intent = dispatchInfo.setViewIntent();
        assertThat(intent).isNotNull();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_VIEW);
    }

    @Test
    public void testTryStartActivity() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        ResolveInfo ri = mock(ResolveInfo.class);
        ActivityInfo ai = mock(ActivityInfo.class);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.uid = 0;
        ai.applicationInfo = applicationInfo;
        ai.packageName = "com.android.test";
        ai.name = "test";
        ai.exported = true;
        ri.activityInfo = ai;
        List<ResolveInfo> ris = new ArrayList<>();
        ris.add(ri);
        when(mPackageManager.queryIntentActivitiesAsUser(any(), any(), any())).thenReturn(ris);
        boolean result = dispatchInfo.tryStartActivity();
        assertThat(result).isTrue();
        ExtendedMockito.verify(() -> NfcStatsLog.write(NfcStatsLog.NFC_TAG_OCCURRED,
                NfcStatsLog.NFC_TAG_OCCURRED__TYPE__APP_LAUNCH,
                0,
                tag.getTechCodeList(),
                BluetoothProtoEnums.MAJOR_CLASS_UNCATEGORIZED,
                ""));

    }

    @Test
    public void testTryStartActivitySafer() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        ResolveInfo ri = mock(ResolveInfo.class);
        ActivityInfo ai = mock(ActivityInfo.class);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.uid = 0;
        applicationInfo.targetSdkVersion = 37;
        ai.applicationInfo = applicationInfo;
        ai.packageName = "com.android.test";
        ai.name = "test";
        ai.exported = true;
        ai.permission = "android.permission.DISPATCH_NFC_MESSAGE";
        ri.activityInfo = ai;
        List<ResolveInfo> ris = new ArrayList<>();
        ris.add(ri);
        when(mPackageManager.queryIntentActivitiesAsUser(any(), any(), any())).thenReturn(ris);

        assertThat(dispatchInfo.tryStartActivitySafer()).isTrue();
    }

    @Test
    public void testTryStartActivitySafer_noPermission() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        ResolveInfo ri = mock(ResolveInfo.class);
        ActivityInfo ai = mock(ActivityInfo.class);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.uid = 0;
        applicationInfo.targetSdkVersion = 37;
        ai.applicationInfo = applicationInfo;
        ai.packageName = "com.android.test";
        ai.name = "test";
        ai.exported = true;
        ri.activityInfo = ai;
        List<ResolveInfo> ris = new ArrayList<>();
        ris.add(ri);
        when(mPackageManager.queryIntentActivitiesAsUser(any(), any(), any())).thenReturn(ris);

        assertThat(dispatchInfo.tryStartActivitySafer()).isFalse();
    }

    @Test
    public void testTryStartActivitySafer_noPermission_notTargetSdk() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        ResolveInfo ri = mock(ResolveInfo.class);
        ActivityInfo ai = mock(ActivityInfo.class);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.uid = 0;
        applicationInfo.targetSdkVersion = 36;
        ai.applicationInfo = applicationInfo;
        ai.packageName = "com.android.test";
        ai.name = "test";
        ai.exported = true;
        ri.activityInfo = ai;
        List<ResolveInfo> ris = new ArrayList<>();
        ris.add(ri);
        when(mPackageManager.queryIntentActivitiesAsUser(any(), any(), any())).thenReturn(ris);

        assertThat(dispatchInfo.tryStartActivitySafer()).isTrue();
    }

    @Test
    public void testTryStartActivitySafer_appStopped() {
        when(mResources.getBoolean(eq(R.bool.tag_intent_app_pref_supported)))
                .thenReturn(true);
        Tag tag = mock(Tag.class);
        NdefMessage ndefMessage = mock(NdefMessage.class);
        NdefRecord ndefRecord = NdefRecord.createUri("https://www.example.com");
        when(ndefMessage.getRecords()).thenReturn(new NdefRecord[]{ndefRecord});
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher
                .DispatchInfo(mockContext, mNfcInjector, tag, ndefMessage);
        ResolveInfo ri = mock(ResolveInfo.class);
        ActivityInfo ai = mock(ActivityInfo.class);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        applicationInfo.uid = 0;
        applicationInfo.targetSdkVersion = 37;
        applicationInfo.flags |= ApplicationInfo.FLAG_STOPPED;
        ai.applicationInfo = applicationInfo;
        ai.packageName = "com.android.test";
        ai.name = "test";
        ai.exported = true;
        ai.permission = "android.permission.DISPATCH_NFC_MESSAGE";
        ri.activityInfo = ai;
        List<ResolveInfo> ris = new ArrayList<>();
        ris.add(ri);
        when(mPackageManager.queryIntentActivitiesAsUser(any(), any(), any())).thenReturn(ris);

        assertThat(dispatchInfo.tryStartActivitySafer()).isFalse();
    }

    @Test
    public void testMessageHandler() {
        Handler handler = mNfcDispatcher.getHandler();
        Message msg = new Message();
        msg.arg1 = 1;
        msg.what = PeripheralHandoverService.MSG_HEADSET_NOT_CONNECTED;
        handler.handleMessage(msg);
        verify(mAtomicBoolean).set(true);
    }

    @Test
    public void testDump() {
        PrintWriter pw = mock(PrintWriter.class);
        PendingIntent pendingIntent = mock(PendingIntent.class);
        IntentFilter[] intentFilters = {};
        String[][] techLists = {{"Ndef"}};
        mNfcDispatcher.setForegroundDispatch(pendingIntent, intentFilters, techLists);

        mNfcDispatcher.dump(mock(FileDescriptor.class), pw, new String[]{});
        verify(pw).println("mOverrideTechLists=" + Arrays.deepToString(techLists));
    }

    @Test
    public void testDumpDebug() {
        ProtoOutputStream proto = mock(ProtoOutputStream.class);
        PendingIntent pendingIntent = mock(PendingIntent.class);
        IntentFilter[] intentFilters = {};
        String[][] techLists = {{"Ndef"}};
        mNfcDispatcher.setForegroundDispatch(pendingIntent, intentFilters, techLists);
        mNfcDispatcher.disableProvisioningMode();
        when(mAtomicBoolean.get()).thenReturn(true);

        mNfcDispatcher.dumpDebug(proto);
        verify(proto).write(NfcDispatcherProto.PROVISIONING_ONLY, false);
    }

    @Test
    public void testExtractOemPackages() throws RemoteException {
        NdefMessage message = mock(NdefMessage.class);
        INfcOemExtensionCallback nfcOemExtensionCallback = mock(INfcOemExtensionCallback.class);
        mNfcDispatcher.setOemExtension(nfcOemExtensionCallback);

        mNfcDispatcher.extractOemPackages(message);
        verify(nfcOemExtensionCallback).onExtractOemPackages(any(NdefMessage.class), any(
                ResultReceiver.class));
    }

    @Test
    public void testTryActivityOrLaunchAppStore()
            throws PackageManager.NameNotFoundException, NoSuchFieldException,
            IllegalAccessException {
        NfcDispatcher.DispatchInfo dispatch = mock(NfcDispatcher.DispatchInfo.class);
        List<String> packages = new ArrayList<>();
        packages.add("example.nfc");
        UserHandle userHandle = mock(UserHandle.class);
        Context context = mock(Context.class);
        PackageManager pm = mock(PackageManager.class);
        Intent appLaunchIntent = mock(Intent.class);
        Intent intent = mock(Intent.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        when(dispatch.tryStartActivity()).thenReturn(false);
        when(dispatch.getCurrentActiveUserHandles()).thenReturn(luh);
        when(mockContext.createPackageContextAsUser(anyString(), anyInt(),
                any(UserHandle.class))).thenReturn(context);
        when(context.getPackageManager()).thenReturn(pm);
        when(pm.getLaunchIntentForPackage(packages.getFirst())).thenReturn(appLaunchIntent);
        when(pm.resolveActivity(appLaunchIntent, 0)).thenReturn(null);
        when(dispatch.tryStartActivity(any(Intent.class))).thenReturn(true);
        Field field = NfcDispatcher.DispatchInfo.class.getDeclaredField("intent");
        field.setAccessible(true);
        field.set(dispatch, intent);

        assertTrue(mNfcDispatcher.tryActivityOrLaunchAppStore(dispatch, packages, true));
    }

    @Test
    public void testTryActivityOrLaunchAppStoreWhenAarToNdef()
            throws NoSuchFieldException, IllegalAccessException {
        NfcDispatcher.DispatchInfo dispatch = mock(NfcDispatcher.DispatchInfo.class);
        List<String> packages = new ArrayList<>();
        packages.add("example.nfc");
        UserHandle userHandle = mock(UserHandle.class);
        Intent intent = mock(Intent.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        when(dispatch.tryStartActivitySafer()).thenReturn(true);
        Field field = NfcDispatcher.DispatchInfo.class.getDeclaredField("intent");
        field.setAccessible(true);
        field.set(dispatch, intent);

        assertTrue(mNfcDispatcher.tryActivityOrLaunchAppStore(dispatch, packages, true));
    }

    @Test
    public void testTryActivityOrLaunchAppStoreWhenOemToNdef()
            throws NoSuchFieldException, IllegalAccessException {
        NfcDispatcher.DispatchInfo dispatch = mock(NfcDispatcher.DispatchInfo.class);
        List<String> packages = new ArrayList<>();
        packages.add("example.nfc");
        UserHandle userHandle = mock(UserHandle.class);
        Intent intent = mock(Intent.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        when(dispatch.tryStartActivitySafer()).thenReturn(true);
        Field field = NfcDispatcher.DispatchInfo.class.getDeclaredField("intent");
        field.setAccessible(true);
        field.set(dispatch, intent);

        assertTrue(mNfcDispatcher.tryActivityOrLaunchAppStore(dispatch, packages, false));
    }

    @Test
    public void testTryActivityOrLaunchAppStoreWhenMatchedAarApplicationLaunch()
            throws PackageManager.NameNotFoundException, NoSuchFieldException,
            IllegalAccessException {
        NfcDispatcher.DispatchInfo dispatch = mock(NfcDispatcher.DispatchInfo.class);
        List<String> packages = new ArrayList<>();
        packages.add("example.nfc");
        UserHandle userHandle = mock(UserHandle.class);
        Context context = mock(Context.class);
        PackageManager pm = mock(PackageManager.class);
        Intent appLaunchIntent = mock(Intent.class);
        Intent intent = mock(Intent.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        ResolveInfo ri = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        activityInfo.exported = true;
        ri.activityInfo = activityInfo;
        when(dispatch.tryStartActivity()).thenReturn(false);
        when(dispatch.getCurrentActiveUserHandles()).thenReturn(luh);
        when(mockContext.createPackageContextAsUser(anyString(), anyInt(),
                any(UserHandle.class))).thenReturn(context);
        when(context.getPackageManager()).thenReturn(pm);
        when(pm.getLaunchIntentForPackage(packages.getFirst())).thenReturn(appLaunchIntent);
        when(pm.resolveActivity(appLaunchIntent, 0)).thenReturn(ri);
        when(dispatch.tryStartActivity(any(Intent.class))).thenReturn(true);
        Field field = NfcDispatcher.DispatchInfo.class.getDeclaredField("intent");
        field.setAccessible(true);
        field.set(dispatch, intent);

        assertTrue(mNfcDispatcher.tryActivityOrLaunchAppStore(dispatch, packages, true));
    }

    @Test
    public void testTryActivityOrLaunchAppStoreWhenMatchedOemApplicationLaunch()
            throws PackageManager.NameNotFoundException, NoSuchFieldException,
            IllegalAccessException {
        NfcDispatcher.DispatchInfo dispatch = mock(NfcDispatcher.DispatchInfo.class);
        List<String> packages = new ArrayList<>();
        packages.add("example.nfc");
        UserHandle userHandle = mock(UserHandle.class);
        Context context = mock(Context.class);
        PackageManager pm = mock(PackageManager.class);
        Intent appLaunchIntent = mock(Intent.class);
        Intent intent = mock(Intent.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        ResolveInfo ri = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        activityInfo.exported = true;
        ri.activityInfo = activityInfo;
        when(dispatch.tryStartActivity()).thenReturn(false);
        when(dispatch.getCurrentActiveUserHandles()).thenReturn(luh);
        when(mockContext.createPackageContextAsUser(anyString(), anyInt(),
                any(UserHandle.class))).thenReturn(context);
        when(context.getPackageManager()).thenReturn(pm);
        when(pm.getLaunchIntentForPackage(packages.getFirst())).thenReturn(appLaunchIntent);
        when(pm.resolveActivity(appLaunchIntent, 0)).thenReturn(ri);
        when(dispatch.tryStartActivity(any(Intent.class))).thenReturn(true);
        Field field = NfcDispatcher.DispatchInfo.class.getDeclaredField("intent");
        field.setAccessible(true);
        field.set(dispatch, intent);

        assertTrue(mNfcDispatcher.tryActivityOrLaunchAppStore(dispatch, packages, false));
        verify(pm).getLaunchIntentForPackage(packages.getFirst());
    }

    @Test
    public void testTryActivityOrLaunchAppStoreWithException()
            throws PackageManager.NameNotFoundException, NoSuchFieldException,
            IllegalAccessException {
        NfcDispatcher.DispatchInfo dispatch = mock(NfcDispatcher.DispatchInfo.class);
        List<String> packages = new ArrayList<>();
        packages.add("example.nfc");
        UserHandle userHandle = mock(UserHandle.class);
        Context context = mock(Context.class);
        PackageManager pm = mock(PackageManager.class);
        Intent intent = mock(Intent.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        ResolveInfo ri = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        activityInfo.exported = true;
        ri.activityInfo = activityInfo;
        when(dispatch.tryStartActivity()).thenReturn(false);
        when(dispatch.getCurrentActiveUserHandles()).thenReturn(luh);
        when(mockContext.createPackageContextAsUser(anyString(), anyInt(),
                any(UserHandle.class))).thenThrow(PackageManager.NameNotFoundException.class);
        when(context.getPackageManager()).thenReturn(pm);
        Field field = NfcDispatcher.DispatchInfo.class.getDeclaredField("intent");
        field.setAccessible(true);
        field.set(dispatch, intent);
        INfcOemExtensionCallback nfcOemExtensionCallback = mock(INfcOemExtensionCallback.class);
        mNfcDispatcher.setOemExtension(nfcOemExtensionCallback);

        assertFalse(mNfcDispatcher.tryActivityOrLaunchAppStore(dispatch, packages, false));
    }

    @Test
    public void testTryTechWithSingleMatch() throws IllegalAccessException, NoSuchFieldException,
            PackageManager.NameNotFoundException {
        NfcDispatcher.DispatchInfo dispatch = mock(NfcDispatcher.DispatchInfo.class);
        RegisteredComponentCache mTechListFilters = mock(RegisteredComponentCache.class);
        UserHandle userHandle = mock(UserHandle.class);
        Context context = mock(Context.class);
        ResolveInfo resolveInfo = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        ApplicationInfo appInfo = mock(ApplicationInfo.class);
        Intent intent = mock(Intent.class);
        Tag tag = mock(Tag.class);
        String packageName = "sample.package.name";
        RegisteredComponentCache.ComponentInfo info = mock(
                RegisteredComponentCache.ComponentInfo.class);
        PackageManager pm = mock(PackageManager.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        ArrayList<RegisteredComponentCache.ComponentInfo> registered = new ArrayList<>();
        registered.add(info);
        Map<String, Boolean> prefList = new HashMap<>();
        prefList.put(packageName + "another", true);
        String[] tagTechs =
                new String[]{IsoDep.class.getName(), NfcA.class.getName(), NfcB.class.getName()};
        Field field = NfcDispatcher.class.getDeclaredField("mTechListFilters");
        field.setAccessible(true);
        field.set(mNfcDispatcher, mTechListFilters);
        Field fieldCompInfoResolveInfo =
                RegisteredComponentCache.ComponentInfo.class.getDeclaredField("resolveInfo");
        fieldCompInfoResolveInfo.setAccessible(true);
        fieldCompInfoResolveInfo.set(info, resolveInfo);
        activityInfo.exported = true;
        activityInfo.applicationInfo = appInfo;
        activityInfo.packageName = packageName;
        activityInfo.name = "name";
        resolveInfo.activityInfo = activityInfo;
        Field fieldCompInfoResolveTech =
                RegisteredComponentCache.ComponentInfo.class.getDeclaredField("techs");
        fieldCompInfoResolveTech.setAccessible(true);
        fieldCompInfoResolveTech.set(info, tagTechs);
        Field fieldNfcAdapter = NfcDispatcher.class.getDeclaredField("mNfcAdapter");
        fieldNfcAdapter.setAccessible(true);
        fieldNfcAdapter.set(mNfcDispatcher, mNfcAdapter);
        Field fieldTagAppSupported = NfcDispatcher.class.getDeclaredField("mIsTagAppPrefSupported");
        fieldTagAppSupported.setAccessible(true);
        fieldTagAppSupported.set(mNfcDispatcher, true);
        Field fieldIntent = NfcDispatcher.DispatchInfo.class.getDeclaredField("intent");
        fieldIntent.setAccessible(true);
        fieldIntent.set(dispatch, intent);
        when(tag.getTechList()).thenReturn(tagTechs);
        when(mTechListFilters.getComponents()).thenReturn(registered);
        when(dispatch.getCurrentActiveUserHandles()).thenReturn(luh);
        when(mockContext.createPackageContextAsUser("android", 0, userHandle))
                .thenReturn(context);
        when(context.getPackageManager()).thenReturn(pm);
        when(pm.getActivityInfo(any(ComponentName.class), anyInt())).thenReturn(activityInfo);
        when(mockContext.getPackageManager()).thenReturn(pm);
        when(pm.getApplicationLabel(appInfo)).thenReturn("appname");
        when(userHandle.getIdentifier()).thenReturn(0);
        when(mNfcAdapter.getTagIntentAppPreferenceForUser(0)).thenReturn(prefList);
        when(dispatch.tryStartActivity()).thenReturn(true);

        assertTrue(mNfcDispatcher.tryTech(dispatch, tag));
        verify(mNfcAdapter).setTagIntentAppPreferenceForUser(0, packageName, true);
    }

    @Test
    public void testTryTechWithSingleMatch_withActivityPermission() throws IllegalAccessException,
            NoSuchFieldException, PackageManager.NameNotFoundException {
        NfcDispatcher.DispatchInfo dispatch = mock(NfcDispatcher.DispatchInfo.class);
        RegisteredComponentCache mTechListFilters = mock(RegisteredComponentCache.class);
        UserHandle userHandle = mock(UserHandle.class);
        Context context = mock(Context.class);
        ResolveInfo resolveInfo = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        ApplicationInfo appInfo = mock(ApplicationInfo.class);
        Intent intent = mock(Intent.class);
        Tag tag = mock(Tag.class);
        String packageName = "sample.package.name";
        RegisteredComponentCache.ComponentInfo info = mock(
                RegisteredComponentCache.ComponentInfo.class);
        PackageManager pm = mock(PackageManager.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        ArrayList<RegisteredComponentCache.ComponentInfo> registered = new ArrayList<>();
        registered.add(info);
        Map<String, Boolean> prefList = new HashMap<>();
        prefList.put(packageName + "another", true);
        String[] tagTechs =
                new String[]{IsoDep.class.getName(), NfcA.class.getName(), NfcB.class.getName()};
        Field field = NfcDispatcher.class.getDeclaredField("mTechListFilters");
        field.setAccessible(true);
        field.set(mNfcDispatcher, mTechListFilters);
        Field fieldCompInfoResolveInfo =
                RegisteredComponentCache.ComponentInfo.class.getDeclaredField("resolveInfo");
        fieldCompInfoResolveInfo.setAccessible(true);
        fieldCompInfoResolveInfo.set(info, resolveInfo);
        appInfo.targetSdkVersion = 37;
        activityInfo.exported = true;
        activityInfo.applicationInfo = appInfo;
        activityInfo.packageName = packageName;
        activityInfo.name = "name";
        activityInfo.permission = "android.permission.DISPATCH_NFC_MESSAGE";
        resolveInfo.activityInfo = activityInfo;
        Field fieldCompInfoResolveTech =
                RegisteredComponentCache.ComponentInfo.class.getDeclaredField("techs");
        fieldCompInfoResolveTech.setAccessible(true);
        fieldCompInfoResolveTech.set(info, tagTechs);
        Field fieldNfcAdapter = NfcDispatcher.class.getDeclaredField("mNfcAdapter");
        fieldNfcAdapter.setAccessible(true);
        fieldNfcAdapter.set(mNfcDispatcher, mNfcAdapter);
        Field fieldTagAppSupported = NfcDispatcher.class.getDeclaredField("mIsTagAppPrefSupported");
        fieldTagAppSupported.setAccessible(true);
        fieldTagAppSupported.set(mNfcDispatcher, true);
        Field fieldIntent = NfcDispatcher.DispatchInfo.class.getDeclaredField("intent");
        fieldIntent.setAccessible(true);
        fieldIntent.set(dispatch, intent);
        when(tag.getTechList()).thenReturn(tagTechs);
        when(mTechListFilters.getComponents()).thenReturn(registered);
        when(dispatch.getCurrentActiveUserHandles()).thenReturn(luh);
        when(mockContext.createPackageContextAsUser("android", 0, userHandle))
                .thenReturn(context);
        when(context.getPackageManager()).thenReturn(pm);
        when(pm.getActivityInfo(any(ComponentName.class), anyInt())).thenReturn(activityInfo);
        when(mockContext.getPackageManager()).thenReturn(pm);
        when(pm.getApplicationLabel(appInfo)).thenReturn("appname");
        when(userHandle.getIdentifier()).thenReturn(0);
        when(mNfcAdapter.getTagIntentAppPreferenceForUser(0)).thenReturn(prefList);
        when(dispatch.tryStartActivity()).thenReturn(true);

        assertTrue(mNfcDispatcher.tryTech(dispatch, tag));
        verify(mNfcAdapter).setTagIntentAppPreferenceForUser(0, packageName, true);
    }


    @Test
    public void testTryTechWithActivityNoPermission() throws IllegalAccessException,
            NoSuchFieldException, PackageManager.NameNotFoundException {
        NfcDispatcher.DispatchInfo dispatch = mock(NfcDispatcher.DispatchInfo.class);
        RegisteredComponentCache mTechListFilters = mock(RegisteredComponentCache.class);
        UserHandle userHandle = mock(UserHandle.class);
        Context context = mock(Context.class);
        ResolveInfo resolveInfo = mock(ResolveInfo.class);
        ActivityInfo activityInfo = mock(ActivityInfo.class);
        ApplicationInfo appInfo = mock(ApplicationInfo.class);
        Intent intent = mock(Intent.class);
        Tag tag = mock(Tag.class);
        String packageName = "sample.package.name";
        RegisteredComponentCache.ComponentInfo info = mock(
                RegisteredComponentCache.ComponentInfo.class);
        PackageManager pm = mock(PackageManager.class);
        List<UserHandle> luh = new ArrayList<>();
        luh.add(userHandle);
        ArrayList<RegisteredComponentCache.ComponentInfo> registered = new ArrayList<>();
        registered.add(info);
        Map<String, Boolean> prefList = new HashMap<>();
        prefList.put(packageName + "another", true);
        String[] tagTechs =
                new String[]{IsoDep.class.getName(), NfcA.class.getName(), NfcB.class.getName()};
        Field field = NfcDispatcher.class.getDeclaredField("mTechListFilters");
        field.setAccessible(true);
        field.set(mNfcDispatcher, mTechListFilters);
        Field fieldCompInfoResolveInfo =
                RegisteredComponentCache.ComponentInfo.class.getDeclaredField("resolveInfo");
        fieldCompInfoResolveInfo.setAccessible(true);
        fieldCompInfoResolveInfo.set(info, resolveInfo);
        luh.add(userHandle);
        appInfo.targetSdkVersion = 37;
        activityInfo.exported = true;
        activityInfo.applicationInfo = appInfo;
        activityInfo.packageName = packageName;
        activityInfo.name = "name";
        resolveInfo.activityInfo = activityInfo;
        Field fieldCompInfoResolveTech =
                RegisteredComponentCache.ComponentInfo.class.getDeclaredField("techs");
        fieldCompInfoResolveTech.setAccessible(true);
        fieldCompInfoResolveTech.set(info, tagTechs);
        Field fieldNfcAdapter = NfcDispatcher.class.getDeclaredField("mNfcAdapter");
        fieldNfcAdapter.setAccessible(true);
        fieldNfcAdapter.set(mNfcDispatcher, mNfcAdapter);
        Field fieldTagAppSupported = NfcDispatcher.class.getDeclaredField("mIsTagAppPrefSupported");
        fieldTagAppSupported.setAccessible(true);
        fieldTagAppSupported.set(mNfcDispatcher, true);
        Field fieldIntent = NfcDispatcher.DispatchInfo.class.getDeclaredField("intent");
        fieldIntent.setAccessible(true);
        fieldIntent.set(dispatch, intent);
        when(tag.getTechList()).thenReturn(tagTechs);
        when(mTechListFilters.getComponents()).thenReturn(registered);
        when(dispatch.getCurrentActiveUserHandles()).thenReturn(luh);
        when(mockContext.createPackageContextAsUser("android", 0, userHandle))
                .thenReturn(context);
        when(context.getPackageManager()).thenReturn(pm);
        when(pm.getActivityInfo(any(ComponentName.class), anyInt())).thenReturn(activityInfo);
        when(mockContext.getPackageManager()).thenReturn(pm);
        when(pm.getApplicationLabel(appInfo)).thenReturn("appname");
        when(userHandle.getIdentifier()).thenReturn(0);
        when(mNfcAdapter.getTagIntentAppPreferenceForUser(0)).thenReturn(prefList);

        assertFalse(mNfcDispatcher.tryTech(dispatch, tag));
        verify(dispatch, never()).tryStartActivity();
    }

    @Test
    public void testTryOverrides_NdefDispatchFails() throws CanceledException {
        // Verifies that tryOverrides returns false when PendingIntent.send fails for NDEF.
        // Setup a pending intent that will fail by throwing CanceledException.
        PendingIntent pendingIntent = mock(PendingIntent.class);
        doThrow(new CanceledException()).when(pendingIntent)
                .send(any(Context.class), anyInt(), any(Intent.class));

        // Setup a tag with an NDEF message to trigger the NDEF dispatch path.
        NdefRecord record = NdefRecord.createMime("text/plain", "test".getBytes());
        NdefMessage message = new NdefMessage(record);
        Tag tag = Tag.createMockTag(new byte[]{0x01}, new int[]{TagTechnology.NDEF}, new Bundle[1],
                0L);
        NfcDispatcher.DispatchInfo dispatch = new NfcDispatcher.DispatchInfo(mockContext,
                mNfcInjector, tag, message);

        // Setup filters to match the NDEF intent.
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            filter.addDataType("text/plain");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            Assert.fail("Malformed Mime Type");
        }
        IntentFilter[] filters = new IntentFilter[]{filter};

        // Call tryOverrides and expect it to fail because the PendingIntent send fails.
        boolean result = mNfcDispatcher.tryOverrides(dispatch, tag, message, pendingIntent, filters,
                null);

        // Assert that the method returns false, indicating failure.
        assertFalse(result);
        // Verify that the send method was called, which then threw the mocked exception.
        verify(pendingIntent).send(any(Context.class), eq(Activity.RESULT_OK), any(Intent.class));
    }

    @Test
    public void testTryOverrides_TechDispatchFails() throws CanceledException {
        // Verifies that tryOverrides returns false when PendingIntent.send fails for TECH.
        // Setup a pending intent that will fail by throwing CanceledException.
        PendingIntent pendingIntent = mock(PendingIntent.class);
        doThrow(new CanceledException()).when(pendingIntent)
                .send(any(Context.class), anyInt(), any(Intent.class));

        // Setup a tag with NfcA tech to trigger the TECH dispatch path.
        Tag tag = mock(Tag.class);
        when(tag.getTechList()).thenReturn(new String[]{NfcA.class.getName()});
        NfcDispatcher.DispatchInfo dispatch = new NfcDispatcher.DispatchInfo(mockContext,
                mNfcInjector, tag, null);

        // Setup tech lists to match the tag's tech.
        String[][] techLists = new String[][]{{NfcA.class.getName()}};

        // Call tryOverrides and expect it to fail because the PendingIntent send fails.
        boolean result = mNfcDispatcher.tryOverrides(dispatch, tag, null, pendingIntent, null,
                techLists);

        // Assert that the method returns false, indicating failure.
        assertFalse(result);
        // Verify that the send method was called, which then threw the mocked exception.
        verify(pendingIntent).send(any(Context.class), eq(Activity.RESULT_OK), any(Intent.class));
    }

    @Test
    public void testTryOverrides_TagDispatchFails() throws CanceledException {
        // Verifies that tryOverrides returns false when PendingIntent.send fails for TAG.
        // Setup a pending intent that will fail by throwing CanceledException.
        PendingIntent pendingIntent = mock(PendingIntent.class);
        doThrow(new CanceledException()).when(pendingIntent)
                .send(any(Context.class), anyInt(), any(Intent.class));

        // Setup a generic tag to trigger the TAG dispatch path.
        Tag tag = Tag.createMockTag(new byte[]{0x01}, new int[0], new Bundle[0], 0L);
        NfcDispatcher.DispatchInfo dispatch = new NfcDispatcher.DispatchInfo(mockContext,
                mNfcInjector, tag, null);

        // Setup filters to match the TAG_DISCOVERED intent.
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter[] filters = new IntentFilter[]{filter};

        // Call tryOverrides and expect it to fail because the PendingIntent send fails.
        boolean result = mNfcDispatcher.tryOverrides(dispatch, tag, null, pendingIntent, filters,
                null);

        // Assert that the method returns false, indicating failure.
        assertFalse(result);
        // Verify that the send method was called, which then threw the mocked exception.
        verify(pendingIntent).send(any(Context.class), eq(Activity.RESULT_OK), any(Intent.class));
    }

    @Test
    public void testCheckPrefList_withActionView_doesNotMuteNewApp() {
        // This test verifies that when nfcstack26q2Updates is enabled,
        // an app that handles an ACTION_VIEW intent is not muted by default.
        when(com.android.nfc.module.flags.Flags.nfcstack26q2Updates()).thenReturn(true);
        when(mNfcInjector.createNfcTagAllowNotification(any(), any(), eq(false)))
                .thenReturn(mNfcTagAllowNotification);

        // Setup DispatchInfo with an ACTION_VIEW intent
        Tag tag = mock(Tag.class);
        NdefRecord record = NdefRecord.createUri("https://example.com");
        NdefMessage message = new NdefMessage(record);
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher.DispatchInfo(
                mockContext, mNfcInjector, tag, message);
        dispatchInfo.setViewIntent(); // This sets ACTION_VIEW

        // Setup a single activity to handle the intent
        ResolveInfo resolveInfo = createResolveInfo("com.example.app", "TestActivity", 0);
        List<ResolveInfo> activities = new ArrayList<>();
        activities.add(resolveInfo);

        // Mock that the app preference does not exist yet
        when(mNfcAdapter.getTagIntentAppPreferenceForUser(anyInt())).thenReturn(new HashMap<>());

        // Call checkPrefList
        List<ResolveInfo> filteredActivities = dispatchInfo.checkPrefList(activities, 0);

        // Verify the app is not filtered out
        assertThat(filteredActivities).hasSize(1);
        assertThat(filteredActivities.get(0)).isEqualTo(resolveInfo);

        // Verify the app preference is set to 'allowed' (true)
        verify(mNfcAdapter).setTagIntentAppPreferenceForUser(0, "com.example.app", true);

        // Verify the notification indicates the app is allowed
        verify(mNfcInjector).createNfcTagAllowNotification(any(), any(), eq(true));
        verify(mNfcTagAllowNotification).startNotification();
    }

    @Test
    public void testCheckPrefList_withTechDiscovered_mutesNewApp() {
        // This test verifies that when nfcstack26q2Updates is enabled,
        // an app that handles an ACTION_TECH_DISCOVERED intent is muted by default.
        when(com.android.nfc.module.flags.Flags.nfcstack26q2Updates()).thenReturn(true);
        when(mNfcInjector.createNfcTagAllowNotification(any(), any(), eq(false)))
                .thenReturn(mNfcTagAllowNotification);

        // Setup DispatchInfo with an ACTION_TECH_DISCOVERED intent
        Tag tag = mock(Tag.class);
        NfcDispatcher.DispatchInfo dispatchInfo = new NfcDispatcher.DispatchInfo(
                mockContext, mNfcInjector, tag, null);
        dispatchInfo.setTechIntent(); // This sets ACTION_TECH_DISCOVERED

        // Setup a single activity to handle the intent
        ResolveInfo resolveInfo = createResolveInfo("com.example.app", "TestActivity", 0);
        List<ResolveInfo> activities = new ArrayList<>();
        activities.add(resolveInfo);

        // Mock that the app preference does not exist yet
        when(mNfcAdapter.getTagIntentAppPreferenceForUser(anyInt())).thenReturn(new HashMap<>());

        // Call checkPrefList
        List<ResolveInfo> filteredActivities = dispatchInfo.checkPrefList(activities, 0);

        // Verify the app is filtered out (muted)
        assertThat(filteredActivities).isEmpty();

        // Verify the app preference is set to 'muted' (false)
        verify(mNfcAdapter).setTagIntentAppPreferenceForUser(0, "com.example.app", false);

        // Verify the notification indicates the app is not allowed
        verify(mNfcInjector).createNfcTagAllowNotification(any(), any(), eq(false));
        verify(mNfcTagAllowNotification).startNotification();
    }

    private ResolveInfo createResolveInfo(String packageName, String name, int uid) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = packageName;
        resolveInfo.activityInfo.name = name;
        resolveInfo.activityInfo.applicationInfo = new ApplicationInfo();
        resolveInfo.activityInfo.applicationInfo.uid = uid;
        resolveInfo.activityInfo.exported = true;
        return resolveInfo;
    }
}
