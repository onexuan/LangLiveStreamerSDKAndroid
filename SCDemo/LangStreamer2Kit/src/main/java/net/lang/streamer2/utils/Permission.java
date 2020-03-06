package net.lang.streamer2.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

public class Permission {
    private static final String TAG = Permission.class.getSimpleName();

    public static boolean checkPermissions(Context context, String[] requestPermissions) {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return true;
        }

        Activity currentActivity = null;
        while (context instanceof android.content.ContextWrapper) {
            if (context instanceof Activity) {
                currentActivity = (Activity) context;
                break;
            }
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        if (context == null) {
            Log.e(TAG, "checkPermissions: cannot find activity from current view!");
            return false;
        }

        for (int i = 0; i < requestPermissions.length; i++) {
            String requestPermission = requestPermissions[i];
            int checkSelfPermission = -1;
            try {
                checkSelfPermission = ActivityCompat.checkSelfPermission(currentActivity, requestPermission);
            } catch (RuntimeException e) {
                DebugLog.e(TAG, "RuntimeException: " + e.getMessage());
                return false;
            }

            if (checkSelfPermission != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "checkPermissions: no " + requestPermission + "permission!");
                return false;
            }
        }

        return true;
    }
}
