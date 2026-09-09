package com.dvid.dcam.platform.logging.app;

import android.content.Context;
import android.util.Log;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.BuildSecrets;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.platform.logging.loggly.LogglyCrashSpool;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * App-process Logger adapter: writes Logcat, local logs.txt, Room, and crash
 * spool.
 */
public final class AppLogger implements Logger {
    private static final AppLogger INSTANCE = new AppLogger();
    private static final String TAG = "DCAM";
    private static final String UNKNOWN = "unknown";
    private static final String ERROR_LEVEL = "ERROR";
    private static final int LOG_SCHEMA_VERSION = 1;
    private static final String CRASH_MESSAGE_PREFIX = "Crash on ";
    private static final DateTimeFormatter LOG_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId BDMA_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int LOCAL_LOG_RETENTION_DAYS = 14;
    private static final Object LOCAL_LOG_LOCK = new Object();
    private static final Pattern SERIAL_VALUE_PATTERN = Pattern.compile(
            "(?i)(serial\\s*[:=]\\s*)([^\\s,;]+)");
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
                writeInternal(LogCategory.DB, "unspecified", null, ERROR_LEVEL, "Room log writer initialization failed", error);
            }
        }
    }

    public static synchronized void init(Context context, DeviceInfo deviceInfo) {
        bootstrap(context);
        hardwareId = safe(deviceInfo.getHardwareId());
        model = safe(deviceInfo.getModel());
        INSTANCE.info(LogCategory.APP, "unspecified", "Logger started: " + logFile.getAbsolutePath());
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
            write(LogCategory.APP, "unspecified", null, ERROR_LEVEL, message, error, source);
        } catch (Throwable loggingFailure) {
            try {
                writeInternal(LogCategory.APP, "unspecified", null, ERROR_LEVEL, "Crash persistence failed", loggingFailure);
            } catch (Throwable ignored) {
                // A second logging failure must not interfere with crash delegation.
            }
            if (BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) {
                try {
                    spoolCrash(json(LogCategory.APP, "unspecified", null,
                            ERROR_LEVEL, message, error, threadName, source));
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
    public void debug(LogCategory category, String eventName, String message) {
        write(category, eventName, null, "DEBUG", message, null);
    }

    @Override
    public void info(LogCategory category, String eventName, String message) {
        write(category, eventName, null, "INFO", message, null);
    }

    @Override
    public void info(LogCategory category, String eventName, String reasonCode,
            String message, Throwable error) {
        write(category, eventName, reasonCode, "INFO", message, error);
    }

    @Override
    public void warn(LogCategory category, String eventName, String reasonCode,
            String message, Throwable error) {
        write(category, eventName, reasonCode, "WARN", message, error);
    }

    @Override
    public void error(LogCategory category, String eventName, String reasonCode,
            String message, Throwable error) {
        write(category, eventName, reasonCode, ERROR_LEVEL, message, error);
    }

    private static void write(
            LogCategory category, String eventName, String reasonCode,
            String level, String message, Throwable error) {
        write(category, eventName, reasonCode, level, message, error, callerClass());
    }

    private static void write(
            LogCategory category, String eventName, String reasonCode,
            String level, String message, Throwable error, String source) {
        Log.println(toAndroidLevel(level), TAG, message + (error == null ? "" : "\n" + stackTrace(error)));
        String thread = safe(Thread.currentThread().getName());
        String resolvedSource = safe(source);
        String payload = json(category, eventName, reasonCode, level, message, error, thread, resolvedSource);
        if (!isCrashPersistence(level, message)) {
            persistAsync(level, payload);
            return;
        }
        boolean savedToRoom = persist(level, payload);
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
    private static void persistAsync(String level, String payload) {
        try {
            PERSISTENCE_EXECUTOR.execute(() -> {
                try {
                    persist(level, payload);
                } catch (Throwable persistenceFailure) {
                    try {
                        writeInternal(LogCategory.STORAGE, "unspecified", null, ERROR_LEVEL,
                                "Asynchronous log persistence failed", persistenceFailure);
                    } catch (Throwable ignored) {
                        // Reporting a failed log write must not terminate the persistence worker.
                    }
                }
            });
        } catch (Throwable persistenceFailure) {
            try {
                writeInternal(LogCategory.STORAGE, "unspecified", null, ERROR_LEVEL,
                        "Could not queue asynchronous log persistence", persistenceFailure);
            } catch (Throwable ignored) {
                // There is no synchronous fallback when the logging executor is unavailable.
            }
        }
    }

    private static boolean persist(String level, String payload) {
        synchronized (LOCAL_LOG_LOCK) {
            if (logFile != null) {
                prepareLocalLog();
                try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
                    out.println(payload);
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
                writeInternal(LogCategory.DB, "unspecified", null, ERROR_LEVEL, "Room log write failed", error);
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
            writeInternal(LogCategory.NETWORK, "unspecified", null, ERROR_LEVEL,
                    "Could not queue crash for Loggly upload", null);
        }
    }

    static String json(String message, Throwable error, String thread, String source) {
        return json(LogCategory.UNSPECIFIED, "unspecified", null,
                "INFO", message, error, thread, source);
    }

    static String json(
            String level, String message, Throwable error,
            String thread, String source) {
        return json(LogCategory.UNSPECIFIED, "unspecified", null,
                level, message, error, thread, source);
    }

    static String json(
            LogCategory category, String level, String message,
            Throwable error, String thread, String source) {
        return json(category, "unspecified", null, level, message, error, thread, source);
    }

    static String json(
            LogCategory category, String eventName, String reasonCode,
            String level, String message, Throwable error, String thread, String source) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("schemaVersion", LOG_SCHEMA_VERSION);
            payload.put("eventName", valueOr(eventName, "unspecified"));
            if ("WARN".equals(level) || ERROR_LEVEL.equals(level)) {
                payload.put("reasonCode", valueOr(reasonCode, "unspecified"));
            } else if (reasonCode != null && !reasonCode.isBlank()
                    && !"unspecified".equals(reasonCode)) {
                payload.put("reasonCode", reasonCode);
            }
            payload.put("app", "DCAM");
            payload.put("version", BuildConfig.VERSION_NAME);
            payload.put("level", valueOr(level, UNKNOWN));
            payload.put("timestamp", OffsetDateTime.now(BDMA_TIME_ZONE)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            payload.put("thread", valueOr(localValue(thread), UNKNOWN));
            payload.put("source", valueOr(source, UNKNOWN));
            String resolvedHardwareId = valueOr(hardwareId, UNKNOWN);
            payload.put("hardwareId", UNKNOWN.equals(resolvedHardwareId)
                    ? UNKNOWN : maskExplicit(resolvedHardwareId));
            payload.put("model", valueOr(model, UNKNOWN));
            String serial = valueOr(deviceSerial, UNKNOWN);
            payload.put("deviceSerial", UNKNOWN.equals(serial) ? UNKNOWN : maskExplicit(serial));
            String normalizedMessage = sanitizeLocalText(message);
            payload.put("message", valueOr(normalizedMessage, ""));
            String stack = sanitizeLocalText(stackTrace(error));
            if (!stack.isBlank())
                payload.put("stack", stack);
            payload.put("category", category == null ? LogCategory.UNSPECIFIED.name() : category.name());
            return payload.toString();
        } catch (JSONException ignored) {
            return new JSONObject().toString();
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String maskExplicit(String value) {
        if (value == null || value.isEmpty())
            return value;
        int length = value.length();
        int visiblePrefixLength = length >= 9 ? 2 : 0;
        int visibleSuffixLength = length <= 4 ? 0 : length <= 8 ? 2 : 4;
        StringBuilder masked = new StringBuilder(value);
        for (int index = visiblePrefixLength; index < length - visibleSuffixLength; index++)
            masked.setCharAt(index, '*');
        return masked.toString();
    }

    private static String maskSerialValues(String text) {
        Matcher matcher = SERIAL_VALUE_PATTERN.matcher(text);
        StringBuffer masked = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + maskExplicit(matcher.group(2));
            matcher.appendReplacement(masked, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    private static String sanitizeLocalText(String text) {
        if (text == null)
            return null;
        String sanitized = maskSerialValues(text);
        sanitized = sanitized.replaceAll(
                "(?i)(password|passwd|credential|token|secret|android[_ ]id|latitude|longitude|location)\\s*[:=]\\s*[^\\s,;]+",
                "$1=[REDACTED]");
        sanitized = sanitized.replaceAll(
                "(?i)(bearer\\s+)[^\\s,;]+",
                "$1[REDACTED]");
        return sanitized.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static String localValue(String text) {
        if (text == null || text.isBlank() || UNKNOWN.equals(text))
            return null;
        return text.trim().replaceAll("\\s+", " ");
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

    static synchronized void writeInternal(
            LogCategory category, String eventName, String reasonCode,
            String level, String message, Throwable error) {
        Log.println(toAndroidLevel(level), TAG,
                message + (error == null ? "" : "\n" + stackTrace(error)));
        if (logFile == null)
            return;
        prepareLocalLog();
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
            out.println(json(
                    category, eventName, reasonCode, level, message, error,
                    Thread.currentThread().getName(), "AppLogger"));
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
