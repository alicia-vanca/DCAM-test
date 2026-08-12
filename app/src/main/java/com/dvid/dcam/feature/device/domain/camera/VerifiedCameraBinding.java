package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;

public record VerifiedCameraBinding(
        CameraOperationContext context,
        CameraPipelineDiagnostics diagnostics,
        int sensorOrientationDegrees) {
    public VerifiedCameraBinding {
        context = Objects.requireNonNull(context, "context");
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        sensorOrientationDegrees = Math.floorMod(sensorOrientationDegrees, 360);
        if (sensorOrientationDegrees % 90 != 0) {
            throw new IllegalArgumentException(
                    "sensorOrientationDegrees must be a multiple of 90");
        }
        if (!context.matchesCurrentOperation(diagnostics.context())) {
            throw new IllegalArgumentException("diagnostics belong to another operation");
        }
        if (!diagnostics.sessionBound()) {
            throw new IllegalArgumentException("verified binding must retain a bound session");
        }
        if (diagnostics.encoderActive() || !diagnostics.encoderFinalized()) {
            throw new IllegalArgumentException(
                    "verified binding requires finalized inactive encoder");
        }
        if (diagnostics.encodedSampleCount() <= 0
                || diagnostics.encodedVideoResolution().isEmpty()
                || diagnostics.capturedJpegResolution().isEmpty()
                || !diagnostics.jpegCapturedWhileEncoderActive()) {
            throw new IllegalArgumentException("verified binding lacks output evidence");
        }
        CameraResolution expectedVideo = context.tuple()
                .videoMode().resolution().actual();
        CameraResolution expectedImage = context.tuple()
                .imageMode().resolution().actual();
        if (!expectedVideo.equals(diagnostics.encodedVideoResolution().orElseThrow())
                || !expectedImage.matchesConsideringRotation(
                diagnostics.capturedJpegResolution().orElseThrow(),
                sensorOrientationDegrees)) {
            throw new IllegalArgumentException("verified binding output size mismatch");
        }
    }
}