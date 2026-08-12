package com.dvid.dcam.platform.logging.loggly;

import android.content.Context;
import android.util.Log;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.platform.database.AppDatabase;
import com.dvid.dcam.platform.database.dao.PendingLogDao;
import com.dvid.dcam.platform.database.entities.PendingLogEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** :loggly worker: reads Room, sends logs, and stores retry state back in Room. */
final class RoomLogUploader {
    private static final String TAG = "LogglyUpload";
    private static final int NEW_LOG_BATCH_SIZE = 80;
    private static final int RETRY_LOG_BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 20;
    private static final long BASE_RETRY_MS = 10_000L;
    private static final long MAX_RETRY_MS = 5L * 60 * 60 * 1_000;

    private RoomLogUploader() { }

    static synchronized Long uploadPending(Context context, BooleanSupplier uploadStopped) {
        if (BuildConfig.LOGGLY_TOKEN.isBlank()) return null;
        PendingLogDao pendingLogs;
        try {
            pendingLogs = AppDatabase.get(context).pendingLogs();
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not open Room log queue", error);
            return System.currentTimeMillis() + BASE_RETRY_MS;
        }

        while (!uploadStopped.getAsBoolean()) {
            List<PendingLogEntity> newLogs = pendingLogs.oldestPending(NEW_LOG_BATCH_SIZE);
            List<PendingLogEntity> retryLogs = pendingLogs.dueRetries(
                    System.currentTimeMillis(), RETRY_LOG_BATCH_SIZE);
            if (newLogs.isEmpty() && retryLogs.isEmpty()) break;
            if (uploadStopped.getAsBoolean()) return System.currentTimeMillis() + BASE_RETRY_MS;
            if (!uploadBatch(pendingLogs, newLogs)) break;
            if (uploadStopped.getAsBoolean()) return System.currentTimeMillis() + BASE_RETRY_MS;
            if (!uploadBatch(pendingLogs, retryLogs)) break;
        }
        if (uploadStopped.getAsBoolean()) return System.currentTimeMillis() + BASE_RETRY_MS;
        return pendingLogs.earliestRetryAt();
    }

    private static boolean uploadBatch(
            PendingLogDao pendingLogs, List<PendingLogEntity> logs) {
        if (logs.isEmpty()) return true;
        List<String> payloads = new ArrayList<>(logs.size());
        List<Long> ids = new ArrayList<>(logs.size());
        for (PendingLogEntity pendingLog : logs) {
            payloads.add(payloadForUpload(pendingLog));
            ids.add(pendingLog.id);
        }
        String errorMessage = LogglyHttpClient.sendBatch(payloads);
        if (errorMessage == null) {
            pendingLogs.deleteIds(ids);
            return true;
        }
        for (PendingLogEntity pendingLog : logs) {
            recordFailure(pendingLogs, pendingLog, errorMessage);
        }
        return false;
    }

    static long retryDelayMillis(int attemptCount) {
        long delay = BASE_RETRY_MS;
        for (int attempt = 1; attempt < attemptCount && delay < MAX_RETRY_MS; attempt++) {
            delay = Math.min(MAX_RETRY_MS, delay * 2);
        }
        return delay;
    }

    private static void recordFailure(
            PendingLogDao pendingLogs, PendingLogEntity pendingLog, String errorMessage) {
        long now = System.currentTimeMillis();
        int attemptCount = pendingLog.attemptCount + 1;
        long firstFailedAt = pendingLog.firstFailedAt == 0 ? now : pendingLog.firstFailedAt;
        if (attemptCount >= MAX_ATTEMPTS) {
            pendingLogs.markDead(pendingLog.id, attemptCount, firstFailedAt, errorMessage);
            Log.e(TAG, "Log quarantined after " + attemptCount
                    + " attempts eventId=" + pendingLog.eventId);
        } else {
            pendingLogs.markRetry(pendingLog.id, attemptCount,
                    now + retryDelayMillis(attemptCount), firstFailedAt, errorMessage);
        }
    }

    private static String payloadForUpload(PendingLogEntity pendingLog) {
        if (!pendingLog.payload.startsWith("{")) return pendingLog.payload;
        return "{\"sequenceId\":" + pendingLog.id + ",\"createdAtEpochMs\":"
                + pendingLog.createdAt + "," + pendingLog.payload.substring(1);
    }
}