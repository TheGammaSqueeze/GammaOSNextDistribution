/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep.fallback;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.RecentsActivity;

/**
 * Drag layer for fallback recents activity.
 * Forces the nav-bar visible on attach and always applies a bottom inset
 * equal to the taskbar height so RecentsView is laid out correctly.
 */
public class RecentsDragLayer extends BaseDragLayer<RecentsActivity> {

    public RecentsDragLayer(Context context, AttributeSet attrs) {
        super(context, attrs, 1 /* alphaChannelCount */);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // 1) Force the navigation bar to show immediately, breaking immersive mode.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController ic = getContext().getSystemService(WindowInsetsController.class);
            if (ic != null) {
                ic.show(WindowInsets.Type.navigationBars());
            }
        } else {
            // Pre-Android 11 fallback
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }

        // 2) Re-trigger an inset dispatch so dispatchApplyWindowInsets runs now
        requestApplyInsets();
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        // Force the bottom inset to the taskbar height on every inset pass
        if (Utilities.ATLEAST_Q) {
            DeviceProfile dp = mActivity.getDeviceProfile();
            int bottomInset = dp.taskbarHeight;
            insets = insets.replaceSystemWindowInsets(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    bottomInset
            );
        }
        // Let BaseDragLayer handle the rest (including feeding insets to RecentsView)
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    public void recreateControllers() {
        mControllers = new TouchController[] {
                new RecentsTaskController(mActivity),
                new FallbackNavBarTouchController(mActivity),
        };
    }
}
