package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.application.port.CameraGateway;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Single application authority for video/SOS recording commands and camera events.
 *
 * <p>All mutable recording state is owned by one FIFO queue. Camera/file/DB work remains behind
 * its platform boundaries and must report completion back through this coordinator.</p>
 */
public final class SerializedRecordingCoordinator
        implements VideoRecordingUseCase, CaptureEventUseCase {
    private static final Consumer<CaptureEvent> NO_LISTENER = event -> {};

    private enum Phase {
        IDLE,
        STARTING,
        RECORDING,
        INTERRUPTED,
        STOPPING,
        SWITCHING_TO_SOS
    }

    private final SerialExecutor queue;
    private volatile Consumer<CaptureEvent> listener = NO_LISTENER;
    private volatile RecordingMode currentMode = RecordingMode.IDLE;

    // Accessed only by queue tasks after construction-time binding.
    private CameraGateway camera;
    private Phase phase = Phase.IDLE;
    private volatile RecordingMode requestedMode = RecordingMode.IDLE;
    private String currentFileName;
    private String interruptionMessage;
    private CaptureEvent lastTerminalEvent;

    public SerializedRecordingCoordinator(Executor executor) {
        queue = new SerialExecutor(Objects.requireNonNull(executor, "executor"));
    }

    /** Binds the one platform camera target used by this coordinator. */
    public synchronized void bindCamera(CameraGateway camera) {
        if (this.camera != null) throw new IllegalStateException("Camera already bound");
        this.camera = Objects.requireNonNull(camera, "camera");
    }

    @Override public void toggleVideo() {
        queue.execute(() -> {
            if (phase == Phase.IDLE) start(RecordingMode.VIDEO);
            else stop();
        });
    }

    @Override public void startVideo() {
        queue.execute(() -> {
            if (phase == Phase.IDLE) start(RecordingMode.VIDEO);
        });
    }

    @Override public void startSos() {
        queue.execute(() -> {
            if (phase == Phase.IDLE) {
                start(RecordingMode.SOS);
            } else if (phase == Phase.RECORDING && currentMode != RecordingMode.SOS) {
                requestedMode = RecordingMode.SOS;
                phase = Phase.SWITCHING_TO_SOS;
                camera().startSos();
            }
        });
    }

    @Override public void stopRecording() {
        queue.execute(this::stop);
    }

    @Override public void toggleSos() {
        queue.execute(() -> {
            if (phase == Phase.IDLE) start(RecordingMode.SOS);
            else if (phase == Phase.RECORDING && currentMode != RecordingMode.SOS) {
                requestedMode = RecordingMode.SOS;
                phase = Phase.SWITCHING_TO_SOS;
                camera().startSos();
            } else stop();
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
        return currentMode == RecordingMode.IDLE ? requestedMode : currentMode;
    }

    @Override public void recordingStarted(RecordingMode mode, String fileName) {
        queue.execute(() -> {
            if (phase != Phase.STARTING && phase != Phase.SWITCHING_TO_SOS) return;
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
        queue.execute(() -> {
            currentMode = RecordingMode.IDLE;
            if (phase == Phase.SWITCHING_TO_SOS && requestedMode == RecordingMode.SOS) {
                phase = Phase.STARTING;
            } else {
                requestedMode = RecordingMode.IDLE;
                phase = Phase.IDLE;
            }
            currentFileName = null;
            interruptionMessage = null;
            lastTerminalEvent = CaptureEvent.recordingCompleted(fileName);
            emit(lastTerminalEvent);
        });
    }
    @Override public void recordingStoppedForStorage(String fileName) {
        queue.execute(() -> {
            currentMode = RecordingMode.IDLE;
            requestedMode = RecordingMode.IDLE;
            phase = Phase.IDLE;
            currentFileName = null;
            interruptionMessage = null;
            lastTerminalEvent = CaptureEvent.recordingStoppedForStorage(fileName);
            emit(lastTerminalEvent);
        });
    }

    @Override public void photoSaved(String fileName) {
        queue.execute(() -> {
            lastTerminalEvent = CaptureEvent.photoSaved(fileName);
            emit(lastTerminalEvent);
        });
    }

    @Override public void captureFailed(String operation, String message) {
        queue.execute(() -> {
            currentMode = RecordingMode.IDLE;
            requestedMode = RecordingMode.IDLE;
            phase = Phase.IDLE;
            currentFileName = null;
            interruptionMessage = null;
            lastTerminalEvent = CaptureEvent.error(operation, message);
            emit(lastTerminalEvent);
        });
    }

    private void start(RecordingMode mode) {
        requestedMode = mode;
        currentFileName = null;
        interruptionMessage = null;
        lastTerminalEvent = null;
        phase = Phase.STARTING;
        emit(CaptureEvent.recordingStarting(mode));
        if (mode == RecordingMode.SOS) camera().startSos();
        else camera().startVideo();
    }

    private void stop() {
        if (phase == Phase.IDLE || phase == Phase.STOPPING) return;
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
                emit(CaptureEvent.recordingStarting(requestedMode));
                break;
            case RECORDING:
                emit(CaptureEvent.recordingStarted(currentMode, currentFileName));
                break;
            case INTERRUPTED:
                emit(CaptureEvent.recordingStarted(currentMode, currentFileName));
                emit(CaptureEvent.recordingInterrupted(currentMode, interruptionMessage));
                break;
            case STOPPING:
                RecordingMode stoppingMode = currentMode == RecordingMode.IDLE
                        ? requestedMode : currentMode;
                if (currentFileName != null) {
                    emit(CaptureEvent.recordingStarted(stoppingMode, currentFileName));
                } else {
                    emit(CaptureEvent.recordingStarting(stoppingMode));
                }
                emit(CaptureEvent.recordingStopping(stoppingMode));
                break;
            case SWITCHING_TO_SOS:
                emit(CaptureEvent.recordingStarted(currentMode, currentFileName));
                break;
            case IDLE:
                if (lastTerminalEvent != null
                        && lastTerminalEvent.getType() != CaptureEvent.Type.ERROR) {
                    emit(lastTerminalEvent);
                }
                break;
            default:
                throw new IllegalStateException("Unsupported recording phase " + phase);
        }
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
