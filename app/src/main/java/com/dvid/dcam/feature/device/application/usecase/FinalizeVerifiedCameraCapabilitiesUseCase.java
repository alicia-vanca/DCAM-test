package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineComparisonSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineVerificationCoverage;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedPipeline;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase.PipelineRun;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineCoverage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineRunStatus;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparison;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparisonInput;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class FinalizeVerifiedCameraCapabilitiesUseCase {
    private final ResolveCameraRuntimeSelectionUseCase selectionResolver;
    private final SelectCameraPipelineUseCase pipelineSelector;

    public FinalizeVerifiedCameraCapabilitiesUseCase() {
        this(new SelectCameraPipelineUseCase());
    }

    public FinalizeVerifiedCameraCapabilitiesUseCase(
            SelectCameraPipelineUseCase pipelineSelector) {
        this.pipelineSelector = Objects.requireNonNull(
                pipelineSelector, "pipelineSelector");
        selectionResolver = new ResolveCameraRuntimeSelectionUseCase(pipelineSelector);
    }

    public Result execute(Request request) {
        Objects.requireNonNull(request, "request");
        requireComplete(request.pipelineA());
        requireComplete(request.pipelineB());
        if (request.pipelineA().pipelineId().equals(
                request.pipelineB().pipelineId())) {
            throw new IllegalArgumentException("verified pipelines must differ");
        }

        Map<CameraId, PipelineCoverage> coverageA = byCamera(request.pipelineA());
        Map<CameraId, PipelineCoverage> coverageB = byCamera(request.pipelineB());
        List<CameraSnapshot> cameras = new ArrayList<>();
        int tuplesA = 0;
        int tuplesB = 0;
        for (CameraSnapshot camera : request.baseline().cameras()) {
            PipelineCoverage sourceA = requiredCoverage(coverageA, camera.cameraId());
            PipelineCoverage sourceB = requiredCoverage(coverageB, camera.cameraId());
            PipelineEvidence finalA = durableEvidence(sourceA.evidence());
            PipelineEvidence finalB = durableEvidence(sourceB.evidence());
            PipelineVerificationCoverage durableCoverageA = verificationCoverage(sourceA.evidence());
            PipelineVerificationCoverage durableCoverageB = verificationCoverage(sourceB.evidence());
            requireCandidatesTerminal(sourceA.evidence());
            requireCandidatesTerminal(sourceB.evidence());
            tuplesA += effectiveTupleCount(finalA);
            tuplesB += effectiveTupleCount(finalB);

            Set<CaptureModeTuple> validUniverse = tupleUnion(finalA, finalB);
            if (tupleCount(finalA) + tupleCount(finalB) == 0) {
                throw new IllegalStateException(
                        "camera has no verified recording tuple: " + camera.cameraId());
            }
            PipelineComparison comparison = PipelineComparison.compare(validUniverse,
                    PipelineComparisonInput.withoutPerformance(finalA, true),
                    PipelineComparisonInput.withoutPerformance(finalB, true));
            if (comparison.status() != PipelineComparison.Status.COMPLETE) {
                throw new IllegalStateException(
                        "verified pipeline comparison is incomplete");
            }
            CodecSnapshot h264 = new CodecSnapshot(VideoCodec.H264,
                    CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                    List.of(finalA, finalB), Optional.of(
                    PipelineComparisonSnapshot.withCoverage(
                            comparison, durableCoverageA, durableCoverageB)));
            List<CodecSnapshot> codecs = new ArrayList<>();
            codecs.add(h264);
            camera.codecs().stream()
                    .filter(codec -> codec.codec() != VideoCodec.H264)
                    .forEach(codecs::add);
            CameraSnapshot unselected = new CameraSnapshot(camera.cameraId(),
                    camera.hardwareSignature(), codecs, Optional.empty(), Optional.empty(),
                    camera.sensorOrientationDegrees());
            SelectedPipeline selected = selectedPipeline(
                    unselected, request.forcedPipeline());
            cameras.add(new CameraSnapshot(camera.cameraId(), camera.hardwareSignature(),
                    codecs, Optional.of(selected), Optional.empty(),
                    camera.sensorOrientationDegrees()));
        }

        Snapshot selected = new Snapshot(request.baseline().format(),
                InitializationState.INCOMPLETE,
                request.baseline().hardwareSignature(),
                request.baseline().cameraOrderOverride(), cameras);
        List<CameraSnapshot> withProfiles = new ArrayList<>();
        for (CameraSnapshot camera : selected.cameras()) {
            Optional<SelectedRecordingProfile> preserved = previousProfile(
                    request.baseline(), camera);
            CandidateKey candidate = preserved.map(profile -> CandidateKey.forTuple(
                    camera.cameraId(), profile.codec(),
                    profile.verificationPipelineId(), profile.tuple()))
                    .orElseGet(() -> selectionResolver.defaultCandidate(
                            selected, camera.cameraId()).orElseThrow(() ->
                            new IllegalStateException(
                                    "verified default tuple unavailable")));
            SelectedRecordingProfile profile = new SelectedRecordingProfile(
                    candidate.codec(), candidate.verificationPipelineId(),
                    candidate.tuple().orElseThrow());
            withProfiles.add(new CameraSnapshot(camera.cameraId(),
                    camera.hardwareSignature(), camera.codecs(),
                    camera.selectedPipeline(), Optional.of(profile),
                    camera.sensorOrientationDegrees()));
        }
        Snapshot finalized = new Snapshot(request.baseline().format(),
                InitializationState.READY_REUSABLE,
                request.baseline().hardwareSignature(),
                request.baseline().cameraOrderOverride(), withProfiles);
        Summary summary = new Summary(tuplesA, tuplesB,
                failedTuples(request.pipelineA()), failedTuples(request.pipelineB()),
                request.pipelineA().fastScanMillis()
                        + request.pipelineA().realVerifyMillis(),
                request.pipelineB().fastScanMillis()
                        + request.pipelineB().realVerifyMillis());
        return new Result(finalized, summary);
    }

    private SelectedPipeline selectedPipeline(CameraSnapshot camera,
            Optional<VerificationPipelineId> forcedPipeline) {
        if (forcedPipeline.isPresent()) {
            VerificationPipelineId pipelineId = forcedPipeline.orElseThrow();
            PipelineEvidence pipeline = pipeline(camera, pipelineId).orElseThrow(() ->
                    new IllegalStateException("forced pipeline missing"));
            if (tupleCount(pipeline) == 0) {
                throw new IllegalStateException("forced pipeline has no verified tuple");
            }
            return SelectedPipeline.fixed(pipelineId);
        }
        SelectCameraPipelineUseCase.Decision decision = pipelineSelector.decision(camera)
                .orElseThrow(() -> new IllegalStateException(
                        "verified pipeline selection unavailable"));
        return SelectedPipeline.autoVerified(
                decision.pipeline().verificationPipelineId(), decision.reason());
    }

    private static PipelineEvidence durableEvidence(PipelineEvidence source) {
        List<CandidateEvidence> facts = source.candidateEvidence().entrySet().stream()
                .filter(entry -> entry.getValue().isTerminalEvidence())
                .map(entry -> new CandidateEvidence(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toList());
        return new PipelineEvidence(source.cameraId(), source.codec(),
                source.verificationPipelineId(), PipelineAvailability.AVAILABLE,
                source.rawFastCandidates(), facts);
    }

    private static PipelineVerificationCoverage verificationCoverage(
            PipelineEvidence planned) {
        int plannedImages = candidateCount(planned, CandidateKey.Kind.IMAGE);
        int verifiedImages = terminalCandidateCount(planned, CandidateKey.Kind.IMAGE);
        int plannedTuples = candidateCount(planned, CandidateKey.Kind.TUPLE);
        int verifiedTuples = terminalCandidateCount(planned, CandidateKey.Kind.TUPLE);
        return new PipelineVerificationCoverage(
                plannedImages, verifiedImages, plannedTuples, verifiedTuples);
    }

    private static int candidateCount(PipelineEvidence evidence, CandidateKey.Kind kind) {
        return Math.toIntExact(evidence.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == kind)
                .count());
    }

    private static int terminalCandidateCount(PipelineEvidence evidence, CandidateKey.Kind kind) {
        return Math.toIntExact(evidence.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == kind)
                .filter(candidate -> evidence.outcome(candidate).isTerminalEvidence())
                .count());
    }

    private static void requireCandidatesTerminal(PipelineEvidence evidence) {
        boolean incomplete = evidence.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE
                        || candidate.kind() == CandidateKey.Kind.TUPLE)
                .anyMatch(candidate -> !evidence.outcome(candidate).isTerminalEvidence());
        if (incomplete) {
            throw new IllegalStateException("camera verification is incomplete");
        }
    }

    private static Optional<SelectedRecordingProfile> previousProfile(
            Snapshot baseline, CameraSnapshot finalized) {
        CameraSnapshot previous = baseline.cameras().stream()
                .filter(camera -> camera.cameraId().equals(finalized.cameraId()))
                .findFirst().orElse(null);
        if (previous == null || previous.selectedRecordingProfile().isEmpty()
                || finalized.selectedPipeline().isEmpty()) {
            return Optional.empty();
        }
        SelectedRecordingProfile profile =
                previous.selectedRecordingProfile().orElseThrow();
        if (!profile.verificationPipelineId().equals(
                finalized.selectedPipeline().orElseThrow().pipelineId())) {
            return Optional.empty();
        }
        return pipeline(finalized, profile.verificationPipelineId())
                .filter(value -> value.outcome(profile.tuple())
                        == VerificationOutcome.VERIFIED_PASS)
                .map(ignored -> profile);
    }

    private static Optional<PipelineEvidence> pipeline(CameraSnapshot camera,
            VerificationPipelineId pipelineId) {
        return camera.codecs().stream()
                .filter(codec -> codec.codec() == VideoCodec.H264)
                .flatMap(codec -> codec.pipelines().stream())
                .filter(pipeline -> pipeline.verificationPipelineId().equals(pipelineId))
                .findFirst();
    }

    private static Set<CaptureModeTuple> tupleUnion(
            PipelineEvidence left, PipelineEvidence right) {
        TreeSet<CaptureModeTuple> result = new TreeSet<>(
                com.dvid.dcam.feature.device.domain.camera.CameraModeOrder.tuples());
        addTuples(result, left);
        addTuples(result, right);
        return Set.copyOf(result);
    }

    private static void addTuples(Set<CaptureModeTuple> target,
            PipelineEvidence evidence) {
        evidence.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .map(candidate -> candidate.tuple().orElseThrow())
                .forEach(target::add);
    }

    private static int effectiveTupleCount(PipelineEvidence evidence) {
        return Math.toIntExact(evidence.effectiveCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .filter(candidate -> evidence.outcome(candidate) == VerificationOutcome.VERIFIED_PASS)
                .count());
    }

    private static int tupleCount(PipelineEvidence evidence) {
        return Math.toIntExact(evidence.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .filter(candidate -> evidence.outcome(candidate) == VerificationOutcome.VERIFIED_PASS)
                .count());
    }

    private static Map<CameraId, PipelineCoverage> byCamera(PipelineRun run) {
        Map<CameraId, PipelineCoverage> result = new HashMap<>();
        for (PipelineCoverage coverage : run.cameras()) {
            if (result.put(coverage.cameraId(), coverage) != null) {
                throw new IllegalArgumentException("duplicate pipeline camera coverage");
            }
        }
        return Map.copyOf(result);
    }

    private static PipelineCoverage requiredCoverage(
            Map<CameraId, PipelineCoverage> values, CameraId cameraId) {
        PipelineCoverage coverage = values.get(cameraId);
        if (coverage == null) {
            throw new IllegalArgumentException("pipeline camera coverage missing");
        }
        return coverage;
    }

    private static void requireComplete(PipelineRun run) {
        if (run.status() != PipelineRunStatus.COMPLETE || !run.cleanupComplete()
                || run.cameras().stream().anyMatch(coverage ->
                coverage.status() != PipelineRunStatus.COMPLETE
                        || !coverage.cleanupComplete()
                        || coverage.unknownCount() != 0)) {
            throw new IllegalArgumentException("pipeline verification is incomplete");
        }
    }

    public record Request(Snapshot baseline, PipelineRun pipelineA,
            PipelineRun pipelineB,
            Optional<VerificationPipelineId> forcedPipeline) {
        public Request {
            baseline = Objects.requireNonNull(baseline, "baseline");
            pipelineA = Objects.requireNonNull(pipelineA, "pipelineA");
            pipelineB = Objects.requireNonNull(pipelineB, "pipelineB");
            forcedPipeline = Objects.requireNonNull(
                    forcedPipeline, "forcedPipeline");
        }
    }

    public record FailedTuple(String cameraId, CaptureModeTuple tuple, String reason) {
        public FailedTuple {
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            tuple = Objects.requireNonNull(tuple, "tuple");
            reason = Objects.requireNonNull(reason, "reason");
            if (cameraId.isBlank() || reason.isBlank()) {
                throw new IllegalArgumentException("failed tuple details are required");
            }
        }
    }

    public record Summary(int pipelineATupleCount, int pipelineBTupleCount,
            List<FailedTuple> pipelineAFailedTuples, List<FailedTuple> pipelineBFailedTuples,
            long pipelineAElapsedMillis, long pipelineBElapsedMillis) {
        public Summary {
            pipelineAFailedTuples = List.copyOf(Objects.requireNonNull(
                    pipelineAFailedTuples, "pipelineAFailedTuples"));
            pipelineBFailedTuples = List.copyOf(Objects.requireNonNull(
                    pipelineBFailedTuples, "pipelineBFailedTuples"));
            if (pipelineATupleCount < 0 || pipelineBTupleCount < 0
                    || pipelineAElapsedMillis < 0 || pipelineBElapsedMillis < 0) {
                throw new IllegalArgumentException("negative capability summary");
            }
        }

        public Summary(int pipelineATupleCount, int pipelineBTupleCount,
                long pipelineAElapsedMillis, long pipelineBElapsedMillis) {
            this(pipelineATupleCount, pipelineBTupleCount, List.of(), List.of(),
                    pipelineAElapsedMillis, pipelineBElapsedMillis);
        }
    }

    private static List<FailedTuple> failedTuples(PipelineRun run) {
        List<FailedTuple> result = new ArrayList<>();
        for (PipelineCoverage coverage : run.cameras()) {
            for (TupleOutcome outcome : coverage.outcomes()) {
                if (outcome.outcome() == VerificationOutcome.DEFINITIVE_UNSUPPORTED) {
                    result.add(new FailedTuple(coverage.cameraId().value(),
                            outcome.tuple(), outcome.reason()));
                }
            }
        }
        return List.copyOf(result);
    }

    public record Result(Snapshot snapshot, Summary summary) {
        public Result {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            summary = Objects.requireNonNull(summary, "summary");
        }
    }
}