package com.dvid.dcam.platform.camera.shared.outputsharing;

final class NativeFrameTargetPolicy {
    record Decision(boolean includePreview, boolean includeEncoder,
            boolean previewSuppressed) {}

    private NativeFrameTargetPolicy() {}

    static Decision repeating(boolean encoderActive) {
        return new Decision(true, encoderActive, false);
    }

    static Decision jpeg(boolean externalPreview, boolean encoderActive) {
        boolean includePreview = !encoderActive || !externalPreview;
        return new Decision(includePreview, encoderActive,
                encoderActive && externalPreview);
    }
}