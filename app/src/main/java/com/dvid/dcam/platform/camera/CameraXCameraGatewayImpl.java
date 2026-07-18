package com.dvid.dcam.platform.camera;

import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCase;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
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
import androidx.lifecycle.Observer;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/** CameraX camera adapter. CameraX types do not escape through CameraGateway. */
@androidx.annotation.OptIn(markerClass = androidx.camera.video.ExperimentalPersistentRecording.class)
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
    private final BooleanSupplier storageWarningActive;
    private final ImageCapture imageCapture = new ImageCapture.Builder().build();
    private VideoCapture<Recorder> videoCapture;
    private boolean videoAvailable;
    private Preview cameraPreview;
    private CameraSelector cameraSelector;
    private ListenableFuture<ProcessCameraProvider> providerFuture;
    private boolean cameraBindInFlight;
    private Recording activeRecording;
    private DcamFileType pendingRecordingType;
    private CameraInfo observedCameraInfo;
    private boolean recordingInterrupted;
    private boolean pauseRequested;
    private boolean cameraRecoveredWhilePausing;
    private boolean stopRequested;
    private boolean recoveryRebindInFlight;
    private final Observer<CameraState> cameraStateObserver = this::onCameraStateChanged;

    public CameraXCameraGatewayImpl(Context context, LifecycleOwner lifecycleOwner, DcamConfig config,
                                    DcamMediaOutput mediaOutput, LogSink log,
                                    CaptureEventUseCase captureEvents,
                                    MediaEncryptionSettingsUseCase mediaEncryptionSettings,
                                    OperatorSessionUseCase operatorSession,
                                    BooleanSupplier storageWarningActive,
                                    CameraXPreviewView previewView) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner; this.config = config; this.mediaOutput = mediaOutput; this.log = log;
        this.captureEvents = captureEvents;
        this.mediaEncryptionSettings = mediaEncryptionSettings;
        this.operatorSession = operatorSession;
        this.storageWarningActive = storageWarningActive;
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

    public void refreshCameraState() {
        CameraInfo cameraInfo = observedCameraInfo;
        CameraState state = cameraInfo == null ? null : cameraInfo.getCameraState().getValue();
        if (activeRecording != null && state != null && state.getType() != CameraState.Type.OPEN) {
            rebindForRecovery();
            return;
        }
        if (state != null) onCameraStateChanged(state);
    }

    private void rebindForRecovery() {
        if (providerFuture == null || !providerFuture.isDone()
                || cameraSelector == null || cameraPreview == null) return;
        try {
            ProcessCameraProvider provider = providerFuture.get();
            provider.unbind(cameraPreview, imageCapture, videoCapture);
            Camera camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector,
                    cameraPreview, imageCapture, videoCapture);
            observeCameraState(camera.getCameraInfo());
        } catch (Exception error) {
            log.warn("Camera recovery rebind failed", error);
        }
    }

    public void bindIfPermitted() {
        CameraXPreviewView preview = previewView;
        if (preview == null) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            preview.showPermissionRequired(); return;
        }
        preview.showStarting(lifecycleOwner);
        log.info("RECORD_TRACE camera-bind-request videoAvailable=" + videoAvailable
                + " previewBound=" + (cameraPreview != null)
                + " bindInFlight=" + cameraBindInFlight
                + " pendingRecording=" + pendingRecordingType);
        if (cameraPreview != null) {
            cameraPreview.setSurfaceProvider(preview.surfaceProvider());
            return;
        }
        if (cameraBindInFlight) {
            log.info("RECORD_TRACE camera-bind-already-in-flight pendingRecording="
                    + pendingRecordingType);
            return;
        }
        cameraBindInFlight = true;
        providerFuture = ProcessCameraProvider.getInstance(context);
        providerFuture.addListener(() -> {
            cameraBindInFlight = false;
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
                this.cameraSelector = cameraSelector;
                cameraPreview = new Preview.Builder().build();
                cameraPreview.setSurfaceProvider(
                        attachedPreview == null ? null : attachedPreview.surfaceProvider());
                provider.unbindAll();
                try {
                    Camera camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector,
                            cameraPreview, imageCapture, videoCapture);
                    observeCameraState(camera.getCameraInfo());
                    videoAvailable = true;
                    log.info("RECORD_TRACE camera-bind-complete videoAvailable=true pendingRecording="
                            + pendingRecordingType);
                    startPendingRecordingAfterCameraBind();
                } catch (IllegalArgumentException videoError) {
                    videoAvailable = false;
                    log.warn("RECORD_TRACE video-bind-failed; keeping photo camera active", videoError);
                    Camera camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector,
                            cameraPreview, imageCapture);
                    observeCameraState(camera.getCameraInfo());
                    log.info("RECORD_TRACE photo-only-bind-complete pendingRecording="
                            + pendingRecordingType);
                    startPendingRecordingAfterCameraBind();
                }
            } catch (Exception error) {
                providerFuture = null;
                log.error("RECORD_TRACE camera-bind-failed pendingRecording="
                        + pendingRecordingType, error);
                showError(error.getMessage());
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void observeCameraState(CameraInfo cameraInfo) {
        if (observedCameraInfo != null) {
            observedCameraInfo.getCameraState().removeObserver(cameraStateObserver);
        }
        observedCameraInfo = cameraInfo;
        cameraInfo.getCameraState().observe(lifecycleOwner, cameraStateObserver);
    }

    private void onCameraStateChanged(CameraState state) {
        if (activeRecording == null || stopRequested) return;
        if (state.getType() == CameraState.Type.OPEN) {
            if (pauseRequested) {
                cameraRecoveredWhilePausing = true;
            } else if (recordingInterrupted) {
                resumeInterruptedRecording();
            }
            return;
        }
        CameraState.StateError error = state.getError();
        if (error == null || error.getType() != CameraState.ErrorType.RECOVERABLE
                || pauseRequested || recordingInterrupted) return;
        try {
            pauseRequested = true;
            activeRecording.pause();
        } catch (RuntimeException failure) {
            pauseRequested = false;
            log.warn("Camera interruption could not pause recording", failure);
        }
    }

    private void resumeInterruptedRecording() {
        if (activeRecording == null || stopRequested || !recordingInterrupted) return;
        try {
            activeRecording.resume();
        } catch (RuntimeException error) {
            log.warn("Camera recovery could not resume recording", error);
        }
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
        stopRequested = true;
        pauseRequested = false;
        cameraRecoveredWhilePausing = false;
        recoveryRebindInFlight = false;
        recordingInterrupted = false;
        onPreview(CameraXPreviewView::clearCameraInterrupted);
        if (activeRecording != null) activeRecording.stop();
    }

    private void startRecording(DcamFileType type) {
        log.info("RECORD_TRACE start-request type=" + type
                + " videoAvailable=" + videoAvailable
                + " previewBound=" + (cameraPreview != null)
                + " providerPresent=" + (providerFuture != null)
                + " activeRecording=" + (activeRecording != null)
                + " stopRequested=" + stopRequested);
        if (!videoAvailable && cameraPreview == null) {
            pendingRecordingType = type;
            stopRequested = false;
            IllegalStateException startupTrace = new IllegalStateException(
                    "Recording requested before CameraX video binding completed");
            log.warn("RECORD_TRACE recording-deferred-until-camera-ready type=" + type,
                    startupTrace);
            bindIfPermitted();
            return;
        }
        if (!videoAvailable) {
            String message = "Video recording unavailable on this camera";
            log.warn("RECORD_TRACE video-start-rejected-after-camera-bind type=" + type,
                    new IllegalStateException(message));
            captureEvents.captureFailed("Recording", message);
            onPreview(view -> view.showError(message));
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
            onPreview(view -> view.showTransientError(warning));
            return;
        }
        if (!ensureStorageReady("Recording")) return;
        LocalDateTime at = LocalDateTime.now();
        boolean encrypt = mediaEncryptionSettings.isMediaEncryptionEnabled();
        DcamMediaFile mediaFile = mediaOutput.mediaFile(
                type, config.getAccountUserId(), fileUserId, at, encrypt);
        AtomicBoolean startCanceled = new AtomicBoolean();
        PendingRecording pending = mediaOutput.prepareVideoRecording(
                context, videoCapture, mediaFile, mediaOutput.recordingFileSizeLimit());
        pending = pending.asPersistentRecording();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            pending = pending.withAudioEnabled();
        if (!RecordingForegroundService.startVideo(context, mediaFile.getFileName())) {
            String message = "Could not start recording foreground protection";
            log.error(message, null);
            captureEvents.captureFailed("Recording", message);
            onPreview(view -> view.showError(message));
            return;
        }
        activeRecording = pending.start(ContextCompat.getMainExecutor(context), event -> {
            if (event instanceof VideoRecordEvent.Start) {
                if (stopRequested) {
                    startCanceled.set(true);
                    log.info("Stopping recording that started after cancellation: "
                            + mediaFile.getFileName());
                    if (activeRecording != null) activeRecording.stop();
                    return;
                }
                pauseRequested = false;
                cameraRecoveredWhilePausing = false;
                recordingInterrupted = false;
                pauseRequested = false;
                cameraRecoveredWhilePausing = false;
                onPreview(view -> view.showRecording(mediaFile.getFileName()));
                log.info("Recording started: " + mediaFile.getFileName());
                RecordingMode mode = type == DcamFileType.SOS ? RecordingMode.SOS : RecordingMode.VIDEO;
                captureEvents.recordingStarted(mode, mediaFile.getFileName());
            }
            else if (event instanceof VideoRecordEvent.Pause) {
                pauseRequested = false;
                if (stopRequested) return;
                recordingInterrupted = true;
                captureEvents.recordingInterrupted("Camera interrupted by another application");
                onPreview(view -> view.showCameraInterrupted(
                        "Camera interrupted by another application"));
                if (cameraRecoveredWhilePausing) {
                    cameraRecoveredWhilePausing = false;
                    resumeInterruptedRecording();
                }
            }
            else if (event instanceof VideoRecordEvent.Resume) {
                if (stopRequested) return;
                recordingInterrupted = false;
                captureEvents.recordingResumed();
                onPreview(CameraXPreviewView::clearCameraInterrupted);
            }
            else if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize done = (VideoRecordEvent.Finalize) event;
                log.info("Recording finalize event: file=" + mediaFile.getFileName()
                        + " error=" + done.getError()
                        + " hasError=" + done.hasError()
                        + " cause=" + (done.getCause() == null
                                ? "none" : done.getCause().getClass().getSimpleName()
                                + ":" + done.getCause().getMessage()));
                // CameraX has closed this Recording. Keep the foreground indicator until
                // publication completes, but never call stop() on the finalized handle again.
                activeRecording = null;
                recordingInterrupted = false;
                pauseRequested = false;
                cameraRecoveredWhilePausing = false;
                stopRequested = false;
                onPreview(CameraXPreviewView::clearCameraInterrupted);
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
                                    if (startCanceled.get()) {
                                        log.info("Canceled startup finalized without publication: "
                                                + mediaFile.getFileName());
                                        finishRecordingAttempt();
                                        return;
                                    }
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
        if (cameraPreview != null) {
            cameraPreview.setSurfaceProvider(previewView.surfaceProvider());
            log.info("Camera preview reattached without rebinding use cases");
            return;
        }
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
        startPendingRecording("recording-finished");
    }

    private void startPendingRecordingAfterCameraBind() {
        startPendingRecording("camera-bind-complete");
    }

    private void startPendingRecording(String reason) {
        if (pendingRecordingType == null) return;
        DcamFileType nextType = pendingRecordingType;
        pendingRecordingType = null;
        log.info("RECORD_TRACE pending-recording-resumed reason=" + reason + " type=" + nextType
                + " videoAvailable=" + videoAvailable);
        startRecording(nextType);
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
        return storageWarningActive != null && storageWarningActive.getAsBoolean();
    }

    private String activeFileUserId() {
        OperatorSession tracedSession = operatorSession.current();
        log.info("AUTH_TRACE camera-session gateway=" + identity(this)
                + " sessionUseCase=" + identity(operatorSession)
                + " session=" + sessionSummary(tracedSession));
        OperatorSession session = tracedSession;
        if (session != null) return session.getFileUserId();
        showError("Operator login required");
        return null;
    }

    private static String identity(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(value));
    }

    private static String shortId(String value) {
        if (value == null) return "null";
        return Integer.toHexString(value.hashCode());
    }

    private static String sessionSummary(OperatorSession session) {
        return session == null ? "null" : shortId(session.getSessionId())
                + "/bootHash=" + shortId(session.getBootId());
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


