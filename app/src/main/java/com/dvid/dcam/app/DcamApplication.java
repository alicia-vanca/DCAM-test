package com.dvid.dcam.app;

import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import com.dvid.dcam.platform.logging.DcamLogger;
import com.dvid.dcam.platform.logging.LogglyDrainService;
import com.dvid.dcam.platform.device.AndroidDeviceCapabilities;

public final class DcamApplication extends Application {
    private final ServiceConnection logglyConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) { }
        @Override public void onServiceDisconnected(ComponentName name) { }
    };

    @Override public void onCreate() {
        super.onCreate();
        if (processName().endsWith(":loggly")) return;
        new AndroidDeviceCapabilities(this).evaluate();
        DcamLogger.setRemoteUploadsEnabled(true);
        DcamLogger.setContinuousDrainEnabled(LogglyDrainService.start(this));
        try {
            bindService(new Intent(this, LogglyDrainService.class), logglyConnection,
                    Context.BIND_AUTO_CREATE);
        } catch (RuntimeException error) {
            DcamLogger.sendBootstrapFailure("Could not bind Loggly drain service", error);
        }
        DcamLogger.bootstrap(this);
    }

    private String processName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return Application.getProcessName();
        ActivityManager manager = getSystemService(ActivityManager.class);
        if (manager != null) {
            for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
                if (process.pid == Process.myPid()) return process.processName;
            }
        }
        return getPackageName();
    }
}
