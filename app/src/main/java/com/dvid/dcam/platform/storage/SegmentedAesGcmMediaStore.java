package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.crypto.AEADBadTagException;

final class SegmentedAesGcmMediaStore implements DcamRandomAccessMedia {
    static final int RECORDING_BLOCK_BYTES = 64 * 1024;
    static final int AUDIO_RECORDING_BLOCK_BYTES = 4 * 1024;
    // ponytail: 2 MiB covers current header/index block rewrites; raise if layout tests touch more blocks.
    static final long FINALIZATION_RESERVE_BYTES = 2L * 1024L * 1024L;

    private static final int RECORD_OVERHEAD_BYTES =
            DcamSegmentedGcmFormat.RECORD_HEADER_BYTES + DcamSegmentedGcmFormat.TAG_BYTES;

    private final RandomAccessFile physicalFile;
    private final FileChannel physicalChannel;
    private final DcamSegmentedGcmFormat.Header header;
    private final byte[] key;
    private final SecureRandom random;
    private final Map<Long, BlockRef> blocks = new HashMap<>();
    private final Set<NonceKey> usedNonces = new HashSet<>();
    private final TreeMap<Long, TransactionBlock> transactionBlocks = new TreeMap<>();

    private long logicalLength;
    private long coveredLength;
    private long logicalPosition;
    private long nextSequence = 1L;
    private boolean finalized;
    private boolean writeFailed;

    private byte[] tailBlock;
    private long tailBlockIndex = -1L;
    private int tailBytes;
    private boolean tailDirty;

    private boolean transactionActive;
    private long transactionLength;
    private long transactionPhysicalStart;
    private long transactionSequenceStart;

    private long cachedBlockIndex = -1L;
    private byte[] cachedBlock;

    static SegmentedAesGcmMediaStore create(File target, String password) throws IOException {
        return create(target, password, RECORDING_BLOCK_BYTES);
    }

    static SegmentedAesGcmMediaStore create(
            File target, String password, int blockBytes) throws IOException {
        requirePassword(password);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create Segmented AES-GCM media directory: " + parent);
        }

