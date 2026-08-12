package com.dvid.dcam.app.ui.settings.camera;

import java.util.Objects;

/** Pure presentation resolution supplied by the production capability facade adapter. */
public record CameraResolutionOption(String id, int width, int height) {
    public CameraResolutionOption {
        id = required(id, "id");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("resolution must be positive");
        }
    }

    public String label() {
        return id + " (" + width + "×" + height + ")";
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}