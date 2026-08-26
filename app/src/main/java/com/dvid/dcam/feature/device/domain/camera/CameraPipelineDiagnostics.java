package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;
import java.util.Optional;

public record CameraPipelineDiagnostics(
        CameraOperationContext context,
        boolean sessionBound,
        boolean previewProgressing,
        boolean encoderActive,
        boolean encoderFinalized,
        long sourceFrameCount,
        long previewFrameCount,
        long encodedSampleCount,
        int cameraOutputCount,
        int downstreamSurfaceCount,
        Optional<CameraResolution> encodedVideoResolution,
        Optional<CameraResolution> capturedJpegResolution,
        boolean jpegCapturedWhileEncoderActive,
        Optional<String> finalizedVideoArtifact,
        Optional<String> capturedJpegArtifact,
        String detail) {
    public CameraPipelineDiagnostics {
        context = Objects.requireNonNull(context, "context");
        if (sourceFrameCount < 0 || previewFrameCount < 0 || encodedSampleCount < 0) {
            throw new IllegalArgumentException("frame and sample counts must not be negative");
        }
        if (cameraOutputCount < 0 || downstreamSurfaceCount < 0) {
            throw new IllegalArgumentException("surface counts must not be negative");
        }
        if (encoderActive && encoderFinalized) {
            throw new IllegalArgumentException("active encoder cannot be finalized");
        }
        encodedVideoResolution = Objects.requireNonNull(
                encodedVideoResolution, "encodedVideoResolution");
        capturedJpegResolution = Objects.requireNonNull(
                capturedJpegResolution, "capturedJpegResolution");
        finalizedVideoArtifact = checkedArtifact(
                finalizedVideoArtifact, "finalizedVideoArtifact");
        capturedJpegArtifact = checkedArtifact(
                capturedJpegArtifact, "capturedJpegArtifact");
        if (jpegCapturedWhileEncoderActive && !capturedJpegResolution.isPresent()) {
            throw new IllegalArgumentException(
                    "JPEG capture evidence requires captured resolution");
        }
        detail = checkedDetail(detail);
    }

    public boolean hasPreviewProgressSince(long previousFrameCount) {
        if (previousFrameCount < 0) {
            throw new IllegalArgumentException(
                    "previousFrameCount must not be negative");
        }
        return previewFrameCount > previousFrameCount;
    }

    private static Optional<String> checkedArtifact(
            Optional<String> artifact, String name) {
        Optional<String> checked = Objects.requireNonNull(artifact, name);
        if (checked.isPresent() && checked.orElseThrow().isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return checked;
    }

    private static String checkedDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail is required");
        }
        return detail;
    }
}