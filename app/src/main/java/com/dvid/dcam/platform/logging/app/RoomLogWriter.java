package com.dvid.dcam.platform.logging.app;

import android.content.Context;
import com.dvid.dcam.BuildSecrets;
import com.dvid.dcam.platform.database.AppDatabase;
import com.dvid.dcam.platform.database.dao.PendingLogDao;
import com.dvid.dcam.platform.database.entities.PendingLogEntity;
import com.dvid.dcam.platform.logging.loggly.LogglyUploadScheduler;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** App-process Room roomExecutor for the pending cloud-upload queue. */
final class RoomLogWriter {
    private static final long RETENTION_MS = 14L * 24 * 60 * 60 * 1_000;
    private static final long SOFT_LIMIT_BYTES = 100L * 1024 * 1024;
    private static final long HARD_LIMIT_BYTES = 150L * 1024 * 1024;
    private static final int PRUNE_CHUNK = 100;
    private static final int SUMMARY_EVENT_ID_LIMIT = 50;
    private static final String DEAD_ARCHIVE_PREFIX = "loggly-dead-";
    private static final String ERROR_LEVEL = "ERROR";

    private final Context appContext;
    private final AppDatabase database;
    private final PendingLogDao pendingLogs;
    private final ExecutorService roomExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "room-log-outbox-roomExecutor");
        thread.setDaemon(true);
        return thread;
    });

    RoomLogWriter(Context appContext) {
        this.appContext = appContext.getApplicationContext();
        database = AppDatabase.get(this.appContext);
        pendingLogs = database.pendingLogs();
        executeSafely(() -> {
            prune();
            scheduleUpload();
        }, "Room log writer startup failed");
    }

    boolean write(String level, String payload) {
        return runAndWait(() -> {
            pendingLogs.insert(newPendingLog(level, payload));
            prune();
            if (BuildSecrets.LOGGLY_TOKEN_CONFIGURED() && pendingLogs.pendingCount() == 1) {
                LogglyUploadScheduler.scheduleJobNow(appContext);
            }
        }, "Failed to persist Room log event");
    }

    void archiveDeadEvents(LocalDate archiveDate) {
        runAndWait(() -> archiveDeadEventsInternal(archiveDate), "Failed to archive dead Loggly events");
    }

    private PendingLogEntity newPendingLog(String level, String payload) {
        String eventId = UUID.randomUUID().toString();
        String identifiedPayload = payload.startsWith("{")
                ? "{\"eventId\":\"" + eventId + "\"," + payload.substring(1)
                : payload;
        return new PendingLogEntity(eventId, System.currentTimeMillis(), level, identifiedPayload);
    }

    private void archiveDeadEventsInternal(LocalDate archiveDate) {
        List<PendingLogEntity> dead = pendingLogs.deadEvents();
        File logDir = logDir();
        deleteExpiredDeadLogs(logDir, LocalDate.now(ZoneId.systemDefault()).minusDays(14));
        if (dead.isEmpty()) return;

        File archive = new File(logDir, DEAD_ARCHIVE_PREFIX + archiveDate + ".log");
        try (FileOutputStream stream = new FileOutputStream(archive, true);
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
            for (PendingLogEntity event : dead) {
                output.write(deadArchiveLine(event));
                output.newLine();
            }
            output.flush();
            stream.getFD().sync();
        } catch (Exception error) {
            AppLogger.writeInternal(ERROR_LEVEL, "Dead-event archive write failed", error);
            return;
        }

        List<Long> deadIds = dead.stream().map(event -> event.id).collect(Collectors.toList());
        PendingLogEntity summary = newPendingLog("WARN", summaryPayload(archive.getName(), dead));
        database.runInTransaction(() -> {
            pendingLogs.insert(summary);
            pendingLogs.deleteIds(deadIds);
        });
        if (BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) LogglyUploadScheduler.scheduleJobNow(appContext);
    }

    /**
     * CharSequence.isEmpty() requires API 35; length() preserves compatibility with minSdk 26.
     */
    @SuppressWarnings("java:S7158")
    private String summaryPayload(String archiveName, List<PendingLogEntity> events) {
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        int debug = 0;
        int info = 0;
        int warn = 0;
        int error = 0;
        int sampled = 0;
        StringBuilder ids = new StringBuilder();
        for (PendingLogEntity event : events) {
            first = Math.min(first, event.createdAt);
            last = Math.max(last, event.createdAt);
            if (ERROR_LEVEL.equals(event.level)) error++;
            else if ("WARN".equals(event.level)) warn++;
            else if ("DEBUG".equals(event.level)) debug++;
            else info++;
            if (sampled >= SUMMARY_EVENT_ID_LIMIT) continue;
            if (ids.length() > 0) ids.append(',');
            ids.append('"').append(escape(event.eventId)).append('"');
            sampled++;
        }
        return "{\"type\":\"loggly_dead_summary\",\"archive\":\"" + escape(archiveName)
                + "\",\"deadCount\":" + events.size() + ",\"firstEventAt\":" + first
                + ",\"lastEventAt\":" + last + ",\"levels\":{\"DEBUG\":" + debug
                + ",\"INFO\":" + info
                + ",\"WARN\":" + warn + ",\"ERROR\":" + error + "},\"eventIdSample\":[" + ids + "]}";
    }

    private String deadArchiveLine(PendingLogEntity event) {
        return "{\"eventId\":\"" + escape(event.eventId) + "\",\"attemptCount\":" + event.attemptCount
                + ",\"firstFailedAt\":" + event.firstFailedAt + ",\"lastError\":\""
                + escape(event.lastError == null ? "" : event.lastError) + "\",\"payload\":" + event.payload + "}";
    }

    private File logDir() {
        File root = appContext.getExternalFilesDir(null);
        if (root == null) root = appContext.getFilesDir();
        File dir = new File(root, "Logs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void deleteExpiredDeadLogs(File dir, LocalDate cutoff) {
        File[] files = dir.listFiles(
                (parent, name) -> name.startsWith(DEAD_ARCHIVE_PREFIX) && name.endsWith(".log"));
        if (files == null) return;
        for (File file : files) {
            String date = file.getName().substring(
                    DEAD_ARCHIVE_PREFIX.length(), file.getName().length() - 4);
            try {
                if (LocalDate.parse(date).isBefore(cutoff)) {
                    Files.delete(file.toPath());
                }
            } catch (DateTimeParseException ignored) {
                // Leave unrecognized files untouched; they are not managed dead-event archives.
            } catch (IOException error) {
                AppLogger.writeInternal("WARN", "Could not delete expired " + file.getName(), error);
            }
        }
    }

    private boolean runAndWait(Runnable action, String failureMessage) {
        Future<?> saved = null;
        try {
            saved = roomExecutor.submit(action);
            saved.get(2, TimeUnit.SECONDS);
            return true;
        } catch (Exception error) {
            if (saved != null) saved.cancel(true);
            AppLogger.writeInternal(ERROR_LEVEL, failureMessage, cause(error));
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private void executeSafely(Runnable action, String failureMessage) {
        roomExecutor.execute(() -> {
            try {
                action.run();
            } catch (RuntimeException error) {
                AppLogger.writeInternal(ERROR_LEVEL, failureMessage, error);
            }
        });
    }

    private static Throwable cause(Exception error) {
        if (error instanceof ExecutionException && error.getCause() != null) return error.getCause();
        return error;
    }

    private void prune() {
        pendingLogs.deleteExpiredLowPriority(System.currentTimeMillis() - RETENTION_MS);
        while (pendingLogs.payloadBytes() > SOFT_LIMIT_BYTES
                && pendingLogs.deleteOldestLowPriority(PRUNE_CHUNK) > 0) {
            // Each DAO call deletes one pruning chunk; reevaluate the queue size before the next.
        }
        boolean hardPruned = false;
        while (pendingLogs.payloadBytes() > HARD_LIMIT_BYTES && pendingLogs.deleteOldest(PRUNE_CHUNK) > 0) hardPruned = true;
        if (hardPruned) {
            AppLogger.writeInternal(ERROR_LEVEL,
                    "Room log queue exceeded hard limit; oldest events removed", null);
        }
    }

    private void scheduleUpload() {
        if (!BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) return;
        if (pendingLogs.pendingCount() > 0) LogglyUploadScheduler.scheduleJobNow(appContext);
        else LogglyUploadScheduler.scheduleRetry(appContext, pendingLogs.earliestRetryAt());
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
