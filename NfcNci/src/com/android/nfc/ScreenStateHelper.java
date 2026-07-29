package com.android.nfc;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.view.Display;

import androidx.annotation.VisibleForTesting;

import com.android.server.display.feature.flags.Flags;

/**
 * Helper class for determining the current screen state for NFC activities.
 */
public class ScreenStateHelper {

    public static final int SCREEN_STATE_UNKNOWN = 0x00;
    public static final int SCREEN_STATE_OFF_UNLOCKED = 0x01;
    public static final int SCREEN_STATE_OFF_LOCKED = 0x02;
    public static final int SCREEN_STATE_ON_LOCKED = 0x04;
    public static final int SCREEN_STATE_ON_UNLOCKED = 0x08;

    // Display category built-in displays before the AOSP API is ready.
    // TODO (b/321309554): Get the correct value from OEM.
    private static final int DISPLAY_BUILT_IN_DISPLAY_OEM = 1;

    //Polling mask
    static final int SCREEN_POLLING_TAG_MASK = 0x10;
    static final int SCREEN_POLLING_READER_MASK = 0x40;

    private final PowerManager mPowerManager;
    private final DisplayManager mDisplayManager;

    ScreenStateHelper(Context context) {
        this(context.getSystemService(PowerManager.class),
                context.getSystemService(DisplayManager.class));
    }

    private boolean isDisplayOn() {
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Display displayBuiltIn = null;
        if (Flags.displayCategoryBuiltIn()) {
            Display[] displaysBuiltIn =
                    mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_BUILT_IN_DISPLAYS);
            if (displaysBuiltIn.length > 0) {
                displayBuiltIn = displaysBuiltIn[0];

                // if there is any screen it's on, choosing the display for it
                for (Display dis : displaysBuiltIn) {
                    if (dis.getState() == Display.STATE_ON) {
                        displayBuiltIn = dis;
                        break;
                    }
                }
            }
        } else {
            displayBuiltIn = mDisplayManager.getDisplay(DISPLAY_BUILT_IN_DISPLAY_OEM);
        }
        return display.getState() == Display.STATE_ON
                || (displayBuiltIn != null && displayBuiltIn.getState() == Display.STATE_ON);
    }

    public int checkScreenState(boolean checkDisplayState) {
        if ((!checkDisplayState && mPowerManager.isInteractive())
                || (checkDisplayState && isDisplayOn())) {
            if (NfcInjector.getInstance().isDeviceLocked()) {
                return SCREEN_STATE_ON_LOCKED;
            } else {
                return SCREEN_STATE_ON_UNLOCKED;
            }
        } else if (NfcInjector.getInstance().isDeviceLocked()) {
            return SCREEN_STATE_OFF_LOCKED;
        } else {
            return SCREEN_STATE_OFF_UNLOCKED;
        }
    }

    int checkScreenStateProvisionMode() {
        if (!mPowerManager.isInteractive()) {
            if (NfcInjector.getInstance().isDeviceLocked()) {
                return SCREEN_STATE_OFF_LOCKED;
            } else {
                return SCREEN_STATE_OFF_UNLOCKED;
            }
        } else if (NfcInjector.getInstance().isDeviceLocked()) {
            return SCREEN_STATE_ON_LOCKED;
        } else {
            return SCREEN_STATE_ON_UNLOCKED;
        }
    }

    /**
     * For debugging only - no i18n
     */
    static String screenStateToString(int screenState) {
        switch (screenState) {
            case SCREEN_STATE_OFF_LOCKED:
                return "OFF_LOCKED";
            case SCREEN_STATE_ON_LOCKED:
                return "ON_LOCKED";
            case SCREEN_STATE_ON_UNLOCKED:
                return "ON_UNLOCKED";
            case SCREEN_STATE_OFF_UNLOCKED:
                return "OFF_UNLOCKED";
            default:
                return "UNKNOWN";
        }
    }

    static int screenStateToProtoEnum(int screenState) {
        switch (screenState) {
            case SCREEN_STATE_OFF_LOCKED:
                return NfcServiceDumpProto.SCREEN_STATE_OFF_LOCKED;
            case SCREEN_STATE_ON_LOCKED:
                return NfcServiceDumpProto.SCREEN_STATE_ON_LOCKED;
            case SCREEN_STATE_ON_UNLOCKED:
                return NfcServiceDumpProto.SCREEN_STATE_ON_UNLOCKED;
            case SCREEN_STATE_OFF_UNLOCKED:
                return NfcServiceDumpProto.SCREEN_STATE_OFF_UNLOCKED;
            default:
                return NfcServiceDumpProto.SCREEN_STATE_UNKNOWN;
        }
    }

    @VisibleForTesting
    ScreenStateHelper(PowerManager powerManager, DisplayManager displayManager) {
        mPowerManager = powerManager;
        mDisplayManager = displayManager;
    }
}
