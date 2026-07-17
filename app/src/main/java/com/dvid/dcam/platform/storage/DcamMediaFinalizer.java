package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.IOException;

/** Filesystem readiness boundary: validate staging, publish safely, then remove staging. */
public final class DcamMediaFinalizer {
    private final DcamStorage storage;
    private final DcamMediaPublisher publisher;

    public DcamMediaFinalizer(DcamStorage storage) {
        this(storage, new DcamMediaPublisher());
    }

    DcamMediaFinalizer(DcamStorage storage, DcamMediaPublisher publisher) {
        this.storage = storage;
        this.publisher = publisher;
    }

    public File finalizeMedia(DcamMediaFile mediaFile) throws IOException {
        return finalizeMedia(mediaFile, false);
    }

    public File finalizeMedia(DcamMediaFile mediaFile, boolean createMd5) throws IOException {
        File staging = mediaFile.getFile();
        DcamMediaPublisher.validate(staging, "staging");
        File target = storage.finalFile(mediaFile);
        try {
            boolean writeMd5 = createMd5 && "mp4".equals(mediaFile.getType().getExtension());
            DcamMediaPublisher.Publication publication = publisher.publish(staging, target, writeMd5);
            File published = publication.file;
            if (writeMd5) {
                try {
                    DcamMd5Sidecar.write(published, publication.md5);
                } catch (IOException | RuntimeException failure) {
                    try { java.nio.file.Files.deleteIfExists(published.toPath()); } catch (IOException cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                    throw failure;
                }
            }
            // A leftover staging duplicate is safe; never roll back a valid final file for cleanup failure.
            try { java.nio.file.Files.deleteIfExists(staging.toPath()); } catch (IOException ignored) { }
            storage.deleteEmptyStagingDateDirectory(mediaFile);
            storage.deleteTargetMarker(mediaFile);
            return published;
        } catch (IOException | RuntimeException failure) {
            throw asIOException("Media publication failed", failure);
        }
    }

    private static IOException asIOException(String prefix, Throwable failure) {
        return new IOException(prefix + ": " + message(failure), failure);
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
