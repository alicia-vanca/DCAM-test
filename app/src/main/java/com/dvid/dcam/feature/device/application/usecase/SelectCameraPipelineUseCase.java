package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineDecisionReason;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineDecisionStatus;
import com.dvid.dcam.feature.device.domain.camera.CameraModeOrder;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparison;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class SelectCameraPipelineUseCase {
    public record Decision(
            PipelineEvidence pipeline,
            PipelineDecisionStatus status,
            PipelineDecisionReason reason) {
        public Decision {
            Objects.requireNonNull(pipeline, "pipeline");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
        }
    }

    private final List<VerificationPipelineId> expectedFastPipelines;
    private final Optional<VerificationPipelineId> fastTiePreference;

    public SelectCameraPipelineUseCase() {
        this(List.of(), Optional.empty());
    }

    public SelectCameraPipelineUseCase(
            Collection<VerificationPipelineId> expectedFastPipelines) {
        this(expectedFastPipelines, Optional.empty());
    }

    public SelectCameraPipelineUseCase(
            Collection<VerificationPipelineId> expectedFastPipelines,
            VerificationPipelineId fastTiePreference) {
        this(expectedFastPipelines, Optional.of(
                Objects.requireNonNull(fastTiePreference, "fastTiePreference")));
    }

    @SuppressWarnings("java:S1144") // Called by the public constructors through this(...).
    private SelectCameraPipelineUseCase(
            Collection<VerificationPipelineId> expectedFastPipelines,
            Optional<VerificationPipelineId> fastTiePreference) {
        this.expectedFastPipelines = List.copyOf(
                Objects.requireNonNull(expectedFastPipelines, "expectedFastPipelines"));
        this.fastTiePreference = Objects.requireNonNull(
                fastTiePreference, "fastTiePreference");
        if (this.fastTiePreference.isPresent()
                && !this.expectedFastPipelines.contains(
                this.fastTiePreference.orElseThrow())) {
            throw new IllegalArgumentException(
                    "fast tie preference must be an expected pipeline");
        }
    }

    public Optional<PipelineEvidence> select(CameraSnapshot camera) {
        return ordered(camera).stream().findFirst();
    }

    public List<PipelineEvidence> ordered(CameraSnapshot camera) {
        Objects.requireNonNull(camera, "camera");
        CodecSnapshot codec = h264(camera).orElse(null);
        if (codec == null) return List.of();

        if (camera.selectedPipeline().isPresent()) {
            return pipeline(codec, camera.selectedPipeline().orElseThrow().pipelineId())
                    .filter(SelectCameraPipelineUseCase::usable)
                    .map(List::of).orElseGet(List::of);
        }

        Optional<Decision> verified = verifiedDecision(codec);
        if (verified.isPresent()) return List.of(verified.orElseThrow().pipeline());
        if (!fastInventoryComplete(codec)) return List.of();

        List<PipelineEvidence> result = new ArrayList<>();
        codec.pipelines().stream().filter(SelectCameraPipelineUseCase::usable)
                .sorted(this::compareFast)
                .forEach(result::add);
        return fastTieUnresolved(result) ? List.of() : List.copyOf(result);
    }

    public Optional<Decision> decision(CameraSnapshot camera) {
        Objects.requireNonNull(camera, "camera");
        CodecSnapshot codec = h264(camera).orElse(null);
        if (codec == null) return Optional.empty();

        if (camera.selectedPipeline().isPresent()) {
            var selected = camera.selectedPipeline().orElseThrow();
            return pipeline(codec, selected.pipelineId())
                    .filter(SelectCameraPipelineUseCase::usable)
                    .map(value -> new Decision(value, selected.decisionStatus(),
                            selected.decisionReason()));
        }

        Optional<Decision> verified = verifiedDecision(codec);
        if (verified.isPresent()) return verified;
        if (!fastInventoryComplete(codec)) return Optional.empty();

        List<PipelineEvidence> usable = codec.pipelines().stream()
                .filter(SelectCameraPipelineUseCase::usable)
                .sorted(this::compareFast)
                .collect(java.util.stream.Collectors.toList());
        if (usable.isEmpty() || fastTieUnresolved(usable)) return Optional.empty();
        PipelineDecisionReason reason = usable.size() == 1
                ? PipelineDecisionReason.ONLY_AVAILABLE
                : fastDecisionReason(usable.get(0), usable.get(1));
        return Optional.of(new Decision(usable.get(0),
                PipelineDecisionStatus.FAST_COMPLETE, reason));
    }

    public Optional<VerificationPipelineId> selectedPipeline(CameraSnapshot camera) {
        return select(camera).map(PipelineEvidence::verificationPipelineId);
    }

    private boolean fastInventoryComplete(CodecSnapshot codec) {
        if (expectedFastPipelines.isEmpty()) return !codec.pipelines().isEmpty();
        for (VerificationPipelineId expected : expectedFastPipelines) {
            if (pipeline(codec, expected).isEmpty()) return false;
        }
        return true;
    }

    private static Optional<CodecSnapshot> h264(CameraSnapshot camera) {
        return camera.codecs().stream()
                .filter(value -> value.codec() == VideoCodec.H264).findFirst();
    }

    private static Optional<Decision> verifiedDecision(CodecSnapshot codec) {
        if (codec.comparison().isEmpty()) return Optional.empty();
        PipelineComparison comparison = codec.comparison().orElseThrow().comparison();
        if (comparison.status() != PipelineComparison.Status.COMPLETE) {
            return Optional.empty();
        }
        VerificationPipelineId selected = switch (comparison.recommendation()) {
            case PIPELINE_A -> comparison.pipelineA();
            case PIPELINE_B -> comparison.pipelineB();
            case NONE -> null;
        };
        if (selected == null) return Optional.empty();
        PipelineDecisionReason reason = switch (comparison.recommendationReason()) {
            case STRICT_SUPERSET -> PipelineDecisionReason.STRICT_SUPERSET;
            case BEST_TUPLE -> PipelineDecisionReason.BEST_TUPLE;
            case PASS_COUNT -> PipelineDecisionReason.PASS_COUNT;
            case PERFORMANCE -> PipelineDecisionReason.PERFORMANCE;
            case FINAL_TIE_A -> PipelineDecisionReason.FINAL_TIE_A;
            case INCOMPLETE -> throw new IllegalArgumentException(
                    "complete comparison has incomplete recommendation reason");
        };
        return pipeline(codec, selected).filter(SelectCameraPipelineUseCase::usable)
                .map(value -> new Decision(value,
                        PipelineDecisionStatus.VERIFIED_COMPLETE, reason));
    }

    private static Optional<PipelineEvidence> pipeline(
            CodecSnapshot codec, VerificationPipelineId pipelineId) {
        return codec.pipelines().stream()
                .filter(value -> value.verificationPipelineId().equals(pipelineId)).findFirst();
    }

    private static boolean usable(PipelineEvidence pipeline) {
        return pipeline.availability()
                != com.dvid.dcam.feature.device.domain.camera.PipelineAvailability.UNAVAILABLE
                && pipeline.effectiveCandidates().stream()
                        .anyMatch(value -> value.kind() == CandidateKey.Kind.TUPLE);
    }

    private int compareFast(PipelineEvidence left, PipelineEvidence right) {
        int rank = compareFastRank(left, right);
        if (rank != 0) return rank;
        if (fastTiePreference.filter(value -> Objects.equals(
                left.verificationPipelineId(), value)).isPresent()) {
            return -1;
        }
        if (fastTiePreference.filter(value -> Objects.equals(
                right.verificationPipelineId(), value)).isPresent()) {
            return 1;
        }
        return 0;
    }

    private static int compareFastRank(PipelineEvidence left, PipelineEvidence right) {
        Set<CaptureModeTuple> leftTuples = fastTuples(left);
        Set<CaptureModeTuple> rightTuples = fastTuples(right);
        boolean leftSuperset = leftTuples.containsAll(rightTuples);
        boolean rightSuperset = rightTuples.containsAll(leftTuples);
        if (leftSuperset != rightSuperset) return leftSuperset ? -1 : 1;

        CaptureModeTuple leftBest = bestTuple(leftTuples);
        CaptureModeTuple rightBest = bestTuple(rightTuples);
        int bestOrder = Comparator.nullsLast(CameraModeOrder.tuples().reversed())
                .compare(leftBest, rightBest);
        if (bestOrder != 0) return bestOrder;
        return Integer.compare(rightTuples.size(), leftTuples.size());
    }

    private boolean fastTieUnresolved(List<PipelineEvidence> pipelines) {
        if (pipelines.size() < 2 || compareFastRank(pipelines.get(0), pipelines.get(1)) != 0) {
            return false;
        }
        return fastTiePreference.filter(value -> value.equals(
                pipelines.get(0).verificationPipelineId()) || value.equals(
                pipelines.get(1).verificationPipelineId())).isEmpty();
    }

    private static PipelineDecisionReason fastDecisionReason(
            PipelineEvidence winner, PipelineEvidence runnerUp) {
        Set<CaptureModeTuple> winnerTuples = fastTuples(winner);
        Set<CaptureModeTuple> runnerUpTuples = fastTuples(runnerUp);
        if (winnerTuples.containsAll(runnerUpTuples)
                && !runnerUpTuples.containsAll(winnerTuples)) {
            return PipelineDecisionReason.STRICT_SUPERSET;
        }
        if (CameraModeOrder.tuples().compare(
                bestTuple(winnerTuples), bestTuple(runnerUpTuples)) != 0) {
            return PipelineDecisionReason.BEST_TUPLE;
        }
        if (winnerTuples.size() != runnerUpTuples.size()) {
            return PipelineDecisionReason.TUPLE_COUNT;
        }
        return PipelineDecisionReason.FINAL_TIE_A;
    }

    private static Set<CaptureModeTuple> fastTuples(PipelineEvidence pipeline) {
        TreeSet<CaptureModeTuple> result = new TreeSet<>(CameraModeOrder.tuples());
        for (CandidateKey candidate : pipeline.rawFastCandidates()) {
            if (candidate.kind() == CandidateKey.Kind.TUPLE) {
                result.add(candidate.tuple().orElseThrow());
            }
        }
        return result;
    }

    private static CaptureModeTuple bestTuple(Set<CaptureModeTuple> tuples) {
        return tuples.stream().max(CameraModeOrder.tuples()).orElse(null);
    }
}
