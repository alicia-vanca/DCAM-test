package com.dvid.dcam.feature.capture.application.port;

/** Camera capability required by application workflows, independent of camera framework/vendor APIs. */
public interface CameraGateway {
    void takePhoto();
    void startVideo();
    void startImp();
    void stopRecording();
}
