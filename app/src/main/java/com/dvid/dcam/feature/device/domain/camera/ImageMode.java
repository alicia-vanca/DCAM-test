package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;

public record ImageMode(StandardResolution resolution) implements Comparable<ImageMode> {
    public ImageMode {
        Objects.requireNonNull(resolution, "resolution");
    }

    @Override public int compareTo(ImageMode other) {
        return resolution.compareTo(other.resolution);
    }

    @Override public String toString() { return resolution.toString(); }
}