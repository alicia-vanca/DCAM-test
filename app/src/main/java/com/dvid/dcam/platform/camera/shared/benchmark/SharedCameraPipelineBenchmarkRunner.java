package com.dvid.dcam.platform.camera.shared.benchmark;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraHealthGenerationProvider;
import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.CameraFastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase.Invocation;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase.Measurement;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase.PipelineRun;
import com.dvid.dcam.feature.device.application.usecase.VerifyCameraSelectionUseCase;
import com.dvid.dcam.feature.device.application.usecase.VerifyStandaloneImageUseCase;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraModeOrder;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.CameraScope;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PerformanceSample;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineCoverage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineRunStatus;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.ResourceSample;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleStage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.platform.camera.shared.CameraOrientation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SharedCameraPipelineBenchmarkRunner
        extends CompareCameraPipelinesUseCase.PipelineRunner {
    private final BuildFastCameraCapabilitiesUseCase fastUseCase;
    private final FastCameraCapabilityProbe fastProbe;
    private final VerificationPipelineId pipelineId;
    private final CandidateVerifier candidateVerifier;
    private final StandaloneImageVerifier standaloneImageVerifier;
    private final Logger logger;
    private final CameraHealthGenerationProvider healthGenerationProvider;
    private final CameraBenchmarkTelemetry telemetry;
    private final CameraPipelineBenchmarkPlan.FrozenEnvironment frozenEnvironment;
    private final EnvironmentValidator environmentValidator;
    private final BenchmarkingRuntime runtime;
    private final Map<CameraId, FrozenFastCoverage> frozenFastCoverage;
    private final Map<CameraId, Long> fastMillisByCamera = new TreeMap<>();
    private final Map<CameraId, Integer> sensorOrientationDegreesByCamera = new TreeMap<>();
    private long sessionGeneration = 1;
    private CameraOperationContext activeContext;

    public SharedCameraPipelineBenchmarkRunner(
            BuildFastCameraCapabilitiesUseCase fastUseCase,
            FastCameraCapabilityProbe fastProbe,
            CameraRuntimeOperations verificationRuntime,
            CandidateVerifier candidateVerifier,
            StandaloneImageVerifier standaloneImageVerifier,
            Logger logger,
            CameraHealthGenerationProvider healthGenerationProvider,
            CameraPipelineBenchmarkPlan.FrozenEnvironment frozenEnvironment,
            EnvironmentValidator environmentValidator,
            CameraBenchmarkTelemetry telemetry) {
        this.fastUseCase = Objects.requireNonNull(fastUseCase, "fastUseCase");
        this.fastProbe = Objects.requireNonNull(fastProbe, "fastProbe");
        this.pipelineId = fastProbe.pipelineId();
        this.candidateVerifier = Objects.requireNonNull(candidateVerifier, "candidateVerifier");
        this.standaloneImageVerifier = Objects.requireNonNull(
                standaloneImageVerifier, "standaloneImageVerifier");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.healthGenerationProvider = Objects.requireNonNull(
                healthGenerationProvider, "healthGenerationProvider");
        this.frozenEnvironment = Objects.requireNonNull(
                frozenEnvironment, "frozenEnvironment");
        this.environmentValidator = Objects.requireNonNull(
                environmentValidator, "environmentValidator");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.runtime = new BenchmarkingRuntime(
                Objects.requireNonNull(verificationRuntime, "verificationRuntime"));
        this.frozenFastCoverage = null;
    }

    SharedCameraPipelineBenchmarkRunner(
            Map<CameraId, FrozenFastCoverage> frozenFastCoverage,
            VerificationPipelineId pipelineId,
            CameraRuntimeOperations verificationRuntime,
            CandidateVerifier candidateVerifier,
            StandaloneImageVerifier standaloneImageVerifier,
            Logger logger,
            CameraHealthGenerationProvider healthGenerationProvider,
            CameraPipelineBenchmarkPlan.FrozenEnvironment frozenEnvironment,
            EnvironmentValidator environmentValidator,
            CameraBenchmarkTelemetry telemetry) {
        this.fastUseCase = null;
        this.fastProbe = null;
        this.pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
        this.candidateVerifier = Objects.requireNonNull(candidateVerifier, "candidateVerifier");
        this.standaloneImageVerifier = Objects.requireNonNull(
                standaloneImageVerifier, "standaloneImageVerifier");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.healthGenerationProvider = Objects.requireNonNull(
                healthGenerationProvider, "healthGenerationProvider");
        this.frozenEnvironment = Objects.requireNonNull(
                frozenEnvironment, "frozenEnvironment");
        this.environmentValidator = Objects.requireNonNull(
                environmentValidator, "environmentValidator");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.runtime = new BenchmarkingRuntime(
                Objects.requireNonNull(verificationRuntime, "verificationRuntime"));
        this.frozenFastCoverage = Map.copyOf(Objects.requireNonNull(
                frozenFastCoverage, "frozenFastCoverage"));
        for (FrozenFastCoverage coverage : this.frozenFastCoverage.values()) {
            if (!coverage.evidence().verificationPipelineId().equals(pipelineId)) {
                throw new IllegalArgumentException("frozen fast pipeline ID differs");
            }
        }
    }

    @Override public VerificationPipelineId pipelineId() { return pipelineId; }

    @Override public PipelineRun runCoverage(CameraPipelineBenchmarkPlan plan,
            BooleanSupplier cancellationSignal, Consumer<CompareCameraPipelinesUseCase.Progress> progressListener) {
        requireNoActiveOwner();
        EnvironmentValidation environment = validateFrozenEnvironment(plan);
        if (!environment.ready()) return environmentBlocked(plan, environment.detail());
        long startedNanos = System.nanoTime();
        fastMillisByCamera.clear();
        sensorOrientationDegreesByCamera.clear();
        if (frozenFastCoverage != null) {
            return runFrozenCoverage(plan, cancellationSignal, progressListener);
        }
        BuildFastCameraCapabilitiesUseCase.Result fast = fastUseCase.execute(
                fastProbe, cancellationSignal::getAsBoolean);
        if (!fast.complete()) {
            return incompleteFast(plan, fast, cancellationSignal.getAsBoolean());
        }
        var snapshot = fast.authoritativeSnapshot().orElseThrow();
        Map<CameraId, CameraFastSnapshot> fastByCamera = new TreeMap<>();
        snapshot.cameras().forEach(value -> {
            CameraId cameraId = value.cameraFacts().cameraId();
            fastByCamera.put(cameraId, value);
            sensorOrientationDegreesByCamera.put(
                    cameraId, sensorOrientationDegrees(value));
        });
        List<PipelineCoverage> coverages = new ArrayList<>();
        boolean complete = true;
        boolean cleanupComplete = true;
        long realTotal = 0;
        for (CameraScope scope : plan.cameras()) {
            CameraFastSnapshot cameraFast = fastByCamera.get(scope.cameraId());
            if (cameraFast == null) {
                coverages.add(missingCamera(scope));
                complete = false;
                continue;
            }
            fastMillisByCamera.put(scope.cameraId(), cameraFast.probeElapsedMillis());
            CameraCoverageResult camera = verifyCamera(plan, scope, coverageEvidence(cameraFast),
                    cameraFast.probeElapsedMillis(), cancellationSignal, progressListener);
            coverages.add(camera.coverage);
            realTotal += camera.coverage.realVerifyMillis();
            cleanupComplete &= camera.coverage.cleanupComplete();
            complete &= camera.coverage.status() == PipelineRunStatus.COMPLETE;
            if (cancellationSignal.getAsBoolean()) break;
        }
        PipelineRunStatus status = cancellationSignal.getAsBoolean()
                ? PipelineRunStatus.CANCELLED
                : complete && cleanupComplete ? PipelineRunStatus.COMPLETE
                : PipelineRunStatus.INCOMPLETE;
        return new PipelineRun(pipelineId(), status, coverages,
                fast.benchmark().totalElapsedMillis(), realTotal, cleanupComplete,
                status == PipelineRunStatus.COMPLETE ? "complete" : "coverage_incomplete");
    }

    private EnvironmentValidation validateFrozenEnvironment(
            CameraPipelineBenchmarkPlan plan) {
        if (!plan.environment().equals(frozenEnvironment)) {
            return new EnvironmentValidation(false, "benchmark_environment_changed");
        }
        return Objects.requireNonNull(environmentValidator.validate(frozenEnvironment),
                "environmentValidator result");
    }

    private void requireNoActiveOwner() {
        if (activeContext != null) {
            throw new IllegalStateException("previous benchmark camera owner still active");
        }
    }

    @Override public Measurement measure(CameraPipelineBenchmarkPlan plan,
            CameraId cameraId, CaptureModeTuple tuple, Invocation invocation,
            BooleanSupplier cancellationSignal) {
        requireNoActiveOwner();
        EnvironmentValidation environment = validateFrozenEnvironment(plan);
        if (!environment.ready()) {
            return Measurement.incomplete("environment=" + environment.detail());
        }
        if (cancellationSignal.getAsBoolean()) {
            return Measurement.incomplete("cancelled_before_measurement");
        }
        CameraScope scope = plan.cameras().stream()
                .filter(value -> value.cameraId().equals(cameraId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("camera outside benchmark scope"));
        if (!scope.candidateUniverse().contains(tuple)) {
            throw new IllegalArgumentException("tuple outside benchmark universe");
        }
        CandidateKey candidate = CandidateKey.forTuple(cameraId, VideoCodec.H264,
                pipelineId(), tuple);
        PipelineEvidence evidence = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineId(), PipelineAvailability.AVAILABLE, List.of(candidate), List.of());
        logger.info("camera_pipeline_benchmark measurement_start pipeline=" + pipelineId()
                + " camera=" + cameraId + " tuple=" + tuple
                + " warmup=" + invocation.warmup() + " block=" + invocation.block());
        ExactResult exact = verifyExact(plan, scope, evidence, candidate, true);
        if (exact.outcome != VerificationOutcome.VERIFIED_PASS
                || exact.activeContext.isEmpty()) {
            logger.info("camera_pipeline_benchmark measurement_end pipeline=" + pipelineId()
                    + " camera=" + cameraId + " tuple=" + tuple
                    + " outcome=" + exact.outcome + " stage=" + exact.stage
                    + " reason=" + exact.reason);
            return Measurement.incomplete("measurement_verify=" + exact.outcome);
        }
        activeContext = exact.activeContext.orElseThrow();
        ResourceSample resources = exact.resources;
        PerformanceSample sample = new PerformanceSample(pipelineId(), cameraId, tuple,
                invocation.warmup(), invocation.block(), invocation.position(),
                fastMillisByCamera.getOrDefault(cameraId, 0L),
                optional(runtime.bindToPreviewMillis),
                optional(runtime.firstEncodedSampleMillis),
                optional(runtime.jpegCaptureMillis),
                optional(runtime.stopFinalizeMillis),
                OptionalLong.of(exact.elapsedMillis), runtime.measuredFps(),
                runtime.droppedFrames(), resources);
        logger.info("camera_pipeline_benchmark measurement_end pipeline=" + pipelineId()
                + " camera=" + cameraId + " tuple=" + tuple + " outcome=pass"
                + " totalVerifyMillis=" + exact.elapsedMillis);
        return new Measurement(true, Optional.of(sample), "complete");
    }

    @Override public boolean release() {
        CameraOperationContext context = activeContext;
        if (context == null) return true;
        CameraOperationResult result = runtime.release(context);
        if (result.outcome() == CameraOperationOutcome.PASS) activeContext = null;
        logger.info("camera_pipeline_benchmark release pipeline=" + pipelineId()
                + " outcome=" + result.outcome() + " detail=" + result.detail());
        return result.outcome() == CameraOperationOutcome.PASS;
    }

    private CameraCoverageResult verifyCamera(CameraPipelineBenchmarkPlan plan,
            CameraScope scope, PipelineEvidence fastEvidence, long fastScanMillis,
            BooleanSupplier cancellation, Consumer<CompareCameraPipelinesUseCase.Progress> progress) {
        long startedNanos = System.nanoTime();
        CandidateSets sets = candidateSets(scope, fastEvidence);
        Map<CandidateKey, VerificationOutcome> facts = new TreeMap<>();
        fastEvidence.candidateEvidence().forEach((candidate, outcome) -> {
            if (candidate.kind() == CandidateKey.Kind.VIDEO
                    && outcome.isTerminalEvidence()
                    && sets.rawCandidates().contains(candidate)) {
                facts.put(candidate, outcome);
            }
        });
        List<TupleOutcome> outcomes = new ArrayList<>();
        List<CandidateKey> images = sets.rawCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int total = images.size() + scope.candidateUniverse().size();
        int completed = 0;
        boolean cleanupComplete = true;
        boolean imageCoverageComplete = true;
        String unfinishedReason = "not_attempted";
        for (CandidateKey image : images) {
            EnvironmentValidation environment = validateFrozenEnvironment(plan);
            if (!environment.ready()) {
                imageCoverageComplete = false;
                unfinishedReason = "environment=" + environment.detail();
                break;
            }
            completed++;
            progress.accept(new CompareCameraPipelinesUseCase.Progress(
                    "standalone_image_verify", completed, total,
                    "pipeline=" + pipelineId() + " camera=" + scope.cameraId()
                            + " image=" + image.imageMode().orElseThrow()));
            if (cancellation.getAsBoolean()) {
                imageCoverageComplete = false;
                unfinishedReason = "cancelled";
                break;
            }
            PipelineEvidence aggregate = evidence(scope.cameraId(),
                    sets.rawCandidates(), facts);
            if (aggregate.outcome(image).isTerminalEvidence()) continue;
            Optional<CaptureModeTuple> binding = bindingTuple(image, sets.rawCandidates());
            if (binding.isEmpty()) {
                facts.put(image, VerificationOutcome.DEFINITIVE_UNSUPPORTED);
                continue;
            }
            ImageExactResult exact = verifyStandaloneImage(
                    plan, scope, aggregate, image, binding.orElseThrow());
            cleanupComplete &= exact.cleanupComplete();
            for (var entry : exact.evidence().candidateEvidence().entrySet()) {
                facts.put(entry.getKey(), entry.getValue());
            }
            if (!exact.outcome().isTerminalEvidence()) {
                imageCoverageComplete = false;
                unfinishedReason = exact.reason();
                break;
            }
            if (!exact.cleanupComplete()) {
                imageCoverageComplete = false;
                unfinishedReason = "standalone_image_cleanup_incomplete";
                break;
            }
        }

        if (imageCoverageComplete) {
            for (CaptureModeTuple tuple : scope.candidateUniverse()) {
                EnvironmentValidation environment = validateFrozenEnvironment(plan);
                if (!environment.ready()) {
                    unfinishedReason = "environment=" + environment.detail();
                    break;
                }
                completed++;
                CandidateKey candidate = CandidateKey.forTuple(scope.cameraId(), VideoCodec.H264,
                        pipelineId(), tuple);
                progress.accept(new CompareCameraPipelinesUseCase.Progress(
                        "exhaustive_real_verify", completed, total,
                        "pipeline=" + pipelineId() + " camera=" + scope.cameraId()
                                + " tuple=" + tuple));
                logger.info("camera_pipeline_benchmark candidate_start pipeline=" + pipelineId()
                        + " camera=" + scope.cameraId() + " tuple=" + tuple);
                if (cancellation.getAsBoolean()) {
                    outcomes.add(new TupleOutcome(tuple, VerificationOutcome.CANCELLED_UNKNOWN,
                            TupleStage.UNKNOWN, "cancelled"));
                    continue;
                }

                PipelineEvidence aggregate = evidence(scope.cameraId(),
                        sets.rawCandidates(), facts);

                ExactResult exact = verifyExact(plan, scope, aggregate, candidate, false);
                cleanupComplete &= exact.cleanupComplete;
                for (var entry : exact.evidence.candidateEvidence().entrySet()) {
                    facts.put(entry.getKey(), entry.getValue());
                }
                PipelineEvidence updated = evidence(scope.cameraId(),
                        sets.rawCandidates(), facts);
                VerificationOutcome outcome = updated.outcome(tuple);
                outcomes.add(new TupleOutcome(tuple, outcome, exact.stage, exact.reason));
                logger.info("camera_pipeline_benchmark candidate_end pipeline=" + pipelineId()
                        + " camera=" + scope.cameraId() + " tuple=" + tuple
                        + " outcome=" + outcome + " stage=" + exact.stage
                        + " reason=" + exact.reason + " cleanup=" + exact.cleanupComplete);
                if (!exact.cleanupComplete) {
                    unfinishedReason = "cleanup_incomplete";
                    break;
                }
            }
        }
        Set<CaptureModeTuple> reported = outcomes.stream().map(TupleOutcome::tuple)
                .collect(java.util.stream.Collectors.toSet());
        for (CaptureModeTuple tuple : scope.candidateUniverse()) {
            if (!reported.contains(tuple)) {
                outcomes.add(new TupleOutcome(tuple, VerificationOutcome.UNKNOWN,
                        TupleStage.UNKNOWN, unfinishedReason));
            }
        }
        PipelineEvidence finalEvidence = evidence(scope.cameraId(),
                sets.rawCandidates(), facts);
        boolean imagesTerminal = images.stream()
                .allMatch(image -> finalEvidence.outcome(image).isTerminalEvidence());
        boolean tuplesTerminal = outcomes.stream()
                .allMatch(value -> value.outcome().isTerminalEvidence());
        PipelineRunStatus status = imagesTerminal && tuplesTerminal && cleanupComplete
                ? PipelineRunStatus.COMPLETE : PipelineRunStatus.INCOMPLETE;
        PipelineCoverage coverage = new PipelineCoverage(scope.cameraId(), pipelineId(),
                status, finalEvidence, outcomes, fastScanMillis,
                elapsedMillis(startedNanos), cleanupComplete,
                status == PipelineRunStatus.COMPLETE ? "complete" : "unknown_or_cleanup");
        return new CameraCoverageResult(coverage);
    }

    private ImageExactResult verifyStandaloneImage(CameraPipelineBenchmarkPlan plan,
            CameraScope scope, PipelineEvidence aggregate, CandidateKey image,
            CaptureModeTuple bindingTuple) {
        long startedNanos = System.nanoTime();
        PipelineEvidence scopedEvidence = scopedImageEvidence(
                aggregate, image, bindingTuple);
        Snapshot scopedSnapshot = scopedSnapshot(plan, scope, scopedEvidence);
        long generation = sessionGeneration;
        sessionGeneration += 2;
        VerifyStandaloneImageUseCase.Request request =
                new VerifyStandaloneImageUseCase.Request(scopedSnapshot, image,
                        bindingTuple, generation,
                        healthGenerationProvider.currentGeneration(scope.cameraId()));
        VerifyStandaloneImageUseCase.Result result = Objects.requireNonNull(
                standaloneImageVerifier.verify(runtime, request),
                "standaloneImageVerifier result");
        return new ImageExactResult(result.outcome(), pipeline(result.snapshot()),
                result.detail(), elapsedMillis(startedNanos), result.cleanupComplete());
    }
    private ExactResult verifyExact(CameraPipelineBenchmarkPlan plan, CameraScope scope,
            PipelineEvidence aggregate, CandidateKey candidate, boolean retainBinding) {
        runtime.reset();
        CameraBenchmarkTelemetry.Token telemetryToken = telemetry.begin();
        long startedNanos = System.nanoTime();
        PipelineEvidence scopedEvidence = scopedEvidence(aggregate, candidate);
        Snapshot scopedSnapshot = scopedSnapshot(plan, scope, scopedEvidence);
        StagingStore stagingStore = new StagingStore(scopedSnapshot);
        VerifyCameraSelectionUseCase.Request verifyRequest =
                new VerifyCameraSelectionUseCase.Request(scopedSnapshot, candidate,
                        Optional.empty(), sessionGeneration++,
                        healthGenerationProvider.currentGeneration(scope.cameraId()));
        boolean telemetryEnded = false;
        try {
            VerifyCameraSelectionUseCase.Result result = Objects.requireNonNull(
                    candidateVerifier.verify(runtime, stagingStore, verifyRequest),
                    "candidateVerifier result");
            boolean selectedExact = result.selectedCandidate().filter(candidate::equals).isPresent();
            VerificationOutcome outcome = result.outcome();
            String detail = result.detail();
            if (outcome == VerificationOutcome.VERIFIED_PASS && !selectedExact) {
                outcome = VerificationOutcome.UNKNOWN;
                detail += ":selected_candidate_mismatch";
            }
            Optional<CameraOperationContext> active = result.activeBinding();
            boolean retainActive = retainBinding && selectedExact
                    && outcome == VerificationOutcome.VERIFIED_PASS;
            boolean cleanup = true;
            if (retainActive && active.isPresent()) {
                CameraOperationResult preview = runtime.previewProgress(
                        active.orElseThrow());
                logger.info("camera_pipeline_benchmark preview_progress pipeline=" + pipelineId()
                        + " camera=" + scope.cameraId() + " outcome=" + preview.outcome()
                        + " detail=" + preview.detail());
                if (preview.outcome() != CameraOperationOutcome.PASS) {
                    outcome = VerificationOutcome.UNKNOWN;
                    detail += ":preview_progress=" + preview.outcome();
                    retainActive = false;
                }
            }
            if (!retainActive && active.isPresent()) {
                CameraOperationResult release = runtime.release(active.orElseThrow());
                cleanup = release.outcome() == CameraOperationOutcome.PASS;
                if (cleanup) {
                    active = Optional.empty();
                    activeContext = null;
                } else {
                    activeContext = active.orElseThrow();
                }
            }
            ResourceSample resources = telemetry.end(telemetryToken);
            telemetryEnded = true;
            PipelineEvidence resultEvidence = selectedExact ? pipeline(result.snapshot()) : scopedEvidence;
            TupleStage stage = stage(result.attempts());
            return new ExactResult(outcome, resultEvidence, stage, detail,
                    elapsedMillis(startedNanos), cleanup, active, resources);
        } catch (RuntimeException error) {
            runtime.lastContext().ifPresent(context -> activeContext = context);
            if (!telemetryEnded) {
                try {
                    telemetry.end(telemetryToken);
                } catch (RuntimeException telemetryError) {
                    error.addSuppressed(telemetryError);
                }
            }
            throw error;
        }
    }

    private PipelineRun runFrozenCoverage(CameraPipelineBenchmarkPlan plan,
            BooleanSupplier cancellation,
            Consumer<CompareCameraPipelinesUseCase.Progress> progress) {
        List<PipelineCoverage> coverages = new ArrayList<>();
        boolean complete = true;
        boolean cleanupComplete = true;
        long fastTotal = 0;
        long realTotal = 0;
        for (CameraScope scope : plan.cameras()) {
            FrozenFastCoverage fast = frozenFastCoverage.get(scope.cameraId());
            if (fast == null) {
                coverages.add(missingCamera(scope));
                complete = false;
                continue;
            }
            fastMillisByCamera.put(scope.cameraId(), fast.elapsedMillis());
            sensorOrientationDegreesByCamera.put(
                    scope.cameraId(), fast.sensorOrientationDegrees());
            fastTotal += fast.elapsedMillis();
            CameraCoverageResult camera = verifyCamera(plan, scope, fast.evidence(),
                    fast.elapsedMillis(), cancellation, progress);
            coverages.add(camera.coverage());
            realTotal += camera.coverage().realVerifyMillis();
            cleanupComplete &= camera.coverage().cleanupComplete();
            complete &= camera.coverage().status() == PipelineRunStatus.COMPLETE;
        }
        PipelineRunStatus status = cancellation.getAsBoolean()
                ? PipelineRunStatus.CANCELLED
                : complete && cleanupComplete ? PipelineRunStatus.COMPLETE
                : PipelineRunStatus.INCOMPLETE;
        return new PipelineRun(pipelineId(), status, coverages, fastTotal, realTotal,
                cleanupComplete, status == PipelineRunStatus.COMPLETE
                ? "complete_frozen_fast" : "frozen_fast_incomplete");
    }
    private PipelineRun environmentBlocked(CameraPipelineBenchmarkPlan plan, String detail) {
        List<PipelineCoverage> coverages = new ArrayList<>();
        for (CameraScope scope : plan.cameras()) {
            PipelineEvidence evidence = new PipelineEvidence(scope.cameraId(), VideoCodec.H264,
                    pipelineId(), PipelineAvailability.UNKNOWN, List.of(), List.of());
            List<TupleOutcome> outcomes = scope.candidateUniverse().stream()
                    .map(tuple -> new TupleOutcome(tuple, VerificationOutcome.UNKNOWN,
                            TupleStage.UNKNOWN, detail))
                    .collect(java.util.stream.Collectors.toList());
            coverages.add(new PipelineCoverage(scope.cameraId(), pipelineId(),
                    PipelineRunStatus.INCOMPLETE, evidence, outcomes,
                    0, 0, true, detail));
        }
        return new PipelineRun(pipelineId(), PipelineRunStatus.INCOMPLETE,
                coverages, 0, 0, true, detail);
    }

    private PipelineRun incompleteFast(CameraPipelineBenchmarkPlan plan,
            BuildFastCameraCapabilitiesUseCase.Result fast, boolean cancelled) {
        Map<CameraId, CameraFastSnapshot> partial = new TreeMap<>();
        fast.partialCameras().forEach(value -> partial.put(value.cameraFacts().cameraId(), value));
        List<PipelineCoverage> coverages = new ArrayList<>();
        for (CameraScope scope : plan.cameras()) {
            CameraFastSnapshot value = partial.get(scope.cameraId());
            PipelineEvidence evidence = value == null
                    ? new PipelineEvidence(scope.cameraId(), VideoCodec.H264, pipelineId(),
                    PipelineAvailability.UNAVAILABLE, List.of(), List.of())
                    : value.evidence();
            List<TupleOutcome> outcomes = scope.candidateUniverse().stream()
                    .map(tuple -> new TupleOutcome(tuple,
                            cancelled ? VerificationOutcome.CANCELLED_UNKNOWN : VerificationOutcome.UNKNOWN,
                            TupleStage.FAST_SCAN, fast.detail())).collect(java.util.stream.Collectors.toList());
            coverages.add(new PipelineCoverage(scope.cameraId(), pipelineId(),
                    cancelled ? PipelineRunStatus.CANCELLED : PipelineRunStatus.INCOMPLETE,
                    evidence, outcomes, value == null ? 0 : value.probeElapsedMillis(),
                    0, fast.benchmark().rounds().stream().allMatch(
                    BuildFastCameraCapabilitiesUseCase.RoundTiming::cleanupComplete), fast.detail()));
        }
        boolean cleanupComplete = fast.benchmark().rounds().stream().allMatch(
                BuildFastCameraCapabilitiesUseCase.RoundTiming::cleanupComplete);
        return new PipelineRun(pipelineId(), cancelled
                ? PipelineRunStatus.CANCELLED : PipelineRunStatus.INCOMPLETE,
                coverages, fast.benchmark().totalElapsedMillis(), 0, cleanupComplete, fast.detail());
    }

    private PipelineCoverage missingCamera(CameraScope scope) {
        PipelineEvidence evidence = new PipelineEvidence(scope.cameraId(), VideoCodec.H264,
                pipelineId(), PipelineAvailability.UNAVAILABLE, List.of(), List.of());
        List<TupleOutcome> outcomes = scope.candidateUniverse().stream()
                .map(tuple -> new TupleOutcome(tuple, VerificationOutcome.UNKNOWN,
                        TupleStage.FAST_SCAN, "camera_missing_from_fast_scan")).collect(java.util.stream.Collectors.toList());
        return new PipelineCoverage(scope.cameraId(), pipelineId(), PipelineRunStatus.INCOMPLETE,
                evidence, outcomes, 0, 0, true, "camera_missing_from_fast_scan");
    }

    private static PipelineEvidence coverageEvidence(CameraFastSnapshot camera) {
        PipelineEvidence source = camera.evidence();
        TreeSet<CandidateKey> raw = new TreeSet<>(source.rawFastCandidates());
        TreeMap<CandidateKey, VerificationOutcome> facts =
                new TreeMap<>(source.candidateEvidence());
        for (CandidateKey candidate : source.rawFastCandidates()) {
            if (candidate.kind() != CandidateKey.Kind.TUPLE) continue;
            CaptureModeTuple tuple = candidate.tuple().orElseThrow();
            CandidateKey video = CandidateKey.forVideo(source.cameraId(), source.codec(),
                    source.verificationPipelineId(), tuple.videoMode());
            raw.add(video);
            facts.put(video, VerificationOutcome.VERIFIED_PASS);
            raw.add(CandidateKey.forImage(source.cameraId(), source.codec(),
                    source.verificationPipelineId(), tuple.imageMode()));
        }
        for (var image : camera.imageModes()) {
            raw.add(CandidateKey.forImage(source.cameraId(), source.codec(),
                    source.verificationPipelineId(), image));
        }
        List<CandidateEvidence> evidence = new ArrayList<>();
        facts.forEach((candidate, outcome) ->
                evidence.add(new CandidateEvidence(candidate, outcome)));
        return new PipelineEvidence(source.cameraId(), source.codec(),
                source.verificationPipelineId(), source.availability(), raw, evidence);
    }

    private CandidateSets candidateSets(CameraScope scope, PipelineEvidence fastEvidence) {
        TreeSet<CandidateKey> raw = new TreeSet<>();
        raw.addAll(fastEvidence.rawFastCandidates());
        for (ImageMode image : scope.imageUniverse()) {
            raw.add(CandidateKey.forImage(scope.cameraId(), VideoCodec.H264,
                    pipelineId(), image));
        }
        for (CaptureModeTuple tuple : scope.candidateUniverse()) {
            raw.add(CandidateKey.forTuple(scope.cameraId(), VideoCodec.H264,
                    pipelineId(), tuple));
            raw.add(CandidateKey.forVideo(scope.cameraId(), VideoCodec.H264,
                    pipelineId(), tuple.videoMode()));
            raw.add(CandidateKey.forImage(scope.cameraId(), VideoCodec.H264,
                    pipelineId(), tuple.imageMode()));
        }
        return new CandidateSets(Set.copyOf(raw));
    }

    private Optional<CaptureModeTuple> bindingTuple(CandidateKey image,
            Collection<CandidateKey> rawCandidates) {
        TreeSet<CaptureModeTuple> exact = new TreeSet<>(CameraModeOrder.tuples());
        TreeSet<CaptureModeTuple> synthetic = new TreeSet<>(CameraModeOrder.tuples());
        for (CandidateKey candidate : rawCandidates) {
            if (candidate.kind() == CandidateKey.Kind.TUPLE
                    && candidate.imageMode().equals(image.imageMode())) {
                exact.add(candidate.tuple().orElseThrow());
            } else if (candidate.kind() == CandidateKey.Kind.VIDEO) {
                synthetic.add(new CaptureModeTuple(
                        candidate.videoMode().orElseThrow(),
                        image.imageMode().orElseThrow()));
            }
        }
        if (!exact.isEmpty()) return Optional.of(exact.last());
        return synthetic.isEmpty() ? Optional.empty() : Optional.of(synthetic.last());
    }
    private PipelineEvidence evidence(CameraId cameraId, Collection<CandidateKey> raw,
            Map<CandidateKey, VerificationOutcome> facts) {
        List<CandidateEvidence> evidence = facts.entrySet().stream()
                .map(entry -> new CandidateEvidence(entry.getKey(), entry.getValue())).collect(java.util.stream.Collectors.toList());
        return new PipelineEvidence(cameraId, VideoCodec.H264, pipelineId(),
                PipelineAvailability.AVAILABLE, raw, evidence);
    }

    private PipelineEvidence scopedImageEvidence(PipelineEvidence aggregate,
            CandidateKey image, CaptureModeTuple bindingTuple) {
        CandidateKey video = CandidateKey.forVideo(image.cameraId(), image.codec(),
                image.verificationPipelineId(), bindingTuple.videoMode());
        CandidateKey tuple = CandidateKey.forTuple(image.cameraId(), image.codec(),
                image.verificationPipelineId(), bindingTuple);
        List<CandidateEvidence> facts = new ArrayList<>();
        for (CandidateKey key : List.of(video, image, tuple)) {
            VerificationOutcome outcome = aggregate.outcome(key);
            if (outcome.isTerminalEvidence()) {
                facts.add(new CandidateEvidence(key, outcome));
            }
        }
        return new PipelineEvidence(image.cameraId(), image.codec(),
                image.verificationPipelineId(), PipelineAvailability.AVAILABLE,
                List.of(video, image, tuple), facts);
    }
    private PipelineEvidence scopedEvidence(PipelineEvidence aggregate, CandidateKey tuple) {
        CaptureModeTuple value = tuple.tuple().orElseThrow();
        CandidateKey video = CandidateKey.forVideo(tuple.cameraId(), tuple.codec(),
                tuple.verificationPipelineId(), value.videoMode());
        CandidateKey image = CandidateKey.forImage(tuple.cameraId(), tuple.codec(),
                tuple.verificationPipelineId(), value.imageMode());
        List<CandidateEvidence> facts = new ArrayList<>();
        for (CandidateKey key : List.of(video, image, tuple)) {
            VerificationOutcome outcome = aggregate.outcome(key);
            if (outcome.isTerminalEvidence()) facts.add(new CandidateEvidence(key, outcome));
        }
        return new PipelineEvidence(tuple.cameraId(), tuple.codec(), tuple.verificationPipelineId(),
                PipelineAvailability.AVAILABLE, List.of(video, image, tuple), facts);
    }

    private Snapshot scopedSnapshot(CameraPipelineBenchmarkPlan plan, CameraScope scope,
            PipelineEvidence evidence) {
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264,
                CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                List.of(evidence), Optional.empty());
        Integer sensorOrientationDegrees = sensorOrientationDegreesByCamera.get(scope.cameraId());
        if (sensorOrientationDegrees == null && frozenFastCoverage != null) {
            FrozenFastCoverage frozen = frozenFastCoverage.get(scope.cameraId());
            if (frozen != null) sensorOrientationDegrees = frozen.sensorOrientationDegrees();
        }
        if (sensorOrientationDegrees == null) {
            throw new IllegalStateException(
                    "camera orientation unavailable: " + scope.cameraId().value());
        }
        CameraSnapshot camera = new CameraSnapshot(scope.cameraId(), scope.hardwareSignature(),
                List.of(codec), Optional.empty(), Optional.empty(), sensorOrientationDegrees);
        return Snapshot.current(plan.environment().hardwareSignature(), List.of(), List.of(camera));
    }

    private PipelineEvidence pipeline(Snapshot snapshot) {
        return snapshot.cameras().get(0).codecs().get(0).pipelines().get(0);
    }


    private static TupleStage stage(List<VerifyCameraSelectionUseCase.Attempt> attempts) {
        if (attempts.isEmpty()) return TupleStage.UNKNOWN;
        return switch (attempts.get(attempts.size() - 1).stage()) {
            case COMBO -> TupleStage.COMBO;
            case ROLLBACK -> TupleStage.UNKNOWN;
        };
    }

    private static OptionalLong optional(Long value) {
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    @FunctionalInterface
    public interface EnvironmentValidator {
        EnvironmentValidation validate(
                CameraPipelineBenchmarkPlan.FrozenEnvironment environment);
    }

    public record EnvironmentValidation(boolean ready, String detail) {
        public EnvironmentValidation {
            detail = Objects.requireNonNull(detail, "detail");
            if (detail.isBlank()) throw new IllegalArgumentException("detail is required");
        }
    }

    @FunctionalInterface
    public interface CandidateVerifier {
        VerifyCameraSelectionUseCase.Result verify(CameraRuntimeOperations runtime,
                CameraCapabilityStore stagingStore,
                VerifyCameraSelectionUseCase.Request request);
    }

    @FunctionalInterface
    public interface StandaloneImageVerifier {
        VerifyStandaloneImageUseCase.Result verify(CameraRuntimeOperations runtime,
                VerifyStandaloneImageUseCase.Request request);
    }

    public record FrozenFastCoverage(
            PipelineEvidence evidence,
            long elapsedMillis,
            int sensorOrientationDegrees) {
        public FrozenFastCoverage {
            evidence = Objects.requireNonNull(evidence, "evidence");
            if (elapsedMillis < 0) throw new IllegalArgumentException("elapsedMillis");
            sensorOrientationDegrees = CameraOrientation.normalize(sensorOrientationDegrees);
        }

        public static FrozenFastCoverage from(CameraFastSnapshot camera) {
            Objects.requireNonNull(camera, "camera");
            return new FrozenFastCoverage(coverageEvidence(camera),
                    camera.probeElapsedMillis(), SharedCameraPipelineBenchmarkRunner.sensorOrientationDegrees(camera));
        }
    }

    private static int sensorOrientationDegrees(CameraFastSnapshot camera) {
        Integer sensorOrientationDegrees = camera.cameraFacts().sensorOrientationDegrees();
        if (sensorOrientationDegrees == null) {
            throw new IllegalStateException("camera sensor orientation unavailable: "
                    + camera.cameraFacts().cameraId().value());
        }
        return CameraOrientation.normalize(sensorOrientationDegrees);
    }

    private record CandidateSets(Set<CandidateKey> rawCandidates) {}
    private record CameraCoverageResult(PipelineCoverage coverage) {}
    private record ImageExactResult(VerificationOutcome outcome,
            PipelineEvidence evidence, String reason, long elapsedMillis,
            boolean cleanupComplete) {}
    private record ExactResult(VerificationOutcome outcome, PipelineEvidence evidence,
            TupleStage stage, String reason, long elapsedMillis, boolean cleanupComplete,
            Optional<CameraOperationContext> activeContext, ResourceSample resources) {}

    private static final class StagingStore implements CameraCapabilityStore {
        private Snapshot latest;
        private StagingStore(Snapshot initial) { latest = initial; }
        @Override public LoadResult load(Freshness freshness) { return LoadResult.loaded(latest); }
        @Override public void requestWrite(Snapshot snapshot) { latest = snapshot; }
    }

    private static final class BenchmarkingRuntime implements CameraRuntimeOperations {
        private final CameraRuntimeOperations delegate;
        private Long bindMillis;
        private Long previewMillis;
        private Long bindToPreviewMillis;
        private Long firstEncodedSampleMillis;
        private Long jpegCaptureMillis;
        private Long stopMillis;
        private Long finalizeMillis;
        private Long stopFinalizeMillis;
        private Long encoderStartedNanos;
        private Long encoderStoppedNanos;
        private CameraOperationContext lastEncoderContext;
        private CameraOperationContext lastContext;
        private CameraPipelineDiagnostics diagnostics;

        private BenchmarkingRuntime(CameraRuntimeOperations delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        private void reset() {
            bindMillis = null;
            previewMillis = null;
            bindToPreviewMillis = null;
            firstEncodedSampleMillis = null;
            jpegCaptureMillis = null;
            stopMillis = null;
            finalizeMillis = null;
            stopFinalizeMillis = null;
            encoderStartedNanos = null;
            encoderStoppedNanos = null;
            lastEncoderContext = null;
            lastContext = null;
            diagnostics = null;
        }

        @Override public CameraOperationResult bindSession(CameraOperationContext context) {
            lastContext = context;
            CameraOperationResult result = delegate.bindSession(context);
            bindMillis = result.elapsedMillis();
            return result;
        }

        @Override public CameraOperationResult bindStandaloneImageSession(
                CameraOperationContext context) {
            lastContext = context;
            CameraOperationResult result = delegate.bindStandaloneImageSession(context);
            bindMillis = result.elapsedMillis();
            return result;
        }

        @Override public CameraOperationResult updateSession(CameraOperationContext context) {
            lastContext = context;
            return delegate.updateSession(context);
        }

        @Override public CameraOperationResult previewProgress(CameraOperationContext context) {
            lastContext = context;
            CameraOperationResult result = delegate.previewProgress(context);
            previewMillis = result.elapsedMillis();
            if (bindMillis != null) bindToPreviewMillis = bindMillis + previewMillis;
            return result;
        }

        @Override public CameraOperationResult startEncoder(CameraOperationContext context) {
            lastContext = context;
            encoderStartedNanos = System.nanoTime();
            lastEncoderContext = context;
            CameraOperationResult result = delegate.startEncoder(context);
            firstEncodedSampleMillis = result.elapsedMillis();
            return result;
        }

        @Override public CameraOperationResult stopEncoder(CameraOperationContext context) {
            lastContext = context;
            encoderStoppedNanos = System.nanoTime();
            CameraOperationResult result = delegate.stopEncoder(context);
            stopMillis = result.elapsedMillis();
            updateStopFinalize();
            return result;
        }

        @Override public CameraOperationResult finalizeEncoder(CameraOperationContext context) {
            lastContext = context;
            CameraOperationResult result = delegate.finalizeEncoder(context);
            finalizeMillis = result.elapsedMillis();
            updateStopFinalize();
            return result;
        }

        @Override public CameraOperationResult captureJpeg(CameraOperationContext context) {
            lastContext = context;
            CameraOperationResult result = delegate.captureJpeg(context);
            jpegCaptureMillis = result.elapsedMillis();
            return result;
        }

        @Override public CameraOperationResult release(CameraOperationContext context) {
            lastContext = context;
            CameraOperationResult result = delegate.release(context);
            if (result.outcome() == CameraOperationOutcome.PASS) lastContext = null;
            return result;
        }

        @Override public CameraPipelineDiagnostics diagnostics(CameraOperationContext context) {
            lastContext = context;
            diagnostics = delegate.diagnostics(context);
            return diagnostics;
        }

        private void updateStopFinalize() {
            if (stopMillis != null && finalizeMillis != null) {
                stopFinalizeMillis = stopMillis + finalizeMillis;
            }
        }

        private OptionalDouble measuredFps() {
            if (diagnostics == null || diagnostics.encodedSampleCount() <= 0) {
                return OptionalDouble.empty();
            }
            long durationNanos = encoderStartedNanos == null || encoderStoppedNanos == null
                    ? 0 : Math.max(1, encoderStoppedNanos - encoderStartedNanos);
            return OptionalDouble.of(diagnostics.encodedSampleCount() * 1_000_000_000.0 / durationNanos);
        }

        private Optional<CameraOperationContext> lastContext() {
            return Optional.ofNullable(lastContext);
        }

        private OptionalLong droppedFrames() {
            if (diagnostics == null) return OptionalLong.empty();
            if (lastEncoderContext == null || encoderStartedNanos == null || encoderStoppedNanos == null) {
                return OptionalLong.empty();
            }
            long durationNanos = Math.max(1, encoderStoppedNanos - encoderStartedNanos);
            long expected = Math.round(lastEncoderContext.tuple().videoMode().framesPerSecond()
                    * durationNanos / 1_000_000_000.0);
            return OptionalLong.of(Math.max(0, expected - diagnostics.encodedSampleCount()));
        }
    }
}