package com.dvid.dcam.platform.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class DcamSegmentedGcmFormat {
    static final int FILE_HEADER_BYTES = 96;
    static final int RECORD_HEADER_BYTES = 96;
    static final int TAG_BYTES = 16;
    static final int NONCE_BYTES = 12;
    static final int SALT_BYTES = 16;
    static final int FILE_ID_BYTES = 16;
    static final int AES_KEY_BYTES = 32;
    static final int MIN_BLOCK_BYTES = 4 * 1024;
    static final int MAX_BLOCK_BYTES = 1024 * 1024;
    static final int DEFAULT_BLOCK_BYTES = 256 * 1024;
    static final int MIN_KDF_ITERATIONS = 1_000;
    static final int MAX_KDF_ITERATIONS = 2_000_000;
    static final int DEFAULT_KDF_ITERATIONS = 1_000;
    static final int TYPE_DATA = 1;
    static final int TYPE_COMMIT = 2;
    static final int TYPE_CHECKPOINT = 3;
    static final int TYPE_FINAL = 4;

    private static final int FORMAT_REVISION = 1;
    private static final int KDF_ID_PBKDF2_HMAC_SHA256 = 1;
    private static final int CIPHER_ID_AES_256_GCM = 1;
    private static final int FILE_HEADER_CRC_OFFSET = 92;
    private static final int RECORD_HEADER_CRC_OFFSET = 92;
    private static final int RECORD_FIXED_BYTES = RECORD_HEADER_BYTES + TAG_BYTES;
    private static final int GCM_TAG_BITS = TAG_BYTES * Byte.SIZE;
    private static final byte[] FILE_MAGIC =
            "DCAM-GCM-MEDIA\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RECORD_MAGIC =
            "DCRD".getBytes(StandardCharsets.US_ASCII);

    private DcamSegmentedGcmFormat() {}

    static Header createHeader(
            int blockBytes, int kdfIterations, byte[] salt, byte[] fileId) throws IOException {
        validateBlockBytes(blockBytes);
        validateKdfIterations(kdfIterations);
        byte[] safeSalt = requireBytes(salt, SALT_BYTES, "KDF salt");
        byte[] safeFileId = requireBytes(fileId, FILE_ID_BYTES, "file ID");
        if (isAllZero(safeSalt) || isAllZero(safeFileId)) {
            throw new IOException("Segmented-GCM salt and file ID must not be all zero.");
        }

        byte[] encoded = new byte[FILE_HEADER_BYTES];
        ByteBuffer header = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        header.put(FILE_MAGIC);
        header.putShort((short) FORMAT_REVISION);
        header.putShort((short) FILE_HEADER_BYTES);
        header.putInt(0);
        header.putInt(blockBytes);
        header.putInt(kdfIterations);
        header.putShort((short) KDF_ID_PBKDF2_HMAC_SHA256);
        header.putShort((short) CIPHER_ID_AES_256_GCM);
        header.putShort((short) NONCE_BYTES);
        header.putShort((short) TAG_BYTES);
        header.put(safeSalt);
        header.put(safeFileId);
        header.put(FILE_MAGIC);
        header.putInt(0);
        header.putInt(crc32(encoded, 0, FILE_HEADER_CRC_OFFSET));
        return new Header(blockBytes, kdfIterations, safeSalt, safeFileId, encoded);
    }

    static Header readHeader(byte[] encoded) throws IOException {
        if (encoded == null || encoded.length != FILE_HEADER_BYTES) {
            throw new IOException("Segmented-GCM file header must be 96 bytes.");
        }
        if (!matches(encoded, 0, FILE_MAGIC) || !matches(encoded, 72, FILE_MAGIC)) {
            throw new IOException("Segmented-GCM file magic is invalid.");
        }
        if (Integer.toUnsignedLong(readInt(encoded, FILE_HEADER_CRC_OFFSET))
                != Integer.toUnsignedLong(crc32(encoded, 0, FILE_HEADER_CRC_OFFSET))) {
            throw new IOException("Segmented-GCM file header CRC is invalid.");
        }

        ByteBuffer header = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        header.position(FILE_MAGIC.length);
        int revision = Short.toUnsignedInt(header.getShort());
        int headerBytes = Short.toUnsignedInt(header.getShort());
        long flags = Integer.toUnsignedLong(header.getInt());
        int blockBytes = header.getInt();
        int kdfIterations = header.getInt();
        int kdfId = Short.toUnsignedInt(header.getShort());
        int cipherId = Short.toUnsignedInt(header.getShort());
        int nonceBytes = Short.toUnsignedInt(header.getShort());
        int tagBytes = Short.toUnsignedInt(header.getShort());
        byte[] salt = new byte[SALT_BYTES];
        byte[] fileId = new byte[FILE_ID_BYTES];
        header.get(salt);
        header.get(fileId);

        if (revision != FORMAT_REVISION || headerBytes != FILE_HEADER_BYTES || flags != 0L
                || kdfId != KDF_ID_PBKDF2_HMAC_SHA256
                || cipherId != CIPHER_ID_AES_256_GCM
                || nonceBytes != NONCE_BYTES || tagBytes != TAG_BYTES
                || !isZero(encoded, 88, 4)) {
            throw new IOException("Segmented-GCM file header fields are unsupported.");
        }
        validateBlockBytes(blockBytes);
        validateKdfIterations(kdfIterations);
        if (isAllZero(salt) || isAllZero(fileId)) {
            throw new IOException("Segmented-GCM salt or file ID is invalid.");
        }
        return new Header(blockBytes, kdfIterations, salt, fileId, encoded.clone());
    }

    static boolean hasFamilyMagic(byte[] bytes) {
        if (bytes == null) return false;
        return bytes.length >= FILE_MAGIC.length && matches(bytes, 0, FILE_MAGIC)
                || bytes.length >= 72 + FILE_MAGIC.length && matches(bytes, 72, FILE_MAGIC);
    }

    static byte[] deriveKey(Header header, String password) throws IOException {
        if (header == null) throw new IOException("Segmented-GCM header is required.");
        if (password == null || password.isBlank()) {
            throw new IOException("Media encryption password is required.");
        }
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        try {
            return pbkdf2HmacSha256(passwordBytes, header.salt, header.kdfIterations);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    static byte[] encodeRecord(
            Header header, byte[] key, RecordMetadata metadata, byte[] plaintext) throws IOException {
        validateKey(key);
        byte[] safePlaintext = plaintext == null ? new byte[0] : plaintext;
        byte[] recordHeader = encodeRecordHeader(header, metadata, safePlaintext.length);
        byte[] sealed = crypt(Cipher.ENCRYPT_MODE, key, metadata.nonce,
                header.encoded, recordHeader, safePlaintext);
        if (sealed.length != safePlaintext.length + TAG_BYTES) {
            Arrays.fill(sealed, (byte) 0);
            throw new IOException("Segmented-GCM provider returned an invalid record size.");
        }

        byte[] encoded = new byte[recordHeader.length + sealed.length];
        System.arraycopy(recordHeader, 0, encoded, 0, recordHeader.length);
        System.arraycopy(sealed, safePlaintext.length, encoded, recordHeader.length, TAG_BYTES);
        System.arraycopy(sealed, 0, encoded, RECORD_FIXED_BYTES, safePlaintext.length);
        Arrays.fill(sealed, (byte) 0);
        return encoded;
    }

    static DecodedRecord decodeRecord(Header header, byte[] key, byte[] encoded) throws IOException {
        validateKey(key);
        if (encoded == null || encoded.length < RECORD_FIXED_BYTES) {
            throw new IOException("Segmented-GCM record is truncated.");
        }
        byte[] recordHeader = Arrays.copyOf(encoded, RECORD_HEADER_BYTES);
        RecordMetadata metadata = readRecordHeader(header, recordHeader);
        if (encoded.length != metadata.totalBytes) {
            throw new IOException("Segmented-GCM record length does not match its header.");
        }

        byte[] sealed = new byte[metadata.plaintextBytes + TAG_BYTES];
        System.arraycopy(encoded, RECORD_FIXED_BYTES, sealed, 0, metadata.plaintextBytes);
        System.arraycopy(encoded, RECORD_HEADER_BYTES, sealed, metadata.plaintextBytes, TAG_BYTES);
        try {
            byte[] plaintext = crypt(Cipher.DECRYPT_MODE, key, metadata.nonce,
                    header.encoded, recordHeader, sealed);
            if (plaintext.length != metadata.plaintextBytes) {
                Arrays.fill(plaintext, (byte) 0);
                throw new IOException("Segmented-GCM plaintext length is invalid.");
            }
            return new DecodedRecord(metadata, plaintext);
        } finally {
            Arrays.fill(sealed, (byte) 0);
        }
    }

    static RecordMetadata readRecordHeader(Header header, byte[] encoded) throws IOException {
        if (header == null) throw new IOException("Segmented-GCM header is required.");
        if (encoded == null || encoded.length != RECORD_HEADER_BYTES) {
            throw new IOException("Segmented-GCM record header must be 96 bytes.");
        }
        if (!matches(encoded, 0, RECORD_MAGIC)) {
            throw new IOException("Segmented-GCM record magic is invalid.");
        }
        if (Integer.toUnsignedLong(readInt(encoded, RECORD_HEADER_CRC_OFFSET))
                != Integer.toUnsignedLong(crc32(encoded, 0, RECORD_HEADER_CRC_OFFSET))) {
            throw new IOException("Segmented-GCM record header CRC is invalid.");
        }

        ByteBuffer recordBuffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        recordBuffer.position(RECORD_MAGIC.length);
        int headerBytes = Short.toUnsignedInt(recordBuffer.getShort());
        int type = Byte.toUnsignedInt(recordBuffer.get());
        int flags = Byte.toUnsignedInt(recordBuffer.get());
        long totalBytesLong = Integer.toUnsignedLong(recordBuffer.getInt());
        long sequence = recordBuffer.getLong();
        long transactionId = recordBuffer.getLong();
        long blockIndex = recordBuffer.getLong();
        long plaintextBytesLong = Integer.toUnsignedLong(recordBuffer.getInt());
        long relatedCountLong = Integer.toUnsignedLong(recordBuffer.getInt());
        long logicalLength = recordBuffer.getLong();
        byte[] nonce = new byte[NONCE_BYTES];
        recordBuffer.get(nonce);

        if (headerBytes != RECORD_HEADER_BYTES || flags != 0 || !isZero(encoded, 64, 28)
                || totalBytesLong > Integer.MAX_VALUE
                || plaintextBytesLong > Integer.MAX_VALUE
                || relatedCountLong > Integer.MAX_VALUE
                || sequence <= 0L || transactionId < 0L || blockIndex < 0L
                || logicalLength < 0L || isAllZero(nonce)) {
            throw new IOException("Segmented-GCM record header fields are invalid.");
        }
        int plaintextBytes = (int) plaintextBytesLong;
        int relatedCount = (int) relatedCountLong;
        int totalBytes = (int) totalBytesLong;
        if (totalBytes != RECORD_FIXED_BYTES + plaintextBytes) {
            throw new IOException("Segmented-GCM total record size is invalid.");
        }

        RecordMetadata metadata = new RecordMetadata(type, sequence, transactionId,
                blockIndex, plaintextBytes, relatedCount, logicalLength, nonce,
                totalBytes, encoded.clone());
        validateMetadata(header, metadata);
        return metadata;
    }

    private static byte[] encodeRecordHeader(
            Header header, RecordMetadata metadata, int plaintextBytes) throws IOException {
        if (header == null || metadata == null) {
            throw new IOException("Segmented-GCM header and record metadata are required.");
        }
        if (plaintextBytes != metadata.plaintextBytes) {
            throw new IOException("Segmented-GCM plaintext does not match record metadata.");
        }
        validateMetadata(header, metadata);

        byte[] encoded = new byte[RECORD_HEADER_BYTES];
        ByteBuffer recordBuffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        recordBuffer.put(RECORD_MAGIC);
        recordBuffer.putShort((short) RECORD_HEADER_BYTES);
        recordBuffer.put((byte) metadata.type);
        recordBuffer.put((byte) 0);
        recordBuffer.putInt(RECORD_FIXED_BYTES + plaintextBytes);
        recordBuffer.putLong(metadata.sequence);
        recordBuffer.putLong(metadata.transactionId);
        recordBuffer.putLong(metadata.blockIndex);
        recordBuffer.putInt(plaintextBytes);
        recordBuffer.putInt(metadata.relatedCount);
        recordBuffer.putLong(metadata.logicalLength);
        recordBuffer.put(metadata.nonce);
        recordBuffer.position(RECORD_HEADER_CRC_OFFSET);
        recordBuffer.putInt(crc32(encoded, 0, RECORD_HEADER_CRC_OFFSET));
        return encoded;
    }

    // Integer constant labels cannot use Java's pattern-only `when` guard syntax.
    @SuppressWarnings("java:S6916")
    private static void validateMetadata(Header header, RecordMetadata metadata)
            throws IOException {
        if (metadata.sequence <= 0L || metadata.transactionId < 0L
                || metadata.blockIndex < 0L || metadata.plaintextBytes < 0
                || metadata.relatedCount < 0 || metadata.logicalLength < 0L
                || metadata.nonce == null || metadata.nonce.length != NONCE_BYTES
                || isAllZero(metadata.nonce)) {
            throw new IOException("Segmented-GCM record metadata is invalid.");
        }
        switch (metadata.type) {
            case TYPE_DATA -> {
                if (metadata.plaintextBytes <= 0 || metadata.plaintextBytes > header.blockBytes
                        || metadata.relatedCount != 0
                        || metadata.transactionId == 0L && metadata.logicalLength <= 0L
                        || metadata.transactionId > 0L && metadata.logicalLength != 0L) {
                    throw new IOException("Segmented-GCM DATA metadata is invalid.");
                }
            }
            case TYPE_COMMIT -> {
                if (metadata.transactionId <= 0L || metadata.blockIndex != 0L
                        || metadata.plaintextBytes != 0) {
                    throw new IOException("Segmented-GCM COMMIT metadata is invalid.");
                }
            }
            case TYPE_CHECKPOINT, TYPE_FINAL -> {
                if (metadata.transactionId != 0L || metadata.blockIndex != 0L
                        || metadata.plaintextBytes != 0 || metadata.relatedCount != 0) {
                    throw new IOException("Segmented-GCM control metadata is invalid.");
                }
            }
            default -> throw new IOException("Segmented-GCM record type is unsupported.");
        }
    }

    private static byte[] crypt(int mode, byte[] key, byte[] nonce,
            byte[] fileHeader, byte[] recordHeader, byte[] input) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(fileHeader);
            cipher.updateAAD(recordHeader);
            return cipher.doFinal(input);
        } catch (AEADBadTagException error) {
            throw new IOException("Segmented-GCM authentication failed.", error);
        } catch (GeneralSecurityException error) {
            throw new IOException("Segmented-GCM cryptographic operation failed.", error);
        }
    }

    private static byte[] pbkdf2HmacSha256(
            byte[] password, byte[] salt, int iterations) throws IOException {
        byte[] initialInput = Arrays.copyOf(salt, salt.length + Integer.BYTES);
        ByteBuffer.wrap(initialInput, salt.length, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN).putInt(1);
        byte[] current = null;
        byte[] derived = null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(password, "HmacSHA256"));
            current = mac.doFinal(initialInput);
            derived = current.clone();
            for (int iteration = 1; iteration < iterations; iteration++) {
                byte[] previous = current;
                current = mac.doFinal(previous);
                Arrays.fill(previous, (byte) 0);
                for (int index = 0; index < derived.length; index++) {
                    derived[index] ^= current[index];
                }
            }
            return derived;
        } catch (GeneralSecurityException error) {
            throw new IOException("Cannot derive segmented-GCM media key.", error);
        } finally {
            Arrays.fill(initialInput, (byte) 0);
            if (current != null) Arrays.fill(current, (byte) 0);
        }
    }

    private static void validateKey(byte[] key) throws IOException {
        if (key == null || key.length != AES_KEY_BYTES) {
            throw new IOException("Segmented-GCM AES-256 key must be 32 bytes.");
        }
    }

    private static void validateBlockBytes(int blockBytes) throws IOException {
        if (blockBytes < MIN_BLOCK_BYTES || blockBytes > MAX_BLOCK_BYTES
                || Integer.bitCount(blockBytes) != 1) {
            throw new IOException("Segmented-GCM logical block size is invalid.");
        }
    }

    private static void validateKdfIterations(int iterations) throws IOException {
        if (iterations < MIN_KDF_ITERATIONS || iterations > MAX_KDF_ITERATIONS) {
            throw new IOException("Segmented-GCM PBKDF2 iteration count is invalid.");
        }
    }

    private static byte[] requireBytes(byte[] bytes, int length, String name) throws IOException {
        if (bytes == null || bytes.length != length) {
            throw new IOException(name + " must be " + length + " bytes.");
        }
        return bytes.clone();
    }

    private static boolean matches(byte[] source, int offset, byte[] expected) {
        if (offset < 0 || source.length - offset < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if (source[offset + index] != expected[index]) return false;
        }
        return true;
    }

    private static boolean isZero(byte[] bytes, int offset, int length) {
        for (int index = offset; index < offset + length; index++) {
            if (bytes[index] != 0) return false;
        }
        return true;
    }

    private static boolean isAllZero(byte[] bytes) {
        return isZero(bytes, 0, bytes.length);
    }

    private static int crc32(byte[] bytes, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(bytes, offset, length);
        return (int) crc.getValue();
    }

    private static int readInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN).getInt();
    }

    static final class Header {
        final int blockBytes;
        final int kdfIterations;
        final byte[] salt;
        final byte[] fileId;
        final byte[] encoded;

        private Header(int blockBytes, int kdfIterations,
                byte[] salt, byte[] fileId, byte[] encoded) {
            this.blockBytes = blockBytes;
            this.kdfIterations = kdfIterations;
            this.salt = salt.clone();
            this.fileId = fileId.clone();
            this.encoded = encoded.clone();
        }
    }

    // Constructor fields map directly to the fixed binary record header; grouping would obscure it.
    @SuppressWarnings("java:S107")
    static final class RecordMetadata {
        final int type;
        final long sequence;
        final long transactionId;
        final long blockIndex;
        final int plaintextBytes;
        final int relatedCount;
        final long logicalLength;
        final byte[] nonce;
        final int totalBytes;
        final byte[] encodedHeader;

        RecordMetadata(int type, long sequence, long transactionId, long blockIndex,
                int plaintextBytes, int relatedCount, long logicalLength, byte[] nonce) {
            this(type, sequence, transactionId, blockIndex, plaintextBytes, relatedCount,
                    logicalLength, nonce, RECORD_FIXED_BYTES + plaintextBytes, null);
        }

        private RecordMetadata(int type, long sequence, long transactionId, long blockIndex,
                int plaintextBytes, int relatedCount, long logicalLength, byte[] nonce,
                int totalBytes, byte[] encodedHeader) {
            this.type = type;
            this.sequence = sequence;
            this.transactionId = transactionId;
            this.blockIndex = blockIndex;
            this.plaintextBytes = plaintextBytes;
            this.relatedCount = relatedCount;
            this.logicalLength = logicalLength;
            this.nonce = nonce == null ? null : nonce.clone();
            this.totalBytes = totalBytes;
            this.encodedHeader = encodedHeader == null ? null : encodedHeader.clone();
        }
    }

    static final class DecodedRecord {
        final RecordMetadata metadata;
        final byte[] plaintext;

        private DecodedRecord(RecordMetadata metadata, byte[] plaintext) {
            this.metadata = metadata;
            this.plaintext = plaintext;
        }
    }
}
