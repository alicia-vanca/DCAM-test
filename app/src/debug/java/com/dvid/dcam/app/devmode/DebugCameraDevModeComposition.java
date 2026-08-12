package com.dvid.dcam.app.devmode;

import android.content.Context;
import com.dvid.dcam.app.AppComposition;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.usecase.DebugCameraBenchmarkUseCase;
import com.dvid.dcam.platform.camera.shared.benchmark.DebugCameraPipelineBenchmarkEngine;
import com.dvid.dcam.platform.device.capability.CameraCapabilityService;
import com.dvid.dcam.platform.logging.app.AppLogger;
import com.dvid.dcam.platform.recording.DebugRecordingIdleProbe;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DebugCameraDevModeComposition {
    private static volatile DebugCameraDevModeComposition instance;
    private final DebugCameraBenchmarkUseCase benchmark;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dcam-debug-devmode");
        thread.setDaemon(true);
        return thread;
    });

    private DebugCameraDevModeComposition(DebugCameraBenchmarkUseCase benchmark) {
        this.benchmark = benchmark;
    }

    public static synchronized DebugCameraDevModeComposition create(Context context) {
        if (instance != null) return instance;
        Context applicationContext = Objects.requireNonNull(context, "context").getApplicationContext();
        Context checkedContext = applicationContext == null ? context : applicationContext;
        Logger logger = AppLogger.get();
        CameraCapabilityService capabilities =
                AppComposition.cameraCapabilities(checkedContext, logger);
        DebugCameraPipelineBenchmarkEngine engine = new DebugCameraPipelineBenchmarkEngine(
                checkedContext, logger, capabilities);
        DebugRecordingIdleProbe recordingIdle = new DebugRecordingIdleProbe(checkedContext);
        instance = new DebugCameraDevModeComposition(
                new DebugCameraBenchmarkUseCase(engine,
                        () -> engine.isCameraIdle() && recordingIdle.isIdle(),
                        capabilities.runtimeOwner()::beginCapabilityScan,
                        capabilities.runtimeOwner()::endCapabilityScan));
        return instance;
    }

    public DebugCameraBenchmarkUseCase benchmark() {
        return benchmark;
    }

    public ExecutorService executor() {
        return executor;
    }


}
