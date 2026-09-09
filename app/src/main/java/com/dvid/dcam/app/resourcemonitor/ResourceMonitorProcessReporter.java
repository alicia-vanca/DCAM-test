package com.dvid.dcam.app.resourcemonitor;

import android.app.ActivityManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import androidx.core.content.ContextCompat;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;

/** Reports self metrics only when Resource Monitor explicitly requests a sample. */
public final class ResourceMonitorProcessReporter {
    public static final String REQUEST_ACTION_SUFFIX = ".RESOURCE_MONITOR_REQUEST";
    public static final String RESPONSE_ACTION_SUFFIX = ".RESOURCE_MONITOR_RESPONSE";
    public static final String EXTRA_REQUEST_ID = "request_id";
    public static final String EXTRA_PROCESS_PID = "process_pid";
    public static final String EXTRA_PROCESS_UID = "process_uid";
    public static final String EXTRA_PROCESS_NAME = "process_name";
    public static final String EXTRA_CPU_TIME_MS = "cpu_time_ms";
    public static final String EXTRA_INCLUDE_PSS = "include_pss";
    public static final String EXTRA_PSS_BYTES = "pss_bytes";
    public static final String EXTRA_HEAP_USED_BYTES = "heap_used_bytes";
    public static final String EXTRA_HEAP_LIMIT_BYTES = "heap_limit_bytes";

    private static boolean installed;
    private ResourceMonitorProcessReporter() {
    }

    public static synchronized void install(Context context, Logger logger) {
        if (installed) return;
        Context application = context.getApplicationContext();
        Context target = application == null ? context : application;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                if (!requestAction(receiverContext).equals(intent.getAction())) return;
                String requestId = intent.getStringExtra(EXTRA_REQUEST_ID);
                if (requestId == null || requestId.trim().isEmpty()) return;
                sendResponse(receiverContext, requestId,
                        intent.getBooleanExtra(EXTRA_INCLUDE_PSS, false),
                        logger);
            }
        };
        String action = requestAction(target);
        logger.info(LogCategory.PERF, "unspecified", "Resource Monitor process reporter registering in "
                + processName(target) + " PID " + Process.myPid() + ". Action: "
                + action + ". Package: " + target.getPackageName() + ".");
        ContextCompat.registerReceiver(target, receiver,
                new IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED);
        installed = true;
        logger.info(LogCategory.PERF, "unspecified", "Resource Monitor process reporter registered in "
                + processName(target) + " PID " + Process.myPid() + ".");
    }

    public static String requestAction(Context context) {
        return context.getPackageName() + REQUEST_ACTION_SUFFIX;
    }

    public static String responseAction(Context context) {
        return context.getPackageName() + RESPONSE_ACTION_SUFFIX;
    }

    public static String processName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String name = Application.getProcessName();
            if (name != null && !name.trim().isEmpty()) return name;
        }
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager != null) {
            for (ActivityManager.RunningAppProcessInfo process
                    : manager.getRunningAppProcesses()) {
                if (process.pid == Process.myPid() && process.processName != null) {
                    return process.processName;
                }
            }
        }
        return context.getPackageName();
    }

    private static void sendResponse(Context context, String requestId, boolean includePss,
            Logger logger) {
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
        long pssBytes = includePss ? currentPssBytes() : -1L;
        String processName = processName(context);
        int pid = Process.myPid();
        Intent response = new Intent(responseAction(context))
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_PROCESS_PID, pid)
                .putExtra(EXTRA_PROCESS_UID, Process.myUid())
                .putExtra(EXTRA_PROCESS_NAME, processName)
                .putExtra(EXTRA_CPU_TIME_MS, Process.getElapsedCpuTime())
                .putExtra(EXTRA_PSS_BYTES, pssBytes)
                .putExtra(EXTRA_HEAP_USED_BYTES, heapUsed)
                .putExtra(EXTRA_HEAP_LIMIT_BYTES, runtime.maxMemory());
        try {
            int responseCode = 31 * requestId.hashCode() + pid;
            PendingIntent.getBroadcast(context, responseCode, response,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE).send();
        } catch (PendingIntent.CanceledException | RuntimeException error) {
            logger.warn(LogCategory.PERF, "unspecified", null, "Resource Monitor process reporter could not send response "
                    + requestId + " from " + processName + " PID " + pid + ".", error);
        }
    }

    private static long currentPssBytes() {
        try {
            long pssKilobytes = Debug.getPss();
            return pssKilobytes < 0L ? -1L : pssKilobytes * 1024L;
        } catch (RuntimeException error) {
            return -1L;
        }
    }
}
