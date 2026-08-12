package com.dvid.dcam.app.ui.settings.camera;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** One video resolution and the exact FPS options valid for that resolution. */
public record CameraVideoOption(CameraResolutionOption resolution, List<Integer> frameRates) {
    public CameraVideoOption {
        resolution = Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(frameRates, "frameRates");
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer frameRate : frameRates) {
            if (frameRate == null || frameRate <= 0) {
                throw new IllegalArgumentException("frame rate must be positive");
            }
            normalized.add(frameRate);
        }
        frameRates = List.copyOf(normalized);
    }

    public String id() { return resolution.id(); }
    public String label() { return resolution.label(); }
}