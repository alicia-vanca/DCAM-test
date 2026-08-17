package com.dvid.dcam.platform.storage;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;

import android.content.Context;
import java.io.File;
import java.io.IOException;

public interface DcamMediaOutput {
    DcamMediaFile mediaFile(
            DcamFileType type,
            String cameraId,
            String fileUserId,
            boolean encrypted);
    DcamMediaFile durableAudioMediaFile(
            String cameraId, String fileUserId, boolean encrypted) throws IOException;
    long mediaReservationDelayMillis(DcamFileType type);
    boolean hasPublishedFile(String fileName);
    CaptureStorageCheck checkCaptureReady();
    CaptureStorageCheck checkRecordingReady(long bitrateBitsPerSecond);
    long minimumRecordingStartFreeBytes(long bitrateBitsPerSecond);
    default CaptureStorageCheck checkCaptureWritable() { return checkCaptureReady(); }
    default boolean isExternalStorageRequested() { return false; }
    long availableBytesForNextCapture();
    long recordingFileSizeLimit();
    void prepareVideoFile(DcamMediaFile mediaFile);
    DcamRecordingOutput openVideoOutput(DcamMediaFile mediaFile) throws IOException;
    void prepareImageFile(DcamMediaFile mediaFile);
    SegmentedAesGcmJpegOutput openSegmentedAesGcmJpegOutput(DcamMediaFile mediaFile)
            throws IOException;
    File audioFile(DcamMediaFile mediaFile);
    DcamRecordingOutput openAudioOutput(DcamMediaFile mediaFile) throws IOException;
    File finalizeOpenAudioM4aNow(Context context, DcamMediaFile mediaFile,
            DcamRecordingOutput recordingOutput, long durationUs) throws IOException;
    void releaseMediaReservation(DcamMediaFile mediaFile);
    void encryptSaved(Context context, DcamMediaFile mediaFile, String password) throws IOException;
    File finalizeSavedNow(Context context, DcamMediaFile mediaFile) throws IOException;
    void finalizeSaved(Context context, DcamMediaFile mediaFile,
            String encryptionPassword, FinalizationCallback callback);
    void finalizeCleanVideo(Context context, DcamMediaFile mediaFile,
            DcamRecordingOutput recordingOutput, long durationUs,
            FinalizationCallback callback);
    void recoverStaged(RecoveryCallback callback);

    interface FinalizationCallback {
        void onSuccess(File finalFile);
        void onFailure(Exception failure);
    }

    interface RecoveryCallback {
        void onComplete(StagedMediaRecoveryReport report);
    }
}
