package com.dvid.dcam.platform.storage;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import androidx.camera.core.ImageCapture;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Recorder;
import androidx.camera.video.VideoCapture;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.dvid.dcam.platform.logging.DcamLogger;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class DcamMediaOutputImpl implements DcamMediaOutput {
    private static final String MD5_RETRY_NOW = "dcam-md5-retry-now";
    private static final String MD5_RETRY_PERIODIC = "dcam-md5-retry-periodic";
    private static final ExecutorService FINALIZATION_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "dcam-media-finalization");
                thread.setDaemon(true);
                return thread;
            });
    private static final ScheduledExecutorService MD5_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "dcam-video-md5");
                thread.setDaemon(true);
                return thread;
            });
    private final DcamStorage storage;
    private final DcamMediaFinalizer finalizer;
    private final ExecutorService finalizationExecutor;
    private final BooleanSupplier createVideoMd5;
    private final DcamMd5RetryQueue md5RetryQueue;
    private final Context context;

    public DcamMediaOutputImpl(DcamStorage storage) {
        this(storage, null);
    }

    public DcamMediaOutputImpl(DcamStorage storage, BooleanSupplier createVideoMd5) {
        this(null, storage, createVideoMd5);
    }

    public DcamMediaOutputImpl(
            Context context, DcamStorage storage, BooleanSupplier createVideoMd5) {
        this.context = context == null ? null : context.getApplicationContext();
        this.storage = storage;
        this.finalizer = new DcamMediaFinalizer(storage);
        this.finalizationExecutor = FINALIZATION_EXECUTOR;
        this.createVideoMd5 = createVideoMd5;
        this.md5RetryQueue = new DcamMd5RetryQueue(
                storage.configsFile().getParentFile().getParentFile());
        if (this.context != null) scheduleMd5Retries(this.context);
    }

    @Override public DcamMediaFile mediaFile(
            DcamFileType type,
            String cameraId,
            String fileUserId,
            LocalDateTime at,
            boolean encrypted) {
        return storage.mediaFile(type, cameraId, fileUserId, at, encrypted);
    }

    @Override public DcamMediaFile durableAudioMediaFile(
            String cameraId, String fileUserId, LocalDateTime at, boolean encrypted)
            throws IOException {
        return storage.durableAudioMediaFile(cameraId, fileUserId, at, encrypted);
    }

    @Override public CaptureStorageCheck checkCaptureReady() {
        return storage.checkCaptureReady();
    }

    @Override public long availableBytesForNextCapture() {
        return storage.availableBytesForNextCapture();
    }

    @Override public long recordingFileSizeLimit() {
        return storage.recordingFileSizeLimit();
    }

    @Override public ImageCapture.OutputFileOptions imageOptions(Context context, DcamMediaFile mediaFile) {
        if (usesPublicMediaStore(mediaFile.getType())) {
            return new ImageCapture.OutputFileOptions.Builder(context.getContentResolver(),
                    DcamMediaStore.imageCollection(), DcamMediaStore.values(mediaFile)).build();
        }
        return new ImageCapture.OutputFileOptions.Builder(storage.prepareFile(mediaFile)).build();
    }

    @Override public PendingRecording prepareVideoRecording(
            Context context,
            VideoCapture<Recorder> videoCapture,
            DcamMediaFile mediaFile,
            long fileSizeLimitBytes) {
        if (usesPublicMediaStore(mediaFile.getType())) {
            MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(context.getContentResolver(),
                    DcamMediaStore.videoCollection())
                    .setContentValues(DcamMediaStore.values(mediaFile))
                    .setFileSizeLimit(fileSizeLimitBytes)
                    .build();
            return videoCapture.getOutput().prepareRecording(context, options);
        }
        FileOutputOptions options = new FileOutputOptions.Builder(storage.prepareFile(mediaFile))
                .setFileSizeLimit(fileSizeLimitBytes)
                .build();
        return videoCapture.getOutput().prepareRecording(context, options);
    }

    @Override public File audioFile(DcamMediaFile mediaFile) {
        return storage.prepareFile(mediaFile);
    }

    @Override public void encryptSaved(Context context, DcamMediaFile mediaFile, Uri savedUri, String password)
            throws IOException {
        if (savedUri != null) {
            BodycamMediaCrypto.encryptContentUri(
                    context.getContentResolver(), savedUri, context.getCacheDir(), password);
            return;
        }
        BodycamMediaCrypto.encryptFileInPlace(mediaFile.getFile(), password);
    }

    @Override public void publishSaved(Context context, DcamMediaFile mediaFile) {
        if (!storage.isPublicDcim() || usesPublicMediaStore(mediaFile.getType())) return;
        MediaScannerConnection.scanFile(context, new String[] { mediaFile.getFile().getAbsolutePath() },
                new String[] { mediaFile.getType().getMimeType() }, null);
    }

    @Override public void finalizeSaved(
            Context context, DcamMediaFile mediaFile, FinalizationCallback callback) {
        finalizationExecutor.execute(() -> {
            try {
                File finalFile = finalizeSavedNow(context, mediaFile);
                callback.onSuccess(finalFile);
                if (createVideoMd5 != null && createVideoMd5.getAsBoolean()
                        && "mp4".equals(mediaFile.getType().getExtension())) {
                    createMd5WithRetry(finalFile);
                }
            } catch (Exception failure) {
                callback.onFailure(failure);
            }
        });
    }

    @Override public File finalizeSavedNow(Context context, DcamMediaFile mediaFile)
            throws IOException {
        File finalFile = finalizer.finalizeMedia(mediaFile, false);
        if (storage.isPublicDcim()) {
            MediaScannerConnection.scanFile(context,
                    new String[] { finalFile.getAbsolutePath() },
                    new String[] { mediaFile.getType().getMimeType() }, null);
        }
        return finalFile;
    }

    private void createMd5WithRetry(File file) {
        MD5_EXECUTOR.execute(() -> {
            try {
                DcamMd5Sidecar.write(file, DcamMd5RetryProcessor.digest(file));
                md5RetryQueue.remove(file);
            } catch (Exception failure) {
                try { md5RetryQueue.add(file); }
                catch (IOException queueFailure) {
                    failure.addSuppressed(queueFailure);
                    DcamLogger.e("Video MD5 failed; retry queue unavailable: "
                            + file.getAbsolutePath(), failure);
                    return;
                }
                DcamLogger.e("Video MD5 failed; queued for retry: " + file.getAbsolutePath(), failure);
                if (context != null) {
                    try {
                        scheduleMd5RetryNow(context);
                    } catch (RuntimeException schedulingFailure) {
                        DcamLogger.e("Video MD5 retry scheduling failed: "
                                + file.getAbsolutePath(), schedulingFailure);
                    }
                }
            }
        });
    }

    private static void scheduleMd5Retries(Context context) {
        WorkManager workManager = WorkManager.getInstance(context.getApplicationContext());
        workManager.enqueueUniqueWork(MD5_RETRY_NOW, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(DcamMd5RetryWorker.class).build());
        workManager.enqueueUniquePeriodicWork(MD5_RETRY_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(DcamMd5RetryWorker.class, 15, TimeUnit.MINUTES).build());
    }

    private static void scheduleMd5RetryNow(Context context) {
        WorkManager.getInstance(context).enqueueUniqueWork(MD5_RETRY_NOW, ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(DcamMd5RetryWorker.class).build());
    }

    @Override public void recoverStaged(RecoveryCallback callback) {
        finalizationExecutor.execute(() -> callback.onComplete(
                new DcamStagedMediaRecovery(
                        storage, finalizer, new AndroidDcamMediaValidator(), createVideoMd5).recover()));
    }

    private boolean usesPublicMediaStore(DcamFileType type) {
        return Build.VERSION.SDK_INT >= 29 && storage.isPublicDcim() && type != DcamFileType.AUDIO;
    }
}
