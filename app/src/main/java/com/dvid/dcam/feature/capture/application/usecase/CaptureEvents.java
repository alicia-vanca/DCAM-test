package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.function.Consumer;

/** Application entry point for asynchronous camera events. */
public interface CaptureEvents {
    void setListener(Consumer<CaptureEvent> listener);
    void clearListener();
    RecordingMode currentMode();
    default void recordingStartBlocked() {}
    void recordingStarted(RecordingMode mode, String fileName);
    void recordingInterrupted(String message);
    void recordingResumed();
    void recordingCompleted(String fileName);
    void recordingStoppedForStorage(String fileName);
    void audioRecordingStarted(String fileName, long startedAtMillis);
    default void audioRecordingStopping() {}
    default void audioRecordingStopCancelled() {}
    void audioRecordingStopped(String fileName);
    default void photoSaving() {}
    void photoSaved(String fileName);
    void photoFailed(String operation, String message);
    void captureFailed(String operation, String message);
}
