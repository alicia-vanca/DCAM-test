package com.dvid.dcam.platform.logging.loggly;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import com.dvid.dcam.BuildSecrets;

/** Starts Loggly foreground upload and retains JobScheduler fallback. */
public final class LogglyUploadScheduler {
    private static final String TAG = "LogglyUpload";
    private static final int UPLOAD_JOB_ID = 0xDC04;
    private static final int RETRY_JOB_ID = 0xDC05;

    private LogglyUploadScheduler() { }

    public static void scheduleNow(Context context) {
        if (!BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) return;
        LogglyUploadService.start(context);
        cancelRetry(context);
        scheduleJobNow(context);
    }

    public static void scheduleRetry(Context context, Long nextRetryAtMillis) {
        if (!BuildSecrets.LOGGLY_TOKEN_CONFIGURED() || nextRetryAtMillis == null) return;
        schedule(context, RETRY_JOB_ID, Math.max(0L, nextRetryAtMillis - System.currentTimeMillis()));
    }

    static boolean scheduleJobNow(Context context) {
        if (!BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) return false;
        return schedule(context, UPLOAD_JOB_ID, 0L);
    }

    private static void cancelRetry(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.cancel(RETRY_JOB_ID);
    }

    private static boolean schedule(Context context, int jobId, long delayMillis) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return false;
        JobInfo job = new JobInfo.Builder(jobId,
                new ComponentName(context, LogglyUploadJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(delayMillis)
                .setPersisted(true)
                .build();
        try {
            int result = scheduler.schedule(job);
            Log.i(TAG, "schedule jobId=" + jobId + " result=" + result);
            return result == JobScheduler.RESULT_SUCCESS;
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not schedule Loggly upload job", error);
            return false;
        }
    }
}
