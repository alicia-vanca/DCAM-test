package com.dvid.dcam.platform.camera.shared;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.platform.camera.shared.egl.EglFanOutPipelineFactory;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativeSurfaceSharingPipelineFactory;
import com.dvid.dcam.platform.camera.shared.runtime.CameraRuntimeSelection;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public interface SharedCameraPipelineProvider {
    SharedCameraCapturePipeline create(
            CameraRuntimeSelection selection,
            SharedCameraPreviewOutput previewSurface);

    void refreshRotation(
            CameraRuntimeSelection selection,
            SharedCameraPreviewOutput previewSurface,
            SharedCameraCapturePipeline pipeline);

    long recordingBitrateBitsPerSecond(CameraRuntimeSelection selection);

    default void startMediaOrientationTracking() {}

    default void stopMediaOrientationTracking() {}

    default int mediaRotationDegrees(
            CameraRuntimeSelection selection, int fallbackRotationDegrees) {
        return CameraOrientation.normalize(fallbackRotationDegrees);
    }

    SharedCameraCapturePipeline createVerification(CameraRuntimeSelection selection);
}

final class DefaultSharedCameraPipelineProvider implements SharedCameraPipelineProvider {
    private static final long FIRST_ORIENTATION_SAMPLE_TIMEOUT_MILLIS = 250L;
    private final Context context;
    private final Logger logger;
    private final ToIntFunction<String> cameraOrientationDegrees;
    private final IntSupplier displayRotationDegrees;
    private final Predicate<String> frontFacing;
    private final long preRecordGopDurationMillis;
    private final NativeSurfaceSharingPipelineFactory nativeFactory;
    private final EglFanOutPipelineFactory eglFactory;
    private final OrientationEventListener mediaOrientationListener;
    private volatile boolean mediaOrientationTracking;
    private volatile int deviceOrientationDegrees =
            OrientationEventListener.ORIENTATION_UNKNOWN;
    private volatile CountDownLatch firstMediaOrientationSample = new CountDownLatch(0);
    private CameraRuntimeSelection bitrateSelection;
    private boolean bitrateIncludesAudio;
    private long recordingBitrateBitsPerSecond;

    public DefaultSharedCameraPipelineProvider(
            Context context, Logger logger, File diagnosticOutputDirectory,
            long preRecordGopDurationMillis,
            Supplier<GpsCoordinate> captureLocation,
            ToIntFunction<String> cameraOrientationDegrees,
            IntSupplier displayRotationDegrees,
            Predicate<String> frontFacing) {
        Context applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        Context runtimeContext = applicationContext == null ? context : applicationContext;
        this.context = runtimeContext;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.cameraOrientationDegrees = Objects.requireNonNull(
                cameraOrientationDegrees, "cameraOrientationDegrees");
        this.displayRotationDegrees = Objects.requireNonNull(
                displayRotationDegrees, "displayRotationDegrees");
        this.frontFacing = Objects.requireNonNull(frontFacing, "frontFacing");
        this.preRecordGopDurationMillis = preRecordGopDurationMillis;
        Objects.requireNonNull(diagnosticOutputDirectory, "diagnosticOutputDirectory");
        nativeFactory = new NativeSurfaceSharingPipelineFactory(
                runtimeContext, logger, diagnosticOutputDirectory, captureLocation);
        eglFactory = new EglFanOutPipelineFactory(
                runtimeContext, logger, diagnosticOutputDirectory, captureLocation);
        mediaOrientationListener = new OrientationEventListener(
                runtimeContext, SensorManager.SENSOR_DELAY_UI) {
            @Override public void onOrientationChanged(int orientation) {
                if (!mediaOrientationTracking) return;
                deviceOrientationDegrees =
                        orientation == OrientationEventListener.ORIENTATION_UNKNOWN
                                ? OrientationEventListener.ORIENTATION_UNKNOWN
                                : CameraOrientation.nearestQuarterTurn(orientation);
                firstMediaOrientationSample.countDown();
            }
        };
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

    @Override public synchronized long recordingBitrateBitsPerSecond(
            CameraRuntimeSelection selection) {
        Objects.requireNonNull(selection, "selection");
        boolean includeAudio = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (selection.equals(bitrateSelection) && includeAudio == bitrateIncludesAudio
                && recordingBitrateBitsPerSecond > 0L) {
            return recordingBitrateBitsPerSecond;
        }
        var video = selection.tuple().videoMode();
        var resolution = video.resolution().actual();
        try {
            recordingBitrateBitsPerSecond = SharedAvcEncoder.recordingBitrateBitsPerSecond(
                    resolution.width(), resolution.height(), video.framesPerSecond(), includeAudio);
        } catch (IOException error) {
            throw new IllegalStateException("Recording bitrate unavailable for " + video, error);
        }
        bitrateSelection = selection;
        bitrateIncludesAudio = includeAudio;
        return recordingBitrateBitsPerSecond;
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

    @Override public void startMediaOrientationTracking() {
        if (mediaOrientationTracking) return;
        CountDownLatch firstSample = new CountDownLatch(1);
        firstMediaOrientationSample = firstSample;
        mediaOrientationTracking = true;
        if (!mediaOrientationListener.canDetectOrientation()) {
            firstSample.countDown();
            logger.warn("Media orientation tracking unavailable because "
                    + "device orientation sensor is missing; photos and videos will use preview "
                    + "orientation.", null);
            return;
        }
        try {
            mediaOrientationListener.enable();
        } catch (RuntimeException error) {
            mediaOrientationTracking = false;
            firstSample.countDown();
            try {
                mediaOrientationListener.disable();
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            logger.warn("Media orientation tracking could not start; photos and videos will use "
                    + "preview orientation. A later camera bind may retry tracking.", error);
        }
    }

    @Override public void stopMediaOrientationTracking() {
        if (!mediaOrientationTracking) return;
        mediaOrientationTracking = false;
        firstMediaOrientationSample.countDown();
        if (mediaOrientationListener.canDetectOrientation()) {
            try {
                mediaOrientationListener.disable();
            } catch (RuntimeException error) {
                logger.warn("Media orientation tracking could not stop cleanly.", error);
            }
        }
    }

    @Override public int mediaRotationDegrees(
            CameraRuntimeSelection selection, int fallbackRotationDegrees) {
        Objects.requireNonNull(selection, "selection");
        int fallback = CameraOrientation.normalize(fallbackRotationDegrees);
        int device = awaitDeviceOrientationDegrees();
        if (!mediaOrientationTracking
                || device == OrientationEventListener.ORIENTATION_UNKNOWN) return fallback;
        String cameraId = selection.cameraId().value();
        try {
            return CameraOrientation.photoRotation(
                    sensorOrientationDegrees(cameraId), device, frontFacing(cameraId));
        } catch (RuntimeException error) {
            logger.warn("Media orientation could not be resolved; using "
                    + "preview orientation.", error);
            return fallback;
        }
    }

    private int awaitDeviceOrientationDegrees() {
        if (!mediaOrientationTracking) return deviceOrientationDegrees;
        try {
            firstMediaOrientationSample.await(
                    FIRST_ORIENTATION_SAMPLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            logger.warn("Media orientation wait interrupted; using preview orientation.", error);
        }
        return deviceOrientationDegrees;
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
