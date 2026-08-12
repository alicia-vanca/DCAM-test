package com.dvid.dcam.feature.device.domain.camera;

import java.util.Comparator;

public final class CameraModeOrder {
    private static final Comparator<StandardResolution> RESOLUTION_ORDER =
            CameraModeOrder::compareResolution;
    private static final Comparator<CaptureModeTuple> TUPLE_ORDER =
            CameraModeOrder::compareTuple;

    private CameraModeOrder() {}

    public static Comparator<StandardResolution> resolutions() {
        return RESOLUTION_ORDER;
    }

    public static Comparator<CaptureModeTuple> tuples() { return TUPLE_ORDER; }

    private static int compareResolution(StandardResolution left,
            StandardResolution right) {
        boolean leftMaximum = left.label() == StandardResolutionLabel.MAX;
        boolean rightMaximum = right.label() == StandardResolutionLabel.MAX;
        if (leftMaximum != rightMaximum) return leftMaximum ? 1 : -1;

        int labelOrder = left.label().compareTo(right.label());
        if (labelOrder != 0) return labelOrder;
        CameraResolution leftActual = left.actual();
        CameraResolution rightActual = right.actual();
        if (!leftMaximum) {
            return -StandardResolutionMapper.compareMatch(left, right);
        }
        int areaOrder = Long.compare(leftActual.pixelCount(), rightActual.pixelCount());
        if (areaOrder != 0) return areaOrder;
        int longEdgeOrder = Integer.compare(longEdge(leftActual), longEdge(rightActual));
        if (longEdgeOrder != 0) return longEdgeOrder;
        int shortEdgeOrder = Integer.compare(shortEdge(leftActual), shortEdge(rightActual));
        if (shortEdgeOrder != 0) return shortEdgeOrder;
        int widthOrder = Integer.compare(leftActual.width(), rightActual.width());
        if (widthOrder != 0) return widthOrder;
        return Integer.compare(leftActual.height(), rightActual.height());
    }

    private static int compareTuple(CaptureModeTuple left, CaptureModeTuple right) {
        int videoOrder = RESOLUTION_ORDER.compare(
                left.videoMode().resolution(), right.videoMode().resolution());
        if (videoOrder != 0) return videoOrder;
        int frameRateOrder = Integer.compare(
                left.videoMode().framesPerSecond(),
                right.videoMode().framesPerSecond());
        if (frameRateOrder != 0) return frameRateOrder;
        return RESOLUTION_ORDER.compare(
                left.imageMode().resolution(), right.imageMode().resolution());
    }

    private static int longEdge(CameraResolution resolution) {
        return Math.max(resolution.width(), resolution.height());
    }

    private static int shortEdge(CameraResolution resolution) {
        return Math.min(resolution.width(), resolution.height());
    }
}