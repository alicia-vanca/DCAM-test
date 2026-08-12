package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;

public record StandardResolution(StandardResolutionLabel label, CameraResolution actual)
        implements Comparable<StandardResolution> {
    public StandardResolution {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(actual, "actual");
        if (!label.accepts(actual)) {
            throw new IllegalArgumentException(actual + " is not eligible for " + label);
        }
    }

    @Override public int compareTo(StandardResolution other) {
        int labelOrder = label.compareTo(other.label);
        if (labelOrder != 0) return labelOrder;
        return actual.compareTo(other.actual);
    }

    @Override public String toString() { return label + ":" + actual; }
}