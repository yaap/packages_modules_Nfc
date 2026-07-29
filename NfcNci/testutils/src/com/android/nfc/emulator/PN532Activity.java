package com.android.nfc.emulator;

import android.content.ComponentName;
import android.content.Intent;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;

import com.android.nfc.service.PollingLoopService;

public class PN532Activity extends BaseEmulatorActivity implements ReaderCallback {
    public static final String ACTION_TAG_DISCOVERED = PACKAGE_NAME + ".TAG_DISCOVERED";
    public static final String ACTION_TAG_LOST_CATCH = PACKAGE_NAME + ".TAG_LOST_CATCH";

    private volatile boolean mIsPolling = false;
    private boolean mStressTestTagLoss = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupServices(PollingLoopService.COMPONENT);
        if (getIntent() != null) {
            mStressTestTagLoss = getIntent().getBooleanExtra("stress_test_tag_loss", false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCardEmulation.setPreferredService(this, PollingLoopService.COMPONENT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCardEmulation.unsetPreferredService(this);
    }

    @Override
    public ComponentName getPreferredServiceComponent() {
        return PollingLoopService.COMPONENT;
    }

    public void enableReaderMode(int flags) {
        Log.d(TAG, "enableReaderMode: " + flags);
        mAdapter.enableReaderMode(this, this, flags, null);
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        Log.d(TAG, "onTagDiscovered");
        Intent intent = new Intent(ACTION_TAG_DISCOVERED);
        sendBroadcast(intent);

        if (mStressTestTagLoss) {
            Log.d(TAG, "Starting TagLoss stress test thread...");
            mIsPolling = true;
            new Thread(() -> {
                android.nfc.tech.IsoDep isoDep = android.nfc.tech.IsoDep.get(tag);
                if (isoDep != null) {
                    try {
                        isoDep.connect();
                        Log.d(TAG, "IsoDep connected. Starting transceive loop...");
                        while (mIsPolling && isoDep.isConnected()) {
                            // Dummy SELECT APDU to maintain session
                            isoDep.transceive(new byte[]{0x00, (byte) 0xA4, 0x04, 0x00, 0x00});
                            android.os.SystemClock.sleep(100);
                        }
                    } catch (android.nfc.TagLostException e) {
                        Log.d(TAG, "Caught expected TagLostException: " + e.getMessage());
                        sendBroadcast(new Intent(ACTION_TAG_LOST_CATCH));
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected exception during transceive: " + e.getMessage());
                    } finally {
                        try {
                            isoDep.close();
                        } catch (Exception ignored) {}
                    }
                } else {
                    Log.e(TAG, "IsoDep tech not supported on tag.");
                }
            }).start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mIsPolling = false;
    }
}
