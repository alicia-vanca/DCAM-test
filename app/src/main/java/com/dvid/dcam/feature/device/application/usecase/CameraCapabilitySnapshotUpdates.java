package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CameraCapabilitySnapshotUpdates {
    private CameraCapabilitySnapshotUpdates() {}

    static PipelineEvidence pipeline(
            Snapshot snapshot,
            CameraId cameraId,
            VideoCodec codec,
            VerificationPipelineId pipelineId) {
        return camera(snapshot, cameraId).flatMap(camera -> codec(camera, codec))
                .flatMap(codecSnapshot -> codecSnapshot.pipelines().stream()
                        .filter(value -> value.verificationPipelineId().equals(pipelineId))
                        .findFirst())
                .orElseThrow(() -> new IllegalArgumentException(
                        "snapshot does not contain requested pipeline"));
    }

    public static Snapshot withEvidence(
            Snapshot snapshot,
            CandidateKey candidate,
            VerificationOutcome outcome) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(outcome, "outcome");
        if (!outcome.isTerminalEvidence()) {
            throw new IllegalArgumentException("only durable evidence may be persisted");
        }
        List<CameraSnapshot> cameras = new ArrayList<>();
        for (CameraSnapshot camera : snapshot.cameras()) {
            if (!camera.cameraId().equals(candidate.cameraId())) {
                cameras.add(camera);
                continue;
            }
            List<CodecSnapshot> codecs = new ArrayList<>();
            for (CodecSnapshot codec : camera.codecs()) {
                if (codec.codec() != candidate.codec()) {
                    codecs.add(codec);
                    continue;
                }
                List<PipelineEvidence> pipelines = new ArrayList<>();
                boolean comparisonInvalidated = false;
                for (PipelineEvidence pipeline : codec.pipelines()) {
                    if (!pipeline.verificationPipelineId()
                            .equals(candidate.verificationPipelineId())) {
                        pipelines.add(pipeline);
                        continue;
                    }
                    boolean[] siblingRemoved = {false};
                    comparisonInvalidated = pipeline.outcome(candidate) != outcome;
                    List<CandidateEvidence> facts = new ArrayList<>();
                    pipeline.candidateEvidence().forEach((key, value) ->
                            facts.add(new CandidateEvidence(key, value)));
                    facts.removeIf(fact -> {
                        if (fact.candidate().equals(candidate)) return true;
                        boolean conflicting = conflictingVerifiedSibling(fact, candidate, outcome);
                        if (conflicting) siblingRemoved[0] = true;
                        return conflicting;
                    });
                    comparisonInvalidated |= siblingRemoved[0];
                    facts.add(new CandidateEvidence(candidate, outcome));
                    pipelines.add(new PipelineEvidence(
                            pipeline.cameraId(), pipeline.codec(),
                            pipeline.verificationPipelineId(), pipeline.availability(),
                            pipeline.rawFastCandidates(), facts));
                }
                codecs.add(new CodecSnapshot(codec.codec(), codec.state(),
                        codec.unsupportedReason(), pipelines,
                        comparisonInvalidated ? Optional.empty() : codec.comparison()));
            }
            cameras.add(new CameraSnapshot(camera.cameraId(), camera.hardwareSignature(),
                    codecs, camera.selectedPipeline(), camera.selectedRecordingProfile(),
                    camera.sensorOrientationDegrees()));
        }
        return new Snapshot(snapshot.format(), snapshot.initializationState(),
                snapshot.hardwareSignature(), snapshot.cameraOrderOverride(), cameras);
    }

    public static Snapshot withStandaloneImageEvidence(
            Snapshot snapshot,
            CandidateKey requested,
            VerificationOutcome outcome) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(outcome, "outcome");
        CandidateKey image = CandidateKey.forImage(requested.cameraId(), requested.codec(),
                requested.verificationPipelineId(), requested.imageMode().orElseThrow());
        return withEvidence(snapshot, image, outcome);
    }
    private static boolean conflictingVerifiedSibling(CandidateEvidence fact,
            CandidateKey selected, VerificationOutcome outcome) {
        if (outcome != VerificationOutcome.VERIFIED_PASS
                || selected.kind() != CandidateKey.Kind.TUPLE
                || fact.outcome() != VerificationOutcome.VERIFIED_PASS
                || fact.candidate().kind() != CandidateKey.Kind.TUPLE) {
            return false;
        }
        CandidateKey existing = fact.candidate();
        if (!existing.cameraId().equals(selected.cameraId())
                || existing.codec() != selected.codec()
                || !existing.verificationPipelineId().equals(
                        selected.verificationPipelineId())) {
            return false;
        }
        var selectedVideo = selected.videoMode().orElseThrow().resolution();
        var existingVideo = existing.videoMode().orElseThrow().resolution();
        boolean conflictingVideo = selectedVideo.label() == existingVideo.label()
                && !selectedVideo.actual().equals(existingVideo.actual());
        var selectedImage = selected.imageMode().orElseThrow().resolution();
        var existingImage = existing.imageMode().orElseThrow().resolution();
        boolean conflictingImage = selectedImage.label() == existingImage.label()
                && !selectedImage.actual().equals(existingImage.actual());
        return conflictingVideo || conflictingImage;
    }

    public static Snapshot withSelectedRecordingProfile(
            Snapshot snapshot,
            CameraId cameraId,
            Optional<CameraCapabilityStore.SelectedRecordingProfile> selection) {
        return withSelectedRecordingProfile(snapshot, cameraId, selection, false);
    }

    static Snapshot withRuntimeVerifiedRecordingProfile(
            Snapshot snapshot,
            CameraId cameraId,
            Optional<CameraCapabilityStore.SelectedRecordingProfile> selection) {
        return withSelectedRecordingProfile(snapshot, cameraId, selection, true);
    }

    private static Snapshot withSelectedRecordingProfile(
            Snapshot snapshot,
            CameraId cameraId,
            Optional<CameraCapabilityStore.SelectedRecordingProfile> selection,
            boolean fixedPipelineTransition) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(cameraId, "cameraId");
        Objects.requireNonNull(selection, "selection");
        List<CameraSnapshot> cameras = new ArrayList<>();
        boolean updated = false;
        for (CameraSnapshot camera : snapshot.cameras()) {
            if (camera.cameraId().equals(cameraId)) {
                updated = true;
                Optional<CameraCapabilityStore.SelectedPipeline> selectedPipeline =
                        camera.selectedPipeline();
                if (selection.isPresent()) {
                    VerificationPipelineId profilePipeline = selection.orElseThrow()
                            .verificationPipelineId();
                    boolean fixedMismatch = selectedPipeline.isPresent()
                            && selectedPipeline.orElseThrow().mode()
                            == CameraCapabilityStore.PipelineSelectionMode.FIXED
                            && !profilePipeline.equals(
                            selectedPipeline.orElseThrow().pipelineId());
                    if (fixedMismatch && !fixedPipelineTransition) {
                        throw new IllegalArgumentException(
                                "recording profile does not match fixed pipeline");
                    }
                    if (fixedMismatch) {
                        selectedPipeline = Optional.of(
                                CameraCapabilityStore.SelectedPipeline.fixed(profilePipeline));
                    } else if (selectedPipeline.isEmpty() || !profilePipeline.equals(
                            selectedPipeline.orElseThrow().pipelineId())) {
                        selectedPipeline = Optional.of(
                                CameraCapabilityStore.SelectedPipeline.autoProfileVerified(
                                        profilePipeline));
                    }
                }
                cameras.add(new CameraSnapshot(camera.cameraId(), camera.hardwareSignature(),
                        camera.codecs(), selectedPipeline, selection,
                        camera.sensorOrientationDegrees()));
            } else {
                cameras.add(camera);
            }
        }
        if (!updated) throw new IllegalArgumentException("snapshot does not contain camera");
        CameraId mainId = snapshot.cameraOrderOverride().isEmpty()
                ? snapshot.cameras().get(0).cameraId()
                : snapshot.cameraOrderOverride().get(0);
        boolean mainSelectionPresent = cameras.stream()
                .filter(camera -> camera.cameraId().equals(mainId))
                .findFirst().flatMap(CameraSnapshot::selectedRecordingProfile).isPresent();
        InitializationState state = mainSelectionPresent
                ? snapshot.initializationState() : InitializationState.INCOMPLETE;
        return new Snapshot(snapshot.format(), state, snapshot.hardwareSignature(),
                snapshot.cameraOrderOverride(), cameras);
    }


    static Optional<CameraCapabilityStore.SelectedRecordingProfile> selectedRecordingProfile(
            Snapshot snapshot, CameraId cameraId) {
        return camera(snapshot, cameraId).flatMap(CameraSnapshot::selectedRecordingProfile);
    }

    static int sensorOrientationDegrees(Snapshot snapshot, CameraId cameraId) {
        return camera(snapshot, cameraId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "snapshot does not contain camera"))
                .sensorOrientationDegrees();
    }

    private static Optional<CameraSnapshot> camera(Snapshot snapshot, CameraId cameraId) {
        return snapshot.cameras().stream()
                .filter(value -> value.cameraId().equals(cameraId))
                .findFirst();
    }

    private static Optional<CodecSnapshot> codec(CameraSnapshot camera, VideoCodec codec) {
        return camera.codecs().stream()
                .filter(value -> value.codec() == codec)
                .findFirst();
    }
}