package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.IOException;

/** Filesystem readiness boundary: validate staging, publish safely, then remove staging. */
public final class DcamMediaFinalizer {
    private final DcamStorage storage;
    private final MediaPublisher publisher;
    private final DcamMediaPublisher cleanPublisher;
    private final Md5SidecarWriter md5SidecarWriter;

    public DcamMediaFinalizer(DcamStorage storage) {
        this(storage, new DcamMediaPublisher(), DcamMd5Sidecar::write);
    }

    DcamMediaFinalizer(DcamStorage storage, DcamMediaPublisher publisher) {
        this(storage, publisher, DcamMd5Sidecar::write);
    }

    DcamMediaFinalizer(
            DcamStorage storage, DcamMediaPublisher publisher, Md5SidecarWriter md5SidecarWriter) {
        this(storage, publisher::publish, publisher, md5SidecarWriter);
    }

    DcamMediaFinalizer(
            DcamStorage storage, MediaPublisher publisher, Md5SidecarWriter md5SidecarWriter) {
        this(storage, publisher, new DcamMediaPublisher(), md5SidecarWriter);
    }

    private DcamMediaFinalizer(
            DcamStorage storage,
            MediaPublisher publisher,
            DcamMediaPublisher cleanPublisher,
            Md5SidecarWriter md5SidecarWriter) {
        this.storage = storage;
        this.publisher = publisher;
        this.cleanPublisher = cleanPublisher;
        this.md5SidecarWriter = md5SidecarWriter;
    }

    public File finalizeMedia(DcamMediaFile mediaFile) throws IOException {
        return finalizeMedia(mediaFile, false);
    }

    public File finalizeCleanMedia(DcamMediaFile mediaFile) throws IOException {
        File staging = mediaFile.getFile();
        DcamMediaPublisher.validate(staging, "staging");
        File target = storage.finalFile(mediaFile);
        return cleanPublisher.publishClean(staging, target);
    }

    void cleanupCleanMedia(DcamMediaFile mediaFile) {
        storage.deleteEmptyStagingDateDirectory(mediaFile);
    }

    public File finalizeMedia(DcamMediaFile mediaFile, boolean createMd5) throws IOException {
        File staging = mediaFile.getFile();
        DcamMediaPublisher.validate(staging, "staging");
        File target = storage.finalFile(mediaFile);
        try {
            boolean writeMd5 = createMd5 && mediaFile.getType().isVideo();
            DcamMediaPublisher.Publication publication = publisher.publish(staging, target, writeMd5);
            File published = publication.file;
            if (writeMd5) {
                md5SidecarWriter.write(published, publication.md5);
            }
            deleteStagingDuplicate(staging);
            storage.deleteEmptyStagingDateDirectory(mediaFile);
            return published;
        } catch (IOException | RuntimeException failure) {
            throw asIOException("Media publication failed", failure);
        }
    }

    @FunctionalInterface
    interface MediaPublisher {
        DcamMediaPublisher.Publication publish(File staging, File target, boolean createMd5)
                throws IOException;
    }

    @FunctionalInterface
    interface Md5SidecarWriter {
        void write(File file, String md5) throws IOException;
    }

    private static IOException asIOException(String prefix, Throwable failure) {
        return new IOException(prefix + ": " + message(failure), failure);
    }

    private static void deleteStagingDuplicate(File staging) {
        try {
            java.nio.file.Files.deleteIfExists(staging.toPath());
        } catch (IOException ignored) {
            // A leftover duplicate is harmless; do not roll back a valid published media file.
        }
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
