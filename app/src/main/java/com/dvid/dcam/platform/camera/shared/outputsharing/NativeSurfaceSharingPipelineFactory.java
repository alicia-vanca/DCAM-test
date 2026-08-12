package com.dvid.dcam.platform.camera.shared.outputsharing;

import android.content.Context;
import android.view.Surface;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.platform.camera.shared.CameraPipelineIds;
import java.io.File;
import java.util.Objects;

public final class NativeSurfaceSharingPipelineFactory {
    public static final VerificationPipelineId PIPELINE_ID =
            CameraPipelineIds.NATIVE_SURFACE_SHARING;

    private final Context context;
    private final Logger logger;
    private final File outputDirectory;

    public NativeSurfaceSharingPipelineFactory(
            Context context, Logger logger, File outputDirectory) {
        Context applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
    }


    public NativeSurfaceSharingPipeline createHeadless(int rotationDegrees) {
        return new NativeSurfaceSharingPipeline(
                context, logger, outputDirectory, null, null, false, rotationDegrees, 0L);
    }

    public NativeSurfaceSharingPipeline createBenchmark(int rotationDegrees) {
        return new NativeSurfaceSharingPipeline(
                context, logger, outputDirectory, null, null, true, rotationDegrees, 0L);
    }



    public NativeSurfaceSharingPipeline create(
            Surface previewSurface, NativePreviewFrameSignal frameSignal, int rotationDegrees,
            long preRecordGopDurationMillis) {
        return new NativeSurfaceSharingPipeline(context, logger, outputDirectory,
                Objects.requireNonNull(previewSurface, "previewSurface"),
                Objects.requireNonNull(frameSignal, "frameSignal"), false, rotationDegrees,
                preRecordGopDurationMillis);
    }
}