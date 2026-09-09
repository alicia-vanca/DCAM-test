package com.dvid.dcam.platform.database;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.sqlite.db.SupportSQLiteDatabase;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Publishes an internal Room snapshot for the trusted Toolbox diagnostic flow. */
public final class AppDatabaseExporter {
    public static final String ACTION_EXPORT_DATABASE =
            "com.dvid.dcam.action.EXPORT_DATABASE";
    public static final String EXPORTED_DATABASE_PATH =
            "/sdcard/Download/DCAM/dcam.db";

    private static final String DATABASE_NAME = "dcam.db";
    private static final String EXPORT_DIRECTORY = "DCAM";
    private static final String DATABASE_MIME_TYPE = "application/vnd.sqlite3";

    private AppDatabaseExporter() {
    }

    public static void export(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        File source = checkpointAndGetDatabase(appContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToMediaStore(appContext.getContentResolver(), source);
        } else {
            exportToLegacyDownloads(source);
        }
    }

    private static File checkpointAndGetDatabase(Context context) throws IOException {
        SupportSQLiteDatabase database = AppDatabase.get(context)
                .getOpenHelper()
                .getWritableDatabase();
        try (Cursor checkpoint = database.query("PRAGMA wal_checkpoint(FULL)")) {
            validateCheckpoint(checkpoint);
        }

        File source = context.getDatabasePath(DATABASE_NAME);
        if (!source.isFile() || source.length() == 0L) {
            throw new IOException("Dcam Room database is unavailable.");
        }
        return source;
    }

    static void validateCheckpoint(Cursor checkpoint) throws IOException {
        // PRAGMA query execution is lazy until the cursor is read.
        if (!checkpoint.moveToFirst() || checkpoint.getColumnCount() < 3) {
            throw new IOException("SQLite WAL checkpoint returned no status.");
        }
        requireCompleteCheckpoint(
                checkpoint.getInt(0), checkpoint.getLong(1), checkpoint.getLong(2));
    }

    static void requireCompleteCheckpoint(int busy, long walFrames, long checkpointedFrames)
            throws IOException {
        if (busy != 0 || checkpointedFrames < walFrames) {
            throw new IOException("SQLite WAL checkpoint was incomplete: busy=" + busy
                    + ", frames=" + walFrames + ", checkpointed=" + checkpointedFrames + '.');
        }
    }

    private static void exportToMediaStore(ContentResolver resolver, File source)
            throws IOException {
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_DIRECTORY;
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] selectionArgs = {DATABASE_NAME, relativePath + "/"};
        resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, selectionArgs);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, DATABASE_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, DATABASE_MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Could not create the public Dcam database export.");
        }

        boolean published = false;
        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new IOException("Could not open the public Dcam database export.");
            }
            Files.copy(source.toPath(), output);
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) {
                throw new IOException("Could not publish the public Dcam database export.");
            }
            published = true;
        } finally {
            if (!published) resolver.delete(uri, null, null);
        }
    }

    @SuppressWarnings("deprecation")
    private static void exportToLegacyDownloads(File source) throws IOException {
        File directory = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                EXPORT_DIRECTORY);
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create the public Dcam database export directory.");
        }

        Path target = new File(directory, DATABASE_NAME).toPath();
        Path temporary = new File(directory, "." + DATABASE_NAME + ".tmp").toPath();
        try {
            Files.copy(source.toPath(), temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | UnsupportedOperationException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
