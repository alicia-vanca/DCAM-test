package com.dvid.dcam.app.ui;

import android.widget.FrameLayout;

/** View-lifecycle presentation surface implemented by the persistent camera Fragment. */
public interface CameraScreenView {
    FrameLayout previewContainer();
    void setRootAlpha(float alpha);
    void post(Runnable action);
    void setIdentity(String value);
    void setGps(boolean visible, String value);
    void setCameraSwitch(boolean visible, boolean enabled);
    void setRecordingBadgeAlpha(float alpha);
    void renderRecordingClock(String currentTime, boolean videoVisible, String videoDuration,
            boolean audioVisible, String audioDuration);
}
