package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class DcamRecordingOutput implements SeekableByteChannel {
    private final File file;
    private final DcamRandomAccessMedia media;
    private final boolean segmentedAesGcm;
    private final LongSupplier availableBytes;

    public static DcamRecordingOutput openPlain(File file) throws IOException {
        File target = Objects.requireNonNull(file, "file");
        return openPlain(target, false, target::getUsableSpace);
    }

    static DcamRecordingOutput openPlain(File file, boolean forceOnCheckpoint)
            throws IOException {
        File target = Objects.requireNonNull(file, "file");
        return openPlain(target, forceOnCheckpoint, target::getUsableSpace);
    }

    static DcamRecordingOutput openPlain(
            File file, boolean forceOnCheckpoint, LongSupplier availableBytes)
            throws IOException {
        File target = Objects.requireNonNull(file, "file");
        return new DcamRecordingOutput(target,
                PlainDcamRandomAccessMedia.create(target, forceOnCheckpoint), false,
                availableBytes);
    }

    static DcamRecordingOutput openSegmentedAesGcm(File file, String password)
            throws IOException {
        File target = Objects.requireNonNull(file, "file");
        return openSegmentedAesGcm(
                target, password, SegmentedAesGcmMediaStore.RECORDING_BLOCK_BYTES,
                target::getUsableSpace);
    }

    static DcamRecordingOutput openSegmentedAesGcm(
            File file, String password, int blockBytes) throws IOException {
        File target = Objects.requireNonNull(file, "file");
        return openSegmentedAesGcm(target, password, blockBytes, target::getUsableSpace);
    }

    static DcamRecordingOutput openSegmentedAesGcm(
            File file, String password, int blockBytes, LongSupplier availableBytes)
            throws IOException {
        File target = Objects.requireNonNull(file, "file");
        return new DcamRecordingOutput(
                target, SegmentedAesGcmMediaStore.create(target, password, blockBytes), true,
                availableBytes);
    }

    private DcamRecordingOutput(
            File file, DcamRandomAccessMedia media, boolean segmentedAesGcm,
            LongSupplier availableBytes) {
        this.file = file;
        this.media = media;
        this.segmentedAesGcm = segmentedAesGcm;
        this.availableBytes = Objects.requireNonNull(availableBytes, "availableBytes");
    }

    public File file() {
        return file;
    }

    public boolean isSegmentedAesGcm() {
        return segmentedAesGcm;
    }

    public long estimatedPhysicalSize() throws IOException {
        return media.estimatedPhysicalSize();
    }

    public long finalizationReserveBytes() {
        return media.finalizationReserveBytes();
    }

    public long availableBytes() {
        return Math.max(0L, availableBytes.getAsLong());
    }

    public void checkpoint() throws IOException {
        media.checkpoint();
    }

    public void finish() throws IOException {
        media.finish();
    }

    void beginTransaction() throws IOException {
        media.beginTransaction();
    }

    void commitTransaction() throws IOException {
        media.commitTransaction();
    }

    void rollbackTransaction() throws IOException {
        media.rollbackTransaction();
    }

    DcamRandomAccessMedia media() {
        return media;
    }

    @Override public int read(ByteBuffer target) throws IOException {
        return media.read(target);
    }

    @Override public int write(ByteBuffer source) throws IOException {
        return media.write(source);
    }

    @Override public long position() throws IOException {
        return media.position();
    }

    @Override public DcamRecordingOutput position(long position) throws IOException {
        media.position(position);
        return this;
    }

    @Override public long size() throws IOException {
        return media.size();
    }

    @Override public DcamRecordingOutput truncate(long size) throws IOException {
        media.truncate(size);
        return this;
    }

    @Override public boolean isOpen() {
        return media.isOpen();
    }

    @Override public void close() throws IOException {
        media.close();
    }
}