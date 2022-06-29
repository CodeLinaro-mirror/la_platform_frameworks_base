/*
   Copyright (c) 2022 Qualcomm Innovation Center, Inc. All rights reserved.
   SPDX-License-Identifier: BSD-3-Clause-Clear
*/

package vendor.qti.bluetooth_offload;

import android.Manifest;
import android.app.ActivityThread;
import android.app.PropertyInvalidatedCache;
import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.SynchronousResultReceiver;
import android.os.SystemProperties;
import vendor.qti.bluetooth_offload.IBluetoothOffloadApp;
import vendor.qti.bluetooth_offload.IBluetoothOffloadLpm;
import vendor.qti.bluetooth_offload.IBluetoothOffloadLpmCallback;
import vendor.qti.bluetooth_offload.INotificationOffloadMgr;
import vendor.qti.bluetooth_offload.INotificationOffloadMgrCallback;
import android.util.Log;

import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** @hide */
public final class BluetoothPowerStateMgr {
  public static final String NOTIFICATION_OFFLOAD_MGR_SERVICE = "notification_offload_mgr";
  private static final String TAG = "BluetoothPowerStateMgr";

  public static final String BLUETOOTH_OFFLOAD_LPM_BINDING = "vendor.qti.bluetooth_offload.IBluetoothOffloadLpm";


  /** @hide */
  public static final int BT_FAIL = 0;
  /** @hide */
  public static final int BT_OK = 1;
  /** @hide */
  public static final int BT_INVALID_STATE = 2;
  /** @hide */
  public static final int TRACKER_MODE = 0;
  /** @hide */
  public static final int TWM_MODE = 1;
  /** @hide */
  public static final int DS_MODE = 2;
  /** @hide */
  public static final int ACTIVE_MODE = 3;
  /** @hide */
  public static final int POWER_STATUS_ACK = 0;
  /** @hide */
  public static final int POWER_STATUS_NACK = 1;

  private boolean mRegisterStatus = false;
  @UnsupportedAppUsage
  private INotificationOffloadMgr mManagerService;
  @UnsupportedAppUsage
  private IBluetoothOffloadLpm mService = null;
  private Context mContext;
  private final ReentrantReadWriteLock mServiceLock =
    new ReentrantReadWriteLock();
  @UnsupportedAppUsage
  private volatile BluetoothOffloadLpmCallback mLpmCallback;


  public BluetoothPowerStateMgr(@NonNull Context context) {
    IBinder b = ServiceManager.getService(NOTIFICATION_OFFLOAD_MGR_SERVICE);

    if (b != null) {
      mManagerService = INotificationOffloadMgr.Stub.asInterface(b);

      if (mManagerService != null) {
        try {
          mServiceLock.writeLock().lock();
          mService = mManagerService.registerLPMAdapter(mManagerCallback);
          if (mService == null) {
            Log.e(TAG, "IBluetoothOffloadLpm service is null");
          }
        } catch (RemoteException e) {
          Log.e(TAG, "", e);
        } finally {
          mServiceLock.writeLock().unlock();
        }
      } else {
        Log.e(TAG, "notification offload manager service is null");
      }
    } else {
      Log.e(TAG, "NotificationOffloadMgr binder is null");
    }
  }

  private final INotificationOffloadMgrCallback mManagerCallback =
    new INotificationOffloadMgrCallback.Stub() {
    public void onBluetoothOffloadServiceUp(IBluetoothOffloadApp
        bluetoothOffloadService, IBluetoothOffloadLpm lpmOffloadService) {
      Log.d(TAG, "onBluetoothOffloadServiceUp: " + lpmOffloadService);

      //Added check to avoid assigning service object to null when we receive onServiceUp for App Service
      if (lpmOffloadService == null) {
          Log.d(TAG, "onBluetoothOffloadServiceUp received on App service up");
          return;
      }
      mServiceLock.writeLock().lock();
      mService = lpmOffloadService;
      mServiceLock.writeLock().unlock();
    }

    public void onBluetoothOffloadServiceDown() {
      Log.d(TAG, "onBluetoothOffloadServiceDown: " + mService);

      try {
        mServiceLock.writeLock().lock();
        mService = null;
      } finally {
        mServiceLock.writeLock().unlock();
      }

      Log.d(TAG, "onBluetoothOffloadServiceDown");
    }
  };

  /** @hide */
  @SuppressLint("ExecutorRegistration")
  public int register(@NonNull BluetoothOffloadLpmCallback mCallback) {
    Log.d(TAG, "register");
    try {
      mServiceLock.readLock().lock();
      if (mService != null) {
        mLpmCallback = mCallback;
        int ret = mService.registerBtLpmFwk(mBluetoothOffloadLpmCallback);

        if (ret != BT_OK) {
          Log.e(TAG, "start: registerBtLpmFwk failed with err=" + ret);
          return BT_FAIL;
        }

        mRegisterStatus = true;
        return BT_OK;
      }
    } catch (RemoteException e) {
      Log.e(TAG, "", e);
    } finally {
      mServiceLock.readLock().unlock();
    }
    return BT_FAIL;
  }

  /** @hide */
  public int unregister() {
    Log.d(TAG, "unregister");
    if (false == mRegisterStatus) {
      Log.i(TAG, "stop: failed (no service handle) for offloadableApp ");
      return BT_FAIL;
    }

    try {
      mServiceLock.readLock().lock();
      if (mService != null) {
        int ret = mService.deregisterBtLpmFwk();

        if (ret != BT_OK) {
          Log.e(TAG, "stop: deregisterBtLpmFwk failed with err=" + ret);
          return BT_FAIL;
        }

        mRegisterStatus = false;
        return BT_OK;
      }
    } catch (RemoteException e) {
      Log.e(TAG, "", e);
    } finally {
      mServiceLock.readLock().unlock();
    }
    return BT_FAIL;
  }

  /** @hide */
  public void transitionToPwrState(int pwrState) {
    Log.d(TAG, "transitionToPwrState pwrState "+ pwrState);
    if (false == mRegisterStatus) {
      Log.e(TAG, "transitionToPwrState: failed (no service handle)");
      return;
    }

    try {
      mServiceLock.readLock().lock();
      if (mService != null) {
        mService.transitionToPwrState(pwrState);
      }
    } catch (RemoteException e) {
      Log.e(TAG, "", e);
    } finally {
      mServiceLock.readLock().unlock();
    }
  }

  private final IBluetoothOffloadLpmCallback mBluetoothOffloadLpmCallback =
    new IBluetoothOffloadLpmCallback.Stub() {
    /** @hide */
    public void transitionToPwrStateCb(int status) {
      Log.d(TAG, "transitionToPwrStateCb status " + status);
      if (mLpmCallback != null) {
        mLpmCallback.onTransitionToPwrStateDone(status);
      }
    }
  };
}
