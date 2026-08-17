package com.dvid.dcam.platform.camera.shared.outputsharing;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.view.Surface;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.platform.camera.shared.AbstractSharedCameraPipeline;
import com.dvid.dcam.platform.camera.shared.CameraPipelineIds;
import com.dvid.dcam.platform.camera.shared.SharedCameraPipelineSupport;
import java.io.File;
import java.util.function.Supplier;

public final class NativeSurfaceSharingPipeline extends AbstractSharedCameraPipeline {
    private final NativePreviewFrameSignal externalPreviewFrameSignal;
    private ImageReader previewReader;
    private Surface previewSurface;
    private OutputConfiguration sharedOutput;
    private boolean previewSuppressedForEncoder;

    NativeSurfaceSharingPipeline(Context context, Logger logger,
            File outputDirectory, Surface externalPreviewSurface,
            NativePreviewFrameSignal externalPreviewFrameSignal, boolean recycleEncoder,
            int rotationDegrees, long preRecordGopDurationMillis,
            Supplier<GpsCoordinate> captureLocation) {
        super(context, logger, outputDirectory, externalPreviewSurface, recycleEncoder,
                rotationDegrees, preRecordGopDurationMillis, captureLocation,
                CameraPipelineIds.NATIVE_SURFACE_SHARING,
                "dcam-native-camera", "dcam-native-avc", "sharedPrivateSurfaces");
        this.externalPreviewFrameSignal = externalPreviewFrameSignal;
        if (externalPreviewSurface != null && externalPreviewFrameSignal == null) {
            throw new IllegalArgumentException(
                    "external preview requires a frame-progress signal");
        }
    }

    int cameraOpenCount() { return cameraOpenCountValue(); }

    int sessionCreateCount() { return sessionCreateCountValue(); }

    @Override protected void prepareTopology(CameraOperationContext value,
            boolean standaloneImage, int width, int height, int framesPerSecond)
            throws PipelineFailure {
        if (externalPreviewSurface() == null) {
            previewReader = ImageReader.newInstance(width, height, ImageFormat.PRIVATE, 4);
            CameraOperationContext boundContext = value;
            previewReader.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    if (image == null || !isCurrent(boundContext)) return;
                    signalPreviewFrame();
                } catch (RuntimeException error) {
                    recordAsyncFailure(CameraOperationOutcome.GLOBAL_FAILURE,
                            "preview_reader:" + error.getClass().getSimpleName(), error);
                }
            }, cameraHandler());
            previewSurface = previewReader.getSurface();
        } else {
            previewSurface = externalPreviewSurface();
            CameraOperationContext boundContext = value;
            externalPreviewFrameSignal.start(cameraHandler(), () -> {
                if (!isCurrent(boundContext)) return;
                signalPreviewFrame();
            });
        }
        if (!standaloneImage) openEncoder(width, height, framesPerSecond);
    }

    @Override protected void finishTopologyPreparation(
            CameraOperationContext value, boolean standaloneImage) throws PipelineFailure {
        sharedOutput = new OutputConfiguration(previewSurface);
        if (!standaloneImage) {
            sharedOutput.enableSurfaceSharing();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && sharedOutput.getMaxSharedSurfaceCount() < 2) {
                throw topologyFailure(CameraOperationOutcome.BLOCKED_EXTERNAL,
                        "pipeline_unavailable:maxSharedSurfaceCount="
                                + sharedOutput.getMaxSharedSurfaceCount());
            }
            sharedOutput.addSurface(encoder().inputSurface());
        }
        configureFps(value);
    }

    @Override protected OutputConfiguration cameraOutputConfiguration() {
        return sharedOutput;
    }

    @Override protected int topologyDownstreamSurfaceCount() {
        return sharedOutput.getSurfaces().size();
    }

    @Override protected int repeatingTemplate(boolean includeEncoder) {
        return includeEncoder ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW;
    }

    @Override protected void addRepeatingTargets(
            CaptureRequest.Builder builder, boolean includeEncoder) {
        NativeFrameTargetPolicy.Decision targets =
                NativeFrameTargetPolicy.repeating(includeEncoder);
        previewSuppressedForEncoder = targets.previewSuppressed();
        if (previewSuppressedForEncoder) {
            long dropped = incrementPreviewDropCount();
            log(activeContext(), "preview_backpressure", "drop",
                    "external_preview_suppressed_for_encoder:droppedFrames=" + dropped);
        }
        if (targets.includePreview()) builder.addTarget(previewSurface);
        if (targets.includeEncoder()) builder.addTarget(encoder().inputSurface());
    }

    @Override protected void addJpegTargets(
            CaptureRequest.Builder builder, boolean encoderWasActive) {
        NativeFrameTargetPolicy.Decision targets =
                NativeFrameTargetPolicy.jpeg(externalPreviewSurface() != null, encoderWasActive);
        if (targets.includePreview()) builder.addTarget(previewSurface);
        if (targets.includeEncoder()) builder.addTarget(encoder().inputSurface());
    }

    @Override protected String updateTopologySession(
            CameraOperationContext value, long started) throws CameraAccessException {
        try {
            cameraSession().updateOutputConfiguration(sharedOutput);
            return "update_output_configuration_pass";
        } catch (IllegalArgumentException | IllegalStateException error) {
            String detail = "retained_encoder_surface_gate:"
                    + error.getClass().getSimpleName();
            logger().warn(prefix(value, "session_update")
                    + " outcome=fallback_gate elapsedMs="
                    + SharedCameraPipelineSupport.elapsed(started), error);
            return detail;
        }
    }

    @Override protected void startTopologyRecording() throws CameraAccessException {
        startRepeating(true);
    }


    @Override protected String encoderStartDetail() {
        return "encoder_started:cameraOpenCount=" + cameraOpenCountValue()
                + ",sessionCreateCount=" + sessionCreateCountValue();
    }

    @Override protected void onCaptureCompleted(TotalCaptureResult result) {
        Long timestamp = result.get(TotalCaptureResult.SENSOR_TIMESTAMP);
        if (timestamp == null) signalSourceFrame();
        else signalSourceFrame(timestamp);
    }

    @Override protected boolean hasPreviewEvidence() {
        return externalPreviewSurface() == null || externalPreviewFrameSignal != null;
    }

    @Override protected String previewProgressDetail() {
        return ",previewEvidence=" + hasPreviewEvidence();
    }

    @Override protected String operationContextDetail(CameraOperationContext value) {
        var video = value.tuple().videoMode();
        var photo = value.tuple().imageMode();
        return " video=" + video.resolution().label() + ":" + video.resolution().actual()
                + " fps=" + video.framesPerSecond()
                + " photo=" + photo.resolution().label() + ":"
                        + photo.resolution().actual();
    }

    @Override protected String topologyDiagnostics() {
        return ",previewEvidence=" + hasPreviewEvidence()
                + ",previewSuppressedForEncoder=" + previewSuppressedForEncoder;
    }

    @Override protected boolean releaseTopology(long deadlineMillis) {
        boolean released = true;
        boolean previewSignalStopped = true;
        if (externalPreviewFrameSignal != null) {
            try { externalPreviewFrameSignal.stop(); }
            catch (RuntimeException error) {
                previewSignalStopped = false;
                released = false;
            }
        }
        if (previewReader != null) {
            try { previewReader.close(); previewReader = null; }
            catch (RuntimeException error) { released = false; }
        }
        if (previewReader == null && previewSignalStopped) previewSurface = null;
        if (cameraSessionReleased()) sharedOutput = null;
        return released;
    }

    @Override protected void resetTopologyEvidence() {
        previewSuppressedForEncoder = false;
    }
}