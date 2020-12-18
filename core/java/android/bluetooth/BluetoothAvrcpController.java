/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * Changes from Qualcomm Technologies, Inc. are provided under the following license:
 *
 * Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */

package android.bluetooth;

import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SuppressLint;
import android.annotation.SdkConstant.SdkConstantType;
import android.bluetooth.annotations.RequiresBluetoothConnectPermission;
import android.bluetooth.annotations.RequiresLegacyBluetoothPermission;
import android.content.Attributable;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides the public APIs to control the Bluetooth AVRCP Controller. It currently
 * supports player information, playback support and track metadata.
 *
 * <p>BluetoothAvrcpController is a proxy object for controlling the Bluetooth AVRCP
 * Service via IPC. Use {@link BluetoothAdapter#getProfileProxy} to get
 * the BluetoothAvrcpController proxy object.
 *
 * {@hide}
 */
public final class BluetoothAvrcpController implements BluetoothProfile {
    private static final String TAG = "BluetoothAvrcpController";
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    /**
     * Intent used to broadcast the change in connection state of the AVRCP Controller
     * profile.
     *
     * <p>This intent will have 3 extras:
     * <ul>
     * <li> {@link #EXTRA_STATE} - The current state of the profile. </li>
     * <li> {@link #EXTRA_PREVIOUS_STATE}- The previous state of the profile.</li>
     * <li> {@link BluetoothDevice#EXTRA_DEVICE} - The remote device. </li>
     * </ul>
     *
     * <p>{@link #EXTRA_STATE} or {@link #EXTRA_PREVIOUS_STATE} can be any of
     * {@link #STATE_DISCONNECTED}, {@link #STATE_CONNECTING},
     * {@link #STATE_CONNECTED}, {@link #STATE_DISCONNECTING}.
     */
    @RequiresLegacyBluetoothPermission
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CONNECTION_STATE_CHANGED =
            "android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED";

    /**
     * Intent used to broadcast the change in player application setting state on AVRCP AG.
     *
     * <p>This intent will have the following extras:
     * <ul>
     * <li> {@link #EXTRA_PLAYER_SETTING} - {@link BluetoothAvrcpPlayerSettings} containing the
     * most recent player setting. </li>
     * </ul>
     */
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PLAYER_SETTING =
            "android.bluetooth.avrcp-controller.profile.action.PLAYER_SETTING";

    public static final String EXTRA_PLAYER_SETTING =
            "android.bluetooth.avrcp-controller.profile.extra.PLAYER_SETTING";

    private final BluetoothAdapter mAdapter;
    private final AttributionSource mAttributionSource;

    /* Remote supported Features */
    public static final int BTRC_FEAT_NONE = 0x00;
    public static final int BTRC_FEAT_METADATA = 0x01;
    public static final int BTRC_FEAT_ABSOLUTE_VOLUME = 0x02;
    public static final int BTRC_FEAT_BROWSE = 0x04;
    public static final int BTRC_FEAT_COVER_ART = 0x08;

    private final BluetoothProfileConnector<IBluetoothAvrcpController> mProfileConnector =
            new BluetoothProfileConnector(this, BluetoothProfile.AVRCP_CONTROLLER,
                    "BluetoothAvrcpController", IBluetoothAvrcpController.class.getName()) {
                @Override
                public IBluetoothAvrcpController getServiceInterface(IBinder service) {
                    return IBluetoothAvrcpController.Stub.asInterface(
                            Binder.allowBlocking(service));
                }
    };

    /**
     * Create a BluetoothAvrcpController proxy object for interacting with the local
     * Bluetooth AVRCP service.
     */
    /* package */ BluetoothAvrcpController(Context context, ServiceListener listener,
            BluetoothAdapter adapter) {
        mAdapter = adapter;
        mAttributionSource = adapter.getAttributionSource();
        mProfileConnector.connect(context, listener);
    }

    /*package*/ void close() {
        mProfileConnector.disconnect();
    }

    private IBluetoothAvrcpController getService() {
        return mProfileConnector.getService();
    }

    @Override
    public void finalize() {
        close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public List<BluetoothDevice> getConnectedDevices() {
        if (VDBG) log("getConnectedDevices()");
        final IBluetoothAvrcpController service =
                getService();
        if (service != null && isEnabled()) {
            try {
                return Attributable.setAttributionSource(
                        service.getConnectedDevices(mAttributionSource), mAttributionSource);
            } catch (RemoteException e) {
                Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
                return new ArrayList<BluetoothDevice>();
            }
        }
        if (service == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (VDBG) log("getDevicesMatchingStates()");
        final IBluetoothAvrcpController service =
                getService();
        if (service != null && isEnabled()) {
            try {
                return Attributable.setAttributionSource(
                        service.getDevicesMatchingConnectionStates(states, mAttributionSource),
                        mAttributionSource);
            } catch (RemoteException e) {
                Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
                return new ArrayList<BluetoothDevice>();
            }
        }
        if (service == null) Log.w(TAG, "Proxy not attached to service");
        return new ArrayList<BluetoothDevice>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public int getConnectionState(BluetoothDevice device) {
        if (VDBG) log("getState(" + device + ")");
        final IBluetoothAvrcpController service =
                getService();
        if (service != null && isEnabled() && isValidDevice(device)) {
            try {
                return service.getConnectionState(device, mAttributionSource);
            } catch (RemoteException e) {
                Log.e(TAG, "Stack:" + Log.getStackTraceString(new Throwable()));
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
        if (service == null) Log.w(TAG, "Proxy not attached to service");
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    /**
     * Gets the player application settings.
     *
     * @return the {@link BluetoothAvrcpPlayerSettings} or {@link null} if there is an error.
     */
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public BluetoothAvrcpPlayerSettings getPlayerSettings(BluetoothDevice device) {
        if (DBG) Log.d(TAG, "getPlayerSettings");
        BluetoothAvrcpPlayerSettings settings = null;
        final IBluetoothAvrcpController service =
                getService();
        if (service != null && isEnabled()) {
            try {
                settings = service.getPlayerSettings(device, mAttributionSource);
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in getMetadata() " + e);
                return null;
            }
        }
        return settings;
    }

    /**
     * Sets the player app setting for current player.
     * returns true in case setting is supported by remote, false otherwise
     */
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public boolean setPlayerApplicationSetting(BluetoothAvrcpPlayerSettings plAppSetting) {
        if (DBG) Log.d(TAG, "setPlayerApplicationSetting");
        final IBluetoothAvrcpController service =
                getService();
        if (service != null && isEnabled()) {
            try {
                return service.setPlayerApplicationSetting(plAppSetting, mAttributionSource);
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in setPlayerApplicationSetting() " + e);
                return false;
            }
        }
        if (service == null) Log.w(TAG, "Proxy not attached to service");
        return false;
    }

    /**
     * Send Group Navigation Command to Remote.
     * possible keycode values: next_grp, previous_grp defined above
     */
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public void sendGroupNavigationCmd(BluetoothDevice device, int keyCode, int keyState) {
        Log.d(TAG, "sendGroupNavigationCmd dev = " + device + " key " + keyCode + " State = "
                + keyState);
        final IBluetoothAvrcpController service =
                getService();
        if (service != null && isEnabled()) {
            try {
                service.sendGroupNavigationCmd(device, keyCode, keyState, mAttributionSource);
                return;
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in sendGroupNavigationCmd()", e);
                return;
            }
        }
        if (service == null) Log.w(TAG, "Proxy not attached to service");
    }

    /**
     * Fetch the playback state to Remote.
     */
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public void getPlaybackState(BluetoothDevice device){
        Log.d(TAG, "getPlaybackStateNative dev = " + device);
        final IBluetoothAvrcpController service = getService();
        if (service != null && isEnabled()) {
           try {
               service.getPlaybackState(device, mAttributionSource);
               return;
           } catch (RemoteException e) {
               Log.e(TAG, "Error talking to BT service in getPlaybackState()", e);
               return;
           }
        }
        if (service == null) Log.w(TAG, "Proxy not attached to service");
    }

     /**
     * Get Supported features for Remote.
     * @hide
     */
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public int getSupportedFeatures(BluetoothDevice device) {
        Log.d(TAG, "getSupportedFeatures dev = " + device);
        final IBluetoothAvrcpController service =
                getService();
        if (service != null && isEnabled()) {
            try {
                if (getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                    return service.getSupportedFeatures(device, mAttributionSource);
                } else {
                    Log.w(TAG, "getSupportedFeatures failed, avrcp connection is required");
                    return 0;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in getSupportedFeatures()", e);
                return 0;
            }
       }
       if (service == null) Log.w(TAG, "Proxy not attached to service");
       return 0;
    }

    /**
     * Get remote AVRCP version.
     * @hide
     */
    @RequiresBluetoothConnectPermission
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    public int getRemoteVersion(BluetoothDevice device) {
        Log.d(TAG, "getRemoteVersion dev = " + device);
        final IBluetoothAvrcpController service =
                getService();
        if (service != null && isEnabled()) {
            try {
                if (getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                    return service.getRemoteVersion(device, mAttributionSource);
                } else {
                    Log.w(TAG, "getRemoteVersion failed, avrcp connection is required");
                    return 0;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in getRemoteVersion()", e);
                return 0;
            }
       }
       if (service == null) Log.w(TAG, "Proxy not attached to service");
       return 0;
    }

    /**
     * Informs AvrcpControllerService to start fetching Album Art.
     * Fetching will start only after this api is called.
     * @device Bluetooth device
     * @type Image type
     * @scheme Image scheme
     * @mimeType Image mime Type
     * @height Image height
     * @width Image width
     * @maxSize Image maximum size
     * if input parameters are null, 0, 0, 0: image in native encoding will be fetched.
     * @hide
     */
    public void startFetchingAlbumArt(BluetoothDevice device, String type, String scheme,
            String mimeType, int height, int width, int maxSize) {
        if (DBG) Log.d(TAG, "startFetchingAlbumArt");
        final IBluetoothAvrcpController service =
                getService();
        if (service != null && isEnabled()) {
            try {
                service.startFetchingAlbumArt(device, type, scheme, mimeType, height, width, maxSize, mAttributionSource);
            } catch (RemoteException e) {
                Log.e(TAG, "Error talking to BT service in startFetchingAlbumArt() " + e);
                return;
            }
        }
        if (service == null) Log.w(TAG, "Proxy not attached to service");
        return ;
    }

    private boolean isEnabled() {
        return mAdapter.getState() == BluetoothAdapter.STATE_ON;
    }

    private static boolean isValidDevice(BluetoothDevice device) {
        return device != null && BluetoothAdapter.checkBluetoothAddress(device.getAddress());
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
