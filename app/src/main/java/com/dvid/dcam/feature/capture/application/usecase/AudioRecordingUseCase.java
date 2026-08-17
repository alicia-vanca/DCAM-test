package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.application.port.AudioPreparationEvents;
import com.dvid.dcam.feature.capture.application.port.AudioRecorder;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class AudioRecordingUseCase {
    private static final AudioPreparationEvents NO_PREPARATION_EVENTS =
            new AudioPreparationEvents() {
                @Override public void onPreparing(String message) {}
                @Override public void onCleared() {}
                @Override public void onUnavailable(String message) {}
            };
    private final AudioRecorder audio;
    private final Executor ioExecutor;
    private final BooleanSupplier recordingStartAllowed;
    private final CaptureEvents captureEvents;
    private final AudioPreparationEvents preparationEvents;
    private final AtomicBoolean toggleInFlight = new AtomicBoolean();
    private final CopyOnWriteArrayList<Runnable> stateObservers =
            new CopyOnWriteArrayList<>();

    public AudioRecordingUseCase(AudioRecorder audio, CaptureEvents captureEvents) {
        this(audio, Runnable::run, () -> true, captureEvents, NO_PREPARATION_EVENTS);
    }

    public AudioRecordingUseCase(
            AudioRecorder audio, Executor ioExecutor, CaptureEvents captureEvents) {
        this(audio, ioExecutor, () -> true, captureEvents, NO_PREPARATION_EVENTS);
    }

    public AudioRecordingUseCase(
            AudioRecorder audio,
            Executor ioExecutor,
            BooleanSupplier recordingStartAllowed,
            CaptureEvents captureEvents) {
        this(audio, ioExecutor, recordingStartAllowed, captureEvents, NO_PREPARATION_EVENTS);
    }

    public AudioRecordingUseCase(
            AudioRecorder audio,
            Executor ioExecutor,
            BooleanSupplier recordingStartAllowed,
            CaptureEvents captureEvents,
            AudioPreparationEvents preparationEvents) {
        this.audio = Objects.requireNonNull(audio, "audio");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.recordingStartAllowed = Objects.requireNonNull(
                recordingStartAllowed, "recordingStartAllowed");
        this.captureEvents = Objects.requireNonNull(captureEvents, "captureEvents");
        this.preparationEvents = Objects.requireNonNull(preparationEvents, "preparationEvents");
    }

    public String toggleAudio() {
        boolean wasRecording = audio.isRecording();
        if (!wasRecording && !recordingStartAllowed.getAsBoolean()) return null;
        if (wasRecording) captureEvents.audioRecordingStopping();
        try {
            String fileName = wasRecording ? audio.toggle() : startAudio();
            reportAudioTransition(wasRecording, fileName, wasRecording);
            return fileName;
        } catch (RuntimeException error) {
            if (wasRecording) captureEvents.audioRecordingStopCancelled();
            throw error;
        } finally {
            publishState();
        }
    }

    public boolean toggleAudioAsync() {
        boolean starting = !audio.isRecording();
        if (starting && !recordingStartAllowed.getAsBoolean()) return false;
        if (!toggleInFlight.compareAndSet(false, true)) return false;
        boolean stopping = !starting;
        if (stopping) captureEvents.audioRecordingStopping();
        try {
            ioExecutor.execute(() -> {
                boolean wasRecording = audio.isRecording();
                try {
                    String fileName = null;
                    if (wasRecording || recordingStartAllowed.getAsBoolean()) {
                        fileName = wasRecording ? audio.toggle() : startAudio();
                    }
                    reportAudioTransition(wasRecording, fileName, stopping);
                } catch (RuntimeException error) {
                    if (stopping) captureEvents.audioRecordingStopCancelled();
                    throw error;
                } finally {
                    toggleInFlight.set(false);
                    publishState();
                }
            });
            return true;
        } catch (RuntimeException error) {
            toggleInFlight.set(false);
            if (stopping) captureEvents.audioRecordingStopCancelled();
            publishState();
            throw error;
        }
    }


    private String startAudio() {
        try {
            String fileName = audio.toggle();
            clearPreparation();
            return fileName;
        } catch (AudioRecorder.PreparationException error) {
            if (error.retryable()) showPreparing(error.getMessage());
            else showUnavailable(error.terminalMessage());
            return null;
        }
    }

    private void showPreparing(String message) {
        preparationEvents.onPreparing(message);
    }

    private void clearPreparation() {
        preparationEvents.onCleared();
    }

    private void showUnavailable(String message) {
        preparationEvents.onUnavailable(message);
    }


    private void reportAudioTransition(
            boolean wasRecording, String fileName, boolean stoppingRequested) {
        boolean recording = audio.isRecording();
        if (wasRecording == recording) {
            if (stoppingRequested) {
                if (recording) captureEvents.audioRecordingStopCancelled();
                else captureEvents.audioRecordingStopped(null);
            }
            return;
        }
        if (recording) {
            long startedAtMillis = audio.recordingStartedAtMillis();
            captureEvents.audioRecordingStarted(fileName,
                    startedAtMillis >= 0L ? startedAtMillis : System.currentTimeMillis());
        } else {
            captureEvents.audioRecordingStopped(fileName);
        }
    }

    public Runnable observeStateChanges(Runnable observer) {
        Runnable checked = Objects.requireNonNull(observer, "observer");
        stateObservers.add(checked);
        return () -> stateObservers.remove(checked);
    }

    public boolean isAudioRecording() { return audio.isRecording(); }

    public boolean hasPendingWork() { return toggleInFlight.get() || audio.hasPendingWork(); }

    private void publishState() { stateObservers.forEach(Runnable::run); }

}
