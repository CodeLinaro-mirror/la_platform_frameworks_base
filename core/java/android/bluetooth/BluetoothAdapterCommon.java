/*
 * Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package android.bluetooth;

/**
 * Blluetooth adapter common defintion and utility function
 * {@hide}
 */
public final class BluetoothAdapterCommon {
    private static final String TAG = "BluetoothAdapterCommon";
    private static final String DESCRIPTOR = "android.bluetooth.BluetoothAdapterCommon";

    /**
     * Bluetooth adapter0 (default)
     * {@hide}
     */
    public static final int ADAPTER_DEFAULT = 0;

    /**
     * Bluetooth adapter1
     * {@hide}
     */
    public static final int ADAPTER_1 = 1;

    /**
     * Bluetooth adapter number
     * {@hide}
     */
    public static final int ADAPTER_NUMBER = 2;

    /** {@hide} */
    public static boolean isAdapterDefault(int adapterIndex) {
        return adapterIndex == ADAPTER_DEFAULT;
    }

    /** {@hide} */
    public static boolean isAdapter1(int adapterIndex) {
        return adapterIndex == ADAPTER_1;
    }

    /** {@hide} */
    public static boolean validAdapter(int adapterIndex) {
        return (adapterIndex >= ADAPTER_DEFAULT) && (adapterIndex < ADAPTER_NUMBER);
    }
}
