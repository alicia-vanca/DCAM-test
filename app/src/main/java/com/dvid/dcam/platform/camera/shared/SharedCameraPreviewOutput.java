package com.dvid.dcam.platform.camera.shared;

import android.view.Surface;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativePreviewFrameSignal;

public interface SharedCameraPreviewOutput extends NativePreviewFrameSignal {
    Surface surface();

    void resize(CameraResolution resolution);

    void setSensorOrientation(int sensorOrientationDegrees);

    void setDisplayRotation(int displayRotationDegrees);

    void setRotation(int rotationDegrees);

    void setMirrored(boolean mirrored);

    void setDisplayResolution(CameraResolution resolution);
}