package com.dvid.dcam.feature.device.application.port;

import com.dvid.dcam.feature.device.domain.camera.CameraId;

@FunctionalInterface
public interface CameraHealthGenerationProvider {
    long currentGeneration(CameraId cameraId);
}