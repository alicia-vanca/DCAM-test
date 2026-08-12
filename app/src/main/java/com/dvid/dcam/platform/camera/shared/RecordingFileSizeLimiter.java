package com.dvid.dcam.platform.camera.shared;

public final class RecordingFileSizeLimiter {
    static final long MP4_FINALIZATION_RESERVE_BYTES = 64L * 1024L;
    static final int MP4_METADATA_BYTES_PER_SAMPLE = 32;

    public enum Decision {
        WRITE(false, true),
        WRITE_AND_STOP(true, true),
        STOP(true, false);

        private final boolean stop;
        private final boolean write;

        Decision(boolean stop, boolean write) {
            this.stop = stop;
            this.write = write;
        }

        public boolean shouldStop() { return stop; }
        public boolean shouldWrite() { return write; }
    }

    private long limitBytes;
    private long finalizationReserveBytes;
    private int metadataBytesPerSample;
    private long writtenBytes;
    private boolean notified;

    public void reset(long value) {
        reset(value, 0L, 0);
    }

    public void resetForMp4(long value) {
        resetForMp4(value, MP4_FINALIZATION_RESERVE_BYTES);
    }

    public void resetForMp4(long value, long finalizationReserveBytes) {
        if (finalizationReserveBytes < 0L) {
            throw new IllegalArgumentException(
                    "finalizationReserveBytes must be non-negative");
        }
        reset(value, finalizationReserveBytes, MP4_METADATA_BYTES_PER_SAMPLE);
    }

    private void reset(long value, long reserveBytes, int sampleMetadataBytes) {
        if (value < 0L) throw new IllegalArgumentException("limitBytes must be non-negative");
        limitBytes = value;
        finalizationReserveBytes = reserveBytes;
        metadataBytesPerSample = sampleMetadataBytes;
        writtenBytes = 0L;
        notified = false;
    }

    public Decision evaluateSample(int sampleBytes) {
        return evaluateSample(sampleBytes, 0L);
    }

    public Decision evaluateSample(int sampleBytes, long currentFileBytes) {
        if (sampleBytes < 0) throw new IllegalArgumentException("sampleBytes must be non-negative");
        if (currentFileBytes < 0L) {
            throw new IllegalArgumentException("currentFileBytes must be non-negative");
        }
        if (notified) return Decision.STOP;
        long accountedBytes = Math.max(writtenBytes, currentFileBytes);
        long nextSampleBytes = (long) sampleBytes + metadataBytesPerSample;
        if (limitBytes <= 0L) {
            writtenBytes = saturatingAdd(accountedBytes, nextSampleBytes);
            return Decision.WRITE;
        }
        long writableLimit = limitBytes - finalizationReserveBytes;
        if (writableLimit < 0L || accountedBytes > writableLimit
                || nextSampleBytes > writableLimit - accountedBytes) {
            notified = true;
            return Decision.STOP;
        }
        writtenBytes = accountedBytes + nextSampleBytes;
        if (writtenBytes < writableLimit) return Decision.WRITE;
        notified = true;
        return Decision.WRITE_AND_STOP;
    }

    public long writtenBytes() {
        return writtenBytes;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
