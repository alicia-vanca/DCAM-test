package com.dvid.dcam.platform.camera.shared.egl;

public final class EglFrameDispatchPolicy {
    public record Decision(boolean renderEncoder, boolean renderPreview,
            boolean countPreviewDrop) {}

    private EglFrameDispatchPolicy() {}

    public static Decision decide(boolean encoderActive,
            boolean previewAvailable, int pendingSourceFrames) {
        if (pendingSourceFrames < 0) {
            throw new IllegalArgumentException("pendingSourceFrames must not be negative");
        }
        boolean renderPreview = previewAvailable && pendingSourceFrames <= 1;
        return new Decision(encoderActive, renderPreview,
                previewAvailable && !renderPreview);
    }
}