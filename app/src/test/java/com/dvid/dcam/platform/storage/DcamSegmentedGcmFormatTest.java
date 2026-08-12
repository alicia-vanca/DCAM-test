package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DcamSegmentedGcmFormatTest {
    private static final String PASSWORD = "contract-pass";
    private static final String ENCRYPTED_SHA256 =
            "9d53238c68e15fa1ba326edfbcfde43f14eec15febe6f04b7b112596c3d12883";
    private static final String PLAINTEXT_SHA256 =
            "ff6a7b63623fba846c3e2079c4e004b3c03dbbd382e67a4b1ce8a2f7181d7d5e";
    private static final String PBKDF2_KEY_HEX =
            "1925c3ce56e5eb7a668e82ec0b625ff348147827a4cf34c76a98b172484fa7e5";

    @Test void regeneratesSharedContractVector() throws Exception {
        byte[] encrypted = resource("dcam-segmented-gcm-contract.bin");
        byte[] plaintext = resource("dcam-segmented-gcm-contract.plain");

        assertArrayEquals(encrypted, buildVector());
        assertArrayEquals(plaintext, buildPlaintext());
        assertEquals(ENCRYPTED_SHA256, sha256(encrypted));
        assertEquals(PLAINTEXT_SHA256, sha256(plaintext));
    }

    @Test void decodesAutocommitTransactionCheckpointAndFinal() throws Exception {
        byte[] encrypted = resource("dcam-segmented-gcm-contract.bin");
        byte[] expected = resource("dcam-segmented-gcm-contract.plain");
        DcamSegmentedGcmFormat.Header header = DcamSegmentedGcmFormat.readHeader(
                Arrays.copyOf(encrypted, DcamSegmentedGcmFormat.FILE_HEADER_BYTES));
        byte[] key = DcamSegmentedGcmFormat.deriveKey(header, PASSWORD);
        assertEquals(PBKDF2_KEY_HEX, HexFormat.of().formatHex(key));
        Map<Long, byte[]> blocks = new HashMap<>();
        List<DcamSegmentedGcmFormat.DecodedRecord> pending = new ArrayList<>();
        long logicalLength = 0L;
        long expectedSequence = 1L;
        boolean checkpointSeen = false;
        boolean finalSeen = false;
        int offset = DcamSegmentedGcmFormat.FILE_HEADER_BYTES;
        try {
            while (offset < encrypted.length) {
                byte[] recordHeader = Arrays.copyOfRange(encrypted, offset,
                        offset + DcamSegmentedGcmFormat.RECORD_HEADER_BYTES);
                DcamSegmentedGcmFormat.RecordMetadata metadata =
                        DcamSegmentedGcmFormat.readRecordHeader(header, recordHeader);
                byte[] recordBytes = Arrays.copyOfRange(
                        encrypted, offset, offset + metadata.totalBytes);
                DcamSegmentedGcmFormat.DecodedRecord record =
                        DcamSegmentedGcmFormat.decodeRecord(header, key, recordBytes);
                assertEquals(expectedSequence++, metadata.sequence);
                switch (metadata.type) {
                    case DcamSegmentedGcmFormat.TYPE_DATA -> {
                        if (metadata.transactionId == 0L) {
                            blocks.put(metadata.blockIndex, record.plaintext);
                            logicalLength = metadata.logicalLength;
                        } else {
                            pending.add(record);
                        }
                    }
                    case DcamSegmentedGcmFormat.TYPE_COMMIT -> {
                        assertEquals(metadata.relatedCount, pending.size());
                        for (DcamSegmentedGcmFormat.DecodedRecord pendingRecord : pending) {
                            blocks.put(pendingRecord.metadata.blockIndex,
                                    pendingRecord.plaintext);
                        }
                        pending.clear();
                        logicalLength = metadata.logicalLength;
                    }
                    case DcamSegmentedGcmFormat.TYPE_CHECKPOINT -> {
                        assertEquals(logicalLength, metadata.logicalLength);
                        checkpointSeen = true;
                    }
                    case DcamSegmentedGcmFormat.TYPE_FINAL -> {
                        assertEquals(logicalLength, metadata.logicalLength);
                        finalSeen = true;
                    }
                    default -> throw new AssertionError("Unexpected record type");
                }
                offset += metadata.totalBytes;
            }
        } finally {
            Arrays.fill(key, (byte) 0);
        }

        assertTrue(checkpointSeen);
        assertTrue(finalSeen);
        assertTrue(pending.isEmpty());
        assertEquals(encrypted.length, offset);
        assertArrayEquals(expected, logicalBytes(
                blocks, logicalLength, header.blockBytes));
    }

    @Test void preservesFragmentedAvSeekLayoutByteForByte() throws Exception {
        byte[] expected = DcamInterruptedMp4FinalizerTest.fragmentedAvWithSeekReserve();
        DcamSegmentedGcmFormat.Header header = DcamSegmentedGcmFormat.createHeader(
                4096, DcamSegmentedGcmFormat.MIN_KDF_ITERATIONS,
                sequence(0x91, DcamSegmentedGcmFormat.SALT_BYTES),
                sequence(0xa1, DcamSegmentedGcmFormat.FILE_ID_BYTES));
        byte[] key = DcamSegmentedGcmFormat.deriveKey(header, PASSWORD);
        ByteArrayOutputStream decrypted = new ByteArrayOutputStream(expected.length);
        long sequence = 1L;
        try {
            for (int offset = 0; offset < expected.length; sequence++) {
                int plaintextBytes = Math.min(header.blockBytes, expected.length - offset);
                byte[] encoded = record(header, key, DcamSegmentedGcmFormat.TYPE_DATA,
                        sequence, 0L, offset / header.blockBytes, plaintextBytes, 0,
                        offset + plaintextBytes, indexedNonce(sequence),
                        Arrays.copyOfRange(expected, offset, offset + plaintextBytes));
                DcamSegmentedGcmFormat.DecodedRecord decoded =
                        DcamSegmentedGcmFormat.decodeRecord(header, key, encoded);
                decrypted.write(decoded.plaintext);
                offset += plaintextBytes;
            }
            DcamSegmentedGcmFormat.decodeRecord(header, key,
                    record(header, key, DcamSegmentedGcmFormat.TYPE_CHECKPOINT,
                            sequence, 0L, 0L, 0, 0, expected.length,
                            indexedNonce(sequence), new byte[0]));
            sequence++;
            DcamSegmentedGcmFormat.decodeRecord(header, key,
                    record(header, key, DcamSegmentedGcmFormat.TYPE_FINAL,
                            sequence, 0L, 0L, 0, 0, expected.length,
                            indexedNonce(sequence), new byte[0]));
        } finally {
            Arrays.fill(key, (byte) 0);
        }

        assertArrayEquals(expected, decrypted.toByteArray());
    }

    @Test void rejectsTamperedTagWithoutPlaintextFallback() throws Exception {
        byte[] encrypted = resource("dcam-segmented-gcm-contract.bin");
        DcamSegmentedGcmFormat.Header header = DcamSegmentedGcmFormat.readHeader(
                Arrays.copyOf(encrypted, DcamSegmentedGcmFormat.FILE_HEADER_BYTES));
        byte[] key = DcamSegmentedGcmFormat.deriveKey(header, PASSWORD);
        int firstRecord = DcamSegmentedGcmFormat.FILE_HEADER_BYTES;
        byte[] recordHeader = Arrays.copyOfRange(encrypted, firstRecord,
                firstRecord + DcamSegmentedGcmFormat.RECORD_HEADER_BYTES);
        DcamSegmentedGcmFormat.RecordMetadata metadata =
                DcamSegmentedGcmFormat.readRecordHeader(header, recordHeader);
        byte[] record = Arrays.copyOfRange(
                encrypted, firstRecord, firstRecord + metadata.totalBytes);
        record[DcamSegmentedGcmFormat.RECORD_HEADER_BYTES] ^= 1;
        try {
            assertThrows(IOException.class,
                    () -> DcamSegmentedGcmFormat.decodeRecord(header, key, record));
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] buildPlaintext() {
        byte[] plaintext = patternedPlaintext();
        for (int index = 100; index < 132; index++) plaintext[index] ^= (byte) 0xa5;
        return Arrays.copyOf(plaintext, 4200);
    }

    private static byte[] patternedPlaintext() {
        byte[] plaintext = new byte[4300];
        for (int index = 0; index < plaintext.length; index++) {
            plaintext[index] = (byte) (index * 31 + 7);
        }
        return plaintext;
    }

    private static byte[] buildVector() throws Exception {
        DcamSegmentedGcmFormat.Header header = DcamSegmentedGcmFormat.createHeader(
                4096, DcamSegmentedGcmFormat.MIN_KDF_ITERATIONS,
                sequence(0x01, DcamSegmentedGcmFormat.SALT_BYTES),
                sequence(0x11, DcamSegmentedGcmFormat.FILE_ID_BYTES));
        byte[] key = DcamSegmentedGcmFormat.deriveKey(header, PASSWORD);
        byte[] initial = patternedPlaintext();
        byte[] expected = buildPlaintext();

        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        encrypted.write(header.encoded);
        try {
            encrypted.write(record(header, key, DcamSegmentedGcmFormat.TYPE_DATA,
                    1, 0, 0, 4096, 0, 4096, nonce(0x21),
                    Arrays.copyOfRange(initial, 0, 4096)));
            encrypted.write(record(header, key, DcamSegmentedGcmFormat.TYPE_DATA,
                    2, 0, 1, 204, 0, 4300, nonce(0x31),
                    Arrays.copyOfRange(initial, 4096, 4300)));
            encrypted.write(record(header, key, DcamSegmentedGcmFormat.TYPE_CHECKPOINT,
                    3, 0, 0, 0, 0, 4300, nonce(0x41), new byte[0]));
            encrypted.write(record(header, key, DcamSegmentedGcmFormat.TYPE_DATA,
                    4, 4, 0, 4096, 0, 0, nonce(0x51),
                    Arrays.copyOfRange(expected, 0, 4096)));
            encrypted.write(record(header, key, DcamSegmentedGcmFormat.TYPE_COMMIT,
                    5, 4, 0, 0, 1, 4300, nonce(0x61), new byte[0]));
            encrypted.write(record(header, key, DcamSegmentedGcmFormat.TYPE_COMMIT,
                    6, 6, 0, 0, 0, 4200, nonce(0x71), new byte[0]));
            encrypted.write(record(header, key, DcamSegmentedGcmFormat.TYPE_FINAL,
                    7, 0, 0, 0, 0, 4200, nonce(0x81), new byte[0]));
            return encrypted.toByteArray();
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] record(DcamSegmentedGcmFormat.Header header, byte[] key,
            int type, long sequence, long transactionId, long blockIndex,
            int plaintextBytes, int relatedCount, long logicalLength,
            byte[] nonce, byte[] plaintext) throws Exception {
        return DcamSegmentedGcmFormat.encodeRecord(header, key,
                new DcamSegmentedGcmFormat.RecordMetadata(type, sequence, transactionId,
                        blockIndex, plaintextBytes, relatedCount, logicalLength, nonce),
                plaintext);
    }

    private static byte[] logicalBytes(
            Map<Long, byte[]> blocks, long logicalLength, int blockBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(logicalLength));
        long blockCount = (logicalLength + blockBytes - 1L) / blockBytes;
        for (long blockIndex = 0L; blockIndex < blockCount; blockIndex++) {
            byte[] block = blocks.get(blockIndex);
            if (block == null) throw new IOException("Missing logical block " + blockIndex);
            int expectedBytes = blockIndex == blockCount - 1L
                    ? (int) (logicalLength - blockIndex * blockBytes) : blockBytes;
            output.write(block, 0, expectedBytes);
        }
        return output.toByteArray();
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = DcamSegmentedGcmFormatTest.class
                .getResourceAsStream("/crypto/" + name)) {
            if (input == null) throw new IOException("Missing contract resource " + name);
            return input.readAllBytes();
        }
    }

    private static byte[] sequence(int first, int length) {
        byte[] bytes = new byte[length];
        for (int index = 0; index < length; index++) bytes[index] = (byte) (first + index);
        return bytes;
    }

    private static byte[] indexedNonce(long sequence) {
        return ByteBuffer.allocate(DcamSegmentedGcmFormat.NONCE_BYTES)
                .putInt(0x4156534b).putLong(sequence).array();
    }

    private static byte[] nonce(int first) {
        return sequence(first, DcamSegmentedGcmFormat.NONCE_BYTES);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}