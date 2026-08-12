package com.dvid.dcam.platform.camera.shared.runtime;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AndroidCameraAvailabilityMonitor implements CameraAvailabilityMonitor {
    private final CameraManager cameraManager;
    private final Logger logger;
    private final HandlerThread callbackThread;
    private final Handler callbackHandler;
    private final AtomicBoolean closed = new AtomicBoolean();

    public AndroidCameraAvailabilityMonitor(Context context, Logger logger) {
        Context applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        Context owner = applicationContext == null ? context : applicationContext;
        cameraManager = Objects.requireNonNull(
                owner.getSystemService(CameraManager.class), "CameraManager");
        this.logger = Objects.requireNonNull(logger, "logger");
        callbackThread = new HandlerThread("dcam-camera-availability");
        callbackThread.start();
        callbackHandler = new Handler(callbackThread.getLooper());
    }

    @Override public Attachment watch(CameraId cameraId, Listener listener) {
        CameraId watched = Objects.requireNonNull(cameraId, "cameraId");
        Listener callback = Objects.requireNonNull(listener, "listener");
        if (closed.get()) throw new IllegalStateException("availability monitor closed");
        CameraManager.AvailabilityCallback availabilityCallback =
                new CameraManager.AvailabilityCallback() {
                    @Override public void onCameraAvailable(String value) {
                        if (watched.value().equals(value)) callback.onAvailable(watched);
                    }

                    @Override public void onCameraUnavailable(String value) {
                        if (watched.value().equals(value)) callback.onUnavailable(watched);
                    }
                };
        cameraManager.registerAvailabilityCallback(availabilityCallback, callbackHandler);
        AtomicBoolean detached = new AtomicBoolean();
        return () -> {
            if (!detached.compareAndSet(false, true)) return;
            cameraManager.unregisterAvailabilityCallback(availabilityCallback);
        };
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        callbackThread.quitSafely();
    }
}