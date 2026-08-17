package com.dvid.dcam.platform.camera.shared;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.view.Surface;
import android.view.WindowManager;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEvents;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeOwner;
import com.dvid.dcam.platform.device.capability.CameraCapabilityService;
import com.dvid.dcam.platform.storage.DcamMediaOutput;
import java.io.File;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SharedCameraGatewayFactory {
    public record Components(
            SharedCameraGateway gateway,
            SharedCameraPreviewView.SurfaceHandle previewSurface) {
        public Components {
            gateway = Objects.requireNonNull(gateway, "gateway");
            previewSurface = Objects.requireNonNull(previewSurface, "previewSurface");
        }

        public SharedCameraPreviewView createPreview(
                Context context,
                Logger logger,
                Consumer<CharSequence> transientNotice,
                Consumer<CharSequence> persistentNotice,
                Runnable clearPersistentNotice) {
            SharedCameraPreviewView view = new SharedCameraPreviewView(
                    context, logger, previewSurface, transientNotice,
                    persistentNotice, clearPersistentNotice);
            view.setPreviewExpectedChanged(
                    expected -> gateway.setPreviewExpected(view, expected));
            gateway.attachPreview(view);
            return view;
        }
    }

    private SharedCameraGatewayFactory() {}

    public static Components create(
            Context context,
            ProcessCameraRuntimeOwner runtimeOwner,
            CameraCapabilityService capabilities,
            CaptureEvents captureEvents,
            DcamMediaOutput mediaOutput,
            Supplier<String> deviceSerialNumber,
            Supplier<String> operatorFileUserId,
            BooleanSupplier mediaEncryptionEnabled,
            Supplier<GpsCoordinate> captureLocation,
            long preRecordGopDurationMillis,
            Logger logger) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(runtimeOwner, "runtimeOwner");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(captureEvents, "captureEvents");
        Objects.requireNonNull(mediaOutput, "mediaOutput");
        Objects.requireNonNull(captureLocation, "captureLocation");
        Objects.requireNonNull(logger, "logger");
        runtimeOwner.installMediaReservation(mediaOutput::mediaReservationDelayMillis);
        SharedCameraPreviewView.SurfaceHandle previewSurface =
                new SharedCameraPreviewView.SurfaceHandle();
        File diagnosticOutput = new File(context.getCacheDir(), "shared-camera-runtime");
        CameraManager cameraManager = context.getSystemService(CameraManager.class);
        SharedCameraPipelineProvider pipelines = new DefaultSharedCameraPipelineProvider(
                context, logger, diagnosticOutput, preRecordGopDurationMillis,
                captureLocation, capabilities::cameraOrientationDegrees,
                () -> displayRotationDegrees(context),
                cameraId -> isFrontFacing(cameraManager, cameraId));
        SharedCameraMediaLifecycle mediaLifecycle = new AndroidSharedCameraMediaLifecycle(
                context, mediaOutput, deviceSerialNumber, operatorFileUserId,
                mediaEncryptionEnabled);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                pipelines, previewSurface, mediaLifecycle, capabilities, captureEvents, logger);
        SharedCameraGateway gateway = new SharedCameraGateway(
                runtimeOwner, backend, captureEvents, logger);
        return new Components(gateway, previewSurface);
    }

    private static int displayRotationDegrees(Context context) {
        WindowManager windowManager = context.getSystemService(WindowManager.class);
        if (windowManager == null) throw new IllegalStateException("window manager unavailable");
        return switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0 -> 0;
            case Surface.ROTATION_90 -> 90;
            case Surface.ROTATION_180 -> 180;
            case Surface.ROTATION_270 -> 270;
            default -> throw new IllegalStateException("unsupported display rotation");
        };
    }

    private static boolean isFrontFacing(CameraManager cameraManager, String cameraId) {
        if (cameraManager == null) throw new IllegalStateException("camera manager unavailable");
        try {
            Integer lensFacing = cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING);
            if (lensFacing == null) {
                throw new IllegalStateException("camera lens facing unavailable:" + cameraId);
            }
            return lensFacing == CameraCharacteristics.LENS_FACING_FRONT;
        } catch (CameraAccessException error) {
            throw new IllegalStateException("camera lens facing query failed:" + cameraId, error);
        }
    }
}