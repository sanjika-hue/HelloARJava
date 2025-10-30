package com.google.ar.core.examples.helloar;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

/**
 * Safe replacement for SnackbarHelper that won't crash on missing resources
 */
public class SafeMessageHelper {
    private static final String TAG = "SafeMessageHelper";
    private Toast currentToast;

    public void showMessage(Activity activity, String message) {
        if (activity == null || message == null || message.isEmpty()) {
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                // Cancel previous toast to avoid queue buildup
                if (currentToast != null) {
                    currentToast.cancel();
                }

                currentToast = Toast.makeText(activity, message, Toast.LENGTH_SHORT);
                currentToast.show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to show message", e);
            }
        });
    }

    public void showError(Activity activity, String message) {
        if (activity == null || message == null || message.isEmpty()) {
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                if (currentToast != null) {
                    currentToast.cancel();
                }

                currentToast = Toast.makeText(activity, "❌ " + message, Toast.LENGTH_LONG);
                currentToast.show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to show error", e);
            }
        });
    }

    public void hide(Activity activity) {
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            if (currentToast != null) {
                currentToast.cancel();
                currentToast = null;
            }
        });
    }
}