package com.dvid.dcam.feature.device.domain.camera;

public enum CameraFailureClass {
    NONE,
    SESSION_CONFIGURATION,
    VIDEO_ENCODER,
    VIDEO_OUTPUT,
    JPEG_CAPTURE,
    JPEG_OUTPUT,
    TOPOLOGY,
    UNKNOWN
}