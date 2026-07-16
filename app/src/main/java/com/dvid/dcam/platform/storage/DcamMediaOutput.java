package com.dvid.dcam.platform.storage;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;

import android.content.Context;
import android.net.Uri;
import androidx.camera.core.ImageCapture;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

public interface DcamMediaOutput {
    DcamMediaFile mediaFile(
            DcamFileType type,
            String cameraId,
            String fileUserId,
            LocalDateTime at,
            boolean encrypted);
    DcamMediaFile durableAudioMediaFile(
            String cameraId, String fileUserId, LocalDateTime at, boolean encrypted) throws IOException;
    CaptureStorageCheck checkCaptureReady();
    long availableBytesForNextCapture();
    long recordingFileSizeLimit();
    ImageCapture.OutputFileOptions imageOptions(Context context, DcamMediaFile mediaFile);
    PendingRecording prepareVideoRecording(
            Context context,
            VideoCapture<Recorder> videoCapture,
            DcamMediaFile mediaFile,
            long fileSizeLimitBytes);
    File audioFile(DcamMediaFile mediaFile);
    void encryptSaved(Context context, DcamMediaFile mediaFile, Uri savedUri, String password) throws IOException;
    File finalizeSavedNow(Context context, DcamMediaFile mediaFile) throws IOException;
    void finalizeSaved(Context context, DcamMediaFile mediaFile, FinalizationCallback callback);
    void recoverStaged(RecoveryCallback callback);
    void publishSaved(Context context, DcamMediaFile mediaFile);

    interface FinalizationCallback {
        void onSuccess(File finalFile);
        void onFailure(Exception failure);
    }

    interface RecoveryCallback {
        void onComplete(StagedMediaRecoveryReport report);
    }
}
