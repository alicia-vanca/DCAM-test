package com.dvid.dcam.feature.device.domain.camera;

public record CameraId(String value) implements Comparable<CameraId> {
    public CameraId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("camera ID is required");
        }
    }

    @Override public int compareTo(CameraId other) {
        return value.compareTo(other.value);
    }

    @Override public String toString() { return value; }
}