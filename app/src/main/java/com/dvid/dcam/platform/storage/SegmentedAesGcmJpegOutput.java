package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Objects;

public final class SegmentedAesGcmJpegOutput {
    private final File file;
    private final String password;
    private boolean written;

    SegmentedAesGcmJpegOutput(File file, String password) {
        this.file = Objects.requireNonNull(file, "file");
        this.password = Objects.requireNonNull(password, "password");
    }

    public File file() {
        return file;
    }

    public synchronized void write(byte[] jpeg) throws IOException {
        Objects.requireNonNull(jpeg, "jpeg");
        if (jpeg.length == 0) throw new IOException("JPEG payload is empty.");
        if (written) throw new IOException("Segmented AES-GCM JPEG was already written.");
        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(file, password)) {
            output.beginTransaction();
            writeFully(output, ByteBuffer.wrap(jpeg));
            output.commitTransaction();
            output.checkpoint();
            output.finish();
        } catch (IOException | RuntimeException failure) {
            try { Files.deleteIfExists(file.toPath()); } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        written = true;
    }

    public synchronized void discard() {
        written = false;
        try { Files.deleteIfExists(file.toPath()); } catch (IOException ignored) { }
    }

    private static void writeFully(DcamRecordingOutput output, ByteBuffer source)
            throws IOException {
        while (source.hasRemaining()) {
            if (output.write(source) <= 0) {
                throw new IOException("Segmented AES-GCM JPEG output stopped accepting data.");
            }
        }
    }
}