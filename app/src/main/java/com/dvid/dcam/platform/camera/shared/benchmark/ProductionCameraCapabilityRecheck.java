package com.dvid.dcam.platform.camera.shared.benchmark;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraHealthGenerationProvider;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.CameraFastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase.PipelineRun;
import com.dvid.dcam.feature.device.application.usecase.FinalizeVerifiedCameraCapabilitiesUseCase;
import com.dvid.dcam.feature.device.application.usecase.FinalizeVerifiedCameraCapabilitiesUseCase.Summary;
import com.dvid.dcam.feature.device.application.usecase.VerifyCameraSelectionUseCase;
import com.dvid.dcam.feature.device.application.usecase.VerifyStandaloneImageUseCase;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.CameraScope;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.FrozenEnvironment;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineRunStatus;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.platform.camera.shared.SharedAvcEncoder;
import com.dvid.dcam.platform.camera.shared.egl.EglFanOutPipelineFactory;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativeSurfaceSharingPipelineFactory;
import com.dvid.dcam.platform.camera.shared.verification.SharedCameraVerificationRuntime;
import com.dvid.dcam.platform.device.capability.probe.egl.EglFanOutFastProbe;
import com.dvid.dcam.platform.device.capability.probe.nativesharing.NativeSurfaceSharingFastProbe;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

public final class ProductionCameraCapabilityRecheck {
    private static final String H264_CONFIGURATION =
            "device-default-avc-profile-level-bitrate";
    private static final String CROP_ROTATION_POLICY =
            "sensor-native-video-jpeg-preview-display-only";
    private static final String THERMAL_GATE = "thermal-status<=moderate";

    public record Progress(String profile, String cameraId, String stage,
            int completed, int total, String detail) {
        public Progress {
            profile = Objects.requireNonNull(profile, "profile");
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            stage = Objects.requireNonNull(stage, "stage");
            detail = Objects.requireNonNull(detail, "detail");
            if (profile.isBlank() || cameraId.isBlank() || stage.isBlank()
                    || detail.isBlank()) {
                throw new IllegalArgumentException("progress text is required");
            }
            if (completed < 0 || total <= 0 || completed > total) {
                throw new IllegalArgumentException("invalid progress range");
            }
        }
    }

    private final Context context;
    private final Logger logger;
    private final CameraHealthGenerationProvider healthGenerationProvider;
    private final File workingDirectory;

    public ProductionCameraCapabilityRecheck(Context context, Logger logger,
            CameraHealthGenerationProvider healthGenerationProvider) {
        Context applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.healthGenerationProvider = Objects.requireNonNull(
                healthGenerationProvider, "healthGenerationProvider");
        workingDirectory = new File(this.context.getFilesDir(),
                "camera-capability-recheck");
    }

    public RunResult execute(Snapshot baseline,
            BuildFastCameraCapabilitiesUseCase.Result resultA,
            BuildFastCameraCapabilitiesUseCase.Result resultB,
            Optional<VerificationPipelineId> forcedPipeline,
            Consumer<Progress> progressListener) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(resultA, "resultA");
        Objects.requireNonNull(resultB, "resultB");
        Objects.requireNonNull(forcedPipeline, "forcedPipeline");
        Objects.requireNonNull(progressListener, "progressListener");
        if (!resultA.complete() || !resultB.complete()) {
            return RunResult.failed("fast_scan_incomplete");
        }
        if (!workingDirectory.exists() && !workingDirectory.mkdirs()) {
            return RunResult.failed("working_directory_unavailable");
        }

