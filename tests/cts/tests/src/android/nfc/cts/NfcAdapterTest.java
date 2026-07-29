package android.nfc.cts;

import static android.Manifest.permission.NFC_SET_CONTROLLER_ALWAYS_ON;
import static android.Manifest.permission.PERFORM_GESTURE_EXCHANGE;
import static android.nfc.NfcOemExtension.HCE_ACTIVATE;
import static android.nfc.NfcRoutingTableEntry.TYPE_AID;
import static android.nfc.NfcRoutingTableEntry.TYPE_PROTOCOL;
import static android.nfc.NfcRoutingTableEntry.TYPE_SYSTEM_CODE;
import static android.nfc.NfcRoutingTableEntry.TYPE_TECHNOLOGY;
import static android.nfc.cardemulation.CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_DEFAULT;
import static android.nfc.cardemulation.CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_DH;
import static android.nfc.cardemulation.CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_ESE;
import static android.nfc.cardemulation.CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_NDEF_NFCEE;
import static android.nfc.cardemulation.CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_UICC;
import static android.nfc.cardemulation.CardEmulation.PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET;
import static android.nfc.cts.NfcUtils.assumeObserveModeSupported;
import static android.nfc.cts.NfcUtils.assumeVsrApiGreaterThanUdc;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.Flags;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAntennaInfo;
import android.nfc.NfcOemExtension;
import android.nfc.NfcRoutingTableEntry;
import android.nfc.OemLogItems;
import android.nfc.RfDiscoverConfig;
import android.nfc.RoutingStatus;
import android.nfc.RoutingTableAidEntry;
import android.nfc.RoutingTableProtocolEntry;
import android.nfc.RoutingTableSystemCodeEntry;
import android.nfc.RoutingTableTechnologyEntry;
import android.nfc.T4tNdefNfcee;
import android.nfc.T4tNdefNfceeCcFileInfo;
import android.nfc.Tag;
import android.nfc.WlcListenerDeviceInfo;
import android.nfc.cardemulation.ApduServiceInfo;
import android.nfc.cardemulation.CardEmulation;
import android.nfc.tech.IsoDep;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.RequiresDevice;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.PropertyUtil;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(JUnit4.class)
public class NfcAdapterTest {

