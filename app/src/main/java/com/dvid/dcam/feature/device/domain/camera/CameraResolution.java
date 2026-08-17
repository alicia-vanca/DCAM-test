package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;

public record CameraResolution(int width, int height)
        implements Comparable<CameraResolution> {
    public CameraResolution {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("resolution must be positive");
        }
    }

    public long pixelCount() { return (long) width * height; }

    public boolean matchesConsideringRotation(
            CameraResolution other, int orientationDegrees) {
        Objects.requireNonNull(other, "other");
        int normalizedOrientation = Math.floorMod(orientationDegrees, 360);
        if (normalizedOrientation % 90 != 0) {
            throw new IllegalArgumentException(
                    "orientationDegrees must be a multiple of 90");
        }
        return equals(other)
                || normalizedOrientation % 180 != 0
                && width == other.height && height == other.width;
    }

    @Override public int compareTo(CameraResolution other) {
        int areaOrder = Long.compare(pixelCount(), other.pixelCount());
        if (areaOrder != 0) return areaOrder;
        int widthOrder = Integer.compare(width, other.width);
        if (widthOrder != 0) return widthOrder;
        return Integer.compare(height, other.height);
    }

    @Override public String toString() { return width + "x" + height; }
}