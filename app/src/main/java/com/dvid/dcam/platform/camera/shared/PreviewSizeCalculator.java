package com.dvid.dcam.platform.camera.shared;

import com.dvid.dcam.feature.device.domain.camera.CameraResolution;

final class PreviewSizeCalculator {
    private PreviewSizeCalculator() {}

    static Size fit(int containerWidth, int containerHeight, CameraResolution source) {
        return fit(containerWidth, containerHeight, source, 0);
    }

    static Size fit(int containerWidth, int containerHeight, CameraResolution source,
            int rotationDegrees) {
        if (containerWidth <= 0 || containerHeight <= 0) {
            throw new IllegalArgumentException("container dimensions must be positive");
        }
        CameraResolution resolution = source;
        int rotation = CameraOrientation.normalize(rotationDegrees);
        int displayWidth = rotation % 180 == 0 ? resolution.width() : resolution.height();
        int displayHeight = rotation % 180 == 0 ? resolution.height() : resolution.width();
        int fittedWidth;
        int fittedHeight;
        if ((long) containerWidth * displayHeight <= (long) containerHeight * displayWidth) {
            fittedWidth = containerWidth;
            fittedHeight = Math.max(1, (int) Math.round(
                    (double) containerWidth * displayHeight / displayWidth));
        } else {
            fittedHeight = containerHeight;
            fittedWidth = Math.max(1, (int) Math.round(
                    (double) containerHeight * displayWidth / displayHeight));
        }
        return rotation % 180 == 0
                ? new Size(fittedWidth, fittedHeight)
                : new Size(fittedHeight, fittedWidth);
    }

    static Size centerCrop(int viewportWidth, int viewportHeight,
            CameraResolution source, int rotationDegrees) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("viewport dimensions must be positive");
        }
        int rotation = CameraOrientation.normalize(rotationDegrees);
        int displayWidth = rotation % 180 == 0 ? source.width() : source.height();
        int displayHeight = rotation % 180 == 0 ? source.height() : source.width();
        double scale = Math.max((double) viewportWidth / displayWidth,
                (double) viewportHeight / displayHeight);
        int croppedWidth = Math.max(viewportWidth,
                (int) Math.round(displayWidth * scale));
        int croppedHeight = Math.max(viewportHeight,
                (int) Math.round(displayHeight * scale));
        return rotation % 180 == 0
                ? new Size(croppedWidth, croppedHeight)
                : new Size(croppedHeight, croppedWidth);
    }

    static Size fitWidth(int containerWidth, CameraResolution source, int rotationDegrees) {
        if (containerWidth <= 0) {
            throw new IllegalArgumentException("container width must be positive");
        }
        int rotation = CameraOrientation.normalize(rotationDegrees);
        int displayWidth = rotation % 180 == 0 ? source.width() : source.height();
        int displayHeight = rotation % 180 == 0 ? source.height() : source.width();
        int fittedHeight = Math.max(1, (int) Math.round(
                (double) containerWidth * displayHeight / displayWidth));
        return rotation % 180 == 0
                ? new Size(containerWidth, fittedHeight)
                : new Size(fittedHeight, containerWidth);
    }

    static Layout containLayout(
            int containerWidth,
            int containerHeight,
            CameraResolution displayResolution,
            CameraResolution bufferResolution,
            int displayRotationDegrees,
            int outputRotationDegrees) {
        int outputRotation = CameraOrientation.normalize(outputRotationDegrees);
        int previewRotation = CameraOrientation.previewBufferRotation(displayRotationDegrees);
        Size fittedViewport = fit(
                containerWidth, containerHeight, displayResolution, outputRotation);
        int viewportWidth = visualWidth(fittedViewport, outputRotation);
        int viewportHeight = visualHeight(fittedViewport, outputRotation);
        Size fittedBuffer = fit(
                viewportWidth, viewportHeight, bufferResolution, outputRotation);
        int visualTextureWidth = visualWidth(fittedBuffer, outputRotation);
        int visualTextureHeight = visualHeight(fittedBuffer, outputRotation);
        int textureLayoutWidth = swapsAxes(previewRotation)
                ? visualTextureHeight : visualTextureWidth;
        int textureLayoutHeight = swapsAxes(previewRotation)
                ? visualTextureWidth : visualTextureHeight;
        return new Layout(viewportWidth, viewportHeight,
                textureLayoutWidth, textureLayoutHeight,
                visualTextureWidth, visualTextureHeight, previewRotation);
    }

    private static int visualWidth(Size size, int rotationDegrees) {
        return swapsAxes(rotationDegrees) ? size.height() : size.width();
    }

    private static int visualHeight(Size size, int rotationDegrees) {
        return swapsAxes(rotationDegrees) ? size.width() : size.height();
    }

    private static boolean swapsAxes(int rotationDegrees) {
        return CameraOrientation.normalize(rotationDegrees) % 180 != 0;
    }

    record Layout(
            int viewportWidth,
            int viewportHeight,
            int textureLayoutWidth,
            int textureLayoutHeight,
            int visualTextureWidth,
            int visualTextureHeight,
            int previewRotationDegrees) {}

    record Size(int width, int height) {}
}