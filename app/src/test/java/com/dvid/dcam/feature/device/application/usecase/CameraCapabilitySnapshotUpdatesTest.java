package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CameraCapabilitySnapshotUpdatesTest {
    @Test void selectedTuplePassRemovesPreviouslyPassedResolutionSibling() {
        CameraId cameraId = new CameraId("0");
        VerificationPipelineId pipelineId = new VerificationPipelineId("pipeline-a");
        VideoMode bestVideo = new VideoMode(new StandardResolution(
                StandardResolutionLabel.SD, new CameraResolution(720, 480)), 30);
        VideoMode fallbackVideo = new VideoMode(new StandardResolution(
                StandardResolutionLabel.SD, new CameraResolution(640, 480)), 30);
        ImageMode image = new ImageMode(new StandardResolution(
                StandardResolutionLabel.SD, new CameraResolution(640, 480)));
        CandidateKey bestVideoKey = CandidateKey.forVideo(
                cameraId, VideoCodec.H264, pipelineId, bestVideo);
        CandidateKey fallbackVideoKey = CandidateKey.forVideo(
                cameraId, VideoCodec.H264, pipelineId, fallbackVideo);
        CandidateKey imageKey = CandidateKey.forImage(
                cameraId, VideoCodec.H264, pipelineId, image);
        CandidateKey bestTuple = CandidateKey.forTuple(cameraId, VideoCodec.H264,
                pipelineId, new CaptureModeTuple(bestVideo, image));
        CandidateKey fallbackTuple = CandidateKey.forTuple(cameraId, VideoCodec.H264,
                pipelineId, new CaptureModeTuple(fallbackVideo, image));
        PipelineEvidence pipeline = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineId, PipelineAvailability.AVAILABLE,
                List.of(bestVideoKey, fallbackVideoKey, imageKey, bestTuple, fallbackTuple),
                List.of(
                        new CandidateEvidence(bestVideoKey, VerificationOutcome.VERIFIED_PASS),
                        new CandidateEvidence(fallbackVideoKey, VerificationOutcome.VERIFIED_PASS),
                        new CandidateEvidence(imageKey, VerificationOutcome.VERIFIED_PASS),
                        new CandidateEvidence(fallbackTuple, VerificationOutcome.VERIFIED_PASS)));
        CameraCapabilityStore.CodecSnapshot codec = new CameraCapabilityStore.CodecSnapshot(
                VideoCodec.H264, CameraCapabilityStore.CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        CameraCapabilityStore.Snapshot snapshot = CameraCapabilityStore.Snapshot.current(
                "hardware", List.of(cameraId), List.of(new CameraCapabilityStore.CameraSnapshot(
                        cameraId, "camera", List.of(codec), Optional.empty(), Optional.empty(),
                        270)));

        CameraCapabilityStore.Snapshot updated = CameraCapabilitySnapshotUpdates.withEvidence(
                snapshot, bestTuple, VerificationOutcome.VERIFIED_PASS);
        PipelineEvidence result = CameraCapabilitySnapshotUpdates.pipeline(updated,
                cameraId, VideoCodec.H264, pipelineId);

        assertEquals(VerificationOutcome.VERIFIED_PASS, result.outcome(bestTuple));
        assertEquals(VerificationOutcome.UNKNOWN, result.outcome(fallbackTuple));
        assertEquals(VerificationOutcome.VERIFIED_PASS, result.outcome(fallbackVideoKey));
        assertEquals(270, updated.cameras().get(0).sensorOrientationDegrees());
        assertTrue(result.rawFastCandidates().contains(fallbackTuple));
    }

    @Test void standaloneImageFailureRemovesOnlyExactStandaloneImage() {
        CameraId cameraId = new CameraId("0");
        VerificationPipelineId pipelineId = new VerificationPipelineId("pipeline-a");
        VideoMode video = new VideoMode(new StandardResolution(
                StandardResolutionLabel.HD, new CameraResolution(1280, 720)), 30);
        ImageMode fhd = new ImageMode(new StandardResolution(
                StandardResolutionLabel.FHD, new CameraResolution(1920, 1080)));
        ImageMode fhdAligned = new ImageMode(new StandardResolution(
                StandardResolutionLabel.FHD, new CameraResolution(1920, 1088)));
        ImageMode hd = new ImageMode(new StandardResolution(
                StandardResolutionLabel.HD, new CameraResolution(1280, 720)));
        CandidateKey fhdImage = CandidateKey.forImage(
                cameraId, VideoCodec.H264, pipelineId, fhd);
        CandidateKey fhdAlignedImage = CandidateKey.forImage(
                cameraId, VideoCodec.H264, pipelineId, fhdAligned);
        CandidateKey hdImage = CandidateKey.forImage(
                cameraId, VideoCodec.H264, pipelineId, hd);
        CandidateKey requested = CandidateKey.forTuple(cameraId, VideoCodec.H264,
                pipelineId, new CaptureModeTuple(video, fhd));
        PipelineEvidence pipeline = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineId, PipelineAvailability.AVAILABLE,
                List.of(fhdImage, fhdAlignedImage, hdImage, requested), List.of());
        CameraCapabilityStore.CodecSnapshot codec = new CameraCapabilityStore.CodecSnapshot(
                VideoCodec.H264, CameraCapabilityStore.CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        CameraCapabilityStore.Snapshot snapshot = CameraCapabilityStore.Snapshot.current(
                "hardware", List.of(cameraId), List.of(new CameraCapabilityStore.CameraSnapshot(
                        cameraId, "camera", List.of(codec), Optional.empty(), Optional.empty())));

        CameraCapabilityStore.Snapshot updated =
                CameraCapabilitySnapshotUpdates.withStandaloneImageEvidence(
                        snapshot, requested, VerificationOutcome.DEFINITIVE_UNSUPPORTED);
        PipelineEvidence result = CameraCapabilitySnapshotUpdates.pipeline(updated,
                cameraId, VideoCodec.H264, pipelineId);

        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED, result.outcome(fhdImage));
        assertEquals(VerificationOutcome.UNKNOWN, result.outcome(fhdAlignedImage));
        assertEquals(VerificationOutcome.UNKNOWN, result.outcome(hdImage));
        assertEquals(VerificationOutcome.UNKNOWN, result.outcome(requested));
        assertTrue(result.effectiveCandidates().contains(requested));
    }
    @Test void recordingProfileChangeAtomicallyUpdatesAutoPipelineDecision() {
        CameraId cameraId = new CameraId("0");
        VerificationPipelineId pipelineA = new VerificationPipelineId("pipeline-a");
        VerificationPipelineId pipelineB = new VerificationPipelineId("pipeline-b");
        CaptureModeTuple tuple = new CaptureModeTuple(
                new VideoMode(new StandardResolution(StandardResolutionLabel.HD,
                        new CameraResolution(1280, 720)), 30),
                new ImageMode(new StandardResolution(StandardResolutionLabel.HD,
                        new CameraResolution(1280, 720))));
        CandidateKey candidateA = CandidateKey.forTuple(
                cameraId, VideoCodec.H264, pipelineA, tuple);
        CandidateKey candidateB = CandidateKey.forTuple(
                cameraId, VideoCodec.H264, pipelineB, tuple);
        PipelineEvidence evidenceA = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineA, PipelineAvailability.AVAILABLE, List.of(candidateA),
                List.of(new CandidateEvidence(
                        candidateA, VerificationOutcome.VERIFIED_PASS)));
        PipelineEvidence evidenceB = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineB, PipelineAvailability.AVAILABLE, List.of(candidateB),
                List.of(new CandidateEvidence(
                        candidateB, VerificationOutcome.VERIFIED_PASS)));
        CameraCapabilityStore.CodecSnapshot codec = new CameraCapabilityStore.CodecSnapshot(
                VideoCodec.H264, CameraCapabilityStore.CodecState.ACTIVE,
                Optional.empty(), List.of(evidenceA, evidenceB), Optional.empty());
        CameraCapabilityStore.Snapshot snapshot = CameraCapabilityStore.Snapshot.current(
                "hardware", List.of(cameraId), List.of(
                        new CameraCapabilityStore.CameraSnapshot(cameraId, "camera",
                                List.of(codec), Optional.of(
                                CameraCapabilityStore.SelectedPipeline.autoFast(pipelineA,
                                        CameraCapabilityStore.PipelineDecisionReason.FINAL_TIE_A)),
                                Optional.of(new CameraCapabilityStore.SelectedRecordingProfile(
                                        VideoCodec.H264, pipelineA, tuple)))));

        CameraCapabilityStore.Snapshot updated =
                CameraCapabilitySnapshotUpdates.withSelectedRecordingProfile(
                        snapshot, cameraId, Optional.of(
                                new CameraCapabilityStore.SelectedRecordingProfile(
                                        VideoCodec.H264, pipelineB, tuple)));

        CameraCapabilityStore.CameraSnapshot updatedCamera = updated.cameras().get(0);
        assertEquals(pipelineB, updatedCamera.selectedPipeline().orElseThrow().pipelineId());
        assertEquals(CameraCapabilityStore.PipelineDecisionStatus.PROFILE_VERIFIED,
                updatedCamera.selectedPipeline().orElseThrow().decisionStatus());
        assertEquals(CameraCapabilityStore.PipelineDecisionReason.VERIFIED_RECORDING_PROFILE,
                updatedCamera.selectedPipeline().orElseThrow().decisionReason());
        assertEquals(pipelineB, updatedCamera.selectedRecordingProfile()
                .orElseThrow().verificationPipelineId());
    }
    @Test void runtimeVerifiedProfileAtomicallyChangesFixedPipeline() {
        CameraId cameraId = new CameraId("0");
        VerificationPipelineId pipelineA = new VerificationPipelineId("pipeline-a");
        VerificationPipelineId pipelineB = new VerificationPipelineId("pipeline-b");
        CaptureModeTuple tuple = new CaptureModeTuple(
                new VideoMode(new StandardResolution(StandardResolutionLabel.HD,
                        new CameraResolution(1280, 720)), 30),
                new ImageMode(new StandardResolution(StandardResolutionLabel.HD,
                        new CameraResolution(1280, 720))));
        CandidateKey candidateA = CandidateKey.forTuple(
                cameraId, VideoCodec.H264, pipelineA, tuple);
        CandidateKey candidateB = CandidateKey.forTuple(
                cameraId, VideoCodec.H264, pipelineB, tuple);
        PipelineEvidence evidenceA = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineA, PipelineAvailability.AVAILABLE, List.of(candidateA),
                List.of(new CandidateEvidence(
                        candidateA, VerificationOutcome.VERIFIED_PASS)));
        PipelineEvidence evidenceB = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineB, PipelineAvailability.AVAILABLE, List.of(candidateB),
                List.of(new CandidateEvidence(
                        candidateB, VerificationOutcome.VERIFIED_PASS)));
        CameraCapabilityStore.CodecSnapshot codec = new CameraCapabilityStore.CodecSnapshot(
                VideoCodec.H264, CameraCapabilityStore.CodecState.ACTIVE,
                Optional.empty(), List.of(evidenceA, evidenceB), Optional.empty());
        CameraCapabilityStore.Snapshot snapshot = CameraCapabilityStore.Snapshot.current(
                "hardware", List.of(cameraId), List.of(
                        new CameraCapabilityStore.CameraSnapshot(cameraId, "camera",
                                List.of(codec), Optional.of(
                                CameraCapabilityStore.SelectedPipeline.fixed(pipelineA)),
                                Optional.of(new CameraCapabilityStore.SelectedRecordingProfile(
                                        VideoCodec.H264, pipelineA, tuple)))));

        CameraCapabilityStore.Snapshot updated =
                CameraCapabilitySnapshotUpdates.withRuntimeVerifiedRecordingProfile(
                        snapshot, cameraId, Optional.of(
                                new CameraCapabilityStore.SelectedRecordingProfile(
                                        VideoCodec.H264, pipelineB, tuple)));

        CameraCapabilityStore.CameraSnapshot updatedCamera = updated.cameras().get(0);
        assertEquals(CameraCapabilityStore.PipelineSelectionMode.FIXED,
                updatedCamera.selectedPipeline().orElseThrow().mode());
        assertEquals(pipelineB, updatedCamera.selectedPipeline().orElseThrow().pipelineId());
        assertEquals(pipelineB, updatedCamera.selectedRecordingProfile()
                .orElseThrow().verificationPipelineId());
    }
    @Test void evidenceChangeInvalidatesComparisonButNoopPreservesIt() {
        CameraId cameraId = new CameraId("0");
        VerificationPipelineId pipelineA = new VerificationPipelineId("pipeline-a");
        VerificationPipelineId pipelineB = new VerificationPipelineId("pipeline-b");
        CaptureModeTuple tuple = new CaptureModeTuple(
                new VideoMode(new StandardResolution(StandardResolutionLabel.FHD,
                        new CameraResolution(1920, 1080)), 30),
                new ImageMode(new StandardResolution(StandardResolutionLabel.HD,
                        new CameraResolution(1280, 720))));
        CandidateKey candidateA = CandidateKey.forTuple(
                cameraId, VideoCodec.H264, pipelineA, tuple);
        CandidateKey candidateB = CandidateKey.forTuple(
                cameraId, VideoCodec.H264, pipelineB, tuple);
        PipelineEvidence evidenceA = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineA, PipelineAvailability.AVAILABLE, List.of(candidateA),
                List.of(new CandidateEvidence(
                        candidateA, VerificationOutcome.VERIFIED_PASS)));
        PipelineEvidence evidenceB = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineB, PipelineAvailability.AVAILABLE, List.of(candidateB),
                List.of(new CandidateEvidence(
                        candidateB, VerificationOutcome.VERIFIED_PASS)));
        PipelineComparison comparison = PipelineComparison.compare(List.of(tuple),
                PipelineComparisonInput.withoutPerformance(evidenceA, true),
                PipelineComparisonInput.withoutPerformance(evidenceB, true));
        CameraCapabilityStore.CodecSnapshot codec =
                new CameraCapabilityStore.CodecSnapshot(VideoCodec.H264,
                        CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                        List.of(evidenceA, evidenceB), Optional.of(
                        CameraCapabilityStore.PipelineComparisonSnapshot
                                .withoutPerformance(comparison)));
        CameraCapabilityStore.Snapshot snapshot = CameraCapabilityStore.Snapshot.current(
                "hardware-signature", List.of(), List.of(
                new CameraCapabilityStore.CameraSnapshot(cameraId,
                        "camera-signature", List.of(codec),
                        Optional.empty(), Optional.empty())));

        CameraCapabilityStore.Snapshot unchanged =
                CameraCapabilitySnapshotUpdates.withEvidence(snapshot, candidateA,
                        VerificationOutcome.VERIFIED_PASS);
        CameraCapabilityStore.Snapshot changed =
                CameraCapabilitySnapshotUpdates.withEvidence(snapshot, candidateA,
                        VerificationOutcome.DEFINITIVE_UNSUPPORTED);

        assertTrue(unchanged.cameras().get(0).codecs().get(0).comparison().isPresent());
        assertTrue(changed.cameras().get(0).codecs().get(0).comparison().isEmpty());
    }
}
