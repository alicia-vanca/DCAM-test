package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class RecordingFileSizeLimiterTest {
    @Test void writesThroughExactLimitThenStops() {
        RecordingFileSizeLimiter limiter = new RecordingFileSizeLimiter();
        limiter.reset(10L);

        assertEquals(RecordingFileSizeLimiter.Decision.WRITE, limiter.evaluateSample(4));
        assertEquals(RecordingFileSizeLimiter.Decision.WRITE_AND_STOP,
                limiter.evaluateSample(6));
        assertEquals(RecordingFileSizeLimiter.Decision.STOP, limiter.evaluateSample(5));
        assertEquals(10L, limiter.writtenBytes());
    }

    @Test void rejectsSampleThatWouldExceedLimit() {
        RecordingFileSizeLimiter limiter = new RecordingFileSizeLimiter();
        limiter.reset(10L);

        assertEquals(RecordingFileSizeLimiter.Decision.WRITE, limiter.evaluateSample(6));
        assertEquals(RecordingFileSizeLimiter.Decision.STOP, limiter.evaluateSample(5));
        assertEquals(6L, limiter.writtenBytes());
    }

    @Test void mp4AccountingReservesFinalizationAndPerSampleMetadata() {
        RecordingFileSizeLimiter limiter = new RecordingFileSizeLimiter();
        long sampleBytes = 10L;
        long limit = RecordingFileSizeLimiter.MP4_FINALIZATION_RESERVE_BYTES
                + RecordingFileSizeLimiter.MP4_METADATA_BYTES_PER_SAMPLE + sampleBytes;
        limiter.resetForMp4(limit);

        assertEquals(RecordingFileSizeLimiter.Decision.WRITE_AND_STOP,
                limiter.evaluateSample((int) sampleBytes, 0L));
        assertEquals(sampleBytes + RecordingFileSizeLimiter.MP4_METADATA_BYTES_PER_SAMPLE,
                limiter.writtenBytes());
    }

    @Test void mp4AccountingUsesObservedContainerLength() {
        RecordingFileSizeLimiter limiter = new RecordingFileSizeLimiter();
        long writableBytes = 100L;
        limiter.resetForMp4(
                RecordingFileSizeLimiter.MP4_FINALIZATION_RESERVE_BYTES + writableBytes);

        assertEquals(RecordingFileSizeLimiter.Decision.STOP,
                limiter.evaluateSample(10, 90L));
        assertEquals(0L, limiter.writtenBytes());
    }

    @Test void zeroLimitAllowsSamplesAndResetStartsNewSegment() {
        RecordingFileSizeLimiter limiter = new RecordingFileSizeLimiter();
        limiter.reset(0L);
        assertEquals(RecordingFileSizeLimiter.Decision.WRITE, limiter.evaluateSample(100));

        limiter.reset(5L);
        assertEquals(RecordingFileSizeLimiter.Decision.WRITE_AND_STOP,
                limiter.evaluateSample(5));
        assertEquals(5L, limiter.writtenBytes());
    }

    @Test void rejectsNegativeLimitsSamplesAndObservedSizes() {
        RecordingFileSizeLimiter limiter = new RecordingFileSizeLimiter();
        assertThrows(IllegalArgumentException.class, () -> limiter.reset(-1L));
        assertThrows(IllegalArgumentException.class, () -> limiter.evaluateSample(-1));
        assertThrows(IllegalArgumentException.class, () -> limiter.evaluateSample(1, -1L));
    }
}
