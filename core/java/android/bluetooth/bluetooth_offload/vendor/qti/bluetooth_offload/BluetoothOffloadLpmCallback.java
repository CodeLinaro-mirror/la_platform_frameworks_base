/*
   Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
   SPDX-License-Identifier: BSD-3-Clause-Clear
*/

package vendor.qti.bluetooth_offload;

import android.annotation.NonNull;

/**
 * Bluetooth Offload updates are reported using these callbacks.
 */
/** @hide */
public abstract class BluetoothOffloadLpmCallback {
    /** @hide */
    public void onTransitionToPwrStateDone(int status) {
    }
}
