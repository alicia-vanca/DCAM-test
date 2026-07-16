package com.dvid.dcam.platform.camera;

import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCase;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.dvid.dcam.core.config.domain.DcamConfig;
import com.dvid.dcam.R;
import com.dvid.dcam.core.logging.application.port.LogSink;
import com.dvid.dcam.feature.capture.application.port.CameraGateway;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEventUseCase;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.auth.domain.OperatorSession;
import com.dvid.dcam.platform.recording.RecordingForegroundService;
import com.dvid.dcam.platform.storage.DcamFileType;
import com.dvid.dcam.platform.storage.DcamMediaFile;
import com.dvid.dcam.platform.storage.DcamMediaOutput;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import com.dvid.dcam.platform.storage.CaptureStorageFailureClassifier;
import com.dvid.dcam.platform.device.AndroidDeviceCapabilities;
import com.google.common.util.concurrent.ListenableFuture;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/** CameraX camera adapter. CameraX types do not escape through CameraGateway. */
public final class CameraXCameraGatewayImpl implements CameraGateway {
    private final Context context;
    private final DcamConfig config;
    private final DcamMediaOutput mediaOutput;
    private final LifecycleOwner lifecycleOwner;
    private CameraXPreviewView previewView;
    private final LogSink log;
    private final CaptureEventUseCase captureEvents;
    private final MediaEncryptionSettingsUseCase mediaEncryptionSettings;
    private final OperatorSessionUseCase operatorSession;
    private final ImageCapture imageCapture = new ImageCapture.Builder().build();
    private VideoCapture<Recorder> videoCapture;
    private boolean videoAvailable;
    private Preview cameraPreview;
    private ListenableFuture<ProcessCameraProvider> providerFuture;
    private Recording activeRecording;
    private DcamFileType pendingRecordingType;

