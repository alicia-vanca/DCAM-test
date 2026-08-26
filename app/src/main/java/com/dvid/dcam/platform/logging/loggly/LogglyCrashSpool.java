package com.dvid.dcam.platform.logging.loggly;

import android.content.Context;
import android.util.Log;
import com.dvid.dcam.BuildSecrets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Durable crash fallback consumed by :loggly without Room or foreground-service startup. */
public final class LogglyCrashSpool {
    private static final String TAG = "LogglyUpload";
    private static final String DIRECTORY_NAME = "loggly-crash-spool";
    private static final int MAX_PENDING_FILES = 32;
    private static final long RETRY_DELAY_MS = 10_000L;
    private static final Object UPLOAD_LOCK = new Object();

    private LogglyCrashSpool() {
        // Static utility class.
    }

    public static boolean enqueue(Context context, String payload) {
        if (context == null || payload == null || payload.isBlank()) return false;
        try {
            enqueue(directory(context), payload);
        } catch (IOException | RuntimeException error) {
            Log.e(TAG, "Could not persist crash spool", error);
            return false;
        }
        if (!LogglyUploadScheduler.scheduleJobNow(context)) {
            Log.w(TAG, "Crash spooled but upload job could not be scheduled");
        }
        return true;
    }

    static void enqueue(File directory, String payload) throws IOException {
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload is blank");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create crash spool " + directory);
        }
        String name = String.format("%019d-%s.json", System.currentTimeMillis(), UUID.randomUUID());
        File target = new File(directory, name);
        File partial = new File(directory, "." + name + ".tmp");
        try (FileOutputStream output = new FileOutputStream(partial)) {
            output.write(payload.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            move(partial, target);
            syncDirectory(directory);
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
        pruneOldest(directory);
    }

    static Long uploadPending(Context context, BooleanSupplier uploadStopped) {
        if (!BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) return null;
        synchronized (UPLOAD_LOCK) {
            try {
                return uploadPending(directory(context), uploadStopped, LogglyHttpClient::send);
            } catch (IOException | RuntimeException error) {
                Log.e(TAG, "Could not upload crash spool", error);
                return System.currentTimeMillis() + RETRY_DELAY_MS;
            }
        }
    }

    static Long uploadPending(
            File directory, BooleanSupplier uploadStopped, Function<String, String> sender)
            throws IOException {
        List<File> pending = pendingFiles(directory);
        Long retryAtMillis = null;
        for (File file : pending) {
            if (uploadStopped.getAsBoolean()) {
                return nextRetryAt(retryAtMillis);
            }
            String payload;
            try {
                payload = read(file);
            } catch (IOException error) {
                Log.e(TAG, "Could not read crash spool " + file.getName(), error);
                retryAtMillis = nextRetryAt(retryAtMillis);
                continue;
            }
            String failure = sender.apply(payload);
            if (failure != null) {
                retryAtMillis = nextRetryAt(retryAtMillis);
            } else {
                retryAtMillis = deleteDelivered(file, retryAtMillis);
            }
        }
        return retryAtMillis;
    }

    private static Long nextRetryAt(Long retryAtMillis) {
        return retryAtMillis == null ? System.currentTimeMillis() + RETRY_DELAY_MS : retryAtMillis;
    }

    private static Long deleteDelivered(File file, Long retryAtMillis) {
        try {
            Files.delete(file.toPath());
        } catch (NoSuchFileException ignored) {
            // Another process already removed the delivered spool file.
        } catch (IOException error) {
            Log.w(TAG, "Could not delete delivered crash spool " + file.getName(), error);
            return nextRetryAt(retryAtMillis);
        }
        return retryAtMillis;
    }

    private static File directory(Context context) {
        return new File(context.getNoBackupFilesDir(), DIRECTORY_NAME);
    }

    private static List<File> pendingFiles(File directory) {
        File[] files = directory.listFiles((parent, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) return List.of();
        Arrays.sort(files, Comparator.comparing(File::getName));
        return new ArrayList<>(Arrays.asList(files));
    }

    private static String read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
            return new String(bytes, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static void pruneOldest(File directory) {
        List<File> files = pendingFiles(directory);
        for (int index = 0; index < files.size() - MAX_PENDING_FILES; index++) {
            File file = files.get(index);
            if (!deletePruned(file)) {
                return;
            }
        }
    }

    private static boolean deletePruned(File file) {
        try {
            Files.delete(file.toPath());
            return true;
        } catch (NoSuchFileException ignored) {
            // Another process already removed this old spool file.
            return true;
        } catch (IOException error) {
            Log.w(TAG, "Could not prune crash spool " + file.getName(), error);
            return false;
        }
    }

    private static void move(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void syncDirectory(File directory) {
        try (FileChannel channel = FileChannel.open(directory.toPath())) {
            channel.force(true);
        } catch (IOException ignored) {
            // The file payload is already synced; directory metadata sync is best effort.
        }
    }
}
