package com.dvid.dcam.platform.camera.shared.egl;

import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;

final class EglEncoderDetachPolicy {
    private EglEncoderDetachPolicy() {}

    record Decision(CameraOperationOutcome outcome, String detail, boolean encoderActive) {}

    static Decision decide(boolean detached, boolean interrupted) {
        if (detached) return new Decision(CameraOperationOutcome.PASS,
                "encoder_detached", false);
        if (interrupted) return new Decision(CameraOperationOutcome.CANCELLED_UNKNOWN,
                "encoder_detach_interrupted", false);
        return new Decision(CameraOperationOutcome.TRANSIENT_RETRYABLE,
                "encoder_detach_failed", false);
    }
}