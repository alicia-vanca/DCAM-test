package com.dvid.dcam.platform.logging.loggly;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.dvid.dcam.BuildSecrets;

/** Uses foreground Activity launch semantics to warm :loggly before optimizers can block service startup. */
public final class LogglyProcessBootstrapActivity extends Activity {
    private static final String TAG = "LogglyUpload";

    public static boolean start(Activity activity) {
        if (activity == null || !BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) return false;
        Intent intent = new Intent(activity, LogglyProcessBootstrapActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            Log.i(TAG, "launch bootstrap activity request");
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not launch Loggly process bootstrap activity", error);
            return false;
        }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "launch bootstrap activity onCreate pid=" + android.os.Process.myPid());
        LogglyUploadService.start(this);
        finish();
    }
}