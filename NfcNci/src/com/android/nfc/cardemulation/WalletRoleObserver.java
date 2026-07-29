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

package com.android.nfc.cardemulation;

import static com.android.permission.flags.Flags.crossUserRoleEnabled;

import android.app.ActivityManager;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.Context;
import android.nfc.PackageAndUser;
import android.os.Binder;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.flags.Flags;
import android.sysprop.NfcProperties;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.nfc.NfcEventLog;
import com.android.nfc.NfcInjector;
import com.android.nfc.proto.NfcEventProto;

import java.util.List;
import java.util.Objects;

public class WalletRoleObserver {
    static final boolean DBG = NfcProperties.debug_enabled().orElse(true);
    private static final String TAG = "NfcWalletRoleObserver";

    public interface Callback {
        void onWalletRoleHolderChanged(String holder, int userId);
    }
    private Context mContext;
    private NfcEventLog mNfcEventLog;
    private RoleManager mRoleManager;
    @VisibleForTesting
    final OnRoleHoldersChangedListener mOnRoleHoldersChangedListener;
    private Callback mCallback;
    private int mCurrentUser;

    public WalletRoleObserver(Context context, RoleManager roleManager,
            Callback callback, NfcInjector nfcInjector) {
        this.mContext = context;
        this.mRoleManager = roleManager;
        this.mCallback = callback;
        this.mNfcEventLog = nfcInjector.getNfcEventLog();
        this.mCurrentUser = ActivityManager.getCurrentUser();
        this.mOnRoleHoldersChangedListener = (roleName, user) -> {
            if (!roleName.equals(RoleManager.ROLE_WALLET)) {
                return;
            }

            Context userContext = mContext.createContextAsUser(
                    UserHandle.of(mCurrentUser), 0);
            RoleManager userRoleManager = userContext.getSystemService(RoleManager.class);
            if (userRoleManager == null) {
                return;
            }

            if (Flags.walletRoleCrossUserEnabled()) {
                if (!Objects.equals(user,
                        userRoleManager.getActiveUserForRole(RoleManager.ROLE_WALLET))) {
                    return;
                }

                UserManager userManager = mContext.getSystemService(UserManager.class);
                if (!Objects.equals(user, UserHandle.of(mCurrentUser)) &&
                        !Objects.equals(userManager.getProfileParent(user),
                                UserHandle.of(mCurrentUser))) {
                    return;
                }
            }

            List<String> roleHolders = roleManager.getRoleHoldersAsUser(RoleManager.ROLE_WALLET,
                    user);
            String roleHolder = roleHolders.isEmpty() ? null : roleHolders.get(0);
            if (DBG) {
                Log.i(TAG, "WalletRoleObserver: Wallet role changed for user "
                        + user.getIdentifier() + " to " + roleHolder);
            }
            mNfcEventLog.logEvent(
                    NfcEventProto.EventType.newBuilder()
                            .setWalletRoleHolderChange(
                                NfcEventProto.NfcWalletRoleHolderChange.newBuilder()
                                .setPackageName(roleHolder != null ? roleHolder : "none")
                                .build())
                            .build());
            callback.onWalletRoleHolderChanged(roleHolder, user.getIdentifier());
        };
        this.mRoleManager.addOnRoleHoldersChangedListenerAsUser(context.getMainExecutor(),
                mOnRoleHoldersChangedListener, UserHandle.ALL);
    }

    public PackageAndUser getDefaultWalletRoleHolder(int userId) {
        final long token = Binder.clearCallingIdentity();
        final PackageAndUser noRoleHolderResult = new PackageAndUser(userId, null);
        try {
            UserHandle roleUserHandle = UserHandle.of(userId);
            Context userContext = mContext.createContextAsUser(UserHandle.of(userId), 0);
            RoleManager userRoleManager = userContext.getSystemService(RoleManager.class);
            if (userRoleManager == null) {
                return noRoleHolderResult;
            }

            if (Flags.walletRoleCrossUserEnabled() && crossUserRoleEnabled()) {
                roleUserHandle = userRoleManager.getActiveUserForRole(
                        RoleManager.ROLE_WALLET);

                if (roleUserHandle == null) {
                    Log.d(TAG, "getDefaultWalletRoleHolder: No active user for role");
                    return noRoleHolderResult;
                }
            } else if (!userRoleManager.isRoleAvailable(RoleManager.ROLE_WALLET)) {
                return noRoleHolderResult;
            }

            List<String> roleHolders = mRoleManager.getRoleHoldersAsUser(RoleManager.ROLE_WALLET,
                    roleUserHandle);
            if (roleHolders.isEmpty()) {
                return new PackageAndUser(roleUserHandle.getIdentifier(), null);
            }

            return new PackageAndUser(roleUserHandle.getIdentifier(), roleHolders.get(0));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    boolean isWalletRoleFeatureEnabled() {
        final long token = Binder.clearCallingIdentity();
        try {
            return Flags.walletRoleEnabled();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    public void onUserSwitched(int userId) {
        mCurrentUser = userId;
        PackageAndUser roleHolder = getDefaultWalletRoleHolder(userId);
        if (DBG) {
            Log.i(TAG, "onUserSwitched: Wallet role for user " + userId + ": "
                    + roleHolder.getUserId() + " (" + roleHolder.getPackage() + ")");
        }
        mCallback.onWalletRoleHolderChanged(roleHolder.getPackage(), roleHolder.getUserId());
    }
}
