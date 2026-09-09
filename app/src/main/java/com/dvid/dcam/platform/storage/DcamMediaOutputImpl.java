package com.dvid.dcam.platform.storage;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import com.dvid.dcam.feature.storage.domain.DcamMediaFileState;

import android.content.Context;
import android.media.MediaScannerConnection;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.platform.database.AppDatabase;
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
    private static final String MEDIA_FILE_PARAMETER = "mediaFile";
    private static final String STAGE_PUBLICATION = "publication";
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
    private final DcamMediaStateStore mediaStateStore;

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
        this(context, storage, createVideoMd5, () -> "", logger, mediaReservation,
                mediaStateStore(context, logger));
    }

    DcamMediaOutputImpl(
            Context context, DcamStorage storage, BooleanSupplier createVideoMd5,
            Supplier<String> mediaEncryptionPassword, Logger logger,
            DcamMediaReservation mediaReservation) {
        this(context, storage, createVideoMd5, mediaEncryptionPassword, logger, mediaReservation,
                mediaStateStore(context, logger));
    }

    DcamMediaOutputImpl(
            Context context, DcamStorage storage, BooleanSupplier createVideoMd5,
            Supplier<String> mediaEncryptionPassword, Logger logger,
            DcamMediaReservation mediaReservation, DcamMediaStateStore mediaStateStore) {
        this.context = context == null ? null : context.getApplicationContext();
        this.mediaEncryptionPassword = Objects.requireNonNull(
                mediaEncryptionPassword, "mediaEncryptionPassword");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.storage = storage;
        this.mediaReservation = Objects.requireNonNull(mediaReservation, "mediaReservation");
        this.mediaStateStore = Objects.requireNonNull(mediaStateStore, "mediaStateStore");
        this.finalizer = new DcamMediaFinalizer(storage);
        this.mp4Finalizer = new DcamInterruptedMp4Finalizer();
        this.finalizationExecutor = FINALIZATION_EXECUTOR;
        this.createVideoMd5 = createVideoMd5;
        this.md5RetryQueue = new DcamMd5RetryQueue(
                storage.configsFile().getParentFile().getParentFile());
        if (this.context != null) scheduleMd5Retries(this.context);
    }

    private static DcamMediaStateStore mediaStateStore(Context context, Logger logger) {
        if (context == null) return DcamMediaStateStore.noOp();
        return new RoomDcamMediaStateStore(AppDatabase.get(context).mediaFileStates(), logger);
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
                logger.info(LogCategory.STORAGE, "unspecified", "Reserve " + type + " media filename success. Filename wait: "
                        + elapsedMillis(reservationStartedAtNanos, reservedAtNanos)
                        + " ms. Path resolve: "
                        + elapsedMillis(reservedAtNanos, pathResolvedAtNanos)
                        + " ms. Collision check: "
                        + elapsedMillis(pathResolvedAtNanos, completedAtNanos)
                        + " ms. Total: "
                        + elapsedMillis(startedAtNanos, completedAtNanos)
                        + " ms. Collisions: " + collisionCount + ".");
            }
            updateMediaState(mediaFile.getFile(), DcamMediaFileState.IN_PROGRESS);
            return mediaFile;
        }
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
            logger.info(LogCategory.STORAGE, "capture_storage_precheck_completed", captureStorageReason(check), "Check capture storage readiness completed. Ready: "
                    + check.isReady() + ". Preparing: " + check.isPreparing()
                    + ". Unavailable: " + check.isUnavailable()
                    + ". Elapsed: " + elapsedMillis + " ms.", null);
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
        logger.info(LogCategory.STORAGE, "unspecified", "media_stage type=video file=" + staged.getName());
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
        Objects.requireNonNull(mediaFile, MEDIA_FILE_PARAMETER);
        if (mediaFile.getType() != DcamFileType.IMAGE || !mediaFile.isEncrypted()) {
            throw new IOException("Segmented AES-GCM JPEG output requires encrypted image media.");
        }
        return new SegmentedAesGcmJpegOutput(
                storage.prepareFile(mediaFile), mediaEncryptionPassword.get());
    }

    @Override public File audioFile(DcamMediaFile mediaFile) {
        File staged = storage.prepareFile(mediaFile);
        logger.info(LogCategory.STORAGE, "unspecified", "media_stage type=audio file=" + staged.getName());
        return staged;
    }
    @Override public DcamRecordingOutput openAudioOutput(DcamMediaFile mediaFile)
            throws IOException {
        return openRecordingOutput(mediaFile);
    }

    private DcamRecordingOutput openRecordingOutput(DcamMediaFile mediaFile)
            throws IOException {
        Objects.requireNonNull(mediaFile, MEDIA_FILE_PARAMETER);
        File staged = storage.prepareFile(mediaFile);
        if (mediaFile.isEncrypted()) {
            int blockBytes = mediaFile.getType().isAudio()
                    ? SegmentedAesGcmMediaStore.AUDIO_RECORDING_BLOCK_BYTES
                    : SegmentedAesGcmMediaStore.RECORDING_BLOCK_BYTES;
            return DcamRecordingOutput.openSegmentedAesGcm(
                    staged, mediaEncryptionPassword.get(), blockBytes,
                    storage::recordingAvailableBytes);
        }
        return DcamRecordingOutput.openPlain(
                staged, true, storage::recordingAvailableBytes);
    }

    @Override public void releaseMediaReservation(DcamMediaFile mediaFile) {
        mediaReservation.releaseActiveStaging(
                Objects.requireNonNull(mediaFile, MEDIA_FILE_PARAMETER).getFile());
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
        finalizationExecutor.execute(
                () -> finalizeSavedOnExecutor(context, mediaFile, encryptionPassword, callback));
    }

    private void finalizeSavedOnExecutor(
            Context context, DcamMediaFile mediaFile, String encryptionPassword,
            FinalizationCallback callback) {
        String stagedDescription = stagedDescription(mediaFile);
        FinalizationProgress progress = new FinalizationProgress(
                initialSavedFinalizationStage(mediaFile, encryptionPassword));
        updateMediaState(mediaFile.getFile(), DcamMediaFileState.FINALIZING);
        logSavedFinalizationStart(stagedDescription, progress.stage());
        File finalFile;
        try {
            finalFile = finalizeSavedStages(
                    context, mediaFile, encryptionPassword, stagedDescription, progress);
        } catch (Exception failure) {
            failSavedFinalization(mediaFile, stagedDescription, progress.stage(), callback, failure);
            return;
        }
        completeSavedFinalization(mediaFile, finalFile, callback);
    }

    private static String initialSavedFinalizationStage(
            DcamMediaFile mediaFile, String encryptionPassword) {
        if (encryptionPassword != null) return "encryption";
        return usesFragmentedMp4Container(mediaFile)
                ? "container finalization" : STAGE_PUBLICATION;
    }

    private void logSavedFinalizationStart(String stagedDescription, String stage) {
        if (!STAGE_PUBLICATION.equals(stage)) {
            logger.info(LogCategory.STORAGE, "media_finalization_started", "Finalization started for staged media " + stagedDescription
                    + ". First stage: " + stage + ".");
        }
    }

    private File finalizeSavedStages(
            Context context, DcamMediaFile mediaFile, String encryptionPassword,
            String stagedDescription, FinalizationProgress progress) throws IOException {
        boolean segmentedAesGcm = encryptionPassword == null
                && SegmentedAesGcmMediaStore.hasFamilyMagic(mediaFile.getFile());
        if (encryptionPassword != null) {
            encryptSaved(context, mediaFile, encryptionPassword);
            logger.info(LogCategory.STORAGE, "unspecified", "Encryption completed for staged media " + stagedDescription + ".");
        } else if (!segmentedAesGcm) {
            finalizeMp4(mediaFile);
        }
        requireEncryptedImageReadyForPublication(mediaFile, segmentedAesGcm);
        progress.moveTo(STAGE_PUBLICATION);
        File finalFile = segmentedAesGcm
                ? publishCleanSavedNow(context, mediaFile, true)
                : publishSavedNow(context, mediaFile);
        if (segmentedAesGcm) finalizer.cleanupCleanMedia(mediaFile);
        return finalFile;
    }

    private void completeSavedFinalization(
            DcamMediaFile mediaFile, File finalFile, FinalizationCallback callback) {
        logger.info(LogCategory.STORAGE, "media_finalization_completed", "Finalization completed for staged media '" + mediaFile.getFileName()
                + "'. Published to '" + finalFile.getAbsolutePath() + "'.");
        mediaReservation.releaseActiveStaging(mediaFile.getFile());
        callback.onSuccess(finalFile);
        if (shouldCreateVideoMd5(mediaFile.getType())) {
            createMd5WithRetry(finalFile);
        }
    }

    private void failSavedFinalization(
            DcamMediaFile mediaFile, String stagedDescription, String stage,
            FinalizationCallback callback, Exception failure) {
        logger.error(LogCategory.STORAGE, "media_finalization_failed", null, "Finalization failed for staged media " + stagedDescription
                + ". Failed stage: " + stage + ". Staged file remains in Temp. Reason: "
                + message(failure) + ".", failure);
        updateMediaState(mediaFile.getFile(), DcamMediaFileState.RECOVERY_REQUIRED);
        mediaReservation.releaseActiveStaging(mediaFile.getFile());
        callback.onFailure(failure);
    }

    @Override public void finalizeCleanVideo(
            Context context, DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput,
            long durationUs, FinalizationCallback callback) {
        finalizationExecutor.execute(
                () -> finalizeCleanVideoOnExecutor(context, mediaFile, recordingOutput, durationUs, callback));
    }

    private void finalizeCleanVideoOnExecutor(
            Context context, DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput,
            long durationUs, FinalizationCallback callback) {
        long started = System.nanoTime();
        FinalizationProgress progress = new FinalizationProgress("container patch");
        updateMediaState(mediaFile.getFile(), DcamMediaFileState.FINALIZING);
        CleanVideoFinalization finalization;
        try {
            finalization = finalizeCleanVideoStages(
                    context, mediaFile, recordingOutput, durationUs, progress);
        } catch (Exception failure) {
            failCleanVideoFinalization(mediaFile, recordingOutput, progress.stage(), callback, failure);
            return;
        }
        completeCleanVideoFinalization(mediaFile, finalization, callback, started);
    }

    private CleanVideoFinalization finalizeCleanVideoStages(
            Context context, DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput,
            long durationUs, FinalizationProgress progress) throws IOException {
        DcamInterruptedMp4Finalizer.CleanResult cleanResult = patchCleanVideoContainer(
                mediaFile, recordingOutput, durationUs);
        long patched = System.nanoTime();
        if (recordingOutput != null) {
            progress.moveTo("recording output finalization");
            recordingOutput.finish();
            recordingOutput.close();
        }
        long outputFinalized = System.nanoTime();
        progress.moveTo("publication rename");
        File finalFile = publishCleanSavedNow(context, mediaFile, false);
        return new CleanVideoFinalization(
                cleanResult, patched, outputFinalized, finalFile, System.nanoTime());
    }

    private DcamInterruptedMp4Finalizer.CleanResult patchCleanVideoContainer(
            DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput, long durationUs)
            throws IOException {
        if (recordingOutput == null) {
            return mp4Finalizer.finalizeCleanTimed(mediaFile.getFile(), durationUs);
        }
        if (!recordingOutput.isOpen()) {
            throw new IOException("Recording output closed before finalization.");
        }
        return mp4Finalizer.finalizeCleanTimed(recordingOutput.media(), durationUs);
    }

    private void failCleanVideoFinalization(
            DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput, String stage,
            FinalizationCallback callback, Exception failure) {
        closeRecordingOutputAfterFailure(recordingOutput, failure);
        logger.error(LogCategory.STORAGE, "media_finalization_failed", null, "Clean MP4 finalization failed for staged media '"
                + mediaFile.getFileName() + "'. Failed stage: " + stage + ". "
                + cleanVideoRecoveryDetail(mediaFile)
                + "Reason: " + message(failure) + ".", failure);
        updateMediaState(mediaFile.getFile(), DcamMediaFileState.RECOVERY_REQUIRED);
        mediaReservation.releaseActiveStaging(mediaFile.getFile());
        callback.onFailure(failure);
    }

    private static String cleanVideoRecoveryDetail(DcamMediaFile mediaFile) {
        return mediaFile.getFile().isFile()
                ? "File remains in Temp for recovery. "
                : "Staged file is no longer in Temp; recovery must reconcile final publication. ";
    }

    private void completeCleanVideoFinalization(
            DcamMediaFile mediaFile, CleanVideoFinalization finalization,
            FinalizationCallback callback, long started) {
        DcamInterruptedMp4Finalizer.CleanResult cleanResult = finalization.cleanResult();
        logger.info(LogCategory.STORAGE, "media_finalization_completed", "Clean MP4 finalization completed for staged media '"
                + mediaFile.getFileName() + "'. Duration: " + cleanResult.durationMillis()
                + " ms. Container patch: " + elapsedMillis(started, finalization.patchedNanos())
                + " ms. Metadata write: " + cleanResult.metadataPatchMillis()
                + " ms. Durability sync: " + cleanResult.durabilitySyncMillis()
                + " ms. Seek index source: "
                + (cleanResult.writerSeekIndexUsed() ? "writer metadata." : "final scan.")
                + " Recording output finalization: "
                + elapsedMillis(finalization.patchedNanos(), finalization.outputFinalizedNanos())
                + " ms. Publication rename: "
                + elapsedMillis(finalization.outputFinalizedNanos(), finalization.publishedNanos())
                + " ms. Total: " + elapsedMillis(started, finalization.publishedNanos()) + " ms.");
        mediaReservation.releaseActiveStaging(mediaFile.getFile());
        callback.onSuccess(finalization.finalFile());
        finalizer.cleanupCleanMedia(mediaFile);
        if (shouldCreateVideoMd5(mediaFile.getType())) {
            createMd5WithRetry(finalization.finalFile());
        }
    }
    @Override public File finalizeOpenAudioM4aNow(
            Context context, DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput,
            long durationUs) throws IOException {
        Objects.requireNonNull(mediaFile, MEDIA_FILE_PARAMETER);
        Objects.requireNonNull(recordingOutput, "recordingOutput");
        if (mediaFile.getType() != DcamFileType.AUDIO_M4A) {
            throw new IOException("Open audio M4A finalization requires AUDIO_M4A media.");
        }
        long started = System.nanoTime();
        FinalizationProgress progress = new FinalizationProgress("container finalization");
        updateMediaState(mediaFile.getFile(), DcamMediaFileState.FINALIZING);
        try {
            AudioM4aContainerFinalization container = finalizeAudioM4aContainer(
                    mediaFile, recordingOutput, durationUs);
            long containerFinalized = System.nanoTime();
            progress.moveTo("recording output finalization");
            recordingOutput.finish();
            recordingOutput.close();
            long outputFinalized = System.nanoTime();
            progress.moveTo(STAGE_PUBLICATION);
            File finalFile = publishCleanSavedNow(context, mediaFile, false);
            long published = System.nanoTime();
            finalizer.cleanupCleanMedia(mediaFile);
            logger.info(LogCategory.STORAGE, "media_finalization_completed", "Audio M4A finalization completed for staged media '"
                    + mediaFile.getFileName() + "'. Duration: " + container.durationMillis()
                    + " ms. Container path: " + container.path() + ". Container finalization: "
                    + elapsedMillis(started, containerFinalized) + " ms. " + container.detail()
                    + " Recording output finalization: "
                    + elapsedMillis(containerFinalized, outputFinalized)
                    + " ms. Publication rename: " + elapsedMillis(outputFinalized, published)
                    + " ms. Total: " + elapsedMillis(started, published) + " ms.");
            return finalFile;
        } catch (Exception failure) {
            closeRecordingOutputAfterFailure(recordingOutput, failure);
            logger.error(LogCategory.STORAGE, "media_finalization_failed", null, "Audio M4A finalization failed for staged media '"
                    + mediaFile.getFileName() + "'. Failed stage: " + progress.stage() + ". "
                    + (mediaFile.getFile().isFile()
                            ? "File remains in Temp for startup recovery. "
                            : "Staged file is no longer in Temp. ")
                    + "Reason: " + message(failure) + ".", failure);
            updateMediaState(mediaFile.getFile(), DcamMediaFileState.RECOVERY_REQUIRED);
            if (failure instanceof IOException ioFailure) throw ioFailure;
            throw new IOException("Audio M4A finalization failed: " + message(failure), failure);
        } finally {
            mediaReservation.releaseActiveStaging(mediaFile.getFile());
        }
    }

    private AudioM4aContainerFinalization finalizeAudioM4aContainer(
            DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput, long durationUs)
            throws IOException {
        if (!recordingOutput.isOpen()) {
            throw new IOException("Recording output closed before audio M4A finalization.");
        }
        try {
            return patchCleanAudioM4a(recordingOutput, durationUs);
        } catch (IOException cleanFailure) {
            return recoverAudioM4aContainer(mediaFile, recordingOutput, cleanFailure);
        }
    }

    private AudioM4aContainerFinalization patchCleanAudioM4a(
            DcamRecordingOutput recordingOutput, long durationUs) throws IOException {
        if (durationUs <= 0L) throw new IOException("Audio M4A duration is unavailable.");
        DcamInterruptedMp4Finalizer.CleanResult cleanResult =
                mp4Finalizer.finalizeCleanTimed(recordingOutput.media(), durationUs);
        String path = cleanResult.writerSeekIndexUsed() ? "writer metadata" : "final scan";
        String detail = "Metadata patch: " + cleanResult.metadataPatchMillis()
                + " ms. Durability sync: " + cleanResult.durabilitySyncMillis() + " ms.";
        return new AudioM4aContainerFinalization(cleanResult.durationMillis(), path, detail);
    }

    private AudioM4aContainerFinalization recoverAudioM4aContainer(
            DcamMediaFile mediaFile, DcamRecordingOutput recordingOutput, IOException cleanFailure)
            throws IOException {
        logger.warn(LogCategory.STORAGE, "media_finalization_retrying", null, "Fast audio M4A finalization could not complete for staged media '"
                + mediaFile.getFileName() + "'. Retrying with recovery scan.", cleanFailure);
        DcamInterruptedMp4Finalizer.Result recoveryResult;
        try {
            recoveryResult = mp4Finalizer.finalizeInterrupted(recordingOutput.media());
        } catch (IOException recoveryFailure) {
            recoveryFailure.addSuppressed(cleanFailure);
            throw recoveryFailure;
        }
        if (recoveryResult.durationMillis() <= 0L) {
            IOException recoveryFailure = new IOException(
                    "Audio M4A duration finalization failed. " + recoveryResult.detail());
            recoveryFailure.addSuppressed(cleanFailure);
            throw recoveryFailure;
        }
        return new AudioM4aContainerFinalization(
                recoveryResult.durationMillis(), "recovery scan", recoveryResult.detail());
    }
    @Override public File finalizeSavedNow(Context context, DcamMediaFile mediaFile)
            throws IOException {
        try {
            updateMediaState(mediaFile.getFile(), DcamMediaFileState.FINALIZING);
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
        } catch (IOException | RuntimeException failure) {
            updateMediaState(mediaFile.getFile(), DcamMediaFileState.RECOVERY_REQUIRED);
            throw failure;
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
            verifySegmentedPublishedEnvelope(mediaFile);
        }
        File finalFile = finalizer.finalizeCleanMedia(mediaFile);
        try {
            return completePublication(context, mediaFile, finalFile);
        } catch (RuntimeException failure) {
            logger.warn(LogCategory.STORAGE, "unspecified", null, "Final media publication completed, but post-publication notification failed for '"
                    + mediaFile.getFileName() + "'.", failure);
            return finalFile;
        }
    }

    private void verifySegmentedPublishedEnvelope(DcamMediaFile mediaFile) throws IOException {
        try (SegmentedAesGcmMediaStore verified = SegmentedAesGcmMediaStore.openPublished(
                mediaFile.getFile(), mediaEncryptionPassword.get())) {
            // Opening validates the completed encrypted envelope before clean publication.
        }
    }

    private static void closeRecordingOutputAfterFailure(
            DcamRecordingOutput recordingOutput, Exception failure) {
        if (recordingOutput == null || !recordingOutput.isOpen()) return;
        try {
            recordingOutput.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private File completePublication(Context context, DcamMediaFile mediaFile, File finalFile) {
        DcamEncryptionJournal.clear(mediaFile);
        updateMediaState(finalFile, shouldCreateVideoMd5(mediaFile.getType())
                ? DcamMediaFileState.FINALIZED : DcamMediaFileState.BDMA_READY);
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
        logger.info(LogCategory.STORAGE, "media_finalization_completed", "Finalized staged MP4 container '" + mediaFile.getFileName() + "'. "
                + result.detail());
    }

    private static boolean usesFragmentedMp4Container(DcamMediaFile mediaFile) {
        return mediaFile.getType().usesFragmentedMp4Container();
    }

    private boolean shouldCreateVideoMd5(DcamFileType type) {
        return type.isVideo() && createVideoMd5 != null && createVideoMd5.getAsBoolean();
    }

    private void updateMediaState(File mediaFile, DcamMediaFileState state) {
        mediaStateStore.update(
                mediaFile.getName(), DcamStorage.storageRootFor(mediaFile), state);
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
                updateMediaState(file, DcamMediaFileState.BDMA_READY);
            } catch (Exception failure) {
                try { md5RetryQueue.add(file); }
                catch (IOException queueFailure) {
                    failure.addSuppressed(queueFailure);
                    logger.error(LogCategory.STORAGE, "mp4_md5_failed", "MD5_RETRY_QUEUE_UNAVAILABLE", "Video MD5 failed; retry queue unavailable: "
                            + file.getAbsolutePath(), failure);
                    return;
                }
                logger.error(LogCategory.STORAGE, "mp4_md5_pending", null, "Video MD5 failed; queued for retry: " + file.getAbsolutePath(), failure);
                if (context != null) {
                    try {
                        scheduleMd5RetryNow(context);
                    } catch (RuntimeException schedulingFailure) {
                        logger.error(LogCategory.STORAGE, "mp4_md5_pending", "MD5_RETRY_SCHEDULING_FAILED", "Video MD5 retry scheduling failed: "
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
                logger.info(LogCategory.STORAGE, "unspecified", "Staged-media recovery skipped active capture files. Count: "
                        + activeStagingPaths.size() + ".");
            }
            File[][] candidates = recovery.snapshot(activeStagingPaths);
            RECOVERY_EXECUTOR.execute(() -> callback.onComplete(
                    recoverSafely(() -> recoverAndReconcile(recovery, candidates))));
        } catch (RuntimeException failure) {
            callback.onComplete(StagedMediaRecoveryReport.failed(failure));
        }
    }

    private StagedMediaRecoveryReport recoverAndReconcile(
            DcamStagedMediaRecovery recovery, File[][] candidates) {
        StagedMediaRecoveryReport report = recovery.recover(candidates);
        var trackedStates = mediaStateStore.findAll();
        var activeStagingPaths = mediaReservation.activeStagingPaths();
        recovery.reconcile(trackedStates, activeStagingPaths);
        return report;
    }


    private DcamStagedMediaRecovery stagedRecovery() {
        return new DcamStagedMediaRecovery(
                storage, finalizer, new AndroidDcamMediaValidator(),
                new DcamInterruptedMp4Finalizer(), createVideoMd5,
                mediaEncryptionPassword, this::createMd5WithRetry, logger, mediaStateStore);
    }

    private static String captureStorageReason(CaptureStorageCheck check) {
        if (check.isPreparing()) return "STORAGE_PREPARING";
        if (check.isUnavailable()) return "STORAGE_UNAVAILABLE";
        if (check.isLowCapacity()) return "INSUFFICIENT_STORAGE";
        return null;
    }

    private static long elapsedMillis(long startedNanos, long completedNanos) {
        return Math.max(0L, completedNanos - startedNanos) / 1_000_000L;
    }

    private static final class FinalizationProgress {
        private String stage;

        private FinalizationProgress(String stage) {
            this.stage = stage;
        }

        private String stage() { return stage; }

        private void moveTo(String nextStage) { stage = nextStage; }
    }

    private record CleanVideoFinalization(
            DcamInterruptedMp4Finalizer.CleanResult cleanResult, long patchedNanos,
            long outputFinalizedNanos, File finalFile, long publishedNanos) {}

    private record AudioM4aContainerFinalization(long durationMillis, String path, String detail) {}

    static StagedMediaRecoveryReport recoverSafely(
            Supplier<StagedMediaRecoveryReport> recovery) {
        try {
            return recovery.get();
        } catch (RuntimeException failure) {
            return StagedMediaRecoveryReport.failed(failure);
        }
    }

}