    public CameraXCameraGatewayImpl(Context context, LifecycleOwner lifecycleOwner, DcamConfig config,
                                    DcamMediaOutput mediaOutput, LogSink log,
                                    CaptureEventUseCase captureEvents,
                                    MediaEncryptionSettingsUseCase mediaEncryptionSettings,
                                    OperatorSessionUseCase operatorSession,
                                    CameraXPreviewView previewView) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner; this.config = config; this.mediaOutput = mediaOutput; this.log = log;
        this.captureEvents = captureEvents;
        this.mediaEncryptionSettings = mediaEncryptionSettings;
        this.operatorSession = operatorSession;
        this.previewView = previewView;
        videoCapture = createVideoCapture();
        bindIfPermitted();
    }

    private QualitySelector selectedQuality() {
        String selected = new AndroidDeviceCapabilities(context).selectedRecordQuality();
        List<Quality> qualities = new java.util.ArrayList<>();
        if ("4K".equals(selected)) qualities.add(Quality.UHD);
        if ("FHD".equals(selected) || "4K".equals(selected)) qualities.add(Quality.FHD);
        if ("HD".equals(selected) || "FHD".equals(selected) || "4K".equals(selected)) qualities.add(Quality.HD);
        qualities.add(Quality.SD);
        return QualitySelector.fromOrderedList(qualities, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD));
    }

    private VideoCapture<Recorder> createVideoCapture() {
        return VideoCapture.withOutput(new Recorder.Builder().setQualitySelector(selectedQuality()).build());
    }

    public void reloadVideoQuality() {
        if (activeRecording != null) return;
        videoCapture = createVideoCapture();
        cameraPreview = null;
        videoAvailable = false;
        bindIfPermitted();
    }

    public static void warmUp(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            ProcessCameraProvider.getInstance(context.getApplicationContext());
        }
    }

    public void bindIfPermitted() {
        CameraXPreviewView preview = previewView;
        if (preview == null) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            preview.showPermissionRequired(); return;
        }
        preview.showStarting();
        if (cameraPreview != null) {
            cameraPreview.setSurfaceProvider(preview.surfaceProvider());
            preview.clearMessage();
            return;
        }
        providerFuture = ProcessCameraProvider.getInstance(context);
        providerFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = providerFuture.get();
                CameraXPreviewView attachedPreview = previewView;
                CameraSelector cameraSelector;
                if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                    log.warn("Back camera unavailable; using front camera", null);
                } else {
                    throw new IllegalStateException("No usable camera found");
                }
                cameraPreview = new Preview.Builder().build();
                cameraPreview.setSurfaceProvider(
                        attachedPreview == null ? null : attachedPreview.surfaceProvider());
                provider.unbindAll();
                try {
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector,
                            cameraPreview, imageCapture, videoCapture);
                    videoAvailable = true;
                } catch (IllegalArgumentException videoError) {
                    videoAvailable = false;
                    log.warn("Video capture unavailable; keeping photo camera active", videoError);
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector,
                            cameraPreview, imageCapture);
                }
                if (attachedPreview != null) attachedPreview.clearMessage();
            } catch (Exception error) { showError(error.getMessage()); }
        }, ContextCompat.getMainExecutor(context));
    }

    @Override public void takePhoto() {
        String fileUserId = activeFileUserId();
        if (fileUserId == null) return;
        if (!ensureStorageReady("Photo")) return;
        LocalDateTime at = LocalDateTime.now();
        boolean encrypt = mediaEncryptionSettings.isMediaEncryptionEnabled();
        DcamMediaFile mediaFile = mediaOutput.mediaFile(
                DcamFileType.IMAGE, config.getAccountUserId(), fileUserId, at, encrypt);
        ImageCapture.OutputFileOptions options = mediaOutput.imageOptions(context, mediaFile);
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(ImageCapture.OutputFileResults result) {
                try {
                    if (encrypt) {
                        mediaOutput.encryptSaved(context, mediaFile, result.getSavedUri(),
                                config.getMediaEncryptionPassword());
                    }
                    mediaOutput.finalizeSaved(context, mediaFile, new DcamMediaOutput.FinalizationCallback() {
                        @Override public void onSuccess(java.io.File finalFile) {
                            onMain(() -> {
                                log.info((encrypt ? "Encrypted photo finalized: " : "Photo finalized: ")
                                        + mediaFile.getFileName());
                                captureEvents.photoSaved(mediaFile.getFileName());
                            });
                        }

                        @Override public void onFailure(Exception failure) {
                            onMain(() -> reportFinalizationFailure("Photo", mediaFile, failure));
                        }
                    });
                } catch (Exception error) {
                    log.error("Photo encryption failed: " + mediaFile.getFileName(), error);
                    captureEvents.captureFailed("Photo encryption", message(error));
                    onPreview(view -> view.showError("Photo encryption failed"));
                }
            }
            @Override public void onError(ImageCaptureException error) {
                CaptureStorageCheck storageCheck = mediaOutput.checkCaptureReady();
                if (!storageCheck.isReady()) {
                    String detail = storageCheck.getReason() + "; staged image preserved if present";
                    log.error("Photo storage failure", error);
                    captureEvents.captureFailed("Storage", detail);
                    onPreview(view -> view.showError(detail));
                    return;
                }
                log.error("Photo failed", error);
                showError(error.getMessage());
            }
        });
    }

    @Override public void startVideo() {
        if (activeRecording == null) startRecording(DcamFileType.VIDEO);
    }

    @Override public void startSos() {
        startRecording(DcamFileType.SOS);
    }

    @Override public void stopRecording() {
        pendingRecordingType = null;
        if (activeRecording != null) activeRecording.stop();
    }

    private void startRecording(DcamFileType type) {
        if (!videoAvailable) {
            log.warn("Video start ignored: camera does not support a usable video quality", null);
            onPreview(view -> view.showError("Video recording unavailable on this camera"));
            return;
        }
        if (activeRecording != null) {
            if (type == DcamFileType.SOS) {
                pendingRecordingType = DcamFileType.SOS;
                log.info("Stopping active recording before SOS handoff");
                activeRecording.stop();
            } else {
                log.warn("Ignored recording start while another recording is active", null);
            }
            return;
        }
        String fileUserId = activeFileUserId();
        if (fileUserId == null) return;
        if (lowStorageThresholdReached()) {
            String warning = context.getString(R.string.low_storage_recording_blocked);
            captureEvents.captureFailed("Storage", warning);
            onPreview(view -> view.showError(warning));
            return;
        }
        if (!ensureStorageReady("Recording")) return;
        LocalDateTime at = LocalDateTime.now();
        boolean encrypt = mediaEncryptionSettings.isMediaEncryptionEnabled();
        DcamMediaFile mediaFile = mediaOutput.mediaFile(
                type, config.getAccountUserId(), fileUserId, at, encrypt);
        PendingRecording pending = mediaOutput.prepareVideoRecording(
                context, videoCapture, mediaFile, mediaOutput.recordingFileSizeLimit());
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            pending = pending.withAudioEnabled();
        activeRecording = pending.start(ContextCompat.getMainExecutor(context), event -> {
            if (event instanceof VideoRecordEvent.Start) {
                onPreview(view -> view.showRecording(mediaFile.getFileName()));
                log.info("Recording started: " + mediaFile.getFileName());
                if (!RecordingForegroundService.startVideo(context, mediaFile.getFileName())) {
                    log.warn("Could not start recording foreground service", null);
                }
                RecordingMode mode = type == DcamFileType.SOS ? RecordingMode.SOS : RecordingMode.VIDEO;
                captureEvents.recordingStarted(mode, mediaFile.getFileName());
            }
            else if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize done = (VideoRecordEvent.Finalize) event;
                // CameraX has closed this Recording. Keep the foreground indicator until
                // publication completes, but never call stop() on the finalized handle again.
                activeRecording = null;
                if (done.hasError()) {
                    boolean storageFailure =
                            CaptureStorageFailureClassifier.isVideoStorageFailure(done.getError())
                                    || !mediaOutput.checkCaptureReady().isReady();
                    String operation = storageFailure ? "Storage" : "Recording";
                    String detail = storageFailure
                            ? "Storage limit reached; staged recording preserved"
                            : "CameraX error " + done.getError();
                    log.error(operation + " failed: " + mediaFile.getFileName()
                            + " error=" + done.getError(), null);
                    if (storageFailure) {
                        log.warn("Storage limit stopped recording; finalizing staged media: "
                                + mediaFile.getFileName(), null);
                        finalizeStorageLimitedRecording(mediaFile, encrypt,
                                done.getOutputResults().getOutputUri());
                    } else {
                        captureEvents.captureFailed(operation, detail);
                        finishRecordingAttempt();
                    }
                }
                else {
                    try {
                        if (encrypt) {
                            mediaOutput.encryptSaved(context, mediaFile, done.getOutputResults().getOutputUri(),
                                    config.getMediaEncryptionPassword());
                        }
                        mediaOutput.finalizeSaved(context, mediaFile,
                                new DcamMediaOutput.FinalizationCallback() {
                            @Override public void onSuccess(java.io.File finalFile) {
                                onMain(() -> {
                                    log.info((encrypt ? "Encrypted recording finalized: "
                                            : "Recording finalized: ") + mediaFile.getFileName());
                                    captureEvents.recordingCompleted(mediaFile.getFileName());
                                    finishRecordingAttempt();
                                });
                            }

                            @Override public void onFailure(Exception failure) {
                                onMain(() -> {
                                    reportFinalizationFailure("Recording", mediaFile, failure);
                                    finishRecordingAttempt();
                                });
                            }
                        });
                    } catch (Exception error) {
                        log.error("Recording encryption failed: " + mediaFile.getFileName(), error);
                        captureEvents.captureFailed("Recording encryption", message(error));
                        onPreview(view -> view.showError("Recording encryption failed"));
                        finishRecordingAttempt();
                    }
                }
            }
        });
    }

    private void finalizeStorageLimitedRecording(DcamMediaFile mediaFile, boolean encrypt,
            android.net.Uri savedUri) {
        try {
            if (encrypt) mediaOutput.encryptSaved(context, mediaFile, savedUri,
                    config.getMediaEncryptionPassword());
        } catch (Exception failure) {
            reportFinalizationFailure("Recording encryption", mediaFile, failure);
            finishRecordingAttempt();
            return;
        }
        mediaOutput.finalizeSaved(context, mediaFile,
                new DcamMediaOutput.FinalizationCallback() {
            @Override public void onSuccess(java.io.File finalFile) {
                onMain(() -> {
                    captureEvents.recordingStoppedForStorage(mediaFile.getFileName());
                    finishRecordingAttempt();
                });
            }

            @Override public void onFailure(Exception failure) {
                onMain(() -> {
                    reportFinalizationFailure("Recording", mediaFile, failure);
                    finishRecordingAttempt();
                });
            }
        });
    }

    public void attachPreview(CameraXPreviewView previewView) {
        this.previewView = previewView;
        bindIfPermitted();
    }

    public void detachPreview(CameraXPreviewView previewView) {
        if (this.previewView != previewView) return;
        this.previewView = null;
        if (cameraPreview != null) cameraPreview.setSurfaceProvider(null);
    }

    private void reportFinalizationFailure(
            String mediaKind, DcamMediaFile mediaFile, Exception failure) {
        String detail = "Finalization failed; staged media preserved: " + message(failure);
        log.error(mediaKind + " finalization failed: " + mediaFile.getFileName(), failure);
        captureEvents.captureFailed("Finalization", detail);
        onPreview(view -> view.showError(detail));
    }

    private void finishRecordingAttempt() {
        activeRecording = null;
        RecordingForegroundService.stopVideo(context);
        if (pendingRecordingType != null) {
            DcamFileType nextType = pendingRecordingType;
            pendingRecordingType = null;
            startRecording(nextType);
        }
    }

    private void onMain(Runnable action) {
        ContextCompat.getMainExecutor(context).execute(action);
    }

    private void showError(String text) {
        log.error("Camera error: " + text, null);
        captureEvents.captureFailed("Camera", text);
        onPreview(view -> view.showError(text));
    }

    private boolean ensureStorageReady(String operation) {
        CaptureStorageCheck check = mediaOutput.checkCaptureReady();
        if (check.isReady()) return true;
        String message = check.getReason() + " (available=" + check.getAvailableBytes()
                + ", required=" + check.getRequiredBytes() + ")";
        log.warn(operation + " rejected: " + message, null);
        captureEvents.captureFailed("Storage", message);
        onPreview(view -> view.showError(message));
        return false;
    }

    private boolean lowStorageThresholdReached() {
        int warningGb = context.getSharedPreferences("dcam_storage", Context.MODE_PRIVATE).getInt("warning_gb", 2);
        long threshold = warningGb * 1024L * 1024L * 1024L;
        long available = mediaOutput.availableBytesForNextCapture();
        return available > 0L && available <= threshold;
    }

    private String activeFileUserId() {
        OperatorSession session = operatorSession.current();
        if (session != null) return session.getFileUserId();
        showError("Operator login required");
        return null;
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private void onPreview(Consumer<CameraXPreviewView> action) {
        CameraXPreviewView preview = previewView;
        if (preview != null) action.accept(preview);
    }

    public void release() {
        pendingRecordingType = null;
        if (activeRecording != null) activeRecording.stop();
        RecordingForegroundService.stopVideo(context);
        if (providerFuture != null && providerFuture.isDone()) {
            try { providerFuture.get().unbindAll(); } catch (Exception ignored) {}
        }
        cameraPreview = null;
    }
}


