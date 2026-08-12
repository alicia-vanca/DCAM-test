package com.dvid.dcam.feature.device.domain.camera;

public enum CameraPipelineOperation {
    BIND_SESSION,
    UPDATE_SESSION,
    PREVIEW_PROGRESS,
    START_ENCODER,
    STOP_ENCODER,
    FINALIZE_ENCODER,
    CAPTURE_JPEG,
    RELEASE,
    DIAGNOSTICS
}