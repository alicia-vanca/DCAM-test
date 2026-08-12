package com.dvid.dcam.feature.device.application.port;

import java.util.Objects;

public interface DeveloperSettingsStore {
    Mode mode();

    default Mode mode(String cameraId) {
        if (cameraId == null || cameraId.isBlank()) {
            throw new IllegalArgumentException("cameraId is required");
        }
        return mode();
    }

    void setMode(Mode mode);

    default void setMode(String cameraId, Mode mode) {
        if (cameraId == null || cameraId.isBlank()) {
            throw new IllegalArgumentException("cameraId is required");
        }
        setMode(Objects.requireNonNull(mode, "mode"));
    }

    boolean releaseCameraWhenScreenOff();

    void setReleaseCameraWhenScreenOff(boolean enabled);

    enum Mode { AUTO, A, B }

}