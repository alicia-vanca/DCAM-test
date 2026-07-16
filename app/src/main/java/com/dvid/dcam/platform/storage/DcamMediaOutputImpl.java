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
import com.dvid.dcam.platform.logging.DcamLogger;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class DcamMediaOutputImpl implements DcamMediaOutput {
    private static final int MD5_MAX_ATTEMPTS = 3;
    private static final long MD5_RETRY_DELAY_SECONDS = 2L;
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

    public DcamMediaOutputImpl(DcamStorage storage) {
        this(storage, null);
    }

    public DcamMediaOutputImpl(DcamStorage storage, BooleanSupplier createVideoMd5) {
        this.storage = storage;
        this.finalizer = new DcamMediaFinalizer(storage);
        this.finalizationExecutor = FINALIZATION_EXECUTOR;
        this.createVideoMd5 = createVideoMd5;
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
                    createMd5WithRetry(finalFile, 1);
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

    private static String digest(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream input = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder(32);
            for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("MD5 unavailable", impossible);
        }
    }

    private static void createMd5WithRetry(File file, int attempt) {
        MD5_EXECUTOR.execute(() -> {
            try {
                DcamMd5Sidecar.write(file, digest(file));
            } catch (Exception failure) {
                if (attempt < MD5_MAX_ATTEMPTS) {
                    MD5_EXECUTOR.schedule(() -> createMd5WithRetry(file, attempt + 1),
                            MD5_RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
                    return;
                }
                DcamLogger.e("Video MD5 failed after " + MD5_MAX_ATTEMPTS
                        + " attempts: " + file.getAbsolutePath(), failure);
            }
        });
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
