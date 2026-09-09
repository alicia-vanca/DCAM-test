package com.dvid.dcam.feature.capture.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.capture.application.port.CameraGateway;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class PhotoCaptureUseCaseTest {
    @Test void capabilityRecheckBlocksPhotoWithoutDeferringIt() {
        FakeCamera camera = new FakeCamera();
        AtomicBoolean allowed = new AtomicBoolean(false);
        PhotoCaptureUseCase photos = new PhotoCaptureUseCase(camera, allowed::get);

        photos.takePhoto();
        allowed.set(true);

        assertEquals(0, camera.photos);
        photos.takePhoto();
        assertEquals(1, camera.photos);
    }

    @Test void idleReleasedCameraDefersPhotoUntilReadyCallback() {
        FakeCamera camera = new FakeCamera();
        AtomicBoolean allowed = new AtomicBoolean(false);
        Runnable[] deferred = new Runnable[1];
        PhotoCaptureUseCase photos = new PhotoCaptureUseCase(camera, allowed::get, action -> {
            deferred[0] = action;
            return true;
        });

        photos.takePhoto();

        assertEquals(0, camera.photos);
        allowed.set(true);
        deferred[0].run();
        assertEquals(1, camera.photos);
    }

    @Test void deferredPhotoRechecksEligibilityBeforeCapture() {
        FakeCamera camera = new FakeCamera();
        Runnable[] deferred = new Runnable[1];
        PhotoCaptureUseCase photos = new PhotoCaptureUseCase(camera, () -> false, action -> {
            deferred[0] = action;
            return true;
        });

        photos.takePhoto();
        deferred[0].run();

        assertEquals(0, camera.photos);
    }

    private static final class FakeCamera implements CameraGateway {
        private int photos;

        @Override public void takePhoto() { photos++; }
        @Override public void startVideo() {}
        @Override public void startImp() {}
        @Override public void stopRecording() {}
    }
}
