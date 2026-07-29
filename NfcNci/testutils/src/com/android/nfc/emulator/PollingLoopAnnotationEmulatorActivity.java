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

package com.android.nfc.emulator;

import android.content.ComponentName;
import android.os.Bundle;

import com.android.nfc.service.PollingLoopAnnotationService;

public class PollingLoopAnnotationEmulatorActivity extends BaseEmulatorActivity {
    private static final String TAG = "PollingLoopAnnotationEmulatorActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupServices(PollingLoopAnnotationService.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCardEmulation.setPreferredService(this, PollingLoopAnnotationService.COMPONENT);
        waitForPreferredService();
        setObserveModeEnabled(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        mCardEmulation.unsetPreferredService(this);
    }

    @Override
    public ComponentName getPreferredServiceComponent() {
        return PollingLoopAnnotationService.COMPONENT;
    }
}
