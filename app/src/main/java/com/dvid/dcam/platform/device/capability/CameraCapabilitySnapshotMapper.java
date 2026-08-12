package com.dvid.dcam.platform.device.capability;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineComparisonSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineVerificationCoverage;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Freshness;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineIdentity;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedPipeline;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.UnsupportedCodecReason;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.RawCatalog;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.CameraFastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.FastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.CameraCapabilitySnapshotUpdates;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.platform.device.capability.probe.egl.EglFanOutFastProbe;
import com.dvid.dcam.platform.device.capability.probe.nativesharing.NativeSurfaceSharingFastProbe;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class CameraCapabilitySnapshotMapper {
    private CameraCapabilitySnapshotMapper() {}

    static Freshness freshness(RawCatalog catalog) {
        String hardwareSignature = sha256(catalog.hardwareSignatureInput());
        TreeMap<CameraId, String> cameraSignatures = new TreeMap<>();
        TreeSet<PipelineIdentity> identities = new TreeSet<>();
        for (CameraFacts camera : catalog.cameras()) {
            CameraId cameraId = camera.cameraId();
            cameraSignatures.put(cameraId, sha256(
                    catalog.hardwareSignatureInput() + "|camera=" + camera));
            identities.add(new PipelineIdentity(cameraId, VideoCodec.H264,
                    NativeSurfaceSharingFastProbe.PIPELINE_ID));
            identities.add(new PipelineIdentity(cameraId, VideoCodec.H264,
                    EglFanOutFastProbe.PIPELINE_ID));
        }
        return new Freshness(hardwareSignature, cameraSignatures, identities);
    }

    static boolean isFresh(Snapshot snapshot, Freshness freshness) {
        if (!snapshot.hardwareSignature().equals(freshness.hardwareSignature())) return false;
        if (snapshot.cameras().size() != freshness.cameraHardwareSignatures().size()) return false;
        for (CameraSnapshot camera : snapshot.cameras()) {
            String expected = freshness.cameraHardwareSignatures().get(camera.cameraId());
            if (!camera.hardwareSignature().equals(expected)) return false;
        }
        return true;
    }
    static Snapshot merge(FastSnapshot fast, Optional<Snapshot> previous) {
        Freshness freshness = freshness(fast.rawCatalog());
        Map<CameraId, CameraSnapshot> oldCameras = new HashMap<>();
        previous.ifPresent(snapshot -> snapshot.cameras().forEach(
                camera -> oldCameras.put(camera.cameraId(), camera)));
        List<CameraSnapshot> cameras = new ArrayList<>();
        for (CameraFastSnapshot camera : fast.cameras()) {
            CameraId cameraId = camera.cameraFacts().cameraId();
            CameraSnapshot oldCamera = oldCameras.get(cameraId);
            List<PipelineEvidence> pipelines = mergePipelines(
                    normalizeFastEvidence(camera), oldCamera);
            CodecSnapshot h264 = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                    Optional.empty(), pipelines, validComparison(oldCamera, pipelines));
            CodecSnapshot h265 = oldUnsupportedH265(oldCamera).orElseGet(() ->
                    new CodecSnapshot(VideoCodec.H265, CodecState.UNSUPPORTED,
                            Optional.of(UnsupportedCodecReason.NOT_IMPLEMENTED),
                            List.of(), Optional.empty()));
            Optional<SelectedPipeline> selectedPipeline =
                    validSelectedPipeline(oldCamera, pipelines);
            Optional<SelectedRecordingProfile> recordingProfile =
                    validRecordingProfile(oldCamera, pipelines)
                            .filter(profile -> selectedPipeline.isEmpty()
                                    || selectedPipeline.orElseThrow().pipelineId().equals(
                                    profile.verificationPipelineId()));
            cameras.add(new CameraSnapshot(cameraId,
                    freshness.cameraHardwareSignatures().get(cameraId),
                    List.of(h264, h265), selectedPipeline, recordingProfile,
                    sensorOrientationDegrees(camera.cameraFacts())));
        }
        return Snapshot.current(freshness.hardwareSignature(), fast.autoCameraOrder(), cameras);
    }

    static int sensorOrientationDegrees(CameraFacts facts) {
        Integer sensorOrientationDegrees = facts.sensorOrientationDegrees();
        if (sensorOrientationDegrees == null) {
            throw new IllegalStateException("camera sensor orientation unavailable cameraId="
                    + facts.cameraId().value());
        }
        return sensorOrientationDegrees;
    }

    static Snapshot withSelectedRecordingProfile(Snapshot snapshot, CameraId cameraId,
            Optional<SelectedRecordingProfile> selection) {
        return CameraCapabilitySnapshotUpdates.withSelectedRecordingProfile(snapshot, cameraId, selection);
    }

    static boolean hasVerifiedSelectionForEveryCamera(Snapshot snapshot) {
        if (snapshot.cameras().isEmpty()) return false;
        for (CameraSnapshot camera : snapshot.cameras()) {
            if (!hasVerifiedTuple(camera)) return false;
        }
        return true;
    }

    static boolean hasExhaustiveVerifiedCapabilities(Snapshot snapshot) {
        if (snapshot.cameras().isEmpty()) return false;
        for (CameraSnapshot camera : snapshot.cameras()) {
            boolean hasVerifiedTuple = false;
            boolean hasH264 = false;
            for (CodecSnapshot codec : camera.codecs()) {
                if (codec.codec() != VideoCodec.H264) continue;
                hasH264 = true;
                if (codec.comparison().isEmpty()) return false;
                PipelineComparisonSnapshot comparison = codec.comparison().orElseThrow();
                if (comparison.pipelineACoverage().isEmpty()
                        || comparison.pipelineBCoverage().isEmpty()) {
                    return false;
                }
                for (PipelineEvidence pipeline : codec.pipelines()) {
                    PipelineVerificationCoverage coverage;
                    if (pipeline.verificationPipelineId().equals(
                            comparison.comparison().pipelineA())) {
                        coverage = comparison.pipelineACoverage().orElseThrow();
                    } else if (pipeline.verificationPipelineId().equals(
                            comparison.comparison().pipelineB())) {
                        coverage = comparison.pipelineBCoverage().orElseThrow();
                    } else {
                        return false;
                    }
                    if (!coverageMatches(pipeline, coverage)) return false;
                    for (var fact : pipeline.candidateEvidence().entrySet()) {
                        if (fact.getKey().kind() == CandidateKey.Kind.TUPLE
                                && fact.getValue() == VerificationOutcome.VERIFIED_PASS) {
                            hasVerifiedTuple = true;
                        }
                    }
                }
            }
            if (!hasH264 || !hasVerifiedTuple) return false;
        }
        return hasVerifiedSelectionForEveryCamera(snapshot);
    }

    private static boolean coverageMatches(PipelineEvidence pipeline,
            PipelineVerificationCoverage coverage) {
        int plannedImages = Math.toIntExact(pipeline.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE)
                .count());
        int plannedTuples = Math.toIntExact(pipeline.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .count());
        int verifiedImages = Math.toIntExact(pipeline.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE)
                .filter(candidate -> pipeline.outcome(candidate).isTerminalEvidence())
                .count());
        int verifiedTuples = Math.toIntExact(pipeline.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .filter(candidate -> pipeline.outcome(candidate).isTerminalEvidence())
                .count());
        if (coverage.plannedImageCount() != plannedImages
                || coverage.plannedTupleCount() != plannedTuples
                || coverage.verifiedImageCount() != verifiedImages
                || coverage.verifiedTupleCount() != verifiedTuples) {
            return false;
        }
        return pipeline.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE
                        || candidate.kind() == CandidateKey.Kind.TUPLE)
                .allMatch(candidate -> pipeline.outcome(candidate).isTerminalEvidence());
    }

    static boolean hasVerifiedSelectionForMainCamera(Snapshot snapshot) {
        if (snapshot.cameras().isEmpty()) return false;
        CameraId mainId = snapshot.cameraOrderOverride().isEmpty()
                ? snapshot.cameras().get(0).cameraId()
                : snapshot.cameraOrderOverride().get(0);
        return snapshot.cameras().stream().filter(camera -> camera.cameraId().equals(mainId))
                .findFirst().map(CameraCapabilitySnapshotMapper::hasVerifiedTuple).orElse(false);
    }

    private static boolean hasVerifiedTuple(CameraSnapshot camera) {
        if (camera.selectedRecordingProfile().isEmpty()) return false;
        SelectedRecordingProfile selection = camera.selectedRecordingProfile().orElseThrow();
        for (CodecSnapshot codec : camera.codecs()) {
            if (codec.codec() != selection.codec()) continue;
            for (PipelineEvidence pipeline : codec.pipelines()) {
                if (!pipeline.verificationPipelineId().equals(selection.verificationPipelineId())) {
                    continue;
                }
                CandidateKey tuple = CandidateKey.forTuple(camera.cameraId(), selection.codec(),
                        selection.verificationPipelineId(), selection.tuple());
                return pipeline.outcome(tuple) == VerificationOutcome.VERIFIED_PASS
                        && pipeline.isEffective(tuple);
            }
        }
        return false;
    }


    static boolean hasUsableTuple(Snapshot snapshot) {
        for (CameraSnapshot camera : snapshot.cameras()) {
            if (hasUsableTuple(camera)) return true;
        }
        return false;
    }

    static boolean hasUsableTupleForEveryCamera(Snapshot snapshot) {
        if (snapshot.cameras().isEmpty()) return false;
        for (CameraSnapshot camera : snapshot.cameras()) {
            if (!hasUsableTuple(camera)) return false;
        }
        return true;
    }

    private static boolean hasUsableTuple(CameraSnapshot camera) {
        for (CodecSnapshot codec : camera.codecs()) {
            if (codec.codec() != VideoCodec.H264) continue;
            for (PipelineEvidence pipeline : codec.pipelines()) {
                for (var candidate : pipeline.effectiveCandidates()) {
                    if (candidate.kind()
                            == com.dvid.dcam.feature.device.domain.camera.CandidateKey.Kind.TUPLE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    private static PipelineEvidence normalizeFastEvidence(CameraFastSnapshot camera) {
        PipelineEvidence evidence = camera.evidence();
        if (evidence.availability()
                == com.dvid.dcam.feature.device.domain.camera.PipelineAvailability.UNAVAILABLE) {
            return evidence;
        }
        TreeSet<CandidateKey> raw = new TreeSet<>(evidence.rawFastCandidates());
        TreeSet<VideoMode> compatibleVideos = new TreeSet<>();
        for (CandidateKey candidate : evidence.rawFastCandidates()) {
            if (candidate.kind() == CandidateKey.Kind.TUPLE) {
                compatibleVideos.add(candidate.videoMode().orElseThrow());
            }
        }
        List<CandidateEvidence> staticSoloFacts = new ArrayList<>();
        for (VideoMode video : compatibleVideos) {
            CandidateKey candidate = CandidateKey.forVideo(camera.cameraFacts().cameraId(),
                    VideoCodec.H264, evidence.verificationPipelineId(), video);
            raw.add(candidate);
            staticSoloFacts.add(new CandidateEvidence(
                    candidate, VerificationOutcome.VERIFIED_PASS));
        }
        for (ImageMode image : camera.imageModes()) {
            CandidateKey candidate = CandidateKey.forImage(camera.cameraFacts().cameraId(),
                    VideoCodec.H264, evidence.verificationPipelineId(), image);
            raw.add(candidate);
        }
        return new PipelineEvidence(evidence.cameraId(), evidence.codec(),
                evidence.verificationPipelineId(), evidence.availability(), raw,
                staticSoloFacts);
    }
    private static PipelineEvidence normalizePersistedEvidence(PipelineEvidence evidence) {
        TreeSet<CandidateKey> raw = new TreeSet<>(evidence.rawFastCandidates());
        for (CandidateKey candidate : evidence.rawFastCandidates()) {
            if (candidate.kind() != CandidateKey.Kind.TUPLE) continue;
            var tuple = candidate.tuple().orElseThrow();
            raw.add(CandidateKey.forVideo(evidence.cameraId(), evidence.codec(),
                    evidence.verificationPipelineId(), tuple.videoMode()));
            raw.add(CandidateKey.forImage(evidence.cameraId(), evidence.codec(),
                    evidence.verificationPipelineId(), tuple.imageMode()));
        }
        List<CandidateEvidence> facts = new ArrayList<>();
        evidence.candidateEvidence().forEach((candidate, outcome) ->
                facts.add(new CandidateEvidence(candidate, outcome)));
        return new PipelineEvidence(evidence.cameraId(), evidence.codec(),
                evidence.verificationPipelineId(), evidence.availability(), raw, facts);
    }
    private static List<PipelineEvidence> mergePipelines(
            PipelineEvidence fresh, CameraSnapshot oldCamera) {
        TreeMap<VerificationPipelineId, PipelineEvidence> pipelines = new TreeMap<>();
        if (oldCamera != null) {
            for (CodecSnapshot codec : oldCamera.codecs()) {
                if (codec.codec() != VideoCodec.H264) continue;
                for (PipelineEvidence pipeline : codec.pipelines()) {
                    pipelines.put(pipeline.verificationPipelineId(),
                            normalizePersistedEvidence(pipeline));
                }
            }
        }
        pipelines.put(fresh.verificationPipelineId(), mergeEvidence(
                fresh, pipelines.get(fresh.verificationPipelineId())));
        return List.copyOf(pipelines.values());
    }

    private static PipelineEvidence mergeEvidence(
            PipelineEvidence fresh, PipelineEvidence previous) {
        if (previous == null) return fresh;
        TreeMap<CandidateKey, VerificationOutcome> durable = new TreeMap<>();
        fresh.candidateEvidence().forEach(durable::put);
        previous.candidateEvidence().forEach((candidate, outcome) -> {
            if ((candidate.kind() == CandidateKey.Kind.TUPLE
                    || candidate.kind() == CandidateKey.Kind.IMAGE)
                    && outcome.isTerminalEvidence()
                    && fresh.hasRawScope(candidate)) {
                durable.put(candidate, outcome);
            }
        });
        List<CandidateEvidence> durableFacts = new ArrayList<>();
        durable.forEach((candidate, outcome) ->
                durableFacts.add(new CandidateEvidence(candidate, outcome)));
        return new PipelineEvidence(fresh.cameraId(), fresh.codec(),
                fresh.verificationPipelineId(), fresh.availability(),
                fresh.rawFastCandidates(), durableFacts);
    }
    private static Optional<PipelineComparisonSnapshot> validComparison(
            CameraSnapshot oldCamera, List<PipelineEvidence> pipelines) {
        if (oldCamera == null) return Optional.empty();
        for (CodecSnapshot codec : oldCamera.codecs()) {
            if (codec.codec() != VideoCodec.H264 || codec.comparison().isEmpty()) continue;
            PipelineComparisonSnapshot comparison = codec.comparison().orElseThrow();
            boolean hasA = pipelines.stream().anyMatch(value -> value.verificationPipelineId()
                    .equals(comparison.comparison().pipelineA()));
            boolean hasB = pipelines.stream().anyMatch(value -> value.verificationPipelineId()
                    .equals(comparison.comparison().pipelineB()));
            if (hasA && hasB) return Optional.of(comparison);
        }
        return Optional.empty();
    }
    private static Optional<CodecSnapshot> oldUnsupportedH265(CameraSnapshot camera) {
        if (camera == null) return Optional.empty();
        for (CodecSnapshot codec : camera.codecs()) {
            if (codec.codec() == VideoCodec.H265 && codec.state() == CodecState.UNSUPPORTED) {
                return Optional.of(codec);
            }
        }
        return Optional.empty();
    }

    private static Optional<SelectedPipeline> validSelectedPipeline(
            CameraSnapshot camera, List<PipelineEvidence> pipelines) {
        if (camera == null || camera.selectedPipeline().isEmpty()) return Optional.empty();
        SelectedPipeline selected = camera.selectedPipeline().orElseThrow();
        for (PipelineEvidence pipeline : pipelines) {
            if (pipeline.verificationPipelineId().equals(selected.pipelineId())) {
                return Optional.of(selected);
            }
        }
        return Optional.empty();
    }

    private static Optional<SelectedRecordingProfile> validRecordingProfile(
            CameraSnapshot camera, List<PipelineEvidence> pipelines) {
        if (camera == null || camera.selectedRecordingProfile().isEmpty()) return Optional.empty();
        SelectedRecordingProfile selection = camera.selectedRecordingProfile().orElseThrow();
        for (PipelineEvidence pipeline : pipelines) {
            if (pipeline.codec() == selection.codec()
                    && pipeline.verificationPipelineId().equals(
                    selection.verificationPipelineId())
                    && pipeline.outcome(selection.tuple())
                    == VerificationOutcome.VERIFIED_PASS) {
                return Optional.of(selection);
            }
        }
        return Optional.empty();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
