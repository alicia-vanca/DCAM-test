package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineComparisonSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.CameraScope;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.CameraComparison;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.MetricSummary;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PerformanceSample;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PerformanceSummary;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineCoverage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineRunStatus;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.ResourceSummary;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleStage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.ResourceSample;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.BenchmarkProtocol;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.FrozenEnvironment;
import com.dvid.dcam.feature.device.domain.camera.CameraModeOrder;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparison;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparisonInput;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public final class CompareCameraPipelinesUseCase {
    private final DurableComparisonPublisher snapshotPublisher;
    private final PipelineRunner pipelineA;
    private final PipelineRunner pipelineB;
    private final Logger logger;
    private final LongConsumer sleeper;

    public CompareCameraPipelinesUseCase(DurableComparisonPublisher snapshotPublisher,
            PipelineRunner pipelineA, PipelineRunner pipelineB, Logger logger) {
        this(snapshotPublisher, pipelineA, pipelineB, logger, millis -> {
            if (millis <= 0) return;
            try {
                Thread.sleep(millis);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("benchmark cooldown interrupted", error);
            }
        });
    }

    public CompareCameraPipelinesUseCase(DurableComparisonPublisher snapshotPublisher,
            PipelineRunner pipelineA, PipelineRunner pipelineB, Logger logger,
            LongConsumer sleeper) {
        this.snapshotPublisher = Objects.requireNonNull(snapshotPublisher, "snapshotPublisher");
        this.pipelineA = Objects.requireNonNull(pipelineA, "pipelineA");
        this.pipelineB = Objects.requireNonNull(pipelineB, "pipelineB");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        if (pipelineA.pipelineId().equals(pipelineB.pipelineId())) {
            throw new IllegalArgumentException("A/B comparison needs different pipeline IDs");
        }
    }

    public synchronized Result execute(Request request) {
        return execute(request, () -> false, progress -> {});
    }

    public synchronized Result execute(Request request,
            BooleanSupplier cancellationSignal, Consumer<Progress> progressListener) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellationSignal, "cancellationSignal");
        Objects.requireNonNull(progressListener, "progressListener");
        progressListener = safeProgress(progressListener);
        long startedNanos = System.nanoTime();
        validateRequest(request);
        progress(progressListener, "frozen", 0, 0, "candidate_universe_frozen");
        if (cancellationSignal.getAsBoolean()) {
            return cancelled(request, startedNanos, "cancelled_before_pipeline_a");
        }

        PipelineRun runA = null;
        PipelineRun runB = null;
        try {
            progress(progressListener, "pipeline_a_fast_and_real", 0, 2, "start");
            runA = pipelineA.runCoverage(request.plan(), cancellationSignal, progressListener);
            runA = withCleanup(runA, pipelineA.release());
            if (!runA.cleanupComplete() || cancellationSignal.getAsBoolean()) {
                return incomplete(request, startedNanos, runA, null,
                        cancellationSignal.getAsBoolean()
                                ? CameraPipelineBenchmarkReport.Status.CANCELLED
                                : CameraPipelineBenchmarkReport.Status.INCOMPLETE,
                        "pipeline_a_incomplete");
            }
            progress(progressListener, "pipeline_b_fast_and_real", 1, 2, "start");
            runB = pipelineB.runCoverage(request.plan(), cancellationSignal, progressListener);
            runB = withCleanup(runB, pipelineB.release());
            if (!runB.cleanupComplete() || cancellationSignal.getAsBoolean()) {
                return incomplete(request, startedNanos, runA, runB,
                        cancellationSignal.getAsBoolean()
                                ? CameraPipelineBenchmarkReport.Status.CANCELLED
                                : CameraPipelineBenchmarkReport.Status.INCOMPLETE,
                        "pipeline_b_incomplete");
            }
            if (!isComplete(runA) || !isComplete(runB)) {
                return incomplete(request, startedNanos, runA, runB,
                        CameraPipelineBenchmarkReport.Status.INCOMPLETE,
                        "pipeline_coverage_incomplete");
            }

            List<CameraComparison> comparisons = compareCoverage(
                    request.plan(), runA, runB, Map.of(), Map.of());
            if (comparisons.stream().anyMatch(value ->
                    value.comparison().status() != PipelineComparison.Status.COMPLETE)) {
                return incomplete(request, startedNanos, runA, runB,
                        CameraPipelineBenchmarkReport.Status.INCOMPLETE,
                        "real_verification_unknown");
            }

            Map<CameraId, List<PerformanceSample>> samplesA = new TreeMap<>();
            Map<CameraId, List<PerformanceSample>> samplesB = new TreeMap<>();
            for (CameraComparison comparison : comparisons) {
                for (CaptureModeTuple tuple : comparison.comparison().intersection()) {
                    List<PerformanceSample> samples = benchmarkTuple(
                            request.plan(), comparison.cameraId(), tuple,
                            cancellationSignal, progressListener);
                    if (samples == null) {
                        return incomplete(request, startedNanos, runA, runB,
                                cancellationSignal.getAsBoolean()
                                        ? CameraPipelineBenchmarkReport.Status.CANCELLED
                                        : CameraPipelineBenchmarkReport.Status.INCOMPLETE,
                                "performance_incomplete");
                    }
                    for (PerformanceSample sample : samples) {
                        Map<CameraId, List<PerformanceSample>> target =
                                sample.pipelineId().equals(pipelineA.pipelineId()) ? samplesA : samplesB;
                        target.computeIfAbsent(sample.cameraId(), ignored -> new ArrayList<>()).add(sample);
                    }
                }
            }

            List<CameraComparison> finalCoverage = compareCoverage(
                    request.plan(), runA, runB,
                    medianTotalForBestCommon(comparisons, samplesA, pipelineA.pipelineId()),
                    medianTotalForBestCommon(comparisons, samplesB, pipelineB.pipelineId()));
            List<CameraComparison> finalComparisons = withPerformance(
                    finalCoverage, samplesA, samplesB);
            if (cancellationSignal.getAsBoolean()) {
                return incomplete(request, startedNanos, runA, runB,
                        CameraPipelineBenchmarkReport.Status.CANCELLED,
                        "cancelled_before_publish");
            }
            Snapshot fresh = buildFreshSnapshot(request.snapshot(), finalComparisons,
                    runA, runB, samplesA, samplesB);
            CameraPipelineBenchmarkReport completeReport = new CameraPipelineBenchmarkReport(
                    CameraPipelineBenchmarkReport.Status.COMPLETE, request.plan(),
                    finalComparisons, elapsedMillis(startedNanos), true, "complete_published");
            boolean published = publish(fresh, completeReport);
            CameraPipelineBenchmarkReport report = published ? completeReport
                    : new CameraPipelineBenchmarkReport(
                            CameraPipelineBenchmarkReport.Status.INCOMPLETE, request.plan(),
                            finalComparisons, elapsedMillis(startedNanos), false,
                            "storage_publish_failed");
            progress(progressListener, "publish", 2, 2,
                    report.status().name().toLowerCase() + " published=" + published);
            return new Result(report, published ? Optional.of(fresh) : Optional.empty());
        } catch (RuntimeException error) {
            logger.warn("camera_pipeline_comparison outcome=failed", error);
            safeRelease(pipelineA);
            safeRelease(pipelineB);
            return incomplete(request, startedNanos, runA, runB,
                    CameraPipelineBenchmarkReport.Status.FAILED,
                    "exception:" + error.getClass().getSimpleName());
        } finally {
            safeRelease(pipelineA);
            safeRelease(pipelineB);
        }
    }

    private List<PerformanceSample> benchmarkTuple(CameraPipelineBenchmarkPlan plan,
            CameraId cameraId, CaptureModeTuple tuple, BooleanSupplier cancellation,
            Consumer<Progress> progress) {
        List<PerformanceSample> result = new ArrayList<>();
        List<Invocation> schedule = invocations(plan.protocol());
        for (int index = 0; index < schedule.size(); index++) {
            Invocation invocation = schedule.get(index);
            if (cancellation.getAsBoolean()) return null;
            PipelineRunner runner = invocation.pipeline() == PipelineSide.A
                    ? pipelineA : pipelineB;
            progress(progress, "performance", index, schedule.size(),
                    cameraId + " tuple=" + tuple + " warmup=" + invocation.warmup());
            if (index > 0 && plan.protocol().cooldownMillis() > 0) {
                sleeper.accept(plan.protocol().cooldownMillis());
            }
            Measurement measurement = runner.measure(plan, cameraId, tuple,
                    invocation, cancellation);
            if (!measurement.complete() || measurement.sample().isEmpty()) {
                logger.info("camera_pipeline_benchmark measurement_incomplete pipeline="
                        + runner.pipelineId() + " camera=" + cameraId + " tuple=" + tuple
                        + " detail=" + measurement.detail());
                return null;
            }
            PerformanceSample sample = measurement.sample().orElseThrow();
            if (!hasRequiredMetrics(sample)) {
                logger.info("camera_pipeline_benchmark measurement_incomplete pipeline="
                        + runner.pipelineId() + " camera=" + cameraId + " tuple=" + tuple
                        + " detail=missing_required_metrics");
                return null;
            }
            result.add(sample);
            if (!runner.release()) return null;
        }
        return List.copyOf(result);
    }

    private List<CameraComparison> compareCoverage(CameraPipelineBenchmarkPlan plan,
            PipelineRun runA, PipelineRun runB,
            Map<CameraId, OptionalLong> mediansA,
            Map<CameraId, OptionalLong> mediansB) {
        List<CameraComparison> result = new ArrayList<>();
        for (CameraScope scope : plan.cameras()) {
            PipelineCoverage a = runA.coverage(scope.cameraId());
            PipelineCoverage b = runB.coverage(scope.cameraId());
            PipelineComparison comparison = PipelineComparison.compare(scope.candidateUniverse(),
                    new PipelineComparisonInput(a.evidence(),
                            a.status() == PipelineRunStatus.COMPLETE,
                            mediansA.getOrDefault(scope.cameraId(), OptionalLong.empty())),
                    new PipelineComparisonInput(b.evidence(),
                            b.status() == PipelineRunStatus.COMPLETE,
                            mediansB.getOrDefault(scope.cameraId(), OptionalLong.empty())));
            result.add(new CameraComparison(scope.cameraId(), scope.candidateUniverse(),
                    a, b, comparison, List.of()));
        }
        return List.copyOf(result);
    }

    private List<CameraComparison> withPerformance(List<CameraComparison> comparisons,
            Map<CameraId, List<PerformanceSample>> samplesA,
            Map<CameraId, List<PerformanceSample>> samplesB) {
        List<CameraComparison> result = new ArrayList<>();
        for (CameraComparison comparison : comparisons) {
            List<PerformanceSummary> performance = new ArrayList<>();
            for (CaptureModeTuple tuple : comparison.comparison().intersection()) {
                List<PerformanceSample> a = samplesFor(samplesA, comparison.cameraId(), tuple,
                        pipelineA.pipelineId());
                List<PerformanceSample> b = samplesFor(samplesB, comparison.cameraId(), tuple,
                        pipelineB.pipelineId());
                performance.add(new PerformanceSummary(comparison.cameraId(), tuple, a, b,
                        metricDistinct(a, value -> value.fastScanMillis()),
                        metricDistinct(b, value -> value.fastScanMillis()),
                        metricOptionalLong(a, value -> value.bindToPreviewMillis()),
                        metricOptionalLong(b, value -> value.bindToPreviewMillis()),
                        metricOptionalLong(a, value -> value.firstEncodedSampleMillis()),
                        metricOptionalLong(b, value -> value.firstEncodedSampleMillis()),
                        metricOptionalLong(a, value -> value.jpegCaptureMillis()),
                        metricOptionalLong(b, value -> value.jpegCaptureMillis()),
                        metricOptionalLong(a, value -> value.stopFinalizeMillis()),
                        metricOptionalLong(b, value -> value.stopFinalizeMillis()),
                        metricOptionalLong(a, value -> value.totalVerifyMillis()),
                        metricOptionalLong(b, value -> value.totalVerifyMillis()),
                        metricOptionalDouble(a, value -> value.measuredFps()),
                        metricOptionalDouble(b, value -> value.measuredFps()),
                        metricOptionalLong(a, value -> value.droppedFrames()),
                        metricOptionalLong(b, value -> value.droppedFrames()),
                        resources(a), resources(b)));
            }
            result.add(new CameraComparison(comparison.cameraId(), comparison.candidateUniverse(),
                    comparison.pipelineA(), comparison.pipelineB(), comparison.comparison(), performance));
        }
        return List.copyOf(result);
    }

    private List<PerformanceSample> samplesFor(Map<CameraId, List<PerformanceSample>> samples,
            CameraId cameraId, CaptureModeTuple tuple, VerificationPipelineId pipelineId) {
        return samples.getOrDefault(cameraId, List.of()).stream()
                .filter(value -> value.pipelineId().equals(pipelineId) && value.tuple().equals(tuple))
                .collect(java.util.stream.Collectors.toList());
    }

    private static boolean hasRequiredMetrics(PerformanceSample sample) {
        return sample.bindToPreviewMillis().isPresent()
                && sample.firstEncodedSampleMillis().isPresent()
                && sample.jpegCaptureMillis().isPresent()
                && sample.stopFinalizeMillis().isPresent()
                && sample.totalVerifyMillis().isPresent()
                && sample.measuredFps().isPresent()
                && sample.droppedFrames().isPresent();
    }
    private static MetricSummary metric(List<PerformanceSample> samples,
            ToLongFunction<PerformanceSample> extractor) {
        return MetricSummary.from(samples.stream().filter(value -> !value.warmup())
                .map(value -> (double) extractor.applyAsLong(value)).collect(java.util.stream.Collectors.toList()));
    }

    private static MetricSummary metricDistinct(List<PerformanceSample> samples,
            ToLongFunction<PerformanceSample> extractor) {
        return MetricSummary.from(samples.stream().filter(value -> !value.warmup())
                .map(value -> extractor.applyAsLong(value)).distinct()
                .map(value -> (double) value).collect(java.util.stream.Collectors.toList()));
    }

    private static MetricSummary metricOptionalLong(List<PerformanceSample> samples,
            java.util.function.Function<PerformanceSample, OptionalLong> extractor) {
        return MetricSummary.from(samples.stream().filter(value -> !value.warmup())
                .flatMap(value -> extractor.apply(value).isPresent()
                        ? java.util.stream.Stream.of((double) extractor.apply(value).orElseThrow())
                        : java.util.stream.Stream.empty()).collect(java.util.stream.Collectors.toList()));
    }

    private static MetricSummary metricOptionalDouble(List<PerformanceSample> samples,
            java.util.function.Function<PerformanceSample, OptionalDouble> extractor) {
        return MetricSummary.from(samples.stream().filter(value -> !value.warmup())
                .flatMap(value -> extractor.apply(value).isPresent()
                        ? java.util.stream.Stream.of(extractor.apply(value).orElseThrow())
                        : java.util.stream.Stream.empty()).collect(java.util.stream.Collectors.toList()));
    }

    private static ResourceSummary resources(List<PerformanceSample> samples) {
        List<PerformanceSample> measured = samples.stream().filter(value -> !value.warmup()).collect(java.util.stream.Collectors.toList());
        return new ResourceSummary(
                MetricSummary.from(measured.stream().flatMap(value -> value.resources().cpuLoad().isPresent()
                        ? java.util.stream.Stream.of(value.resources().cpuLoad().orElseThrow())
                        : java.util.stream.Stream.empty()).collect(java.util.stream.Collectors.toList())),
                MetricSummary.from(measured.stream().flatMap(value -> value.resources().gpuLoad().isPresent()
                        ? java.util.stream.Stream.of(value.resources().gpuLoad().orElseThrow())
                        : java.util.stream.Stream.empty()).collect(java.util.stream.Collectors.toList())),
                MetricSummary.from(measured.stream().flatMap(value -> value.resources().memoryBytes().isPresent()
                        ? java.util.stream.Stream.of((double) value.resources().memoryBytes().orElseThrow())
                        : java.util.stream.Stream.empty()).collect(java.util.stream.Collectors.toList())),
                MetricSummary.from(measured.stream().flatMap(value -> value.resources().thermalStatus().isPresent()
                        ? java.util.stream.Stream.of((double) value.resources().thermalStatus().orElseThrow())
                        : java.util.stream.Stream.empty()).collect(java.util.stream.Collectors.toList())));
    }
    private Map<CameraId, OptionalLong> medianTotalForBestCommon(
            List<CameraComparison> comparisons,
            Map<CameraId, List<PerformanceSample>> samples,
            VerificationPipelineId pipelineId) {
        Map<CameraId, OptionalLong> result = new TreeMap<>();
        for (CameraComparison comparison : comparisons) {
            Optional<CaptureModeTuple> best = comparison.bestCommonTuple();
            if (best.isEmpty()) {
                result.put(comparison.cameraId(), OptionalLong.empty());
                continue;
            }
            List<Double> values = samples.getOrDefault(comparison.cameraId(), List.of()).stream()
                    .filter(value -> value.pipelineId().equals(pipelineId)
                            && !value.warmup() && value.tuple().equals(best.orElseThrow()))
                    .flatMap(value -> value.totalVerifyMillis().isPresent()
                            ? java.util.stream.Stream.of((double) value.totalVerifyMillis().orElseThrow())
                            : java.util.stream.Stream.empty())
                    .collect(java.util.stream.Collectors.toList());
            MetricSummary summary = MetricSummary.from(values);
            result.put(comparison.cameraId(), summary.median().isPresent()
                    ? OptionalLong.of(Math.round(summary.median().orElseThrow()))
                    : OptionalLong.empty());
        }
        return result;
    }

    private Snapshot buildFreshSnapshot(Snapshot baseline,
            List<CameraComparison> comparisons, PipelineRun runA, PipelineRun runB,
            Map<CameraId, List<PerformanceSample>> samplesA,
            Map<CameraId, List<PerformanceSample>> samplesB) {
        Map<CameraId, CameraComparison> byCamera = new HashMap<>();
        comparisons.forEach(value -> byCamera.put(value.cameraId(), value));
        List<CameraSnapshot> cameras = new ArrayList<>();
        for (CameraSnapshot camera : baseline.cameras()) {
            CameraComparison comparison = byCamera.get(camera.cameraId());
            if (comparison == null) {
                cameras.add(camera);
                continue;
            }
            List<PipelineEvidence> replacements = List.of(
                    comparison.pipelineA().evidence(), comparison.pipelineB().evidence());
            List<CodecSnapshot> codecs = new ArrayList<>();
            for (CodecSnapshot codec : camera.codecs()) {
                if (codec.codec() != VideoCodec.H264) {
                    codecs.add(codec);
                    continue;
                }
                List<PipelineEvidence> pipelines = new ArrayList<>();
                for (PipelineEvidence pipeline : codec.pipelines()) {
                    if (!pipeline.verificationPipelineId().equals(pipelineA.pipelineId())
                            && !pipeline.verificationPipelineId().equals(pipelineB.pipelineId())) {
                        pipelines.add(pipeline);
                    }
                }
                pipelines.addAll(replacements);
                OptionalLong medianA = totalMedian(comparison.cameraId(), comparison.bestCommonTuple(),
                        samplesA, pipelineA.pipelineId());
                OptionalLong medianB = totalMedian(comparison.cameraId(), comparison.bestCommonTuple(),
                        samplesB, pipelineB.pipelineId());
                codecs.add(new CodecSnapshot(VideoCodec.H264,
                        CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(), pipelines,
                        Optional.of(new PipelineComparisonSnapshot(comparison.comparison(), medianA, medianB))));
            }
            if (codecs.stream().noneMatch(value -> value.codec() == VideoCodec.H264)) {
                OptionalLong medianA = totalMedian(comparison.cameraId(), comparison.bestCommonTuple(),
                        samplesA, pipelineA.pipelineId());
                OptionalLong medianB = totalMedian(comparison.cameraId(), comparison.bestCommonTuple(),
                        samplesB, pipelineB.pipelineId());
                codecs.add(new CodecSnapshot(VideoCodec.H264,
                        CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(), replacements,
                        Optional.of(new PipelineComparisonSnapshot(comparison.comparison(), medianA, medianB))));
            }
            Optional<CameraCapabilityStore.SelectedPipeline> selectedPipeline =
                    camera.selectedPipeline();
            if (selectedPipeline.isEmpty() || selectedPipeline.filter(value -> value.mode()
                    == CameraCapabilityStore.PipelineSelectionMode.AUTO).isPresent()) {
                CameraSnapshot compared = new CameraSnapshot(camera.cameraId(),
                        camera.hardwareSignature(), codecs, Optional.empty(),
                        camera.selectedRecordingProfile(), camera.sensorOrientationDegrees());
                SelectCameraPipelineUseCase.Decision decision =
                        new SelectCameraPipelineUseCase().decision(compared).orElseThrow();
                selectedPipeline = Optional.of(
                        CameraCapabilityStore.SelectedPipeline.autoVerified(
                                decision.pipeline().verificationPipelineId(),
                                decision.reason()));
            }
            Optional<CameraCapabilityStore.SelectedPipeline> finalSelectedPipeline =
                    selectedPipeline;
            Optional<SelectedRecordingProfile> recordingProfile =
                    camera.selectedRecordingProfile()
                            .filter(profile -> finalSelectedPipeline.isEmpty()
                                    || finalSelectedPipeline.orElseThrow().pipelineId().equals(
                                    profile.verificationPipelineId()));
            cameras.add(new CameraSnapshot(camera.cameraId(), camera.hardwareSignature(), codecs,
                    selectedPipeline, recordingProfile, camera.sensorOrientationDegrees()));
        }
        CameraCapabilityStore.InitializationState state =
                baseline.initializationState();
        if (!cameras.isEmpty()) {
            CameraId mainCameraId = baseline.cameraOrderOverride().isEmpty()
                    ? cameras.get(0).cameraId() : baseline.cameraOrderOverride().get(0);
            boolean mainProfileSelected = cameras.stream()
                    .filter(camera -> camera.cameraId().equals(mainCameraId))
                    .findFirst().flatMap(CameraSnapshot::selectedRecordingProfile)
                    .isPresent();
            if (!mainProfileSelected) {
                state = CameraCapabilityStore.InitializationState.INCOMPLETE;
            }
        }
        return new Snapshot(baseline.format(), state,
                baseline.hardwareSignature(), baseline.cameraOrderOverride(), cameras);
    }

    private OptionalLong totalMedian(CameraId cameraId, Optional<CaptureModeTuple> tuple,
            Map<CameraId, List<PerformanceSample>> samples, VerificationPipelineId pipelineId) {
        if (tuple.isEmpty()) return OptionalLong.empty();
        List<Double> values = samples.getOrDefault(cameraId, List.of()).stream()
                .filter(value -> value.pipelineId().equals(pipelineId)
                        && !value.warmup() && value.tuple().equals(tuple.orElseThrow()))
                .flatMap(value -> value.totalVerifyMillis().isPresent()
                        ? java.util.stream.Stream.of((double) value.totalVerifyMillis().orElseThrow())
                        : java.util.stream.Stream.empty())
                .collect(java.util.stream.Collectors.toList());
        MetricSummary summary = MetricSummary.from(values);
        return summary.median().isPresent()
                ? OptionalLong.of(Math.round(summary.median().orElseThrow()))
                : OptionalLong.empty();
    }

    private boolean publish(Snapshot snapshot, CameraPipelineBenchmarkReport report) {
        try {
            return snapshotPublisher.publishDurably(snapshot, report);
        } catch (RuntimeException error) {
            logger.warn("camera_pipeline_comparison publish_failed", error);
            return false;
        }
    }

    private void validateRequest(Request request) {
        FrozenEnvironment environment = request.plan().environment();
        Snapshot baseline = request.snapshot();
        if (!baseline.hardwareSignature().equals(environment.hardwareSignature())) {
            throw new IllegalArgumentException("benchmark hardware signature changed");
        }
        Set<CameraId> baselineCameraIds = baseline.cameras().stream()
                .map(CameraSnapshot::cameraId).collect(java.util.stream.Collectors.toSet());
        if (!baselineCameraIds.equals(environment.cameraHardwareSignatures().keySet())) {
            throw new IllegalArgumentException("benchmark camera inventory changed");
        }
        for (CameraScope scope : request.plan().cameras()) {
            CameraSnapshot camera = baseline.cameras().stream()
                    .filter(value -> value.cameraId().equals(scope.cameraId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("camera missing from snapshot"));
            if (!camera.hardwareSignature().equals(scope.hardwareSignature())) {
                throw new IllegalArgumentException("camera hardware signature changed");
            }
        }
    }

    private Result incomplete(Request request, long startedNanos, PipelineRun runA,
            PipelineRun runB, CameraPipelineBenchmarkReport.Status status, String detail) {
        List<CameraComparison> comparisons = partialComparisons(request.plan(), runA, runB);
        CameraPipelineBenchmarkReport report = new CameraPipelineBenchmarkReport(
                status, request.plan(), comparisons, elapsedMillis(startedNanos), false, detail);
        return new Result(report, Optional.empty());
    }

    private Result cancelled(Request request, long startedNanos, String detail) {
        return incomplete(request, startedNanos, null, null,
                CameraPipelineBenchmarkReport.Status.CANCELLED, detail);
    }

    private List<CameraComparison> partialComparisons(CameraPipelineBenchmarkPlan plan,
            PipelineRun runA, PipelineRun runB) {
        List<CameraComparison> result = new ArrayList<>();
        for (CameraScope scope : plan.cameras()) {
            PipelineCoverage a = coverageOrEmpty(runA, scope, pipelineA.pipelineId());
            PipelineCoverage b = coverageOrEmpty(runB, scope, pipelineB.pipelineId());
            PipelineComparison comparison = PipelineComparison.compare(scope.candidateUniverse(),
                    PipelineComparisonInput.withoutPerformance(a.evidence(), false),
                    PipelineComparisonInput.withoutPerformance(b.evidence(), false));
            result.add(new CameraComparison(scope.cameraId(), scope.candidateUniverse(), a, b, comparison, List.of()));
        }
        return List.copyOf(result);
    }

    private PipelineCoverage coverageOrEmpty(PipelineRun run, CameraScope scope,
            VerificationPipelineId pipelineId) {
        if (run == null) return emptyCoverage(scope, pipelineId);
        return run.cameras().stream()
                .filter(value -> value.cameraId().equals(scope.cameraId()))
                .findFirst().orElseGet(() -> emptyCoverage(scope, pipelineId));
    }

    private PipelineCoverage emptyCoverage(CameraScope scope, VerificationPipelineId pipelineId) {
        PipelineEvidence evidence = new PipelineEvidence(scope.cameraId(), VideoCodec.H264,
                pipelineId, com.dvid.dcam.feature.device.domain.camera.PipelineAvailability.UNAVAILABLE,
                List.of(), List.of());
        List<TupleOutcome> outcomes = scope.candidateUniverse().stream()
                .map(tuple -> new TupleOutcome(tuple, VerificationOutcome.UNKNOWN,
                        TupleStage.UNKNOWN, "not_run")).collect(java.util.stream.Collectors.toList());
        return new PipelineCoverage(scope.cameraId(), pipelineId, PipelineRunStatus.INCOMPLETE,
                evidence, outcomes, 0, 0, false, "not_run");
    }

    private static PipelineRun withCleanup(PipelineRun run, boolean cleanup) {
        if (run == null || run.cleanupComplete() == cleanup) return run;
        PipelineRunStatus status = run.status() == PipelineRunStatus.COMPLETE && !cleanup
                ? PipelineRunStatus.INCOMPLETE : run.status();
        return new PipelineRun(run.pipelineId(), status, run.cameras(),
                run.fastScanMillis(), run.realVerifyMillis(), cleanup,
                run.detail() + (cleanup ? ":cleanup_recovered" : ":cleanup_incomplete"));
    }

    private static boolean isComplete(PipelineRun run) {
        return run != null && run.status() == PipelineRunStatus.COMPLETE && run.cleanupComplete();
    }

    private static List<Invocation> invocations(BenchmarkProtocol protocol) {
        List<Invocation> result = new ArrayList<>();
        for (var invocation : com.dvid.dcam.feature.device.domain.camera.PipelineBenchmarkSchedule.abba(protocol)) {
            result.add(new Invocation(invocation.pipeline() == CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A
                    ? PipelineSide.A : PipelineSide.B, invocation.warmup(), invocation.block(), invocation.position()));
        }
        return List.copyOf(result);
    }

    private Consumer<Progress> safeProgress(Consumer<Progress> listener) {
        return value -> {
            try {
                listener.accept(value);
            } catch (RuntimeException error) {
                logger.warn("camera_pipeline_comparison progress_callback_failed", error);
            }
        };
    }

    private static void progress(Consumer<Progress> listener, String stage,
            int completed, int total, String detail) {
        listener.accept(new Progress(stage, completed, total, detail));
    }

    private static long elapsedMillis(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static void safeRelease(PipelineRunner runner) {
        try { runner.release(); } catch (RuntimeException ignored) {}
    }

    public record Request(Snapshot snapshot, CameraPipelineBenchmarkPlan plan) {
        public Request {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            plan = Objects.requireNonNull(plan, "plan");
        }
    }

    public record Result(CameraPipelineBenchmarkReport report, Optional<Snapshot> freshSnapshot) {
        public Result {
            report = Objects.requireNonNull(report, "report");
            freshSnapshot = Objects.requireNonNull(freshSnapshot, "freshSnapshot");
        }
    }

    public abstract static class DurableComparisonPublisher {
        public abstract boolean publishDurably(Snapshot snapshot,
                CameraPipelineBenchmarkReport report);
    }

    public abstract static class PipelineRunner {
        public abstract VerificationPipelineId pipelineId();
        public abstract PipelineRun runCoverage(CameraPipelineBenchmarkPlan plan,
                BooleanSupplier cancellationSignal, Consumer<Progress> progressListener);
        public abstract Measurement measure(CameraPipelineBenchmarkPlan plan, CameraId cameraId,
                CaptureModeTuple tuple, Invocation invocation,
                BooleanSupplier cancellationSignal);
        public abstract boolean release();
    }

    public record PipelineRun(
            VerificationPipelineId pipelineId,
            PipelineRunStatus status,
            List<PipelineCoverage> cameras,
            long fastScanMillis,
            long realVerifyMillis,
            boolean cleanupComplete,
            String detail) {
        public PipelineRun {
            pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
            status = Objects.requireNonNull(status, "status");
            cameras = List.copyOf(Objects.requireNonNull(cameras, "cameras"));
            if (fastScanMillis < 0 || realVerifyMillis < 0) throw new IllegalArgumentException("timing");
            detail = required(detail, "detail");
        }

        public PipelineCoverage coverage(CameraId cameraId) {
            return cameras.stream().filter(value -> value.cameraId().equals(cameraId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("pipeline camera missing"));
        }
    }

    public record Measurement(boolean complete, Optional<PerformanceSample> sample, String detail) {
        public Measurement {
            sample = Objects.requireNonNull(sample, "sample");
            detail = required(detail, "detail");
            if (complete != sample.isPresent()) throw new IllegalArgumentException("measurement state mismatch");
        }

        public static Measurement incomplete(String detail) {
            return new Measurement(false, Optional.empty(), detail);
        }
    }
public record Progress(String stage, int completed, int total, String detail) {
        public Progress {
            stage = required(stage, "stage");
            if (completed < 0 || total < 0 || completed > total) throw new IllegalArgumentException("progress");
            detail = required(detail, "detail");
        }
    }

    public record Invocation(PipelineSide pipeline, boolean warmup, int block, int position) {}
    public enum PipelineSide { A, B }


    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}