package com.dvid.dcam.platform.camera.shared;

import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;

public final class CameraPipelineIds {
    public static final VerificationPipelineId NATIVE_SURFACE_SHARING =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");
    public static final VerificationPipelineId EGL_FAN_OUT =
            new VerificationPipelineId("b-camera2-egl-fanout-v1");

    private CameraPipelineIds() {}
}