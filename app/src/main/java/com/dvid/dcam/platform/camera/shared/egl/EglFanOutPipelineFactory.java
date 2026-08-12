package com.dvid.dcam.platform.camera.shared.egl;

import android.content.Context;
import android.view.Surface;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.platform.camera.shared.CameraPipelineIds;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativePreviewFrameSignal;
import java.io.File;
import java.util.Objects;

public final class EglFanOutPipelineFactory {
    public static final VerificationPipelineId PIPELINE_ID =
            CameraPipelineIds.EGL_FAN_OUT;

    private final Context context;
    private final Logger logger;
    private final File outputDirectory;

    public EglFanOutPipelineFactory(Context context, Logger logger, File outputDirectory) {
        Context applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
    }

    public EglFanOutPipeline createHeadless(int rotationDegrees) {
        return new EglFanOutPipeline(context, logger, outputDirectory,
                null, null, false, rotationDegrees, 0L);
    }

    public EglFanOutPipeline createBenchmark(int rotationDegrees) {
        return new EglFanOutPipeline(context, logger, outputDirectory,
                null, null, true, rotationDegrees, 0L);
    }

    public EglFanOutPipeline create(Surface previewSurface,
            NativePreviewFrameSignal frameSignal, int rotationDegrees,
            long preRecordGopDurationMillis) {
        return new EglFanOutPipeline(context, logger, outputDirectory,
                Objects.requireNonNull(previewSurface, "previewSurface"),
                Objects.requireNonNull(frameSignal, "frameSignal"), false, rotationDegrees,
                preRecordGopDurationMillis);
    }
}