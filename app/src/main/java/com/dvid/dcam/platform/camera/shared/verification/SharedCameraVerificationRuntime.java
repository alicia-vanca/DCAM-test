package com.dvid.dcam.platform.camera.shared.verification;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.domain.camera.CameraFailureClass;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
import com.dvid.dcam.platform.camera.shared.api.SharedCameraPipeline;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;

public final class SharedCameraVerificationRuntime implements CameraRuntimeOperations {
    private final SharedCameraPipeline pipeline;
    private final Logger logger;
    private final CameraVerificationClock clock;
    private final Consumer<CameraOperationContext> bindPreparation;

    public SharedCameraVerificationRuntime(SharedCameraPipeline pipeline, Logger logger,
            CameraVerificationClock clock) {
        this(pipeline, logger, clock, ignored -> {});
    }

    public SharedCameraVerificationRuntime(SharedCameraPipeline pipeline, Logger logger,
            CameraVerificationClock clock,
            Consumer<CameraOperationContext> bindPreparation) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.bindPreparation = Objects.requireNonNull(bindPreparation, "bindPreparation");
    }

    @Override public CameraOperationResult bindSession(CameraOperationContext context) {
        bindPreparation.accept(context);
        return mapResult(context, pipeline.bindSession(pipelineContext(context)));
    }

    @Override public CameraOperationResult bindStandaloneImageSession(
            CameraOperationContext context) {
        bindPreparation.accept(context);
        return mapResult(context,
                pipeline.bindStandaloneImageSession(pipelineContext(context)));
    }

    @Override public CameraOperationResult updateSession(CameraOperationContext context) {
        return mapResult(context, pipeline.updateSession(pipelineContext(context)));
    }

    @Override public CameraOperationResult previewProgress(CameraOperationContext context) {
        return mapResult(context, pipeline.previewProgress(pipelineContext(context)));
    }

    @Override public CameraOperationResult startEncoder(CameraOperationContext context) {
        return mapResult(context, pipeline.startEncoder(pipelineContext(context)));
    }

    @Override public CameraOperationResult stopEncoder(CameraOperationContext context) {
        return mapResult(context, pipeline.stopEncoder(pipelineContext(context)));
    }

    @Override public CameraOperationResult finalizeEncoder(CameraOperationContext context) {
        return mapResult(context, pipeline.finalizeEncoder(pipelineContext(context)));
    }

    @Override public CameraOperationResult captureJpeg(CameraOperationContext context) {
        CameraOperationContext pipelineContext = pipelineContext(context);
        CameraPipelineDiagnostics before = pipeline.diagnostics(pipelineContext);
        if (!before.context().matchesCurrentOperation(pipelineContext)) {
            return captureJpegResult(context,
                    new CameraOperationResult(before.context(),
                            CameraPipelineOperation.CAPTURE_JPEG,
                            CameraOperationOutcome.STALE, 0,
                            "stale_diagnostics_context"),
                    CameraOperationOutcome.STALE, "capture_stale_diagnostics");
        }
        return mapResult(context, pipeline.captureJpeg(pipelineContext));
    }

    @Override public CameraOperationResult release(CameraOperationContext context) {
        return mapResult(context, pipeline.release(pipelineContext(context)));
    }

    @Override public CameraPipelineDiagnostics diagnostics(CameraOperationContext context) {
        CameraPipelineDiagnostics diagnostics = pipeline.diagnostics(pipelineContext(context));
        String cleanupDetail = cleanupArtifacts(diagnostics);
        CameraPipelineDiagnostics mapped = mapDiagnostics(context, diagnostics);
        if (cleanupDetail.isEmpty()) return mapped;
        return new CameraPipelineDiagnostics(mapped.context(), mapped.sessionBound(),
                mapped.previewProgressing(), mapped.encoderActive(), mapped.encoderFinalized(),
                mapped.sourceFrameCount(), mapped.previewFrameCount(),
                mapped.encodedSampleCount(), mapped.cameraOutputCount(),
                mapped.downstreamSurfaceCount(), mapped.encodedVideoResolution(),
                mapped.capturedJpegResolution(), mapped.jpegCapturedWhileEncoderActive(),
                mapped.finalizedVideoArtifact(), mapped.capturedJpegArtifact(),
                mapped.detail() + ",artifactCleanup=" + cleanupDetail);
    }

    private CameraOperationContext pipelineContext(CameraOperationContext context) {
        long now = clock.elapsedRealtimeMillis();
        long remaining = context.deadline().remainingMillis(now);
        CameraOperationDeadline deadline = CameraOperationDeadline.after(
                System.currentTimeMillis(), Math.max(1, remaining));
        return new CameraOperationContext(context.cameraId(),
                context.verificationPipelineId(), context.codec(), context.tuple(),
                context.sessionGeneration(), context.cameraHealthGeneration(), deadline);
    }


    private static CameraOperationResult mapResult(CameraOperationContext context,
            CameraOperationResult result) {
        CameraOperationContext mappedContext = mappedContext(context, result.context());
        return new CameraOperationResult(mappedContext, result.operation(), result.outcome(),
                failureClass(result), result.elapsedMillis(), result.detail());
    }

    private static CameraPipelineDiagnostics mapDiagnostics(CameraOperationContext context,
            CameraPipelineDiagnostics diagnostics) {
        return new CameraPipelineDiagnostics(mappedContext(context, diagnostics.context()),
                diagnostics.sessionBound(), diagnostics.previewProgressing(),
                diagnostics.encoderActive(), diagnostics.encoderFinalized(),
                diagnostics.sourceFrameCount(), diagnostics.previewFrameCount(),
                diagnostics.encodedSampleCount(), diagnostics.cameraOutputCount(),
                diagnostics.downstreamSurfaceCount(), diagnostics.encodedVideoResolution(),
                diagnostics.capturedJpegResolution(), diagnostics.jpegCapturedWhileEncoderActive(),
                diagnostics.finalizedVideoArtifact(), diagnostics.capturedJpegArtifact(),
                diagnostics.detail());
    }

    private static CameraOperationContext mappedContext(CameraOperationContext caller,
            CameraOperationContext result) {
        return new CameraOperationContext(result.cameraId(), result.verificationPipelineId(),
                result.codec(), result.tuple(), result.sessionGeneration(),
                result.cameraHealthGeneration(), caller.deadline());
    }

    private static CameraFailureClass failureClass(CameraOperationResult result) {
        if (!result.outcome().isCandidateFailure()) return CameraFailureClass.NONE;
        return classifyFailure(result.operation(), result.detail());
    }

    private static CameraFailureClass classifyFailure(CameraPipelineOperation operation,
            String detail) {
        String value = detail == null ? "" : detail.toLowerCase(java.util.Locale.ROOT).trim();
        for (String wrapper : new String[] {
                "capture_start=", "capture=", "capture_cleanup=" }) {
            if (value.startsWith(wrapper)) {
                value = value.substring(wrapper.length());
                break;
            }
        }
        int end = value.length();
        for (char separator : new char[] { ':', '=', ',', ';', ' ' }) {
            int index = value.indexOf(separator);
            if (index >= 0) end = Math.min(end, index);
        }
        String token = value.substring(0, end);
        if (token.equals("invalid_shared_private_jpeg_topology")) {
            return CameraFailureClass.TOPOLOGY;
        }
        if (token.equals("invalid_video") || token.equals("output_validation_failed")) {
            return operation == CameraPipelineOperation.CAPTURE_JPEG
                    ? CameraFailureClass.JPEG_OUTPUT : CameraFailureClass.VIDEO_OUTPUT;
        }
        if (token.equals("invalid_jpeg")) return CameraFailureClass.JPEG_OUTPUT;
        if (token.equals("jpeg_callback_missing") || token.equals("jpeg_requires_active_encoder")
                || token.equals("capture_failed")) return CameraFailureClass.JPEG_CAPTURE;
        if (token.startsWith("session_config") || token.equals("on_configure_failed")) {
            return CameraFailureClass.SESSION_CONFIGURATION;
        }
        if (token.startsWith("encoder") || token.startsWith("first_encoded_sample")
                || token.startsWith("stop_encoder")) return CameraFailureClass.VIDEO_ENCODER;
        return switch (operation) {
            case BIND_SESSION, UPDATE_SESSION -> CameraFailureClass.SESSION_CONFIGURATION;
            case START_ENCODER, STOP_ENCODER -> CameraFailureClass.VIDEO_ENCODER;
            case FINALIZE_ENCODER -> CameraFailureClass.VIDEO_OUTPUT;
            case CAPTURE_JPEG -> CameraFailureClass.JPEG_CAPTURE;
            default -> CameraFailureClass.UNKNOWN;
        };
    }

    private String cleanupArtifacts(CameraPipelineDiagnostics diagnostics) {
        if (diagnostics.encoderActive()) return "";
        boolean attempted = diagnostics.finalizedVideoArtifact().isPresent()
                || diagnostics.capturedJpegArtifact().isPresent();
        if (!attempted) return "";
        boolean complete = delete(diagnostics.finalizedVideoArtifact().orElse(null))
                & delete(diagnostics.capturedJpegArtifact().orElse(null));
        return complete ? "complete" : "incomplete";
    }

    private boolean delete(String artifact) {
        if (artifact == null) return true;
        try {
            Path path = Paths.get(artifact);
            if (!path.isAbsolute()) {
                throw new IOException("verification artifact path is not absolute: " + artifact);
            }
            Files.deleteIfExists(path);
            return true;
        } catch (IOException | RuntimeException error) {
            logger.warn("camera_verification artifact_cleanup_failed path=" + artifact, error);
            return false;
        }
    }

    private static CameraOperationResult captureJpegResult(
            CameraOperationContext context, CameraOperationResult source,
            CameraOperationOutcome outcome, String detail) {
        return new CameraOperationResult(mappedContext(context, source.context()),
                CameraPipelineOperation.CAPTURE_JPEG, outcome,
                outcome.isCandidateFailure()
                        ? classifyFailure(source.operation(), source.detail())
                        : CameraFailureClass.NONE,
                source.elapsedMillis(), detail);
    }
}