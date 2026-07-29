/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.nfc.cts;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Verify that at most one module or app declares the PERFORM_GESTURE_EXCHANGE permission.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot read all packages' permissions")
public class GestureExchangePermissionTest {
    private static final String PERMISSION_NAME = "android.permission.PERFORM_GESTURE_EXCHANGE";

    @Test
    public void testAtMostOneAppDeclaresGestureExchangePermission() {
        Context context = InstrumentationRegistry.getTargetContext();
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        int declarationCount = 0;
        StringBuilder declaringPackages = new StringBuilder();

        for (PackageInfo pkg : packages) {
            if (pkg.permissions != null) {
                for (PermissionInfo permission : pkg.permissions) {
                    if (PERMISSION_NAME.equals(permission.name)) {
                        declarationCount++;
                        declaringPackages.append(pkg.packageName).append(" ");
                    }
                }
            }
        }

        assertTrue("At most one app or module should declare " + PERMISSION_NAME
                + ". Found declarations in: " + declaringPackages.toString(),
                declarationCount <= 1);
    }
}
