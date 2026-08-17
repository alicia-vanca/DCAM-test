package com.dvid.dcam.platform.camera.shared.benchmark;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Range;
import android.util.Size;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase;
import com.dvid.dcam.feature.device.application.usecase.VerifyCameraSelectionUseCase;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.CameraFastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.FastSnapshot;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.CameraScope;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.FrozenEnvironment;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.platform.camera.shared.egl.EglFanOutPipelineFactory;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativeSurfaceSharingPipelineFactory;
import com.dvid.dcam.platform.camera.shared.verification.SharedCameraVerificationRuntime;
import com.dvid.dcam.platform.device.capability.catalog.AndroidCameraCatalogSource;
import com.dvid.dcam.platform.device.capability.fast.AndroidFastCameraCapabilityProbe;
import com.dvid.dcam.platform.device.capability.probe.egl.EglFanOutFastProbe;
import com.dvid.dcam.platform.device.capability.probe.nativesharing.NativeSurfaceSharingFastProbe;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CameraPipelineBenchmarkDeviceTest {
    @Test public void exportsSameTupleAbReportForBothPipelines() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantPermissions(context);
        NoOpLogger logger = new NoOpLogger();
        NativeSurfaceSharingFastProbe probeA = new NativeSurfaceSharingFastProbe(context, logger);
        EglFanOutFastProbe probeB = new EglFanOutFastProbe(context, logger);
        BuildFastCameraCapabilitiesUseCase.Result discoveryA =
                new BuildFastCameraCapabilitiesUseCase(
                        new AndroidCameraCatalogSource(context, logger), logger)
                        .execute(new AndroidFastCameraCapabilityProbe(probeA, logger));
        BuildFastCameraCapabilitiesUseCase.Result discoveryB =
                new BuildFastCameraCapabilitiesUseCase(
                        new AndroidCameraCatalogSource(context, logger), logger)
                        .execute(new AndroidFastCameraCapabilityProbe(probeB, logger));
        assertTrue(discoveryA.detail(), discoveryA.complete());
        assertTrue(discoveryB.detail(), discoveryB.complete());
        Selection selection = selectCommon(
                discoveryA.authoritativeSnapshot().orElseThrow(),
                discoveryB.authoritativeSnapshot().orElseThrow());
        Assume.assumeTrue("No common full-fast tuple available", selection != null);
        CameraId cameraId = selection.cameraId();
        CaptureModeTuple tuple = selection.tuple();

        File root = new File(context.getCacheDir(), "camera-ab-benchmark");
        String hardware = "device:" + Build.FINGERPRINT;
        String cameraHardware = "camera:" + selection.cameraId;
        String h264Configuration = "device-default-avc-profile-level-bitrate";
        String cropRotationPolicy = "sensor-native-video-jpeg-preview-display-only";
        String thermalGate = "thermal-status<=moderate";
        CameraPipelineBenchmarkPlan plan = new CameraPipelineBenchmarkPlan(
                new FrozenEnvironment(hardware, Map.of(cameraId, cameraHardware),
                        VideoCodec.H264, h264Configuration, cropRotationPolicy, 5_000,
                        root.getAbsolutePath(), thermalGate),
                List.of(new CameraScope(cameraId, cameraHardware, Set.of(tuple))),
                CameraPipelineBenchmarkPlan.BenchmarkProtocol.defaults());
        Snapshot baseline = baseline(hardware, cameraHardware, cameraId, tuple);
        android.os.PowerManager power = context.getSystemService(android.os.PowerManager.class);
        SharedCameraPipelineBenchmarkRunner.EnvironmentValidator environmentValidator =
                requested -> {
                    long thermalStatus = power == null || Build.VERSION.SDK_INT < 29
                            ? Long.MAX_VALUE : power.getCurrentThermalStatus();
                    boolean ready = requested.hardwareSignature().equals(hardware)
                            && requested.cameraHardwareSignatures().equals(
                                    Map.of(cameraId, cameraHardware))
                            && requested.h264Configuration().equals(h264Configuration)
                            && requested.cropRotationPolicy().equals(cropRotationPolicy)
                            && requested.storagePath().equals(root.getAbsolutePath())
                            && requested.thermalGate().equals(thermalGate)
                            && thermalStatus <= android.os.PowerManager.THERMAL_STATUS_MODERATE;
                    return new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                            ready, "thermalStatus=" + thermalStatus + ",ready=" + ready);
                };
        File report = new File(root, "camera-pipeline-ab-report.json");
        RecordingStore store = new RecordingStore(report);
        CameraVerificationClock clock = CameraVerificationClock.system();
        com.dvid.dcam.feature.device.application.port.CameraHealthGenerationProvider health =
                ignored -> 0;
        SharedCameraPipelineBenchmarkRunner.CandidateVerifier candidateVerifier =
                (runtime, stagingStore, request) -> new VerifyCameraSelectionUseCase(
                        runtime, stagingStore, logger, clock, health).execute(request);
        SharedCameraPipelineBenchmarkRunner.StandaloneImageVerifier imageVerifier =
                (runtime, request) -> new com.dvid.dcam.feature.device.application.usecase.VerifyStandaloneImageUseCase(
                        runtime, logger, clock, health).execute(request);
        var pipelineA = new NativeSurfaceSharingPipelineFactory(
                context, logger, root, () -> null).createHeadless(0);
        var pipelineB = new EglFanOutPipelineFactory(
                context, logger, root, () -> null).createHeadless(0);
        SharedCameraPipelineBenchmarkRunner runnerA = new SharedCameraPipelineBenchmarkRunner(
                new BuildFastCameraCapabilitiesUseCase(
                        new AndroidCameraCatalogSource(context, logger), logger),
                new AndroidFastCameraCapabilityProbe(probeA, logger),
                new SharedCameraVerificationRuntime(pipelineA, logger, clock), candidateVerifier,
                imageVerifier,
                logger, health, plan.environment(), environmentValidator,
                new AndroidCameraBenchmarkTelemetry(context));
        SharedCameraPipelineBenchmarkRunner runnerB = new SharedCameraPipelineBenchmarkRunner(
                new BuildFastCameraCapabilitiesUseCase(
                        new AndroidCameraCatalogSource(context, logger), logger),
                new AndroidFastCameraCapabilityProbe(probeB, logger),
                new SharedCameraVerificationRuntime(pipelineB, logger, clock), candidateVerifier,
                imageVerifier,
                logger, health, plan.environment(), environmentValidator,
                new AndroidCameraBenchmarkTelemetry(context));

        CompareCameraPipelinesUseCase.Result result = new CompareCameraPipelinesUseCase(
                store, runnerA, runnerB, logger).execute(
                new CompareCameraPipelinesUseCase.Request(baseline, plan));

        assertTrue(report.isFile());
        assertTrue(report.length() > 0);
        var camera = result.report().cameras().get(0);
        assertEquals(Set.of(tuple), camera.candidateUniverse());
        assertEquals(NativeSurfaceSharingPipelineFactory.PIPELINE_ID,
                camera.pipelineA().pipelineId());
        assertEquals(EglFanOutPipelineFactory.PIPELINE_ID, camera.pipelineB().pipelineId());
        assertEquals(tuple, camera.pipelineA().outcomes().get(0).tuple());
        assertEquals(tuple, camera.pipelineB().outcomes().get(0).tuple());
        assertEquals(CameraPipelineBenchmarkReport.Status.COMPLETE, result.report().status());
        assertTrue(result.report().publishedDurably());
        assertEquals(1, store.writeCount.get());
        assertTrue(camera.comparison().intersection().contains(tuple));
        assertEquals(com.dvid.dcam.feature.device.domain.camera.VerificationOutcome.VERIFIED_PASS,
                camera.pipelineA().outcomes().get(0).outcome());
        assertEquals(com.dvid.dcam.feature.device.domain.camera.VerificationOutcome.VERIFIED_PASS,
                camera.pipelineB().outcomes().get(0).outcome());
        assertTrue(!camera.performance().isEmpty());
        assertTrue(!camera.performance().get(0).samplesA().isEmpty());
        assertTrue(!camera.performance().get(0).samplesB().isEmpty());
        String json = new String(java.nio.file.Files.readAllBytes(report.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        org.json.JSONObject parsed = new org.json.JSONObject(json);
        assertTrue(parsed.getBoolean("publishedDurably"));
        assertTrue(json.contains(NativeSurfaceSharingPipelineFactory.PIPELINE_ID.value()));
        assertTrue(json.contains(EglFanOutPipelineFactory.PIPELINE_ID.value()));
        assertTrue(json.contains("\"fastScanA\""));
        assertTrue(json.contains("\"fastScanB\""));
    }

    private static Selection selectCommon(FastSnapshot pipelineA, FastSnapshot pipelineB) {
        for (CameraFastSnapshot cameraA : pipelineA.cameras()) {
            CameraFastSnapshot cameraB = null;
            for (CameraFastSnapshot candidate : pipelineB.cameras()) {
                if (candidate.cameraFacts().cameraId().equals(cameraA.cameraFacts().cameraId())) {
                    cameraB = candidate;
                    break;
                }
            }
            if (cameraB == null) continue;
            for (CandidateKey keyA : cameraA.evidence().rawFastCandidates()) {
                Optional<CaptureModeTuple> tuple = keyA.tuple();
                if (tuple.isEmpty()) continue;
                CandidateKey keyB = CandidateKey.forTuple(cameraA.cameraFacts().cameraId(),
                        VideoCodec.H264, pipelineB.pipelineId(), tuple.orElseThrow());
                if (cameraB.evidence().rawFastCandidates().contains(keyB)) {
                    return new Selection(cameraA.cameraFacts().cameraId(), tuple.orElseThrow());
                }
            }
        }
        return null;
    }

    private static Snapshot baseline(String hardware, String cameraHardware,
            CameraId cameraId, CaptureModeTuple tuple) {
        CandidateKey keyA = CandidateKey.forTuple(cameraId, VideoCodec.H264,
                NativeSurfaceSharingPipelineFactory.PIPELINE_ID, tuple);
        CandidateKey keyB = CandidateKey.forTuple(cameraId, VideoCodec.H264,
                EglFanOutPipelineFactory.PIPELINE_ID, tuple);
        PipelineEvidence a = new PipelineEvidence(cameraId, VideoCodec.H264,
                NativeSurfaceSharingPipelineFactory.PIPELINE_ID,
                com.dvid.dcam.feature.device.domain.camera.PipelineAvailability.AVAILABLE,
                List.of(keyA), List.of());
        PipelineEvidence b = new PipelineEvidence(cameraId, VideoCodec.H264,
                EglFanOutPipelineFactory.PIPELINE_ID,
                com.dvid.dcam.feature.device.domain.camera.PipelineAvailability.AVAILABLE,
                List.of(keyB), List.of());
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264,
                CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                List.of(a, b), Optional.empty());
        return Snapshot.current(hardware, List.of(), List.of(new CameraSnapshot(
                cameraId, cameraHardware, List.of(codec), Optional.empty(), Optional.empty())));
    }

    private static void grantPermissions(Context context) {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
        automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.RECORD_AUDIO);
    }

    private record Selection(CameraId cameraId, CaptureModeTuple tuple) {}

    private static final class RecordingStore
            extends CompareCameraPipelinesUseCase.DurableComparisonPublisher {
        private final AtomicInteger writeCount = new AtomicInteger();
        private final File reportFile;

        private RecordingStore(File reportFile) {
            this.reportFile = reportFile;
        }

        @Override public boolean publishDurably(Snapshot snapshot,
                CameraPipelineBenchmarkReport report) {
            try {
                CameraPipelineBenchmarkReportJson.writeAtomic(reportFile, report);
                writeCount.incrementAndGet();
                return true;
            } catch (java.io.IOException error) {
                throw new IllegalStateException("report export failed", error);
            }
        }
    }

    private static final class NoOpLogger implements Logger {
        private static void print(String message) {
            System.out.println("CAM_IMP_12 " + message);
        }

        @Override public void debug(String message) { print(message); }
        @Override public void info(String message) { print(message); }
        @Override public void info(String message, Throwable error) {
            print(message + " error=" + error);
        }
        @Override public void warn(String message, Throwable error) {
            print(message + " error=" + error);
        }
        @Override public void error(String message, Throwable error) {
            print(message + " error=" + error);
        }
    }
}
