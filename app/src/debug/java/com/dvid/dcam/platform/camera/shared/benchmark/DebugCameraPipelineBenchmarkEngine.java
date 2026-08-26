package com.dvid.dcam.platform.camera.shared.benchmark;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.PowerManager;
import android.util.JsonWriter;
import androidx.annotation.NonNull;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraHealthGenerationProvider;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.CameraFastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase.PipelineRun;
import com.dvid.dcam.feature.device.application.usecase.DebugCameraBenchmarkUseCase;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.CameraScope;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.FrozenEnvironment;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.CameraComparison;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineCoverage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineRunStatus;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleOutcome;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.platform.camera.shared.egl.EglFanOutPipelineFactory;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativeSurfaceSharingPipelineFactory;
import com.dvid.dcam.platform.camera.shared.verification.SharedCameraVerificationRuntime;
import com.dvid.dcam.platform.device.capability.catalog.AndroidCameraCatalogSource;
import com.dvid.dcam.platform.device.capability.probe.egl.EglFanOutFastProbe;
import com.dvid.dcam.platform.device.capability.fast.AndroidFastCameraCapabilityProbe;
import com.dvid.dcam.platform.device.capability.probe.nativesharing.NativeSurfaceSharingFastProbe;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DebugCameraPipelineBenchmarkEngine
        implements DebugCameraBenchmarkUseCase.Runner {
    private static final long CANDIDATE_TIMEOUT_MILLIS = 5_000;
    private static final String H264_CONFIGURATION = "device-default-avc-profile-level-bitrate";
    private static final String CROP_ROTATION_POLICY = "sensor-native-video-jpeg-preview-display-only";
    private static final String THERMAL_POLICY = "thermal-status-not-gated";

    private final Context context;
    private final Logger logger;
    private final CameraCapabilityStore capabilityStore;
    private final File workingDirectory;
    private final Set<String> unavailableCameraIds = ConcurrentHashMap.newKeySet();
    private volatile File lastReport;
    private volatile List<String> cameraIds = List.of();

    public DebugCameraPipelineBenchmarkEngine(Context context, Logger logger,
            CameraCapabilityStore capabilityStore) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.capabilityStore = Objects.requireNonNull(capabilityStore, "capabilityStore");
        this.workingDirectory = new File(this.context.getFilesDir(), "camera-devmode");
        if (!workingDirectory.exists() && !workingDirectory.mkdirs()) {
            throw new IllegalStateException("camera-devmode directory unavailable");
        }
        CameraManager cameraManager = this.context.getSystemService(CameraManager.class);
        if (cameraManager != null) {
            cameraManager.registerAvailabilityCallback(
                    new CameraManager.AvailabilityCallback() {
                        @Override public void onCameraUnavailable(@NonNull String cameraId) {
                            unavailableCameraIds.add(cameraId);
                        }

                        @Override public void onCameraAvailable(@NonNull String cameraId) {
                            unavailableCameraIds.remove(cameraId);
                        }
                    }, new Handler(Looper.getMainLooper()));
        }
    }

    public boolean isCameraIdle() {
        return unavailableCameraIds.isEmpty();
    }

    @Override public List<String> cameraIds() {
        try {
            List<String> result = new AndroidCameraCatalogSource(context, logger).load().cameras()
                    .stream().map(value -> value.cameraId().value()).sorted().collect(Collectors.toList());
            cameraIds = result;
            return result;
        } catch (Exception error) {
            logger.warn("camera_devmode stage=catalog outcome=failed", error);
            return cameraIds;
        }
    }

    @Override public DebugCameraBenchmarkUseCase.RunResult run(
            DebugCameraBenchmarkUseCase.Request request,
            BooleanSupplier cancellationSignal,
            Consumer<DebugCameraBenchmarkUseCase.Progress> progressListener) {
        long startedNanos = System.nanoTime();
        try {
            progress(progressListener, "fast_scan", 0, 4, "start");
            FastResults fast = fastResults(request, cancellationSignal, progressListener);
            if (fast.cancelled()) return cancelled(startedNanos, "cancelled_during_fast_scan");
            if (!fast.completeFor(request.pipelineSelection())) {
                return incomplete(startedNanos, "fast_scan_incomplete:" + fast.detail());
            }
            CameraPipelineBenchmarkPlan plan = plan(request, fast);
            progress(progressListener, "real_verify", 1, 4,
                    "cameras=" + plan.cameras().size());
            if (request.pipelineSelection() == DebugCameraBenchmarkUseCase.PipelineSelection.BOTH) {
                return runBoth(fast, plan, cancellationSignal, progressListener,
                        startedNanos);
            }
            return runSingle(request, fast, plan, cancellationSignal, progressListener,
                    startedNanos);
        } catch (RuntimeException error) {
            logger.warn("camera_devmode outcome=failed", error);
            return new DebugCameraBenchmarkUseCase.RunResult(
                    DebugCameraBenchmarkUseCase.Status.FAILED,
                    "failed:" + error.getClass().getSimpleName(), Optional.empty());
        }
    }

    @Override public DebugCameraBenchmarkUseCase.ExportResult exportLastReport() {
        File source = lastReport;
        if (source == null || !source.isFile()) {
            return new DebugCameraBenchmarkUseCase.ExportResult(false,
                    "no complete report", Optional.empty());
        }
        File root = context.getExternalFilesDir("camera-devmode-reports");
        if (root == null) root = new File(context.getFilesDir(), "camera-devmode-reports");
        if (!root.exists() && !root.mkdirs()) {
            return new DebugCameraBenchmarkUseCase.ExportResult(false,
                    "report directory unavailable", Optional.empty());
        }
        File target = new File(root, "camera-pipeline-ab-" + System.currentTimeMillis() + ".json");
        try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return new DebugCameraBenchmarkUseCase.ExportResult(true,
                    "exported", Optional.of(target.getAbsolutePath()));
        } catch (IOException error) {
            logger.warn("camera_devmode stage=export outcome=failed", error);
            return new DebugCameraBenchmarkUseCase.ExportResult(false,
                    "export failed:" + error.getClass().getSimpleName(), Optional.empty());
        }
    }

    private DebugCameraBenchmarkUseCase.RunResult runBoth(
            FastResults fast,
            CameraPipelineBenchmarkPlan plan, BooleanSupplier cancellationSignal,
            Consumer<DebugCameraBenchmarkUseCase.Progress> progressListener,
            long startedNanos) {
        File reportFile = new File(workingDirectory, "last-comparison.json");
        CompareCameraPipelinesUseCase.DurableComparisonPublisher publisher =
                new CompareCameraPipelinesUseCase.DurableComparisonPublisher() {
                    @Override public boolean publishDurably(Snapshot snapshot,
                            CameraPipelineBenchmarkReport report) {
                        if (!publishComparison(reportFile, report)) return false;
                        capabilityStore.requestWrite(snapshot);
                        return true;
                    }
                };
        SharedCameraPipelineBenchmarkRunner runnerA = createRunner(
                NativeSurfaceSharingPipelineFactory.PIPELINE_ID, fast.fastA(), plan);
        SharedCameraPipelineBenchmarkRunner runnerB = createRunner(
                EglFanOutPipelineFactory.PIPELINE_ID, fast.fastB(), plan);
        Snapshot baseline = baseline(fast, plan);
        CompareCameraPipelinesUseCase useCase = new CompareCameraPipelinesUseCase(
                publisher, runnerA, runnerB, logger);
        CompareCameraPipelinesUseCase.Result result = useCase.execute(
                new CompareCameraPipelinesUseCase.Request(baseline, plan),
                cancellationSignal, value -> progress(progressListener, value.stage(),
                        Math.min(4, value.completed() + 1), Math.max(4, value.total() + 1),
                        value.detail()));
        if (result.report().status() == CameraPipelineBenchmarkReport.Status.CANCELLED
                || cancellationSignal.getAsBoolean()) {
            return cancelled(startedNanos, result.report().detail());
        }
        if (result.report().status() != CameraPipelineBenchmarkReport.Status.COMPLETE) {
            return incomplete(startedNanos, result.report().detail());
        }
        lastReport = reportFile;
        progress(progressListener, "complete", 4, 4,
                summary(result.report()));
        return new DebugCameraBenchmarkUseCase.RunResult(
                DebugCameraBenchmarkUseCase.Status.COMPLETE,
                summary(result.report()), Optional.of(reportFile.getAbsolutePath()));
    }

    private DebugCameraBenchmarkUseCase.RunResult runSingle(
            DebugCameraBenchmarkUseCase.Request request, FastResults fast,
            CameraPipelineBenchmarkPlan plan, BooleanSupplier cancellationSignal,
            Consumer<DebugCameraBenchmarkUseCase.Progress> progressListener,
            long startedNanos) {
        boolean pipelineA = request.pipelineSelection()
                == DebugCameraBenchmarkUseCase.PipelineSelection.A;
        VerificationPipelineId pipelineId = pipelineA
                ? NativeSurfaceSharingPipelineFactory.PIPELINE_ID
                : EglFanOutPipelineFactory.PIPELINE_ID;
        SharedCameraPipelineBenchmarkRunner runner = createRunner(
                pipelineId, pipelineA ? fast.fastA() : fast.fastB(), plan);
        PipelineRun run;
        boolean cleanup;
        try {
            run = runner.runCoverage(plan, cancellationSignal,
                    value -> progress(progressListener, value.stage(),
                            Math.min(4, value.completed() + 1),
                            Math.max(4, value.total() + 1), value.detail()));
        } finally {
            cleanup = runner.release();
        }
        if (cancellationSignal.getAsBoolean() || run.status() == PipelineRunStatus.CANCELLED) {
            return cancelled(startedNanos, run.detail());
        }
        if (run.status() != PipelineRunStatus.COMPLETE || !cleanup) {
            return incomplete(startedNanos, run.detail());
        }
        File reportFile = new File(workingDirectory, "last-single.json");
        try {
            writeSingleReport(reportFile, request, run);
        } catch (IOException error) {
            logger.warn("camera_devmode stage=single_report outcome=failed", error);
            return incomplete(startedNanos, "report_write_failed");
        }
        lastReport = reportFile;
        String summary = singleSummary(run);
        progress(progressListener, "complete", 4, 4, summary);
        return new DebugCameraBenchmarkUseCase.RunResult(
                DebugCameraBenchmarkUseCase.Status.COMPLETE, summary,
                Optional.of(reportFile.getAbsolutePath()));
    }

    private FastResults fastResults(DebugCameraBenchmarkUseCase.Request request,
            BooleanSupplier cancellationSignal,
            Consumer<DebugCameraBenchmarkUseCase.Progress> progressListener) {
        BuildFastCameraCapabilitiesUseCase.Result resultA = null;
        BuildFastCameraCapabilitiesUseCase.Result resultB = null;
        if (request.pipelineSelection() != DebugCameraBenchmarkUseCase.PipelineSelection.B) {
            resultA = fastResult(new AndroidFastCameraCapabilityProbe(
                            new NativeSurfaceSharingFastProbe(context, logger), logger),
                    cancellationSignal);
            progress(progressListener, "fast_scan_a", 1, 4, resultA.detail());
        }
        if (cancellationSignal.getAsBoolean()) {
            return FastResults.cancelled(resultA);
        }
        if (request.pipelineSelection() != DebugCameraBenchmarkUseCase.PipelineSelection.A) {
            resultB = fastResult(new AndroidFastCameraCapabilityProbe(
                            new EglFanOutFastProbe(context, logger), logger),
                    cancellationSignal);
            progress(progressListener, "fast_scan_b", 2, 4, resultB.detail());
        }
        return new FastResults(resultA, resultB, false,
                resultA != null ? resultA.detail() : resultB.detail());
    }

    private BuildFastCameraCapabilitiesUseCase.Result fastResult(
            com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe probe,
            BooleanSupplier cancellationSignal) {
        return new BuildFastCameraCapabilitiesUseCase(
                new AndroidCameraCatalogSource(context, logger), logger).execute(
                        probe, cancellationSignal::getAsBoolean);
    }

    private CameraPipelineBenchmarkPlan plan(
            DebugCameraBenchmarkUseCase.Request request, FastResults fast) {
        BuildFastCameraCapabilitiesUseCase.FastSnapshot source = fast.source();
        Map<CameraId, CameraFastSnapshot> a = byCamera(fast.resultA());
        Map<CameraId, CameraFastSnapshot> b = byCamera(fast.resultB());
        TreeSet<CameraId> selectedIds = new TreeSet<>();
        if (request.scope() == DebugCameraBenchmarkUseCase.Scope.SINGLE_CAMERA) {
            selectedIds.add(new CameraId(request.cameraId().orElseThrow()));
        } else {
            selectedIds.addAll(a.keySet());
            selectedIds.addAll(b.keySet());
        }
        List<CameraScope> scopes = new ArrayList<>();
        Map<CameraId, String> signatures = new TreeMap<>();
        for (CameraId cameraId : selectedIds) {
            CameraFastSnapshot camera = a.get(cameraId) != null ? a.get(cameraId) : b.get(cameraId);
            if (camera == null) throw new IllegalArgumentException("camera missing:" + cameraId);
            Set<CaptureModeTuple> candidates = tuples(request.pipelineSelection(),
                    a.get(cameraId), b.get(cameraId));
            if (candidates.isEmpty()) continue;
            String signature = camera.cameraFacts().toString();
            signatures.put(cameraId, signature);
            scopes.add(new CameraScope(cameraId, signature, candidates));
        }
        if (scopes.isEmpty()) throw new IllegalArgumentException("no fast candidate tuple");
        String storagePath = workingDirectory.getAbsolutePath();
        FrozenEnvironment environment = new FrozenEnvironment(
                source.rawCatalog().hardwareSignatureInput(), signatures, VideoCodec.H264,
                H264_CONFIGURATION, CROP_ROTATION_POLICY, CANDIDATE_TIMEOUT_MILLIS,
                storagePath, THERMAL_POLICY);
        CameraPipelineBenchmarkPlan.BenchmarkProtocol protocol =
                new CameraPipelineBenchmarkPlan.BenchmarkProtocol(
                        request.measuredBlocks(),
                        request.initialOrder() == DebugCameraBenchmarkUseCase.InitialOrder.A
                                ? CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A
                                : CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_B,
                        request.cooldownMillis());
        return new CameraPipelineBenchmarkPlan(environment, scopes, protocol);
    }

    private SharedCameraPipelineBenchmarkRunner createRunner(
            VerificationPipelineId pipelineId,
            Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> frozenFast,
            CameraPipelineBenchmarkPlan plan) {
        boolean pipelineA = pipelineId.equals(NativeSurfaceSharingPipelineFactory.PIPELINE_ID);
        int initialRotationDegrees = sensorOrientationDegrees(
                frozenFast, plan.cameras().get(0).cameraId());
        var pipeline = pipelineA
                ? new NativeSurfaceSharingPipelineFactory(context, logger, workingDirectory, () -> null)
                        .createHeadless(initialRotationDegrees)
                : new EglFanOutPipelineFactory(context, logger, workingDirectory, () -> null)
                        .createHeadless(initialRotationDegrees);
        CameraVerificationClock clock = CameraVerificationClock.system();
        var runtime = new SharedCameraVerificationRuntime(pipeline, logger, clock,
                operationContext -> {
                    int sensorOrientationDegrees = sensorOrientationDegrees(
                            frozenFast, operationContext.cameraId());
                    pipeline.setRotation(sensorOrientationDegrees);
                    logger.info("camera_devmode stage=orientation outcome=applied"
                            + " cameraId=" + operationContext.cameraId().value()
                            + " sensorDegrees=" + sensorOrientationDegrees
                            + " outputDegrees=" + pipeline.outputRotationDegrees());
                });
        CameraHealthGenerationProvider health = ignored -> 0;
        var verifier = (SharedCameraPipelineBenchmarkRunner.CandidateVerifier)
                (cameraRuntime, stagingStore, verifyRequest) ->
                        new com.dvid.dcam.feature.device.application.usecase.VerifyCameraSelectionUseCase(
                                cameraRuntime, stagingStore, logger,
                                clock, health).execute(verifyRequest);
        var imageVerifier = (SharedCameraPipelineBenchmarkRunner.StandaloneImageVerifier)
                (cameraRuntime, verifyRequest) ->
                        new com.dvid.dcam.feature.device.application.usecase.VerifyStandaloneImageUseCase(
                                cameraRuntime, logger, clock, health).execute(verifyRequest);
        return new SharedCameraPipelineBenchmarkRunner(frozenFast, pipelineId, runtime,
                verifier, imageVerifier, logger, health, plan.environment(),
                environmentValidator(plan), new AndroidCameraBenchmarkTelemetry(context));
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

    private SharedCameraPipelineBenchmarkRunner.EnvironmentValidator environmentValidator(
            CameraPipelineBenchmarkPlan plan) {
        PowerManager power = context.getSystemService(PowerManager.class);
        return requested -> {
            long thermalStatus = power == null || Build.VERSION.SDK_INT < 29
                    ? Long.MAX_VALUE : power.getCurrentThermalStatus();
            boolean ready = requested.equals(plan.environment());
            return new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                    ready, "thermalStatus=" + thermalStatus + ",ready=" + ready);
        };
    }

    private Snapshot baseline(FastResults fast, CameraPipelineBenchmarkPlan plan) {
        Map<CameraId, CameraFastSnapshot> a = byCamera(fast.resultA());
        Map<CameraId, CameraFastSnapshot> b = byCamera(fast.resultB());
        Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> frozenA =
                fast.fastA();
        Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> frozenB =
                fast.fastB();
        List<CameraSnapshot> cameras = new ArrayList<>();
        for (CameraScope scope : plan.cameras()) {
            List<PipelineEvidence> pipelines = new ArrayList<>();
            CameraFastSnapshot fastA = a.get(scope.cameraId());
            if (fastA != null) pipelines.add(fastA.evidence());
            CameraFastSnapshot fastB = b.get(scope.cameraId());
            if (fastB != null) pipelines.add(fastB.evidence());
            var frozen = frozenA.containsKey(scope.cameraId()) ? frozenA : frozenB;
            cameras.add(new CameraSnapshot(scope.cameraId(), scope.hardwareSignature(),
                    List.of(new CodecSnapshot(VideoCodec.H264,
                            CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                            pipelines, Optional.empty())), Optional.empty(), Optional.empty(),
                    sensorOrientationDegrees(frozen, scope.cameraId())));
        }
        return Snapshot.current(plan.environment().hardwareSignature(), List.of(), cameras);
    }

    private boolean publishComparison(File file, CameraPipelineBenchmarkReport report) {
        try {
            CameraPipelineBenchmarkReportJson.writeAtomic(file, report);
            return true;
        } catch (IOException error) {
            logger.warn("camera_devmode stage=publish outcome=failed", error);
            return false;
        }
    }

    private void writeSingleReport(File target, DebugCameraBenchmarkUseCase.Request request,
            PipelineRun run) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary);
                OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                JsonWriter json = new JsonWriter(writer)) {
            json.beginObject();
            json.name("status").value(run.status().name());
            json.name("pipeline").value(request.pipelineSelection().name());
            json.name("codec").value(request.codec().name());
            json.name("elapsedMillis").value(run.fastScanMillis() + run.realVerifyMillis());
            json.name("detail").value(run.detail());
            json.name("cameras").beginArray();
            for (PipelineCoverage coverage : run.cameras()) writeCoverage(json, coverage);
            json.endArray();
            json.endObject();
        }
        Files.move(temporary.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void writeCoverage(JsonWriter json, PipelineCoverage coverage) throws IOException {
        json.beginObject();
        json.name("cameraId").value(coverage.cameraId().value());
        json.name("pipelineId").value(coverage.pipelineId().value());
        json.name("status").value(coverage.status().name());
        json.name("availability").value(coverage.evidence().availability().name());
        json.name("fastScanMillis").value(coverage.fastScanMillis());
        json.name("realVerifyMillis").value(coverage.realVerifyMillis());
        json.name("passCount").value(coverage.passCount());
        json.name("failCount").value(coverage.failCount());
        json.name("unknownCount").value(coverage.unknownCount());
        json.name("outcomes").beginArray();
        for (TupleOutcome outcome : coverage.outcomes()) {
            json.beginObject();
            json.name("tuple").value(outcome.tuple().toString());
            json.name("outcome").value(outcome.outcome().name());
            json.name("stage").value(outcome.stage().name());
            json.name("reason").value(outcome.reason());
            json.endObject();
        }
        json.endArray();
        json.endObject();
    }

    private static Set<CaptureModeTuple> tuples(
            DebugCameraBenchmarkUseCase.PipelineSelection selection,
            CameraFastSnapshot a, CameraFastSnapshot b) {
        TreeSet<CaptureModeTuple> result = new TreeSet<>(com.dvid.dcam.feature.device.domain.camera.CameraModeOrder.tuples());
        if (selection != DebugCameraBenchmarkUseCase.PipelineSelection.B
                && a != null) addTuples(result, a.evidence());
        if (selection != DebugCameraBenchmarkUseCase.PipelineSelection.A
                && b != null) addTuples(result, b.evidence());
        return result;
    }

    private static void addTuples(Collection<CaptureModeTuple> target, PipelineEvidence evidence) {
        for (CandidateKey candidate : evidence.rawFastCandidates()) {
            if (candidate.kind() == CandidateKey.Kind.TUPLE) {
                target.add(candidate.tuple().orElseThrow());
            }
        }
    }

    private static Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> frozen(
            BuildFastCameraCapabilitiesUseCase.Result result) {
        if (result == null || !result.complete()) return Map.of();
        Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> values = new TreeMap<>();
        for (CameraFastSnapshot camera : result.authoritativeSnapshot().orElseThrow().cameras()) {
            values.put(camera.cameraFacts().cameraId(),
                    SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage.from(camera));
        }
        return values;
    }

    private static Map<CameraId, CameraFastSnapshot> byCamera(
            BuildFastCameraCapabilitiesUseCase.Result result) {
        if (result == null || !result.complete()) return Map.of();
        Map<CameraId, CameraFastSnapshot> values = new TreeMap<>();
        for (CameraFastSnapshot camera : result.authoritativeSnapshot().orElseThrow().cameras()) {
            values.put(camera.cameraFacts().cameraId(), camera);
        }
        return values;
    }

    private static DebugCameraBenchmarkUseCase.RunResult cancelled(long startedNanos, String detail) {
        return new DebugCameraBenchmarkUseCase.RunResult(
                DebugCameraBenchmarkUseCase.Status.CANCELLED,
                "cancelled:" + detail + ",elapsedMs=" + elapsedMillis(startedNanos), Optional.empty());
    }

    private static DebugCameraBenchmarkUseCase.RunResult incomplete(long startedNanos, String detail) {
        return new DebugCameraBenchmarkUseCase.RunResult(
                DebugCameraBenchmarkUseCase.Status.INCOMPLETE,
                "incomplete:" + detail + ",elapsedMs=" + elapsedMillis(startedNanos), Optional.empty());
    }

    private String summary(CameraPipelineBenchmarkReport report) {
        StringBuilder result = new StringBuilder("status=").append(report.status())
                .append(" elapsedMs=").append(report.elapsedMillis())
                .append(" logs=").append(logPath());
        for (CameraComparison camera : report.cameras()) {
            var comparison = camera.comparison();
            result.append("\ncamera=").append(camera.cameraId())
                    .append(" coverage=").append(comparison.coverage())
                    .append(" recommendation=").append(comparison.recommendation())
                    .append(" A-status=").append(camera.pipelineA().status())
                    .append(" A-availability=").append(camera.pipelineA().evidence().availability())
                    .append(" B-status=").append(camera.pipelineB().status())
                    .append(" B-availability=").append(camera.pipelineB().evidence().availability())
                    .append(" intersection=").append(comparison.intersection().size())
                    .append(" A-only=").append(comparison.aOnly().size())
                    .append(" B-only=").append(comparison.bOnly().size())
                    .append(" both-fail=").append(comparison.bothFail().size())
                    .append(" unknown=").append(comparison.unknown().size());
        }
        return result.toString();
    }

    private String singleSummary(PipelineRun run) {
        StringBuilder result = new StringBuilder("status=").append(run.status())
                .append(" pipeline=").append(run.pipelineId())
                .append(" elapsedMs=").append(run.fastScanMillis() + run.realVerifyMillis())
                .append(" logs=").append(logPath());
        for (PipelineCoverage coverage : run.cameras()) {
            result.append("\ncamera=").append(coverage.cameraId())
                    .append(" availability=").append(coverage.evidence().availability())
                    .append(" status=").append(coverage.status())
                    .append(" fastScanMs=").append(coverage.fastScanMillis())
                    .append(" realVerifyMs=").append(coverage.realVerifyMillis())
                    .append(" pass=").append(coverage.passCount())
                    .append(" fail=").append(coverage.failCount())
                    .append(" unknown=").append(coverage.unknownCount());
        }
        return result.toString();
    }

    private String logPath() {
        File root = context.getExternalFilesDir(null);
        if (root == null) root = context.getFilesDir();
        return new File(new File(root, "Logs"), "logs.txt").getAbsolutePath();
    }

    private static void progress(Consumer<DebugCameraBenchmarkUseCase.Progress> listener,
            String stage, int completed, int total, String detail) {
        listener.accept(new DebugCameraBenchmarkUseCase.Progress(stage,
                Math.max(0, Math.min(completed, total)), total, detail));
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private record FastResults(
            BuildFastCameraCapabilitiesUseCase.Result resultA,
            BuildFastCameraCapabilitiesUseCase.Result resultB,
            boolean cancelled,
            String detail) {
        private BuildFastCameraCapabilitiesUseCase.FastSnapshot source() {
            if (resultA != null && resultA.complete()) {
                return resultA.authoritativeSnapshot().orElseThrow();
            }
            if (resultB != null && resultB.complete()) {
                return resultB.authoritativeSnapshot().orElseThrow();
            }
            throw new IllegalArgumentException("fast snapshot missing");
        }

        private Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> fastA() {
            return frozen(resultA);
        }

        private Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> fastB() {
            return frozen(resultB);
        }

        private boolean completeFor(DebugCameraBenchmarkUseCase.PipelineSelection selection) {
            return switch (selection) {
                case A -> resultA != null && resultA.complete();
                case B -> resultB != null && resultB.complete();
                case BOTH -> resultA != null && resultA.complete()
                        && resultB != null && resultB.complete();
            };
        }

        private static FastResults cancelled(BuildFastCameraCapabilitiesUseCase.Result resultA) {
            return new FastResults(resultA, null, true, "cancelled");
        }
    }
}
