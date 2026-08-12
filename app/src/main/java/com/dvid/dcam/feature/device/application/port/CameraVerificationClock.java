package com.dvid.dcam.feature.device.application.port;

import java.util.concurrent.TimeUnit;

@FunctionalInterface
public interface CameraVerificationClock {
    long elapsedRealtimeMillis();

    static CameraVerificationClock system() {
        return () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
}