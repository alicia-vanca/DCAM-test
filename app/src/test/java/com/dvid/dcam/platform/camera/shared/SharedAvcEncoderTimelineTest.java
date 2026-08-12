package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SharedAvcEncoderTimelineTest {
    @Test void videoAndAudioUseIndependentClockOrigins() {
        assertEquals(0L,
                SharedAvcEncoder.timelinePresentationTimeUs(9_000_000L, 9_000_000L));
        assertEquals(250_000L,
                SharedAvcEncoder.timelinePresentationTimeUs(9_000_000L, 9_250_000L));
        assertEquals(0L,
                SharedAvcEncoder.timelinePresentationTimeUs(9_000_000L, 8_900_000L));
        assertEquals(31_000_000L, SharedAvcEncoder.videoFrameSystemTimeUs(
                true, true, 250_000L, 31_000_000L, 31_250_000L));
        assertEquals(31_000_000L, SharedAvcEncoder.videoFrameSystemTimeUs(
                true, false, 1_000_000L, 30_000_000L, 31_250_000L));
        assertEquals(31_250_000L, SharedAvcEncoder.videoFrameSystemTimeUs(
                false, false, 0L, 30_000_000L, 31_250_000L));
        assertEquals(14_400L, SharedAvcEncoder.audioAlignmentFrames(
                31_300_000L - 31_000_000L));
        assertEquals(-14_400L, SharedAvcEncoder.audioAlignmentFrames(
                30_700_000L - 31_000_000L));
        assertEquals(9_000_000L,
                SharedAvcEncoder.segmentVideoCutoffUs(9_000_000L, 0L));
        assertEquals(8_500_000L,
                SharedAvcEncoder.segmentVideoCutoffUs(9_000_000L, 500_000L));
        assertFalse(SharedAvcEncoder.sampleAfterCutoff(9_000_000L, 9_000_000L));
        assertTrue(SharedAvcEncoder.sampleAfterCutoff(9_000_000L, 9_000_001L));
        assertEquals(0L, SharedAvcEncoder.timelinePresentationTimeUs(-1L, 500_000L));
    }

    @Test void audioStopTargetUsesVideoDuration() {
        assertEquals(0L, SharedAvcEncoder.audioFrameCountForDurationUs(0L));
        assertEquals(48_000L,
                SharedAvcEncoder.audioFrameCountForDurationUs(1_000_000L));
        assertEquals(2_400L, SharedAvcEncoder.audioSilenceFrameCount(48_000L, 45_600L));
        assertEquals(0L, SharedAvcEncoder.audioSilenceFrameCount(48_000L, 48_000L));
    }

    @Test void cleanSegmentDurationExtendsThroughFinalFrame() {
        assertEquals(120_000L,
                SharedAvcEncoder.segmentDurationUs(3L, 80_000L, 40_000L, 33_333L));
    }

    @Test void cleanSegmentDurationUsesTwoLargestOutOfOrderTimestamps() {
        assertEquals(120_000L,
                SharedAvcEncoder.segmentDurationUs(4L, 90_000L, 60_000L, 33_333L));
    }

    @Test void twoSampleCleanSegmentIncludesFinalFrame() {
        assertEquals(80_000L,
                SharedAvcEncoder.segmentDurationUs(2L, 40_000L, 0L, 33_333L));
    }

    @Test void oneSampleCleanSegmentUsesNominalFrameDuration() {
        assertEquals(33_333L,
                SharedAvcEncoder.segmentDurationUs(1L, 0L, -1L, 33_333L));
    }

    @Test void emptyCleanSegmentHasNoDuration() {
        assertEquals(0L,
                SharedAvcEncoder.segmentDurationUs(0L, -1L, -1L, 33_333L));
    }

}