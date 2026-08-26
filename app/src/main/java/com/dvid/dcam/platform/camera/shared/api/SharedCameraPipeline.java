package com.dvid.dcam.platform.camera.shared.api;

import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;

public interface SharedCameraPipeline {
    VerificationPipelineId pipelineId();

    CameraOperationResult bindSession(CameraOperationContext context);

    default CameraOperationResult bindStandaloneImageSession(CameraOperationContext context) {
        return bindSession(context);
    }

    CameraOperationResult previewProgress(CameraOperationContext context);

    CameraOperationResult startEncoder(CameraOperationContext context);

    CameraOperationResult stopEncoder(CameraOperationContext context);

    CameraOperationResult finalizeEncoder(CameraOperationContext context);

    CameraOperationResult captureJpeg(CameraOperationContext context);

    CameraOperationResult release(CameraOperationContext context);

    CameraPipelineDiagnostics diagnostics(CameraOperationContext context);
}
