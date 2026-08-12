package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraModeOrder;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.RequestedCeilingFallback;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ResolveCameraRuntimeSelectionUseCase {
    private final SelectCameraPipelineUseCase pipelineSelector;

    public ResolveCameraRuntimeSelectionUseCase() {
        this(new SelectCameraPipelineUseCase());
    }

    public ResolveCameraRuntimeSelectionUseCase(SelectCameraPipelineUseCase pipelineSelector) {
        this.pipelineSelector = Objects.requireNonNull(pipelineSelector, "pipelineSelector");
    }

    public Optional<CandidateKey> defaultCandidate(Snapshot snapshot, CameraId cameraId) {
        return defaultCandidates(snapshot, cameraId).stream().findFirst();
    }

    public List<CandidateKey> defaultCandidates(Snapshot snapshot, CameraId cameraId) {
        List<CandidateKey> candidates = new ArrayList<>();
        for (PipelineEvidence pipeline : pipelines(snapshot, cameraId)) {
            bestTuple(pipeline).ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    public Optional<CandidateKey> requestedCandidate(Snapshot snapshot, CameraId cameraId,
            String videoResolutionId, int frameRate, String imageResolutionId) {
        return requestedCandidates(snapshot, cameraId, videoResolutionId, frameRate,
                imageResolutionId).stream().findFirst();
    }

    public List<CandidateKey> requestedCandidates(Snapshot snapshot, CameraId cameraId,
            String videoResolutionId, int frameRate, String imageResolutionId) {
        List<CandidateKey> candidates = new ArrayList<>();
        for (PipelineEvidence pipeline : pipelines(snapshot, cameraId)) {
            requestedCandidate(pipeline, cameraId, videoResolutionId, frameRate, imageResolutionId)
                    .ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    public Optional<CandidateKey> requestedProfileCandidate(
            Snapshot snapshot, CameraId cameraId, CaptureModeTuple requestedTuple) {
        Objects.requireNonNull(requestedTuple, "requestedTuple");
        for (PipelineEvidence pipeline : pipelines(snapshot, cameraId)) {
            CandidateKey requested = CandidateKey.forTuple(cameraId, pipeline.codec(),
                    pipeline.verificationPipelineId(), requestedTuple);
            if (!RequestedCeilingFallback.orderedCandidates(
                    requested, pipeline.effectiveCandidates()).isEmpty()) {
                return Optional.of(requested);
            }
        }
        return Optional.empty();
    }
    public List<CameraId> cameraOrder(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (!snapshot.cameraOrderOverride().isEmpty()) return snapshot.cameraOrderOverride();
        List<CameraId> cameraIds = new ArrayList<>();
        for (CameraSnapshot camera : snapshot.cameras()) cameraIds.add(camera.cameraId());
        return List.copyOf(cameraIds);
    }

    public Optional<SelectCameraPipelineUseCase.Decision> selectedPipelineDecision(
            CameraSnapshot camera) {
        return pipelineSelector.decision(camera);
    }

    public Optional<VerificationPipelineId> selectedPipeline(CameraSnapshot camera) {
        return pipelineSelector.selectedPipeline(camera);
    }

    private List<PipelineEvidence> pipelines(Snapshot snapshot, CameraId cameraId) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(cameraId, "cameraId");
        return snapshot.cameras().stream()
                .filter(value -> value.cameraId().equals(cameraId))
                .findFirst().map(pipelineSelector::ordered).orElseGet(List::of);
    }

    private static Optional<CandidateKey> requestedCandidate(PipelineEvidence pipeline,
            CameraId cameraId, String videoResolutionId, int frameRate, String imageResolutionId) {
        List<CandidateKey> candidates = List.copyOf(pipeline.effectiveCandidates());
        StandardResolution video = resolution(
                candidates, videoResolutionId, CandidateKey.Kind.VIDEO).orElse(null);
        StandardResolution image = resolution(
                candidates, imageResolutionId, CandidateKey.Kind.IMAGE).orElse(null);
        if (video == null || image == null) return Optional.empty();
        int requestedFrameRate = frameRate > 0 ? frameRate : candidates.stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.VIDEO)
                .map(candidate -> candidate.videoMode().orElseThrow())
                .filter(value -> value.resolution().equals(video))
                .mapToInt(VideoMode::framesPerSecond).max().orElse(0);
        if (requestedFrameRate <= 0) return Optional.empty();
        CaptureModeTuple tuple = new CaptureModeTuple(
                new VideoMode(video, requestedFrameRate), new ImageMode(image));
        return Optional.of(CandidateKey.forTuple(cameraId, pipeline.codec(),
                pipeline.verificationPipelineId(), tuple));
    }

    private static Optional<CandidateKey> bestTuple(PipelineEvidence pipeline) {
        List<CandidateKey> tuples = tuples(pipeline);
        if (tuples.isEmpty()) return Optional.empty();
        CandidateKey best = tuples.stream()
                .filter(value -> atOrBelowFhd(value.tuple().orElseThrow()))
                .max((left, right) -> CameraModeOrder.tuples().compare(
                        left.tuple().orElseThrow(), right.tuple().orElseThrow()))
                .orElseGet(() -> tuples.stream()
                        .min((left, right) -> CameraModeOrder.tuples().compare(
                                left.tuple().orElseThrow(), right.tuple().orElseThrow()))
                        .orElseThrow());
        return Optional.of(best);
    }

    private static List<CandidateKey> tuples(PipelineEvidence pipeline) {
        List<CandidateKey> result = new ArrayList<>();
        for (CandidateKey candidate : pipeline.effectiveCandidates()) {
            if (candidate.kind() == CandidateKey.Kind.TUPLE) result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static Optional<StandardResolution> resolution(
            List<CandidateKey> candidates, String id, CandidateKey.Kind kind) {
        if (id == null || id.isBlank()) return Optional.empty();
        return candidates.stream()
                .filter(candidate -> candidate.kind() == kind)
                .map(candidate -> kind == CandidateKey.Kind.VIDEO
                        ? candidate.videoMode().orElseThrow().resolution()
                        : candidate.imageMode().orElseThrow().resolution())
                .filter(resolution -> resolution.label().name().equals(id))
                .max(CameraModeOrder.resolutions());
    }

    private static boolean atOrBelowFhd(CaptureModeTuple tuple) {
        return tuple.videoMode().resolution().label().compareTo(StandardResolutionLabel.FHD) <= 0
                && tuple.imageMode().resolution().label()
                        .compareTo(StandardResolutionLabel.FHD) <= 0;
    }
}