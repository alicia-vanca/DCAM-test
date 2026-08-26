package com.dvid.dcam.platform.logging.app;

import android.content.Context;
import android.util.Log;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.BuildSecrets;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.platform.logging.loggly.LogglyCrashSpool;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App-process Logger adapter: writes Logcat, local logs.txt, Room, and crash
 * spool.
 */
public final class AppLogger implements Logger {
    private static final AppLogger INSTANCE = new AppLogger();
    private static final String TAG = "DCAM";
    private static final String UNKNOWN = "unknown";
    private static final String ERROR_LEVEL = "ERROR";
    private static final String CRASH_MESSAGE_PREFIX = "Crash on ";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter LOG_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId BDMA_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int LOCAL_LOG_RETENTION_DAYS = 14;
    private static final Object LOCAL_LOG_LOCK = new Object();
    private static final ExecutorService PERSISTENCE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "app-log-persistence");
        thread.setDaemon(true);
        return thread;
    });
    private static Context appContext;
    private static File logDir;
    private static File logFile;
    private static LocalDate activeLogDate;
    private static RoomLogWriter roomWriter;
    private static String hardwareId = UNKNOWN;
    private static String model = UNKNOWN;
    private static String deviceSerial;
    private static boolean crashHandlerInstalled;

    private AppLogger() {
    }

    public static Logger get() {
        return INSTANCE;
    }

    public static synchronized boolean hasDeviceSerial() {
        return deviceSerial != null;
    }

    public static synchronized void bootstrap(Context context) {
        appContext = context.getApplicationContext();
        installCrashHandler();
        if (logFile == null) {
            File root = appContext.getExternalFilesDir(null);
            if (root == null)
                root = appContext.getFilesDir();
            logDir = new File(root, "Logs");
            logDir.mkdirs();
            logFile = new File(logDir, "logs.txt");
            prepareLocalLog();
        }
        if (roomWriter == null) {
            try {
                roomWriter = new RoomLogWriter(appContext);
            } catch (Exception error) {
                writeInternal(ERROR_LEVEL, "Room log writer initialization failed", error);
            }
        }
    }

    public static synchronized void init(Context context, DeviceInfo deviceInfo) {
        bootstrap(context);
        hardwareId = safe(deviceInfo.getHardwareId());
        model = safe(deviceInfo.getModel());
        INSTANCE.info("Logger started: " + logFile.getAbsolutePath());
    }

    private static void installCrashHandler() {
        if (crashHandlerInstalled)
            return;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                recordCrash(thread, error);
            } finally {
                if (previous != null)
                    previous.uncaughtException(thread, error);
            }
        });
        crashHandlerInstalled = true;
    }

    /**
     * Crash logging is strictly best effort: a failure while recording a fatal crash must not
     * prevent delegation to the process's previous uncaught-exception handler.
     */
    @SuppressWarnings("java:S1181")
    private static void recordCrash(Thread thread, Throwable error) {
        String threadName = thread == null ? UNKNOWN : safe(thread.getName());
        String message = CRASH_MESSAGE_PREFIX + threadName;
        String source = crashSource(error);
        try {
            write(ERROR_LEVEL, message, error, source);
        } catch (Throwable loggingFailure) {
            try {
                writeInternal(ERROR_LEVEL, "Crash persistence failed", loggingFailure);
            } catch (Throwable ignored) {
                // A second logging failure must not interfere with crash delegation.
            }
            if (BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) {
                try {
                    spoolCrash(json(ERROR_LEVEL, message, error, threadName, source));
                } catch (Throwable ignored) {
                    // The crash spool is optional after primary crash persistence has failed.
                }
            }
        }
    }

    public static synchronized void setDeviceSerial(String nextDeviceSerial) {
        deviceSerial = nextDeviceSerial == null || nextDeviceSerial.isBlank()
                ? null
                : nextDeviceSerial.trim();
    }

    @Override
    public void debug(String message) {
        write("DEBUG", message, null);
    }

    @Override
    public void info(String message) {
        write("INFO", message, null);
    }

    @Override
    public void info(String message, Throwable error) {
        write("INFO", message, error);
    }

    @Override
    public void warn(String message, Throwable error) {
        write("WARN", message, error);
    }

    @Override
    public void error(String message, Throwable error) {
        write(ERROR_LEVEL, message, error);
    }

    private static void write(String level, String message, Throwable error) {
        write(level, message, error, callerClass());
    }

    private static void write(
            String level, String message, Throwable error, String source) {
        Log.println(toAndroidLevel(level), TAG, message + (error == null ? "" : "\n" + stackTrace(error)));
        String thread = safe(Thread.currentThread().getName());
        String resolvedSource = safe(source);
        String line = TIME.format(LocalDateTime.now(ZoneId.systemDefault())) + " " + level + " version=" + BuildConfig.VERSION_NAME
                + " thread=\"" + thread + "\" source=" + resolvedSource + " hardwareId=" + hardwareId
                + " model=\"" + model + "\" deviceSerial="
                + (deviceSerial == null ? "null" : "\"" + deviceSerial + "\"") + " message:\n" + message + "\n";
        String payload = json(level, message, error, thread, resolvedSource);
        if (!isCrashPersistence(level, message)) {
            persistAsync(level, line, error, payload);
            return;
        }
        boolean savedToRoom = persist(level, line, error, payload);
        boolean logglyConfigured = BuildSecrets.LOGGLY_TOKEN_CONFIGURED();
        if (shouldSpoolCrash(level, message, logglyConfigured, savedToRoom))
            spoolCrash(payload);
    }

    static boolean isCrashPersistence(String level, String message) {
        return ERROR_LEVEL.equals(level) && message != null && message.startsWith(CRASH_MESSAGE_PREFIX);
    }

    /**
     * Persistence runs off the caller thread, so every failure is contained to preserve logging
     * availability for subsequent events.
     */
    @SuppressWarnings("java:S1181")
    private static void persistAsync(
            String level, String line, Throwable error, String payload) {
        try {
            PERSISTENCE_EXECUTOR.execute(() -> {
                try {
                    persist(level, line, error, payload);
                } catch (Throwable persistenceFailure) {
                    try {
                        writeInternal(ERROR_LEVEL, "Asynchronous log persistence failed", persistenceFailure);
                    } catch (Throwable ignored) {
                        // Reporting a failed log write must not terminate the persistence worker.
                    }
                }
            });
        } catch (Throwable persistenceFailure) {
            try {
                writeInternal(ERROR_LEVEL, "Could not queue asynchronous log persistence", persistenceFailure);
            } catch (Throwable ignored) {
                // There is no synchronous fallback when the logging executor is unavailable.
            }
        }
    }

    private static boolean persist(
            String level, String line, Throwable error, String payload) {
        synchronized (LOCAL_LOG_LOCK) {
            if (logFile != null) {
                prepareLocalLog();
                try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
                    out.println(line);
                    if (error != null)
                        error.printStackTrace(out);
                } catch (Exception ignored) {
                    // Local file logging is optional; Room persistence still proceeds.
                }
            }
        }
        return writeToRoom(level, payload);
    }

    /** Room persistence is optional and must not make the application's logger throw. */
    @SuppressWarnings("java:S1181")
    private static boolean writeToRoom(String level, String payload) {
        if (roomWriter == null)
            return false;
        try {
            return roomWriter.write(level, payload);
        } catch (Throwable error) {
            try {
                writeInternal(ERROR_LEVEL, "Room log write failed", error);
            } catch (Throwable ignored) {
                // A diagnostic write cannot recover a failed Room write.
            }
            return false;
        }
    }

    static String crashSource(Throwable error) {
        if (error == null) return "UncaughtException";
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame != null) {
                String className = frame.getClassName();
                if (className != null && !className.isBlank()) {
                    int lastDot = className.lastIndexOf('.');
                    return lastDot < 0 ? className : className.substring(lastDot + 1);
                }
            }
        }
        return "UncaughtException";
    }

    static boolean shouldSpoolCrash(
            String level, String message, boolean logglyConfigured, boolean savedToRoom) {
        return logglyConfigured && !savedToRoom && ERROR_LEVEL.equals(level)
                && message != null && message.startsWith(CRASH_MESSAGE_PREFIX);
    }

    private static void spoolCrash(String payload) {
        if (appContext == null || payload == null || payload.isBlank())
            return;
        if (!LogglyCrashSpool.enqueue(appContext, payload)) {
            writeInternal(ERROR_LEVEL, "Could not queue crash for Loggly upload", null);
        }
    }

    static String json(String message, Throwable error, String thread, String source) {
        return json("INFO", message, error, thread, source);
    }

    static String json(String level, String message, Throwable error, String thread, String source) {
        String timestamp = OffsetDateTime.now(BDMA_TIME_ZONE)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String stack = stackTrace(error);
        return "{\"app\":\"DCAM\",\"version\":\"" + escape(BuildConfig.VERSION_NAME) + "\",\"level\":\""
                + escape(level) + "\",\"timestamp\":\"" + escape(timestamp) + "\",\"thread\":\""
                + escape(safe(thread)) + "\",\"source\":\""
                + escape(source) + "\",\"hardwareId\":\"" + escape(hardwareId) + "\",\"model\":\""
                + escape(model) + "\",\"deviceSerial\":"
                + (deviceSerial == null ? "null" : "\"" + escape(deviceSerial) + "\"")
                + ",\"message\":\"" + escape(message == null ? "" : message) + "\""
                + (stack.isEmpty() ? "" : ",\"stack\":\"" + escape(stack) + "\"") + "}";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String safe(String text) {
        return text == null || text.isBlank() ? UNKNOWN : text.trim().replaceAll("\\s+", " ");
    }

    private static String callerClass() {
        return callerClass(Thread.currentThread().getStackTrace());
    }

    static String callerClass(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(AppLogger.class.getName())
                    || className.equals(Thread.class.getName())
                    || className.equals("dalvik.system.VMStack")) {
                continue;
            }
            int lastDot = className.lastIndexOf('.');
            return lastDot < 0 ? className : className.substring(lastDot + 1);
        }
        return UNKNOWN;
    }

    private static String stackTrace(Throwable error) {
        if (error == null)
            return "";
        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }

    static synchronized void writeInternal(String level, String message, Throwable error) {
        Log.println(toAndroidLevel(level), TAG,
                message + (error == null ? "" : "\n" + stackTrace(error)));
        if (logFile == null)
            return;
        prepareLocalLog();
        try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
            out.println(TIME.format(LocalDateTime.now(ZoneId.systemDefault())) + " " + level + " message:\n" + message + "\n");
            if (error != null)
                error.printStackTrace(out);
        } catch (Exception ignored) {
            // Internal diagnostics must not throw back to the original logging operation.
        }
    }

    private static int toAndroidLevel(String level) {
        if (ERROR_LEVEL.equals(level))
            return Log.ERROR;
        if ("WARN".equals(level))
            return Log.WARN;
        if ("DEBUG".equals(level))
            return Log.DEBUG;
        return Log.INFO;
    }

    private static void prepareLocalLog() {
        if (logFile == null || logDir == null)
            return;
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate rotatedDate = null;
        if (activeLogDate == null)
            activeLogDate = existingLogDate(today);
        if (!activeLogDate.equals(today) && logFile.exists() && logFile.length() > 0) {
            File archive = new File(logDir, "logs-" + LOG_DATE.format(activeLogDate) + ".txt");
            try {
                if (archive.exists())
                    appendFile(logFile, archive);
                else
                    move(logFile, archive);
                rotatedDate = activeLogDate;
            } catch (Exception error) {
                Log.w(TAG, "Local log rotation failed", error);
            }
        }
        activeLogDate = today;
        deleteExpiredLocalLogs(today.minusDays(LOCAL_LOG_RETENTION_DAYS));
        if (rotatedDate != null && roomWriter != null)
            roomWriter.archiveDeadEvents(rotatedDate);
    }

    private static LocalDate existingLogDate(LocalDate fallback) {
        if (!logFile.exists() || logFile.length() == 0) {
            return fallback;
        }
        try {
            LocalDate modified = Instant.ofEpochMilli(logFile.lastModified())
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            return modified.isAfter(fallback) ? fallback : modified;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void deleteExpiredLocalLogs(LocalDate cutoff) {
        File[] files = logDir.listFiles((dir, name) -> name.startsWith("logs-") && name.endsWith(".txt"));
        if (files == null)
            return;
        for (File file : files) {
            String dateText = file.getName().substring(5, file.getName().length() - 4);
            try {
                if (LocalDate.parse(dateText, LOG_DATE).isBefore(cutoff)) {
                    Files.delete(file.toPath());
                }
            } catch (DateTimeParseException ignored) {
                // Leave unrecognized files untouched; they are not managed log archives.
            } catch (IOException error) {
                Log.w(TAG, "Could not delete expired local log " + file.getAbsolutePath(), error);
            }
        }
    }

    private static void appendFile(File source, File target) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
                FileOutputStream output = new FileOutputStream(target, true)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1)
                output.write(buffer, 0, read);
        }
        Files.delete(source.toPath());
    }

    private static void move(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
