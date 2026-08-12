package com.dvid.dcam.feature.device.application.port;

import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;

public interface CameraRuntimeOperations {
    CameraOperationResult bindSession(CameraOperationContext context);

    default CameraOperationResult bindStandaloneImageSession(CameraOperationContext context) {
        return bindSession(context);
    }

    CameraOperationResult updateSession(CameraOperationContext context);

    CameraOperationResult previewProgress(CameraOperationContext context);

    CameraOperationResult startEncoder(CameraOperationContext context);

    CameraOperationResult stopEncoder(CameraOperationContext context);

    CameraOperationResult finalizeEncoder(CameraOperationContext context);

    CameraOperationResult captureJpeg(CameraOperationContext context);

    CameraOperationResult release(CameraOperationContext context);

    CameraPipelineDiagnostics diagnostics(CameraOperationContext context);
}