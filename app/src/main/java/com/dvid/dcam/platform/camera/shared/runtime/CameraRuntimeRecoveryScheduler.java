package com.dvid.dcam.platform.camera.shared.runtime;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public interface CameraRuntimeRecoveryScheduler extends AutoCloseable {
    interface Task {
        void cancel();
    }

    Task schedule(Runnable action, long delayMillis);

    @Override
    void close();

    static CameraRuntimeRecoveryScheduler none() {
        return new CameraRuntimeRecoveryScheduler() {
            @Override public Task schedule(Runnable action, long delayMillis) {
                return () -> {};
            }

            @Override public void close() {}
        };
    }
}

final class ScheduledCameraRuntimeRecoveryScheduler implements CameraRuntimeRecoveryScheduler {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "dcam-camera-recovery-watchdog");
                thread.setDaemon(true);
                return thread;
            });

    @Override public Task schedule(Runnable action, long delayMillis) {
        Objects.requireNonNull(action, "action");
        if (delayMillis < 0L) throw new IllegalArgumentException("delayMillis must not be negative");
        var future = executor.schedule(action, delayMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @Override public void close() {
        executor.shutdownNow();
    }
}
