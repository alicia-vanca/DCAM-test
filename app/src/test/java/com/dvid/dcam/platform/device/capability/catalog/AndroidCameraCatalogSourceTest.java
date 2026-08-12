package com.dvid.dcam.platform.device.capability.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderSizeCapabilities;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.StreamSize;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AndroidCameraCatalogSourceTest {
    private static final CameraResolution HD = new CameraResolution(1280, 720);

    @Test void exactSizeAndRateSupportRetainsOnlyPassingFrameRates() {
        EncoderSizeCapabilities result = AndroidCameraCatalogSource.readEncoderSizeCapabilities(
                HD, List.of(24, 30, 60),
                (width, height, frameRate) -> frameRate == 30 || frameRate == 60);

        assertEquals(List.of(30, 60), result.supportedFrameRates());
    }

    @Test void exactSupportQueryFailureExcludesOnlyFailedRate() {
        EncoderSizeCapabilities result = AndroidCameraCatalogSource.readEncoderSizeCapabilities(
                HD, List.of(30, 60), (width, height, frameRate) -> {
                    if (frameRate == 30) throw new IllegalArgumentException("unsupported");
                    return true;
                });

        assertEquals(List.of(60), result.supportedFrameRates());
    }

    @Test void regularAndHighResolutionJpegOutputsMergeAndDeduplicate() {
        StreamSize regular = new StreamSize(new CameraResolution(1920, 1080), 40L);
        StreamSize duplicateHigh = new StreamSize(new CameraResolution(1920, 1080), 30L);
        StreamSize high = new StreamSize(new CameraResolution(4000, 3000), 100L);

        assertEquals(List.of(
                new StreamSize(new CameraResolution(1920, 1080), 30L), high),
                AndroidCameraCatalogSource.mergeJpegOutputs(
                        List.of(regular), List.of(duplicateHigh, high)));
    }
}
