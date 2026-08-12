package com.dvid.dcam.feature.device.domain;

import java.util.Objects;

public final class CaptureQuality {
    private final String id;
    private final int width;
    private final int height;
    private final int frameRate;

    public CaptureQuality(String id, int width, int height) {
        this(id, width, height, 0);
    }

    public CaptureQuality(String id, int width, int height, int frameRate) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("resolution must be positive");
        if (frameRate < 0) throw new IllegalArgumentException("frameRate must not be negative");
        this.id = id;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
    }

    public String getId() { return id; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getFrameRate() { return frameRate; }
    public CaptureQuality withFrameRate(int value) {
        return new CaptureQuality(id, width, height, value);
    }
    public String label() { return id + " (" + width + "×" + height + ")"; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaptureQuality)) return false;
        CaptureQuality quality = (CaptureQuality) other;
        return width == quality.width && height == quality.height
                && frameRate == quality.frameRate && id.equals(quality.id);
    }

    @Override public int hashCode() {
        return Objects.hash(id, width, height, frameRate);
    }

    @Override public String toString() { return label(); }
}