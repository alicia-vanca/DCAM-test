package com.dvid.dcam.app;

import android.app.ActivityManager;
import android.app.Application;
import android.os.Build;
import android.os.Process;
import androidx.annotation.NonNull;
import androidx.work.Configuration;
import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.platform.device.AndroidDeviceRepositoryImpl;
import com.dvid.dcam.platform.logging.app.AppLogger;
import com.dvid.dcam.app.resourcemonitor.ResourceMonitorProcessReporter;
import com.dvid.dcam.platform.logging.loggly.LogglyProcessSupervisor;
import com.dvid.dcam.platform.recording.RecordingForegroundService;

public final class DcamApplication extends Application implements Configuration.Provider {
    private static final int WORK_MANAGER_JOB_ID_MIN = 0xE000;
    private static final int WORK_MANAGER_JOB_ID_MAX = 0xEFFF;

    @Override public void onCreate() {
        super.onCreate();
        ResourceMonitorProcessReporter.install(this, AppLogger.get());
        if (processName().endsWith(":loggly")) return;
        AppLogger.bootstrap(this);
        DeviceInfo deviceInfo = new AndroidDeviceRepositoryImpl(this, getFilesDir()).readInfo();
        AppLogger.init(this, deviceInfo);
        LogglyProcessSupervisor logglySupervisor = new LogglyProcessSupervisor(this);
        logglySupervisor.start();
        AppComposition.loadConfiguredDeviceSerial(this);
        AppComposition.startCameraCapabilities(this, AppLogger.get());
        finalizeInterruptedCapture();
    }

    @NonNull @Override public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setJobSchedulerJobIdRange(WORK_MANAGER_JOB_ID_MIN, WORK_MANAGER_JOB_ID_MAX)
                .build();
    }

    private void finalizeInterruptedCapture() {
        if (!RecordingForegroundService.hasUnfinishedCapture(this)) return;
        RecordingForegroundService.markUnfinishedCaptureFinalizing(this);
        try {
            AppComposition.create(this);
        } catch (RuntimeException error) {
            AppLogger.get().error("Could not prepare interrupted media finalization", error);
        }
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