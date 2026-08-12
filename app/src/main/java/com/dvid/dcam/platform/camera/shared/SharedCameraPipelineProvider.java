package com.dvid.dcam.platform.camera.shared;

import android.content.Context;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.platform.camera.shared.egl.EglFanOutPipelineFactory;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativeSurfaceSharingPipelineFactory;
import com.dvid.dcam.platform.camera.shared.runtime.CameraRuntimeSelection;
import java.io.File;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public interface SharedCameraPipelineProvider {
    SharedCameraCapturePipeline create(
            CameraRuntimeSelection selection,
            SharedCameraPreviewOutput previewSurface);

    void refreshRotation(
            CameraRuntimeSelection selection,
            SharedCameraPreviewOutput previewSurface,
            SharedCameraCapturePipeline pipeline);

    SharedCameraCapturePipeline createVerification(CameraRuntimeSelection selection);
}

final class DefaultSharedCameraPipelineProvider implements SharedCameraPipelineProvider {
    private final Logger logger;
    private final ToIntFunction<String> cameraOrientationDegrees;
    private final IntSupplier displayRotationDegrees;
    private final Predicate<String> frontFacing;
    private final long preRecordGopDurationMillis;
    private final NativeSurfaceSharingPipelineFactory nativeFactory;
    private final EglFanOutPipelineFactory eglFactory;

    public DefaultSharedCameraPipelineProvider(
            Context context, Logger logger, File diagnosticOutputDirectory,
            long preRecordGopDurationMillis,
            ToIntFunction<String> cameraOrientationDegrees,
            IntSupplier displayRotationDegrees,
            Predicate<String> frontFacing) {
        Context applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        Context runtimeContext = applicationContext == null ? context : applicationContext;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.cameraOrientationDegrees = Objects.requireNonNull(
                cameraOrientationDegrees, "cameraOrientationDegrees");
        this.displayRotationDegrees = Objects.requireNonNull(
                displayRotationDegrees, "displayRotationDegrees");
        this.frontFacing = Objects.requireNonNull(frontFacing, "frontFacing");
        this.preRecordGopDurationMillis = preRecordGopDurationMillis;
        Objects.requireNonNull(diagnosticOutputDirectory, "diagnosticOutputDirectory");
        nativeFactory = new NativeSurfaceSharingPipelineFactory(
                runtimeContext, logger, diagnosticOutputDirectory);
        eglFactory = new EglFanOutPipelineFactory(
                runtimeContext, logger, diagnosticOutputDirectory);
    }

    @Override public SharedCameraCapturePipeline create(
            CameraRuntimeSelection selection,
            SharedCameraPreviewOutput previewSurface) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(previewSurface, "previewSurface");
        String cameraId = selection.cameraId().value();
        Orientation orientation = displayOrientation(cameraId);
        previewSurface.resize(selection.tuple().videoMode().resolution().actual());
        previewSurface.setDisplayResolution(
                selection.tuple().videoMode().resolution().actual());
        applyPreviewOrientation(previewSurface, orientation);
        if (CameraPipelineIds.NATIVE_SURFACE_SHARING.equals(
                selection.verificationPipelineId())) {
            return nativeFactory.create(previewSurface.surface(), previewSurface,
                    orientation.outputRotationDegrees(), preRecordGopDurationMillis);
        }
        if (CameraPipelineIds.EGL_FAN_OUT.equals(
                selection.verificationPipelineId())) {
            return eglFactory.create(previewSurface.surface(), previewSurface,
                    orientation.outputRotationDegrees(), preRecordGopDurationMillis);
        }
        throw new IllegalArgumentException(
                "Unsupported shared camera pipeline " + selection.verificationPipelineId());
    }

    @Override public void refreshRotation(
            CameraRuntimeSelection selection,
            SharedCameraPreviewOutput previewSurface,
            SharedCameraCapturePipeline pipeline) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(previewSurface, "previewSurface");
        Objects.requireNonNull(pipeline, "pipeline");
        String cameraId = selection.cameraId().value();
        Orientation orientation = displayOrientation(cameraId);
        int previousOutputDegrees = pipeline.outputRotationDegrees();
        pipeline.setRotation(orientation.outputRotationDegrees());
        applyPreviewOrientation(previewSurface, orientation);
        if (previousOutputDegrees != orientation.outputRotationDegrees()) {
            logger.info("shared_camera_preview stage=orientation outcome=applied"
                    + " cameraId=" + cameraId
                    + " sensorDegrees=" + orientation.sensorOrientationDegrees()
                    + " displayDegrees=" + orientation.displayRotationDegrees()
                    + " previousOutputDegrees=" + previousOutputDegrees
                    + " outputDegrees=" + orientation.outputRotationDegrees()
                    + " previewMirrorCompensation="
                            + orientation.previewMirrorCompensation());
        }
    }

    private Orientation displayOrientation(String cameraId) {
        int sensor = sensorOrientationDegrees(cameraId);
        int display = CameraOrientation.normalize(displayRotationDegrees.getAsInt());
        boolean front = frontFacing(cameraId);
        int output = CameraOrientation.relativeRotation(sensor, display, front);
        // ponytail: BodyCamera front streams are validated mirrored; use a camera capability if another platform differs.
        boolean previewMirrorCompensation = front;
        return new Orientation(sensor, display, output, previewMirrorCompensation);
    }

    private void applyPreviewOrientation(
            SharedCameraPreviewOutput previewSurface, Orientation orientation) {
        previewSurface.setSensorOrientation(orientation.sensorOrientationDegrees());
        previewSurface.setDisplayRotation(orientation.displayRotationDegrees());
        previewSurface.setMirrored(orientation.previewMirrorCompensation());
        previewSurface.setRotation(orientation.outputRotationDegrees());
    }

    private int sensorOrientationDegrees(String cameraId) {
        return CameraOrientation.normalize(cameraOrientationDegrees.applyAsInt(cameraId));
    }

    private boolean frontFacing(String cameraId) {
        return frontFacing.test(cameraId);
    }

    @Override public SharedCameraCapturePipeline createVerification(
            CameraRuntimeSelection selection) {
        Objects.requireNonNull(selection, "selection");
        if (CameraPipelineIds.NATIVE_SURFACE_SHARING.equals(
                selection.verificationPipelineId())) {
            return nativeFactory.createHeadless(
                    sensorOrientationDegrees(selection.cameraId().value()));
        }
        if (CameraPipelineIds.EGL_FAN_OUT.equals(
                selection.verificationPipelineId())) {
            return eglFactory.createHeadless(
                    sensorOrientationDegrees(selection.cameraId().value()));
        }
        throw new IllegalArgumentException(
                "Unsupported shared camera pipeline " + selection.verificationPipelineId());
    }

    private record Orientation(
            int sensorOrientationDegrees,
            int displayRotationDegrees,
            int outputRotationDegrees,
            boolean previewMirrorCompensation) {}
}
