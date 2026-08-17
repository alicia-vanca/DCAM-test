package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SegmentedAesGcmJpegOutputTest {
    private static final String PASSWORD = "photo-pass";

    @Test void writesExactPayloadWithoutPlaintextOrSiblingFile(@TempDir Path root)
            throws Exception {
        File encrypted = root.resolve("DCAM_0_user_20260812_100000_enc.jpg").toFile();
        byte[] jpeg = pattern(1024 * 1024 + 137, 0x31);
        jpeg[0] = (byte) 0xff;
        jpeg[1] = (byte) 0xd8;
        jpeg[jpeg.length - 2] = (byte) 0xff;
        jpeg[jpeg.length - 1] = (byte) 0xd9;

        new SegmentedAesGcmJpegOutput(encrypted, PASSWORD).write(jpeg);

        assertTrue(SegmentedAesGcmMediaStore.hasFamilyMagic(encrypted));
        assertTrue(encrypted.length() < jpeg.length * 3L / 2L);
        DcamSegmentedGcmFormat.Header header = DcamSegmentedGcmFormat.readHeader(
                Arrays.copyOf(Files.readAllBytes(encrypted.toPath()),
                        DcamSegmentedGcmFormat.FILE_HEADER_BYTES));
        assertEquals(1_000, header.kdfIterations);
        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(encrypted, PASSWORD)) {
            assertArrayEquals(jpeg, readAll(decrypted));
        }
        try (var files = Files.list(root)) {
            assertEquals(1L, files.count());
        }
    }

    @Test void discardRemovesCiphertextAndAllowsRetry(@TempDir Path root) throws Exception {
        File encrypted = root.resolve("DCAM_0_user_20260812_100001_enc.jpg").toFile();
        SegmentedAesGcmJpegOutput output =
                new SegmentedAesGcmJpegOutput(encrypted, PASSWORD);
        byte[] first = pattern(80_000, 0x41);
        byte[] second = pattern(90_000, 0x51);

        output.write(first);
        output.discard();
        assertFalse(encrypted.exists());
        output.write(second);

        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(encrypted, PASSWORD)) {
            assertArrayEquals(second, readAll(decrypted));
        }
    }

    private static byte[] readAll(DcamRandomAccessMedia media) throws Exception {
        media.position(0L);
        ByteBuffer target = ByteBuffer.allocate(Math.toIntExact(media.size()));
        while (target.hasRemaining()) {
            int read = media.read(target);
            if (read < 0) break;
        }
        return target.array();
    }

    private static byte[] pattern(int bytes, int seed) {
        byte[] data = new byte[bytes];
        for (int index = 0; index < bytes; index++) {
            data[index] = (byte) (seed + index * 31);
        }
        return data;
    }
}