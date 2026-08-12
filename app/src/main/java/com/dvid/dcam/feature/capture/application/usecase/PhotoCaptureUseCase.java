package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.application.port.CameraGateway;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class PhotoCaptureUseCase {
    private final CameraGateway camera;
    private final BooleanSupplier captureAllowed;
    private final Predicate<Runnable> deferredCapture;

    public PhotoCaptureUseCase(CameraGateway camera) {
        this(camera, () -> true);
    }

    public PhotoCaptureUseCase(CameraGateway camera, BooleanSupplier captureAllowed) {
        this(camera, captureAllowed, action -> false);
    }

    public PhotoCaptureUseCase(CameraGateway camera, BooleanSupplier captureAllowed,
            Predicate<Runnable> deferredCapture) {
        this.camera = Objects.requireNonNull(camera, "camera");
        this.captureAllowed = Objects.requireNonNull(captureAllowed, "captureAllowed");
        this.deferredCapture = Objects.requireNonNull(deferredCapture, "deferredCapture");
    }

    public void takePhoto() {
        if (captureAllowed.getAsBoolean()) {
            camera.takePhoto();
            return;
        }
        deferredCapture.test(() -> {
            if (captureAllowed.getAsBoolean()) camera.takePhoto();
        });
    }
}