        RandomAccessFile physical = new RandomAccessFile(target, "rw");
        byte[] key = null;
        boolean success = false;
        try {
            physical.setLength(0L);
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[DcamSegmentedGcmFormat.SALT_BYTES];
            byte[] fileId = new byte[DcamSegmentedGcmFormat.FILE_ID_BYTES];
            random.nextBytes(salt);
            random.nextBytes(fileId);
            DcamSegmentedGcmFormat.Header header = DcamSegmentedGcmFormat.createHeader(
                    blockBytes, DcamSegmentedGcmFormat.DEFAULT_KDF_ITERATIONS,
                    salt, fileId);
            key = DcamSegmentedGcmFormat.deriveKey(header, password);
            SegmentedAesGcmMediaStore store = new SegmentedAesGcmMediaStore(
                    physical, header, key, random);
            store.writePhysical(0L, header.encoded);
            store.physicalChannel.force(true);
            success = true;
            return store;
        } finally {
            if (!success) {
                if (key != null) Arrays.fill(key, (byte) 0);
                physical.close();
            }
        }
    }

    static SegmentedAesGcmMediaStore openForRecovery(File source, String password)
            throws IOException {
        return open(source, password, true, false);
    }

    static SegmentedAesGcmMediaStore openPublished(File source, String password)
            throws IOException {
        return open(source, password, false, true);
    }

    static boolean hasFamilyMagic(File source) throws IOException {
        if (source == null || !source.isFile()) return false;
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            int bytes = (int) Math.min(input.length(), DcamSegmentedGcmFormat.FILE_HEADER_BYTES);
            byte[] prefix = new byte[bytes];
            input.readFully(prefix);
            return DcamSegmentedGcmFormat.hasFamilyMagic(prefix);
        }
    }

    private static SegmentedAesGcmMediaStore open(
            File source, String password, boolean recoverTail, boolean requireFinal)
            throws IOException {
        requirePassword(password);
        if (source == null || !source.isFile()) {
            throw new IOException("Segmented AES-GCM media file is missing: " + source);
        }
        RandomAccessFile physical = new RandomAccessFile(source, "rw");
        byte[] key = null;
        boolean success = false;
        try {
            if (physical.length() < DcamSegmentedGcmFormat.FILE_HEADER_BYTES) {
                throw new IOException("Segmented AES-GCM file header is truncated.");
            }
            byte[] encodedHeader = new byte[DcamSegmentedGcmFormat.FILE_HEADER_BYTES];
            physical.readFully(encodedHeader);
            DcamSegmentedGcmFormat.Header header =
                    DcamSegmentedGcmFormat.readHeader(encodedHeader);
            key = DcamSegmentedGcmFormat.deriveKey(header, password);
            SegmentedAesGcmMediaStore store = new SegmentedAesGcmMediaStore(
                    physical, header, key, new SecureRandom());
            store.scan(recoverTail, requireFinal);
            success = true;
            return store;
        } finally {
            if (!success) {
                if (key != null) Arrays.fill(key, (byte) 0);
                physical.close();
            }
        }
    }

    private SegmentedAesGcmMediaStore(
            RandomAccessFile physicalFile,
            DcamSegmentedGcmFormat.Header header,
            byte[] key,
            SecureRandom random) {
        this.physicalFile = physicalFile;
        this.physicalChannel = physicalFile.getChannel();
        this.header = header;
        this.key = key;
        this.random = random;
    }

    private void scan(boolean recoverTail, boolean requireFinal) throws IOException {
        long fileBytes = physicalChannel.size();
        long offset = DcamSegmentedGcmFormat.FILE_HEADER_BYTES;
        long expectedSequence = 1L;
        long retainedNextSequence = 1L;
        long pendingStart = -1L;
        long pendingTransactionId = 0L;
        int authenticatedRecords = 0;
        List<BlockRef> pending = new ArrayList<>();

        while (offset < fileBytes) {
            if (finalized) {
                throw new IOException("Segmented AES-GCM file contains bytes after FINAL.");
            }
            long recordStart = offset;
            try {
                if (fileBytes - offset < DcamSegmentedGcmFormat.RECORD_HEADER_BYTES) {
                    throw new IOException("Segmented AES-GCM record header is truncated.");
                }
                byte[] encodedHeader = readPhysical(
                        offset, DcamSegmentedGcmFormat.RECORD_HEADER_BYTES);
                DcamSegmentedGcmFormat.RecordMetadata metadata =
                        DcamSegmentedGcmFormat.readRecordHeader(header, encodedHeader);
                if (metadata.logicalLength > fileBytes) {
                    throw new IOException(
                            "Segmented AES-GCM logical length exceeds physical file size.");
                }
                if (metadata.sequence != expectedSequence) {
                    throw new IOException("Segmented AES-GCM record sequence is not contiguous.");
                }
                long recordEnd = Math.addExact(offset, metadata.totalBytes);
                if (recordEnd > fileBytes) {
                    throw new IOException("Segmented AES-GCM record payload is truncated.");
                }
                if (!usedNonces.add(new NonceKey(metadata.nonce))) {
                    throw new IOException("Segmented AES-GCM nonce is duplicated.");
                }

                byte[] encoded = readPhysical(offset, metadata.totalBytes);
                byte[] plaintext = null;
                try {
                    DcamSegmentedGcmFormat.DecodedRecord decoded =
                            DcamSegmentedGcmFormat.decodeRecord(header, key, encoded);
                    plaintext = decoded.plaintext;
                    authenticatedRecords++;
                    BlockRef ref = new BlockRef(offset, metadata);
                    switch (metadata.type) {
                        case DcamSegmentedGcmFormat.TYPE_DATA -> {
                            if (metadata.transactionId == 0L) {
                                requireNoPending(pending, pendingTransactionId);
                                acceptAutocommit(ref, plaintext);
                                retainedNextSequence = metadata.sequence + 1L;
                            } else {
                                if (pending.isEmpty()) {
                                    if (metadata.transactionId != metadata.sequence) {
                                        throw new IOException(
                                                "Segmented AES-GCM transaction ID must match first DATA sequence.");
                                    }
                                    pendingStart = offset;
                                    pendingTransactionId = metadata.transactionId;
                                } else if (metadata.transactionId != pendingTransactionId) {
                                    throw new IOException(
                                            "Segmented AES-GCM transactions must not interleave.");
                                }
                                pending.add(ref);
                            }
                        }
                        case DcamSegmentedGcmFormat.TYPE_COMMIT -> {
                            acceptCommit(metadata, pending, pendingTransactionId);
                            pending.clear();
                            pendingStart = -1L;
                            pendingTransactionId = 0L;
                            retainedNextSequence = metadata.sequence + 1L;
                        }
                        case DcamSegmentedGcmFormat.TYPE_CHECKPOINT -> {
                            requireNoPending(pending, pendingTransactionId);
                            if (metadata.logicalLength != logicalLength) {
                                throw new IOException(
                                        "Segmented AES-GCM CHECKPOINT length is invalid.");
                            }
                            requireCompleteCoverage();
                            retainedNextSequence = metadata.sequence + 1L;
                        }
                        case DcamSegmentedGcmFormat.TYPE_FINAL -> {
                            requireNoPending(pending, pendingTransactionId);
                            if (metadata.logicalLength != logicalLength || logicalLength <= 0L) {
                                throw new IOException("Segmented AES-GCM FINAL length is invalid.");
                            }
                            requireCompleteCoverage();
                            finalized = true;
                            retainedNextSequence = metadata.sequence + 1L;
                        }
                        default -> throw new IOException(
                                "Segmented AES-GCM record type is unsupported.");
                    }
                } finally {
                    if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
                }
                expectedSequence++;
                offset = recordEnd;
            } catch (IOException | ArithmeticException failure) {
                IOException error = failure instanceof IOException io
                        ? io : new IOException("Segmented AES-GCM record offset overflow.", failure);
                if (!recoverTail || authenticatedRecords == 0 && isAuthenticationFailure(error)) {
                    throw error;
                }
                long truncateOffset = pendingStart >= 0L ? pendingStart : recordStart;
                physicalChannel.truncate(truncateOffset);
                nextSequence = retainedNextSequence;
                logicalPosition = Math.min(logicalPosition, logicalLength);
                return;
            }
        }

        if (!pending.isEmpty()) {
            if (!recoverTail) {
                throw new IOException(
                        "Segmented AES-GCM file ended with an uncommitted transaction.");
            }
            physicalChannel.truncate(pendingStart);
            nextSequence = retainedNextSequence;
        } else {
            nextSequence = expectedSequence;
        }
        if (requireFinal && !finalized) {
            throw new IOException("Segmented AES-GCM published media is missing FINAL.");
        }
        logicalPosition = 0L;
    }

    private void acceptAutocommit(BlockRef ref, byte[] plaintext) throws IOException {
        requireCompleteCoverage();
        DcamSegmentedGcmFormat.RecordMetadata record = ref.record;
        long expectedBlockIndex = logicalLength / header.blockBytes;
        int existingTailBytes = (int) (logicalLength % header.blockBytes);
        long expectedLength;
        try {
            expectedLength = Math.addExact(
                    Math.multiplyExact(record.blockIndex, (long) header.blockBytes),
                    record.plaintextBytes);
        } catch (ArithmeticException failure) {
            throw new IOException("Segmented AES-GCM logical length overflow.", failure);
        }
        if (record.blockIndex != expectedBlockIndex
                || record.plaintextBytes < existingTailBytes
                || record.logicalLength != expectedLength
                || record.logicalLength < logicalLength) {
            throw new IOException("Segmented AES-GCM autocommit DATA is not a tail write.");
        }
        if (existingTailBytes > 0) {
            byte[] previous = committedBlock(record.blockIndex);
            for (int index = 0; index < existingTailBytes; index++) {
                if (previous[index] != plaintext[index]) {
                    throw new IOException(
                            "Segmented AES-GCM autocommit DATA changed committed tail prefix.");
                }
            }
        }
        blocks.put(record.blockIndex, ref);
        invalidateCache(record.blockIndex);
        logicalLength = record.logicalLength;
        coveredLength = logicalLength;
        requireCompleteCoverage();
    }

    private void acceptCommit(
            DcamSegmentedGcmFormat.RecordMetadata record,
            List<BlockRef> pending,
            long pendingTransactionId) throws IOException {
        if (record.relatedCount == 0) {
            if (!pending.isEmpty() || record.transactionId != record.sequence
                    || record.logicalLength > logicalLength) {
                throw new IOException("Segmented AES-GCM zero-DATA COMMIT is invalid.");
            }
        } else if (pending.size() != record.relatedCount
                || pendingTransactionId != record.transactionId) {
            throw new IOException("Segmented AES-GCM COMMIT does not match pending DATA.");
        }

        Map<Long, BlockRef> changed = new HashMap<>();
        for (BlockRef block : pending) changed.put(block.record.blockIndex, block);
        validateCommittedTransaction(changed, record.logicalLength);
        long nextBlockCount = blockCount(record.logicalLength);
        for (Map.Entry<Long, BlockRef> entry : changed.entrySet()) {
            if (entry.getKey() < nextBlockCount) blocks.put(entry.getKey(), entry.getValue());
        }
        if (record.logicalLength < logicalLength) trimBlocks(blocks, record.logicalLength);
        logicalLength = record.logicalLength;
        coveredLength = logicalLength;
        requireCompleteCoverage();
    }

    private void validateCommittedTransaction(
            Map<Long, BlockRef> changed, long nextLength) throws IOException {
        requireCompleteCoverage();
        long nextBlockCount = blockCount(nextLength);
        for (Map.Entry<Long, BlockRef> entry : changed.entrySet()) {
            if (entry.getKey() >= nextBlockCount) continue;
            int expected = expectedBlockBytes(nextLength, entry.getKey());
            if (entry.getValue().record.plaintextBytes < expected) {
                throw new IOException(
                        "Segmented AES-GCM transaction block coverage is invalid: "
                                + entry.getKey());
            }
        }
        if (nextLength <= logicalLength) return;
        long firstExtendedBlock = logicalLength / header.blockBytes;
        for (long blockIndex = firstExtendedBlock;
                blockIndex < nextBlockCount; blockIndex++) {
            BlockRef block = changed.get(blockIndex);
            if (block == null) block = blocks.get(blockIndex);
            int expected = expectedBlockBytes(nextLength, blockIndex);
            if (block == null || block.record.plaintextBytes < expected) {
                throw new IOException(
                        "Segmented AES-GCM transaction block coverage is invalid: "
                                + blockIndex);
            }
        }
    }

    private static void requireNoPending(List<BlockRef> pending, long pendingTransactionId)
            throws IOException {
        if (!pending.isEmpty() || pendingTransactionId != 0L) {
            throw new IOException("Segmented AES-GCM transaction is missing COMMIT.");
        }
    }
    @Override public int read(ByteBuffer target) throws IOException {
        requireOpen();
        if (!target.hasRemaining()) return 0;
        long viewLength = viewLength();
        if (logicalPosition >= viewLength) return -1;
        int copied = 0;
        while (target.hasRemaining() && logicalPosition < viewLength) {
            long blockIndex = logicalPosition / header.blockBytes;
            int blockOffset = (int) (logicalPosition % header.blockBytes);
            int available = expectedBlockBytes(viewLength, blockIndex) - blockOffset;
            if (available <= 0) throw new IOException("Logical media block coverage is invalid.");
            byte[] block = blockForRead(blockIndex);
            if (block.length < blockOffset + available) {
                throw new IOException("Logical media block length is invalid.");
            }
            int bytes = Math.min(target.remaining(), available);
            target.put(block, blockOffset, bytes);
            logicalPosition += bytes;
            copied += bytes;
        }
        return copied;
    }

    @Override public int write(ByteBuffer source) throws IOException {
        requireWritable();
        if (!source.hasRemaining()) return 0;
        if (transactionActive) return writeTransaction(source);
        if (logicalPosition < logicalLength) {
            beginTransactionInternal();
            try {
                int bytes = writeTransaction(source);
                commitTransactionInternal();
                return bytes;
            } catch (IOException | RuntimeException failure) {
                rollbackAfterFailure(failure);
                throw failure;
            }
        }
        return writeSequential(source);
    }

    private int writeSequential(ByteBuffer source) throws IOException {
        int total = source.remaining();
        if (logicalPosition > logicalLength) appendZeros(logicalPosition - logicalLength);
        while (source.hasRemaining()) {
            ensureTailBlock();
            int bytes = Math.min(source.remaining(), header.blockBytes - tailBytes);
            source.get(tailBlock, tailBytes, bytes);
            tailBytes += bytes;
            tailDirty = true;
            logicalLength += bytes;
            logicalPosition += bytes;
            if (tailBytes == header.blockBytes) flushTail();
        }
        return total;
    }

    private void appendZeros(long bytes) throws IOException {
        long remaining = bytes;
        logicalPosition = logicalLength;
        while (remaining > 0L) {
            ensureTailBlock();
            int count = (int) Math.min(remaining, header.blockBytes - tailBytes);
            Arrays.fill(tailBlock, tailBytes, tailBytes + count, (byte) 0);
            tailBytes += count;
            tailDirty = true;
            logicalLength += count;
            logicalPosition += count;
            remaining -= count;
            if (tailBytes == header.blockBytes) flushTail();
        }
    }

    private void ensureTailBlock() throws IOException {
        long blockIndex = logicalLength / header.blockBytes;
        int existingBytes = (int) (logicalLength % header.blockBytes);
        if (tailBlock != null && tailBlockIndex == blockIndex && tailBytes == existingBytes) return;
        clearTail();
        tailBlock = new byte[header.blockBytes];
        tailBlockIndex = blockIndex;
        tailBytes = existingBytes;
        if (existingBytes > 0) {
            byte[] committed = committedBlock(blockIndex);
            if (committed.length < existingBytes) {
                throw new IOException("Segmented AES-GCM tail block is truncated.");
            }
            System.arraycopy(committed, 0, tailBlock, 0, existingBytes);
        }
    }

    private void flushTail() throws IOException {
        if (!tailDirty) return;
        byte[] plaintext = Arrays.copyOf(tailBlock, tailBytes);
        try {
            BlockRef ref = appendData(0L, tailBlockIndex, plaintext, logicalLength);
            blocks.put(tailBlockIndex, ref);
            invalidateCache(tailBlockIndex);
            coveredLength = logicalLength;
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            clearTail();
        }
    }

    @Override public long position() throws IOException {
        requireOpen();
        return logicalPosition;
    }

    @Override public SegmentedAesGcmMediaStore position(long position) throws IOException {
        requireOpen();
        if (position < 0L) throw new IOException("Logical media position must be non-negative.");
        logicalPosition = position;
        return this;
    }

    @Override public long size() throws IOException {
        requireOpen();
        return viewLength();
    }

    @Override public SegmentedAesGcmMediaStore truncate(long size) throws IOException {
        requireWritable();
        if (size < 0L) throw new IOException("Logical media length must be non-negative.");
        long current = viewLength();
        if (size >= current) return this;
        if (transactionActive) {
            transactionLength = size;
            transactionBlocks.keySet().removeIf(index -> index >= blockCount(size));
            if (logicalPosition > size) logicalPosition = size;
            return this;
        }
        beginTransactionInternal();
        try {
            transactionLength = size;
            transactionBlocks.keySet().removeIf(index -> index >= blockCount(size));
            if (logicalPosition > size) logicalPosition = size;
            commitTransactionInternal();
            return this;
        } catch (IOException | RuntimeException failure) {
            rollbackAfterFailure(failure);
            throw failure;
        }
    }

    @Override public long physicalSize() throws IOException {
        requireOpen();
        return physicalChannel.size();
    }

    @Override public long estimatedPhysicalSize() throws IOException {
        long estimated = physicalSize();
        if (tailDirty) estimated = Math.addExact(estimated, RECORD_OVERHEAD_BYTES + tailBytes);
        if (transactionActive) {
            for (Map.Entry<Long, TransactionBlock> entry : transactionBlocks.entrySet()) {
                int bytes = expectedBlockBytes(transactionLength, entry.getKey());
                int previous = expectedBlockBytes(logicalLength, entry.getKey());
                if (bytes > 0 && (entry.getValue().changed || bytes > previous)) {
                    estimated = Math.addExact(estimated, RECORD_OVERHEAD_BYTES + bytes);
                }
            }
            if (transactionLength != logicalLength || !transactionBlocks.isEmpty()) {
                estimated = Math.addExact(estimated, RECORD_OVERHEAD_BYTES);
            }
        }
        return estimated;
    }

    @Override public long finalizationReserveBytes() {
        return FINALIZATION_RESERVE_BYTES;
    }

    @Override public void force(boolean metadata) throws IOException {
        requireOpen();
        if (transactionActive) {
            throw new IOException("Cannot force Segmented AES-GCM media during a transaction.");
        }
        flushTail();
        physicalChannel.force(metadata);
    }

    @Override public void checkpoint() throws IOException {
        requireWritable();
        if (transactionActive) {
            throw new IOException("Cannot checkpoint Segmented AES-GCM media during a transaction.");
        }
        flushTail();
        requireCompleteCoverage();
        appendControl(DcamSegmentedGcmFormat.TYPE_CHECKPOINT, 0L, 0, logicalLength);
        physicalChannel.force(false);
    }

    @Override public void finish() throws IOException {
        requireOpen();
        if (finalized) return;
        requireWritable();
        if (transactionActive) {
            throw new IOException("Cannot finalize Segmented AES-GCM media during a transaction.");
        }
        flushTail();
        if (logicalLength <= 0L) {
            throw new IOException("Cannot finalize empty Segmented AES-GCM media.");
        }
        requireCompleteCoverage();
        appendControl(DcamSegmentedGcmFormat.TYPE_FINAL, 0L, 0, logicalLength);
        physicalChannel.force(true);
        finalized = true;
    }

    @Override public boolean isFinalized() {
        return finalized;
    }
    @Override public void beginTransaction() throws IOException {
        requireWritable();
        if (transactionActive) {
            throw new IOException("Segmented AES-GCM transaction is already active.");
        }
        beginTransactionInternal();
    }

    private void beginTransactionInternal() throws IOException {
        flushTail();
        requireCompleteCoverage();
        transactionActive = true;
        transactionLength = logicalLength;
        transactionPhysicalStart = physicalChannel.size();
        transactionSequenceStart = nextSequence;
    }

    @Override public void commitTransaction() throws IOException {
        requireWritable();
        if (!transactionActive) {
            throw new IOException("Segmented AES-GCM transaction is not active.");
        }
        commitTransactionInternal();
    }

    private void commitTransactionInternal() throws IOException {
        List<Map.Entry<Long, TransactionBlock>> changed = new ArrayList<>();
        for (Map.Entry<Long, TransactionBlock> entry : transactionBlocks.entrySet()) {
            int nextBytes = expectedBlockBytes(transactionLength, entry.getKey());
            int previousBytes = expectedBlockBytes(logicalLength, entry.getKey());
            if (nextBytes > 0 && (entry.getValue().changed || nextBytes > previousBytes)) {
                changed.add(entry);
            }
        }
        validateTransactionCoverage(transactionLength);
        if (changed.isEmpty() && transactionLength == logicalLength) {
            clearTransaction();
            return;
        }

        long transactionId = nextSequence;
        List<BlockRef> committed = new ArrayList<>(changed.size());
        try {
            for (Map.Entry<Long, TransactionBlock> entry : changed) {
                int bytes = expectedBlockBytes(transactionLength, entry.getKey());
                byte[] plaintext = Arrays.copyOf(entry.getValue().data, bytes);
                try {
                    committed.add(appendData(
                            transactionId, entry.getKey(), plaintext, 0L));
                } finally {
                    Arrays.fill(plaintext, (byte) 0);
                }
            }
            appendControl(DcamSegmentedGcmFormat.TYPE_COMMIT,
                    transactionId, committed.size(), transactionLength);
        } catch (IOException | RuntimeException failure) {
            rollbackAfterFailure(failure);
            throw failure;
        }

        for (BlockRef block : committed) blocks.put(block.record.blockIndex, block);
        if (transactionLength < logicalLength) trimBlocks(blocks, transactionLength);
        logicalLength = transactionLength;
        coveredLength = logicalLength;
        if (logicalPosition > logicalLength) logicalPosition = logicalLength;
        invalidateCache(-1L);
        clearTransaction();
        requireCompleteCoverage();
    }

    @Override public void rollbackTransaction() throws IOException {
        requireOpen();
        if (!transactionActive) return;
        physicalChannel.truncate(transactionPhysicalStart);
        nextSequence = transactionSequenceStart;
        if (logicalPosition > logicalLength) logicalPosition = logicalLength;
        clearTransaction();
    }

    private void rollbackAfterFailure(Throwable failure) throws IOException {
        try {
            rollbackTransaction();
        } catch (IOException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            throw rollbackFailure;
        }
    }

    private int writeTransaction(ByteBuffer source) throws IOException {
        int total = source.remaining();
        if (logicalPosition > transactionLength) {
            writeTransactionZeros(logicalPosition - transactionLength);
        }
        while (source.hasRemaining()) {
            long blockIndex = logicalPosition / header.blockBytes;
            int blockOffset = (int) (logicalPosition % header.blockBytes);
            int bytes = Math.min(source.remaining(), header.blockBytes - blockOffset);
            TransactionBlock block = transactionBlock(blockIndex);
            for (int index = 0; index < bytes; index++) {
                byte value = source.get();
                int target = blockOffset + index;
                if (block.data[target] != value) {
                    block.data[target] = value;
                    block.changed = true;
                }
            }
            logicalPosition += bytes;
            transactionLength = Math.max(transactionLength, logicalPosition);
            discardUnchangedTransactionBlock(blockIndex, block);
        }
        return total;
    }

    private void writeTransactionZeros(long bytes) throws IOException {
        long targetPosition = logicalPosition;
        logicalPosition = transactionLength;
        long remaining = bytes;
        while (remaining > 0L) {
            long blockIndex = logicalPosition / header.blockBytes;
            int blockOffset = (int) (logicalPosition % header.blockBytes);
            int count = (int) Math.min(remaining, header.blockBytes - blockOffset);
            TransactionBlock block = transactionBlock(blockIndex);
            for (int index = 0; index < count; index++) {
                int target = blockOffset + index;
                if (block.data[target] != 0) {
                    block.data[target] = 0;
                    block.changed = true;
                }
            }
            logicalPosition += count;
            transactionLength = Math.max(transactionLength, logicalPosition);
            remaining -= count;
            discardUnchangedTransactionBlock(blockIndex, block);
        }
        logicalPosition = targetPosition;
    }

    private TransactionBlock transactionBlock(long blockIndex) throws IOException {
        TransactionBlock existing = transactionBlocks.get(blockIndex);
        if (existing != null) return existing;
        byte[] data = new byte[header.blockBytes];
        int exposedBytes = expectedBlockBytes(Math.min(logicalLength, transactionLength), blockIndex);
        if (exposedBytes > 0) {
            byte[] committed = committedBlock(blockIndex);
            if (committed.length < exposedBytes) {
                throw new IOException("Segmented AES-GCM transaction source block is truncated.");
            }
            System.arraycopy(committed, 0, data, 0, exposedBytes);
        }
        TransactionBlock created = new TransactionBlock(data);
        transactionBlocks.put(blockIndex, created);
        return created;
    }

    private void discardUnchangedTransactionBlock(long blockIndex, TransactionBlock block) {
        if (!block.changed && transactionLength <= logicalLength) {
            transactionBlocks.remove(blockIndex);
            Arrays.fill(block.data, (byte) 0);
        }
    }

    private void validateTransactionCoverage(long length) throws IOException {
        requireCompleteCoverage();
        long count = blockCount(length);
        for (Map.Entry<Long, TransactionBlock> entry : transactionBlocks.entrySet()) {
            if (entry.getKey() >= count) continue;
            if (entry.getValue().data.length < expectedBlockBytes(length, entry.getKey())) {
                throw new IOException(
                        "Segmented AES-GCM transaction block coverage is invalid: "
                                + entry.getKey());
            }
        }
        if (length <= logicalLength) return;
        long firstExtendedBlock = logicalLength / header.blockBytes;
        for (long blockIndex = firstExtendedBlock; blockIndex < count; blockIndex++) {
            TransactionBlock changed = transactionBlocks.get(blockIndex);
            BlockRef committed = changed == null ? blocks.get(blockIndex) : null;
            int expected = expectedBlockBytes(length, blockIndex);
            if (changed == null
                    && (committed == null || committed.record.plaintextBytes < expected)) {
                throw new IOException(
                        "Segmented AES-GCM transaction block coverage is invalid: " + blockIndex);
            }
        }
    }

    private BlockRef appendData(
            long transactionId, long blockIndex, byte[] plaintext, long resultingLength)
            throws IOException {
        return appendRecord(DcamSegmentedGcmFormat.TYPE_DATA, transactionId, blockIndex,
                plaintext.length, 0, resultingLength, plaintext);
    }

    private void appendControl(int type, long transactionId, int relatedCount, long length)
            throws IOException {
        appendRecord(type, transactionId, 0L, 0, relatedCount, length, new byte[0]);
    }

    private BlockRef appendRecord(
            int type,
            long transactionId,
            long blockIndex,
            int plaintextBytes,
            int relatedCount,
            long resultingLength,
            byte[] plaintext) throws IOException {
        long sequence = nextSequence;
        byte[] nonce = nextNonce();
        DcamSegmentedGcmFormat.RecordMetadata metadata =
                new DcamSegmentedGcmFormat.RecordMetadata(
                        type, sequence, transactionId, blockIndex,
                        plaintextBytes, relatedCount, resultingLength, nonce);
        byte[] encoded = DcamSegmentedGcmFormat.encodeRecord(header, key, metadata, plaintext);
        long offset = physicalChannel.size();
        try {
            writePhysical(offset, encoded);
        } catch (IOException failure) {
            writeFailed = true;
            throw failure;
        }
        nextSequence++;
        return new BlockRef(offset, metadata);
    }

    private byte[] nextNonce() {
        while (true) {
            byte[] nonce = new byte[DcamSegmentedGcmFormat.NONCE_BYTES];
            random.nextBytes(nonce);
            if (!isAllZero(nonce) && usedNonces.add(new NonceKey(nonce))) return nonce;
        }
    }
    private byte[] blockForRead(long blockIndex) throws IOException {
        TransactionBlock transaction = transactionBlocks.get(blockIndex);
        if (transaction != null) return transaction.data;
        if (tailBlock != null && tailBlockIndex == blockIndex) return tailBlock;
        return committedBlock(blockIndex);
    }

    private byte[] committedBlock(long blockIndex) throws IOException {
        if (cachedBlock != null && cachedBlockIndex == blockIndex) return cachedBlock;
        BlockRef ref = blocks.get(blockIndex);
        if (ref == null) throw new IOException("Logical media block is missing: " + blockIndex);
        byte[] encoded = readPhysical(ref.offset, ref.record.totalBytes);
        DcamSegmentedGcmFormat.DecodedRecord decoded =
                DcamSegmentedGcmFormat.decodeRecord(header, key, encoded);
        invalidateCache(-1L);
        cachedBlockIndex = blockIndex;
        cachedBlock = decoded.plaintext;
        return cachedBlock;
    }

    private void requireCompleteCoverage() throws IOException {
        if (coveredLength != logicalLength) {
            throw new IOException("Segmented AES-GCM logical coverage is not committed.");
        }
        long count = blockCount(logicalLength);
        if ((long) blocks.size() != count) {
            throw new IOException("Segmented AES-GCM logical block coverage is incomplete.");
        }
        if (count == 0L) return;
        long lastBlockIndex = count - 1L;
        BlockRef last = blocks.get(lastBlockIndex);
        int expected = expectedBlockBytes(logicalLength, lastBlockIndex);
        if (last == null || last.record.plaintextBytes < expected) {
            throw new IOException(
                    "Segmented AES-GCM logical block coverage is invalid: " + lastBlockIndex);
        }
    }

    private void trimBlocks(Map<Long, BlockRef> blockMap, long length) {
        long count = blockCount(length);
        blockMap.keySet().removeIf(blockIndex -> blockIndex >= count);
    }

    private long blockCount(long length) {
        if (length <= 0L) return 0L;
        return (length - 1L) / header.blockBytes + 1L;
    }

    private int expectedBlockBytes(long length, long blockIndex) throws IOException {
        long start;
        try {
            start = Math.multiplyExact(blockIndex, (long) header.blockBytes);
        } catch (ArithmeticException failure) {
            throw new IOException("Segmented AES-GCM logical block offset overflow.", failure);
        }
        long remaining = length - start;
        if (remaining <= 0L) return 0;
        return (int) Math.min(remaining, header.blockBytes);
    }

    private long viewLength() {
        return transactionActive ? transactionLength : logicalLength;
    }

    private byte[] readPhysical(long offset, int bytes) throws IOException {
        if (offset < 0L || bytes < 0) throw new IOException("Invalid encrypted media read range.");
        ByteBuffer target = ByteBuffer.allocate(bytes);
        while (target.hasRemaining()) {
            int read = physicalChannel.read(target, Math.addExact(offset, target.position()));
            if (read < 0) throw new IOException("Unexpected end of encrypted media file.");
            if (read == 0) throw new IOException("Encrypted media file stopped producing data.");
        }
        return target.array();
    }

    private void writePhysical(long offset, byte[] bytes) throws IOException {
        ByteBuffer source = ByteBuffer.wrap(bytes);
        while (source.hasRemaining()) {
            int written = physicalChannel.write(
                    source, Math.addExact(offset, source.position()));
            if (written <= 0) throw new IOException("Encrypted media file stopped accepting data.");
        }
    }

    private void clearTail() {
        if (tailBlock != null) Arrays.fill(tailBlock, (byte) 0);
        tailBlock = null;
        tailBlockIndex = -1L;
        tailBytes = 0;
        tailDirty = false;
    }

    private void clearTransaction() {
        for (TransactionBlock block : transactionBlocks.values()) {
            Arrays.fill(block.data, (byte) 0);
        }
        transactionBlocks.clear();
        transactionActive = false;
        transactionLength = 0L;
        transactionPhysicalStart = 0L;
        transactionSequenceStart = 0L;
    }

    private void invalidateCache(long blockIndex) {
        if (cachedBlock == null || blockIndex >= 0L && cachedBlockIndex != blockIndex) return;
        Arrays.fill(cachedBlock, (byte) 0);
        cachedBlock = null;
        cachedBlockIndex = -1L;
    }

    private void requireOpen() throws IOException {
        if (!physicalChannel.isOpen()) throw new IOException("Segmented AES-GCM media is closed.");
    }

    private void requireWritable() throws IOException {
        requireOpen();
        if (finalized) throw new IOException("Segmented AES-GCM media is already finalized.");
        if (writeFailed) throw new IOException("Segmented AES-GCM media write previously failed.");
    }

    @Override public boolean isOpen() {
        return physicalChannel.isOpen();
    }

    @Override public void close() throws IOException {
        if (!physicalChannel.isOpen()) return;
        IOException failure = null;
        try {
            if (transactionActive) rollbackTransaction();
            else if (!finalized && !writeFailed) flushTail();
        } catch (IOException error) {
            failure = error;
        }
        try {
            physicalFile.close();
        } catch (IOException error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        } finally {
            clearTail();
            clearTransaction();
            invalidateCache(-1L);
            Arrays.fill(key, (byte) 0);
        }
        if (failure != null) throw failure;
    }

    private static void requirePassword(String password) throws IOException {
        if (password == null || password.isBlank()) {
            throw new IOException("Media encryption password is required.");
        }
    }

    private static boolean isAuthenticationFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof AEADBadTagException) return true;
        }
        return false;
    }

    private static boolean isAllZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) return false;
        }
        return true;
    }

    private record BlockRef(long offset, DcamSegmentedGcmFormat.RecordMetadata record) {}

    private static final class TransactionBlock {
        private final byte[] data;
        private boolean changed;

        private TransactionBlock(byte[] data) {
            this.data = data;
        }
    }

    private static final class NonceKey {
        private final byte[] bytes;
        private final int hash;

        private NonceKey(byte[] bytes) {
            this.bytes = bytes.clone();
            this.hash = Arrays.hashCode(this.bytes);
        }

        @Override public boolean equals(Object other) {
            return other instanceof NonceKey key && Arrays.equals(bytes, key.bytes);
        }

        @Override public int hashCode() {
            return hash;
        }
    }
}