        CameraPipelineBenchmarkPlan plan;
        try {
            plan = plan(baseline, resultA, resultB);
        } catch (RuntimeException error) {
            logger.warn("camera_capability_recheck stage=plan outcome=failed", error);
            return RunResult.failed("plan_failed:" + error.getClass().getSimpleName());
        }
        SharedCameraPipelineBenchmarkRunner runnerA = null;
        SharedCameraPipelineBenchmarkRunner runnerB = null;
        try {
            runnerA = runner(NativeSurfaceSharingFastProbe.PIPELINE_ID,
                    frozen(resultA), plan);
            runnerB = runner(EglFanOutFastProbe.PIPELINE_ID,
                    frozen(resultB), plan);
            PipelineRun coverageA = runCoverage(runnerA, plan,
                    resultA.benchmark().totalElapsedMillis(), "A", progressListener);
            if (!complete(coverageA)) {
                return RunResult.failed("pipeline_a_incomplete:" + coverageA.detail());
            }
            PipelineRun coverageB = runCoverage(runnerB, plan,
                    resultB.benchmark().totalElapsedMillis(), "B", progressListener);
            if (!complete(coverageB)) {
                return RunResult.failed("pipeline_b_incomplete:" + coverageB.detail());
            }
            FinalizeVerifiedCameraCapabilitiesUseCase.Result finalized =
                    new FinalizeVerifiedCameraCapabilitiesUseCase().execute(
                            new FinalizeVerifiedCameraCapabilitiesUseCase.Request(
                                    baseline, coverageA, coverageB, forcedPipeline));
            return RunResult.complete(finalized.snapshot(), finalized.summary());
        } catch (RuntimeException error) {
            logger.warn("camera_capability_recheck stage=deep_verify outcome=failed", error);
            return RunResult.failed(
                    "deep_verify_exception:" + error.getClass().getSimpleName());
        } finally {
            safeRelease(runnerA);
            safeRelease(runnerB);
            SharedAvcEncoder.releaseReusableCodecs(logger);
        }
    }

    private CameraPipelineBenchmarkPlan plan(Snapshot baseline,
            BuildFastCameraCapabilitiesUseCase.Result resultA,
            BuildFastCameraCapabilitiesUseCase.Result resultB) {
        Map<CameraId, CameraFastSnapshot> camerasA = cameras(resultA);
        Map<CameraId, CameraFastSnapshot> camerasB = cameras(resultB);
        List<CameraScope> scopes = new ArrayList<>();
        Map<CameraId, String> signatures = new TreeMap<>();
        for (var camera : baseline.cameras()) {
            CameraId cameraId = camera.cameraId();
            CameraFastSnapshot fastA = camerasA.get(cameraId);
            CameraFastSnapshot fastB = camerasB.get(cameraId);
            if (fastA == null || fastB == null) {
                throw new IllegalStateException("fast camera inventory mismatch");
            }
            Set<CaptureModeTuple> tuples = tuples(fastA, fastB);
            if (tuples.isEmpty()) {
                throw new IllegalStateException("camera has no fast tuple");
            }
            Set<ImageMode> images = imageModes(fastA, fastB, tuples);
            signatures.put(cameraId, camera.hardwareSignature());
            scopes.add(new CameraScope(cameraId, camera.hardwareSignature(), images, tuples));
        }
        FrozenEnvironment environment = new FrozenEnvironment(
                baseline.hardwareSignature(), signatures, VideoCodec.H264,
                H264_CONFIGURATION, CROP_ROTATION_POLICY,
                CameraOperationDeadline.CANDIDATE_TIMEOUT_MILLIS,
                workingDirectory.getAbsolutePath(), THERMAL_GATE);
        return new CameraPipelineBenchmarkPlan(environment, scopes,
                CameraPipelineBenchmarkPlan.BenchmarkProtocol.defaults());
    }

    private SharedCameraPipelineBenchmarkRunner runner(
            VerificationPipelineId pipelineId,
            Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> frozen,
            CameraPipelineBenchmarkPlan plan) {
        boolean pipelineA = pipelineId.equals(NativeSurfaceSharingFastProbe.PIPELINE_ID);
        int initialRotationDegrees = sensorOrientationDegrees(
                frozen, plan.cameras().get(0).cameraId());
        var pipeline = pipelineA
                ? new NativeSurfaceSharingPipelineFactory(
                        context, logger, workingDirectory).createBenchmark(
                                initialRotationDegrees)
                : new EglFanOutPipelineFactory(
                        context, logger, workingDirectory).createBenchmark(
                                initialRotationDegrees);
        CameraVerificationClock clock = CameraVerificationClock.system();
        var runtime = new SharedCameraVerificationRuntime(pipeline, logger, clock,
                operationContext -> {
                    int sensorOrientationDegrees = sensorOrientationDegrees(
                            frozen, operationContext.cameraId());
                    pipeline.setRotation(sensorOrientationDegrees);
                    logger.info("camera_capability_recheck stage=orientation outcome=applied"
                            + " cameraId=" + operationContext.cameraId().value()
                            + " sensorDegrees=" + sensorOrientationDegrees
                            + " outputDegrees=" + pipeline.outputRotationDegrees());
                });
        SharedCameraPipelineBenchmarkRunner.CandidateVerifier tupleVerifier =
                (cameraRuntime, stagingStore, request) ->
                        new VerifyCameraSelectionUseCase(cameraRuntime, stagingStore,
                                logger, clock, healthGenerationProvider).execute(request);
        SharedCameraPipelineBenchmarkRunner.StandaloneImageVerifier imageVerifier =
                (cameraRuntime, request) ->
                        new VerifyStandaloneImageUseCase(cameraRuntime, logger,
                                clock, healthGenerationProvider).execute(request);
        return new SharedCameraPipelineBenchmarkRunner(frozen, pipelineId, runtime,
                tupleVerifier, imageVerifier, logger, healthGenerationProvider,
                plan.environment(), environmentValidator(plan),
                new AndroidCameraBenchmarkTelemetry(context));
    }

    private static int sensorOrientationDegrees(
            Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> frozen,
            CameraId cameraId) {
        var coverage = frozen.get(cameraId);
        if (coverage == null) {
            throw new IllegalStateException(
                    "camera orientation unavailable: " + cameraId.value());
        }
        return coverage.sensorOrientationDegrees();
    }

    private PipelineRun runCoverage(SharedCameraPipelineBenchmarkRunner runner,
            CameraPipelineBenchmarkPlan plan, long fastScanMillis, String profile,
            Consumer<Progress> progressListener) {
        PipelineRun measured = runner.runCoverage(plan, () -> false, progress -> {
            logger.info("camera_capability_recheck stage=" + progress.stage()
                    + " completed=" + progress.completed()
                    + " total=" + progress.total()
                    + " detail=" + progress.detail());
            progressListener.accept(new Progress(profile,
                    detailValue(progress.detail(), "camera"), progress.stage(),
                    progress.completed(), progress.total(), progress.detail()));
        });
        PipelineRun result = new PipelineRun(measured.pipelineId(), measured.status(),
                measured.cameras(), fastScanMillis, measured.realVerifyMillis(),
                measured.cleanupComplete(), measured.detail());
        if (runner.release()) return result;
        return new PipelineRun(result.pipelineId(), PipelineRunStatus.INCOMPLETE,
                result.cameras(), result.fastScanMillis(), result.realVerifyMillis(),
                false, result.detail() + ":release_incomplete");
    }

    private static String detailValue(String detail, String key) {
        String prefix = key + "=";
        int start = detail.indexOf(prefix);
        if (start < 0) return "unknown";
        start += prefix.length();
        int end = detail.indexOf(' ', start);
        return end < 0 ? detail.substring(start) : detail.substring(start, end);
    }

    private SharedCameraPipelineBenchmarkRunner.EnvironmentValidator environmentValidator(
            CameraPipelineBenchmarkPlan plan) {
        PowerManager power = context.getSystemService(PowerManager.class);
        return requested -> {
            long thermalStatus = power == null || Build.VERSION.SDK_INT < 29
                    ? PowerManager.THERMAL_STATUS_NONE
                    : power.getCurrentThermalStatus();
            boolean ready = requested.equals(plan.environment())
                    && thermalStatus <= PowerManager.THERMAL_STATUS_MODERATE;
            return new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                    ready, "thermalStatus=" + thermalStatus + ",ready=" + ready);
        };
    }

    private static Map<CameraId,
            SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> frozen(
            BuildFastCameraCapabilitiesUseCase.Result result) {
        Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> values =
                new TreeMap<>();
        for (CameraFastSnapshot camera : result.authoritativeSnapshot()
                .orElseThrow().cameras()) {
            values.put(camera.cameraFacts().cameraId(),
                    SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage.from(camera));
        }
        return Map.copyOf(values);
    }

    private static Map<CameraId, CameraFastSnapshot> cameras(
            BuildFastCameraCapabilitiesUseCase.Result result) {
        Map<CameraId, CameraFastSnapshot> values = new TreeMap<>();
        for (CameraFastSnapshot camera : result.authoritativeSnapshot()
                .orElseThrow().cameras()) {
            values.put(camera.cameraFacts().cameraId(), camera);
        }
        return Map.copyOf(values);
    }

    private static Set<CaptureModeTuple> tuples(
            CameraFastSnapshot cameraA, CameraFastSnapshot cameraB) {
        TreeSet<CaptureModeTuple> result = new TreeSet<>(
                com.dvid.dcam.feature.device.domain.camera.CameraModeOrder.tuples());
        addTuples(result, cameraA);
        addTuples(result, cameraB);
        return Set.copyOf(result);
    }

    private static Set<ImageMode> imageModes(CameraFastSnapshot cameraA,
            CameraFastSnapshot cameraB, Set<CaptureModeTuple> tuples) {
        TreeSet<ImageMode> result = new TreeSet<>();
        result.addAll(cameraA.imageModes());
        result.addAll(cameraB.imageModes());
        for (CaptureModeTuple tuple : tuples) result.add(tuple.imageMode());
        return Set.copyOf(result);
    }

    private static void addTuples(Set<CaptureModeTuple> target,
            CameraFastSnapshot camera) {
        for (CandidateKey candidate : camera.evidence().rawFastCandidates()) {
            if (candidate.kind() == CandidateKey.Kind.TUPLE) {
                target.add(candidate.tuple().orElseThrow());
            }
        }
    }

    private static boolean complete(PipelineRun run) {
        return run.status() == PipelineRunStatus.COMPLETE
                && run.cleanupComplete()
                && run.cameras().stream().allMatch(coverage ->
                coverage.status() == PipelineRunStatus.COMPLETE
                        && coverage.cleanupComplete()
                        && coverage.unknownCount() == 0);
    }

    private static void safeRelease(SharedCameraPipelineBenchmarkRunner runner) {
        if (runner == null) return;
        try {
            runner.release();
        } catch (RuntimeException ignored) {
        }
    }

    public record RunResult(boolean complete, Optional<Snapshot> snapshot,
            Optional<Summary> summary, String detail) {
        public RunResult {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            summary = Objects.requireNonNull(summary, "summary");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
            if (complete != snapshot.isPresent() || complete != summary.isPresent()) {
                throw new IllegalArgumentException("recheck result state mismatch");
            }
        }

        public static RunResult complete(Snapshot snapshot, Summary summary) {
            return new RunResult(true, Optional.of(snapshot), Optional.of(summary),
                    "complete");
        }

        public static RunResult failed(String detail) {
            return new RunResult(false, Optional.empty(), Optional.empty(), detail);
        }
    }
}