/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.globalactions;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.SystemUI;
import com.android.systemui.plugins.GlobalActions;
import com.android.systemui.plugins.GlobalActions.GlobalActionsManager;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.CommandQueue.Callbacks;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.policy.ExtensionController;
import com.android.systemui.statusbar.policy.ExtensionController.Extension;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.android.settingslib.Utils;
import com.android.internal.R;
import android.util.Log;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import android.os.SystemProperties;

/**
 * Manages power menu plugins and communicates power menu actions to the StatusBar.
 */
@Singleton
public class GlobalActionsComponent extends SystemUI implements Callbacks, GlobalActionsManager {

    private static final String TAG = "GlobalActionsComponent";
    private static final String ACTION_FORCE_DEEPSLEEP =
                         "com.qualcomm.qti.intent.action.ACTION_FORCE_DEEPSLEEP";
    private static final String ACTION_FORCE_HIBERNATE =
            "com.qualcomm.qti.intent.action.ACTION_FORCE_HIBERNATE";
    private static final int TYPE_DEEPSLEEP = 10;
    private static final int TYPE_HIBERNATE = 20;
    private static final String TWM_TYPE = "twm_type";
    private final int TWM_HIBERNATE = 1;
    private final int TWM_SHUTDOWN = 0;
    private Context mContext;
    private Dialog mDialog;
    private final CommandQueue mCommandQueue;
    private final ExtensionController mExtensionController;
    private final Provider<GlobalActions> mGlobalActionsProvider;
    private GlobalActions mPlugin;
    private Extension<GlobalActions> mExtension;
    private IStatusBarService mBarService;
    private StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;
    private final int CONFIG_HIBERNATE;

    @Inject
    public GlobalActionsComponent(Context context, CommandQueue commandQueue,
            ExtensionController extensionController,
            Provider<GlobalActions> globalActionsProvider,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager) {
        super(context);
        mCommandQueue = commandQueue;
        mExtensionController = extensionController;
        mGlobalActionsProvider = globalActionsProvider;
        mStatusBarKeyguardViewManager = statusBarKeyguardViewManager;
        mContext = context;
        CONFIG_HIBERNATE = mContext.getResources()
                .getInteger(com.android.internal.R.integer.config_hibernate);
    }

    @Override
    public void start() {
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        mExtension = mExtensionController.newExtension(GlobalActions.class)
                .withPlugin(GlobalActions.class)
                .withDefault(mGlobalActionsProvider::get)
                .withCallback(this::onExtensionCallback)
                .build();
        mPlugin = mExtension.get();
        mCommandQueue.addCallback(this);
        boolean isWatch =
                SystemProperties.getBoolean("ro.product.qti.qcom_watch", false);
        if (isWatch) {
            registerForcePowerBroadcast();
        }
    }

    private void onExtensionCallback(GlobalActions newPlugin) {
        if (mPlugin != null) {
            mPlugin.destroy();
        }
        mPlugin = newPlugin;
    }

    @Override
    public void handleShowShutdownUi(boolean isReboot, String reason) {
        mExtension.get().showShutdownUi(isReboot, reason);
    }

    @Override
    public void handleShowGlobalActionsMenu() {
        mStatusBarKeyguardViewManager.setGlobalActionsVisible(true);
        mExtension.get().showGlobalActions(this);
    }

    @Override
    public void onGlobalActionsShown() {
        try {
            mBarService.onGlobalActionsShown();
        } catch (RemoteException e) {
        }
    }

    @Override
    public void onGlobalActionsHidden() {
        try {
            mStatusBarKeyguardViewManager.setGlobalActionsVisible(false);
            mBarService.onGlobalActionsHidden();
        } catch (RemoteException e) {
        }
    }

    @Override
    public void shutdown() {
        try {
            mBarService.shutdown();
        } catch (RemoteException e) {
        }
    }

    @Override
    public void reboot(boolean safeMode) {
        try {
            mBarService.reboot(safeMode);
        } catch (RemoteException e) {
        }
    }

    @Override
    public void twm() {
        boolean  tWMIsHibernateType =Settings.Global.getInt(mContext.getContentResolver(), TWM_TYPE,CONFIG_HIBERNATE) == TWM_HIBERNATE;
        try {
            if(tWMIsHibernateType){
                hibernate();
            }else {
                mBarService.twm();
            }
        } catch (RemoteException e) {
        }
    }

    @Override
    public void deepsleep() {
        try {
            boolean result = mBarService.deepsleep();
            if (result) {
                showTriggerDialog(TYPE_DEEPSLEEP);
            }
        } catch (RemoteException e) {
        }
    }

    public void hibernate() {
        try {
            boolean result = mBarService.hibernate();
            if (result) {
                showTriggerDialog(TYPE_HIBERNATE);
            }
        } catch (RemoteException e) {
        }
    }

    private void showTriggerDialog(int type) {
        mDialog = new Dialog(mContext,
                com.android.systemui.R.style.Theme_SystemUI_Dialog_GlobalActions);
        Window window = mDialog.getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        window.getAttributes().systemUiVisibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        window.getDecorView();
        window.getAttributes().width = ViewGroup.LayoutParams.MATCH_PARENT;
        window.getAttributes().height = ViewGroup.LayoutParams.MATCH_PARENT;
        window.getAttributes().layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        window.setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
        window.getAttributes().setFitInsetsTypes(0);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        window.setWindowAnimations(com.android.systemui.R.style.Animation_ShutdownUi);
        mDialog.setContentView(R.layout.shutdown_dialog);
        mDialog.setCancelable(false);
        int color = Utils.getColorAttrDefaultColor(mContext,
                com.android.systemui.R.attr.wallpaperTextColor);
        ProgressBar bar = mDialog.findViewById(R.id.progress);
        bar.getIndeterminateDrawable().setTint(color);
        TextView messageView = mDialog.findViewById(R.id.text2);
        messageView.setTextColor(color);
        String triggerType = type==TYPE_DEEPSLEEP ? "entering DeepSleep" : "entering Hibernate";
        messageView.setText(triggerType);
        mDialog.show();
    }

    private void registerForcePowerBroadcast() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_FORCE_DEEPSLEEP);
        intentFilter.addAction(ACTION_FORCE_HIBERNATE);
        intentFilter.setPriority(IntentFilter.SYSTEM_LOW_PRIORITY);
        BroadcastReceiver br = new PowerBroadcastReceiver();
        mContext.registerReceiver(br, intentFilter);
    }

    private final class PowerBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_FORCE_DEEPSLEEP.equals(action) || ACTION_FORCE_HIBERNATE.equals(action)) {
                Log.i(TAG, "Receive force power broadcast: "+action);
                mDialog.dismiss();
            }
        }
    }
}
