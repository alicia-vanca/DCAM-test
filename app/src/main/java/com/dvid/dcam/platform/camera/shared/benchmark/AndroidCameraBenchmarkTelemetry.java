package com.dvid.dcam.platform.camera.shared.benchmark;

import android.os.Debug;
import android.os.PowerManager;
import android.content.Context;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.ResourceSample;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Objects;

public final class AndroidCameraBenchmarkTelemetry implements CameraBenchmarkTelemetry {
    private final Context context;

    public AndroidCameraBenchmarkTelemetry(Context context) {
        Context checked = Objects.requireNonNull(context, "context");
        Context application = checked.getApplicationContext();
        this.context = application == null ? checked : application;
    }

    @Override public Token begin() {
        return new Sample(Debug.threadCpuTimeNanos());
    }

    @Override public ResourceSample end(Token token) {
        Sample start = (Sample) Objects.requireNonNull(token, "token");
        long elapsedCpu = Math.max(0, Debug.threadCpuTimeNanos() - start.cpuNanos);
        long elapsedWall = Math.max(1, System.nanoTime() - start.wallNanos);
        return new ResourceSample(
                OptionalDouble.of(Math.min(1.0, elapsedCpu / (double) elapsedWall)),
                OptionalDouble.empty(), OptionalLong.of(memoryBytes()), thermalStatus());
    }

    private long memoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0, runtime.totalMemory() - runtime.freeMemory());
    }

    private OptionalLong thermalStatus() {
        if (android.os.Build.VERSION.SDK_INT < 29) return OptionalLong.empty();
        PowerManager power = context.getSystemService(PowerManager.class);
        return power == null ? OptionalLong.empty()
                : OptionalLong.of(power.getCurrentThermalStatus());
    }

    private static final class Sample implements Token {
        private final long cpuNanos;
        private final long wallNanos;

        private Sample(long cpuNanos) {
            this.cpuNanos = cpuNanos;
            this.wallNanos = System.nanoTime();
        }
    }
}