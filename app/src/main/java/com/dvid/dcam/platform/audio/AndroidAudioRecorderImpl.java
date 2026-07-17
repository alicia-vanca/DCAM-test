package com.dvid.dcam.platform.audio;

import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCase;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import androidx.core.content.ContextCompat;
import com.dvid.dcam.core.config.domain.DcamConfig;
import com.dvid.dcam.core.logging.application.port.LogSink;
import com.dvid.dcam.feature.capture.application.port.AudioRecorder;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.platform.storage.DcamFileType;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import com.dvid.dcam.platform.storage.DcamMediaFile;
import com.dvid.dcam.platform.storage.DcamMediaOutput;
import com.dvid.dcam.platform.recording.RecordingForegroundService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Android MediaRecorder platform adapter. */
public final class AndroidAudioRecorderImpl implements AudioRecorder {
    private final Context context;
    private final DcamMediaOutput mediaOutput;
    private final LogSink log;
    private final MediaEncryptionSettingsUseCase mediaEncryptionSettings;
    private final OperatorSessionUseCase operatorSession;
    private MediaRecorder recorder;
    private DcamMediaFile outputMediaFile;
    private File outputFile;
    private FileOutputStream outputStream;
    private ScheduledExecutorService durabilitySync;
    private boolean outputEncrypted;

    public AndroidAudioRecorderImpl(Context context, DcamMediaOutput mediaOutput, LogSink log,
                                    MediaEncryptionSettingsUseCase mediaEncryptionSettings,
                                    OperatorSessionUseCase operatorSession) {
        this.context = context; this.mediaOutput = mediaOutput; this.log = log;
        this.mediaEncryptionSettings = mediaEncryptionSettings;
        this.operatorSession = operatorSession;
    }

    @Override public String toggle(DcamConfig config) {
        if (recorder != null) {
            DcamMediaFile completed = outputMediaFile;
            boolean encrypted = outputEncrypted;
            String output = completed == null ? null : completed.getFileName();
            try {
                stopDurabilitySync();
                recorder.stop();
            } catch (RuntimeException error) {
                log.error("Audio stop failed", error);
                output = null;
            } finally {
                syncOutput();
                recorder.release();
                recorder = null;
                closeOutput();
                RecordingForegroundService.stopAudio(context);
                outputMediaFile = null;
                outputFile = null;
                outputEncrypted = false;
            }
            if (output == null || completed == null) return null;
            try {
                if (encrypted) {
                    mediaOutput.encryptSaved(context, completed, null, config.getMediaEncryptionPassword());
                }
                mediaOutput.finalizeSavedNow(context, completed);
                log.info((encrypted ? "Encrypted audio saved: " : "Audio saved: ") + output);
                return output;
            } catch (Exception error) {
                log.error("Audio encryption failed: " + output, error);
                return null;
            }
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            log.warn("Audio permission missing", null);
            return null;
        }

        try {
            OperatorSession session = operatorSession.current();
            if (session == null) {
                log.warn("Audio start ignored: operator login required", null);
                return null;
            }
            CaptureStorageCheck storageCheck = mediaOutput.checkCaptureReady();
            if (!storageCheck.isReady()) {
                log.warn("Audio start rejected: " + storageCheck.getReason(), null);
                return null;
            }
            LocalDateTime at = LocalDateTime.now();
            outputEncrypted = mediaEncryptionSettings.isMediaEncryptionEnabled();
            outputMediaFile = mediaOutput.durableAudioMediaFile(
                    config.getAccountUserId(),
                    session.getFileUserId(),
                    at,
                    outputEncrypted);
            MediaRecorder next = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
            next.setAudioSource(MediaRecorder.AudioSource.MIC);
            next.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            next.setAudioChannels(1);
            next.setAudioSamplingRate(8000);
            next.setAudioEncodingBitRate(64000);
            outputFile = mediaOutput.audioFile(outputMediaFile);
            outputStream = new FileOutputStream(outputFile);
            next.setOutputFile(outputStream.getFD());
            recorder = next;
            next.prepare();
            if (!startProtectedRecording(
                    () -> RecordingForegroundService.startAudio(context, outputMediaFile.getFileName()),
                    next::start)) {
                throw new IllegalStateException("Could not start audio foreground protection");
            }
            startDurabilitySync();
            log.info("Audio started: " + outputFile.getAbsolutePath());
            return outputMediaFile.getFileName();
        } catch (Exception error) {
            RecordingForegroundService.stopAudio(context);
            stopDurabilitySync();
            if (recorder != null) recorder.release();
            closeOutput();
            recorder = null; outputMediaFile = null; outputFile = null; outputEncrypted = false;
            log.error("Audio failed", error);
            return null;
        }
    }

    @Override public void release() {
        if (recorder != null) {
            stopDurabilitySync();
            try { recorder.stop(); } catch (RuntimeException ignored) {}
            syncOutput();
            recorder.release(); recorder = null;
            closeOutput();
            outputMediaFile = null;
            outputFile = null;
            outputEncrypted = false;
        }
        RecordingForegroundService.stopAudio(context);
    }

    static boolean startProtectedRecording(
            java.util.function.BooleanSupplier foregroundStarter, Runnable recorderStarter) {
        if (!foregroundStarter.getAsBoolean()) return false;
        recorderStarter.run();
        return true;
    }

    @Override public boolean isRecording() { return recorder != null; }

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
        if (outputStream == null) return;
        try {
            outputStream.getFD().sync();
        } catch (IOException error) {
            log.warn("Audio durability sync failed", error);
        }
    }

    private synchronized void closeOutput() {
        if (outputStream == null) return;
        try {
            outputStream.close();
        } catch (IOException error) {
            log.warn("Audio output close failed", error);
        }
        outputStream = null;
    }
}
