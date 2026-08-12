package com.dvid.dcam.app.ui.settings.camera;

import java.util.Objects;

/** Complete camera tuple shown as committed or pending target selection. */
public record CameraSelection(String videoResolutionId, int frameRate, String imageResolutionId) {
    public CameraSelection {
        videoResolutionId = required(videoResolutionId, "videoResolutionId");
        imageResolutionId = required(imageResolutionId, "imageResolutionId");
        if (frameRate <= 0) throw new IllegalArgumentException("frameRate must be positive");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}