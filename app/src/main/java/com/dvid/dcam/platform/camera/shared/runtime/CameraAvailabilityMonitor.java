package com.dvid.dcam.platform.camera.shared.runtime;

import com.dvid.dcam.feature.device.domain.camera.CameraId;
import java.util.Objects;

public interface CameraAvailabilityMonitor extends AutoCloseable {
    interface Listener {
        void onAvailable(CameraId cameraId);
        void onUnavailable(CameraId cameraId);
    }

    interface Attachment extends AutoCloseable {
        @Override
        void close();
    }

    Attachment watch(CameraId cameraId, Listener listener);

    @Override
    void close();

    static CameraAvailabilityMonitor none() {
        return new CameraAvailabilityMonitor() {
            @Override public Attachment watch(CameraId cameraId, Listener listener) {
                Objects.requireNonNull(cameraId, "cameraId");
                Objects.requireNonNull(listener, "listener");
                return () -> {};
            }

            @Override public void close() {}
        };
    }
}