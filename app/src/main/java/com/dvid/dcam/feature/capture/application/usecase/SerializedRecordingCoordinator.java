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
        this.listener = listener == null ? NO_LISTENER : listener;
    }

    @Override public void clearListener() {
        listener = NO_LISTENER;
    }

    @Override public RecordingMode currentMode() {
        return currentMode == RecordingMode.IDLE ? requestedMode : currentMode;
    }

    @Override public void recordingStarted(RecordingMode mode, String fileName) {
        queue.execute(() -> {
            currentMode = Objects.requireNonNull(mode, "mode");
            requestedMode = RecordingMode.IDLE;
            phase = Phase.RECORDING;
            listener.accept(CaptureEvent.recordingStarted(mode, fileName));
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
            listener.accept(CaptureEvent.recordingCompleted(fileName));
        });
    }
    @Override public void recordingStoppedForStorage(String fileName) {
        queue.execute(() -> {
            currentMode = RecordingMode.IDLE;
            requestedMode = RecordingMode.IDLE;
            phase = Phase.IDLE;
            listener.accept(CaptureEvent.recordingStoppedForStorage(fileName));
        });
    }

    @Override public void photoSaved(String fileName) {
        queue.execute(() -> listener.accept(CaptureEvent.photoSaved(fileName)));
    }

    @Override public void captureFailed(String operation, String message) {
        queue.execute(() -> {
            currentMode = RecordingMode.IDLE;
            requestedMode = RecordingMode.IDLE;
            phase = Phase.IDLE;
            listener.accept(CaptureEvent.error(operation, message));
        });
    }

    private void start(RecordingMode mode) {
        requestedMode = mode;
        phase = Phase.STARTING;
        listener.accept(CaptureEvent.recordingStarting(mode));
        if (mode == RecordingMode.SOS) camera().startSos();
        else camera().startVideo();
    }

    private void stop() {
        if (phase == Phase.IDLE || phase == Phase.STOPPING) return;
        RecordingMode stoppingMode = currentMode == RecordingMode.IDLE
                ? requestedMode : currentMode;
        requestedMode = RecordingMode.IDLE;
        phase = Phase.STOPPING;
        listener.accept(CaptureEvent.recordingStopping(stoppingMode));
        camera().stopRecording();
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
