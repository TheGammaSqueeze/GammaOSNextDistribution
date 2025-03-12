package com.android.server.policy;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

public class GlobalActionsActivity extends Activity {

    // Blur configuration values – adjust these as needed.
    private final int mBackgroundBlurRadius = 120;
    private final int mBlurBehindRadius = 80;
    private final float mDimAmountWithBlur = 0.1f;
    private final float mDimAmountNoBlur = 0.4f;

    private LegacyGlobalActions mGlobalActions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // (Option 1) If you have an empty layout, call setContentView() with a transparent layout.
        // Otherwise, if using a translucent theme (see manifest), you might not need to call setContentView().
        // setContentView(R.layout.empty_transparent);

        // Make the activity immersive (hide nav bar and status bar)
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Set the window background to transparent
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // Enable dimming behind the window
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Enable blur behind
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.getDecorView().post(() -> {
                window.setBackgroundBlurRadius(mBackgroundBlurRadius);
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.dimAmount = mDimAmountWithBlur;
                window.setAttributes(lp);
            });
        } else {
            // Fallback for older devices
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = mDimAmountNoBlur;
            window.setAttributes(lp);
        }

        // Create and show the LegacyGlobalActions dialog.
        // (Replace 'null' with a valid WindowManagerFuncs instance if available.)
        mGlobalActions = new LegacyGlobalActions(this, null, () -> finish());
        mGlobalActions.showDialog(false, true);
    }
}
