package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/** Durable marker proving local media encryption completed before publication. */
final class DcamEncryptionJournal {
    private static final String SUFFIX = ".enc-complete";

    private DcamEncryptionJournal() {}

    static boolean isComplete(DcamMediaFile mediaFile) {
        return marker(mediaFile.getFile()).isFile();
    }

    static boolean isComplete(File stagingFile) {
        return marker(stagingFile).isFile();
    }

    static boolean isMarker(File file) {
        return file != null && file.getName().endsWith(SUFFIX);
    }

    static void markComplete(DcamMediaFile mediaFile) throws IOException {
        File marker = marker(mediaFile.getFile());
        File parent = marker.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        Files.write(marker.toPath(), "complete".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    static void clear(DcamMediaFile mediaFile) {
        try {
            Files.deleteIfExists(marker(mediaFile.getFile()).toPath());
        } catch (IOException ignored) {}
    }

    private static File marker(File mediaFile) {
        return new File(mediaFile.getPath() + SUFFIX);
    }
}