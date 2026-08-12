package com.dvid.dcam.platform.camera.shared;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.application.port.AudioPreparationEvents;
import com.dvid.dcam.feature.capture.application.port.CameraGateway;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEvents;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
import com.dvid.dcam.platform.camera.shared.runtime.CameraRuntimeSelection;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeBackend;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeOwner;
import com.dvid.dcam.platform.storage.DcamFileType;
import java.util.Locale;
import java.util.Objects;

public final class SharedCameraGateway implements CameraGateway {
    private static final AudioPreparationEvents NO_STORAGE_PREPARATION_EVENTS =
            new AudioPreparationEvents() {
        @Override public void onPreparing(String message) {}
        @Override public void onCleared() {}
        @Override public void onUnavailable(String message) {}
    };
    private final ProcessCameraRuntimeOwner runtimeOwner;
    private final SharedCameraGatewayBackend backend;
    private final CaptureEvents captureEvents;
    private final Logger logger;
    private SharedCameraPreviewView previewView;
    private boolean startupPreviewReady;
    private boolean capabilityCheckInProgress;
    private boolean capabilityCheckFailed;
    private boolean pendingImpHandoff;
    private AudioPreparationEvents storagePreparationEvents = NO_STORAGE_PREPARATION_EVENTS;
    private long recoveryNoticeGeneration = -1L;
    private CapabilityCheckProgress capabilityCheckProgress;

    public SharedCameraGateway(
            ProcessCameraRuntimeOwner runtimeOwner,
            SharedCameraGatewayBackend backend,
            CaptureEvents captureEvents,
            Logger logger) {
        this.runtimeOwner = Objects.requireNonNull(runtimeOwner, "runtimeOwner");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.captureEvents = Objects.requireNonNull(captureEvents, "captureEvents");
        this.logger = Objects.requireNonNull(logger, "logger");
        runtimeOwner.installBackend(backend);
        backend.setImpHandoff(this::queueImpHandoff);
        backend.setRecordingPreparationListener(
                new SharedCameraGatewayBackend.RecordingPreparationListener() {
            @Override public void onPreparing(String message) {
                showRecordingPreparation(message);
            }

            @Override public void onCleared() {
                clearRecordingPreparation();
            }

            @Override public void onUnavailable(String message) {
                showRecordingPreparationUnavailable(message);
            }
        });
        backend.setRecordingStorageLimit(this::queueStorageLimitStop);
        runtimeOwner.attach(this::onRuntimeStateChanged);
    }

    public synchronized void setStoragePreparationEvents(AudioPreparationEvents events) {
        storagePreparationEvents = Objects.requireNonNull(events, "events");
    }

    public ProcessCameraRuntimeOwner.Submission initialize(
            CameraRuntimeSelection verifiedSelection) {
        return runtimeOwner.initialize(verifiedSelection);
    }

    public ProcessCameraRuntimeOwner.Submission switchCamera(
            CameraRuntimeSelection verifiedSelection) {
        return runtimeOwner.switchCamera(verifiedSelection);
    }

    public ProcessCameraRuntimeOwner.Submission verifySetting(
            CameraRuntimeSelection verifiedSelection) {
        return runtimeOwner.verifySetting(verifiedSelection);
    }

    public ProcessCameraRuntimeOwner.Submission bindCommitted() {
        return runtimeOwner.bindCommitted();
    }

    public ProcessCameraRuntimeOwner.Submission bindCommitted(
            CameraRuntimeSelection selection) {
        return runtimeOwner.bindCommitted(selection);
    }

    public ProcessCameraRuntimeOwner.Submission restoreExact(
            CameraRuntimeSelection selection) {
        return runtimeOwner.restoreExact(selection);
    }

    public ProcessCameraRuntimeOwner.Submission releaseCamera() {
        return runtimeOwner.releaseCamera();
    }

    public ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot() {
        return runtimeOwner.snapshot();
    }

    public ProcessCameraRuntimeOwner.Attachment observeRuntimeState(
            ProcessCameraRuntimeOwner.Listener listener) {
        return runtimeOwner.attach(listener);
    }


    public void refreshDisplayRotation() {
        backend.refreshDisplayRotation();
    }

    public synchronized void holdStartupPreview() {
        startupPreviewReady = false;
        if (capabilityCheckInProgress) {
            if (previewView != null) {
                CapabilityCheckProgress progress = capabilityCheckProgress;
                if (progress == null) previewView.showCheckingCapabilities();
                else previewView.showCheckingCapabilities(progress.profile(), progress.cameraId(),
                        progress.stage(), progress.completed(), progress.total(), progress.detail());
            }
            return;
        }
        capabilityCheckFailed = false;
        if (previewView != null) previewView.showStarting();
    }

