package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.function.Consumer;

public final class CaptureEventUseCaseImpl implements CaptureEventUseCase {
    private static final Consumer<CaptureEvent> NONE = event -> {};

    private Consumer<CaptureEvent> listener = NONE;
    private RecordingMode currentMode = RecordingMode.IDLE;
    private CaptureEvent snapshot;

    @Override public void setListener(Consumer<CaptureEvent> listener) {
        this.listener = listener == null ? NONE : listener;
        if (snapshot != null) this.listener.accept(snapshot);
    }

    @Override public void clearListener() {
        listener = NONE;
    }

    @Override public RecordingMode currentMode() {
        return currentMode;
    }

    @Override public void recordingStarted(RecordingMode mode, String fileName) {
        currentMode = mode;
        emit(CaptureEvent.recordingStarted(mode, fileName));
    }

    @Override public void recordingInterrupted(String message) {
        emit(CaptureEvent.recordingInterrupted(currentMode, message));
    }

    @Override public void recordingResumed() {
        emit(CaptureEvent.recordingResumed(currentMode));
    }
    @Override public void recordingCompleted(String fileName) {
        currentMode = RecordingMode.IDLE;
        emit(CaptureEvent.recordingCompleted(fileName));
    }
    @Override public void recordingStoppedForStorage(String fileName) {
        currentMode = RecordingMode.IDLE;
        emit(CaptureEvent.recordingStoppedForStorage(fileName));
    }

    @Override public void photoSaved(String fileName) {
        emit(CaptureEvent.photoSaved(fileName));
    }

    @Override public void captureFailed(String operation, String message) {
        currentMode = RecordingMode.IDLE;
        emit(CaptureEvent.error(operation, message));
    }

    private void emit(CaptureEvent event) {
        snapshot = event;
        listener.accept(event);
    }
}
