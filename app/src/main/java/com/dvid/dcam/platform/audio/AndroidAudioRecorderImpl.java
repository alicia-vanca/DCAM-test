package com.dvid.dcam.platform.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.MediaFormatUtil;
import androidx.media3.muxer.MuxerUtil;
import com.dvid.dcam.R;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.application.port.AudioRecorder;
import com.dvid.dcam.feature.capture.domain.AudioCaptureSettings;
import com.dvid.dcam.feature.capture.domain.AudioFileFormat;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import com.dvid.dcam.platform.recording.RecordingForegroundService;
import com.dvid.dcam.platform.storage.DcamAudioM4aWriter;
import com.dvid.dcam.platform.storage.DcamFileType;
import com.dvid.dcam.platform.storage.DcamMediaFile;
import com.dvid.dcam.platform.storage.DcamMediaOutput;
import com.dvid.dcam.platform.storage.DcamRecordingOutput;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class AndroidAudioRecorderImpl implements AudioRecorder {
    private static final String AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int AUDIO_BIT_RATE = AudioCaptureSettings.BIT_RATE_BPS;
    private static final int ADTS_HEADER_SIZE = 7;
    private static final long ENCODER_STOP_TIMEOUT_MILLIS = 2_000L;

    private final Context context;
    private final DcamMediaOutput mediaOutput;
    private final Logger log;
    private final SharedMicrophoneCapture microphoneCapture;
    private final BooleanSupplier mediaEncryptionEnabled;
    private final Supplier<String> operatorFileUserId;
    private final Supplier<String> deviceSerialNumber;
    private final Supplier<GpsCoordinate> captureLocation;
    private final Supplier<AudioFileFormat> audioFileFormat;
    private volatile Thread encoderThread;
    private volatile boolean stopRequested;
    private volatile Throwable encoderFailure;
    private volatile MediaCodec encoder;
    private volatile SharedMicrophoneCapture.Subscription microphoneSubscription;
    private DcamMediaFile outputMediaFile;
    private File outputFile;
    private DcamRecordingOutput recordingOutput;
    private DcamAudioM4aWriter m4aWriter;
    private AudioFileFormat recordingFormat;
    private long aacSampleCount;
    private long checkpointedOutputBytes;
    private ScheduledExecutorService durabilitySync;
    private boolean outputEncrypted;
    private Long recordingStartedAtMillis;
    private volatile long recordingDurationUs;
    private volatile boolean finalizationInFlight;

    public AndroidAudioRecorderImpl(Context context, DcamMediaOutput mediaOutput, Logger log,
                                    BooleanSupplier mediaEncryptionEnabled,
                                    Supplier<String> operatorFileUserId,
                                    Supplier<String> deviceSerialNumber,
                                    Supplier<GpsCoordinate> captureLocation) {
        this(context, mediaOutput, log, mediaEncryptionEnabled, operatorFileUserId,
                deviceSerialNumber, captureLocation, () -> AudioFileFormat.DEFAULT);
    }

    public AndroidAudioRecorderImpl(Context context, DcamMediaOutput mediaOutput, Logger log,
                                    BooleanSupplier mediaEncryptionEnabled,
                                    Supplier<String> operatorFileUserId,
                                    Supplier<String> deviceSerialNumber,
                                    Supplier<GpsCoordinate> captureLocation,
                                    Supplier<AudioFileFormat> audioFileFormat) {
        this.context = context;
        this.mediaOutput = mediaOutput;
        this.log = log;
        microphoneCapture = SharedMicrophoneCapture.process(log);
        this.mediaEncryptionEnabled = Objects.requireNonNull(
                mediaEncryptionEnabled, "mediaEncryptionEnabled");
        this.operatorFileUserId = Objects.requireNonNull(operatorFileUserId, "operatorFileUserId");
        this.deviceSerialNumber = Objects.requireNonNull(deviceSerialNumber, "deviceSerialNumber");
        this.captureLocation = Objects.requireNonNull(captureLocation, "captureLocation");
        this.audioFileFormat = Objects.requireNonNull(audioFileFormat, "audioFileFormat");
    }

    @Override public String toggle() {
        if (encoderThread != null) return stopRecording();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            RecordingForegroundService.stopAudio(context);
            log.warn("Audio permission missing", null);
            return null;
        }

        try {
            String fileUserId = operatorFileUserId.get();
            if (fileUserId == null || fileUserId.isBlank()) {
                RecordingForegroundService.stopAudio(context);
                log.warn("Audio start ignored: operator login required", null);
                return null;
            }
            CaptureStorageCheck storageCheck = mediaOutput.checkCaptureReady();
            AudioRecorder.PreparationException storagePreparation =
                    externalStoragePreparationException(storageCheck,
                            mediaOutput.isExternalStorageRequested(),
                            context.getString(R.string.sd_card_preparing),
                            context.getString(R.string.sd_card_unavailable), null);
            if (storagePreparation != null) {
                RecordingForegroundService.stopAudio(context);
                throw storagePreparation;
            }
            if (!storageCheck.isReady()) {
                RecordingForegroundService.stopAudio(context);
                log.warn("Audio start rejected: " + storageCheck.getReason(), null);
                return null;
            }
            String cameraId = currentDeviceSerial();
            if (cameraId == null) {
                RecordingForegroundService.stopAudio(context);
                log.warn("Audio start rejected: device serial required", null);
                return null;
            }
            outputEncrypted = mediaEncryptionEnabled.getAsBoolean();
            recordingFormat = Objects.requireNonNull(audioFileFormat.get(), "audioFileFormat");
            try {
                outputMediaFile = mediaOutput.durableAudioMediaFile(
                        outputFileType(recordingFormat), cameraId, fileUserId, outputEncrypted);
                recordingOutput = mediaOutput.openAudioOutput(outputMediaFile);
                outputFile = recordingOutput.file();
                if (usesM4aContainer()) {
                    m4aWriter = new DcamAudioM4aWriter(recordingOutput, captureLocation, log);
                }
                aacSampleCount = 0L;
                checkpointedOutputBytes = 0L;
                recordingDurationUs = 0L;
            } catch (IOException error) {
                AudioRecorder.PreparationException reservationPreparation =
                        externalStoragePreparationException(mediaOutput.checkCaptureReady(),
                                mediaOutput.isExternalStorageRequested(),
                                context.getString(R.string.sd_card_preparing),
                                context.getString(R.string.sd_card_unavailable), error);
                if (reservationPreparation != null) throw reservationPreparation;
                throw error;
            }
            if (!startProtectedRecording(
                    () -> RecordingForegroundService.startAudio(
                            context, outputMediaFile.getFileName()),
                    this::startEncoder)) {
                throw new IllegalStateException("Could not start audio foreground protection");
            }
            if (!usesM4aContainer()) startDurabilitySync();
            recordingStartedAtMillis = System.currentTimeMillis();
            log.info("Audio started: " + outputFile.getAbsolutePath());
            return outputMediaFile.getFileName();
        } catch (Exception error) {
            DcamMediaFile failed = outputMediaFile;
            RecordingForegroundService.stopAudio(context);
            stopDurabilitySync();
            stopEncoder();
            boolean empty = !hasAudioSamples();
            closeM4aWriter();
            closeOutput();
            deleteEmptyStagedAudio(failed, empty);
            releaseFailedMediaReservation(failed);
            clearRecordingState();
            if (error instanceof AudioRecorder.PreparationException preparation) {
                throw preparation;
            }
            log.error("Audio failed", error);
            return null;
        }
    }

    static AudioRecorder.PreparationException externalStoragePreparationException(
            CaptureStorageCheck storageCheck,
            boolean externalStorageRequested,
            String preparingMessage,
            String unavailableMessage,
            Throwable cause) {
        if (!externalStorageRequested) return null;
        if (storageCheck.isPreparing()) {
            return AudioRecorder.PreparationException.retryable(
                    preparingMessage, unavailableMessage, cause);
        }
        if (storageCheck.isUnavailable()) {
            return AudioRecorder.PreparationException.unavailable(unavailableMessage);
        }
        if (isTransientExternalReservationFailure(cause)) {
            return AudioRecorder.PreparationException.retryable(
                    preparingMessage, unavailableMessage, cause);
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

    private String stopRecording() {
        long stopStarted = System.nanoTime();
        finalizationInFlight = true;
        RecordingForegroundService.markAudioFinalizing(context);
        DcamMediaFile completed = outputMediaFile;
        boolean encrypted = outputEncrypted;
        String output = completed == null ? null : completed.getFileName();
        try {
            if (!usesM4aContainer()) stopDurabilitySync();
            boolean encoderStopped;
            try {
                encoderStopped = stopEncoder();
            } finally {
                if (!usesM4aContainer()) syncOutput();
            }
            long encoderStoppedAt = System.nanoTime();
            if (!encoderStopped) {
                log.error("Audio stop failed",
                        new IllegalStateException("audio_encoder_stop_timeout"));
                output = null;
            } else if (encoderFailure != null) {
                log.error("Audio stop failed", encoderFailure);
                output = null;
            }
            boolean empty = !hasAudioSamples();
            if (output == null || completed == null || empty) {
                closeM4aWriter();
                closeOutput();
                deleteEmptyStagedAudio(completed, empty);
                releaseFailedMediaReservation(completed);
                clearRecordingState();
                return null;
            }
            try {
                if (usesM4aContainer()) {
                    finishM4aWriter();
                    long writerClosedAt = System.nanoTime();
                    DcamRecordingOutput completedOutput = recordingOutput;
                    if (completedOutput == null) throw new IOException("audio_output_closed");
                    recordingOutput = null;
                    mediaOutput.finalizeOpenAudioM4aNow(
                            context, completed, completedOutput, recordingDurationUs);
                    long publishedAt = System.nanoTime();
                    clearRecordingState();
                    log.info((encrypted ? "Encrypted audio" : "Audio")
                            + " stop completed for '" + output + "'. Encoder shutdown: "
                            + elapsedMillis(stopStarted, encoderStoppedAt)
                            + " ms. M4A writer close: "
                            + elapsedMillis(encoderStoppedAt, writerClosedAt)
                            + " ms. Media finalization and publication: "
                            + elapsedMillis(writerClosedAt, publishedAt)
                            + " ms. Total: " + elapsedMillis(stopStarted, publishedAt) + " ms.");
                    return output;
                }
                finishAacOutput();
                long outputFinalizedAt = System.nanoTime();
                mediaOutput.finalizeSavedNow(context, completed);
                long publishedAt = System.nanoTime();
                clearRecordingState();
                log.info((encrypted ? "Encrypted audio" : "Audio")
                        + " AAC stop completed for '" + output + "'. Encoder shutdown: "
                        + elapsedMillis(stopStarted, encoderStoppedAt)
                        + " ms. Audio output finalization: "
                        + elapsedMillis(encoderStoppedAt, outputFinalizedAt)
                        + " ms. Publication: "
                        + elapsedMillis(outputFinalizedAt, publishedAt)
                        + " ms. Total: " + elapsedMillis(stopStarted, publishedAt) + " ms.");
                return output;
            } catch (Exception error) {
                closeM4aWriter();
                closeOutput();
                releaseFailedMediaReservation(completed);
                clearRecordingState();
                log.error("Audio finalization failed: " + output, error);
                return null;
            }
        } finally {
            finalizationInFlight = false;
            RecordingForegroundService.completeAudioFinalization(context);
        }
    }
    private void startEncoder() {
        MediaCodec nextEncoder = null;
        SharedMicrophoneCapture.Subscription nextMicrophone = null;
        try {
            nextEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            MediaFormat format = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE,
                    SharedMicrophoneCapture.SAMPLE_RATE,
                    SharedMicrophoneCapture.CHANNEL_COUNT);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4_096);
            nextEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            nextEncoder.start();
            nextMicrophone = microphoneCapture.subscribe();
            MediaCodec runningEncoder = nextEncoder;
            SharedMicrophoneCapture.Subscription runningMicrophone = nextMicrophone;
            Thread nextThread = new Thread(
                    () -> encode(runningEncoder, runningMicrophone), "dcam-standalone-audio");
            nextThread.setDaemon(true);
            stopRequested = false;
            encoderFailure = null;
            encoder = nextEncoder;
            microphoneSubscription = nextMicrophone;
            encoderThread = nextThread;
            nextThread.start();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            releaseEncoder(nextEncoder, nextMicrophone);
            throw new IllegalStateException("shared_microphone_subscribe_interrupted", error);
        } catch (IOException | RuntimeException error) {
            releaseEncoder(nextEncoder, nextMicrophone);
            throw new IllegalStateException("standalone_audio_encoder_start_failed", error);
        }
    }

    private void encode(MediaCodec runningEncoder,
            SharedMicrophoneCapture.Subscription runningMicrophone) {
        try {
            runEncoder(runningEncoder, runningMicrophone);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (!stopRequested) encoderFailure = error;
        } catch (IOException | RuntimeException error) {
            if (!stopRequested) encoderFailure = error;
        } finally {
            releaseEncoder(runningEncoder, runningMicrophone);
            if (encoder == runningEncoder) encoder = null;
            if (microphoneSubscription == runningMicrophone) microphoneSubscription = null;
        }
    }

    private void runEncoder(MediaCodec runningEncoder,
            SharedMicrophoneCapture.Subscription runningMicrophone)
            throws IOException, InterruptedException {
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        long submittedFrames = 0L;
        boolean inputEnded = false;
        boolean outputEnded = false;
        while (!outputEnded) {
            if (!inputEnded) {
                int inputIndex = runningEncoder.dequeueInputBuffer(10_000L);
                if (inputIndex >= 0) {
                    ByteBuffer input = runningEncoder.getInputBuffer(inputIndex);
                    if (input == null) {
                        throw new IllegalStateException("standalone_audio_input_buffer_missing");
                    }
                    input.clear();
                    int bytes = runningMicrophone.read(input);
                    if (bytes < 0) {
                        long durationUs = audioPresentationTimeUs(submittedFrames);
                        recordingDurationUs = durationUs;
                        runningEncoder.queueInputBuffer(inputIndex, 0, 0, durationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputEnded = true;
                    } else if (bytes > 0) {
                        bytes -= bytes % SharedMicrophoneCapture.BYTES_PER_FRAME;
                        runningEncoder.queueInputBuffer(inputIndex, 0, bytes,
                                audioPresentationTimeUs(submittedFrames), 0);
                        submittedFrames += bytes / SharedMicrophoneCapture.BYTES_PER_FRAME;
                    }
                }
            }
            while (true) {
                int outputIndex = runningEncoder.dequeueOutputBuffer(
                        outputInfo, inputEnded ? 10_000L : 0L);
                if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break;
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (usesM4aContainer()) startM4aTrack(runningEncoder.getOutputFormat());
                    continue;
                }
                if (outputIndex < 0) continue;
                try {
                    ByteBuffer output = runningEncoder.getOutputBuffer(outputIndex);
                    if (output != null && outputInfo.size > 0
                            && (outputInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        output.position(outputInfo.offset);
                        output.limit(outputInfo.offset + outputInfo.size);
                        if (usesM4aContainer()) writeM4aSample(output, outputInfo);
                        else writeAdtsFrame(output);
                    }
                    if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEnded = true;
                    }
                } finally {
                    runningEncoder.releaseOutputBuffer(outputIndex, false);
                }
                if (outputEnded) break;
            }
        }
    }


    static DcamFileType outputFileType(AudioFileFormat format) {
        return switch (Objects.requireNonNull(format, "format")) {
            case AAC -> DcamFileType.AUDIO;
            case M4A -> DcamFileType.AUDIO_M4A;
        };
    }

    private boolean usesM4aContainer() {
        return recordingFormat == AudioFileFormat.M4A;
    }

    private static long audioPresentationTimeUs(long frames) {
        return frames * 1_000_000L / SharedMicrophoneCapture.SAMPLE_RATE;
    }

    private static long elapsedMillis(long startedNanos, long completedNanos) {
        return Math.max(0L, completedNanos - startedNanos) / 1_000_000L;
    }

    private boolean stopEncoder() {
        stopRequested = true;
        SharedMicrophoneCapture.Subscription currentMicrophone = microphoneSubscription;
        if (currentMicrophone != null) currentMicrophone.close();
        Thread currentThread = encoderThread;
        if (currentThread == null || currentThread == Thread.currentThread()) return true;
        try {
            currentThread.join(ENCODER_STOP_TIMEOUT_MILLIS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (!currentThread.isAlive()) return true;
        currentThread.interrupt();
        MediaCodec currentEncoder = encoder;
        if (currentEncoder != null) releaseEncoder(currentEncoder, currentMicrophone);
        return false;
    }

    private static void releaseEncoder(MediaCodec encoder,
            SharedMicrophoneCapture.Subscription microphone) {
        if (microphone != null) microphone.close();
        if (encoder == null) return;
        try { encoder.stop(); } catch (RuntimeException ignored) {}
        try { encoder.release(); } catch (RuntimeException ignored) {}
    }

    private String currentDeviceSerial() {
        String serial = deviceSerialNumber.get();
        return serial == null || serial.isBlank() ? null : serial.trim();
    }

    @Override public void release() {
        if (encoderThread != null) {
            DcamMediaFile failed = outputMediaFile;
            stopDurabilitySync();
            stopEncoder();
            boolean empty = !hasAudioSamples();
            closeM4aWriter();
            closeOutput();
            deleteEmptyStagedAudio(failed, empty);
            releaseFailedMediaReservation(failed);
            clearRecordingState();
        }
        RecordingForegroundService.stopAudio(context);
    }

    @Override public long recordingStartedAtMillis() {
        return recordingStartedAtMillis == null ? -1L : recordingStartedAtMillis;
    }

    static boolean startProtectedRecording(
            java.util.function.BooleanSupplier foregroundStarter, Runnable recorderStarter) {
        if (!foregroundStarter.getAsBoolean()) return false;
        recorderStarter.run();
        return true;
    }

    @Override public boolean isRecording() { return encoderThread != null; }

    @Override public boolean hasPendingWork() {
        return encoderThread != null || finalizationInFlight;
    }

    private void clearRecordingState() {
        encoderThread = null;
        encoder = null;
        microphoneSubscription = null;
        stopRequested = false;
        encoderFailure = null;
        outputMediaFile = null;
        outputFile = null;
        recordingOutput = null;
        m4aWriter = null;
        recordingFormat = null;
        aacSampleCount = 0L;
        checkpointedOutputBytes = 0L;
        outputEncrypted = false;
        recordingStartedAtMillis = null;
        recordingDurationUs = 0L;
    }

    private synchronized void startM4aTrack(MediaFormat format) throws IOException {
        if (m4aWriter == null) throw new IOException("audio_m4a_writer_unavailable");
        m4aWriter.start(MediaFormatUtil.createFormatFromMediaFormat(format));
    }

    private synchronized void writeM4aSample(
            ByteBuffer payload, MediaCodec.BufferInfo sampleInfo) throws IOException {
        if (m4aWriter == null) throw new IOException("audio_m4a_writer_unavailable");
        m4aWriter.writeSample(payload,
                MuxerUtil.getMuxerBufferInfoFromMediaCodecBufferInfo(sampleInfo));
    }

    private synchronized void writeAdtsFrame(ByteBuffer payload) throws IOException {
        if (recordingOutput == null) throw new IOException("audio_output_closed");
        ByteBuffer frame = ByteBuffer.allocate(ADTS_HEADER_SIZE + payload.remaining());
        frame.put(adtsHeader(payload.remaining()));
        frame.put(payload);
        frame.flip();
        while (frame.hasRemaining()) {
            if (recordingOutput.write(frame) <= 0) {
                throw new IOException("audio_output_write_failed");
            }
        }
        aacSampleCount++;
    }

    static byte[] adtsHeader(int payloadLength) {
        if (payloadLength < 0 || payloadLength > 0x1FFF - ADTS_HEADER_SIZE) {
            throw new IllegalArgumentException("invalid AAC payload length");
        }
        int frameLength = payloadLength + ADTS_HEADER_SIZE;
        int profile = 1;
        int frequencyIndex = 3;
        int channelConfiguration = SharedMicrophoneCapture.CHANNEL_COUNT;
        return new byte[] {
                (byte) 0xFF,
                (byte) 0xF1,
                (byte) ((profile << 6) | (frequencyIndex << 2)
                        | (channelConfiguration >> 2)),
                (byte) (((channelConfiguration & 3) << 6) | (frameLength >> 11)),
                (byte) ((frameLength >> 3) & 0xFF),
                (byte) (((frameLength & 7) << 5) | 0x1F),
                (byte) 0xFC
        };
    }

    private synchronized boolean hasAudioSamples() {
        return usesM4aContainer()
                ? m4aWriter != null && m4aWriter.sampleCount() > 0L
                : aacSampleCount > 0L;
    }

    private synchronized void finishAacOutput() throws IOException {
        if (recordingOutput == null) throw new IOException("audio_output_closed");
        DcamRecordingOutput output = recordingOutput;
        output.finish();
        output.close();
        recordingOutput = null;
    }

    private synchronized void finishM4aWriter() throws IOException {
        if (m4aWriter == null) throw new IOException("audio_m4a_writer_unavailable");
        DcamAudioM4aWriter writer = m4aWriter;
        m4aWriter = null;
        writer.close();
    }

    private synchronized void closeM4aWriter() {
        if (m4aWriter == null) return;
        DcamAudioM4aWriter writer = m4aWriter;
        m4aWriter = null;
        try {
            writer.close();
        } catch (IOException error) {
            log.warn("Audio M4A writer close failed. Staged media remains available for "
                    + "startup recovery.", error);
        }
    }

    private void startDurabilitySync() {
        durabilitySync = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dcam-audio-sync");
            thread.setDaemon(true);
            return thread;
        });
        durabilitySync.scheduleWithFixedDelay(this::syncOutput, 1, 1, TimeUnit.SECONDS);
    }

    private void stopDurabilitySync() {
        if (durabilitySync == null) return;
        durabilitySync.shutdownNow();
        durabilitySync = null;
    }

    private synchronized void syncOutput() {
        if (recordingOutput == null) return;
        try {
            long logicalBytes = recordingOutput.size();
            if (logicalBytes <= checkpointedOutputBytes) return;
            recordingOutput.checkpoint();
            checkpointedOutputBytes = logicalBytes;
        } catch (IOException error) {
            log.warn("Audio durability sync failed", error);
        }
    }

    private synchronized void closeOutput() {
        if (recordingOutput == null) return;
        try {
            recordingOutput.close();
        } catch (IOException error) {
            log.warn("Audio output close failed", error);
        }
        recordingOutput = null;
    }

    private void releaseFailedMediaReservation(DcamMediaFile mediaFile) {
        if (mediaFile != null) mediaOutput.releaseMediaReservation(mediaFile);
    }
    private void deleteEmptyStagedAudio(DcamMediaFile mediaFile, boolean empty) {
        if (!empty || mediaFile == null) return;
        File staged = mediaFile.getFile();
        if (!staged.isFile()) return;
        try {
            if (!Files.deleteIfExists(staged.toPath())) return;
        } catch (IOException failure) {
            log.warn("Could not remove empty staged audio file '" + staged.getAbsolutePath()
                    + "' after recording failure.", failure);
            return;
        }
        deleteEmptyParent(staged);
        log.info("Removed empty staged audio file '" + staged.getName()
                + "' after recording failure. No audio data was available to recover.");
    }

    private void deleteEmptyParent(File staged) {
        File dateDirectory = staged.getParentFile();
        if (dateDirectory == null) return;
        try {
            Files.deleteIfExists(dateDirectory.toPath());
        } catch (DirectoryNotEmptyException ignored) {
        } catch (IOException failure) {
            log.warn("Could not remove empty audio staging directory '"
                    + dateDirectory.getAbsolutePath() + "'.", failure);
        }
    }
}