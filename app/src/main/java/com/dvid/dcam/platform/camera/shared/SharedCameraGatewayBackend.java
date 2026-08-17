package com.dvid.dcam.platform.camera.shared;

import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeBackend;

public interface SharedCameraGatewayBackend extends ProcessCameraRuntimeBackend {
    @FunctionalInterface
    interface ImpHandoff {
        void startImpRecording();
    }

    interface RecordingPreparationListener {
        void onPreparing(String message);
        void onCleared();
        void onUnavailable(String message);
    }

    SharedCameraPreviewOutput previewOutput();

    void refreshDisplayRotation();

    void setImpHandoff(ImpHandoff handoff);

    default void setRecordingPreparationListener(RecordingPreparationListener listener) {}

    long recordingBitrateBitsPerSecond(
            com.dvid.dcam.platform.camera.shared.runtime.CameraRuntimeSelection selection);

    void requestRecording(RecordingMode mode);

    void requestImpHandoff();

    void setRecordingStorageLimit(Runnable listener);

    boolean recordingStorageLimitRequested();

    void cancelPendingRecording();
}