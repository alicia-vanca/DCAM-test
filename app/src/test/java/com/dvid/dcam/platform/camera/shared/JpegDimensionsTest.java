package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class JpegDimensionsTest {
    @Test void readsDimensionsFromStartOfFrameMarker() throws IOException {
        File file = file("jpeg-dimensions-valid.jpg");
        Files.write(file.toPath(), jpeg(1280, 720));

        assertEquals(new JpegDimensions.Size(1280, 720),
                JpegDimensions.read(file).orElseThrow());
    }

    @Test void invalidOrMissingFileHasNoDimensions() throws IOException {
        File invalid = file("jpeg-dimensions-invalid.jpg");
        Files.write(invalid.toPath(), new byte[] {0x00, 0x01, 0x02});

        assertTrue(JpegDimensions.read(invalid).isEmpty());
        assertTrue(JpegDimensions.read(new File(invalid.getParentFile(), "missing.jpg")).isEmpty());
    }

    @Test void rejectsTruncatedStartOfFrameSegment() throws IOException {
        File invalid = file("jpeg-dimensions-short-sof.jpg");
        Files.write(invalid.toPath(), new byte[] {
                (byte) 0xff, (byte) 0xd8,
                (byte) 0xff, (byte) 0xc0, 0x00, 0x02,
                0x08, 0x02, (byte) 0x80, 0x05, 0x00
        });

        assertTrue(JpegDimensions.read(invalid).isEmpty());
    }
    static byte[] jpeg(int width, int height) {
        return new byte[] {
                (byte) 0xff, (byte) 0xd8,
                (byte) 0xff, (byte) 0xe0, 0x00, 0x04, 0x01, 0x02,
                (byte) 0xff, (byte) 0xc0, 0x00, 0x11, 0x08,
                (byte) (height >>> 8), (byte) height,
                (byte) (width >>> 8), (byte) width
        };
    }

    private static File file(String name) throws IOException {
        File directory = new File("build/tmp/jpeg-dimensions-test");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("failed to create test directory");
        }
        File file = new File(directory, name);
        Files.deleteIfExists(file.toPath());
        return file;
    }
}