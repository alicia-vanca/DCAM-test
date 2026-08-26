package com.dvid.dcam.app.ui;

/** Formats the camera identity shown on the camera screen. */
public final class CameraIdentityPresentation {
    private CameraIdentityPresentation() {}

    public static String cameraLabel(String serial, boolean configured) {
        return configured ? "CAM " + serial : "CAM —";
    }
}
