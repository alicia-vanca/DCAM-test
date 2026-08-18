package com.dvid.dcam.platform.storage;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;

import android.content.Context;
import android.media.MediaScannerConnection;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.dvid.dcam.core.logging.application.port.Logger;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class DcamMediaOutputImpl implements DcamMediaOutput {
    private static final String MD5_RETRY_NOW = "dcam-md5-retry-now";
    private static final String MD5_RETRY_PERIODIC = "dcam-md5-retry-periodic";
    private static final ExecutorService FINALIZATION_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "dcam-media-finalization");
                thread.setDaemon(true);
                return thread;
            });
    private static final ExecutorService RECOVERY_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "dcam-media-recovery");
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
    private final DcamInterruptedMp4Finalizer mp4Finalizer;
    private final ExecutorService finalizationExecutor;
    private final BooleanSupplier createVideoMd5;
    private final DcamMd5RetryQueue md5RetryQueue;
    private final DcamMediaReservation mediaReservation;
    private final Context context;
    private final Supplier<String> mediaEncryptionPassword;
    private final Logger logger;

    public DcamMediaOutputImpl(
            Context context, DcamStorage storage, BooleanSupplier createVideoMd5,
            Supplier<String> mediaEncryptionPassword, Logger logger) {
        this(context, storage, createVideoMd5, mediaEncryptionPassword, logger,
                new DcamMediaReservation());
    }

    DcamMediaOutputImpl(
            Context context, DcamStorage storage, BooleanSupplier createVideoMd5, Logger logger) {
        this(context, storage, createVideoMd5, () -> "", logger, new DcamMediaReservation());
    }

    DcamMediaOutputImpl(
            Context context, DcamStorage storage, BooleanSupplier createVideoMd5,
            Logger logger, DcamMediaReservation mediaReservation) {
        this(context, storage, createVideoMd5, () -> "", logger, mediaReservation);
    }

    DcamMediaOutputImpl(
            Context context, DcamStorage storage, BooleanSupplier createVideoMd5,
            Supplier<String> mediaEncryptionPassword, Logger logger,
            DcamMediaReservation mediaReservation) {
        this.context = context == null ? null : context.getApplicationContext();
        this.mediaEncryptionPassword = Objects.requireNonNull(
                mediaEncryptionPassword, "mediaEncryptionPassword");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.storage = storage;
        this.mediaReservation = Objects.requireNonNull(mediaReservation, "mediaReservation");
        this.finalizer = new DcamMediaFinalizer(storage);
        this.mp4Finalizer = new DcamInterruptedMp4Finalizer();
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
            boolean encrypted) {
        try {
            return claimMediaFile(type, cameraId, fileUserId, encrypted);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not reserve media file", failure);
        }
    }

    @Override public DcamMediaFile durableAudioMediaFile(
            String cameraId, String fileUserId, boolean encrypted)
            throws IOException {
        return durableAudioMediaFile(
                DcamFileType.AUDIO_M4A, cameraId, fileUserId, encrypted);
    }

    @Override public DcamMediaFile durableAudioMediaFile(
            DcamFileType type, String cameraId, String fileUserId, boolean encrypted)
            throws IOException {
        if (type == null || !type.isAudio()) {
            throw new IllegalArgumentException("Audio media type required");
        }
        return claimMediaFile(type, cameraId, fileUserId, encrypted);
    }

    private DcamMediaFile claimMediaFile(
            DcamFileType type, String cameraId, String fileUserId, boolean encrypted)
            throws IOException {
        long startedAtNanos = System.nanoTime();
        int collisionCount = 0;
        while (true) {
            long reservationStartedAtNanos = System.nanoTime();
            LocalDateTime reservedAt = mediaReservation.reserve(type);
            long reservedAtNanos = System.nanoTime();
            DcamMediaFile mediaFile = storage.mediaFile(
                    type, cameraId, fileUserId, reservedAt, encrypted);
            long pathResolvedAtNanos = System.nanoTime();
            synchronized (DcamMediaReservation.FILESYSTEM_LOCK) {
                if (mediaFile.getFile().isFile()
                        || storage.hasFinalMediaFile(mediaFile)
                        || !mediaReservation.trackActiveStaging(mediaFile.getFile())) {
                    collisionCount++;
                    continue;
                }
                long completedAtNanos = System.nanoTime();
                logger.info("Reserve " + type + " media filename success. Filename wait: "
                        + elapsedMillis(reservationStartedAtNanos, reservedAtNanos)
                        + " ms. Path resolve: "
                        + elapsedMillis(reservedAtNanos, pathResolvedAtNanos)
                        + " ms. Collision check: "
                        + elapsedMillis(pathResolvedAtNanos, completedAtNanos)
                        + " ms. Total: "
                        + elapsedMillis(startedAtNanos, completedAtNanos)
                        + " ms. Collisions: " + collisionCount + ".");
                return mediaFile;
            }
        }
    }
    private void logMediaReservationFailure(DcamFileType type, File target, IOException failure) {
        File parent = target.getParentFile();
        String storageCheck;
        try {
            CaptureStorageCheck check = storage.checkCaptureReady();
            storageCheck = "ready=" + check.isReady()
                    + ", preparing=" + check.isPreparing()
                    + ", unavailable=" + check.isUnavailable()
                    + ", availableBytes=" + check.getAvailableBytes()
                    + ", requiredBytes=" + check.getRequiredBytes()
                    + ", reason=" + check.getReason();
        } catch (RuntimeException checkFailure) {
            storageCheck = "failed with " + checkFailure.getClass().getSimpleName()
                    + ": " + checkFailure.getMessage();
        }
        logger.error("Reserve " + type + " media file failed at " + target.getAbsolutePath()
                + ". Failure: " + failure.getClass().getSimpleName()
                + ": " + failure.getMessage()
                + ". Parent exists: " + (parent != null && parent.exists())
                + ". Parent directory: " + (parent != null && parent.isDirectory())
                + ". Parent writable: " + (parent != null && parent.canWrite())
                + ". Requested storage: " + storage.getRequestedMode()
                + ". Fresh storage check: " + storageCheck + ".", failure);
    }

    @Override public long mediaReservationDelayMillis(DcamFileType type) {
        return mediaReservation.delayMillis(type);
    }

    @Override public boolean hasPublishedFile(String fileName) {
        return storage.hasPublishedFile(fileName);
    }

    @Override public CaptureStorageCheck checkCaptureReady() {
        long startedAtNanos = System.nanoTime();
        CaptureStorageCheck check = storage.checkCaptureReady();
        long completedAtNanos = System.nanoTime();
        long elapsedMillis = elapsedMillis(startedAtNanos, completedAtNanos);
        if (elapsedMillis >= 100L) {
            logger.info("Check capture storage readiness completed. Ready: "
                    + check.isReady() + ". Preparing: " + check.isPreparing()
                    + ". Unavailable: " + check.isUnavailable()
                    + ". Elapsed: " + elapsedMillis + " ms.");
        }
        return check;
    }

    @Override public CaptureStorageCheck checkRecordingReady(long bitrateBitsPerSecond) {
        return storage.checkRecordingReady(bitrateBitsPerSecond);
    }

    @Override public long minimumRecordingStartFreeBytes(long bitrateBitsPerSecond) {
        return storage.minimumRecordingStartFreeBytes(bitrateBitsPerSecond);
    }

    @Override public CaptureStorageCheck checkCaptureWritable() {
        return storage.checkCaptureWritable();
    }

    @Override public boolean isExternalStorageRequested() {
        return storage.getRequestedMode()
                == com.dvid.dcam.feature.storage.domain.MediaPartitionLocation.EXTERNAL;
    }

    @Override public long availableBytesForNextCapture() {
        return storage.availableBytesForNextCapture();
    }

    @Override public long recordingFileSizeLimit() {
        return storage.recordingFileSizeLimit();
    }

    @Override public void prepareVideoFile(DcamMediaFile mediaFile) {
        File staged = storage.prepareFile(mediaFile);
        logger.info("media_stage type=video file=" + staged.getName());
    }
    @Override public DcamRecordingOutput openVideoOutput(DcamMediaFile mediaFile)
            throws IOException {
        return openRecordingOutput(mediaFile);
    }

    @Override public void prepareImageFile(DcamMediaFile mediaFile) {
        storage.prepareFile(mediaFile);
    }

    @Override public SegmentedAesGcmJpegOutput openSegmentedAesGcmJpegOutput(
            DcamMediaFile mediaFile) throws IOException {
        Objects.requireNonNull(mediaFile, "mediaFile");
        if (mediaFile.getType() != DcamFileType.IMAGE || !mediaFile.isEncrypted()) {
            throw new IOException("Segmented AES-GCM JPEG output requires encrypted image media.");
        }
        return new SegmentedAesGcmJpegOutput(
                storage.prepareFile(mediaFile), mediaEncryptionPassword.get());
    }

    @Override public File audioFile(DcamMediaFile mediaFile) {
        File staged = storage.prepareFile(mediaFile);
        logger.info("media_stage type=audio file=" + staged.getName());
        return staged;
    }
    @Override public DcamRecordingOutput openAudioOutput(DcamMediaFile mediaFile)
            throws IOException {
        return openRecordingOutput(mediaFile);
    }

    private DcamRecordingOutput openRecordingOutput(DcamMediaFile mediaFile)
            throws IOException {
        Objects.requireNonNull(mediaFile, "mediaFile");
        File staged = storage.prepareFile(mediaFile);
        if (mediaFile.isEncrypted()) {
            int blockBytes = mediaFile.getType().isAudio()
                    ? SegmentedAesGcmMediaStore.AUDIO_RECORDING_BLOCK_BYTES
                    : SegmentedAesGcmMediaStore.RECORDING_BLOCK_BYTES;
            return DcamRecordingOutput.openSegmentedAesGcm(
                    staged, mediaEncryptionPassword.get(), blockBytes);
        }
        return DcamRecordingOutput.openPlain(staged, true);
    }

    @Override public void releaseMediaReservation(DcamMediaFile mediaFile) {
        mediaReservation.releaseActiveStaging(
                Objects.requireNonNull(mediaFile, "mediaFile").getFile());
    }


    @Override public void encryptSaved(
            Context context, DcamMediaFile mediaFile, String password) throws IOException {
        if (DcamEncryptionJournal.isComplete(mediaFile)) return;
        if (SegmentedAesGcmMediaStore.hasFamilyMagic(mediaFile.getFile())) {
            throw new IOException("Segmented AES-GCM media must not use legacy AES-CTR encryption.");
        }
        finalizeMp4(mediaFile);
        BodycamMediaCrypto.encryptFileInPlace(mediaFile.getFile(), password);
        DcamEncryptionJournal.markComplete(mediaFile);
    }

    @Override public void finalizeSaved(
            Context context,
            DcamMediaFile mediaFile,
            String encryptionPassword,
            FinalizationCallback callback) {
        finalizationExecutor.execute(() -> {
            String stagedDescription = stagedDescription(mediaFile);
            String stage = encryptionPassword == null && usesFragmentedMp4Container(mediaFile)
                    ? "container finalization"
                    : encryptionPassword == null ? "publication" : "encryption";
            if (!"publication".equals(stage)) {
                logger.info("Finalization started for staged media " + stagedDescription
                        + ". First stage: " + stage + ".");
            }
            File finalFile;
            boolean segmentedAesGcm = false;
            try {
                segmentedAesGcm = encryptionPassword == null
                        && SegmentedAesGcmMediaStore.hasFamilyMagic(mediaFile.getFile());
                if (encryptionPassword != null) {
                    encryptSaved(context, mediaFile, encryptionPassword);
                    logger.info("Encryption completed for staged media " + stagedDescription + ".");
                } else if (!segmentedAesGcm) {
                    finalizeMp4(mediaFile);
                }
                requireEncryptedImageReadyForPublication(mediaFile, segmentedAesGcm);
                stage = "publication";
                finalFile = segmentedAesGcm
                        ? publishCleanSavedNow(context, mediaFile, true)
                        : publishSavedNow(context, mediaFile);
                if (segmentedAesGcm) finalizer.cleanupCleanMedia(mediaFile);
            } catch (Exception failure) {
                logger.error("Finalization failed for staged media " + stagedDescription
                        + ". Failed stage: " + stage + ". Staged file remains in Temp. Reason: "
                        + message(failure) + ".", failure);
                mediaReservation.releaseActiveStaging(mediaFile.getFile());
                callback.onFailure(failure);
                return;
            }
            logger.info("Finalization completed for staged media '" + mediaFile.getFileName()
                    + "'. Published to '" + finalFile.getAbsolutePath() + "'.");
            mediaReservation.releaseActiveStaging(mediaFile.getFile());
            callback.onSuccess(finalFile);
            if (createVideoMd5 != null && createVideoMd5.getAsBoolean()
                    && mediaFile.getType().isVideo()) {
                createMd5WithRetry(finalFile);
            }
        });
    }

    @Override public void finalizeCleanVideo(
            Context context, DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput,
            long durationUs, FinalizationCallback callback) {
        finalizationExecutor.execute(() -> {
            long started = System.nanoTime();
            long durationMillis;
            long metadataPatchMillis;
            long durabilitySyncMillis;
            boolean writerSeekIndexUsed;
            long patched;
            long outputFinalized;
            long published;
            String stage = "container patch";
            File finalFile;
            try {
                DcamInterruptedMp4Finalizer.CleanResult cleanResult;
                if (recordingOutput == null) {
                    cleanResult = mp4Finalizer.finalizeCleanTimed(
                            mediaFile.getFile(), durationUs);
                } else {
                    if (!recordingOutput.isOpen()) {
                        throw new IOException("Recording output closed before finalization.");
                    }
                    cleanResult = mp4Finalizer.finalizeCleanTimed(
                            recordingOutput.media(), durationUs);
                }
                durationMillis = cleanResult.durationMillis();
                metadataPatchMillis = cleanResult.metadataPatchMillis();
                durabilitySyncMillis = cleanResult.durabilitySyncMillis();
                writerSeekIndexUsed = cleanResult.writerSeekIndexUsed();
                patched = System.nanoTime();
                if (recordingOutput != null) {
                    stage = "recording output finalization";
                    recordingOutput.finish();
                    recordingOutput.close();
                }
                outputFinalized = System.nanoTime();
                stage = "publication rename";
                finalFile = publishCleanSavedNow(context, mediaFile, false);
                published = System.nanoTime();
            } catch (Exception failure) {
                if (recordingOutput != null && recordingOutput.isOpen()) {
                    try {
                        recordingOutput.close();
                    } catch (IOException closeFailure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
                boolean stagingPreserved = mediaFile.getFile().isFile();
                logger.error("Clean MP4 finalization failed for staged media '"
                        + mediaFile.getFileName() + "'. Failed stage: " + stage + ". "
                        + (stagingPreserved
                                ? "File remains in Temp for recovery. "
                                : "Staged file is no longer in Temp; recovery must reconcile final publication. ")
                        + "Reason: " + message(failure) + ".", failure);
                mediaReservation.releaseActiveStaging(mediaFile.getFile());
                callback.onFailure(failure);
                return;
            }
            logger.info("Clean MP4 finalization completed for staged media '"
                    + mediaFile.getFileName() + "'. Duration: " + durationMillis
                    + " ms. Container patch: " + elapsedMillis(started, patched)
                    + " ms. Metadata write: " + metadataPatchMillis
                    + " ms. Durability sync: " + durabilitySyncMillis
                    + " ms. Seek index source: "
                    + (writerSeekIndexUsed ? "writer metadata." : "final scan.")
                    + " Recording output finalization: "
                    + elapsedMillis(patched, outputFinalized)
                    + " ms. Publication rename: " + elapsedMillis(outputFinalized, published)
                    + " ms. Total: " + elapsedMillis(started, published) + " ms.");
            mediaReservation.releaseActiveStaging(mediaFile.getFile());
            callback.onSuccess(finalFile);
            finalizer.cleanupCleanMedia(mediaFile);
            if (createVideoMd5 != null && createVideoMd5.getAsBoolean()) {
                createMd5WithRetry(finalFile);
            }
        });
    }
    @Override public File finalizeOpenAudioM4aNow(
            Context context, DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput,
            long durationUs) throws IOException {
        Objects.requireNonNull(mediaFile, "mediaFile");
        Objects.requireNonNull(recordingOutput, "recordingOutput");
        if (mediaFile.getType() != DcamFileType.AUDIO_M4A) {
            throw new IOException("Open audio M4A finalization requires AUDIO_M4A media.");
        }
        long started = System.nanoTime();
        long durationMillis;
        String containerPath;
        String containerDetail;
        String stage = "container finalization";
        try {
            if (!recordingOutput.isOpen()) {
                throw new IOException("Recording output closed before audio M4A finalization.");
            }
            try {
                if (durationUs <= 0L) {
                    throw new IOException("Audio M4A duration is unavailable.");
                }
                DcamInterruptedMp4Finalizer.CleanResult cleanResult =
                        mp4Finalizer.finalizeCleanTimed(recordingOutput.media(), durationUs);
                durationMillis = cleanResult.durationMillis();
                containerPath = cleanResult.writerSeekIndexUsed()
                        ? "writer metadata" : "final scan";
                containerDetail = "Metadata patch: " + cleanResult.metadataPatchMillis()
                        + " ms. Durability sync: " + cleanResult.durabilitySyncMillis() + " ms.";
            } catch (IOException cleanFailure) {
                logger.warn("Fast audio M4A finalization could not complete for staged media '"
                        + mediaFile.getFileName()
                        + "'. Retrying with recovery scan.", cleanFailure);
                DcamInterruptedMp4Finalizer.Result recoveryResult;
                try {
                    recoveryResult = mp4Finalizer.finalizeInterrupted(recordingOutput.media());
                } catch (IOException recoveryFailure) {
                    recoveryFailure.addSuppressed(cleanFailure);
                    throw recoveryFailure;
                }
                if (recoveryResult.durationMillis() <= 0L) {
                    IOException recoveryFailure = new IOException(
                            "Audio M4A duration finalization failed. "
                                    + recoveryResult.detail());
                    recoveryFailure.addSuppressed(cleanFailure);
                    throw recoveryFailure;
                }
                durationMillis = recoveryResult.durationMillis();
                containerPath = "recovery scan";
                containerDetail = recoveryResult.detail();
            }
            long containerFinalized = System.nanoTime();
            stage = "recording output finalization";
            recordingOutput.finish();
            recordingOutput.close();
            long outputFinalized = System.nanoTime();
            stage = "publication";
            File finalFile = publishCleanSavedNow(context, mediaFile, false);
            long published = System.nanoTime();
            finalizer.cleanupCleanMedia(mediaFile);
            logger.info("Audio M4A finalization completed for staged media '"
                    + mediaFile.getFileName() + "'. Duration: " + durationMillis
                    + " ms. Container path: " + containerPath + ". Container finalization: "
                    + elapsedMillis(started, containerFinalized) + " ms. " + containerDetail
                    + " Recording output finalization: "
                    + elapsedMillis(containerFinalized, outputFinalized)
                    + " ms. Publication rename: " + elapsedMillis(outputFinalized, published)
                    + " ms. Total: " + elapsedMillis(started, published) + " ms.");
            return finalFile;
        } catch (Exception failure) {
            if (recordingOutput.isOpen()) {
                try {
                    recordingOutput.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            logger.error("Audio M4A finalization failed for staged media '"
                    + mediaFile.getFileName() + "'. Failed stage: " + stage + ". "
                    + (mediaFile.getFile().isFile()
                            ? "File remains in Temp for startup recovery. "
                            : "Staged file is no longer in Temp. ")
                    + "Reason: " + message(failure) + ".", failure);
            if (failure instanceof IOException ioFailure) throw ioFailure;
            throw new IOException("Audio M4A finalization failed: " + message(failure), failure);
        } finally {
            mediaReservation.releaseActiveStaging(mediaFile.getFile());
        }
    }
    @Override public File finalizeSavedNow(Context context, DcamMediaFile mediaFile)
            throws IOException {
        try {
            boolean segmentedAesGcm =
                    SegmentedAesGcmMediaStore.hasFamilyMagic(mediaFile.getFile());
            requireEncryptedImageReadyForPublication(mediaFile, segmentedAesGcm);
            if (!DcamEncryptionJournal.isComplete(mediaFile) && !segmentedAesGcm) {
                finalizeMp4(mediaFile);
            }
            if (!segmentedAesGcm) return publishSavedNow(context, mediaFile);
            File published = publishCleanSavedNow(context, mediaFile, true);
            finalizer.cleanupCleanMedia(mediaFile);
            return published;
        } finally {
            mediaReservation.releaseActiveStaging(mediaFile.getFile());
        }
    }

    private static void requireEncryptedImageReadyForPublication(
            DcamMediaFile mediaFile, boolean segmentedAesGcm) throws IOException {
        if (mediaFile.getType() != DcamFileType.IMAGE || !mediaFile.isEncrypted()
                || segmentedAesGcm || DcamEncryptionJournal.isComplete(mediaFile)) return;
        throw new IOException(
                "Encrypted image staging has no Segmented AES-GCM envelope or completed legacy AES-CTR journal.");
    }

    private File publishSavedNow(Context context, DcamMediaFile mediaFile) throws IOException {
        File finalFile = finalizer.finalizeMedia(mediaFile, false);
        return completePublication(context, mediaFile, finalFile);
    }

    private File publishCleanSavedNow(
            Context context, DcamMediaFile mediaFile, boolean verifySegmentedFinal)
            throws IOException {
        if (verifySegmentedFinal
                && SegmentedAesGcmMediaStore.hasFamilyMagic(mediaFile.getFile())) {
            try (SegmentedAesGcmMediaStore ignored = SegmentedAesGcmMediaStore.openPublished(
                    mediaFile.getFile(), mediaEncryptionPassword.get())) {
            }
        }
        File finalFile = finalizer.finalizeCleanMedia(mediaFile);
        try {
            return completePublication(context, mediaFile, finalFile);
        } catch (RuntimeException failure) {
            logger.warn("Final media publication completed, but post-publication notification failed for '"
                    + mediaFile.getFileName() + "'.", failure);
            return finalFile;
        }
    }

    private File completePublication(Context context, DcamMediaFile mediaFile, File finalFile) {
        DcamEncryptionJournal.clear(mediaFile);
        if (storage.isPublicDcim()) {
            MediaScannerConnection.scanFile(context,
                    new String[] { finalFile.getAbsolutePath() },
                    new String[] { mediaFile.getType().getMimeType() }, null);
        }
        return finalFile;
    }

    private void finalizeMp4(DcamMediaFile mediaFile) throws IOException {
        if (!usesFragmentedMp4Container(mediaFile)) return;
        DcamInterruptedMp4Finalizer.Result result =
                mp4Finalizer.finalizeInterrupted(mediaFile.getFile());
        if (result.durationMillis() <= 0L) {
            throw new IOException("MP4 duration finalization failed. " + result.detail());
        }
        logger.info("Finalized staged MP4 container '" + mediaFile.getFileName() + "'. "
                + result.detail());
    }

    private static boolean usesFragmentedMp4Container(DcamMediaFile mediaFile) {
        return mediaFile.getType().usesFragmentedMp4Container();
    }

    private static String stagedDescription(DcamMediaFile mediaFile) {
        File staged = mediaFile.getFile();
        return "'" + mediaFile.getFileName() + "' (" + mediaFile.getType().name()
                + ", " + staged.length() + " bytes at '" + staged.getAbsolutePath() + "')";
    }

    private static String message(Throwable failure) {
        String detail = failure.getMessage();
        return detail == null || detail.isBlank()
                ? failure.getClass().getSimpleName() : detail;
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
                    logger.error("Video MD5 failed; retry queue unavailable: "
                            + file.getAbsolutePath(), failure);
                    return;
                }
                logger.error("Video MD5 failed; queued for retry: " + file.getAbsolutePath(), failure);
                if (context != null) {
                    try {
                        scheduleMd5RetryNow(context);
                    } catch (RuntimeException schedulingFailure) {
                        logger.error("Video MD5 retry scheduling failed: "
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
        Objects.requireNonNull(callback, "callback");
        try {
            DcamStagedMediaRecovery recovery = stagedRecovery();
            java.util.Set<String> activeStagingPaths = mediaReservation.activeStagingPaths();
            if (!activeStagingPaths.isEmpty()) {
                logger.info("Staged-media recovery skipped active capture files. Count: "
                        + activeStagingPaths.size() + ".");
            }
            File[][] candidates = recovery.snapshot(activeStagingPaths);
            RECOVERY_EXECUTOR.execute(() -> callback.onComplete(
                    recoverSafely(() -> recovery.recover(candidates))));
        } catch (RuntimeException failure) {
            callback.onComplete(StagedMediaRecoveryReport.failed(failure));
        }
    }


    private DcamStagedMediaRecovery stagedRecovery() {
        return new DcamStagedMediaRecovery(
                storage, finalizer, new AndroidDcamMediaValidator(), createVideoMd5,
                mediaEncryptionPassword, this::createMd5WithRetry, logger);
    }

    private static long elapsedMillis(long startedNanos, long completedNanos) {
        return Math.max(0L, completedNanos - startedNanos) / 1_000_000L;
    }

    static StagedMediaRecoveryReport recoverSafely(
            Supplier<StagedMediaRecoveryReport> recovery) {
        try {
            return recovery.get();
        } catch (RuntimeException failure) {
            return StagedMediaRecoveryReport.failed(failure);
        }
    }

}
