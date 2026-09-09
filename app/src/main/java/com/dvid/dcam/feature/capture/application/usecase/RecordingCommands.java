package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.domain.RecordingMode;

/** Application entry point for the related video/IMP recording operations. */
public interface RecordingCommands {
    void toggleVideo();
    void startVideo();
    void startImp();
    void stopRecording();
    void toggleImp();
    RecordingMode currentMode();

    /** Returns true for startup and active recording, but not for a retained pre-start request. */
    default boolean isRecording() {
        return currentMode() != RecordingMode.IDLE;
    }

    default boolean isRecordingStartPending() {
        return false;
    }
}
