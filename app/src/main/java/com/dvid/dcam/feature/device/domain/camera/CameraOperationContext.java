package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;

public record CameraOperationContext(
        CameraId cameraId,
        VerificationPipelineId verificationPipelineId,
        VideoCodec codec,
        CaptureModeTuple tuple,
        long sessionGeneration,
        long cameraHealthGeneration,
        CameraOperationDeadline deadline) {
    public CameraOperationContext {
        cameraId = Objects.requireNonNull(cameraId, "cameraId");
        verificationPipelineId = Objects.requireNonNull(
                verificationPipelineId, "verificationPipelineId");
        codec = Objects.requireNonNull(codec, "codec");
        tuple = Objects.requireNonNull(tuple, "tuple");
        if (sessionGeneration <= 0) {
            throw new IllegalArgumentException("sessionGeneration must be positive");
        }
        if (cameraHealthGeneration < 0) {
            throw new IllegalArgumentException(
                    "cameraHealthGeneration must not be negative");
        }
        deadline = Objects.requireNonNull(deadline, "deadline");
    }

    public boolean matchesCurrentOperation(CameraOperationContext current) {
        Objects.requireNonNull(current, "current");
        return cameraId.equals(current.cameraId)
                && verificationPipelineId.equals(current.verificationPipelineId)
                && codec == current.codec
                && tuple.equals(current.tuple)
                && sessionGeneration == current.sessionGeneration
                && cameraHealthGeneration == current.cameraHealthGeneration;
    }
}