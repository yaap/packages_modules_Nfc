/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.nfc;

import static org.mockito.Mockito.verify;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class IAppCallbackTest {
    private IAppCallback mBinder;

    @Mock
    private IAppCallback mCallback;

    @Mock
    private Tag mTag;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mBinder = new IAppCallback.Stub() {
            @Override
            public void onTagDiscovered(Tag tag) {
                try {
                    mCallback.onTagDiscovered(tag);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onTagLost(Tag tag) {
                try {
                    mCallback.onTagLost(tag);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    @Test
    public void testOnTagDiscovered() throws RemoteException {
        mBinder.onTagDiscovered(mTag);
        verify(mCallback).onTagDiscovered(mTag);
    }

    @Test
    public void testOnTagLost() throws RemoteException {
        mBinder.onTagLost(mTag);
        verify(mCallback).onTagLost(mTag);
    }



    class NfcAppCallbackService extends Service {
        private static final String TAG = "NfcAppCallbackService";

        private final IAppCallback.Stub binder = new IAppCallback.Stub() {
            @Override
            public void onTagDiscovered(Tag tag) throws RemoteException {
                // override
            }
            @Override
            public void onTagLost(Tag tag) throws RemoteException {
                // override
            }
        };

        @Override
        public IBinder onBind(Intent intent) {
            return binder;
        }
    }
}
