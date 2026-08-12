package com.dvid.dcam.platform.storage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DcamFileName {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_FOLDER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");
    private DcamFileName() {}

    public static String dateFolder(LocalDateTime at) {
        return DATE_FOLDER.format(at);
    }

    static boolean matchesTypeSecond(
            DcamFileType type, LocalDateTime at, String fileName) {
        String marker = type.getMarker().isEmpty() ? "" : "_" + type.getMarker();
        String timestamp = "_" + DATE.format(at) + "_" + TIME.format(at) + marker;
        String extension = "." + type.getExtension();
        return fileName.startsWith("DCAM_")
                && (fileName.endsWith(timestamp + extension)
                || fileName.endsWith(timestamp + "_enc" + extension));
    }

    public static String build(DcamFileType type, String cameraId, String fileUserId,
                               LocalDateTime at, boolean encrypted) {
        String marker = type.getMarker().isEmpty() ? "" : "_" + type.getMarker();
        String enc = encrypted ? "_enc" : "";
        return "DCAM_" + cameraId + "_" + fileUserId + "_" + DATE.format(at) + "_" +
                TIME.format(at) + marker + enc + "." + type.getExtension();
    }
}
