/*==========================================================================
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
#=========================================================================== */

package com.qualcomm.qti.fullscreengestureservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.os.Bundle;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;
import android.os.RemoteException;
import android.graphics.Point;
import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Looper;
import android.hardware.display.DisplayManagerGlobal;
import android.view.Display;
import android.util.DisplayMetrics;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import android.content.res.Resources;
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;
import android.provider.DeviceConfig;
import android.util.TypedValue;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_INPUT_MONITOR;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_SYSUI_PROXY;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_TRACING_ENABLED;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputConsumerController;

public class fullscreengestureservice extends Service {
    public final String TAG = "fullscreengestureservice";

    public final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static boolean sIsInitialized = false;

    private InputMonitor mInputMonitor;
    private InputEventReceiver mInputEventReceiver;
    ISystemUiProxy mProxy = null;

    private Context mContext;
    private Point mDisplaySize = new Point();
    private float mBottomGestureHeight;

    private Point mStartPoint = new Point();
    private long mStartTime;

    private static final int SWIPE_NONE = 0;
    private static final int SWIPE_FROM_TOP = 1;
    private static final int SWIPE_FROM_BOTTOM = 2;
    private static final int SWIPE_FROM_RIGHT = 3;
    private static final int SWIPE_FROM_LEFT = 4;

    private  static int swipe_result ;

    private static final long SWIPE_THRESHOLD_MS = 100;
    private boolean mSwipeUpStart = false;

    private final String WATCH_HOME = "com.qualcomm.qti.weartech.watchhome";

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG,"onBind");
        return mMyBinder;
    }

    private void disposeEventHandlers() {
        if (mInputEventReceiver != null) {
            mInputMonitor.dispose();
            mInputEventReceiver = null;
        }
        if (mInputMonitor != null) {
            mInputMonitor.dispose();
            mInputMonitor = null;
        }
    }

    private void initInputMonitor() {
        disposeEventHandlers();

        // Register input event receiver
		Log.d(TAG,"Register input event receiver");
		try {
            Bundle bundle = mProxy.monitorGestureInput("swipe-up", 0);
            mInputMonitor = bundle.getParcelable(KEY_EXTRA_INPUT_MONITOR);
            mInputEventReceiver = new SysUiInputEventReceiver(
                    mInputMonitor.getInputChannel(), Looper.getMainLooper());
        }catch (RemoteException e){
		    Log.d(TAG,"register input event receiver failed");
        }
    }

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        public void onInitialize(Bundle bundle) {
            Log.d(TAG,"onInitialize");
            mProxy = ISystemUiProxy.Stub.asInterface(bundle.getBinder(KEY_EXTRA_SYSUI_PROXY));

            getNavigationBarHeight();

            initInputMonitor();

            sIsInitialized = true;
        }

        @Override
        public void onOverviewToggle() {

        }

        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {

        }

        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {

        }

        @Override
        public void onTip(int actionType, int viewType) {

        }

        @Override
        public void onAssistantAvailable(boolean available) {

        }

        @Override
        public void onAssistantVisibilityChanged(float visibility) {

        }

        public void onBackAction(boolean completed, int downX, int downY, boolean isButton,
                                 boolean gestureSwipeLeft) {

        }

        public void onSystemUiStateChanged(int stateFlags) {

        }

        public void onActiveNavBarRegionChanges(Region region) {

        }

        public void onSplitScreenSecondaryBoundsChanged(Rect bounds, Rect insets)  {

        }

        /** Deprecated methods **/
        public void onQuickStep(MotionEvent motionEvent) { }

        public void onQuickScrubEnd() { }

        public void onQuickScrubProgress(float progress) { }

        public void onQuickScrubStart() { }

        public void onPreMotionEvent(int downHitTarget) { }

        public void onMotionEvent(MotionEvent ev) {
            ev.recycle();
        }

        public void onBind(ISystemUiProxy iSystemUiProxy) { }
    };

    private void getNavigationBarHeight() {
        Display display = DisplayManagerGlobal.getInstance().getRealDisplay(Display.DEFAULT_DISPLAY);
        display.getRealSize(mDisplaySize);
        Log.d(TAG, "display size=" + mDisplaySize.toString());

        mContext = getApplicationContext();
        Resources res = mContext.getResources();
        final DisplayMetrics dm = res.getDisplayMetrics();
        final float defaultGestureHeight = res.getDimension(com.android.internal.R.dimen.navigation_bar_gesture_height) / dm.density;
        final float gestureHeight = DeviceConfig.getFloat(DeviceConfig.NAMESPACE_SYSTEMUI, SystemUiDeviceConfigFlags.BACK_GESTURE_BOTTOM_HEIGHT, defaultGestureHeight);
        mBottomGestureHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, gestureHeight,dm);
    }

    private boolean isInBottomRegoin(MotionEvent ev) {
        if(ev.getY() < mDisplaySize.y && ev.getY() > (mDisplaySize.y - mBottomGestureHeight))
            return true;
        return false;
    }

    private void startAPPListActivity() {
        Log.d(TAG, "startAPPListActivity");
        //Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);//mContext.getPackageManager().getLaunchIntentForPackage(WATCH_HOME);
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(WATCH_HOME);
        //clear top activity
        //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent !=null ) {
            mContext.startActivity(intent);
        }
    }

    private void HandleInputEvent(MotionEvent ev) {
        if(DEBUG) Log.d(TAG, "HandleInputEvent ev=" + ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if(DEBUG) Log.d(TAG, "ACTION_DOWN");
                mStartPoint.x = (int) ev.getX();
                mStartPoint.y = (int) ev.getY();
                mStartTime = ev.getEventTime();
                if (isInBottomRegoin(ev) && !mSwipeUpStart) {
                    if(DEBUG) Log.d(TAG, "mSwipeUpStart");
                    mSwipeUpStart = true;
                    swipe_result = SWIPE_NONE;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if(DEBUG) Log.d(TAG, "ACTION_MOVE");
                if (mSwipeUpStart) {
                    long elapsed = ev.getEventTime() - mStartTime;
                    int swipeDistance = mStartPoint.y - (int) ev.getY();
                    if(DEBUG) Log.d(TAG, "elapsed=" + elapsed + ", swipeDistance=" + swipeDistance);
                    if (swipeDistance > (mBottomGestureHeight/2)  && elapsed > SWIPE_THRESHOLD_MS) {
                        if(DEBUG) Log.d(TAG, "swipe from bottom");
                        swipe_result = SWIPE_FROM_BOTTOM;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (swipe_result == SWIPE_FROM_BOTTOM) {
                    startAPPListActivity();
                }
                mSwipeUpStart = false;
                swipe_result = SWIPE_NONE;
                break;
        }
    }

    class SysUiInputEventReceiver extends InputEventReceiver {
        SysUiInputEventReceiver(InputChannel channel, Looper looper) {
            super(channel, looper);
        }

        public void onInputEvent(InputEvent event) {
            if (event instanceof MotionEvent) {
                HandleInputEvent((MotionEvent) event);
                finishInputEvent(event, true);
            }
        }
    }
}
