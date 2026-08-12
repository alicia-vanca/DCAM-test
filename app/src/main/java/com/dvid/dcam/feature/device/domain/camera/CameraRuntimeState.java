package com.dvid.dcam.feature.device.domain.camera;

public enum CameraRuntimeState {
    CLOSED,
    OPENING,
    BINDING,
    VERIFYING,
    READY,
    RECORDING,
    RECOVERING,
    RELEASING
}