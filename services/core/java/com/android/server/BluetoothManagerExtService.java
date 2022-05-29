/*
 * Copyright (C) 2012 The Android Open Source Project
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
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 *
 */

package com.android.server;

import android.bluetooth.BluetoothAdapterCommon;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerExt;

import android.content.Context;
import android.util.Log;
import android.util.Slog;

class BluetoothManagerExtService extends IBluetoothManagerExt.Stub {
    private static final String TAG = "BluetoothManagerExtService";
    private static final boolean DBG = true;

    private static final int ADAPTER_1 = BluetoothAdapterCommon.ADAPTER_1;

    private Context mContext;
    private BluetoothManagerService mManagerService;

    BluetoothManagerExtService(Context context) {
        if (DBG) {
            Slog.d(TAG, "constructor");
        }
        mContext = context;
        mManagerService = new BluetoothManagerService(context, ADAPTER_1);
    }

    @Override
    public IBluetoothManager getBluetoothManager() {
        if (DBG) {
            Slog.d(TAG, "getBluetoothManager");
        }
        return IBluetoothManager.Stub.asInterface(mManagerService);
    }

    /**
     * Send enable message and set adapter name and address. Called when the boot phase becomes
     * PHASE_SYSTEM_SERVICES_READY.
     */
    public void handleOnBootPhase() {
        if (DBG) {
            Slog.d(TAG, "Bluetooth boot completed");
        }
        mManagerService.handleOnBootPhase();
    }

    /**
     * Called when switching to a different foreground user.
     */
    public void handleOnSwitchUser(int userHandle) {
        if (DBG) {
            Slog.d(TAG, "User " + userHandle + " switched");
        }
        mManagerService.handleOnSwitchUser(userHandle);
    }

    /**
     * Called when user is unlocked.
     */
    public void handleOnUnlockUser(int userHandle) {
        if (DBG) {
            Slog.d(TAG, "User " + userHandle + " unlocked");
        }
        mManagerService.handleOnUnlockUser(userHandle);
    }
}
