package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SegmentedAesGcmMediaStoreTest {
    private static final String PASSWORD = "recording-pass";

    @Test void roundTripsSequentialRewriteTruncateAndFinal(@TempDir Path root) throws Exception {
        File encrypted = root.resolve("recording_enc.mp4").toFile();
        byte[] initial = pattern(180_000, 0x21);
        byte[] replacement = pattern(1_500, 0x71);
        byte[] expected = Arrays.copyOf(initial, 150_000);
        System.arraycopy(replacement, 0, expected, 12_345, replacement.length);

        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(encrypted, PASSWORD)) {
            writeFully(output, ByteBuffer.wrap(initial, 0, 90_000));
            output.checkpoint();
            writeFully(output, ByteBuffer.wrap(initial, 90_000, initial.length - 90_000));
            output.beginTransaction();
            output.position(12_345L);
            writeFully(output, ByteBuffer.wrap(replacement));
            output.truncate(expected.length);
            output.commitTransaction();
            output.finish();
        }

        assertTrue(SegmentedAesGcmMediaStore.hasFamilyMagic(encrypted));
        try (SegmentedAesGcmMediaStore input =
                     SegmentedAesGcmMediaStore.openPublished(encrypted, PASSWORD)) {
            assertTrue(input.isFinalized());
            assertArrayEquals(expected, readAll(input));
        }
    }

    @Test void fragmentCheckpointsStayWellBelowDoubleStorage(@TempDir Path root)
            throws Exception {
        File encrypted = root.resolve("long_enc.mp4").toFile();
        byte[] plaintext = pattern(4 * 1024 * 1024, 0x31);
        int fragmentBytes = 112 * 1024;

        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(encrypted, PASSWORD)) {
            int offset = 0;
            while (offset < plaintext.length) {
                int bytes = Math.min(fragmentBytes, plaintext.length - offset);
                writeFully(output, ByteBuffer.wrap(plaintext, offset, bytes));
                output.checkpoint();
                offset += bytes;
            }
            output.finish();
        }

        assertTrue(encrypted.length() < plaintext.length * 3L / 2L);
        try (SegmentedAesGcmMediaStore input =
                     SegmentedAesGcmMediaStore.openPublished(encrypted, PASSWORD)) {
            assertArrayEquals(plaintext, readAll(input));
        }
        try (var files = Files.list(root)) {
            assertEquals(1L, files.count());
        }
    }

    @Test void tornCommitRestoresPriorAuthenticatedState(@TempDir Path root) throws Exception {
        File encrypted = root.resolve("commit_enc.mp4").toFile();
        byte[] original = pattern(100_000, 0x41);
        long transactionStart;

        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(encrypted, PASSWORD)) {
            writeFully(output, ByteBuffer.wrap(original));
            output.checkpoint();
            transactionStart = encrypted.length();
            output.beginTransaction();
            output.position(100L);
            writeFully(output, ByteBuffer.wrap(pattern(500, 0x7a)));
            output.commitTransaction();
        }
        try (RandomAccessFile physical = new RandomAccessFile(encrypted, "rw")) {
            physical.setLength(physical.length() - 8L);
        }

        try (SegmentedAesGcmMediaStore recovered =
                     SegmentedAesGcmMediaStore.openForRecovery(encrypted, PASSWORD)) {
            assertEquals(transactionStart, encrypted.length());
            assertArrayEquals(original, readAll(recovered));
            recovered.finish();
        }
    }

    @Test void tornTailDataDropsOnlyUncommittedTail(@TempDir Path root) throws Exception {
        File encrypted = root.resolve("tail_enc.mp4").toFile();
        byte[] committed = pattern(80_000, 0x51);
        long checkpointBytes;

        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(encrypted, PASSWORD)) {
            writeFully(output, ByteBuffer.wrap(committed));
            output.checkpoint();
            checkpointBytes = encrypted.length();
            writeFully(output, ByteBuffer.wrap(pattern(20_000, 0x61)));
        }
        try (RandomAccessFile physical = new RandomAccessFile(encrypted, "rw")) {
            physical.setLength(physical.length() - 10L);
        }

        try (SegmentedAesGcmMediaStore recovered =
                     SegmentedAesGcmMediaStore.openForRecovery(encrypted, PASSWORD)) {
            assertEquals(checkpointBytes, encrypted.length());
            assertArrayEquals(committed, readAll(recovered));
        }
    }

    @Test void autocommitCannotRewriteCommittedTailPrefix(@TempDir Path root)
            throws Exception {
        File encrypted = root.resolve("prefix_enc.mp4").toFile();
        byte[] original = pattern(100, 0x31);
        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(encrypted, PASSWORD)) {
            writeFully(output, ByteBuffer.wrap(original));
            output.checkpoint();
        }

        byte[] key = null;
        try (RandomAccessFile physical = new RandomAccessFile(encrypted, "rw")) {
            byte[] encodedHeader = new byte[DcamSegmentedGcmFormat.FILE_HEADER_BYTES];
            physical.readFully(encodedHeader);
            DcamSegmentedGcmFormat.Header header =
                    DcamSegmentedGcmFormat.readHeader(encodedHeader);
            key = DcamSegmentedGcmFormat.deriveKey(header, PASSWORD);
            byte[] mutated = original.clone();
            mutated[0] ^= 0x55;
            byte[] dataNonce = new byte[DcamSegmentedGcmFormat.NONCE_BYTES];
            byte[] finalNonce = new byte[DcamSegmentedGcmFormat.NONCE_BYTES];
            dataNonce[0] = 1;
            finalNonce[0] = 2;
            physical.seek(physical.length());
            physical.write(DcamSegmentedGcmFormat.encodeRecord(header, key,
                    new DcamSegmentedGcmFormat.RecordMetadata(
                            DcamSegmentedGcmFormat.TYPE_DATA, 3L, 0L, 0L,
                            mutated.length, 0, mutated.length, dataNonce), mutated));
            physical.write(DcamSegmentedGcmFormat.encodeRecord(header, key,
                    new DcamSegmentedGcmFormat.RecordMetadata(
                            DcamSegmentedGcmFormat.TYPE_FINAL, 4L, 0L, 0L,
                            0, 0, mutated.length, finalNonce), new byte[0]));
        } finally {
            if (key != null) Arrays.fill(key, (byte) 0);
        }

        assertThrows(IOException.class,
                () -> SegmentedAesGcmMediaStore.openPublished(encrypted, PASSWORD));
        try (SegmentedAesGcmMediaStore recovered =
                     SegmentedAesGcmMediaStore.openForRecovery(encrypted, PASSWORD)) {
            assertArrayEquals(original, readAll(recovered));
        }
    }

    @Test void wrongPasswordNeverTruncatesStaging(@TempDir Path root) throws Exception {
        File encrypted = root.resolve("password_enc.mp4").toFile();
        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(encrypted, PASSWORD)) {
            writeFully(output, ByteBuffer.wrap(pattern(70_000, 0x11)));
            output.checkpoint();
        }
        long bytes = encrypted.length();

        assertThrows(Exception.class,
                () -> SegmentedAesGcmMediaStore.openForRecovery(encrypted, "wrong-pass"));
        assertEquals(bytes, encrypted.length());
    }

    private static byte[] readAll(DcamRandomAccessMedia media) throws Exception {
        media.position(0L);
        ByteBuffer bytes = ByteBuffer.allocate(Math.toIntExact(media.size()));
        while (bytes.hasRemaining()) {
            int read = media.read(bytes);
            if (read < 0) break;
        }
        return bytes.array();
    }

    private static void writeFully(DcamRecordingOutput output, ByteBuffer source)
            throws Exception {
        while (source.hasRemaining()) {
            assertTrue(output.write(source) > 0);
        }
    }

    private static byte[] pattern(int bytes, int seed) {
        byte[] data = new byte[bytes];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) (seed + index * 31);
        }
        return data;
    }
}