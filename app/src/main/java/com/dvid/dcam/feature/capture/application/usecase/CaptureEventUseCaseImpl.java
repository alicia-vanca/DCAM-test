package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.function.Consumer;

public final class CaptureEventUseCaseImpl implements CaptureEventUseCase {
    private static final Consumer<CaptureEvent> NONE = event -> {};

    private Consumer<CaptureEvent> listener = NONE;
    private RecordingMode currentMode = RecordingMode.IDLE;

    @Override public void setListener(Consumer<CaptureEvent> listener) {
        this.listener = listener == null ? NONE : listener;
    }

    @Override public void clearListener() {
        listener = NONE;
    }

    @Override public RecordingMode currentMode() {
        return currentMode;
    }

    @Override public void recordingStarted(RecordingMode mode, String fileName) {
        currentMode = mode;
        listener.accept(CaptureEvent.recordingStarted(mode, fileName));
    }

    @Override public void recordingCompleted(String fileName) {
        currentMode = RecordingMode.IDLE;
        listener.accept(CaptureEvent.recordingCompleted(fileName));
    }
    @Override public void recordingStoppedForStorage(String fileName) {
        currentMode = RecordingMode.IDLE;
        listener.accept(CaptureEvent.recordingStoppedForStorage(fileName));
    }

    @Override public void photoSaved(String fileName) {
        listener.accept(CaptureEvent.photoSaved(fileName));
    }

    @Override public void captureFailed(String operation, String message) {
        currentMode = RecordingMode.IDLE;
        listener.accept(CaptureEvent.error(operation, message));
    }
}