    private static final long MAX_POLLING_PAUSE_TIMEOUT = 40000;
    static final int MOCK_NATIVE_HANDLE = 0;
    static boolean sTagRemoved = false;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    private Context mContext;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private boolean supportsHardware() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_NFC_ANY);
    }

    private int getVendorApiLevel() {
        return SystemProperties.getInt("ro.board.api_level", 0);
    }

    private static boolean isEmulator() {
        return PropertyUtil.propertyEquals("ro.hardware", "cutf_cvm");
    }

    @Before
    public void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);
        mContext = spy(new ContextWrapper(InstrumentationRegistry.getContext()));
        assumeTrue("Device must support NFC", supportsHardware());

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(mContext);
        assertNotNull("NFC Adapter is null", adapter);
        assertTrue("NFC Adapter could not be enabled", NfcUtils.enableNfc(adapter, mContext));
    }

    @Test
    public void testGetDefaultAdapter() {
        NfcAdapter adapter = getDefaultAdapter();
        assertNotNull(adapter);
    }

    @Test
    public void testAddAndRemoveNfcUnlockHandler() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC));
        NfcAdapter adapter = getDefaultAdapter();
        CtsNfcUnlockHandler unlockHandler = new CtsNfcUnlockHandler();

        adapter.addNfcUnlockHandler(unlockHandler, new String[]{"IsoDep"});
        adapter.removeNfcUnlockHandler(unlockHandler);
    }

    @Test
    public void testEnableAndDisable() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        assertTrue(adapter.isEnabled());

        // Disable NFC
        assertTrue(NfcUtils.disableNfc(adapter, mContext));
        assertFalse(adapter.isEnabled());

        // Re-enable NFC
        assertTrue(NfcUtils.enableNfc(adapter, mContext));
        assertTrue(adapter.isEnabled());
    }

    @Test
    public void testEnableAndDisablePersist() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        assertTrue(adapter.isEnabled());

        // Disable NFC
        assertTrue(NfcUtils.disableNfc(adapter, mContext, /* persist = */ false));
        assertFalse(adapter.isEnabled());

        // Re-enable NFC
        assertTrue(NfcUtils.enableNfc(adapter, mContext));
        assertTrue(adapter.isEnabled());
    }

    @Test
    public void testEnableAndDisableForegroundDispatch() throws RemoteException {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC));
        NfcAdapter adapter = getDefaultAdapter();
        Activity activity = createAndResumeActivity();
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
            NfcFCardEmulationActivity.class);
        PendingIntent pendingIntent
            = PendingIntent.getActivityAsUser(ApplicationProvider.getApplicationContext(), 0,
            intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE, null,
            UserHandle.of(mContext.getUser().getIdentifier()));
        String[][] techLists = new String[][]{new String[]{}};

        adapter.enableForegroundDispatch(activity, pendingIntent, null, techLists);
        adapter.disableForegroundDispatch(activity);
    }

    @Test
    public void testEnableAndDisableReaderMode() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC));
        NfcAdapter adapter = getDefaultAdapter();
        Activity activity = createAndResumeActivity();
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
            NfcFCardEmulationActivity.class);
        PendingIntent pendingIntent
            = PendingIntent.getActivityAsUser(ApplicationProvider.getApplicationContext(), 0,
            intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE, null,
            UserHandle.of(mContext.getUser().getIdentifier()));
        String[][] techLists = new String[][]{new String[]{}};
        // Move activity to the foreground
        adapter.enableForegroundDispatch(activity, pendingIntent, null, techLists);

        adapter.enableReaderMode(activity, new CtsReaderCallback(),
            NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, new Bundle());
        adapter.disableReaderMode(activity);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_READER_OPTION)
    public void testEnableAndDisableReaderOption() throws NoSuchFieldException, RemoteException {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC));
        NfcAdapter adapter = getDefaultAdapter();
        assumeTrue("Device must support reader option", adapter.isReaderOptionSupported());

        assertTrue(adapter.enableReaderOption(/* enable = */ true));
        assertTrue(adapter.isReaderOptionEnabled());

        assertTrue(adapter.enableReaderOption(/* enable = */ false));
        assertFalse(adapter.isReaderOptionEnabled());
    }

    @Test
    public void testEnableAndDisableSecureNfc() throws RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        assumeTrue("Device must support secure NFC", adapter.isSecureNfcSupported());

        assertTrue(adapter.enableSecureNfc(/* enable = */ true));
        assertTrue(adapter.isSecureNfcEnabled());

        assertTrue(adapter.enableSecureNfc(/* enable = */ false));
        assertFalse(adapter.isSecureNfcEnabled());
    }

    @Test
    public void testGetNfcAntennaInfo() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        NfcAntennaInfo antenna = adapter.getNfcAntennaInfo();
        assertNotNull(antenna);
    }

    @Test
    public void testIgnore() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        CtsOnTagRemovedListener listener = new CtsOnTagRemovedListener();
        sTagRemoved = false;
        IsoDep isoDep = createIsoDepTag();

        assertTrue(adapter.ignore(isoDep.getTag(), 0, listener, null));
        // Wait a second to make sure listener callback fired
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore
        }
        assertTrue(sTagRemoved);
    }

    @Test
    public void testEnableThenDisableControllerAlwaysOnSupported()
        throws NoSuchFieldException, InterruptedException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .getUiAutomation().adoptShellPermissionIdentity(NFC_SET_CONTROLLER_ALWAYS_ON);
        assumeTrue("Device must support controller always on",
            adapter.isControllerAlwaysOnSupported());
        NfcControllerAlwaysOnListener cb = null;
        CountDownLatch countDownLatch;
        try {
            countDownLatch = new CountDownLatch(1);
            cb = new NfcControllerAlwaysOnListener(countDownLatch);
            adapter.registerControllerAlwaysOnListener(Executors.newSingleThreadExecutor(), cb);
            assertTrue(adapter.setControllerAlwaysOn(true));
            assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));
            assertTrue(adapter.isControllerAlwaysOn());
            adapter.unregisterControllerAlwaysOnListener(cb);

            countDownLatch = new CountDownLatch(1);
            cb = new NfcControllerAlwaysOnListener(countDownLatch);
            adapter.registerControllerAlwaysOnListener(Executors.newSingleThreadExecutor(), cb);
            assertTrue(adapter.setControllerAlwaysOn(false));
            assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));
            assertFalse(adapter.isControllerAlwaysOn());
            adapter.unregisterControllerAlwaysOnListener(cb);
        } finally {
            if (cb != null)
                adapter.unregisterControllerAlwaysOnListener(cb);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
        }

    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testAdapterState() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();

        assertTrue(NfcUtils.enableNfc(adapter, mContext));
        assertTrue(adapter.getAdapterState() == NfcAdapter.STATE_ON);

        assertTrue(NfcUtils.disableNfc(adapter, mContext));
        assertTrue(adapter.getAdapterState() == NfcAdapter.STATE_OFF);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_SET_DISCOVERY_TECH)
    public void testResetDiscoveryTechnology() {
        NfcAdapter adapter = getDefaultAdapter();
        Activity activity = createAndResumeActivity();
        adapter.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_KEEP,
                NfcAdapter.FLAG_LISTEN_KEEP);
        adapter.resetDiscoveryTechnology(activity);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_SET_DISCOVERY_TECH)
    public void testSetDiscoveryTechnology() {
        NfcAdapter adapter = getDefaultAdapter();
        Activity activity = createAndResumeActivity();
        adapter.setDiscoveryTechnology(activity,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_NFC_F,
                NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_A | NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_B
                | NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_F);
        adapter.resetDiscoveryTechnology(activity);
        adapter.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_DISABLE,
                NfcAdapter.FLAG_LISTEN_KEEP);
        adapter.resetDiscoveryTechnology(activity);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_SET_DEFAULT_DISC_TECH)
    public void testSetDefaultDiscoveryTechnology() {
        NfcAdapter adapter = getDefaultAdapter();
        Activity activity = createAndResumeActivity();
        adapter.setDiscoveryTechnology(activity,
                NfcAdapter.FLAG_READER_KEEP,
                NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_B
                | NfcAdapter.FLAG_SET_DEFAULT_TECH);
        adapter.setDiscoveryTechnology(activity, NfcAdapter.FLAG_READER_KEEP,
                NfcAdapter.FLAG_LISTEN_KEEP | NfcAdapter.FLAG_SET_DEFAULT_TECH | 0xff);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_SET_DEFAULT_DISC_TECH)
    public void testSetDiscoveryTechnologyWithoutActivity() {
        NfcAdapter adapter = getDefaultAdapter();
        // CTS has privileged permission to set discovery technology with null activity.
        // This test is to ensure that the API does not crash or throw any exceptions.
        adapter.setDiscoveryTechnology(null,
                NfcAdapter.FLAG_READER_KEEP,
                NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_B
                | NfcAdapter.FLAG_SET_DEFAULT_TECH);
        adapter.setDiscoveryTechnology(null, NfcAdapter.FLAG_READER_KEEP,
                NfcAdapter.FLAG_LISTEN_KEEP | NfcAdapter.FLAG_SET_DEFAULT_TECH | 0xff);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_SET_DISCOVERY_TECH)
    public void testResetDiscoveryTechnologyWithoutActivity() {
        NfcAdapter adapter = getDefaultAdapter();
        // CTS has privileged permission to set discovery technology with null activity.
        // This test is to ensure that the API does not crash or throw any exceptions.
        adapter.setDiscoveryTechnology(null,
                NfcAdapter.FLAG_READER_KEEP,
                NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_B
                        | NfcAdapter.FLAG_SET_DEFAULT_TECH);
        adapter.resetDiscoveryTechnology(null);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_MAINLINE)
    public void testSetReaderMode() {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC));
        NfcAdapter adapter = getDefaultAdapter();
        // Verify the API does not crash or throw any exceptions.
        adapter.setReaderModePollingEnabled(true);
        adapter.setReaderModePollingEnabled(false);
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testAllowTransaction() {
        assumeTrue(getVendorApiLevel() > 202404);
        ComponentName originalDefault = null;
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            assumeObserveModeSupported(adapter);
            boolean result = adapter.setObserveModeEnabled(false);
            assertTrue(result);
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDisallowTransaction() {
        assumeTrue(getVendorApiLevel() > 202404);
        ComponentName originalDefault = null;
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);

            assumeObserveModeSupported(adapter);
            boolean result = adapter.setObserveModeEnabled(true);
            assertTrue(result);
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }


    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDefaultObserveModePaymentDynamic() {
        assumeTrue(getVendorApiLevel() > 202404);
        ComponentName originalDefault = null;
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), true);
            originalDefault = setDefaultPaymentService(CustomHostApduService.class);
            CardEmulationTest.ensurePreferredService(CustomHostApduService.class, mContext);
            assertTrue(adapter.isObserveModeEnabled());
            setDefaultPaymentService(CtsMyHostApduService.class);
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            assertFalse(adapter.isObserveModeEnabled());
        } finally {
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), false);
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testDefaultObserveModeForegroundDynamic() {
        assumeTrue(getVendorApiLevel() > 202404);
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        assumeObserveModeSupported(adapter);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        try {
            Activity activity = createAndResumeActivity();
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), true);
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CtsMyHostApduService.class), false);
            assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CustomHostApduService.class)));
            CardEmulationTest.ensurePreferredService(CustomHostApduService.class, mContext);
            waitForObserveModeState(adapter, true);
            assertTrue(cardEmulation.setPreferredService(activity,
                    new ComponentName(mContext, CtsMyHostApduService.class)));
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            adapter.notifyHceDeactivated();
            assertFalse(adapter.isObserveModeEnabled());
        } finally {
            cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                    CustomHostApduService.class), false);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testDefaultObserveModeOnlyWithServiceChange() {
        assumeTrue(getVendorApiLevel() > 202404);
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        assumeObserveModeSupported(adapter);
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                CtsMyHostApduService.class), true);
        cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                CustomHostApduService.class), false);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME, () -> {
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            assertTrue("observe mode isn't enabled after setting preferred service to one that"
                    + " defaults it on", adapter.isObserveModeEnabled());
            assertTrue("set observe mode to false failed", adapter.setObserveModeEnabled(false));
            assertFalse("observe mode is still enabled after setting it to false",
                    adapter.isObserveModeEnabled());
            try {
                Activity activity = createAndResumeActivity();
                assertTrue(cardEmulation.setPreferredService(activity,
                        new ComponentName(mContext, CtsMyHostApduService.class)));
                CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
                assertFalse("observe mode enabled after setting preferred service to one that"
                        + " defaults it enabled, even though preferred service didn't change",
                        adapter.isObserveModeEnabled());
                assertTrue("set observe mode enabled failed", adapter.setObserveModeEnabled(true));
                assertTrue("observe mode disabled after enabling it",
                        adapter.isObserveModeEnabled());
                assertTrue("setting preferred service failed",
                        cardEmulation.setPreferredService(activity,
                        new ComponentName(mContext, CustomHostApduService.class)));
                CardEmulationTest.ensurePreferredService(CustomHostApduService.class, mContext);
                assertFalse("observe mode enabled after setting preferred service that disables it",
                        adapter.isObserveModeEnabled());
            } finally {
                cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                        CustomHostApduService.class), false);
                cardEmulation.setShouldDefaultToObserveModeForService(new ComponentName(mContext,
                        CtsMyHostApduService.class), false);
                adapter.notifyHceDeactivated();
            }
        });
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    @RequiresFlagsDisabled(android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED)
    public void testDefaultObserveModePayment() {
        assumeTrue(getVendorApiLevel() > 202404);
        ComponentName originalDefault = null;
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        try {
            originalDefault = setDefaultPaymentService(BackgroundHostApduService.class);
            CardEmulationTest.ensurePreferredService(BackgroundHostApduService.class, mContext);
            assertTrue(adapter.isObserveModeEnabled());
            setDefaultPaymentService(CtsMyHostApduService.class);
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
            assertFalse(adapter.isObserveModeEnabled());
        } finally {
            setDefaultPaymentService(originalDefault);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(android.nfc.Flags.FLAG_NFC_OBSERVE_MODE)
    public void testDefaultObserveModeForeground() {
        assumeTrue(getVendorApiLevel() > 202404);
        NfcAdapter adapter = getDefaultAdapter();
        CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        cardEmulation.setShouldDefaultToObserveModeForService(
            new ComponentName(mContext, CtsMyHostApduService.class), false);
        Activity activity = createAndResumeActivity();
        adapter.notifyHceDeactivated();
        assumeObserveModeSupported(adapter);
        assertTrue(cardEmulation.setPreferredService(activity,
                new ComponentName(mContext, BackgroundHostApduService.class)));
        CardEmulationTest.ensurePreferredService(BackgroundHostApduService.class, mContext);
        assertTrue(adapter.isObserveModeEnabled());
        assertTrue(cardEmulation.setPreferredService(activity,
                new ComponentName(mContext, CtsMyHostApduService.class)));
        CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
        assertFalse(adapter.isObserveModeEnabled());
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testAllowTransaction_walletRoleEnabled() {
        assumeTrue(getVendorApiLevel() > 202404);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = getDefaultAdapter();
            adapter.notifyHceDeactivated();
            assumeObserveModeSupported(adapter);
            adapter.setObserveModeEnabled(false);
            assertFalse(adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
        });
    }

    private void waitForObserveModeState(NfcAdapter adapter, boolean expectedState) {
        try {
            PollingCheck.check(
                    "Timed out waiting for Observe Mode to be "
                            + (expectedState ? "ENABLED" : "DISABLED"),
                    5000, /* timeout in ms */
                    () -> adapter.isObserveModeEnabled() == expectedState);
        } catch (Exception e) {
            throw new RuntimeException("Exception while waiting for Observe Mode state", e);
        }
    }

    @Test
    @RequiresFlagsEnabled({
            com.android.nfc.module.flags.Flags.FLAG_NFCSTACK_26Q2_UPDATES,
            android.nfc.Flags.FLAG_NFC_OBSERVE_MODE
    })
    public void testAllowOneTransaction() throws Exception {
        assumeTrue(getVendorApiLevel() > 202404);
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();

        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME, () -> {

            ComponentName originalDefault = null;
            try {
                originalDefault = setDefaultPaymentService(CtsMyHostApduService.class);
                CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);
                assumeObserveModeSupported(adapter);

                assertTrue("Failed to enable observe mode as a precondition",
                        adapter.setObserveModeEnabled(true));

                waitForObserveModeState(adapter, true);

                adapter.allowOneTransaction();

                waitForObserveModeState(adapter, false);

            } finally {
                if (originalDefault != null) {
                    setDefaultPaymentService(originalDefault);
                }

                adapter.setObserveModeEnabled(false);
                adapter.notifyHceDeactivated();
            }
        });
    }

    @Test
    @RequiresFlagsEnabled({android.nfc.Flags.FLAG_NFC_OBSERVE_MODE,
            android.permission.flags.Flags.FLAG_WALLET_ROLE_ENABLED})
    public void testDisallowTransaction_walletRoleEnabled() {
        assumeTrue(getVendorApiLevel() > 202404);
        WalletRoleTestUtils.runWithRole(mContext, WalletRoleTestUtils.CTS_PACKAGE_NAME, () -> {
            NfcAdapter adapter = getDefaultAdapter();
            adapter.notifyHceDeactivated();
            assumeObserveModeSupported(adapter);
            adapter.setObserveModeEnabled(true);
            assertTrue(adapter.isObserveModeEnabled());
            adapter.notifyHceDeactivated();
        });
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_CHARGING)
    public void testDisableAndEnableNfcCharging() throws NoSuchFieldException, RemoteException {
        NfcAdapter adapter = getDefaultAdapter();

        // Disable charging feature
        assertTrue(adapter.setWlcEnabled(false));
        assertFalse(adapter.isWlcEnabled());

        // Enable charging feature
        assertTrue(adapter.setWlcEnabled(true));
        assertTrue(adapter.isWlcEnabled());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_VENDOR_CMD)
    public void testSendVendorCmd() throws InterruptedException, RemoteException {
        assumeVsrApiGreaterThanUdc();
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcVendorNciCallback cb =
                new NfcVendorNciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            nfcAdapter.registerNfcVendorNciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Android GET_CAPS command
            int gid = 0xF;
            int oid = 0xC;
            byte[] payload = new byte[1];
            payload[0] = 0;
            nfcAdapter.sendVendorNciMessage(NfcAdapter.MESSAGE_TYPE_COMMAND, gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.gid).isEqualTo(gid);
            assertThat(cb.oid).isEqualTo(oid);
            assertThat(cb.payload).isNotEmpty();
        } finally {
            nfcAdapter.unregisterNfcVendorNciCallback(cb);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_VENDOR_CMD)
    public void testSendVendorCmd_payloadSize256_shouldNoResponse() throws InterruptedException {
        assumeVsrApiGreaterThanUdc();
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcVendorNciCallback cb =
                new NfcVendorNciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            nfcAdapter.registerNfcVendorNciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Android GET_CAPS command
            int gid = 0x2F;
            int oid = 0x0C;
            byte[] payload = new byte[256];
            Arrays.fill(payload, (byte) 0x00);
            nfcAdapter.sendVendorNciMessage(NfcAdapter.MESSAGE_TYPE_COMMAND, gid, oid, payload);

            // Verify no response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isFalse();
        } finally {
            nfcAdapter.unregisterNfcVendorNciCallback(cb);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_VENDOR_CMD)
    public void testSendVendorCmd_payloadSize255() throws InterruptedException {
        // Skip test since is unable to send more than 1 payload on Cuttlefish Nfc.
        // If test on Cuttlefish, write payload to /dev/hvc12 will no response and
        // NFC never to start again.
        assumeFalse("Test is skipped on virtual device ", isEmulator());
        assumeVsrApiGreaterThanUdc();
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcVendorNciCallback cb =
                new NfcVendorNciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            nfcAdapter.registerNfcVendorNciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Android GET_CAPS command
            int gid = 0x2F;
            int oid = 0x0C;
            byte[] payload = new byte[255];
            Arrays.fill(payload, (byte) 0);
            nfcAdapter.sendVendorNciMessage(NfcAdapter.MESSAGE_TYPE_COMMAND, gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.payload).isNotEmpty();
        } finally {
            nfcAdapter.unregisterNfcVendorNciCallback(cb);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_STATE_CHANGE)
    public void testEnableByDeviceOwner() throws NoSuchFieldException, RemoteException {
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(new UserHandle(UserHandle.getCallingUserId()));
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(ComponentName.createRelative("com.android.nfc", ".AdapterTest"));
        when(mContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.enable();
        assertTrue(result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_STATE_CHANGE)
    public void testDisableByDeviceOwner() throws NoSuchFieldException, RemoteException {
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        when(mDevicePolicyManager.getDeviceOwnerUser())
                .thenReturn(new UserHandle(UserHandle.getCallingUserId()));
        when(mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .thenReturn(ComponentName.createRelative("com.android.nfc", ".AdapterTest"));
        when(mContext.getSystemService(DevicePolicyManager.class))
                .thenReturn(mDevicePolicyManager);
        NfcAdapter adapter = getDefaultAdapter();
        boolean result = adapter.disable();
        assertTrue(result);
        result = adapter.enable();
        assertTrue(result);
    }

    @Test
    public void testShouldDefaultToObserveModeAfterNfcOffOn() throws InterruptedException {
        assumeTrue(getVendorApiLevel() > 202404);
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        assumeObserveModeSupported(adapter);
        adapter.notifyHceDeactivated();
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName ctsService = new ComponentName(mContext, CtsMyHostApduService.class);

        try {
            assertTrue(cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    true));

            assertTrue(cardEmulation.setPreferredService(activity, ctsService));
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);

            waitForObserveModeState(adapter, true);
            assertTrue(NfcUtils.disableNfc(adapter, mContext));
            assertTrue(NfcUtils.enableNfc(adapter, mContext));
            waitForObserveModeState(adapter, true);
        } finally {
            cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    false);
            cardEmulation.unsetPreferredService(activity);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    public void testShouldDefaultToObserveModeWithNfcOff() throws InterruptedException {
        assumeTrue(getVendorApiLevel() > 202404);
        NfcAdapter adapter = getDefaultAdapter();
        adapter.notifyHceDeactivated();
        assumeObserveModeSupported(adapter);
        Activity activity = createAndResumeActivity();
        final CardEmulation cardEmulation = CardEmulation.getInstance(adapter);
        ComponentName ctsService = new ComponentName(mContext, CtsMyHostApduService.class);
        try {
            assertTrue(NfcUtils.disableNfc(adapter, mContext));
            assertTrue(cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    true));

            assertTrue(cardEmulation.setPreferredService(activity, ctsService));
            CardEmulationTest.ensurePreferredService(CtsMyHostApduService.class, mContext);

            assertTrue(NfcUtils.enableNfc(adapter, mContext));
            waitForObserveModeState(adapter, true);
        } finally {
            cardEmulation.setShouldDefaultToObserveModeForService(ctsService,
                    false);
            cardEmulation.unsetPreferredService(activity);
            adapter.notifyHceDeactivated();
        }
    }

    @Test
    @RequiresFlagsEnabled(com.android.nfc.module.flags.Flags.FLAG_OEM_EXTENSION_25Q4)
    public void testIsExitFramesSupported() {
        NfcAdapter adapter = getDefaultAdapter();
        adapter.isExitFramesSupported();
    }

    @Test
    @RequiresFlagsEnabled(com.android.nfc.module.flags.Flags.FLAG_NFC_POWER_SAVING_MODE)
    public void testTogglePowerSavingMode() {
        assumeTrue(getDefaultAdapter().isPowerSavingModeSupported());

        NfcAdapter adapter = getDefaultAdapter();
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().adoptShellPermissionIdentity(NFC_SET_CONTROLLER_ALWAYS_ON);
        try {
            adapter.setPowerSavingMode(true);
            assertTrue(adapter.isPowerSavingModeEnabled());

            adapter.setPowerSavingMode(false);
            assertFalse(adapter.isPowerSavingModeEnabled());
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OEM_EXTENSION)
    public void testOemExtension() throws InterruptedException {
        CountDownLatch tagDetectedCountDownLatch = new CountDownLatch(3);
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();
        assertNotNull(nfcOemExtension);
        NfcOemExtensionCallback cb =
                new NfcOemExtensionCallback(tagDetectedCountDownLatch);
        try {
            nfcOemExtension.registerCallback(
                    Executors.newSingleThreadExecutor(), cb);
            tagDetectedCountDownLatch.await();

            // TODO: Fix these tests as we add more functionality to this API surface.
            nfcOemExtension.clearPreference();
            nfcOemExtension.synchronizeScreenState();
            Map<String, Integer> nfceeMap = nfcOemExtension.getActiveNfceeList();
            for (var nfcee : nfceeMap.entrySet()) {
                assertThat(nfcee.getKey()).isNotEmpty();
            }
            nfcOemExtension.hasUserEnabledNfc();
            nfcOemExtension.isTagPresent();
            RoutingStatus status = nfcOemExtension.getRoutingStatus();
            status.getDefaultRoute();
            status.getDefaultIsoDepRoute();
            status.getDefaultOffHostRoute();
            status.getDefaultFelicaRoute();
            nfcOemExtension.setAutoChangeEnabled(true);
            assertThat(nfcOemExtension.isAutoChangeEnabled()).isTrue();
            T4tNdefNfcee ndefNfcee = nfcOemExtension.getT4tNdefNfcee();
            assertThat(ndefNfcee).isNotNull();
            if (ndefNfcee.isSupported()) {
                String data = "0123456789012345678901234567890123456789";
                NdefRecord record = NdefRecord.createTextRecord("en", data);
                NdefMessage message = new NdefMessage(new NdefRecord[]{record});
                byte[] ndefData = message.toByteArray();
                byte[] FILE_ID_NDEF_TEST = new byte[]{(byte)0xE1, 0x04};
                assertThat(ndefNfcee.writeData(bytesToInt(FILE_ID_NDEF_TEST), ndefData))
                               .isEqualTo(T4tNdefNfcee.WRITE_DATA_SUCCESS);
                assertThat(ndefNfcee.readData(bytesToInt(FILE_ID_NDEF_TEST))).isEqualTo(ndefData);
                assertThat(ndefNfcee.isOperationOngoing()).isEqualTo(false);
                T4tNdefNfceeCcFileInfo ccFileInfo = ndefNfcee.readCcfile();
                assertThat(ccFileInfo).isNotNull();
                assertThat(ccFileInfo.getCcFileLength()).isGreaterThan(0);
                assertThat(ccFileInfo.getVersion()).isGreaterThan(0);
                assertThat(ccFileInfo.getFileId()).isGreaterThan(5);
                assertThat(ccFileInfo.getMaxSize()).isGreaterThan(0);
                assertThat(ccFileInfo.isReadAllowed()).isEqualTo(true);
                assertThat(ccFileInfo.isWriteAllowed()).isEqualTo(true);
                assertThat(ndefNfcee.clearData()).isEqualTo(T4tNdefNfcee.CLEAR_DATA_SUCCESS);
            }
            if (Flags.nfcOverrideRecoverRoutingTable()) {
                nfcOemExtension.overwriteRoutingTable(PROTOCOL_AND_TECHNOLOGY_ROUTE_ESE,
                        PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET, PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET,
                        PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET);
            }
            List<NfcRoutingTableEntry> entries = nfcOemExtension.getRoutingTable();
            assertThat(entries).isNotNull();
            for (NfcRoutingTableEntry entry : entries) {
                switch (entry.getType()) {
                    case TYPE_AID:
                        ((RoutingTableAidEntry) entry).getAid();
                        break;
                    case TYPE_PROTOCOL:
                        ((RoutingTableProtocolEntry) entry).getProtocol();
                        break;
                    case TYPE_TECHNOLOGY:
                        ((RoutingTableTechnologyEntry) entry).getTechnology();
                        break;
                    case TYPE_SYSTEM_CODE:
                        ((RoutingTableSystemCodeEntry) entry).getSystemCode();
                        break;
                    default:
                }
                assertThat(entries.getFirst().getRouteType())
                        .isAnyOf(
                                PROTOCOL_AND_TECHNOLOGY_ROUTE_DH,
                                PROTOCOL_AND_TECHNOLOGY_ROUTE_ESE,
                                PROTOCOL_AND_TECHNOLOGY_ROUTE_UICC,
                                PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET,
                                PROTOCOL_AND_TECHNOLOGY_ROUTE_DEFAULT,
                                PROTOCOL_AND_TECHNOLOGY_ROUTE_NDEF_NFCEE);
                entries.getFirst().getNfceeId();
            }

            if (com.android.nfc.module.flags.Flags.oemExtension25q4()) {
                nfcOemExtension.overwriteRoutingTable(PROTOCOL_AND_TECHNOLOGY_ROUTE_ESE,
                        PROTOCOL_AND_TECHNOLOGY_ROUTE_ESE, PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET,
                        PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET, PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET);

                entries = nfcOemExtension.getRoutingTable();
                assertThat(entries).isNotNull();
                for (NfcRoutingTableEntry entry : entries) {
                    switch (entry.getType()) {
                        case TYPE_AID:
                            ((RoutingTableAidEntry) entry).getAid();
                            break;
                        case TYPE_PROTOCOL:
                            ((RoutingTableProtocolEntry) entry).getProtocol();
                            break;
                        case TYPE_TECHNOLOGY:
                            ((RoutingTableTechnologyEntry) entry).getTechnology();
                            break;
                        case TYPE_SYSTEM_CODE:
                            ((RoutingTableSystemCodeEntry) entry).getSystemCode();
                            break;
                        default:
                    }
                    assertThat(entries.getFirst().getRouteType())
                            .isAnyOf(
                                    PROTOCOL_AND_TECHNOLOGY_ROUTE_DH,
                                    PROTOCOL_AND_TECHNOLOGY_ROUTE_ESE,
                                    PROTOCOL_AND_TECHNOLOGY_ROUTE_UICC,
                                    PROTOCOL_AND_TECHNOLOGY_ROUTE_UNSET,
                                    PROTOCOL_AND_TECHNOLOGY_ROUTE_DEFAULT,
                                    PROTOCOL_AND_TECHNOLOGY_ROUTE_NDEF_NFCEE);
                    entries.getFirst().getNfceeId();
                }
            }
            nfcOemExtension.forceRoutingTableCommit();
            assertEquals(MAX_POLLING_PAUSE_TIMEOUT,
                    nfcOemExtension.getMaxPausePollingTimeoutMills());
        } finally {
            nfcOemExtension.unregisterCallback(cb);
        }
    }

    @Test
    @RequiresDevice
    @RequiresFlagsEnabled(com.android.nfc.module.flags.Flags.FLAG_OEM_EXTENSION_25Q4)
    public void testOemExtensionEmulateNfcTechnologyATag()
            throws InterruptedException, RemoteException {
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();
        assertNotNull(nfcOemExtension);

        byte[] uid = new byte[] { 0x01, 0x02, 0x03, 0x04 };
        nfcOemExtension.emulateNfcTechnologyATag(true, (byte) 0x6, (byte) 0xC,
                (byte) 0x20, uid, (byte) 0x40, null);

        nfcOemExtension.emulateNfcTechnologyATag(false, (byte) 0x4, (byte) 0x0,
                (byte) 0x20, uid, (byte) 0x40, null);
    }

    @Test
    @RequiresDevice
    @Ignore("b/404565741")
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OEM_EXTENSION)
    public void testOemExtensionMaybeTriggerFirmwareUpdateWhenEnabled()
            throws InterruptedException, RemoteException {
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();
        assertNotNull(nfcOemExtension);
        nfcOemExtension.maybeTriggerFirmwareUpdate();
    }

    @Test
    @RequiresDevice
    @Ignore("b/404565741")
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OEM_EXTENSION)
    public void testOemExtensionMaybeTriggerFirmwareUpdateWhenDisabled()
            throws InterruptedException, RemoteException {
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        // Disable NFC
        assertTrue(NfcUtils.disableNfc(nfcAdapter, mContext));
        assertFalse(nfcAdapter.isEnabled());
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();
        assertNotNull(nfcOemExtension);
        nfcOemExtension.maybeTriggerFirmwareUpdate();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OEM_EXTENSION)
    public void testOemExtensionTriggerInitialization()
            throws InterruptedException, RemoteException {
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();
        assertNotNull(nfcOemExtension);
        nfcOemExtension.triggerInitialization();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OEM_EXTENSION)
    public void testOemExtensionCallback()
            throws RemoteException, InterruptedException {
        CountDownLatch tagDetectedCountDownLatch = new CountDownLatch(5);
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();

        assertNotNull(nfcOemExtension);
        NfcOemExtension.Callback cb = new NfcOemExtensionCallback(tagDetectedCountDownLatch);
        try {
            nfcOemExtension.registerCallback(
                    Executors.newSingleThreadExecutor(), cb);
            NfcOemExtension.NfcOemExtensionCallback callback =
                    nfcOemExtension.getOemNfcExtensionCallback();
            callback.onTagConnected(true);
            tagDetectedCountDownLatch.await();
            // Test onStateUpdated
            callback.onStateUpdated(NfcAdapter.STATE_OFF);

            // Test onApplyRouting
            CountDownLatch latch = new CountDownLatch(1);
            MockResultReceiver isSkipped = new MockResultReceiver(latch);
            callback.onApplyRouting(isSkipped);
            latch.await();
            assertEquals(0, isSkipped.getResultCode());

            // Test onNdefRead
            latch = new CountDownLatch(1);
            MockResultReceiver isNdefReadSkipped = new MockResultReceiver(latch);
            callback.onNdefRead(isNdefReadSkipped);
            latch.await();
            assertEquals(1, isNdefReadSkipped.getResultCode());

            // Test onEnableRequested
            latch = new CountDownLatch(1);
            MockResultReceiver isEnableAllowed = new MockResultReceiver(latch);
            callback.onEnable(isEnableAllowed);
            latch.await();
            assertEquals(1, isEnableAllowed.getResultCode());

            // Test onDisableRequested
            latch = new CountDownLatch(1);
            MockResultReceiver isDisableAllowed = new MockResultReceiver(latch);
            callback.onDisable(isDisableAllowed);
            latch.await();
            assertEquals(1, isDisableAllowed.getResultCode());

            callback.onBootStarted();
            callback.onEnableStarted();
            callback.onDisableStarted();
            callback.onBootFinished(0);
            callback.onEnableFinished(0);
            callback.onDisableFinished(0);

            // Test onTagDispatch
            latch = new CountDownLatch(1);
            MockResultReceiver isTagDispatchSkipped = new MockResultReceiver(latch);
            callback.onTagDispatch(isTagDispatchSkipped);
            latch.await();
            assertEquals(1, isTagDispatchSkipped.getResultCode());

            // Test onRoutingChanged
            latch = new CountDownLatch(1);
            MockResultReceiver isCommitRoutingSkipped = new MockResultReceiver(latch);
            callback.onRoutingChanged(isCommitRoutingSkipped);
            latch.await();
            assertEquals(0, isCommitRoutingSkipped.getResultCode());

            callback.onHceEventReceived(HCE_ACTIVATE);
            callback.onReaderOptionChanged(true);
            callback.onCardEmulationActivated(true);
            callback.onRfFieldDetected(true);
            callback.onRfDiscoveryStarted(true);
            callback.onEeListenActivated(true);
            callback.onEeUpdated();

            // Test onGetOemAppSearchIntent
            List<String> packages = new ArrayList<>();
            packages.add("com.example.app1");
            packages.add("com.example.app2");
            latch = new CountDownLatch(1);
            MockResultReceiver intentReceiver = new MockResultReceiver(latch);
            callback.onGetOemAppSearchIntent(packages, intentReceiver);
            latch.await();
            assertNotNull(intentReceiver.getResultData());

            // Test onNdefMessage
            latch = new CountDownLatch(1);
            MockResultReceiver receiver = new MockResultReceiver(latch);
            callback.onNdefMessage(null, null, receiver);
            latch.await();
            assertEquals(1, receiver.getResultCode());

            // Test onLaunchHceAppChooserActivity
            String selectedAid = "A0000000031010";
            List<ApduServiceInfo> services = new ArrayList<>();
            ComponentName failedComponent =
                    new ComponentName("com.example", "com.example.Activity");
            String category = "android.nfc.cardemulation.category.DEFAULT";
            callback.onLaunchHceAppChooserActivity(
                    selectedAid, services, failedComponent, category);

            // Test onLaunchHceTapAgainDialog
            callback.onLaunchHceTapAgainActivity(null, category);

            // Test onRoutingTableFull
            callback.onRoutingTableFull();

            // Test onLogEventNotified
            OemLogItems logItem =
                    new OemLogItems.Builder(OemLogItems.LOG_ACTION_NFC_TOGGLE).build();
            callback.onLogEventNotified(logItem);

            // Test onExtractOemPackages
            latch = new CountDownLatch(1);
            MockResultReceiver packageReceiver = new MockResultReceiver(latch);
            callback.onExtractOemPackages(null, packageReceiver);
            latch.await();
            assertNotNull(packageReceiver.getResultData());
        } finally {
            nfcOemExtension.unregisterCallback(cb);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_OEM_EXTENSION)
    public void testOemExtensionSetControllerAlwaysOn() throws InterruptedException {
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();
        assertNotNull(nfcOemExtension);
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().adoptShellPermissionIdentity(NFC_SET_CONTROLLER_ALWAYS_ON);
        assumeTrue("Device must support controller always on",
            nfcAdapter.isControllerAlwaysOnSupported());
        NfcControllerAlwaysOnListener cb = null;
        CountDownLatch countDownLatch;
        try {
            countDownLatch = new CountDownLatch(1);
            cb = new NfcControllerAlwaysOnListener(countDownLatch);
            nfcAdapter.registerControllerAlwaysOnListener(
                    Executors.newSingleThreadExecutor(), cb);
            nfcOemExtension.setControllerAlwaysOnMode(NfcOemExtension.ENABLE_TRANSPARENT);
            assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));
            nfcAdapter.unregisterControllerAlwaysOnListener(cb);

            countDownLatch = new CountDownLatch(1);
            cb = new NfcControllerAlwaysOnListener(countDownLatch);
            nfcAdapter.registerControllerAlwaysOnListener(
                    Executors.newSingleThreadExecutor(), cb);
            nfcOemExtension.setControllerAlwaysOnMode(NfcOemExtension.DISABLE);
            assertTrue(countDownLatch.await(1, TimeUnit.SECONDS));
            nfcAdapter.unregisterControllerAlwaysOnListener(cb);
        } finally {
            if (cb != null) nfcAdapter.unregisterControllerAlwaysOnListener(cb);
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresDevice
    @RequiresFlagsEnabled(com.android.nfc.module.flags.Flags.FLAG_NFCSTACK_26Q2_UPDATES)
    public void testOemExtensionGetRfDiscoverConfigurations()
            throws InterruptedException, RemoteException {
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcOemExtension nfcOemExtension = nfcAdapter.getNfcOemExtension();
        assertNotNull(nfcOemExtension);

        List<RfDiscoverConfig> config = nfcOemExtension.getRfDiscoverConfigurations();
        assertNotNull(config);
        assertThat(config.size()).isGreaterThan(0);

        Activity activity = createAndResumeActivity();
        nfcAdapter.setDiscoveryTechnology(activity,
                NfcAdapter.FLAG_READER_NFC_B, NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_A);
        List<RfDiscoverConfig> config2 = nfcOemExtension.getRfDiscoverConfigurations();
        // There should be at least one Poll tech and one Listen tech
        assertThat(config2.size()).isAtLeast(2);

        for (RfDiscoverConfig c: config2) {
            // There should be no Tech-A Poll configuration
            if (c.getTechnologyMode() == RfDiscoverConfig.NFC_A_PASSIVE_POLL_MODE) {
                assertTrue("Incorrect Rf Discover configuration", false);
            }
        }

        nfcAdapter.resetDiscoveryTechnology(activity);
    }

    @Test
    @RequiresFlagsEnabled(com.android.nfc.module.flags.Flags.FLAG_TAP_TO_X)
    public void testGetGestureExchangeAid() {
        NfcAdapter adapter = getDefaultAdapter();
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().adoptShellPermissionIdentity(PERFORM_GESTURE_EXCHANGE);
        adapter.getGestureExchangeAid();
    }

    @Test
    @RequiresFlagsEnabled(com.android.nfc.module.flags.Flags.FLAG_TAP_TO_X)
    public void testRegisterAndUnregisterGestureExchangeCallbacks() {
        assumeTrue(getVendorApiLevel() > 202504);
        NfcAdapter nfcAdapter = getDefaultAdapter();
        assertNotNull(nfcAdapter);
        NfcAdapter.ReaderCallback cb = new CtsReaderCallback();
        try {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().adoptShellPermissionIdentity(PERFORM_GESTURE_EXCHANGE);
            nfcAdapter.registerGestureExchangeReaderCallback(
                    Executors.newSingleThreadExecutor(), cb);

        } finally {
            nfcAdapter.unregisterGestureExchangeReaderCallback(cb);
        }
    }

    private class NfcControllerAlwaysOnListener implements NfcAdapter.ControllerAlwaysOnListener {
        private final CountDownLatch mCountDownLatch;

        NfcControllerAlwaysOnListener(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onControllerAlwaysOnChanged(boolean isEnabled) {
            mCountDownLatch.countDown();
        }
    }

    private class NfcOemExtensionCallback implements NfcOemExtension.Callback {
        private final CountDownLatch mTagDetectedCountDownLatch;

        NfcOemExtensionCallback(CountDownLatch countDownLatch) {
            mTagDetectedCountDownLatch = countDownLatch;
        }

        @Override
        public void onTagConnected(boolean connected) {
            mTagDetectedCountDownLatch.countDown();
        }

        @Override
        public void onStateUpdated(int state) {
        }

        @Override
        public void onApplyRouting(@NonNull Consumer<Boolean> isSkipped) {
            isSkipped.accept(false);
        }

        @Override
        public void onNdefRead(@NonNull Consumer<Boolean> isSkipped) {
            isSkipped.accept(true);
        }

        @Override
        public void onEnableRequested(@NonNull Consumer<Boolean> isAllowed) {
            isAllowed.accept(true);
        }

        @Override
        public void onDisableRequested(@NonNull Consumer<Boolean> isAllowed) {
            isAllowed.accept(true);
        }

        @Override
        public void onBootStarted() {
        }

        @Override
        public void onEnableStarted() {
        }

        @Override
        public void onDisableStarted() {
        }

        @Override
        public void onBootFinished(int status) {
        }

        @Override
        public void onEnableFinished(int status) {
        }

        @Override
        public void onDisableFinished(int status) {
        }

        @Override
        public void onTagDispatch(@NonNull Consumer<Boolean> isSkipped) {
            isSkipped.accept(true);
        }

        @Override
        public void onRoutingChanged(@NonNull Consumer<Boolean> isSkipped) {
            isSkipped.accept(false);
        }

        @Override
        public void onRoutingChangeCompleted() {
        }

        @Override
        public void onHceEventReceived(int action) {
        }

        @Override
        public void onReaderOptionChanged(boolean enabled) {
        }

        public void onCardEmulationActivated(boolean isActivated) {
            mTagDetectedCountDownLatch.countDown();
        }

        @Override
        public void onRfFieldDetected(boolean isActive) {
            mTagDetectedCountDownLatch.countDown();
        }

        @Override
        public void onRfDiscoveryStarted(boolean isDiscoveryStarted) {
            mTagDetectedCountDownLatch.countDown();
        }

        @Override
        public void onEeListenActivated(boolean isActivated) {
            mTagDetectedCountDownLatch.countDown();
        }

        @Override
        public void onEeUpdated() {
        }

        @Override
        public void onGetOemAppSearchIntent(@NonNull List<String> packages,
                                            @NonNull Consumer<Intent> intentConsumer) {
            intentConsumer.accept(new Intent());
        }

        @Override
        public void onNdefMessage(@NonNull Tag tag, @NonNull NdefMessage message,
                                  @NonNull Consumer<Boolean> hasOemExecutableContent) {
            hasOemExecutableContent.accept(true);
        }

        @Override
        public void onLaunchHceAppChooserActivity(@NonNull String selectedAid,
                                                  @NonNull List<ApduServiceInfo> services,
                                                  @NonNull ComponentName failedComponent,
                                                  @NonNull String category) {
        }

        @Override
        public void onLaunchHceTapAgainDialog(@NonNull ApduServiceInfo service,
                                              @NonNull String category) {
        }

        @Override
        public void onRoutingTableFull() {
        }

        @Override
        public void onLogEventNotified(@NonNull OemLogItems item) {
            item.getAction();
            item.getEvent();
            item.getCallingPid();
            item.getTag();
            item.getCommandApdu();
            item.getResponseApdu();
            item.getRfFieldEventTimeMillis();
        }

        @Override
        public void onExtractOemPackages(@NonNull NdefMessage message,
                @NonNull Consumer<List<String>> packageConsumer) {
            packageConsumer.accept(new ArrayList<>());
        }
    }

    private class MockResultReceiver extends ResultReceiver {

        int mResultCode;
        Bundle mResultData;
        CountDownLatch mLatch;

        MockResultReceiver(CountDownLatch latch) {
            super(null);
            mLatch = latch;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mResultCode = resultCode;
            mResultData = resultData;
            mLatch.countDown();
        }

        public int getResultCode() {
            return mResultCode;
        }

        public Bundle getResultData() {
            return mResultData;
        }
    }

    private class NfcVendorNciCallback implements NfcAdapter.NfcVendorNciCallback {
        private final CountDownLatch mRspCountDownLatch;
        private final CountDownLatch mNtfCountDownLatch;

        public int gid;
        public int oid;
        public byte[] payload;

        NfcVendorNciCallback(CountDownLatch rspCountDownLatch, CountDownLatch ntfCountDownLatch) {
            mRspCountDownLatch = rspCountDownLatch;
            mNtfCountDownLatch = ntfCountDownLatch;
        }

        @Override
        public void onVendorNciResponse(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mRspCountDownLatch.countDown();
        }

        @Override
        public void onVendorNciNotification(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mNtfCountDownLatch.countDown();
        }
    }

    private class CtsReaderCallback implements NfcAdapter.ReaderCallback {
        @Override
        public void onTagDiscovered(Tag tag) {}
    }

    private class CtsNfcUnlockHandler implements NfcAdapter.NfcUnlockHandler {
        @Override
        public boolean onUnlockAttempted(Tag tag) {
            return true;
        }
    }

    private static class CtsWlcStateListener implements NfcAdapter.WlcStateListener {
        @Override
        public void onWlcStateChanged(WlcListenerDeviceInfo deviceInfo) {}
    }

    private static class CtsOnTagRemovedListener implements NfcAdapter.OnTagRemovedListener {
        @Override
        public void onTagRemoved() {
            sTagRemoved = true;
        }
    }

    private Activity createAndResumeActivity() {
        CardEmulationTest.ensureUnlocked();
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
            NfcFCardEmulationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        InstrumentationRegistry.getInstrumentation().callActivityOnResume(activity);
        ComponentName topComponentName = mContext.getSystemService(ActivityManager.class)
                .getRunningTasks(1).get(0).topActivity;
        Assert.assertEquals("Foreground activity not in the foreground",
                NfcFCardEmulationActivity.class.getName(), topComponentName.getClassName());
        return activity;
    }

    private NfcAdapter getDefaultAdapter() {
        return NfcAdapter.getDefaultAdapter(mContext);
    }

    private IsoDep createIsoDepTag() {
        Bundle extras = new Bundle();
        extras.putByteArray(IsoDep.EXTRA_HI_LAYER_RESP, new byte[]{0x00});
        extras.putByteArray(IsoDep.EXTRA_HIST_BYTES, new byte[]{0x00});
        Tag tag = new Tag(new byte[]{0x00}, new int[]{TagTechnology.ISO_DEP},
                new Bundle[]{extras}, MOCK_NATIVE_HANDLE, 0L, null);
        return IsoDep.get(tag);
    }

    private ComponentName setDefaultPaymentService(Class serviceClass) {
        ComponentName componentName = setDefaultPaymentService(
                new ComponentName(mContext, serviceClass));
        if (componentName == null) {
            return null;
        }
        return componentName;
    }

    private ComponentName setDefaultPaymentService(ComponentName serviceName) {
        if (serviceName == null) {
            return null;
        }
        return DefaultPaymentProviderTestUtils.setDefaultPaymentService(serviceName, mContext);
    }

    private void denyPermission(String permission) {
        when(mContext.checkCallingOrSelfPermission(permission))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        doThrow(new SecurityException()).when(mContext)
                .enforceCallingOrSelfPermission(eq(permission), anyString());
    }

    private static int bytesToInt(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b & 0xFF));
        }
        return Integer.parseInt(hexString.toString(), 16);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NFC_CHECK_TAG_INTENT_PREFERENCE)
    public void testSetTagIntentAppPreference() throws NoSuchFieldException, RemoteException {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC));
        NfcAdapter adapter = getDefaultAdapter();
        assumeTrue("Device must support tag intent app preference",
            adapter.isTagIntentAppPreferenceSupported());
        int user = mContext.getUser().getIdentifier();

        // Disallow package
        assertEquals(NfcAdapter.TAG_INTENT_APP_PREF_RESULT_SUCCESS,
            adapter.setTagIntentAppPreferenceForUser(
            user, "android.nfc.cts", /* allow = */ false));
        Map<String, Boolean> disallowMap = adapter.getTagIntentAppPreferenceForUser(user);
        assertNotNull(disallowMap);
        assertFalse(disallowMap.isEmpty());
        assertEquals(false, disallowMap.get("android.nfc.cts"));
        assertFalse(adapter.isTagIntentAllowed());

        // Allow package
        assertEquals(NfcAdapter.TAG_INTENT_APP_PREF_RESULT_SUCCESS,
            adapter.setTagIntentAppPreferenceForUser(
                user, "android.nfc.cts", /* allow = */ true));
        Map<String, Boolean> allowMap = adapter.getTagIntentAppPreferenceForUser(user);
        assertNotNull(allowMap);
        assertFalse(allowMap.isEmpty());
        assertEquals(true, allowMap.get("android.nfc.cts"));
        assertTrue(adapter.isTagIntentAllowed());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NFC_CHARGING)
    public void testRegisterAndUnregisterWlcStateListener() throws RemoteException {
        NfcAdapter adapter = getDefaultAdapter();
        CtsWlcStateListener listener = new CtsWlcStateListener();

        adapter.registerWlcStateListener(Executors.newSingleThreadExecutor(), listener);
        adapter.unregisterWlcStateListener(listener);
    }
}
