package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineComparisonSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineDecisionReason;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineDecisionStatus;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparison;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparisonInput;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

final class SelectCameraPipelineUseCaseTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId A =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");
    private static final VerificationPipelineId B =
            new VerificationPipelineId("b-camera2-egl-fanout-v1");
    private static final CaptureModeTuple FHD =
            tuple(StandardResolutionLabel.FHD, 1920, 1080);

    @Test void completeVerifiedComparisonOutranksFastInventory() {
        PipelineEvidence pipelineA = pipeline(A, List.of(FHD),
                VerificationOutcome.VERIFIED_PASS);
        PipelineEvidence pipelineB = pipeline(B, List.of(FHD),
                VerificationOutcome.VERIFIED_PASS);
        PipelineComparison comparison = PipelineComparison.compare(List.of(FHD),
                new PipelineComparisonInput(pipelineA, true, OptionalLong.of(20)),
                new PipelineComparisonInput(pipelineB, true, OptionalLong.of(10)));
        CameraSnapshot camera = camera(pipelineA, pipelineB,
                Optional.of(new PipelineComparisonSnapshot(
                        comparison, OptionalLong.of(20), OptionalLong.of(10))));

        SelectCameraPipelineUseCase.Decision decision =
                new SelectCameraPipelineUseCase(List.of(A, B))
                        .decision(camera).orElseThrow();

        assertEquals(B, decision.pipeline().verificationPipelineId());
        assertEquals(PipelineDecisionStatus.VERIFIED_COMPLETE, decision.status());
        assertEquals(PipelineDecisionReason.PERFORMANCE, decision.reason());
    }

    @Test void fastRankingPrefersStrictRawTupleSuperset() {
        CaptureModeTuple hd = tuple(StandardResolutionLabel.HD, 1280, 720);
        PipelineEvidence pipelineA = pipeline(A, List.of(FHD, hd),
                VerificationOutcome.UNKNOWN);
        PipelineEvidence pipelineB = pipeline(B, List.of(FHD),
                VerificationOutcome.UNKNOWN);

        SelectCameraPipelineUseCase.Decision decision =
                selector().decision(camera(pipelineA, pipelineB, Optional.empty()))
                        .orElseThrow();

        assertEquals(A, decision.pipeline().verificationPipelineId());
        assertEquals(PipelineDecisionReason.STRICT_SUPERSET, decision.reason());
    }

    @Test void fastRankingPrefersBestActualTuple() {
        CaptureModeTuple exact = tuple(StandardResolutionLabel.FHD, 1920, 1080);
        CaptureModeTuple aligned = tuple(StandardResolutionLabel.FHD, 1920, 1088);
        PipelineEvidence pipelineA = pipeline(A, List.of(exact),
                VerificationOutcome.UNKNOWN);
        PipelineEvidence pipelineB = pipeline(B, List.of(aligned),
                VerificationOutcome.UNKNOWN);

        SelectCameraPipelineUseCase.Decision decision =
                selector().decision(camera(pipelineA, pipelineB, Optional.empty()))
                        .orElseThrow();

        assertEquals(A, decision.pipeline().verificationPipelineId());
        assertEquals(PipelineDecisionReason.BEST_TUPLE, decision.reason());
    }

    @Test void fastRankingUsesTupleCountAfterBestTupleTie() {
        CaptureModeTuple hd = tuple(StandardResolutionLabel.HD, 1280, 720);
        CaptureModeTuple sdWide = tuple(StandardResolutionLabel.SD, 720, 480);
        CaptureModeTuple sd = tuple(StandardResolutionLabel.SD, 640, 480);
        PipelineEvidence pipelineA = pipeline(A, List.of(FHD, sdWide, sd),
                VerificationOutcome.UNKNOWN);
        PipelineEvidence pipelineB = pipeline(B, List.of(FHD, hd),
                VerificationOutcome.UNKNOWN);

        SelectCameraPipelineUseCase.Decision decision =
                selector().decision(camera(pipelineA, pipelineB, Optional.empty()))
                        .orElseThrow();

        assertEquals(A, decision.pipeline().verificationPipelineId());
        assertEquals(PipelineDecisionReason.TUPLE_COUNT, decision.reason());
    }

    @Test void fastTieUsesExplicitPreferenceInsteadOfConfiguredOrderOrRecordingProfile() {
        PipelineEvidence pipelineA = pipeline(A, List.of(FHD),
                VerificationOutcome.UNKNOWN);
        PipelineEvidence pipelineB = pipeline(B, List.of(FHD),
                VerificationOutcome.VERIFIED_PASS);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipelineA, pipelineB), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "hardware", List.of(codec),
                Optional.empty(), Optional.of(
                        new SelectedRecordingProfile(VideoCodec.H264, B, FHD)));

        SelectCameraPipelineUseCase selector =
                new SelectCameraPipelineUseCase(List.of(B, A), A);

        assertEquals(A, selector.decision(camera).orElseThrow()
                .pipeline().verificationPipelineId());
        assertEquals(PipelineDecisionReason.FINAL_TIE_A,
                selector.decision(camera).orElseThrow().reason());
        assertEquals(List.of(A, B), selector.ordered(camera).stream()
                .map(PipelineEvidence::verificationPipelineId).toList());
    }

    @Test void fastPhaseIgnoresPartialDeepVerificationEvidence() {
        CaptureModeTuple exact = tuple(StandardResolutionLabel.FHD, 1920, 1080);
        CaptureModeTuple aligned = tuple(StandardResolutionLabel.FHD, 1920, 1088);
        CandidateKey exactA = CandidateKey.forTuple(CAMERA, VideoCodec.H264, A, exact);
        CandidateKey alignedA = CandidateKey.forTuple(CAMERA, VideoCodec.H264, A, aligned);
        PipelineEvidence pipelineA = new PipelineEvidence(
                CAMERA, VideoCodec.H264, A, PipelineAvailability.AVAILABLE,
                List.of(exactA, alignedA), List.of(new CandidateEvidence(
                        exactA, VerificationOutcome.VERIFIED_PASS)));
        CandidateKey exactB = CandidateKey.forTuple(CAMERA, VideoCodec.H264, B, exact);
        CandidateKey alignedB = CandidateKey.forTuple(CAMERA, VideoCodec.H264, B, aligned);
        PipelineEvidence pipelineB = new PipelineEvidence(
                CAMERA, VideoCodec.H264, B, PipelineAvailability.AVAILABLE,
                List.of(exactB, alignedB), List.of());

        SelectCameraPipelineUseCase.Decision decision = selector()
                .decision(camera(pipelineA, pipelineB, Optional.empty())).orElseThrow();

        assertEquals(A, decision.pipeline().verificationPipelineId());
        assertEquals(PipelineDecisionStatus.FAST_COMPLETE, decision.status());
        assertEquals(PipelineDecisionReason.FINAL_TIE_A, decision.reason());
    }

    @Test void exactFastTieWithoutExplicitPreferenceRemainsUnresolved() {
        PipelineEvidence pipelineA = pipeline(A, List.of(FHD),
                VerificationOutcome.UNKNOWN);
        PipelineEvidence pipelineB = pipeline(B, List.of(FHD),
                VerificationOutcome.UNKNOWN);
        CameraSnapshot camera = camera(pipelineA, pipelineB, Optional.empty());
        SelectCameraPipelineUseCase selector =
                new SelectCameraPipelineUseCase(List.of(A, B));

        assertTrue(selector.decision(camera).isEmpty());
        assertTrue(selector.ordered(camera).isEmpty());
    }

    @Test void decisionWaitsForAllExpectedFastInventories() {
        PipelineEvidence pipelineA = pipeline(A, List.of(FHD),
                VerificationOutcome.UNKNOWN);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipelineA), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "hardware", List.of(codec),
                Optional.empty(), Optional.empty());

        assertTrue(selector().decision(camera).isEmpty());
        assertTrue(selector().select(camera).isEmpty());
    }

    private static SelectCameraPipelineUseCase selector() {
        return new SelectCameraPipelineUseCase(List.of(A, B), A);
    }

    private static CameraSnapshot camera(PipelineEvidence pipelineA,
            PipelineEvidence pipelineB, Optional<PipelineComparisonSnapshot> comparison) {
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipelineA, pipelineB), comparison);
        return new CameraSnapshot(CAMERA, "hardware", List.of(codec),
                Optional.empty(), Optional.empty());
    }

    private static PipelineEvidence pipeline(VerificationPipelineId pipeline,
            List<CaptureModeTuple> tuples, VerificationOutcome outcome) {
        List<CandidateKey> candidates = new ArrayList<>();
        List<CandidateEvidence> evidence = new ArrayList<>();
        for (CaptureModeTuple tuple : tuples) {
            CandidateKey candidate = CandidateKey.forTuple(
                    CAMERA, VideoCodec.H264, pipeline, tuple);
            candidates.add(candidate);
            if (outcome != VerificationOutcome.UNKNOWN) {
                evidence.add(new CandidateEvidence(candidate, outcome));
            }
        }
        return new PipelineEvidence(CAMERA, VideoCodec.H264, pipeline,
                PipelineAvailability.AVAILABLE, candidates, evidence);
    }

    private static CaptureModeTuple tuple(StandardResolutionLabel label,
            int width, int height) {
        StandardResolution resolution = new StandardResolution(
                label, new CameraResolution(width, height));
        return new CaptureModeTuple(new VideoMode(resolution, 30),
                new ImageMode(resolution));
    }
}
