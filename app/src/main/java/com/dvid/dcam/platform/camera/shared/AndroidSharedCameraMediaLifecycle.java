package com.dvid.dcam.platform.camera.shared;

import android.content.Context;
import android.text.format.Formatter;
import com.dvid.dcam.R;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import com.dvid.dcam.platform.recording.RecordingForegroundService;
import com.dvid.dcam.platform.storage.DcamFileType;
import com.dvid.dcam.platform.storage.DcamMediaFile;
import com.dvid.dcam.platform.storage.DcamMediaOutput;
import com.dvid.dcam.platform.storage.DcamRecordingOutput;
import com.dvid.dcam.platform.storage.SegmentedAesGcmJpegOutput;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class AndroidSharedCameraMediaLifecycle implements SharedCameraMediaLifecycle {
    private static final String STORAGE = "Storage";
    private static final String PHOTO = "Photo";
    private static final String CAPTURE = "capture";

    private final Context context;
    private final DcamMediaOutput mediaOutput;
    private final Supplier<String> deviceSerialNumber;
    private final Supplier<String> operatorFileUserId;
    private final BooleanSupplier mediaEncryptionEnabled;
    private final Logger logger;

    public AndroidSharedCameraMediaLifecycle(
            Context context,
            DcamMediaOutput mediaOutput,
            Supplier<String> deviceSerialNumber,
            Supplier<String> operatorFileUserId,
            BooleanSupplier mediaEncryptionEnabled,
            Logger logger) {
        Context applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
        this.mediaOutput = Objects.requireNonNull(mediaOutput, "mediaOutput");
        this.deviceSerialNumber = Objects.requireNonNull(
                deviceSerialNumber, "deviceSerialNumber");
        this.operatorFileUserId = Objects.requireNonNull(
                operatorFileUserId, "operatorFileUserId");
        this.mediaEncryptionEnabled = Objects.requireNonNull(
                mediaEncryptionEnabled, "mediaEncryptionEnabled");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override public RecordingCapture prepareRecording(
            RecordingMode mode, long bitrateBitsPerSecond)
            throws PreparationException {
        Objects.requireNonNull(mode, "mode");
        if (bitrateBitsPerSecond <= 0L) {
            throw new PreparationException("Recording", "Recording bitrate is unavailable");
        }
        Identity identity = identity("Recording");
        requireRecordingStorage(bitrateBitsPerSecond);
        long fileSizeLimitBytes = mediaOutput.recordingFileSizeLimit();
        boolean encrypted = mediaEncryptionEnabled.getAsBoolean();
        DcamFileType type = recordingFileType(mode);
        DcamMediaFile mediaFile = null;
        DcamRecordingOutput recordingOutput = null;
        try {
            mediaFile = mediaOutput.mediaFile(type, identity.deviceSerial,
                    identity.operatorId, encrypted);
            mediaOutput.prepareVideoFile(mediaFile);
            recordingOutput = mediaOutput.openVideoOutput(mediaFile);
        } catch (IOException | RuntimeException error) {
            closeRecordingOutput(recordingOutput);
            if (mediaFile != null) {
                mediaFile.getFile().delete();
                mediaOutput.releaseMediaReservation(mediaFile);
            }
            CaptureStorageCheck reservationCheck =
                    mediaOutput.checkRecordingReady(bitrateBitsPerSecond);
            PreparationException reservationPreparation = recordingStoragePreparationException(
                    reservationCheck,
                    mediaOutput.isExternalStorageRequested(),
                    lowStorageRecordingBlockedMessage(reservationCheck),
                    context.getString(R.string.sd_card_preparing),
                    context.getString(R.string.sd_card_unavailable), error);
            if (reservationPreparation != null) throw reservationPreparation;
            throw new PreparationException(STORAGE,
                    "Could not prepare recording file", error);
        }
        if (!RecordingForegroundService.startVideo(
                context, mediaFile.getFileName(), mode)) {
            closeRecordingOutput(recordingOutput);
            mediaFile.getFile().delete();
            mediaOutput.releaseMediaReservation(mediaFile);
            throw new PreparationException("Recording",
                    "Could not start recording foreground service");
        }
        return new RecordingCapture(
                mode, mediaFile, encrypted, fileSizeLimitBytes, recordingOutput);
    }
    static PreparationException recordingStoragePreparationException(
            CaptureStorageCheck storageCheck,
            boolean externalStorageRequested,
            String lowStorageMessage,
            String preparingMessage,
            String unavailableMessage,
            Throwable cause) {
        if (storageCheck.isLowCapacity()) {
            return new PreparationException(STORAGE, lowStorageMessage, cause);
        }
        return externalStoragePreparationException(storageCheck, externalStorageRequested,
                preparingMessage, unavailableMessage, cause);
    }

    static PreparationException externalStoragePreparationException(
            CaptureStorageCheck storageCheck,
            boolean externalStorageRequested,
            String preparingMessage,
            String unavailableMessage,
            Throwable cause) {
        if (!externalStorageRequested) return null;
        if (storageCheck.isPreparing()) {
            return PreparationException.retryable(
                    STORAGE, preparingMessage, unavailableMessage, cause);
        }
        if (storageCheck.isUnavailable()) {
            return PreparationException.unavailable(STORAGE, unavailableMessage);
        }
        if (isTransientExternalReservationFailure(cause)) {
            return PreparationException.retryable(
                    STORAGE, preparingMessage, unavailableMessage, cause);
        }
        return null;
    }

    private static boolean isTransientExternalReservationFailure(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof FileSystemException failure
                    && "Operation not permitted".equals(failure.getReason())) {
                return true;
            }
        }
        return false;
    }

    @Override public void requirePhotoStorage() throws PreparationException {
        identity(PHOTO);
        requireStorage();
    }

    @Override public PhotoCapture preparePhoto() throws PreparationException {
        Identity identity = identity(PHOTO);
        requireStorage();
        boolean encrypted = mediaEncryptionEnabled.getAsBoolean();
        DcamMediaFile mediaFile = null;
        try {
            mediaFile = mediaOutput.mediaFile(DcamFileType.IMAGE,
                    identity.deviceSerial, identity.operatorId, encrypted);
            mediaOutput.prepareImageFile(mediaFile);
            SegmentedAesGcmJpegOutput encryptedOutput = encrypted
                    ? mediaOutput.openSegmentedAesGcmJpegOutput(mediaFile) : null;
            return new PhotoCapture(mediaFile, encrypted, encryptedOutput);
        } catch (IOException | RuntimeException error) {
            if (mediaFile != null) {
                mediaFile.getFile().delete();
                mediaOutput.releaseMediaReservation(mediaFile);
            }
            PreparationException reservationPreparation = externalStoragePreparationException(
                    mediaOutput.checkCaptureReady(), mediaOutput.isExternalStorageRequested(),
                    context.getString(R.string.sd_card_preparing),
                    context.getString(R.string.sd_card_unavailable), error);
            if (reservationPreparation != null) throw reservationPreparation;
            throw new PreparationException(STORAGE,
                    "Could not prepare image file", error);
        }
    }

    @Override public void abortRecordingStart(RecordingCapture capture) {
        Objects.requireNonNull(capture, CAPTURE);
        RecordingForegroundService.stopVideo(context);
        closeRecordingOutput(capture.recordingOutput());
        capture.outputFile().delete();
        mediaOutput.releaseMediaReservation(capture.mediaFile());
    }

    @Override public void failRecording(RecordingCapture capture) {
        Objects.requireNonNull(capture, CAPTURE);
        RecordingForegroundService.updateVideoMode(context, capture.mode());
        RecordingForegroundService.stopVideo(context);
        DcamRecordingOutput output = capture.recordingOutput();
        boolean outputOpenBeforeClose = output != null && output.isOpen();
        logger.info("Fail recording '" + capture.mediaFile().getFileName()
                + "': closing its staged output. Output open before close: "
                + outputOpenBeforeClose + ".");
        closeRecordingOutput(output);
        logger.info("Fail recording '" + capture.mediaFile().getFileName()
                + "': staged output cleanup completed. Output open after close: "
                + (output != null && output.isOpen()) + ".");
        mediaOutput.releaseMediaReservation(capture.mediaFile());
    }

    @Override public void finalizeRecording(
            RecordingCapture capture, long durationUs, Completion completion) {
        Objects.requireNonNull(capture, CAPTURE);
        Objects.requireNonNull(completion, "completion");
        RecordingForegroundService.updateVideoMode(context, capture.mode());
        RecordingForegroundService.markVideoFinalizing(context);
        DcamMediaOutput.FinalizationCallback callback = new DcamMediaOutput.FinalizationCallback() {
            @Override public void onSuccess(java.io.File finalFile) {
                RecordingForegroundService.completeVideoFinalization(context);
                completion.onSuccess(finalFile);
            }

            @Override public void onFailure(Exception failure) {
                RecordingForegroundService.completeVideoFinalization(context);
                completion.onFailure(failure);
            }
        };
        mediaOutput.finalizeCleanVideo(context, capture.mediaFile(),
                capture.recordingOutput(), durationUs, callback);
    }

    @Override public void finalizePhoto(PhotoCapture capture, Completion completion) {
        Objects.requireNonNull(capture, CAPTURE);
        Objects.requireNonNull(completion, "completion");
        mediaOutput.finalizeSaved(context, capture.mediaFile(), null,
                new DcamMediaOutput.FinalizationCallback() {
            @Override public void onSuccess(java.io.File finalFile) {
                completion.onSuccess(finalFile);
            }

            @Override public void onFailure(Exception failure) {
                completion.onFailure(failure);
            }
        });
    }

    private static void closeRecordingOutput(DcamRecordingOutput output) {
        if (output == null || !output.isOpen()) return;
        try {
            output.close();
        } catch (IOException ignored) {
            // The caller must still release the media reservation after a failed output close.
        }
    }
    private static DcamFileType recordingFileType(RecordingMode mode) {
        return mode == RecordingMode.IMP ? DcamFileType.IMP : DcamFileType.VIDEO;
    }

    private Identity identity(String operation) throws PreparationException {
        String operatorId = operatorFileUserId.get();
        if (operatorId == null || operatorId.isBlank()) {
            throw new PreparationException(operation, "Operator login required");
        }
        String serial = deviceSerialNumber.get();
        if (serial == null || serial.isBlank()) {
            throw new PreparationException(operation, "Device serial required");
        }
        return new Identity(serial, operatorId);
    }

    private String lowStorageRecordingBlockedMessage(CaptureStorageCheck check) {
        return context.getString(R.string.low_storage_recording_blocked,
                Formatter.formatFileSize(context, check.getRequiredBytes()));
    }

    private void requireRecordingStorage(long bitrateBitsPerSecond)
            throws PreparationException {
        CaptureStorageCheck check = mediaOutput.checkRecordingReady(bitrateBitsPerSecond);
        if (check.isLowCapacity()) {
            throw new PreparationException(STORAGE,
                    lowStorageRecordingBlockedMessage(check));
        }
        requireStorage(check);
    }

    private void requireStorage() throws PreparationException {
        CaptureStorageCheck check = mediaOutput.checkCaptureReady();
        requireStorage(check);
    }

    private void requireStorage(CaptureStorageCheck check)
            throws PreparationException {
        if (check.isReady()) return;
        if (check.isPreparing()) {
            throw PreparationException.retryable(STORAGE,
                    context.getString(R.string.sd_card_preparing),
                    context.getString(R.string.sd_card_unavailable), null);
        }
        String reason = check.getReason();
        if (check.isUnavailable() && mediaOutput.isExternalStorageRequested()) {
            throw PreparationException.unavailable(STORAGE,
                    context.getString(R.string.sd_card_unavailable));
        }
        throw new PreparationException(STORAGE, reason
                + " (available=" + check.getAvailableBytes()
                + ", required=" + check.getRequiredBytes() + ")");
    }

    private record Identity(String deviceSerial, String operatorId) {}

}
