/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.ActivityManager;
import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.app.backup.BackupRestoreEventLogger;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;

/**
 * Performs backup and restore of NFC settings.
 * This agent uses SharedPreferencesBackupHelper to handle two preference files:
 * 1. NfcService.PREF: Contains general NFC settings like the on/off state.
 * 2. NfcService.PREF_TAG_APP_LIST: Contains preferences for tag-related applications.
 *
 * To enable backup/restore metrics, this class overrides onBackup() and onRestore()
 * to wrap the superclass's implementation, allowing success or failure events to be logged
 * via BackupRestoreEventLogger without altering the data format, ensuring backward
 * and forward compatibility.
 */
public class NfcBackupAgent extends BackupAgentHelper {
    private static final String TAG = "NfcBackupAgent";

    // Constants for Backup/Restore Metrics Logging.
    private static final String DATA_TYPE_NFC_PREFS = "nfc:prefs";
    private static final String DATA_TYPE_NFC_TAG_APPS = "nfc:tag_apps";
    private static final String ERROR_EXCEPTION = "backup_restore_exception";

    // Backup identifier
    static final String SHARED_PREFS_BACKUP_KEY = "shared_prefs";

    // Logger instance for reporting backup and restore events.
    private BackupRestoreEventLogger mLogger;

    // Cache the result for performance
    private Boolean mIsUnderTest = null;

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferencesBackupHelper helper =
                new SharedPreferencesBackupHelper(
                        this, NfcService.PREF, NfcService.PREF_TAG_APP_LIST);
        addHelper(SHARED_PREFS_BACKUP_KEY, helper);
    }

    /**
     * Checks if the agent is running within a unit test environment.
     * This is determined by checking for the presence of a known testing framework class.
     * @return true if running in a test, false otherwise.
     */
    private boolean isRunningInTest() {
        if (mIsUnderTest == null) {
            try {
                Class.forName("org.junit.Test");
                mIsUnderTest = true;
            } catch (ClassNotFoundException e) {
                mIsUnderTest = false;
            }
        }
        return mIsUnderTest;
    }

    /**
     * Lazily initializes and returns the BackupRestoreEventLogger instance.
     * Returns null if running in a test environment to prevent crashes.
     */
    private BackupRestoreEventLogger getLogger() {
        if (isRunningInTest()) {
            return null;
        }
        if (mLogger == null) {
            BackupManager backupManager = new BackupManager(this);
            mLogger = backupManager.getBackupRestoreEventLogger(this);
        }
        return mLogger;
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        BackupRestoreEventLogger logger = getLogger();
        try {
            super.onBackup(oldState, data, newState);
            if (logger != null) {
                logger.logItemsBackedUp(DATA_TYPE_NFC_PREFS, 1);
                logger.logItemsBackedUp(DATA_TYPE_NFC_TAG_APPS, 1);
                Log.d(TAG, "onBackup: Logged successful backup.");
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.logItemsBackupFailed(DATA_TYPE_NFC_PREFS, 1, ERROR_EXCEPTION);
                logger.logItemsBackupFailed(DATA_TYPE_NFC_TAG_APPS, 1, ERROR_EXCEPTION);
            }
            Log.e(TAG, "onBackup: Exception during backup.", e);
            throw e;
        }
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        BackupRestoreEventLogger logger = getLogger();
        try {
            super.onRestore(data, appVersionCode, newState);
            if (logger != null) {
                logger.logItemsRestored(DATA_TYPE_NFC_PREFS, 1);
                logger.logItemsRestored(DATA_TYPE_NFC_TAG_APPS, 1);
                Log.d(TAG, "onRestore: Logged successful restore.");
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.logItemsRestoreFailed(DATA_TYPE_NFC_PREFS, 1, ERROR_EXCEPTION);
                logger.logItemsRestoreFailed(DATA_TYPE_NFC_TAG_APPS, 1, ERROR_EXCEPTION);
            }
            Log.e(TAG, "onRestore: Exception during restore.", e);
            throw e;
        }
    }

    @Override
    public void onRestoreFinished() {
        super.onRestoreFinished();
        Log.d(TAG, "onRestoreFinished: Applying restored settings.");

        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        NfcService.sIsNfcRestore = true;
        DeviceConfigFacade deviceConfigFacade = NfcInjector.getInstance().getDeviceConfigFacade();

        if (nfcAdapter != null) {
            SharedPreferences prefs = getSharedPreferences(NfcService.PREF,
                Context.MODE_MULTI_PROCESS);
            if (prefs.getBoolean(NfcService.PREF_NFC_ON,
                    deviceConfigFacade.getNfcDefaultState())) {
                nfcAdapter.enable();
            } else {
                nfcAdapter.disable();
            }

            if (prefs.getBoolean(NfcService.PREF_NFC_READER_OPTION_ON,
                    deviceConfigFacade.getDefaultReaderOption())) {
                nfcAdapter.enableReaderOption(true);
            } else {
                nfcAdapter.enableReaderOption(false);
            }

            int userId = ActivityManager.getCurrentUser();
            if (prefs.getBoolean(NfcService.PREF_SECURE_NFC_ON + "_" + userId,
                    deviceConfigFacade.getDefaultSecureNfcState())
                    && nfcAdapter.isSecureNfcSupported()) {
                nfcAdapter.enableSecureNfc(true);
            } else {
                nfcAdapter.enableSecureNfc(false);
            }
        }
    }
}
