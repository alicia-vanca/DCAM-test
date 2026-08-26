package com.dvid.dcam.platform.storage;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

interface DcamRandomAccessMedia extends SeekableByteChannel {
    long physicalSize() throws IOException;
    long estimatedPhysicalSize() throws IOException;
    long finalizationReserveBytes();
    void force(boolean metadata) throws IOException;
    void checkpoint() throws IOException;
    void finish() throws IOException;
    boolean isFinalized();
    void beginTransaction() throws IOException;
    void commitTransaction() throws IOException;
    void rollbackTransaction() throws IOException;

    default long length() throws IOException {
        return size();
    }

    default void setLength(long length) throws IOException {
        if (length < 0L) throw new IOException("Media length must be non-negative.");
        long current = size();
        if (length < current) {
            truncate(length);
            return;
        }
        if (length == current) return;
        long pointer = position();
        position(length - 1L);
        writeFully(this, ByteBuffer.wrap(new byte[] {0}));
        position(pointer);
    }

    default long getFilePointer() throws IOException {
        return position();
    }

    default void seek(long position) throws IOException {
        position(position);
    }

    default int readInt() throws IOException {
        ByteBuffer value = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        readFully(this, value);
        value.flip();
        return value.getInt();
    }

    default long readLong() throws IOException {
        ByteBuffer value = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
        readFully(this, value);
        value.flip();
        return value.getLong();
    }

    default int readUnsignedByte() throws IOException {
        ByteBuffer value = ByteBuffer.allocate(1);
        readFully(this, value);
        return Byte.toUnsignedInt(value.array()[0]);
    }

    default void writeInt(int value) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        data.putInt(value).flip();
        writeFully(this, data);
    }

    default void writeLong(long value) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN);
        data.putLong(value).flip();
        writeFully(this, data);
    }

    default void writeShort(int value) throws IOException {
        ByteBuffer data = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.BIG_ENDIAN);
        data.putShort((short) value).flip();
        writeFully(this, data);
    }

    private static void readFully(SeekableByteChannel channel, ByteBuffer target)
            throws IOException {
        while (target.hasRemaining()) {
            int read = channel.read(target);
            if (read < 0) throw new EOFException("Unexpected end of logical media.");
            if (read == 0) throw new IOException("Logical media stopped producing data.");
        }
    }

    private static void writeFully(SeekableByteChannel channel, ByteBuffer source)
            throws IOException {
        while (source.hasRemaining()) {
            if (channel.write(source) <= 0) {
                throw new IOException("Logical media stopped accepting data.");
            }
        }
    }
}

final class PlainDcamRandomAccessMedia implements DcamRandomAccessMedia {
    private final RandomAccessFile file;
    private final FileChannel channel;
    private final boolean forceOnCheckpoint;

    // Ownership of the open file transfers to the returned media; closing it here would be incorrect.
    @SuppressWarnings("java:S2093")
    static PlainDcamRandomAccessMedia create(File target, boolean forceOnCheckpoint)
            throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create media output directory: " + parent);
        }
        RandomAccessFile file = new RandomAccessFile(target, "rw");
        boolean success = false;
        try {
            file.setLength(0L);
            success = true;
            return new PlainDcamRandomAccessMedia(file, forceOnCheckpoint);
        } finally {
            if (!success) file.close();
        }
    }

    static PlainDcamRandomAccessMedia open(File target) throws IOException {
        return new PlainDcamRandomAccessMedia(new RandomAccessFile(target, "rw"), false);
    }

    private PlainDcamRandomAccessMedia(RandomAccessFile file, boolean forceOnCheckpoint) {
        this.file = file;
        this.channel = file.getChannel();
        this.forceOnCheckpoint = forceOnCheckpoint;
    }

    @Override public int read(ByteBuffer target) throws IOException {
        return channel.read(target);
    }

    @Override public int write(ByteBuffer source) throws IOException {
        return channel.write(source);
    }

    @Override public long position() throws IOException {
        return channel.position();
    }

    @Override public PlainDcamRandomAccessMedia position(long position) throws IOException {
        channel.position(position);
        return this;
    }

    @Override public long size() throws IOException {
        return channel.size();
    }

    @Override public PlainDcamRandomAccessMedia truncate(long size) throws IOException {
        channel.truncate(size);
        if (channel.position() > size) channel.position(size);
        return this;
    }

    @Override public long physicalSize() throws IOException {
        return channel.size();
    }

    @Override public long estimatedPhysicalSize() throws IOException {
        return channel.size();
    }

    @Override public long finalizationReserveBytes() {
        return 0L;
    }

    @Override public void force(boolean metadata) throws IOException {
        channel.force(metadata);
    }

    @Override public void checkpoint() throws IOException {
        if (forceOnCheckpoint) channel.force(false);
    }

    @Override public void finish() throws IOException {
        channel.force(true);
    }

    @Override public boolean isFinalized() {
        return false;
    }

    @Override public void beginTransaction() {
        // Plain media writes directly and therefore has no transaction boundary to begin.
    }

    @Override public void commitTransaction() {
        // Plain media writes are already applied, so commit requires no extra operation.
    }

    @Override public void rollbackTransaction() {
        // Plain media cannot roll back direct writes; its caller owns any cleanup decision.
    }

    @Override public boolean isOpen() {
        return channel.isOpen();
    }

    @Override public void close() throws IOException {
        file.close();
    }
}