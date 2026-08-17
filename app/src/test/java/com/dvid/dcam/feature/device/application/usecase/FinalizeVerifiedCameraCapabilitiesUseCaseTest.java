package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedPipeline;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase.PipelineRun;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineCoverage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineRunStatus;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleStage;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class FinalizeVerifiedCameraCapabilitiesUseCaseTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE_A =
            new VerificationPipelineId("pipeline-a");
    private static final VerificationPipelineId PIPELINE_B =
            new VerificationPipelineId("pipeline-b");
    private static final CaptureModeTuple HD = tuple(StandardResolutionLabel.HD,
            1280, 720);
    private static final CaptureModeTuple FHD = tuple(StandardResolutionLabel.FHD,
            1920, 1080);

    @Test void finalSnapshotContainsOnlyVerifiedOptionsAndReusableSelection() {
        Snapshot baseline = baseline(FHD);
        PipelineRun runA = run(PIPELINE_A, Map.of(
                HD, VerificationOutcome.VERIFIED_PASS,
                FHD, VerificationOutcome.DEFINITIVE_UNSUPPORTED), 5, 10);
        PipelineRun runB = run(PIPELINE_B, Map.of(
                HD, VerificationOutcome.VERIFIED_PASS,
                FHD, VerificationOutcome.DEFINITIVE_UNSUPPORTED), 7, 20);

        FinalizeVerifiedCameraCapabilitiesUseCase.Result result =
                new FinalizeVerifiedCameraCapabilitiesUseCase().execute(
                        new FinalizeVerifiedCameraCapabilitiesUseCase.Request(
                                baseline, runA, runB, Optional.of(PIPELINE_A)));

        Snapshot snapshot = result.snapshot();
        assertEquals(InitializationState.READY_REUSABLE,
                snapshot.initializationState());
        assertEquals(HD, snapshot.cameras().get(0).selectedRecordingProfile()
                .orElseThrow().tuple());
        assertEquals(PIPELINE_A, snapshot.cameras().get(0).selectedPipeline()
                .orElseThrow().pipelineId());
        PipelineEvidence finalA = pipeline(snapshot, PIPELINE_A);
        assertTrue(finalA.rawFastCandidates().contains(tupleKey(PIPELINE_A, FHD)));
        assertTrue(finalA.rawFastCandidates().contains(tupleKey(PIPELINE_A, HD)));
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                finalA.outcome(tupleKey(PIPELINE_A, FHD)));
        assertFalse(finalA.effectiveCandidates().contains(tupleKey(PIPELINE_A, FHD)));
        assertEquals(1, result.summary().pipelineAFailedTuples().size());
        assertEquals(FHD, result.summary().pipelineAFailedTuples().get(0).tuple());
        assertEquals(1, result.summary().pipelineATupleCount());
        assertEquals(1, result.summary().pipelineBTupleCount());
        assertEquals(15, result.summary().pipelineAElapsedMillis());
        assertEquals(27, result.summary().pipelineBElapsedMillis());
    }

    @Test void summaryCountsOnlyEffectiveVerifiedTuples() {
        CaptureModeTuple lowerSd = tuple(StandardResolutionLabel.SD, 640, 480);
        CaptureModeTuple pinnedSd = tuple(StandardResolutionLabel.SD, 720, 480);
        Snapshot baseline = baseline(HD);
        PipelineRun runA = run(PIPELINE_A, Map.of(
                lowerSd, VerificationOutcome.VERIFIED_PASS,
                pinnedSd, VerificationOutcome.VERIFIED_PASS), 1, 1);
        PipelineRun runB = run(PIPELINE_B, Map.of(
                lowerSd, VerificationOutcome.VERIFIED_PASS,
                pinnedSd, VerificationOutcome.VERIFIED_PASS), 1, 1);

        FinalizeVerifiedCameraCapabilitiesUseCase.Result result =
                new FinalizeVerifiedCameraCapabilitiesUseCase().execute(
                        new FinalizeVerifiedCameraCapabilitiesUseCase.Request(
                                baseline, runA, runB, Optional.of(PIPELINE_A)));

        assertEquals(2, pipeline(result.snapshot(), PIPELINE_A)
                .rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .count());
        assertEquals(1, pipeline(result.snapshot(), PIPELINE_A)
                .effectiveCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .count());
        assertEquals(1, result.summary().pipelineATupleCount());
        assertEquals(1, result.summary().pipelineBTupleCount());
    }

    @Test void validPreviousProfileIsPreservedInsteadOfDefaultingHigher() {
        Snapshot baseline = baseline(HD);
        PipelineRun runA = run(PIPELINE_A, Map.of(
                HD, VerificationOutcome.VERIFIED_PASS,
                FHD, VerificationOutcome.VERIFIED_PASS), 1, 2);
        PipelineRun runB = run(PIPELINE_B, Map.of(
                HD, VerificationOutcome.VERIFIED_PASS,
                FHD, VerificationOutcome.VERIFIED_PASS), 1, 2);

        Snapshot snapshot = new FinalizeVerifiedCameraCapabilitiesUseCase().execute(
                new FinalizeVerifiedCameraCapabilitiesUseCase.Request(
                        baseline, runA, runB, Optional.of(PIPELINE_A))).snapshot();

        assertEquals(HD, snapshot.cameras().get(0).selectedRecordingProfile()
                .orElseThrow().tuple());
    }

    @Test void standaloneImageFailureDoesNotRemoveVerifiedTuple() {
        Snapshot baseline = baseline(HD);
        PipelineRun runA = runWithImageOutcome(PIPELINE_A, HD,
                VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                VerificationOutcome.VERIFIED_PASS);
        PipelineRun runB = run(PIPELINE_B, Map.of(
                HD, VerificationOutcome.VERIFIED_PASS), 1, 1);

        Snapshot snapshot = new FinalizeVerifiedCameraCapabilitiesUseCase().execute(
                new FinalizeVerifiedCameraCapabilitiesUseCase.Request(
                        baseline, runA, runB, Optional.of(PIPELINE_A))).snapshot();

        PipelineEvidence finalA = pipeline(snapshot, PIPELINE_A);
        CandidateKey image = CandidateKey.forImage(CAMERA, VideoCodec.H264,
                PIPELINE_A, HD.imageMode());
        CandidateKey video = CandidateKey.forVideo(CAMERA, VideoCodec.H264,
                PIPELINE_A, HD.videoMode());
        assertTrue(finalA.rawFastCandidates().contains(image));
        assertTrue(finalA.rawFastCandidates().contains(video));
        assertTrue(finalA.rawFastCandidates().contains(tupleKey(PIPELINE_A, HD)));
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED, finalA.outcome(image));
        assertFalse(finalA.effectiveCandidates().contains(image));
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                finalA.outcome(tupleKey(PIPELINE_A, HD)));
    }

    @Test void incompleteImageOrTupleEvidenceIsRejected() {
        Snapshot baseline = baseline(HD);
        PipelineRun completeB = run(PIPELINE_B, Map.of(
                HD, VerificationOutcome.VERIFIED_PASS), 1, 1);
        PipelineRun imageUnknown = runWithUnknown(PIPELINE_A, true, false);
        PipelineRun tupleUnknown = runWithUnknown(PIPELINE_A, false, true);
        FinalizeVerifiedCameraCapabilitiesUseCase useCase =
                new FinalizeVerifiedCameraCapabilitiesUseCase();

        assertThrows(IllegalStateException.class, () -> useCase.execute(
                new FinalizeVerifiedCameraCapabilitiesUseCase.Request(
                        baseline, imageUnknown, completeB, Optional.of(PIPELINE_A))));
        assertThrows(IllegalStateException.class, () -> useCase.execute(
                new FinalizeVerifiedCameraCapabilitiesUseCase.Request(
                        baseline, tupleUnknown, completeB, Optional.of(PIPELINE_A))));
    }

    private static Snapshot baseline(CaptureModeTuple selectedTuple) {
        PipelineEvidence pipelineA = verifiedBaseline(PIPELINE_A);
        PipelineEvidence pipelineB = verifiedBaseline(PIPELINE_B);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264,
                CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                List.of(pipelineA, pipelineB), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-hardware",
                List.of(codec), Optional.of(SelectedPipeline.fixed(PIPELINE_A)),
                Optional.of(new SelectedRecordingProfile(
                        VideoCodec.H264, PIPELINE_A, selectedTuple)));
        return Snapshot.current("hardware", List.of(CAMERA), List.of(camera));
    }

    private static PipelineEvidence verifiedBaseline(VerificationPipelineId pipelineId) {
        List<CandidateKey> raw = raw(pipelineId, List.of(HD, FHD));
        List<CandidateEvidence> facts = raw.stream()
                .map(candidate -> new CandidateEvidence(
                        candidate, VerificationOutcome.VERIFIED_PASS)).toList();
        return new PipelineEvidence(CAMERA, VideoCodec.H264, pipelineId,
                PipelineAvailability.AVAILABLE, raw, facts);
    }

    private static PipelineRun run(VerificationPipelineId pipelineId,
            Map<CaptureModeTuple, VerificationOutcome> tupleOutcomes,
            long fastMillis, long verifyMillis) {
        List<CaptureModeTuple> tuples = List.copyOf(tupleOutcomes.keySet());
        List<CandidateKey> raw = raw(pipelineId, tuples);
        List<CandidateEvidence> facts = new ArrayList<>();
        for (CandidateKey candidate : raw) {
            VerificationOutcome outcome = switch (candidate.kind()) {
                case VIDEO -> VerificationOutcome.VERIFIED_PASS;
                case IMAGE -> imageOutcome(candidate, tupleOutcomes);
                case TUPLE -> tupleOutcomes.get(candidate.tuple().orElseThrow());
            };
            if (outcome.isTerminalEvidence()) {
                facts.add(new CandidateEvidence(candidate, outcome));
            }
        }
        PipelineEvidence evidence = new PipelineEvidence(CAMERA, VideoCodec.H264,
                pipelineId, PipelineAvailability.AVAILABLE, raw, facts);
        List<TupleOutcome> outcomes = tupleOutcomes.entrySet().stream()
                .map(entry -> new TupleOutcome(entry.getKey(), entry.getValue(),
                        TupleStage.COMBO, "verified")).toList();
        PipelineCoverage coverage = new PipelineCoverage(CAMERA, pipelineId,
                PipelineRunStatus.COMPLETE, evidence, outcomes,
                fastMillis, verifyMillis, true, "complete");
        return new PipelineRun(pipelineId, PipelineRunStatus.COMPLETE,
                List.of(coverage), fastMillis, verifyMillis, true, "complete");
    }

    private static PipelineRun runWithImageOutcome(VerificationPipelineId pipelineId,
            CaptureModeTuple tuple, VerificationOutcome imageOutcome,
            VerificationOutcome tupleOutcome) {
        List<CandidateKey> raw = raw(pipelineId, List.of(tuple));
        List<CandidateEvidence> facts = new ArrayList<>();
        for (CandidateKey candidate : raw) {
            VerificationOutcome outcome = switch (candidate.kind()) {
                case VIDEO -> VerificationOutcome.VERIFIED_PASS;
                case IMAGE -> imageOutcome;
                case TUPLE -> tupleOutcome;
            };
            facts.add(new CandidateEvidence(candidate, outcome));
        }
        PipelineEvidence evidence = new PipelineEvidence(CAMERA, VideoCodec.H264,
                pipelineId, PipelineAvailability.AVAILABLE, raw, facts);
        PipelineCoverage coverage = new PipelineCoverage(CAMERA, pipelineId,
                PipelineRunStatus.COMPLETE, evidence,
                List.of(new TupleOutcome(tuple, tupleOutcome,
                        TupleStage.COMBO, "verified")),
                1, 1, true, "complete");
        return new PipelineRun(pipelineId, PipelineRunStatus.COMPLETE,
                List.of(coverage), 1, 1, true, "complete");
    }

    private static PipelineRun runWithUnknown(VerificationPipelineId pipelineId,
            boolean imageUnknown, boolean tupleUnknown) {
        List<CandidateKey> raw = raw(pipelineId, List.of(HD));
        List<CandidateEvidence> facts = new ArrayList<>();
        for (CandidateKey candidate : raw) {
            if (candidate.kind() == CandidateKey.Kind.VIDEO) {
                facts.add(new CandidateEvidence(
                        candidate, VerificationOutcome.VERIFIED_PASS));
            } else if (candidate.kind() == CandidateKey.Kind.IMAGE && !imageUnknown) {
                facts.add(new CandidateEvidence(
                        candidate, VerificationOutcome.VERIFIED_PASS));
            } else if (candidate.kind() == CandidateKey.Kind.TUPLE && !tupleUnknown) {
                facts.add(new CandidateEvidence(
                        candidate, VerificationOutcome.VERIFIED_PASS));
            }
        }
        PipelineEvidence evidence = new PipelineEvidence(CAMERA, VideoCodec.H264,
                pipelineId, PipelineAvailability.AVAILABLE, raw, facts);
        PipelineCoverage coverage = new PipelineCoverage(CAMERA, pipelineId,
                PipelineRunStatus.COMPLETE, evidence,
                List.of(new TupleOutcome(HD, VerificationOutcome.VERIFIED_PASS,
                        TupleStage.COMBO, "declared complete")),
                1, 1, true, "complete");
        return new PipelineRun(pipelineId, PipelineRunStatus.COMPLETE,
                List.of(coverage), 1, 1, true, "complete");
    }

    private static VerificationOutcome imageOutcome(CandidateKey image,
            Map<CaptureModeTuple, VerificationOutcome> tupleOutcomes) {
        return tupleOutcomes.entrySet().stream()
                .filter(entry -> entry.getKey().imageMode().equals(
                        image.imageMode().orElseThrow()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(VerificationOutcome.DEFINITIVE_UNSUPPORTED);
    }

    private static List<CandidateKey> raw(VerificationPipelineId pipelineId,
            List<CaptureModeTuple> tuples) {
        List<CandidateKey> result = new ArrayList<>();
        for (CaptureModeTuple tuple : tuples) {
            CandidateKey video = CandidateKey.forVideo(
                    CAMERA, VideoCodec.H264, pipelineId, tuple.videoMode());
            CandidateKey image = CandidateKey.forImage(
                    CAMERA, VideoCodec.H264, pipelineId, tuple.imageMode());
            CandidateKey combo = tupleKey(pipelineId, tuple);
            if (!result.contains(video)) result.add(video);
            if (!result.contains(image)) result.add(image);
            result.add(combo);
        }
        return List.copyOf(result);
    }

    private static PipelineEvidence pipeline(Snapshot snapshot,
            VerificationPipelineId pipelineId) {
        return snapshot.cameras().get(0).codecs().get(0).pipelines().stream()
                .filter(value -> value.verificationPipelineId().equals(pipelineId))
                .findFirst().orElseThrow();
    }

    private static CandidateKey tupleKey(VerificationPipelineId pipelineId,
            CaptureModeTuple tuple) {
        return CandidateKey.forTuple(CAMERA, VideoCodec.H264, pipelineId, tuple);
    }

    private static CaptureModeTuple tuple(StandardResolutionLabel label,
            int width, int height) {
        StandardResolution resolution = new StandardResolution(
                label, new CameraResolution(width, height));
        return new CaptureModeTuple(new VideoMode(resolution, 30),
                new ImageMode(resolution));
    }
}