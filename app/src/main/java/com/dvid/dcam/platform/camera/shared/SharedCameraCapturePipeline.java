package com.dvid.dcam.platform.camera.shared;

import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.platform.storage.DcamRecordingOutput;
import com.dvid.dcam.platform.camera.shared.api.SharedCameraPipeline;
import java.io.File;

public interface SharedCameraCapturePipeline extends SharedCameraPipeline {
    record HealthSnapshot(
            boolean sessionBound,
            boolean recoveryRequired,
            boolean previewSignalAvailable,
            long sourceFrameCount,
            long previewFrameCount,
            String detail) {}

    @FunctionalInterface
    interface RecordingLimitListener {
        void onLimitReached();
    }

    CameraOperationResult startEncoder(CameraOperationContext context, File outputFile);

    CameraOperationResult startEncoder(
            CameraOperationContext context,
            File outputFile,
            long fileSizeLimitBytes,
            RecordingLimitListener listener);
    default CameraOperationResult startEncoder(
            CameraOperationContext context,
            DcamRecordingOutput recordingOutput,
            long fileSizeLimitBytes,
            RecordingLimitListener listener) {
        return startEncoder(context, recordingOutput.file(), fileSizeLimitBytes, listener);
    }

    HealthSnapshot healthSnapshot(CameraOperationContext context);

    default long finalizedDurationUs() { return 0L; }

    default CameraOperationResult finalizeEncoder(
            CameraOperationContext context, boolean cleanStop) {
        return finalizeEncoder(context);
    }

    default void setPreviewExpected(boolean expected) {}

    void setRotation(int rotationDegrees);

    int outputRotationDegrees();

    CameraOperationResult captureJpeg(CameraOperationContext context, File outputFile);
}