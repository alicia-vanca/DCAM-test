package com.dvid.dcam.feature.device.domain.camera;

import java.util.List;
import java.util.Objects;

public enum StandardResolutionLabel {
    SD(List.of(new CameraResolution(720, 480), new CameraResolution(640, 480))),
    HD(List.of(new CameraResolution(1280, 720))),
    FHD(List.of(new CameraResolution(1920, 1080))),
    QHD(List.of(new CameraResolution(2560, 1440))),
    UHD(List.of(new CameraResolution(3840, 2160))),
    MAX(List.of());

    static final int AXIS_TOLERANCE = 16;

    private final List<CameraResolution> targets;

    StandardResolutionLabel(List<CameraResolution> targets) {
        this.targets = targets;
    }

    public boolean accepts(CameraResolution actual) {
        Objects.requireNonNull(actual, "actual");
        if (this == MAX) return true;
        for (CameraResolution target : targets) {
            if (Math.abs(actual.width() - target.width()) <= AXIS_TOLERANCE
                    && Math.abs(actual.height() - target.height()) <= AXIS_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    List<CameraResolution> targets() { return targets; }
}