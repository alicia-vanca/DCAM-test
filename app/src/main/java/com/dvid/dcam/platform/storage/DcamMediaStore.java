package com.dvid.dcam.platform.storage;

import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.time.LocalDateTime;

public final class DcamMediaStore {
    private DcamMediaStore() {}

    public static String relativePath(DcamFileType type, LocalDateTime at) {
        return "DCIM/Media/" + type.getFolder() + "/" + DcamFileName.dateFolder(at);
    }
    public static ContentValues values(DcamMediaFile mediaFile) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, mediaFile.getFileName());
        values.put(MediaStore.MediaColumns.MIME_TYPE, mediaFile.getType().getMimeType());
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(mediaFile.getType(), mediaFile.getCreatedAt()));
        return values;
    }
    public static Uri imageCollection() { return MediaStore.Images.Media.EXTERNAL_CONTENT_URI; }
    public static Uri videoCollection() { return MediaStore.Video.Media.EXTERNAL_CONTENT_URI; }
}
