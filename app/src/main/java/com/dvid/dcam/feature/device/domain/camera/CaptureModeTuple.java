package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;

public record CaptureModeTuple(VideoMode videoMode, ImageMode imageMode)
        implements Comparable<CaptureModeTuple> {
    public CaptureModeTuple {
        Objects.requireNonNull(videoMode, "videoMode");
        Objects.requireNonNull(imageMode, "imageMode");
    }

    @Override public int compareTo(CaptureModeTuple other) {
        int videoOrder = videoMode.compareTo(other.videoMode);
        if (videoOrder != 0) return videoOrder;
        return imageMode.compareTo(other.imageMode);
    }

    @Override public String toString() { return videoMode + "+" + imageMode; }
}