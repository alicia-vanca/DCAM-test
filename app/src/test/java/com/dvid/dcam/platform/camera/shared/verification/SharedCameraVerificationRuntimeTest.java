package com.dvid.dcam.platform.camera.shared.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.domain.camera.CameraFailureClass;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.platform.camera.shared.api.SharedCameraPipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SharedCameraVerificationRuntimeTest {
    @Test void adapterDelegatesComboOperationsAndDiagnostics() {
        RecordingPipeline pipeline = new RecordingPipeline();
        SharedCameraVerificationRuntime adapter =
                new SharedCameraVerificationRuntime(pipeline, new NoOpLogger(), clock());
        CameraOperationContext context = context();

        adapter.bindSession(context);
        adapter.previewProgress(context);
        adapter.startEncoder(context);
        adapter.captureJpeg(context);
        adapter.stopEncoder(context);
        adapter.finalizeEncoder(context);
        adapter.release(context);
        adapter.diagnostics(context);

        assertEquals(List.of(CameraPipelineOperation.BIND_SESSION,
                CameraPipelineOperation.PREVIEW_PROGRESS,
                CameraPipelineOperation.START_ENCODER,
                CameraPipelineOperation.DIAGNOSTICS,
                CameraPipelineOperation.CAPTURE_JPEG,
                CameraPipelineOperation.STOP_ENCODER,
                CameraPipelineOperation.FINALIZE_ENCODER,
                CameraPipelineOperation.RELEASE,
                CameraPipelineOperation.DIAGNOSTICS), pipeline.calls);
    }

    @Test void imageSoloCaptureDoesNotStartEncoder() {
        RecordingPipeline pipeline = new RecordingPipeline();
        SharedCameraVerificationRuntime adapter =
                new SharedCameraVerificationRuntime(pipeline, new NoOpLogger(), clock());
        CameraOperationContext context = context();

        adapter.bindSession(context);
        CameraOperationResult result = adapter.captureJpeg(context);

        assertEquals(CameraOperationOutcome.PASS, result.outcome());
        assertEquals(List.of(CameraPipelineOperation.BIND_SESSION,
                CameraPipelineOperation.DIAGNOSTICS,
                CameraPipelineOperation.CAPTURE_JPEG), pipeline.calls);
    }

    @Test void imageSoloCandidateFailureRemainsCandidateSuspectForConfirmation() {
        RecordingPipeline pipeline = new RecordingPipeline();
        pipeline.captureOutcome = CameraOperationOutcome.CANDIDATE_SUSPECT;
        SharedCameraVerificationRuntime adapter =
                new SharedCameraVerificationRuntime(pipeline, new NoOpLogger(), clock());
        CameraOperationContext context = context();

        adapter.bindSession(context);
        CameraOperationResult result = adapter.captureJpeg(context);

        assertEquals(CameraOperationOutcome.CANDIDATE_SUSPECT, result.outcome());
        assertEquals(CameraFailureClass.JPEG_CAPTURE, result.failureClass());
        assertEquals(CameraPipelineOperation.CAPTURE_JPEG, result.operation());
    }

    @Test void bindPreparationRunsForComboAndStandaloneSessions() {
        RecordingPipeline pipeline = new RecordingPipeline();
        List<CameraId> prepared = new ArrayList<>();
        SharedCameraVerificationRuntime adapter = new SharedCameraVerificationRuntime(
                pipeline, new NoOpLogger(), clock(),
                context -> prepared.add(context.cameraId()));
        CameraOperationContext context = context();

        adapter.bindSession(context);
        adapter.bindStandaloneImageSession(context);

        assertEquals(List.of(context.cameraId(), context.cameraId()), prepared);
    }

    @Test void adapterPreservesPipelineGenerationForStaleDetection() {
        RecordingPipeline pipeline = new RecordingPipeline();
        pipeline.generationOffset = 1;
        SharedCameraVerificationRuntime adapter =
                new SharedCameraVerificationRuntime(pipeline, new NoOpLogger(), clock());
        CameraOperationContext context = context();

        CameraOperationResult result = adapter.bindSession(context);
        CameraPipelineDiagnostics diagnostics = adapter.diagnostics(context);

        assertEquals(2L, result.context().sessionGeneration());
        assertEquals(2L, diagnostics.context().sessionGeneration());
    }
    private static CameraVerificationClock clock() {
        return () -> 100L;
    }

    private static CameraOperationContext context() {
        VideoMode video = new VideoMode(new StandardResolution(
                StandardResolutionLabel.FHD, new CameraResolution(1920, 1080)), 30);
        ImageMode image = new ImageMode(new StandardResolution(
                StandardResolutionLabel.HD, new CameraResolution(1280, 720)));
        return new CameraOperationContext(new CameraId("0"),
                new VerificationPipelineId("pipeline-a"), VideoCodec.H264,
                new CaptureModeTuple(video, image), 1, 0,
                CameraOperationDeadline.after(100, 5000));
    }

    private static final class RecordingPipeline implements SharedCameraPipeline {
        private final List<CameraPipelineOperation> calls = new ArrayList<>();
        private boolean encoderActive;
        private long generationOffset;
        private CameraOperationOutcome captureOutcome = CameraOperationOutcome.PASS;

        @Override public VerificationPipelineId pipelineId() {
            return new VerificationPipelineId("pipeline-a");
        }
        @Override public CameraOperationResult bindSession(CameraOperationContext context) {
            return result(context, CameraPipelineOperation.BIND_SESSION);
        }
        @Override public CameraOperationResult previewProgress(CameraOperationContext context) {
            return result(context, CameraPipelineOperation.PREVIEW_PROGRESS);
        }
        @Override public CameraOperationResult startEncoder(CameraOperationContext context) {
            encoderActive = true;
            return result(context, CameraPipelineOperation.START_ENCODER);
        }
        @Override public CameraOperationResult stopEncoder(CameraOperationContext context) {
            encoderActive = false;
            return result(context, CameraPipelineOperation.STOP_ENCODER);
        }
        @Override public CameraOperationResult finalizeEncoder(CameraOperationContext context) {
            return result(context, CameraPipelineOperation.FINALIZE_ENCODER);
        }
        @Override public CameraOperationResult captureJpeg(CameraOperationContext context) {
            calls.add(CameraPipelineOperation.CAPTURE_JPEG);
            return new CameraOperationResult(context, CameraPipelineOperation.CAPTURE_JPEG,
                    captureOutcome, 0, "capture");
        }
        @Override public CameraOperationResult release(CameraOperationContext context) {
            return result(context, CameraPipelineOperation.RELEASE);
        }
        @Override public CameraPipelineDiagnostics diagnostics(CameraOperationContext context) {
            calls.add(CameraPipelineOperation.DIAGNOSTICS);
            return new CameraPipelineDiagnostics(returnedContext(context), true, true,
                    encoderActive, false, 1, 1, 0, 2, 2,
                    Optional.empty(), Optional.empty(), false,
                    Optional.empty(), Optional.empty(), "diagnostics");
        }

        private CameraOperationResult result(CameraOperationContext context,
                CameraPipelineOperation operation) {
            calls.add(operation);
            return new CameraOperationResult(returnedContext(context), operation,
                    CameraOperationOutcome.PASS, 0, "pass");
        }

        private CameraOperationContext returnedContext(CameraOperationContext context) {
            return new CameraOperationContext(context.cameraId(),
                    context.verificationPipelineId(), context.codec(), context.tuple(),
                    context.sessionGeneration() + generationOffset,
                    context.cameraHealthGeneration(), context.deadline());
        }
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }
}
