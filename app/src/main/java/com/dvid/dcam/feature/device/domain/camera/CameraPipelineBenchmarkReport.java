package com.dvid.dcam.feature.device.domain.camera;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

public record CameraPipelineBenchmarkReport(
        Status status,
        CameraPipelineBenchmarkPlan plan,
        List<CameraComparison> cameras,
        long elapsedMillis,
        boolean publishedDurably,
        String detail) {
    public CameraPipelineBenchmarkReport {
        status = Objects.requireNonNull(status, "status");
        plan = Objects.requireNonNull(plan, "plan");
        cameras = immutableCameras(cameras);
        if (elapsedMillis < 0) throw new IllegalArgumentException("elapsedMillis");
        detail = required(detail, "detail");
        if (publishedDurably && status != Status.COMPLETE) {
            throw new IllegalArgumentException("incomplete report cannot be published");
        }
    }

    public enum Status { COMPLETE, INCOMPLETE, CANCELLED, FAILED }

    public record CameraComparison(
            CameraId cameraId,
            Set<CaptureModeTuple> candidateUniverse,
            PipelineCoverage pipelineA,
            PipelineCoverage pipelineB,
            PipelineComparison comparison,
            List<PerformanceSummary> performance) {
        public CameraComparison {
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            candidateUniverse = ordered(candidateUniverse);
            pipelineA = Objects.requireNonNull(pipelineA, "pipelineA");
            pipelineB = Objects.requireNonNull(pipelineB, "pipelineB");
            comparison = Objects.requireNonNull(comparison, "comparison");
            performance = List.copyOf(Objects.requireNonNull(performance, "performance"));
            if (!pipelineA.cameraId().equals(cameraId) || !pipelineB.cameraId().equals(cameraId)) {
                throw new IllegalArgumentException("pipeline camera mismatch");
            }
        }

        public Optional<CaptureModeTuple> bestCommonTuple() {
            return comparison.intersection().stream().max(CameraModeOrder.tuples());
        }
    }

    public record PipelineCoverage(
            CameraId cameraId,
            VerificationPipelineId pipelineId,
            PipelineRunStatus status,
            PipelineEvidence evidence,
            List<TupleOutcome> outcomes,
            long fastScanMillis,
            long realVerifyMillis,
            boolean cleanupComplete,
            String detail) {
        public PipelineCoverage {
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
            status = Objects.requireNonNull(status, "status");
            evidence = Objects.requireNonNull(evidence, "evidence");
            outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
            if (!evidence.cameraId().equals(cameraId)
                    || !evidence.verificationPipelineId().equals(pipelineId)) {
                throw new IllegalArgumentException("pipeline evidence identity mismatch");
            }
            if (fastScanMillis < 0 || realVerifyMillis < 0) {
                throw new IllegalArgumentException("coverage timing must not be negative");
            }
            detail = required(detail, "detail");
        }

        public long passCount() {
            return outcomes.stream().filter(value -> value.outcome() == VerificationOutcome.VERIFIED_PASS).count();
        }

        public long failCount() {
            return outcomes.stream().filter(value -> value.outcome() == VerificationOutcome.DEFINITIVE_UNSUPPORTED).count();
        }

        public long unknownCount() {
            return outcomes.size() - passCount() - failCount();
        }

        public Optional<CaptureModeTuple> bestVerifiedTuple() {
            return outcomes.stream().filter(value -> value.outcome() == VerificationOutcome.VERIFIED_PASS)
                    .map(TupleOutcome::tuple).max(CameraModeOrder.tuples());
        }
    }

    public record TupleOutcome(
            CaptureModeTuple tuple,
            VerificationOutcome outcome,
            TupleStage stage,
            String reason) {
        public TupleOutcome {
            tuple = Objects.requireNonNull(tuple, "tuple");
            outcome = Objects.requireNonNull(outcome, "outcome");
            stage = Objects.requireNonNull(stage, "stage");
            reason = required(reason, "reason");
        }
    }

    public record PerformanceSummary(
            CameraId cameraId,
            CaptureModeTuple tuple,
            List<PerformanceSample> samplesA,
            List<PerformanceSample> samplesB,
            MetricSummary fastScanA,
            MetricSummary fastScanB,
            MetricSummary bindToPreviewA,
            MetricSummary bindToPreviewB,
            MetricSummary firstEncodedSampleA,
            MetricSummary firstEncodedSampleB,
            MetricSummary jpegCaptureA,
            MetricSummary jpegCaptureB,
            MetricSummary stopFinalizeA,
            MetricSummary stopFinalizeB,
            MetricSummary totalVerifyA,
            MetricSummary totalVerifyB,
            MetricSummary measuredFpsA,
            MetricSummary measuredFpsB,
            MetricSummary droppedFramesA,
            MetricSummary droppedFramesB,
            ResourceSummary resourcesA,
            ResourceSummary resourcesB) {
        public PerformanceSummary {
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            tuple = Objects.requireNonNull(tuple, "tuple");
            samplesA = List.copyOf(Objects.requireNonNull(samplesA, "samplesA"));
            samplesB = List.copyOf(Objects.requireNonNull(samplesB, "samplesB"));
            fastScanA = Objects.requireNonNull(fastScanA, "fastScanA");
            fastScanB = Objects.requireNonNull(fastScanB, "fastScanB");
            bindToPreviewA = Objects.requireNonNull(bindToPreviewA, "bindToPreviewA");
            bindToPreviewB = Objects.requireNonNull(bindToPreviewB, "bindToPreviewB");
            firstEncodedSampleA = Objects.requireNonNull(firstEncodedSampleA, "firstEncodedSampleA");
            firstEncodedSampleB = Objects.requireNonNull(firstEncodedSampleB, "firstEncodedSampleB");
            jpegCaptureA = Objects.requireNonNull(jpegCaptureA, "jpegCaptureA");
            jpegCaptureB = Objects.requireNonNull(jpegCaptureB, "jpegCaptureB");
            stopFinalizeA = Objects.requireNonNull(stopFinalizeA, "stopFinalizeA");
            stopFinalizeB = Objects.requireNonNull(stopFinalizeB, "stopFinalizeB");
            totalVerifyA = Objects.requireNonNull(totalVerifyA, "totalVerifyA");
            totalVerifyB = Objects.requireNonNull(totalVerifyB, "totalVerifyB");
            measuredFpsA = Objects.requireNonNull(measuredFpsA, "measuredFpsA");
            measuredFpsB = Objects.requireNonNull(measuredFpsB, "measuredFpsB");
            droppedFramesA = Objects.requireNonNull(droppedFramesA, "droppedFramesA");
            droppedFramesB = Objects.requireNonNull(droppedFramesB, "droppedFramesB");
            resourcesA = Objects.requireNonNull(resourcesA, "resourcesA");
            resourcesB = Objects.requireNonNull(resourcesB, "resourcesB");
        }
    }

    public record PerformanceSample(
            VerificationPipelineId pipelineId,
            CameraId cameraId,
            CaptureModeTuple tuple,
            boolean warmup,
            int block,
            int position,
            long fastScanMillis,
            OptionalLong bindToPreviewMillis,
            OptionalLong firstEncodedSampleMillis,
            OptionalLong jpegCaptureMillis,
            OptionalLong stopFinalizeMillis,
            OptionalLong totalVerifyMillis,
            OptionalDouble measuredFps,
            OptionalLong droppedFrames,
            ResourceSample resources) {
        public PerformanceSample {
            pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            tuple = Objects.requireNonNull(tuple, "tuple");
            if (block < 0 || position < 0 || fastScanMillis < 0) {
                throw new IllegalArgumentException("sample metadata invalid");
            }
            bindToPreviewMillis = nonNegative(bindToPreviewMillis, "bindToPreviewMillis");
            firstEncodedSampleMillis = nonNegative(firstEncodedSampleMillis, "firstEncodedSampleMillis");
            jpegCaptureMillis = nonNegative(jpegCaptureMillis, "jpegCaptureMillis");
            stopFinalizeMillis = nonNegative(stopFinalizeMillis, "stopFinalizeMillis");
            totalVerifyMillis = nonNegative(totalVerifyMillis, "totalVerifyMillis");
            measuredFps = nonNegative(measuredFps, "measuredFps");
            droppedFrames = nonNegative(droppedFrames, "droppedFrames");
            resources = Objects.requireNonNull(resources, "resources");
        }
    }

    public record ResourceSample(
            OptionalDouble cpuLoad,
            OptionalDouble gpuLoad,
            OptionalLong memoryBytes,
            OptionalLong thermalStatus) {
        public ResourceSample {
            cpuLoad = nonNegative(cpuLoad, "cpuLoad");
            gpuLoad = nonNegative(gpuLoad, "gpuLoad");
            memoryBytes = nonNegative(memoryBytes, "memoryBytes");
            thermalStatus = nonNegative(thermalStatus, "thermalStatus");
        }

        public static ResourceSample unavailable() {
            return new ResourceSample(OptionalDouble.empty(), OptionalDouble.empty(),
                    OptionalLong.empty(), OptionalLong.empty());
        }
    }

    public record ResourceSummary(
            MetricSummary cpuLoad,
            MetricSummary gpuLoad,
            MetricSummary memoryBytes,
            MetricSummary thermalStatus) {
        public ResourceSummary {
            cpuLoad = Objects.requireNonNull(cpuLoad, "cpuLoad");
            gpuLoad = Objects.requireNonNull(gpuLoad, "gpuLoad");
            memoryBytes = Objects.requireNonNull(memoryBytes, "memoryBytes");
            thermalStatus = Objects.requireNonNull(thermalStatus, "thermalStatus");
        }
    }

    public record MetricSummary(
            OptionalDouble median,
            OptionalDouble p95,
            int sampleCount) {
        public MetricSummary {
            median = nonNegative(median, "median");
            p95 = nonNegative(p95, "p95");
            if (sampleCount < 0) throw new IllegalArgumentException("sampleCount");
        }

        public static MetricSummary from(Collection<Double> values) {
            List<Double> sorted = new ArrayList<>();
            for (Double value : Objects.requireNonNull(values, "values")) {
                if (value != null && value >= 0) sorted.add(value);
            }
            sorted.sort(Comparator.naturalOrder());
            if (sorted.isEmpty()) return new MetricSummary(OptionalDouble.empty(), OptionalDouble.empty(), 0);
            return new MetricSummary(OptionalDouble.of(percentile(sorted, 0.50)),
                    sorted.size() >= 2 ? OptionalDouble.of(percentile(sorted, 0.95))
                            : OptionalDouble.empty(), sorted.size());
        }

        private static double percentile(List<Double> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }
    }

    public enum PipelineRunStatus { COMPLETE, UNAVAILABLE, INCOMPLETE, CANCELLED, FAILED }
    public enum TupleStage { FAST_SCAN, VIDEO_SOLO, IMAGE_SOLO, COMBO, SKIPPED_DEPENDENCY, UNKNOWN }

    private static List<CameraComparison> immutableCameras(Collection<CameraComparison> values) {
        List<CameraComparison> result = new ArrayList<>(Objects.requireNonNull(values, "cameras"));
        result.sort(Comparator.comparing(CameraComparison::cameraId));
        return List.copyOf(result);
    }

    private static Set<CaptureModeTuple> ordered(Collection<CaptureModeTuple> values) {
        TreeSet<CaptureModeTuple> result = new TreeSet<>(CameraModeOrder.tuples());
        for (CaptureModeTuple value : Objects.requireNonNull(values, "candidateUniverse")) {
            result.add(Objects.requireNonNull(value, "candidate tuple"));
        }
        return Collections.unmodifiableSet(result);
    }

    private static OptionalLong nonNegative(OptionalLong value, String name) {
        OptionalLong checked = Objects.requireNonNull(value, name);
        if (checked.isPresent() && checked.orElseThrow() < 0) throw new IllegalArgumentException(name);
        return checked;
    }

    private static OptionalDouble nonNegative(OptionalDouble value, String name) {
        OptionalDouble checked = Objects.requireNonNull(value, name);
        if (checked.isPresent() && checked.orElseThrow() < 0) throw new IllegalArgumentException(name);
        return checked;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}