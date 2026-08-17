package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.application.port.CameraGateway;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Single application authority for video/IMP recording commands and camera events.
 *
 * <p>All mutable recording state is owned by one FIFO queue. Camera/file/DB work remains behind
 * its platform boundaries and must report completion back through this coordinator.</p>
 */
public final class SerializedRecordingCoordinator
        implements RecordingCommands, CaptureEvents {
    private static final Consumer<CaptureEvent> NO_LISTENER = event -> {};

    private enum Phase {
        IDLE,
        STARTING,
        RECORDING,
        INTERRUPTED,
        STOPPING,
        SWITCHING_TO_IMP
    }

    private final SerialExecutor queue;
    private final BooleanSupplier recordingStartAllowed;
    private final BooleanSupplier recordingStartBlocked;
    private final Runnable recordingStartPreparation;
    private volatile Consumer<CaptureEvent> listener = NO_LISTENER;
    private volatile RecordingMode currentMode = RecordingMode.IDLE;

    // Accessed only by queue tasks after construction-time binding.
    private CameraGateway camera;
    private Phase phase = Phase.IDLE;
    private volatile RecordingMode requestedMode = RecordingMode.IDLE;
    private volatile RecordingMode pendingStartMode = RecordingMode.IDLE;
    private String currentFileName;
    private String interruptionMessage;
    private CaptureEvent lastTerminalEvent;
    private volatile boolean photoSaving;
    private boolean audioStateInitialized;
    private boolean audioRecording;
    private boolean audioSaving;
    private String audioFileName;
    private long audioStartedAtMillis = -1L;
    private CaptureEvent lastAudioTerminalEvent;

    public SerializedRecordingCoordinator(Executor executor) {
        this(executor, () -> true, () -> false);
    }

    public SerializedRecordingCoordinator(
            Executor executor, BooleanSupplier recordingStartAllowed) {
        this(executor, recordingStartAllowed, () -> false);
    }

    public SerializedRecordingCoordinator(
            Executor executor, BooleanSupplier recordingStartAllowed,
            BooleanSupplier recordingStartBlocked) {
        this(executor, recordingStartAllowed, recordingStartBlocked, () -> {});
    }

    public SerializedRecordingCoordinator(
            Executor executor, BooleanSupplier recordingStartAllowed,
            BooleanSupplier recordingStartBlocked, Runnable recordingStartPreparation) {
        queue = new SerialExecutor(Objects.requireNonNull(executor, "executor"));
        this.recordingStartAllowed = Objects.requireNonNull(
                recordingStartAllowed, "recordingStartAllowed");
        this.recordingStartBlocked = Objects.requireNonNull(
                recordingStartBlocked, "recordingStartBlocked");
        this.recordingStartPreparation = Objects.requireNonNull(
                recordingStartPreparation, "recordingStartPreparation");
    }
    /** Binds the one platform camera target used by this coordinator. */
    public synchronized void bindCamera(CameraGateway camera) {
        if (this.camera != null) throw new IllegalStateException("Camera already bound");
        this.camera = Objects.requireNonNull(camera, "camera");
    }

    @Override public void toggleVideo() {
        queue.execute(() -> {
            if (phase == Phase.STARTING) return;
            if (pendingStartMode != RecordingMode.IDLE) {
                recordingStartPreparation.run();
                return;
            }
            if (phase == Phase.IDLE) {
                requestStart(RecordingMode.VIDEO);
            } else {
                stop();
            }
        });
    }

    @Override public void startVideo() {
        queue.execute(() -> {
            if (phase != Phase.IDLE) return;
            if (pendingStartMode != RecordingMode.IDLE) {
                recordingStartPreparation.run();
                return;
            }
            requestStart(RecordingMode.VIDEO);
        });
    }

    @Override public void startImp() {
        queue.execute(() -> {
            if (phase == Phase.IDLE) {
                if (pendingStartMode == RecordingMode.IMP) recordingStartPreparation.run();
                else requestStart(RecordingMode.IMP);
            } else if (phase == Phase.RECORDING && currentMode != RecordingMode.IMP) {
                if (recordingStartBlocked.getAsBoolean()) return;
                requestedMode = RecordingMode.IMP;
                phase = Phase.SWITCHING_TO_IMP;
                camera().startImp();
            }
        });
    }

    @Override public void stopRecording() {
        queue.execute(this::stop);
    }

    @Override public void toggleImp() {
        queue.execute(() -> {
            if (phase == Phase.STARTING) return;
            if (pendingStartMode != RecordingMode.IDLE) {
                recordingStartPreparation.run();
                return;
            }
            if (phase == Phase.IDLE) {
                requestStart(RecordingMode.IMP);
            } else if (phase == Phase.RECORDING && currentMode != RecordingMode.IMP) {
                if (recordingStartBlocked.getAsBoolean()) return;
                requestedMode = RecordingMode.IMP;
                phase = Phase.SWITCHING_TO_IMP;
                camera().startImp();
            } else {
                stop();
            }
        });
    }
    @Override public void setListener(Consumer<CaptureEvent> listener) {
        Consumer<CaptureEvent> next = listener == null ? NO_LISTENER : listener;
        queue.execute(() -> {
            this.listener = next;
            replayCurrentState();
        });
    }

    @Override public void clearListener() {
        queue.execute(() -> listener = NO_LISTENER);
    }

    @Override public RecordingMode currentMode() {
        RecordingMode active = currentMode;
        if (active != RecordingMode.IDLE) return active;
        RecordingMode requested = requestedMode;
        return requested == RecordingMode.IDLE ? pendingStartMode : requested;
    }

    @Override public boolean isRecordingStartPending() {
        return currentMode == RecordingMode.IDLE
                && (requestedMode != RecordingMode.IDLE
                || pendingStartMode != RecordingMode.IDLE);
    }

    public boolean hasPhotoWork() { return photoSaving; }

    public void resumePendingStart() {
        queue.execute(() -> {
            if (pendingStartMode == RecordingMode.IDLE) return;
            if (recordingStartBlocked.getAsBoolean()) {
                pendingStartMode = RecordingMode.IDLE;
                return;
            }
            if (phase != Phase.IDLE || !recordingStartAllowed.getAsBoolean()) return;
            RecordingMode mode = pendingStartMode;
            pendingStartMode = RecordingMode.IDLE;
            start(mode);
        });
    }

    public void discardPendingStart() {
        queue.execute(() -> pendingStartMode = RecordingMode.IDLE);
    }

    @Override public void recordingStartBlocked() {
        queue.execute(() -> {
            if (phase != Phase.STARTING) return;
            currentMode = RecordingMode.IDLE;
            requestedMode = RecordingMode.IDLE;
            pendingStartMode = RecordingMode.IDLE;
            currentFileName = null;
            interruptionMessage = null;
            lastTerminalEvent = CaptureEvent.recordingStartCancelled();
            phase = Phase.IDLE;
            emit(lastTerminalEvent);
        });
    }

    @Override public void recordingStarted(RecordingMode mode, String fileName) {
        queue.execute(() -> {
            if (phase != Phase.STARTING && phase != Phase.SWITCHING_TO_IMP) return;
            currentMode = Objects.requireNonNull(mode, "mode");
            requestedMode = RecordingMode.IDLE;
            currentFileName = fileName;
            interruptionMessage = null;
            lastTerminalEvent = null;
            phase = Phase.RECORDING;
            emit(CaptureEvent.recordingStarted(mode, fileName));
        });
    }

    @Override public void recordingInterrupted(String message) {
        queue.execute(() -> {
            if (phase != Phase.RECORDING || currentMode == RecordingMode.IDLE) return;
            phase = Phase.INTERRUPTED;
            interruptionMessage = message;
            emit(CaptureEvent.recordingInterrupted(currentMode, message));
        });
    }

    @Override public void recordingResumed() {
        queue.execute(() -> {
            if (phase != Phase.INTERRUPTED || currentMode == RecordingMode.IDLE) return;
            phase = Phase.RECORDING;
            interruptionMessage = null;
            emit(CaptureEvent.recordingResumed(currentMode));
        });
    }
    @Override public void recordingCompleted(String fileName) {
        queue.execute(() -> completeSegment(CaptureEvent.recordingCompleted(fileName)));
    }
    @Override public void recordingStoppedForStorage(String fileName) {
        queue.execute(() -> completeSegment(CaptureEvent.recordingStoppedForStorage(fileName)));
    }

    @Override public void audioRecordingStarted(String fileName, long startedAtMillis) {
        queue.execute(() -> {
            audioStateInitialized = true;
            audioRecording = true;
            audioSaving = false;
            audioFileName = fileName;
            audioStartedAtMillis = startedAtMillis;
            lastAudioTerminalEvent = null;
            emit(CaptureEvent.audioRecordingStarted(fileName, startedAtMillis));
        });
    }

    @Override public void audioRecordingStopping() {
        queue.execute(() -> {
            if (!audioRecording) return;
            audioStateInitialized = true;
            audioRecording = false;
            audioSaving = true;
            lastAudioTerminalEvent = null;
            emit(CaptureEvent.audioRecordingStopping());
        });
    }

    @Override public void audioRecordingStopCancelled() {
        queue.execute(() -> {
            if (!audioSaving) return;
            audioSaving = false;
            audioRecording = true;
            emit(CaptureEvent.audioRecordingStarted(audioFileName, audioStartedAtMillis));
        });
    }

    @Override public void audioRecordingStopped(String fileName) {
        queue.execute(() -> {
            audioStateInitialized = true;
            audioRecording = false;
            audioSaving = false;
            audioFileName = null;
            audioStartedAtMillis = -1L;
            lastAudioTerminalEvent = CaptureEvent.audioRecordingStopped(fileName);
            emit(lastAudioTerminalEvent);
        });
    }

    @Override public void photoSaving() {
        queue.execute(() -> {
            photoSaving = true;
            lastTerminalEvent = null;
            emit(CaptureEvent.photoSaving());
        });
    }

    @Override public void photoSaved(String fileName) {
        queue.execute(() -> {
            photoSaving = false;
            lastTerminalEvent = CaptureEvent.photoSaved(fileName);
            emit(lastTerminalEvent);
        });
    }

    @Override public void photoFailed(String operation, String message) {
        queue.execute(() -> {
            photoSaving = false;
            lastTerminalEvent = CaptureEvent.photoFailed(operation, message);
            emit(lastTerminalEvent);
        });
    }

    @Override public void captureFailed(String operation, String message) {
        queue.execute(() -> completeSegment(CaptureEvent.error(operation, message)));
    }

    private void completeSegment(CaptureEvent event) {
        currentMode = RecordingMode.IDLE;
        currentFileName = null;
        interruptionMessage = null;
        lastTerminalEvent = event;
        if (phase == Phase.SWITCHING_TO_IMP && requestedMode == RecordingMode.IMP) {
            phase = Phase.STARTING;
        } else {
            requestedMode = RecordingMode.IDLE;
            phase = Phase.IDLE;
        }
        emit(event);
    }

    private void requestStart(RecordingMode mode) {
        if (recordingStartBlocked.getAsBoolean()) return;
        pendingStartMode = mode;
        if (!recordingStartAllowed.getAsBoolean()) {
            recordingStartPreparation.run();
            return;
        }
        pendingStartMode = RecordingMode.IDLE;
        start(mode);
    }
    private void start(RecordingMode mode) {

        requestedMode = mode;
        currentFileName = null;
        interruptionMessage = null;
        lastTerminalEvent = null;
        phase = Phase.STARTING;
        emit(CaptureEvent.recordingStarting(mode));
        if (mode == RecordingMode.IMP) camera().startImp();
        else camera().startVideo();
    }
    private void stop() {
        if (phase == Phase.IDLE) {
            pendingStartMode = RecordingMode.IDLE;
            return;
        }
        if (phase == Phase.STOPPING) return;
        if (phase == Phase.STARTING) {
            requestedMode = RecordingMode.IDLE;
            phase = Phase.IDLE;
            currentFileName = null;
            interruptionMessage = null;
            camera().stopRecording();
            return;
        }
        RecordingMode stoppingMode = currentMode == RecordingMode.IDLE
                ? requestedMode : currentMode;
        requestedMode = RecordingMode.IDLE;
        phase = Phase.STOPPING;
        emit(CaptureEvent.recordingStopping(stoppingMode));
        camera().stopRecording();
    }
    private void emit(CaptureEvent event) {
        listener.accept(event);
    }


    private void replayCurrentState() {
        switch (phase) {
            case STARTING:
                emitReplay(CaptureEvent.recordingStarting(requestedMode));
                break;
            case RECORDING:
                emitReplay(CaptureEvent.recordingStarted(currentMode, currentFileName));
                break;
            case INTERRUPTED:
                emitReplay(CaptureEvent.recordingStarted(currentMode, currentFileName));
                emitReplay(CaptureEvent.recordingInterrupted(currentMode, interruptionMessage));
                break;
            case STOPPING:
                RecordingMode stoppingMode = currentMode == RecordingMode.IDLE
                        ? requestedMode : currentMode;
                if (currentFileName != null) {
                    emitReplay(CaptureEvent.recordingStarted(stoppingMode, currentFileName));
                } else {
                    emitReplay(CaptureEvent.recordingStarting(stoppingMode));
                }
                emitReplay(CaptureEvent.recordingStopping(stoppingMode));
                break;
            case SWITCHING_TO_IMP:
                emitReplay(CaptureEvent.recordingStarted(currentMode, currentFileName));
                break;
            case IDLE:
                if (lastTerminalEvent != null
                        && lastTerminalEvent.getType() != CaptureEvent.Type.ERROR
                        && lastTerminalEvent.getType() != CaptureEvent.Type.PHOTO_FAILED) {
                    emitReplay(lastTerminalEvent);
                }
                break;
            default:
                throw new IllegalStateException("Unsupported recording phase " + phase);
        }
        if (photoSaving) emitReplay(CaptureEvent.photoSaving());
        if (audioStateInitialized) {
            if (audioSaving) {
                emitReplay(CaptureEvent.audioRecordingStopping());
            } else if (audioRecording) {
                emitReplay(CaptureEvent.audioRecordingStarted(audioFileName, audioStartedAtMillis));
            } else if (lastAudioTerminalEvent != null) {
                emitReplay(lastAudioTerminalEvent);
            } else {
                emitReplay(CaptureEvent.audioRecordingStopped(null));
            }
        }
    }

    private void emitReplay(CaptureEvent event) {
        listener.accept(event.asReplay());
    }

    private synchronized CameraGateway camera() {
        if (camera == null) throw new IllegalStateException("Camera not bound");
        return camera;
    }

    /** FIFO serialization on top of the executor selected by the composition root. */
    private static final class SerialExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private final Executor executor;
        private Runnable active;

        private SerialExecutor(Executor executor) {
            this.executor = executor;
        }

        @Override public synchronized void execute(Runnable command) {
            tasks.offer(() -> {
                try {
                    command.run();
                } finally {
                    scheduleNext();
                }
            });
            if (active == null) scheduleNext();
        }

        private synchronized void scheduleNext() {
            active = tasks.poll();
            if (active != null) executor.execute(active);
        }
    }
}
