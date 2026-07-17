package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.function.Consumer;

/** Application entry point for asynchronous camera events. */
public interface CaptureEventUseCase {
    void setListener(Consumer<CaptureEvent> listener);
    void clearListener();
    RecordingMode currentMode();
    void recordingStarted(RecordingMode mode, String fileName);
    void recordingInterrupted(String message);
    void recordingResumed();
    void recordingCompleted(String fileName);
    void recordingStoppedForStorage(String fileName);
    void photoSaved(String fileName);
    void captureFailed(String operation, String message);
}
