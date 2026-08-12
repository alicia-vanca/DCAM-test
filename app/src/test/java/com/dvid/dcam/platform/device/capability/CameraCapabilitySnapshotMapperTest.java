package com.dvid.dcam.platform.device.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineComparisonSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineVerificationCoverage;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.ConcurrentCameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.DeviceIdentity;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.RawCatalog;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.CameraFastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.FastSnapshot;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CameraCapabilitySnapshotMapperTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");
    private static final VerificationPipelineId PIPELINE_B =
            new VerificationPipelineId("b-camera2-egl-fanout-v1");
    private static final VideoMode VIDEO = video(1920, 1080);
    private static final VideoMode FALLBACK_VIDEO = video(1920, 1088);
    private static final ImageMode IMAGE = image(1920, 1080);
    private static final CaptureModeTuple TUPLE = new CaptureModeTuple(VIDEO, IMAGE);
    private static final CaptureModeTuple FALLBACK_TUPLE =
            new CaptureModeTuple(FALLBACK_VIDEO, IMAGE);

    @Test void freshFastScanPreservesMatchingDurableVerifiedFact() {
        CandidateKey tuple = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE, TUPLE);
        PipelineEvidence previousEvidence = evidence(List.of(
                new CandidateEvidence(tuple, VerificationOutcome.VERIFIED_PASS)));
        Snapshot previous = snapshot(previousEvidence);
        PipelineEvidence freshEvidence = evidence(List.of());
        FastSnapshot fast = fastSnapshot(freshEvidence);

        Snapshot merged = CameraCapabilitySnapshotMapper.merge(fast, Optional.of(previous));

        PipelineEvidence mergedEvidence = merged.cameras().get(0).codecs().get(0)
                .pipelines().get(0);
        assertEquals(VerificationOutcome.VERIFIED_PASS, mergedEvidence.outcome(tuple));
        assertEquals(1, mergedEvidence.effectiveCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.VIDEO).count());
        assertEquals(1, mergedEvidence.effectiveCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE).count());
        assertEquals(List.of(CAMERA), merged.cameraOrderOverride());
        assertTrue(CameraCapabilitySnapshotMapper.hasUsableTupleForEveryCamera(merged));
    }

    @Test void freshFastScanPreservesStandaloneImageFailure() {
        CandidateKey image = CandidateKey.forImage(
                CAMERA, VideoCodec.H264, PIPELINE, IMAGE);
        CandidateKey tuple = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE, TUPLE);
        PipelineEvidence previousEvidence = new PipelineEvidence(
                CAMERA, VideoCodec.H264, PIPELINE, PipelineAvailability.AVAILABLE,
                List.of(tuple, image), List.of(new CandidateEvidence(
                        image, VerificationOutcome.DEFINITIVE_UNSUPPORTED)));
        Snapshot merged = CameraCapabilitySnapshotMapper.merge(
                fastSnapshot(evidence(List.of())), Optional.of(snapshot(previousEvidence)));

        PipelineEvidence mergedEvidence = merged.cameras().get(0).codecs().get(0)
                .pipelines().get(0);
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                mergedEvidence.outcome(image));
        assertFalse(mergedEvidence.effectiveCandidates().contains(image));
    }

    @Test void freshnessUsesStableRootAndPerCameraSignatures() {
        FastSnapshot fast = fastSnapshot(evidence(List.of()));

        var first = CameraCapabilitySnapshotMapper.freshness(fast.rawCatalog());
        var second = CameraCapabilitySnapshotMapper.freshness(fast.rawCatalog());

        assertEquals(first, second);
        assertEquals(64, first.hardwareSignature().length());
        assertEquals(64, first.cameraHardwareSignatures().get(CAMERA).length());
        assertEquals(2, first.pipelineIdentities().size());
        assertTrue(CameraCapabilitySnapshotMapper.isFresh(
                CameraCapabilitySnapshotMapper.merge(fast, Optional.empty()), first));
    }

    @Test void missingSensorOrientationCannotBecomeZeroDegrees() {
        CameraFacts facts = new CameraFacts(CAMERA, 1, 1, null, List.of(), List.of(),
                true, List.of(), List.of(), List.of(), List.of(), List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CameraCapabilitySnapshotMapper.sensorOrientationDegrees(facts));

        assertEquals("camera sensor orientation unavailable cameraId=0", error.getMessage());
    }

    @Test void fastInventoryLeavesStandaloneImageOutputUnknown() {
        CameraId cameraId = new CameraId("0");
        VerificationPipelineId pipelineId = new VerificationPipelineId("pipeline-a");
        VideoMode compatibleVideo = video(1920, 1080);
        VideoMode orphanVideo = video(1920, 1088);
        ImageMode compatibleImage = image(1920, 1080);
        ImageMode orphanImage = image(1920, 1088);
        CandidateKey tuple = CandidateKey.forTuple(cameraId, VideoCodec.H264, pipelineId,
                new CaptureModeTuple(compatibleVideo, compatibleImage));
        PipelineEvidence fastEvidence = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineId, PipelineAvailability.AVAILABLE, List.of(tuple), List.of());
        CameraFacts facts = new CameraFacts(cameraId, 1, 1, 90, List.of(), List.of(),
                true, List.of(), List.of(), List.of(), List.of(), List.of());
        CameraFastSnapshot camera = new CameraFastSnapshot(facts,
                List.of(compatibleVideo, orphanVideo),
                List.of(compatibleImage, orphanImage), fastEvidence,
                Optional.of(tuple.tuple().orElseThrow()), 1L, "complete");
        RawCatalog catalog = new RawCatalog(device(), List.of(facts),
                new ConcurrentCameraFacts(false, List.of()), List.of(), "signature");

        Snapshot merged = CameraCapabilitySnapshotMapper.merge(
                new FastSnapshot(catalog, pipelineId, List.of(camera), List.of(cameraId)),
                Optional.empty());
        assertEquals(90, merged.cameras().get(0).sensorOrientationDegrees());
        PipelineEvidence persisted = merged.cameras().get(0).codecs().get(0)
                .pipelines().get(0);
        CandidateKey compatibleVideoKey = CandidateKey.forVideo(
                cameraId, VideoCodec.H264, pipelineId, compatibleVideo);
        CandidateKey orphanVideoKey = CandidateKey.forVideo(
                cameraId, VideoCodec.H264, pipelineId, orphanVideo);
        CandidateKey compatibleImageKey = CandidateKey.forImage(
                cameraId, VideoCodec.H264, pipelineId, compatibleImage);
        CandidateKey orphanImageKey = CandidateKey.forImage(
                cameraId, VideoCodec.H264, pipelineId, orphanImage);

        assertTrue(persisted.rawFastCandidates().contains(compatibleVideoKey));
        assertFalse(persisted.rawFastCandidates().contains(orphanVideoKey));
        assertTrue(persisted.rawFastCandidates().containsAll(
                List.of(compatibleImageKey, orphanImageKey)));
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                persisted.outcome(compatibleVideoKey));
        assertEquals(VerificationOutcome.UNKNOWN,
                persisted.outcome(compatibleImageKey));
        assertEquals(VerificationOutcome.UNKNOWN, persisted.outcome(orphanImageKey));
    }

    @Test void reusableSelectionRequiresVerifiedTupleOnly() {
        CandidateKey video = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, VIDEO);
        CandidateKey fallbackVideo = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, FALLBACK_VIDEO);
        CandidateKey image = CandidateKey.forImage(
                CAMERA, VideoCodec.H264, PIPELINE, IMAGE);
        CandidateKey tuple = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE, TUPLE);
        List<CandidateEvidence> selectedFacts = List.of(
                new CandidateEvidence(video, VerificationOutcome.VERIFIED_PASS),
                new CandidateEvidence(image, VerificationOutcome.VERIFIED_PASS),
                new CandidateEvidence(tuple, VerificationOutcome.VERIFIED_PASS));
        PipelineEvidence incomplete = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, List.of(video, fallbackVideo, image, tuple),
                selectedFacts);
        CodecSnapshot incompleteCodec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(incomplete), Optional.empty());
        Snapshot incompleteSnapshot = Snapshot.current("hardware", List.of(CAMERA), List.of(
                new CameraSnapshot(CAMERA, "camera", List.of(incompleteCodec), Optional.empty(),
                        Optional.of(selection(TUPLE)))));

        assertTrue(CameraCapabilitySnapshotMapper.hasVerifiedSelectionForEveryCamera(
                incompleteSnapshot));
        assertTrue(CameraCapabilitySnapshotMapper.hasVerifiedSelectionForMainCamera(
                incompleteSnapshot));

        List<CandidateEvidence> completeFacts = new java.util.ArrayList<>(selectedFacts);
        completeFacts.add(new CandidateEvidence(
                fallbackVideo, VerificationOutcome.DEFINITIVE_UNSUPPORTED));
        PipelineEvidence complete = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, List.of(video, fallbackVideo, image, tuple),
                completeFacts);
        CodecSnapshot completeCodec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(complete), Optional.empty());
        Snapshot completeSnapshot = Snapshot.current("hardware", List.of(CAMERA), List.of(
                new CameraSnapshot(CAMERA, "camera", List.of(completeCodec), Optional.empty(),
                        Optional.of(selection(TUPLE)))));

        assertTrue(CameraCapabilitySnapshotMapper.hasVerifiedSelectionForEveryCamera(
                completeSnapshot));
    }

    @Test void reusableMainCameraDoesNotRequireAuxiliarySelectedRecordingProfile() {
        CameraId auxiliary = new CameraId("1");
        Snapshot snapshot = Snapshot.current("hardware", List.of(CAMERA, auxiliary), List.of(
                cameraSnapshot(CAMERA, Optional.of(selection(TUPLE))),
                cameraSnapshot(auxiliary, Optional.empty())));

        assertTrue(CameraCapabilitySnapshotMapper.hasVerifiedSelectionForMainCamera(snapshot));
        assertFalse(CameraCapabilitySnapshotMapper.hasVerifiedSelectionForEveryCamera(snapshot));

        Snapshot reusable = snapshot.withInitializationState(InitializationState.READY_REUSABLE);
        Snapshot restored = CameraCapabilitySnapshotMapper.withSelectedRecordingProfile(
                reusable, auxiliary, Optional.empty());
        assertEquals(InitializationState.READY_REUSABLE, restored.initializationState());
    }
    @Test void exhaustiveVerificationRequiresFactForEveryRawCandidate() {
        CandidateKey tuple = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE, TUPLE);
        CandidateKey image = CandidateKey.forImage(
                CAMERA, VideoCodec.H264, PIPELINE, IMAGE);
        List<CandidateKey> raw = List.of(tuple, image);
        PipelineEvidence complete = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, raw, List.of(
                        new CandidateEvidence(tuple, VerificationOutcome.VERIFIED_PASS),
                        new CandidateEvidence(image, VerificationOutcome.VERIFIED_PASS)));
        PipelineEvidence unknown = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, raw, List.of(
                        new CandidateEvidence(tuple, VerificationOutcome.VERIFIED_PASS)));
        PipelineEvidence failed = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, raw, List.of(
                        new CandidateEvidence(tuple, VerificationOutcome.VERIFIED_PASS),
                        new CandidateEvidence(image,
                                VerificationOutcome.DEFINITIVE_UNSUPPORTED)));

        assertTrue(CameraCapabilitySnapshotMapper.hasExhaustiveVerifiedCapabilities(
                selectedSnapshot(complete)));
        assertFalse(CameraCapabilitySnapshotMapper.hasExhaustiveVerifiedCapabilities(
                selectedSnapshot(unknown)));
        assertTrue(CameraCapabilitySnapshotMapper.hasExhaustiveVerifiedCapabilities(
                selectedSnapshot(failed)));
    }

    @Test void missingVerifiedOptionConflictsWithPersistedCoverage() {
        CandidateKey tuple = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE, TUPLE);
        PipelineEvidence missingImage = new PipelineEvidence(
                CAMERA, VideoCodec.H264, PIPELINE, PipelineAvailability.AVAILABLE,
                List.of(tuple), List.of(
                new CandidateEvidence(tuple, VerificationOutcome.VERIFIED_PASS)));
        PipelineVerificationCoverage expected = new PipelineVerificationCoverage(1, 1, 1, 1);

        assertThrows(IllegalArgumentException.class,
                () -> selectedSnapshot(missingImage, expected));
    }

    @Test void withSelectedRecordingProfileRestoresOrClearsOnlyTargetCamera() {
        CameraId otherCamera = new CameraId("1");
        SelectedRecordingProfile original = selection(TUPLE);
        SelectedRecordingProfile replacement = selection(FALLBACK_TUPLE);
        SelectedRecordingProfile otherSelection = selection(TUPLE);
        Snapshot snapshot = Snapshot.current("hardware", List.of(CAMERA, otherCamera), List.of(
                cameraSnapshot(CAMERA, Optional.of(replacement)),
                cameraSnapshot(otherCamera, Optional.of(otherSelection))));

        Snapshot restored = CameraCapabilitySnapshotMapper.withSelectedRecordingProfile(
                snapshot, CAMERA, Optional.of(original));

        assertEquals(Optional.of(original), restored.cameras().get(0).selectedRecordingProfile());
        assertEquals(Optional.of(otherSelection), restored.cameras().get(1).selectedRecordingProfile());

        Snapshot cleared = CameraCapabilitySnapshotMapper.withSelectedRecordingProfile(
                restored, CAMERA, Optional.empty());

        assertTrue(cleared.cameras().get(0).selectedRecordingProfile().isEmpty());
        assertEquals(Optional.of(otherSelection), cleared.cameras().get(1).selectedRecordingProfile());
    }

    private static FastSnapshot fastSnapshot(PipelineEvidence evidence) {
        CameraFacts facts = cameraFacts();
        CameraFastSnapshot camera = new CameraFastSnapshot(facts, List.of(VIDEO),
                List.of(IMAGE), evidence, Optional.of(TUPLE), 12, "complete");
        RawCatalog catalog = new RawCatalog(device(), List.of(facts),
                new ConcurrentCameraFacts(true, List.of()), List.of(), "raw-hardware-input");
        return new FastSnapshot(catalog, PIPELINE, List.of(camera), List.of(CAMERA));
    }

    private static Snapshot selectedSnapshot(PipelineEvidence evidence) {
        return selectedSnapshot(evidence, coverage(evidence));
    }

    private static Snapshot selectedSnapshot(PipelineEvidence evidence,
            PipelineVerificationCoverage coverage) {
        PipelineEvidence other = reidentify(evidence, PIPELINE_B);
        PipelineComparison comparison = PipelineComparison.compare(Set.of(TUPLE),
                PipelineComparisonInput.withoutPerformance(evidence, true),
                PipelineComparisonInput.withoutPerformance(other, true));
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(evidence, other), Optional.of(
                PipelineComparisonSnapshot.withCoverage(comparison, coverage, coverage)));
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-signature",
                List.of(codec), Optional.empty(), Optional.of(selection(TUPLE)));
        return Snapshot.current("hardware-signature", List.of(CAMERA), List.of(camera));
    }

    private static PipelineVerificationCoverage coverage(PipelineEvidence evidence) {
        int images = Math.toIntExact(evidence.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE).count());
        int tuples = Math.toIntExact(evidence.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE).count());
        return new PipelineVerificationCoverage(images, images, tuples, tuples);
    }

    private static PipelineEvidence reidentify(PipelineEvidence source,
            VerificationPipelineId pipelineId) {
        java.util.List<CandidateKey> raw = source.rawFastCandidates().stream()
                .map(candidate -> reidentify(candidate, pipelineId)).toList();
        java.util.List<CandidateEvidence> facts = source.candidateEvidence().entrySet().stream()
                .map(entry -> new CandidateEvidence(
                        reidentify(entry.getKey(), pipelineId), entry.getValue())).toList();
        return new PipelineEvidence(source.cameraId(), source.codec(), pipelineId,
                source.availability(), raw, facts);
    }

    private static CandidateKey reidentify(CandidateKey candidate,
            VerificationPipelineId pipelineId) {
        return switch (candidate.kind()) {
            case VIDEO -> CandidateKey.forVideo(candidate.cameraId(), candidate.codec(),
                    pipelineId, candidate.videoMode().orElseThrow());
            case IMAGE -> CandidateKey.forImage(candidate.cameraId(), candidate.codec(),
                    pipelineId, candidate.imageMode().orElseThrow());
            case TUPLE -> CandidateKey.forTuple(candidate.cameraId(), candidate.codec(),
                    pipelineId, candidate.tuple().orElseThrow());
        };
    }

    private static Snapshot snapshot(PipelineEvidence evidence) {
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(evidence), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "old-camera-signature",
                List.of(codec), Optional.empty(), Optional.empty());
        return Snapshot.current("old-hardware-signature", List.of(CAMERA), List.of(camera));
    }

    private static CameraSnapshot cameraSnapshot(
            CameraId cameraId, Optional<SelectedRecordingProfile> selectedRecordingProfile) {
        CandidateKey primary = CandidateKey.forTuple(
                cameraId, VideoCodec.H264, PIPELINE, TUPLE);
        CandidateKey fallback = CandidateKey.forTuple(
                cameraId, VideoCodec.H264, PIPELINE, FALLBACK_TUPLE);
        PipelineEvidence pipeline = new PipelineEvidence(cameraId, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, List.of(primary, fallback), List.of(
                        new CandidateEvidence(primary, VerificationOutcome.VERIFIED_PASS),
                        new CandidateEvidence(fallback, VerificationOutcome.VERIFIED_PASS)));
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        return new CameraSnapshot(cameraId, "camera-" + cameraId.value(),
                List.of(codec), Optional.empty(), selectedRecordingProfile);
    }

    private static SelectedRecordingProfile selection(CaptureModeTuple tuple) {
        return new SelectedRecordingProfile(VideoCodec.H264, PIPELINE, tuple);
    }

    private static PipelineEvidence evidence(List<CandidateEvidence> facts) {
        return new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE,
                List.of(CandidateKey.forTuple(
                        CAMERA, VideoCodec.H264, PIPELINE, TUPLE)), facts);
    }

    private static CameraFacts cameraFacts() {
        return new CameraFacts(CAMERA, 1, 1, 90, List.of(), List.of(), true,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static VideoMode video(int width, int height) {
        return new VideoMode(new StandardResolution(StandardResolutionLabel.FHD,
                new CameraResolution(width, height)), 30);
    }

    private static ImageMode image(int width, int height) {
        return new ImageMode(new StandardResolution(StandardResolutionLabel.FHD,
                new CameraResolution(width, height)));
    }

    private static DeviceIdentity device() {
        return new DeviceIdentity(36, "manufacturer", "brand", "device", "product",
                "model", "hardware", "board", "bootloader", "build", "incremental",
                "patch", "fingerprint");
    }
}