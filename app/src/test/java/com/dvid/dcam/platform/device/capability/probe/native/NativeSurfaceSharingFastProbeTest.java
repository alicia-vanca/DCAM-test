package com.dvid.dcam.platform.device.capability.probe.nativesharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class NativeSurfaceSharingFastProbeTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final StandardResolution SD = new StandardResolution(
            StandardResolutionLabel.SD,
            new com.dvid.dcam.feature.device.domain.camera.CameraResolution(640, 480));
    private static final StandardResolution HD = new StandardResolution(
            StandardResolutionLabel.HD,
            new com.dvid.dcam.feature.device.domain.camera.CameraResolution(1280, 720));
    private static final StandardResolution FHD = new StandardResolution(
            StandardResolutionLabel.FHD,
            new com.dvid.dcam.feature.device.domain.camera.CameraResolution(1920, 1080));
    private static final VideoMode HD_30 = new VideoMode(HD, 30);
    private static final VideoMode HD_60 = new VideoMode(HD, 60);
    private static final ImageMode SD_IMAGE = new ImageMode(SD);
    private static final ImageMode FHD_IMAGE = new ImageMode(FHD);

    @Test void eligibilitySeparatesPipelineUnavailableFromTupleFailure() {
        assertEquals("api_below_26", NativeSurfaceSharingFastProbe.pipelineUnavailableReason(
                25, OptionalInt.empty(), true));
        assertEquals("max_shared_surface_count_below_2",
                NativeSurfaceSharingFastProbe.pipelineUnavailableReason(
                        28, OptionalInt.of(1), true));
        assertEquals("h264_encoder_unavailable",
                NativeSurfaceSharingFastProbe.pipelineUnavailableReason(
                        27, OptionalInt.empty(), false));
        assertNull(NativeSurfaceSharingFastProbe.pipelineUnavailableReason(
                31, OptionalInt.of(4), true));
    }

    @Test void buildsFullCartesianMatrixWithoutVerifiedPassClaims() {
        CapturingLogger logger = new CapturingLogger();
        AtomicInteger queryCount = new AtomicInteger();
        CaptureModeTuple first = new CaptureModeTuple(HD_30, SD_IMAGE);
        CaptureModeTuple second = new CaptureModeTuple(HD_60, FHD_IMAGE);

        NativeSurfaceSharingFastProbe.Result result = run(
                List.of(HD_60, HD_30), List.of(FHD_IMAGE, SD_IMAGE),
                (tuple, attempt, confirmation) -> {
                    queryCount.incrementAndGet();
                    return tuple.equals(first) || tuple.equals(second)
                            ? NativeSurfaceSharingFastProbe.ProbeDecision.supported("fixture")
                            : NativeSurfaceSharingFastProbe.ProbeDecision.rejected("fixture");
                }, logger);

        assertTrue(result.complete());
        assertEquals(PipelineAvailability.AVAILABLE, result.evidence().availability());
        assertEquals(4, queryCount.get());
        assertEquals(Set.of(
                CandidateKey.forTuple(CAMERA, VideoCodec.H264,
                        NativeSurfaceSharingFastProbe.PIPELINE_ID, first),
                CandidateKey.forTuple(CAMERA, VideoCodec.H264,
                        NativeSurfaceSharingFastProbe.PIPELINE_ID, second)),
                result.evidence().rawFastCandidates());
        assertTrue(result.evidence().candidateEvidence().isEmpty());
        assertTrue(logger.lines.isEmpty());
        String summary = NativeSurfaceSharingFastProbe.completeLog(result, 2);
        assertTrue(summary.contains("cameraId=0")
                && summary.contains("pipeline=a-camera2-native-surface-sharing-v1")
                && summary.contains("codec=h264")
                && summary.contains("vfProfileCount=2")
                && summary.contains("imageProfileCount=2")
                && summary.contains("tupleProfileCount=2"));
    }

    @Test void requeriesOnlyFalseCellsThatWouldDestroyFpsRow() {
        Map<CaptureModeTuple, Integer> counts = new HashMap<>();
        CaptureModeTuple supported = new CaptureModeTuple(HD_60, SD_IMAGE);
        NativeSurfaceSharingFastProbe.Result result = run(
                List.of(HD_30, HD_60), List.of(SD_IMAGE, FHD_IMAGE),
                (tuple, attempt, confirmation) -> {
                    counts.merge(tuple, 1, Integer::sum);
                    return tuple.equals(supported)
                            ? NativeSurfaceSharingFastProbe.ProbeDecision.supported("fixture")
                            : NativeSurfaceSharingFastProbe.ProbeDecision.rejected("fixture");
                }, new CapturingLogger());

        assertEquals(2, counts.get(new CaptureModeTuple(HD_30, SD_IMAGE)));
        assertEquals(2, counts.get(new CaptureModeTuple(HD_30, FHD_IMAGE)));
        assertEquals(1, counts.get(new CaptureModeTuple(HD_60, SD_IMAGE)));
        assertEquals(1, counts.get(new CaptureModeTuple(HD_60, FHD_IMAGE)));
        assertTrue(result.attempts().stream()
                .filter(value -> value.attempt() == 2)
                .allMatch(value -> value.confirmation().equals("fps_row")));
    }

    @Test void wholeRejectedVideoModeConfirmsEachFalseCellOnlyOnce() {
        Map<CaptureModeTuple, Integer> counts = new HashMap<>();
        NativeSurfaceSharingFastProbe.Result result = run(
                List.of(HD_30, HD_60), List.of(SD_IMAGE, FHD_IMAGE),
                (tuple, attempt, confirmation) -> {
                    counts.merge(tuple, 1, Integer::sum);
                    return NativeSurfaceSharingFastProbe.ProbeDecision.rejected("fixture");
                }, new CapturingLogger());

        assertTrue(result.complete());
        assertTrue(result.evidence().rawFastCandidates().isEmpty());
        assertEquals(8, result.attempts().size());
        assertTrue(counts.values().stream().allMatch(count -> count == 2));
        assertFalse(result.attempts().stream()
                .anyMatch(value -> value.confirmation().equals("video_mode")));
    }

    @Test void transientFailureKeepsEarlierRawFactsAndNeverPrunes() {
        CaptureModeTuple supported = new CaptureModeTuple(HD_30, SD_IMAGE);
        NativeSurfaceSharingFastProbe.Result result = run(
                List.of(HD_30), List.of(SD_IMAGE, FHD_IMAGE),
                (tuple, attempt, confirmation) -> tuple.equals(supported)
                        ? NativeSurfaceSharingFastProbe.ProbeDecision.supported("fixture")
                        : NativeSurfaceSharingFastProbe.ProbeDecision.transientFailure(
                                "camera_busy"),
                new CapturingLogger());

        assertEquals(NativeSurfaceSharingFastProbe.Completion.INCOMPLETE_TRANSIENT,
                result.completion());
        assertEquals(PipelineAvailability.UNKNOWN, result.evidence().availability());
        assertEquals(Set.of(CandidateKey.forTuple(CAMERA, VideoCodec.H264,
                NativeSurfaceSharingFastProbe.PIPELINE_ID, supported)),
                result.evidence().rawFastCandidates());
        assertTrue(result.evidence().candidateEvidence().isEmpty());
    }

    @Test void pipelineAFailuresDoNotMutatePipelineBEvidence() {
        VerificationPipelineId pipelineB = new VerificationPipelineId("b-camera2-egl-fanout-v1");
        CaptureModeTuple tuple = new CaptureModeTuple(HD_30, SD_IMAGE);
        CandidateKey pipelineBCandidate = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, pipelineB, tuple);
        PipelineEvidence pipelineBEvidence = new PipelineEvidence(
                CAMERA, VideoCodec.H264, pipelineB, PipelineAvailability.AVAILABLE,
                List.of(pipelineBCandidate), List.of());
        Set<CandidateKey> before = Set.copyOf(pipelineBEvidence.rawFastCandidates());

        NativeSurfaceSharingFastProbe.Result pipelineA = run(
                List.of(HD_30), List.of(SD_IMAGE),
                (ignored, attempt, confirmation) ->
                        NativeSurfaceSharingFastProbe.ProbeDecision.rejected("a_failure"),
                new CapturingLogger());

        assertTrue(pipelineA.evidence().rawFastCandidates().isEmpty());
        assertEquals(before, pipelineBEvidence.rawFastCandidates());
        assertEquals(PipelineAvailability.AVAILABLE, pipelineBEvidence.availability());
        assertTrue(pipelineBEvidence.effectiveCandidates().contains(pipelineBCandidate));
    }

    @Test void openGuardTransfersOwnershipAcrossTimeoutRace() {
        Object earlyCamera = new Object();
        NativeSurfaceSharingFastProbe.OpenGuard<Object> callbackFirst =
                new NativeSurfaceSharingFastProbe.OpenGuard<>();
        assertTrue(callbackFirst.accept(earlyCamera));
        assertSame(earlyCamera, callbackFirst.abandon());

        NativeSurfaceSharingFastProbe.OpenGuard<Object> timeoutFirst =
                new NativeSurfaceSharingFastProbe.OpenGuard<>();
        assertNull(timeoutFirst.abandon());
        assertFalse(timeoutFirst.accept(new Object()));
    }
    private static NativeSurfaceSharingFastProbe.Result run(
            List<VideoMode> videos, List<ImageMode> images,
            NativeSurfaceSharingFastProbe.TupleQuery query, CapturingLogger logger) {
        return NativeSurfaceSharingFastProbe.runMatrix(
                31, OptionalInt.of(4), CAMERA, videos, images,
                query, logger, System.nanoTime());
    }

    private static final class CapturingLogger implements Logger {
        private final List<String> lines = new ArrayList<>();

        @Override public void debug(String message) { lines.add(message); }
        @Override public void info(String message) { lines.add(message); }
        @Override public void info(String message, Throwable error) { lines.add(message); }
        @Override public void warn(String message, Throwable error) { lines.add(message); }
        @Override public void error(String message, Throwable error) { lines.add(message); }
    }
}