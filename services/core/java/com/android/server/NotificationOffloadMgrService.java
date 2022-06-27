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
 */

/*
Changes from Qualcomm Innovation Center are provided under the following license:

Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 
Redistribution and use in source and binary forms, with or without
modification, are permitted (subject to the limitations in the
disclaimer below) provided that the following conditions are met:
 
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
 
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
 
    * Neither the name of Qualcomm Innovation Center, Inc. nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.
 
NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.android.server;

import static android.os.UserHandle.USER_SYSTEM;

import vendor.qti.bluetooth_offload.NotificationOffloadMgr;
import vendor.qti.bluetooth_offload.BluetoothPowerStateMgr;
import vendor.qti.bluetooth_offload.INotificationOffloadMgr;
import vendor.qti.bluetooth_offload.INotificationOffloadMgrCallback;
import vendor.qti.bluetooth_offload.IBluetoothOffloadApp;
import vendor.qti.bluetooth_offload.IBluetoothOffloadLpm;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.FeatureFlagUtils;
import android.util.Log;
import android.util.Slog;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.pm.UserRestrictionsUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class NotificationOffloadMgrService extends INotificationOffloadMgr.Stub {
    private static final String TAG = "NotificationOffloadMgrService";
    private static final boolean DBG = true;

    private static final int CRASH_LOG_MAX_SIZE = 100;

    private static final int TIMEOUT_BIND_MS = 3000; //Maximum msec to wait for a bind

    private static final int MESSAGE_INFORM_ADAPTER_SERVICE_UP = 22;
    private static final int MESSAGE_INFORM_LPM_ADAPTER_SERVICE_UP = 23;
    private static final int MESSAGE_BLUETOOTH_OFFLOAD_SERVICE_CONNECTED = 40;
    private static final int MESSAGE_BLUETOOTH_OFFLOAD_SERVICE_DISCONNECTED = 41;
    private static final int MESSAGE_BLUETOOTH_OFFLOAD_LPM_SERVICE_CONNECTED = 43;
    private static final int MESSAGE_BLUETOOTH_OFFLOAD_LPM_SERVICE_DISCONNECTED = 44;
    private static final int MESSAGE_TIMEOUT_BIND = 100;
    private static final int MESSAGE_TIMEOUT_UNBIND = 101;
    private static final int MESSAGE_TIMEOUT_LPM_BIND = 102;
    private static final int MESSAGE_USER_SWITCHED = 300;
    private static final int MESSAGE_USER_UNLOCKED = 301;

    private static final int SERVICE_IBLUETOOTH_OFFLOAD = 1;

    private final Context mContext;

    private final RemoteCallbackList<INotificationOffloadMgrCallback> mCallbacks;
    private IBinder mBluetoothBinder;
    private IBluetoothOffloadApp mBluetoothOffload;
    private final ReentrantReadWriteLock mBluetoothOffloadLock = new ReentrantReadWriteLock();
    private boolean mBinding;
    private boolean mUnbinding;
    private boolean mTryBindOnBindTimeout = false;

    private IBluetoothOffloadLpm mLpmBluetoothOffload;
    private final ReentrantReadWriteLock mLpmBluetoothOffloadLock = new ReentrantReadWriteLock();
    private boolean mLpmBinding;
    private boolean mLpmUnbinding;
    private boolean mLpmTryBindOnBindTimeout = false;

    // used inside handler thread
    private boolean mQuietEnable = false;

    private final LinkedList<Long> mCrashTimestamps = new LinkedList<>();
    private int mCrashes;

    private final BluetoothOffloadHandler mHandler;

    NotificationOffloadMgrService(Context context) {
        mHandler = new BluetoothOffloadHandler(IoThread.get().getLooper());
		Slog.w(TAG, "NotificationOffloadMgrService--");
		//TODO
		//BluetoothOffloadService offloadService = new BluetoothOffloadService();
		//offloadService = BluetoothOffloadService.getBluetoothOffloadService();

        mContext = context;

        mCrashes = 0;
        mBluetoothOffload = null;
        mLpmBluetoothOffload = null;
        mBluetoothBinder = null;
        mBinding = false;
        mLpmBinding = false;
        mTryBindOnBindTimeout = false;
        mUnbinding = false;

        mCallbacks = new RemoteCallbackList<INotificationOffloadMgrCallback>();
    }

    public IBluetoothOffloadApp registerAdapter(INotificationOffloadMgrCallback callback) {
        Slog.w(TAG, "registerAdapter");
        if (callback == null) {
            Slog.w(TAG, "Callback is null in registerAdapter");
            return null;
        }
        synchronized (mCallbacks) {
            mCallbacks.register(callback);
        }
        /* Madhu: To Confirm */
        return mBluetoothOffload;
    }

    public IBluetoothOffloadLpm registerLPMAdapter(INotificationOffloadMgrCallback callback) {
        Slog.w(TAG, "registerLPMAdapter");
        if (callback == null) {
            Slog.w(TAG, "Callback is null in registerLPMAdapter");
            return null;
        }
        synchronized (mCallbacks) {
            mCallbacks.register(callback);
        }
        return mLpmBluetoothOffload;
    }

    public void unregisterAdapter(INotificationOffloadMgrCallback callback) {
        if (callback == null) {
            Slog.w(TAG, "Callback is null in unregisterAdapter");
            return;
        }
        synchronized (mCallbacks) {
            mCallbacks.unregister(callback);
        }
    }

    public void unbindAndFinish() {
        if (DBG) {
            Slog.d(TAG, "unbindAndFinish(): " + mBluetoothOffload + " mBinding = " + mBinding
                    + " mUnbinding = " + mUnbinding);
        }

        try {
            mBluetoothOffloadLock.writeLock().lock();
            if (mUnbinding) {
                return;
            }
            mUnbinding = true;
            if (mLpmBluetoothOffload != null) {
                mLpmBluetoothOffload = null;
                mLpmBinding = false;
                mLpmUnbinding = false;
                mLpmTryBindOnBindTimeout = false;
                mContext.unbindService(mLpmConnection);
            }
            if (mBluetoothOffload != null) {
                mBluetoothBinder = null;
                mBluetoothOffload = null;
                mContext.unbindService(mConnection);
                mUnbinding = false;
                mBinding = false;
                mTryBindOnBindTimeout = false;
            } else {
                mUnbinding = false;
            }
        } finally {
            mBluetoothOffloadLock.writeLock().unlock();
        }
    }

    /**
     * Send enable message and set adapter name and address. Called when the boot phase becomes
     * PHASE_SYSTEM_SERVICES_READY.
     */
    public void handleOnBootPhase() {
        if (DBG) {
            Slog.d(TAG, "Notification Offload boot completed");
        }
    }

    /**
     * Called when switching to a different foreground user.
     */
    public void handleOnSwitchUser(int userHandle) {
        if (DBG) {
            Slog.d(TAG, "User " + userHandle + " switched");
        }
        mHandler.obtainMessage(MESSAGE_USER_SWITCHED, userHandle, 0).sendToTarget();
    }

    /**
     * Called when user is unlocked.
     */
    public void handleOnUnlockUser(int userHandle) {
        if (DBG) {
            Slog.d(TAG, "User " + userHandle + " unlocked");
        }
        mHandler.obtainMessage(MESSAGE_USER_UNLOCKED, userHandle, 0).sendToTarget();
    }

    /**
     * Inform BluetoothAdapter instances that Adapter service is up
     */
    private void sendBluetoothServiceUpCallback() {
        synchronized (mCallbacks) {
            try {
                mBluetoothOffloadLock.writeLock().lock();
                int n = mCallbacks.beginBroadcast();
                Slog.d(TAG, "Broadcasting onBluetoothOffloadServiceUp() to " + n + " receivers.");
                for (int i = 0; i < n; i++) {
                    try {
                        mCallbacks.getBroadcastItem(i).onBluetoothOffloadServiceUp(mBluetoothOffload, null);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to call onBluetoothOffloadServiceUp() on callback #" + i, e);
                    }
                }
            } finally {
                mCallbacks.finishBroadcast();
                mBluetoothOffloadLock.writeLock().unlock();
            }
        }
    }

    private void sendBluetoothLPMServiceUpCallback() {
        synchronized (mCallbacks) {
            try {
                mLpmBluetoothOffloadLock.writeLock().lock();
                int n = mCallbacks.beginBroadcast();
                Slog.d(TAG, "Broadcasting onBluetoothOffloadServiceUp() to " + n + " receivers.");
                for (int i = 0; i < n; i++) {
                    try {
                        mCallbacks.getBroadcastItem(i).onBluetoothOffloadServiceUp(null, mLpmBluetoothOffload);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to call onBluetoothOffloadServiceUp() on callback #" + i, e);
                    }
                }
            } finally {
                mCallbacks.finishBroadcast();
                mLpmBluetoothOffloadLock.writeLock().unlock();
            }
        }
    }

    /**
     * Inform BluetoothAdapter instances that Adapter service is down
     */
    private void sendBluetoothServiceDownCallback() {
        synchronized (mCallbacks) {
            try {
                int n = mCallbacks.beginBroadcast();
                Slog.d(TAG, "Broadcasting onBluetoothOffloadServiceDown() to " + n + " receivers.");
                for (int i = 0; i < n; i++) {
                    try {
                        mCallbacks.getBroadcastItem(i).onBluetoothOffloadServiceDown();
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to call onBluetoothOffloadServiceDown() on callback #" + i, e);
                    }
                }
            } finally {
                mCallbacks.finishBroadcast();
            }
        }
    }

    private class BluetoothOffloadServiceConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            String name = componentName.getClassName();
            if (DBG) {
                Slog.d(TAG, "BluetoothOffloadServiceConnection: " + name);
            }
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_OFFLOAD_SERVICE_CONNECTED);
            if (name.equals("vendor.qti.bluetooth_offload.btservice.BluetoothOffloadService")) {
                msg.arg1 = SERVICE_IBLUETOOTH_OFFLOAD;
                mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);
            } else {
                Slog.e(TAG, "Unknown service connected: " + name);
                return;
            }
            msg.obj = service;
            mHandler.sendMessage(msg);
        }

        public void onServiceDisconnected(ComponentName componentName) {
            // Called if we unexpectedly disconnect.
            String name = componentName.getClassName();
            if (DBG) {
                Slog.d(TAG, "BluetoothOffloadServiceDiscConnection, disconnected: " + name);
            }
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_OFFLOAD_SERVICE_DISCONNECTED);
            if (!name.equals("vendor.qti.bluetooth_offload.btservice.BluetoothOffloadService")) {
                msg.arg1 = SERVICE_IBLUETOOTH_OFFLOAD;
            } else {
                Slog.e(TAG, "Unknown service disconnected: " + name);
                return;
            }
            mHandler.sendMessage(msg);
        }
    }

    private BluetoothOffloadServiceConnection mConnection = new BluetoothOffloadServiceConnection();

    private class BluetoothOffloadLPMServiceConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            String name = componentName.getClassName();
            if (DBG) {
                Slog.d(TAG, "BluetoothOffloadLPMServiceConnection: " + name);
            }
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_OFFLOAD_LPM_SERVICE_CONNECTED);
            if (name.equals("vendor.qti.bluetooth_offload.btservice.BluetoothOffloadService")) {
                msg.arg1 = SERVICE_IBLUETOOTH_OFFLOAD;
                mHandler.removeMessages(MESSAGE_TIMEOUT_LPM_BIND);
            } else {
                Slog.e(TAG, "Unknown service connected: " + name);
                return;
            }
            msg.obj = service;
            mHandler.sendMessage(msg);
        }

        public void onServiceDisconnected(ComponentName componentName) {
            // Called if we unexpectedly disconnect.
            String name = componentName.getClassName();
            if (DBG) {
                Slog.d(TAG, "BluetoothOffloadServiceDiscConnection, disconnected: " + name);
            }
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_OFFLOAD_LPM_SERVICE_DISCONNECTED);
            if (!name.equals("vendor.qti.bluetooth_offload.btservice.BluetoothOffloadService")) {
                msg.arg1 = SERVICE_IBLUETOOTH_OFFLOAD;
            } else {
                Slog.e(TAG, "Unknown service disconnected: " + name);
                return;
            }
            mHandler.sendMessage(msg);
        }
    }

    private BluetoothOffloadLPMServiceConnection mLpmConnection = new BluetoothOffloadLPMServiceConnection();

    private class BluetoothOffloadHandler extends Handler {
        boolean mGetNameAddressOnly = false;

        BluetoothOffloadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_INFORM_ADAPTER_SERVICE_UP: {
                    if (DBG) Slog.d(TAG,"MESSAGE_INFORM_ADAPTER_SERVICE_UP");
                    sendBluetoothServiceUpCallback();
                    break;
                }
                case MESSAGE_INFORM_LPM_ADAPTER_SERVICE_UP: {
                    if (DBG) Slog.d(TAG,"MESSAGE_INFORM_LPM_ADAPTER_SERVICE_UP");
                    sendBluetoothLPMServiceUpCallback();
                    break;
                }
                case MESSAGE_BLUETOOTH_OFFLOAD_SERVICE_CONNECTED: {
                    if (DBG) {
                        Slog.d(TAG, "MESSAGE_BLUETOOTH_OFFLOAD_SERVICE_CONNECTED: " + msg.arg1);
                    }

                    IBinder service = (IBinder) msg.obj;
                    try {
                        mBluetoothOffloadLock.writeLock().lock();

                        mBinding = false;
                        mTryBindOnBindTimeout = false;
                        mBluetoothBinder = service;
                        mBluetoothOffload = IBluetoothOffloadApp.Stub.asInterface(Binder.allowBlocking(service));

                        //Inform BluetoothAdapter instances that service is up
                        Message informMsg =
                                    mHandler.obtainMessage(MESSAGE_INFORM_ADAPTER_SERVICE_UP);
                        mHandler.sendMessage(informMsg);

                    } finally {
                        mBluetoothOffloadLock.writeLock().unlock();
                    }
                    break;
                }

                case MESSAGE_BLUETOOTH_OFFLOAD_LPM_SERVICE_CONNECTED: {
                    if (DBG) {
                        Slog.d(TAG, "MESSAGE_BLUETOOTH_OFFLOAD_LPM_SERVICE_CONNECTED: " + msg.arg1);
                    }

                    IBinder service = (IBinder) msg.obj;
                    try {
                        mLpmBluetoothOffloadLock.writeLock().lock();

                        mLpmBinding = false;
                        mLpmTryBindOnBindTimeout = false;
                        mLpmBluetoothOffload = IBluetoothOffloadLpm.Stub.asInterface(Binder.allowBlocking(service));

                        //Inform BluetoothAdapter instances that service is up
                        Message informMsg =
                                    mHandler.obtainMessage(MESSAGE_INFORM_LPM_ADAPTER_SERVICE_UP);
                        mHandler.sendMessage(informMsg);

                    } finally {
                        mLpmBluetoothOffloadLock.writeLock().unlock();
                    }
                    break;
                }

                case MESSAGE_BLUETOOTH_OFFLOAD_SERVICE_DISCONNECTED: {
                    Slog.e(TAG, "MESSAGE_BLUETOOTH_OFFLOAD_SERVICE_DISCONNECTED(" + msg.arg1 + ")");
                    try {
                        mBluetoothOffloadLock.writeLock().lock();
                        if (msg.arg1 == SERVICE_IBLUETOOTH_OFFLOAD) {
                            // if service is unbinded already, do nothing and return
                            if (mBluetoothOffload == null) {
                                break;
                            }
                            mBluetoothOffload = null;
                        } else {
                            Slog.e(TAG, "Unknown argument for service disconnect!");
                            break;
                        }
                    } finally {
                        mBluetoothOffloadLock.writeLock().unlock();
                    }

                    // log the unexpected crash
                    addCrashLog();
                    sendBluetoothServiceDownCallback();
                    break;
                }

                case MESSAGE_BLUETOOTH_OFFLOAD_LPM_SERVICE_DISCONNECTED: {
                    Slog.e(TAG, "MESSAGE_BLUETOOTH_OFFLOAD_LPM_SERVICE_DISCONNECTED(" + msg.arg1 + ")");
                    try {
                        mLpmBluetoothOffloadLock.writeLock().lock();
                        if (msg.arg1 == SERVICE_IBLUETOOTH_OFFLOAD) {
                            // if service is unbinded already, do nothing and return
                            if (mLpmBluetoothOffload == null) {
                                break;
                            }
                            mLpmBluetoothOffload = null;
                        } else {
                            Slog.e(TAG, "Unknown argument for service disconnect!");
                            break;
                        }
                    } finally {
                        mLpmBluetoothOffloadLock.writeLock().unlock();
                    }

                    // log the unexpected crash
                    addCrashLog();
                    sendBluetoothServiceDownCallback();
                    break;
                }
                case MESSAGE_TIMEOUT_BIND: {
                    Slog.e(TAG, "MESSAGE_TIMEOUT_BIND");
                    mBluetoothOffloadLock.writeLock().lock();
                    mBinding = false;
                    mBluetoothOffloadLock.writeLock().unlock();
                    // Ensure try BIND for one more time
                    if(!mTryBindOnBindTimeout) {
                        Slog.e(TAG, " Trying to Bind again");
                        mTryBindOnBindTimeout = true;
                        handleEnable(mQuietEnable);
                    } else {
                        Slog.e(TAG, "Bind trails excedded");
                        mTryBindOnBindTimeout = false;
                    }
                    break;
                }
                case MESSAGE_TIMEOUT_LPM_BIND: {
                    Slog.e(TAG, "MESSAGE_TIMEOUT_LPM_BIND");
                    mLpmBluetoothOffloadLock.writeLock().lock();
                    mLpmBinding = false;
                    mLpmBluetoothOffloadLock.writeLock().unlock();
                    // Ensure try BIND for one more time
                    if(!mLpmTryBindOnBindTimeout) {
                        Slog.e(TAG, " Trying to Bind again for Lpm");
                        mLpmTryBindOnBindTimeout = true;
                        handleEnable(mQuietEnable);
                    } else {
                        Slog.e(TAG, "Bind trails excedded for LPM");
                        mLpmTryBindOnBindTimeout = false;
                    }
                    break;
                }
                case MESSAGE_TIMEOUT_UNBIND: {
                    Slog.e(TAG, "MESSAGE_TIMEOUT_UNBIND");
                    mBluetoothOffloadLock.writeLock().lock();
                    mUnbinding = false;
                    mBluetoothOffloadLock.writeLock().unlock();
                    break;
                }

                case MESSAGE_USER_SWITCHED: {
                    if (DBG) {
                        Slog.d(TAG, "MESSAGE_USER_SWITCHED");
                    }
                    break;
                }
                case MESSAGE_USER_UNLOCKED: {
                    if (DBG) {
                        Slog.d(TAG, "MESSAGE_USER_UNLOCKED");
                    }
                    mHandler.removeMessages(MESSAGE_USER_SWITCHED);

                    if (!mBinding && (mBluetoothOffload == null)) {
                        // We should be connected, but we gave up for some
                        // reason; maybe the Bluetooth service wasn't encryption
                        // aware, so try binding again.
                        if (DBG) {
                            Slog.d(TAG, "Enabled but not bound; retrying after unlock");
                        }
                        handleEnable(mQuietEnable);
                    } else if (!mLpmBinding && (mLpmBluetoothOffload == null)) {
                        if (DBG) {
                            Slog.d(TAG, "Enabled but LPM not bound; retrying after unlock");
                        }
                        handleEnable(mQuietEnable);
                    }
                }
            }
        }
    }

    private void handleEnable(boolean quietMode) {
        mQuietEnable = quietMode;

        try {
            mBluetoothOffloadLock.writeLock().lock();
            if ((mBluetoothOffload == null) && (!mBinding)) {
                Slog.d(TAG, "binding Bluetooth service");
                //Start bind timeout and bind
                Message timeoutMsg = mHandler.obtainMessage(MESSAGE_TIMEOUT_BIND);
                mHandler.sendMessageDelayed(timeoutMsg, TIMEOUT_BIND_MS);
                Intent i = new Intent(IBluetoothOffloadApp.class.getName());
                i.setAction(NotificationOffloadMgr.BLUETOOTH_OFFLOAD_APP_BINDING);
                if (!doBind(i, mConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                        UserHandle.CURRENT)) {
                    mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);
                } else {
                    mBinding = true;
                }
            }
        } finally {
            mBluetoothOffloadLock.writeLock().unlock();
        }

        try {
            mLpmBluetoothOffloadLock.writeLock().lock();
            if ((mLpmBluetoothOffload == null) && (!mLpmBinding)) {
                Slog.d(TAG, "binding Bluetooth Lpm service");
                //Start bind timeout and bind
                Message timeoutMsg = mHandler.obtainMessage(MESSAGE_TIMEOUT_LPM_BIND);
                mHandler.sendMessageDelayed(timeoutMsg, TIMEOUT_BIND_MS);
                Intent i = new Intent(IBluetoothOffloadLpm.class.getName());
                i.setAction(BluetoothPowerStateMgr.BLUETOOTH_OFFLOAD_LPM_BINDING);
                if (!doBind(i, mLpmConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                            UserHandle.CURRENT)) {
                    mHandler.removeMessages(MESSAGE_TIMEOUT_LPM_BIND);
                } else {
                    mLpmBinding = true;
                }
            }
        } finally {
            mLpmBluetoothOffloadLock.writeLock().unlock();
        }
    }

    boolean doBind(Intent intent, ServiceConnection conn, int flags, UserHandle user) {
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !mContext.bindServiceAsUser(intent, conn, flags, user)) {
            Slog.e(TAG, "Fail to bind to: " + intent);
            return false;
        }
        return true;
    }

    private void addCrashLog() {
        synchronized (mCrashTimestamps) {
            if (mCrashTimestamps.size() == CRASH_LOG_MAX_SIZE) {
                mCrashTimestamps.removeFirst();
            }
            mCrashTimestamps.add(System.currentTimeMillis());
            mCrashes++;
        }
    }
}
