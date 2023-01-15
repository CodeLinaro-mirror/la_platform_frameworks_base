/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package android.bluetooth;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.compat.annotation.UnsupportedAppUsage;
import android.os.RemoteException;
import android.util.Log;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Bluetooth adapter utility
 * {@hide}
 */
public final class BluetoothAdapterUtil {
    private static final String TAG = "BluetoothAdapterUtil";

    private static final int ADAPTER_DEFAULT = BluetoothAdapterCommon.ADAPTER_DEFAULT;
    private static final int ADAPTER_1 = BluetoothAdapterCommon.ADAPTER_1;
    private static final int ADAPTER_NUMBER = BluetoothAdapterCommon.ADAPTER_NUMBER;

    private static boolean sDualBluetoothSupported = false;
    private static HashMap<Integer, ArrayList<Integer>> sProfiles;

    static {
        classInit();
    }

    private static void classInit() {
        sDualBluetoothSupported = BluetoothAdapterExt.isSupported();
        sProfiles = new HashMap<Integer, ArrayList<Integer>>(ADAPTER_NUMBER);

        sProfiles.put(ADAPTER_DEFAULT, new ArrayList<Integer>(Arrays.asList(
                BluetoothProfile.A2DP_SINK,
                BluetoothProfile.AVRCP_CONTROLLER,
                BluetoothProfile.GATT,
                BluetoothProfile.GATT_SERVER,
                BluetoothProfile.HEADSET_CLIENT,
                BluetoothProfile.MAP_CLIENT,
                BluetoothProfile.OPP,
                BluetoothProfile.PBAP_CLIENT)));

        sProfiles.put(ADAPTER_1, new ArrayList<Integer>(Arrays.asList(
                BluetoothProfile.A2DP,
                BluetoothProfile.AVRCP,
                BluetoothProfile.GATT,
                BluetoothProfile.GATT_SERVER,
                BluetoothProfile.HEADSET,
                BluetoothProfile.HID_HOST,
                BluetoothProfile.OPP,
                BluetoothProfile.PBAP)));
    }

    /** {@hide} */
    public static boolean isDualBluetoothSupported() {
        return sDualBluetoothSupported;
    }

    /** {@hide} */
    @NonNull
    public static BluetoothAdapter getAdapter() {
        BluetoothAdapter newAdapter = getNewAdapter();
        // Return new Bluetooth adapter if available.
        // Otherwise, return default Bluetooth adapter.
        return isDualBluetoothSupported() && (newAdapter != null) ?
                newAdapter :
                getDefaultAdapter();
    }

    /** {@hide} */
    @UnsupportedAppUsage
    /*package*/ public static BluetoothAdapter getAdapter(int adapterIndex) {
        return validAdapter(adapterIndex) ?
                (isDefaultAdapter(adapterIndex) ?
                getDefaultAdapter() :
                getNewAdapter()) :
                null;
    }

    /** {@hide} */
    public static BluetoothAdapter getDefaultAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    /** {@hide} */
    @Nullable
    public static BluetoothAdapter getNewAdapter() {
        BluetoothAdapterExt adapterExt = BluetoothAdapterExt.getDefaultAdapter();
        return adapterExt != null ? adapterExt.getBluetoothAdapter() : null;
    }

    /** {@hide} */
    @Nullable
    public static BluetoothAdapter getNewAdapter(BluetoothDevice device) {
        return isDualBluetoothSupported() &&
                isNewAdapterMatched(device) ?
                getNewAdapter() :
                null;
    }

    /** {@hide} */
    public static int getAdapterIndex(int profileId) {
        return isProfileSupported(profileId, ADAPTER_DEFAULT) ?
                ADAPTER_DEFAULT : (isProfileSupported(profileId, ADAPTER_1) ?
                ADAPTER_1 : ADAPTER_NUMBER);
    }

    /** {@hide} */
    @UnsupportedAppUsage
    /*package*/ public static boolean isProfileSupported(int profileId, int adapterIndex) {
        return validAdapter(adapterIndex) ?
                sProfiles.get(adapterIndex).contains(profileId) :
                false;
    }

    /** {@hide} */
    public static boolean isProfileSupported(int profileId) {
        return isProfileSupported(profileId, ADAPTER_DEFAULT) ||
                isProfileSupported(profileId, ADAPTER_1);
    }

    private static boolean validAdapter(int adapterIndex) {
        return BluetoothAdapterCommon.validAdapter(adapterIndex);
    }

    /** {@hide} */
    public static boolean isDefaultAdapter(BluetoothAdapter adapter) {
        return adapter != null && isDefaultAdapter(adapter.getAdapterIndex());
    }

    /** {@hide} */
    public static boolean isDefaultAdapter(BluetoothDevice device) {
        return (device != null) ?
                isDefaultAdapter(device.getAdapterIndex()) :
                false;
    }

    private static boolean isDefaultAdapter(int adapterIndex) {
        return BluetoothAdapterCommon.isAdapterDefault(adapterIndex);
    }

    /** {@hide} */
    public static boolean isNewAdapter(BluetoothAdapter adapter) {
        return adapter != null && isNewAdapter(adapter.getAdapterIndex());
    }

    /** {@hide} */
    public static boolean isNewAdapter(BluetoothDevice device) {
        return (device != null) ?
                isNewAdapter(device.getAdapterIndex()) :
                false;
    }

    private static boolean isNewAdapter(int adapterIndex) {
        return BluetoothAdapterCommon.isAdapter1(adapterIndex);
    }

    private static boolean isNewAdapterMatched(BluetoothDevice device) {
        return device != null && isNewAdapter(getAdapterIndexMatched(device));
    }

    private static int getAdapterIndexMatched(BluetoothDevice device) {
        BluetoothClass btClass = device.getBluetoothClass();
        return btClass.doesClassMatch(BluetoothClass.PROFILE_HEADSET) ||
                btClass.doesClassMatch(BluetoothClass.PROFILE_A2DP) ||
                btClass.doesClassMatch(BluetoothClass.PROFILE_HID) ?
                ADAPTER_1 :
                ADAPTER_DEFAULT;
    }

    /** {@hide} */
    public static IBluetoothGatt getBluetoothGatt(int adapterIndex) {
        BluetoothAdapter adapter = getAdapter(adapterIndex);
        if (adapter == null)
            return null;

        IBluetoothManager bluetoothManager = adapter.getBluetoothManager();
        if (bluetoothManager == null)
            return null;

        try {
            return bluetoothManager.getBluetoothGatt();
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
            return null;
        }
    }
}