    public synchronized void releaseStartupPreviewWhenFrameArrives() {
        startupPreviewReady = true;
        if (previewView != null) previewView.showPreviewWhenFrameArrives();
    }


    public synchronized void beginCapabilityCheckPreview() {
        if (capabilityCheckInProgress) return;
        capabilityCheckInProgress = true;
        capabilityCheckFailed = false;
        startupPreviewReady = false;
        capabilityCheckProgress = null;
        logger.info("shared_camera_gateway stage=capability_progress"
                + " surface=preview_overlay action=begin");
        if (previewView != null) previewView.showCheckingCapabilities();
    }

    public synchronized void updateCapabilityCheckProgress(String profile, String cameraId,
            String stage, int completed, int total, String detail) {
        if (!capabilityCheckInProgress) beginCapabilityCheckPreview();
        CapabilityCheckProgress progress = new CapabilityCheckProgress(profile, cameraId, stage,
                completed, total, detail);
        capabilityCheckProgress = progress;
        logger.info("shared_camera_gateway stage=capability_progress surface=preview_overlay"
                + " profile=" + profile + " camera=" + cameraId + " stage=" + stage
                + " completed=" + completed + " total=" + total);
        if (previewView != null) previewView.showCheckingCapabilities(profile, cameraId, stage,
                completed, total, detail);
    }

    public synchronized void completeCapabilityCheckPreview(boolean successful) {
        capabilityCheckInProgress = false;
        capabilityCheckProgress = null;
        capabilityCheckFailed = !successful;
        startupPreviewReady = successful;
        logger.info("shared_camera_gateway stage=capability_progress"
                + " surface=preview_overlay action=terminal successful=" + successful);
        if (previewView == null) return;
        if (successful) previewView.showPreviewWhenFrameArrives();
        else previewView.showCapabilityCheckFailed();
    }

    public synchronized void attachPreview(SharedCameraPreviewView view) {
        Objects.requireNonNull(view, "view");
        if (view.surfaceHandle() != backend.previewOutput()) {
            throw new IllegalArgumentException("Preview uses different process surface");
        }
        if (previewView != null && previewView != view) {
            previewView.clearPreviewExpectedChanged();
            previewView.detachSurfaceHandle();
        }
        previewView = view;
        runtimeOwner.setPreviewExpected(view.isPreviewExpected());
        if (startupPreviewReady) view.showPreviewWhenFrameArrives();
        else if (capabilityCheckInProgress && capabilityCheckProgress != null) {
            CapabilityCheckProgress progress = capabilityCheckProgress;
            view.showCheckingCapabilities(progress.profile(), progress.cameraId(),
                    progress.stage(), progress.completed(), progress.total(), progress.detail());
        } else if (capabilityCheckInProgress) view.showCheckingCapabilities();
        else if (capabilityCheckFailed) view.showCapabilityCheckFailed();
        else view.showStarting();
        onRuntimeStateChanged(runtimeOwner.snapshot());
        logger.info("shared_camera_gateway preview=attached cameraLifetime=retained");
    }

    synchronized void setPreviewExpected(
            SharedCameraPreviewView view, boolean expected) {
        if (previewView != view) return;
        runtimeOwner.setPreviewExpected(expected);
    }

    public synchronized void detachPreview(SharedCameraPreviewView view) {
        if (previewView != view) return;
        view.clearPreviewExpectedChanged();
        view.detachSurfaceHandle();
        previewView = null;
        runtimeOwner.setPreviewExpected(false);
        logger.info("shared_camera_gateway preview=detached cameraLifetime=retained");
    }

    private record CapabilityCheckProgress(String profile, String cameraId, String stage,
            int completed, int total, String detail) {}

    @Override public void takePhoto() {
        ProcessCameraRuntimeOwner.Submission submission = runtimeOwner.capturePhoto();
        if (accepted(submission)) return;
        String detail = submissionMessage("Photo", submission);
        captureEvents.photoFailed("Photo", detail);
        showTransientError(detail);
        logRejected("Photo", submission);
    }

    @Override public void startVideo() {
        backend.requestRecording(RecordingMode.VIDEO);
        ProcessCameraRuntimeOwner.Submission submission =
                runtimeOwner.startRecording(DcamFileType.VIDEO);
        logger.info("Submit video recording start to retained camera runtime. Result: "
                + submission + ".");
        submitRecording(submission, "Recording");
    }

    @Override public void startImp() {
        if (runtimeOwner.snapshot().state() == CameraRuntimeState.RECORDING) {
            backend.requestImpHandoff();
            ProcessCameraRuntimeOwner.Submission submission = runtimeOwner.stopRecording();
            if (!accepted(submission)) {
                backend.cancelPendingRecording();
                reportCaptureFailure("IMP", submission);
            }
            return;
        }
        backend.requestRecording(RecordingMode.IMP);
        submitRecording(runtimeOwner.startRecording(DcamFileType.IMP), "IMP");
    }

