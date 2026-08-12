package com.dvid.dcam.platform.camera.shared.runtime;

import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import java.util.Objects;

public record CameraRuntimeSelection(
        CameraId cameraId,
        VerificationPipelineId verificationPipelineId,
        VideoCodec codec,
        CaptureModeTuple tuple) {
    public CameraRuntimeSelection {
        cameraId = Objects.requireNonNull(cameraId, "cameraId");
        verificationPipelineId = Objects.requireNonNull(
                verificationPipelineId, "verificationPipelineId");
        codec = Objects.requireNonNull(codec, "codec");
        tuple = Objects.requireNonNull(tuple, "tuple");
    }

    public boolean matches(CameraOperationContext context) {
        Objects.requireNonNull(context, "context");
        return cameraId.equals(context.cameraId())
                && verificationPipelineId.equals(context.verificationPipelineId())
                && codec == context.codec()
                && tuple.equals(context.tuple());
    }
}