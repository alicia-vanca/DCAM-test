package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import androidx.media3.container.Mp4LocationData;
import androidx.media3.muxer.AnnexBToAvccConverter;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import java.nio.ByteBuffer;
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

    @Test void partialVideoOutputIsJoinedBeforeMuxing() {
        SharedAvcEncoder.VideoSampleAssembler assembler =
                new SharedAvcEncoder.VideoSampleAssembler(32);

        assertNull(assembler.append(ByteBuffer.wrap(new byte[] {0, 0}), 1_000L,
                android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                        | android.media.MediaCodec.BUFFER_FLAG_PARTIAL_FRAME));
        SharedAvcEncoder.AssembledVideoSample sample = assembler.append(
                ByteBuffer.wrap(new byte[] {0, 1, 0x65, 0x12}), 1_001L, 0);

        assertArrayEquals(new byte[] {0, 0, 0, 1, 0x65, 0x12}, sample.data());
        assertEquals(1_000L, sample.presentationTimeUs());
        assertEquals(android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME, sample.flags());
        assertEquals(2, sample.bufferCount());
        assertTrue(sample.timestampChanged());
        assertFalse(assembler.hasPending());
    }

    @Test void oversizedPartialVideoOutputClearsPendingBytes() {
        SharedAvcEncoder.VideoSampleAssembler assembler =
                new SharedAvcEncoder.VideoSampleAssembler(4);
        assertNull(assembler.append(ByteBuffer.wrap(new byte[] {0, 0, 0}), 1_000L,
                android.media.MediaCodec.BUFFER_FLAG_PARTIAL_FRAME));

        assertThrows(IllegalStateException.class, () -> assembler.append(
                ByteBuffer.wrap(new byte[] {1, 2}), 1_000L, 0));

        assertFalse(assembler.hasPending());
    }

    @Test void annexBValidationRejectsInternalZeroSequence() {
        ByteBuffer malformed = ByteBuffer.wrap(new byte[] {
                0, 0, 0, 1, 0x65, 0x12, 0, 0, 0, 2, 0x44
        });

        assertEquals(9, SharedAvcEncoder.invalidAnnexBOffset(malformed));
        assertThrows(IllegalStateException.class,
                () -> AnnexBToAvccConverter.DEFAULT.process(malformed.duplicate()));
    }

    @Test void annexBValidationAcceptsMultipleNalUnitsAndTrailingZeros() {
        ByteBuffer valid = ByteBuffer.wrap(new byte[] {
                0, 0, 0, 1, 0x65, 0, 0, 3, 0, 0x12,
                0, 0, 1, 0x41, 0x22, 0, 0
        });

        assertEquals(-1, SharedAvcEncoder.invalidAnnexBOffset(valid));
    }
    @Test void gpsCoordinateMapsToStandardMp4LocationMetadata() {
        GpsCoordinate coordinate = new GpsCoordinate(21.034918, 105.767322);

        Mp4LocationData metadata = SharedAvcEncoder.mp4LocationData(coordinate);

        assertEquals((float) coordinate.getLatitude(), metadata.latitude);
        assertEquals((float) coordinate.getLongitude(), metadata.longitude);
    }
    @Test void gpsRouteQueuesOnlyCoordinateChanges() {
        GpsCoordinate first = new GpsCoordinate(21.034918, 105.767322);
        GpsCoordinate same = new GpsCoordinate(21.034918, 105.767322);
        GpsCoordinate moved = new GpsCoordinate(21.034919, 105.767322);

        assertTrue(SharedAvcEncoder.gpsCoordinateChanged(null, first));
        assertFalse(SharedAvcEncoder.gpsCoordinateChanged(first, same));
        assertTrue(SharedAvcEncoder.gpsCoordinateChanged(first, moved));
        assertFalse(SharedAvcEncoder.gpsCoordinateChanged(first, null));
    }


}