    @Override public void stopRecording() {
        ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot = runtimeOwner.snapshot();
        if (snapshot.inFlight().orElse(null)
                == ProcessCameraRuntimeBackend.Operation.START_RECORDING) {
            backend.cancelPendingRecording();
            return;
        }
        ProcessCameraRuntimeOwner.Submission submission = runtimeOwner.stopRecording();
        if (submission == ProcessCameraRuntimeOwner.Submission.CONSUMED) {
            backend.cancelPendingRecording();
            return;
        }
        if (!accepted(submission)) reportCaptureFailure("Recording", submission);
    }

    private void submitRecording(
            ProcessCameraRuntimeOwner.Submission submission, String operation) {
        if (accepted(submission)) return;
        backend.cancelPendingRecording();
        reportCaptureFailure(operation, submission);
    }

    private void reportCaptureFailure(
            String operation, ProcessCameraRuntimeOwner.Submission submission) {
        String detail = submissionMessage(operation, submission);
        captureEvents.captureFailed(operation, detail);
        showTransientError(detail);
        logRejected(operation, submission);
    }

    private static boolean accepted(ProcessCameraRuntimeOwner.Submission submission) {
        return submission == ProcessCameraRuntimeOwner.Submission.ACCEPTED
                || submission == ProcessCameraRuntimeOwner.Submission.HELD
                || submission == ProcessCameraRuntimeOwner.Submission.COALESCED
                || submission == ProcessCameraRuntimeOwner.Submission.CONSUMED
                || submission == ProcessCameraRuntimeOwner.Submission.NO_OP;
    }

    private static String submissionMessage(
            String operation, ProcessCameraRuntimeOwner.Submission submission) {
        return switch (submission) {
            case REJECTED_TRANSITION -> operation + " unavailable while camera is busy";
            case REJECTED_NOT_READY -> operation + " unavailable until camera is ready";
            case REJECTED_RECORDING -> operation + " unavailable while recording";
            default -> operation + " unavailable";
        };
    }

    private void logRejected(
            String operation, ProcessCameraRuntimeOwner.Submission submission) {
        ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot = runtimeOwner.snapshot();
        String inFlight = snapshot.inFlight()
                .map(value -> value.name().toLowerCase(Locale.ROOT)).orElse("none");
        logger.warn("shared_camera_gateway command_rejected operation="
                + operation.toLowerCase(Locale.ROOT)
                + " submission=" + submission.name().toLowerCase(Locale.ROOT)
                + " state=" + snapshot.state().name().toLowerCase(Locale.ROOT)
                + " inFlight=" + inFlight, null);
    }

    private synchronized void onRuntimeStateChanged(ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot) {
        stopForStorageLimit(snapshot);
        startPendingImp(snapshot);
        if (snapshot.state() != CameraRuntimeState.RECOVERING
                || previewView == null
                || recoveryNoticeGeneration == snapshot.healthGeneration()) return;
        recoveryNoticeGeneration = snapshot.healthGeneration();
        previewView.showTransientError("Camera unavailable. Retrying recovery.");
        logger.warn("shared_camera_gateway recovery_notice healthGeneration="
                + snapshot.healthGeneration(), null);
    }

    private synchronized void queueImpHandoff() {
        pendingImpHandoff = true;
        startPendingImp(runtimeOwner.snapshot());
    }

    private void startPendingImp(ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot) {
        if (!pendingImpHandoff || snapshot.state() != CameraRuntimeState.READY
                || snapshot.inFlight().isPresent()) return;
        pendingImpHandoff = false;
        backend.requestRecording(RecordingMode.IMP);
        submitRecording(runtimeOwner.startRecording(DcamFileType.IMP), "IMP");
    }

    private synchronized void queueStorageLimitStop() {
        stopForStorageLimit(runtimeOwner.snapshot());
    }

    private void stopForStorageLimit(ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot) {
        if (snapshot.state() != CameraRuntimeState.RECORDING
                || snapshot.inFlight().isPresent()
                || !backend.recordingStorageLimitRequested()) return;
        ProcessCameraRuntimeOwner.Submission submission = runtimeOwner.stopRecording();
        if (!accepted(submission)) reportCaptureFailure("Storage", submission);
    }

    private synchronized void showRecordingPreparation(String message) {
        storagePreparationEvents.onPreparing(message);
    }

    private synchronized void clearRecordingPreparation() {
        storagePreparationEvents.onCleared();
    }

    private synchronized void showRecordingPreparationUnavailable(String message) {
        storagePreparationEvents.onUnavailable(message);
    }

    private synchronized void showTransientError(String detail) {
        if (previewView != null) previewView.showTransientError(detail);
    }
}