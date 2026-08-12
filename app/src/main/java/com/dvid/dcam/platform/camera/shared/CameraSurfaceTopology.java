package com.dvid.dcam.platform.camera.shared;

public record CameraSurfaceTopology(
        int cameraOutputCount,
        int privateSurfaceCount,
        int jpegSurfaceCount) {
    public CameraSurfaceTopology {
        if (cameraOutputCount < 0 || privateSurfaceCount < 0 || jpegSurfaceCount < 0) {
            throw new IllegalArgumentException("surface counts must not be negative");
        }
    }

    public boolean isExactPrivateSourcePlusJpeg() {
        return cameraOutputCount == 2
                && privateSurfaceCount == 2
                && jpegSurfaceCount == 1;
    }

    public boolean isExactStandaloneImage() {
        return cameraOutputCount == 2
                && privateSurfaceCount == 1
                && jpegSurfaceCount == 1;
    }
}
