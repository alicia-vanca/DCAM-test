package com.dvid.dcam.platform.camera.shared;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;

import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import java.util.Objects;

public final class CameraPipelineFailureClassifier {
    public enum Signal {
        CAMERA_IN_USE,
        MAX_CAMERAS_IN_USE,
        CAMERA_DISABLED,
        CAMERA_DEVICE_ERROR,
        CAMERA_SERVICE_ERROR,
        CAMERA_DISCONNECTED,
        PERMISSION_BLOCKED,
        STORAGE_BLOCKED,
        SESSION_REJECTED,
        CODEC_REJECTED,
        OUTPUT_INVALID,
        EGL_UNAVAILABLE,
        EGL_BAD_ALLOC,
        EGL_CONTEXT_LOST,
        TIMEOUT,
        CANCELLED,
        STALE,
        UNKNOWN_GLOBAL
    }

    private CameraPipelineFailureClassifier() {}

    public static CameraOperationOutcome classify(Signal signal) {
        return switch (Objects.requireNonNull(signal, "signal")) {
            case CAMERA_IN_USE, MAX_CAMERAS_IN_USE, CAMERA_DEVICE_ERROR,
                    CAMERA_DISCONNECTED -> CameraOperationOutcome.TRANSIENT_RETRYABLE;
            case CAMERA_SERVICE_ERROR, EGL_BAD_ALLOC, EGL_CONTEXT_LOST,
                    UNKNOWN_GLOBAL -> CameraOperationOutcome.GLOBAL_FAILURE;
            case CAMERA_DISABLED, PERMISSION_BLOCKED, STORAGE_BLOCKED,
                    EGL_UNAVAILABLE -> CameraOperationOutcome.BLOCKED_EXTERNAL;
            case SESSION_REJECTED, CODEC_REJECTED,
                    OUTPUT_INVALID -> CameraOperationOutcome.CANDIDATE_SUSPECT;
            case TIMEOUT -> CameraOperationOutcome.TIMEOUT_UNKNOWN;
            case CANCELLED -> CameraOperationOutcome.CANCELLED_UNKNOWN;
            case STALE -> CameraOperationOutcome.STALE;
        };
    }

    public static CameraOperationOutcome classifyCameraAccessReason(int reason) {
        Signal signal = switch (reason) {
            case CameraAccessException.CAMERA_IN_USE -> Signal.CAMERA_IN_USE;
            case CameraAccessException.MAX_CAMERAS_IN_USE -> Signal.MAX_CAMERAS_IN_USE;
            case CameraAccessException.CAMERA_DISABLED -> Signal.CAMERA_DISABLED;
            case CameraAccessException.CAMERA_DISCONNECTED -> Signal.CAMERA_DISCONNECTED;
            case CameraAccessException.CAMERA_ERROR -> Signal.CAMERA_SERVICE_ERROR;
            default -> Signal.UNKNOWN_GLOBAL;
        };
        return classify(signal);
    }

    public static CameraOperationOutcome classifyCameraStateError(int errorCode) {
        Signal signal = switch (errorCode) {
            case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> Signal.CAMERA_IN_USE;
            case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE ->
                    Signal.MAX_CAMERAS_IN_USE;
            case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> Signal.CAMERA_DISABLED;
            case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> Signal.CAMERA_DEVICE_ERROR;
            case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> Signal.CAMERA_SERVICE_ERROR;
            default -> Signal.UNKNOWN_GLOBAL;
        };
        return classify(signal);
    }

    public static CameraOperationOutcome withCleanupResult(
            CameraOperationOutcome original, boolean cleanupCompleted) {
        Objects.requireNonNull(original, "original");
        return cleanupCompleted ? original : CameraOperationOutcome.TRANSIENT_RETRYABLE;
    }
}