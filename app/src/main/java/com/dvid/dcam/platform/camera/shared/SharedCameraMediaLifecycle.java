package com.dvid.dcam.platform.camera.shared;

import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.platform.storage.DcamMediaFile;
import com.dvid.dcam.platform.storage.DcamRecordingOutput;
import com.dvid.dcam.platform.storage.SegmentedAesGcmJpegOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

public interface SharedCameraMediaLifecycle {
    record RecordingCapture(
            RecordingMode mode,
            DcamMediaFile mediaFile,
            boolean encrypted,
            long fileSizeLimitBytes,
            DcamRecordingOutput recordingOutput) {
        public RecordingCapture(
                RecordingMode mode, DcamMediaFile mediaFile, boolean encrypted,
                long fileSizeLimitBytes) {
            this(mode, mediaFile, encrypted, fileSizeLimitBytes, null);
        }

        public RecordingCapture {
            mode = Objects.requireNonNull(mode, "mode");
            mediaFile = Objects.requireNonNull(mediaFile, "mediaFile");
            if (fileSizeLimitBytes < 0L) {
                throw new IllegalArgumentException("fileSizeLimitBytes must be non-negative");
            }
        }

        public File outputFile() {
            return mediaFile.getFile();
        }
    }
    record PhotoCapture(
            DcamMediaFile mediaFile,
            boolean encrypted,
            SegmentedAesGcmJpegOutput segmentedAesGcmOutput) {
        public PhotoCapture(DcamMediaFile mediaFile, boolean encrypted) {
            this(mediaFile, encrypted, null);
        }

        public PhotoCapture {
            mediaFile = Objects.requireNonNull(mediaFile, "mediaFile");
            if (encrypted != (segmentedAesGcmOutput != null)) {
                throw new IllegalArgumentException(
                        "Encrypted photo media requires Segmented AES-GCM JPEG output.");
            }
        }

        public File outputFile() {
            return mediaFile.getFile();
        }

        public void discard() {
            if (segmentedAesGcmOutput == null) deletePlainPhotoQuietly(outputFile());
            else segmentedAesGcmOutput.discard();
        }

        private static void deletePlainPhotoQuietly(File outputFile) {
            try {
                Files.delete(outputFile.toPath());
            } catch (IOException ignored) {
                // Failed or cancelled capture cleanup must not replace its original outcome.
            }
        }
    }

    interface Completion {
        void onSuccess(File finalFile);
        void onFailure(Exception failure);
    }

    final class PreparationException extends Exception {
        private final String operation;
        private final boolean retryable;
        private final boolean unavailable;
        private final String terminalMessage;

        public PreparationException(String operation, String message) {
            this(operation, message, null, false, false, message);
        }

        public PreparationException(String operation, String message, Throwable cause) {
            this(operation, message, cause, false, false, message);
        }

        private PreparationException(
                String operation, String message, Throwable cause, boolean retryable,
                boolean unavailable, String terminalMessage) {
            super(message, cause);
            if (operation == null || operation.isBlank()) {
                throw new IllegalArgumentException("operation is required");
            }
            this.operation = operation;
            this.retryable = retryable;
            this.unavailable = unavailable;
            this.terminalMessage = Objects.requireNonNull(terminalMessage, "terminalMessage");
        }

        public static PreparationException retryable(
                String operation, String message, String terminalMessage, Throwable cause) {
            return new PreparationException(
                    operation, message, cause, true, false, terminalMessage);
        }

        public static PreparationException unavailable(String operation, String message) {
            return new PreparationException(operation, message, null, false, true, message);
        }

        public String operation() {
            return operation;
        }

        public boolean retryable() {
            return retryable;
        }

        public boolean unavailable() {
            return unavailable;
        }

        public String terminalMessage() {
            return terminalMessage;
        }
    }

    RecordingCapture prepareRecording(
            RecordingMode mode, long bitrateBitsPerSecond) throws PreparationException;

    default void requirePhotoStorage() throws PreparationException {}

    PhotoCapture preparePhoto() throws PreparationException;

    void abortRecordingStart(RecordingCapture capture);

    void failRecording(RecordingCapture capture);

    void finalizeRecording(RecordingCapture capture, long durationUs, Completion completion);

    void finalizePhoto(PhotoCapture capture, Completion completion);
}