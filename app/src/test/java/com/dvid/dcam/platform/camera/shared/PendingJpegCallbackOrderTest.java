package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import java.io.File;
import org.junit.jupiter.api.Test;

final class PendingJpegCallbackOrderTest {
    @Test void imageBeforeShutterIsRecoveredWhenTimestampMatches() {
        AbstractSharedCameraPipeline.PendingJpeg request = request();
        AbstractSharedCameraPipeline.JpegPayload image = payload(42L);

        assertNull(request.accept(image));
        assertSame(image, request.markStarted(42L));
    }

    @Test void shutterBeforeImageKeepsNormalFlow() {
        AbstractSharedCameraPipeline.PendingJpeg request = request();
        AbstractSharedCameraPipeline.JpegPayload image = payload(42L);

        assertNull(request.markStarted(42L));
        assertSame(image, request.accept(image));
    }

    @Test void staleEarlyImageDoesNotHideMatchingImage() {
        AbstractSharedCameraPipeline.PendingJpeg request = request();
        AbstractSharedCameraPipeline.JpegPayload matching = payload(42L);

        assertNull(request.accept(payload(41L)));
        assertNull(request.accept(matching));
        assertSame(matching, request.markStarted(42L));
    }

    @Test void staleImageAfterShutterIsRejected() {
        AbstractSharedCameraPipeline.PendingJpeg request = request();
        AbstractSharedCameraPipeline.JpegPayload matching = payload(42L);

        assertNull(request.markStarted(42L));
        assertNull(request.accept(payload(41L)));
        assertSame(matching, request.accept(matching));
    }

    private static AbstractSharedCameraPipeline.PendingJpeg request() {
        return new AbstractSharedCameraPipeline.PendingJpeg(
                null, new File("unused"), null, true, 0, 1L);
    }

    private static AbstractSharedCameraPipeline.JpegPayload payload(long timestamp) {
        return new AbstractSharedCameraPipeline.JpegPayload(
                timestamp, new byte[] {1}, new CameraResolution(1, 1));
    }
}

