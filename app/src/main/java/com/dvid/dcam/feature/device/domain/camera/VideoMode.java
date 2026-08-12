package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;

public record VideoMode(StandardResolution resolution, int framesPerSecond)
        implements Comparable<VideoMode> {
    public VideoMode {
        Objects.requireNonNull(resolution, "resolution");
        if (framesPerSecond <= 0) {
            throw new IllegalArgumentException("frames per second must be positive");
        }
    }

    @Override public int compareTo(VideoMode other) {
        int resolutionOrder = resolution.compareTo(other.resolution);
        if (resolutionOrder != 0) return resolutionOrder;
        return Integer.compare(framesPerSecond, other.framesPerSecond);
    }

    @Override public String toString() {
        return resolution + "@" + framesPerSecond;
    }
}