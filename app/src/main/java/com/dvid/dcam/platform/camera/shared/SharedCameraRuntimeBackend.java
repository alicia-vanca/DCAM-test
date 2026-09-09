package com.dvid.dcam.platform.camera.shared;

import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEvents;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.usecase.VerifyCameraSelectionUseCase;
import com.dvid.dcam.feature.device.domain.camera.CameraFailureClass;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.platform.camera.shared.runtime.CameraRuntimeSelection;
import com.dvid.dcam.platform.camera.shared.verification.SharedCameraVerificationSession;
import com.dvid.dcam.platform.device.capability.CameraCapabilityService;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeBackend;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SharedCameraRuntimeBackend implements SharedCameraGatewayBackend {
    private static final String TRANSITION_SUPERSEDED = "transition_superseded";
    private static final String RECORDING = "Recording";
    private static final String RECORDING_START_CANCELLED = "recording_start_cancelled";
    private static final String TOTAL_DURATION = " ms. Total: ";
    private static final String PHOTO = "Photo";
    private static final String PHOTO_BINDING_UNCHANGED = "photo_binding_unchanged";
    private static final String ACTIVE_SELECTION = "activeSelection";
    private static final String ACTIVE_CONTEXT = "activeContext";
    private static final String FINALIZATION = "Finalization";
    private static final Executor RECORDING_FINALIZATION_EXECUTOR = action -> {
        Thread thread = new Thread(action, "dcam-recording-finalization");
        thread.setDaemon(true);
        thread.start();
    };
    private static final SharedCameraGatewayBackend.RecordingPreparationListener
            NO_RECORDING_PREPARATION_LISTENER = new SharedCameraGatewayBackend.RecordingPreparationListener() {
        @Override public void onPreparing(String message) {
            // No preparation listener is registered.
        }
        @Override public void onCleared() {
            // No preparation listener is registered.
        }
        @Override public void onUnavailable(String message) {
            // No preparation listener is registered.
        }
    };
    private final SharedCameraPipelineProvider pipelineProvider;
    private final SharedCameraPreviewOutput previewSurface;
    private final SharedCameraMediaLifecycle mediaLifecycle;
    private final CapabilityAccess capabilities;
    private final CaptureEvents captureEvents;
    private final Logger logger;
    private final Executor recordingFinalizationExecutor;
    private final AtomicLong cancelledThroughSequence = new AtomicLong();

    private SharedCameraCapturePipeline pipeline;
    private volatile SharedCameraCapturePipeline bindingPipeline;
    private volatile boolean previewExpected;
    private CameraRuntimeSelection activeSelection;
    private CameraOperationContext activeContext;
    private SharedCameraMediaLifecycle.RecordingCapture activeRecording;
    private PendingRecordingFinalization pendingRecordingFinalization;
    private RecordingMode requestedRecordingMode;
    private boolean impHandoffRequested;
    private boolean recordingStorageLimitRequested;

    private boolean recordingStartCancelled;
    private SharedCameraGatewayBackend.ImpHandoff impHandoff = () -> {};
    private SharedCameraGatewayBackend.RecordingPreparationListener recordingPreparationListener =
            NO_RECORDING_PREPARATION_LISTENER;
    private Runnable recordingStorageLimitListener = () -> {};

    interface CapabilityAccess {
        CameraCapabilityStore capabilityStore();
        Optional<Snapshot> currentSnapshot();
        Optional<CandidateKey> requestedCandidate(String cameraId);
        void applyRecordingFallback(CandidateKey requested, CandidateKey effective);
        void restoreSelectedRecordingProfile(com.dvid.dcam.feature.device.domain.camera.CameraId cameraId,
                Optional<SelectedRecordingProfile> selection);
        VerificationOutcome standaloneImageOutcome(CandidateKey requested);
        OptionalInt sensorOrientationDegrees(
                com.dvid.dcam.feature.device.domain.camera.CameraId cameraId);
        Optional<CandidateKey> recordStandaloneImageOutcome(
                CandidateKey requested, VerificationOutcome outcome);
    }

    SharedCameraRuntimeBackend(
            SharedCameraPipelineProvider pipelineProvider,
            SharedCameraPreviewOutput previewSurface,
            SharedCameraMediaLifecycle mediaLifecycle,
            CaptureEvents captureEvents,
            Logger logger) {
        this(pipelineProvider, previewSurface, mediaLifecycle, (CapabilityAccess) null,
                captureEvents, logger, Runnable::run);
    }

    SharedCameraRuntimeBackend(
            SharedCameraPipelineProvider pipelineProvider,
            SharedCameraPreviewOutput previewSurface,
            SharedCameraMediaLifecycle mediaLifecycle,
            CapabilityAccess capabilities,
            CaptureEvents captureEvents,
            Logger logger) {
        this(pipelineProvider, previewSurface, mediaLifecycle, capabilities,
                captureEvents, logger, Runnable::run);
    }

    SharedCameraRuntimeBackend(
            SharedCameraPipelineProvider pipelineProvider,
            SharedCameraPreviewOutput previewSurface,
            SharedCameraMediaLifecycle mediaLifecycle,
            CapabilityAccess capabilities,
            CaptureEvents captureEvents,
            Logger logger,
            Executor recordingFinalizationExecutor) {
        this.pipelineProvider = Objects.requireNonNull(pipelineProvider, "pipelineProvider");
        this.previewSurface = Objects.requireNonNull(previewSurface, "previewSurface");
        this.mediaLifecycle = Objects.requireNonNull(mediaLifecycle, "mediaLifecycle");
        this.capabilities = capabilities;
        this.captureEvents = Objects.requireNonNull(captureEvents, "captureEvents");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.recordingFinalizationExecutor = Objects.requireNonNull(
                recordingFinalizationExecutor, "recordingFinalizationExecutor");
    }
    public SharedCameraRuntimeBackend(
            SharedCameraPipelineProvider pipelineProvider,
            SharedCameraPreviewOutput previewSurface,
            SharedCameraMediaLifecycle mediaLifecycle,
            CameraCapabilityService capabilities,
            CaptureEvents captureEvents,
            Logger logger) {
        this(pipelineProvider, previewSurface, mediaLifecycle,
                capabilityAccess(Objects.requireNonNull(capabilities, "capabilities")),
                captureEvents, logger, RECORDING_FINALIZATION_EXECUTOR);
    }

    private static CapabilityAccess capabilityAccess(CameraCapabilityService service) {
        return new CapabilityAccess() {
            @Override public CameraCapabilityStore capabilityStore() {
                return service;
            }

            @Override public Optional<Snapshot> currentSnapshot() {
                return service.currentSnapshot();
            }

            @Override public Optional<CandidateKey> requestedCandidate(String cameraId) {
                return service.requestedCandidate(cameraId);
            }

            @Override public void applyRecordingFallback(CandidateKey requested,
                    CandidateKey effective) {
                service.applyRecordingFallback(requested, effective);
            }

            @Override public void restoreSelectedRecordingProfile(
                    com.dvid.dcam.feature.device.domain.camera.CameraId cameraId,
                    Optional<SelectedRecordingProfile> selection) {
                service.restoreSelectedRecordingProfile(cameraId, selection);
            }

            @Override public VerificationOutcome standaloneImageOutcome(CandidateKey requested) {
                return service.standaloneImageOutcome(requested);
            }

            @Override public OptionalInt sensorOrientationDegrees(
                    com.dvid.dcam.feature.device.domain.camera.CameraId cameraId) {
                return service.currentSnapshot().stream()
                        .flatMap(snapshot -> snapshot.cameras().stream())
                        .filter(camera -> camera.cameraId().equals(cameraId))
                        .mapToInt(CameraCapabilityStore.CameraSnapshot::sensorOrientationDegrees)
                        .findFirst();
            }

            @Override public Optional<CandidateKey> recordStandaloneImageOutcome(
                    CandidateKey requested,
                    VerificationOutcome outcome) {
                return service.recordStandaloneImageOutcome(requested, outcome);
            }
        };
    }
    @Override public SharedCameraPreviewOutput previewOutput() {
        return previewSurface;
    }

    @Override public synchronized void refreshDisplayRotation() {
        SharedCameraCapturePipeline current = pipeline;
        CameraRuntimeSelection selection = activeSelection;
        if (current == null || selection == null) return;
        pipelineProvider.refreshRotation(selection, previewSurface, current);
    }

    @Override public synchronized void setPreviewExpected(boolean expected) {
        previewExpected = expected;
        SharedCameraCapturePipeline current = pipeline;
        if (current != null) current.setPreviewExpected(expected);
        SharedCameraCapturePipeline binding = bindingPipeline;
        if (binding != null && binding != current) binding.setPreviewExpected(expected);
    }

    @Override public synchronized Optional<ProcessCameraRuntimeBackend.HealthSnapshot> healthSnapshot(
            CameraOperationContext expectedBinding) {
        Objects.requireNonNull(expectedBinding, "expectedBinding");
        SharedCameraCapturePipeline current = pipeline;
        CameraOperationContext bound = activeContext;
        if (current == null || bound == null
                || !expectedBinding.matchesCurrentOperation(bound)) {
            return Optional.empty();
        }
        try {
            SharedCameraCapturePipeline.HealthSnapshot health =
                    current.healthSnapshot(runtimeContext());
            return Optional.of(new ProcessCameraRuntimeBackend.HealthSnapshot(
                    health.sessionBound(), health.recoveryRequired(),
                    health.previewSignalAvailable(), health.sourceFrameCount(),
                    health.previewFrameCount(), health.detail()));
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, "shared_camera_gateway_backend health_probe_failed", error);
            return Optional.of(new ProcessCameraRuntimeBackend.HealthSnapshot(
                    false, true, false, 0L, 0L,
                    "health_probe_exception:" + error.getClass().getSimpleName()));
        }
    }

    @Override public synchronized void setImpHandoff(SharedCameraGatewayBackend.ImpHandoff value) {
        impHandoff = Objects.requireNonNull(value, "impHandoff");
    }

    @Override public synchronized void setRecordingPreparationListener(
            SharedCameraGatewayBackend.RecordingPreparationListener listener) {
        recordingPreparationListener = Objects.requireNonNull(listener, "listener");
    }

    @Override public synchronized long recordingBitrateBitsPerSecond(
            CameraRuntimeSelection selection) {
        Objects.requireNonNull(selection, "selection");
        if (pipeline != null && selection.equals(activeSelection)) {
            long current = pipeline.recordingBitrateBitsPerSecond();
            if (current > 0L) return current;
        }
        return pipelineProvider.recordingBitrateBitsPerSecond(selection);
    }

    @Override public synchronized void requestRecording(RecordingMode mode) {
        requestedRecordingMode = Objects.requireNonNull(mode, "mode");
        recordingStartCancelled = false;

        recordingStorageLimitRequested = false;

    }

    @Override public synchronized void requestImpHandoff() {
        requestedRecordingMode = RecordingMode.IMP;

        impHandoffRequested = true;
    }

    @Override public synchronized void setRecordingStorageLimit(Runnable listener) {
        recordingStorageLimitListener = Objects.requireNonNull(listener, "listener");
    }

    @Override public synchronized boolean recordingStorageLimitRequested() {
        return recordingStorageLimitRequested;
    }

    private void recordingStorageLimitReached() {
        Runnable listener;
        synchronized (this) {
            recordingStorageLimitRequested = true;
            listener = recordingStorageLimitListener;
        }
        listener.run();
    }

    @Override public synchronized void cancelPendingRecording() {
        if (activeRecording == null) {
            requestedRecordingMode = null;
            recordingStartCancelled = true;

        }
        impHandoffRequested = false;
        recordingStorageLimitRequested = false;
    }

    @Override public void cancel(Command command) {
        Objects.requireNonNull(command, "command");
        cancelledThroughSequence.accumulateAndGet(command.operationSequence(), Math::max);
        if (command.operation() == Operation.START_RECORDING) cancelPendingRecording();
    }

    @Override public void execute(Command command, Completion completion) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(completion, "completion");
        try {
            if (deferPipelineReplacementUntilRecordingFinalizationHandoff(
                    command, completion)) return;
            switch (command.operation()) {
                case INITIALIZE, SWITCH_CAMERA, VERIFY_SETTING ->
                        executeVerifiedTransition(command, completion);
                case BIND_COMMITTED, RESTORE_EXACT, RECOVER ->
                        executeDirectBind(command, completion);
                case RELEASE -> executeRelease(command, completion);
                case START_RECORDING -> executeStartRecording(command, completion);
                case STOP_RECORDING -> executeStopRecording(command, completion);
                case CAPTURE_PHOTO -> executeCapturePhoto(command, completion);
            }
        } catch (RuntimeException error) {
            logger.error(LogCategory.CAMERA, "unspecified", null, logPrefix(command) + " outcome=exception", error);
            completion.complete(Result.recoveryRequired(
                    "runtime_exception:" + error.getClass().getSimpleName()));
        } finally {
            stopMediaOrientationTrackingIfUnbound();
        }
    }

    @SuppressWarnings({"java:S6541", "java:S3776"})
    // Keep verification, rollback, and binding adoption in one transition boundary.
    private void executeVerifiedTransition(Command command, Completion completion) {
        if (superseded(command)) {
            complete(command, completion, Result.cancelled(TRANSITION_SUPERSEDED));
            return;
        }
        if (capabilities == null) {
            CameraRuntimeSelection target = command.target().orElseThrow();
            Result targetResult = bindSelection(command, target, false);
            if (targetResult.outcome() == Outcome.READY || command.previous().isEmpty()) {
                complete(command, completion, targetResult);
                return;
            }
            complete(command, completion, bindSelection(
                    command, command.previous().orElseThrow(), true));
            return;
        }
        Result release = releaseCurrent();
        if (release.outcome() == Outcome.RECOVERY_REQUIRED) {
            complete(command, completion, release);
            return;
        }
        if (superseded(command)) {
            complete(command, completion, Result.cancelled(TRANSITION_SUPERSEDED));
            return;
        }
        CameraRuntimeSelection target = command.target().orElseThrow();
        var snapshot = capabilities.currentSnapshot();
        if (snapshot.isEmpty()) {
            complete(command, completion, Result.targetFailed("capability_snapshot_unavailable"));
            return;
        }
        pipelineProvider.startMediaOrientationTracking();
        SharedCameraVerificationSession session = new SharedCameraVerificationSession(
                selection -> pipelineProvider.create(selection, previewSurface), logger,
                com.dvid.dcam.feature.device.application.port.CameraVerificationClock.system(),
                this::observeBindingPipeline);
        CandidateKey requested = CandidateKey.forTuple(target.cameraId(), target.codec(),
                target.verificationPipelineId(), target.tuple());
        Optional<SelectedRecordingProfile> previousTargetSelection = snapshot.orElseThrow().cameras()
                .stream()
                .filter(camera -> camera.cameraId().equals(target.cameraId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "target camera absent from capability snapshot"))
                .selectedRecordingProfile();
        Optional<VerifyCameraSelectionUseCase.PreviousSelection> previous = command.previous()
                .map(value -> new VerifyCameraSelectionUseCase.PreviousSelection(
                        value.cameraId(), new SelectedRecordingProfile(value.codec(),
                        value.verificationPipelineId(), value.tuple())));
        VerifyCameraSelectionUseCase verifier = new VerifyCameraSelectionUseCase(
                session, capabilities.capabilityStore(), logger,
                com.dvid.dcam.feature.device.application.port.CameraVerificationClock.system(),
                ignored -> superseded(command)
                        ? command.healthGeneration() + 1 : command.healthGeneration());
        VerifyCameraSelectionUseCase.Result verified = verifier.execute(
                new VerifyCameraSelectionUseCase.Request(snapshot.orElseThrow(), requested,
                        previous, command.operationSequence(), command.healthGeneration()));
        if (superseded(command)) {
            Optional<CameraOperationContext> verificationContext = session.activeContext();
            if (verificationContext.isPresent()) {
                Result released = releaseVerification(session, verificationContext.orElseThrow());
                if (released.outcome() == Outcome.RECOVERY_REQUIRED) {
                    complete(command, completion, released);
                    return;
                }
            }
            restoreSelection(target, previousTargetSelection);
            complete(command, completion, Result.cancelled(TRANSITION_SUPERSEDED));
            return;
        }
        if (verified.verified()) {
            CandidateKey selected = verified.selectedCandidate().orElseThrow();
            CameraOperationContext verificationBinding =
                    verified.activeBinding().orElseThrow();
            CameraRuntimeSelection actual = runtimeSelection(selected);
            capabilities.applyRecordingFallback(requested, selected);
            Result adopted = adoptVerificationBinding(session, verificationBinding,
                    actual, false, verified.detail());
            if (adopted.outcome() != Outcome.READY) {
                restoreSelection(target, previousTargetSelection);
            }
            complete(command, completion, adopted);
            return;
        }
        if (verified.completion() == VerifyCameraSelectionUseCase.Completion.ROLLED_BACK
                && verified.activeBinding().isPresent() && command.previous().isPresent()) {
            CameraRuntimeSelection previousSelection = command.previous().orElseThrow();
            complete(command, completion, adoptVerificationBinding(session,
                    verified.activeBinding().orElseThrow(), previousSelection, true,
                    "rollback_retained"));
            return;
        }
        complete(command, completion, verified.recoveryRequired()
                ? Result.recoveryRequired(verified.detail())
                : Result.targetFailed(verified.detail()));
    }

    private void restoreSelection(CameraRuntimeSelection target,
            Optional<SelectedRecordingProfile> previousTargetSelection) {
        capabilities.restoreSelectedRecordingProfile(target.cameraId(), previousTargetSelection);
    }

    private void observeBindingPipeline(SharedCameraCapturePipeline value) {
        bindingPipeline = value;
        if (value != null) value.setPreviewExpected(previewExpected);
    }

    private void stopMediaOrientationTrackingIfUnbound() {
        synchronized (this) {
            if (pipeline != null || bindingPipeline != null) return;
        }
        pipelineProvider.stopMediaOrientationTracking();
    }

    private Result adoptVerificationBinding(
            SharedCameraVerificationSession session,
            CameraOperationContext context,
            CameraRuntimeSelection selection,
            boolean rollback,
            String detail) {
        SharedCameraCapturePipeline retained;
        try {
            retained = session.detachActiveBinding(context);
        } catch (RuntimeException error) {
            Result released = releaseVerification(session, context);
            return released.outcome() == Outcome.RECOVERY_REQUIRED
                    ? released : Result.recoveryRequired(
                            "verification_handoff_failed:" + message(error));
        }
        try {
            synchronized (this) {
                retained.setPreviewExpected(previewExpected);
                pipeline = retained;
                activeSelection = selection;
                activeContext = context;
                bindingPipeline = null;
            }
        } catch (RuntimeException error) {
            bindingPipeline = null;
            try {
                CameraOperationResult release = retained.release(context);
                if (release.outcome().requiresRuntimeRecovery()) {
                    return Result.recoveryRequired(
                            "verification_handoff_release_failed:" + release.detail());
                }
            } catch (RuntimeException releaseError) {
                return Result.recoveryRequired(
                        "verification_handoff_release_exception:" + message(releaseError));
            }
            return Result.recoveryRequired("verification_handoff_failed:" + message(error));
        }
        return rollback
                ? Result.rolledBackReady(selection, context, detail)
                : Result.ready(selection, context, detail + "_retained");
    }

    static Result releaseVerification(
            SharedCameraVerificationSession session, CameraOperationContext context) {
        try {
            CameraOperationResult release = session.release(context);
            return release.outcome().requiresRuntimeRecovery()
                    ? Result.recoveryRequired("verification_release_failed:" + release.detail())
                    : Result.pass("verification_released");
        } catch (RuntimeException error) {
            return Result.recoveryRequired("verification_release_exception:"
                    + error.getClass().getSimpleName());
        }
    }

    private boolean superseded(Command command) {
        return cancelledThroughSequence.get() >= command.operationSequence();
    }

    private static CameraRuntimeSelection runtimeSelection(CandidateKey candidate) {
        return new CameraRuntimeSelection(candidate.cameraId(),
                candidate.verificationPipelineId(), candidate.codec(),
                candidate.tuple().orElseThrow());
    }

    private void executeDirectBind(Command command, Completion completion) {
        Result result = bindSelection(command, command.target().orElseThrow(), false);
        if (result.outcome() == Outcome.TARGET_FAILED) {
            result = Result.recoveryRequired(result.detail());
        }
        complete(command, completion, result);
    }

    private Result bindSelection(
            Command command, CameraRuntimeSelection selection, boolean rollback) {
        return bindSelectionAttempt(command, selection, rollback).result();
    }

    private SelectionBindResult bindSelectionAttempt(
            Command command, CameraRuntimeSelection selection, boolean rollback) {
        Result release = releaseCurrent();
        if (release.outcome() == Outcome.RECOVERY_REQUIRED) {
            return new SelectionBindResult(release, Optional.empty());
        }
        SharedCameraCapturePipeline next;
        try {
            pipelineProvider.startMediaOrientationTracking();
            next = pipelineProvider.create(selection, previewSurface);
            bindingPipeline = next;
            next.setPreviewExpected(previewExpected);
        } catch (RuntimeException error) {
            logger.error(LogCategory.CAMERA, "unspecified", null, logPrefix(command) + " stage=pipeline_create pipeline="
                    + selection.verificationPipelineId(), error);
            Result result = rollback
                    ? Result.recoveryRequired("rollback_pipeline_create_failed")
                    : Result.targetFailed("pipeline_create_failed");
            return new SelectionBindResult(result, Optional.empty());
        }
        CameraOperationContext context = operationContext(command, selection);
        CameraOperationResult bind;
        try {
            bind = next.bindSession(context);
        } finally {
            bindingPipeline = null;
        }
        if (bind.outcome() == CameraOperationOutcome.PASS) {
            synchronized (this) {
                next.setPreviewExpected(previewExpected);
                pipeline = next;
                activeSelection = selection;
                activeContext = context;
            }
            Result result = rollback
                    ? Result.rolledBackReady(selection, context, "rollback_bound")
                    : Result.ready(selection, context, "selection_bound");
            return new SelectionBindResult(result, Optional.empty());
        }
        next.release(context);
        if (bind.outcome().requiresRuntimeRecovery()) {
            return new SelectionBindResult(
                    Result.recoveryRequired("bind_failed:" + bind.detail()),
                    Optional.of(bind.outcome()));
        }
        Result result = rollback
                ? Result.recoveryRequired("rollback_bind_failed:" + bind.detail())
                : Result.targetFailed("bind_failed:" + bind.detail());
        return new SelectionBindResult(result, Optional.of(bind.outcome()));
    }

    private record SelectionBindResult(Result result,
            Optional<CameraOperationOutcome> failureOutcome) {}

    private static final class PendingRecordingFinalization {
        private final SharedCameraMediaLifecycle.RecordingCapture recording;
        private DeferredBackendCommand deferredCommand;

        private PendingRecordingFinalization(
                SharedCameraMediaLifecycle.RecordingCapture recording) {
            this.recording = recording;
        }
    }

    private record DeferredBackendCommand(Command command, Completion completion) {}

    private record RecordingFinalizationRequest(
            Command command,
            SharedCameraCapturePipeline current,
            CameraOperationContext operationContext,
            SharedCameraMediaLifecycle.RecordingCapture recording,
            PendingRecordingFinalization pendingFinalization,
            long stopStartedAtNanos,
            long inputStoppedAtNanos,
            AtomicBoolean finalizationSucceeded,
            Runnable startHandoffWhenReady) {}

    private void executeRelease(Command command, Completion completion) {
        Result result;
        try {
            result = releaseCurrent();
        } finally {
            pipelineProvider.stopMediaOrientationTracking();
        }
        complete(command, completion, result.outcome() == Outcome.RECOVERY_REQUIRED
                ? result : Result.pass("camera_released"));
    }

    private Result releaseCurrent() {
        SharedCameraCapturePipeline current;
        CameraOperationContext context;
        synchronized (this) {
            current = pipeline;
            context = activeContext;
            pipeline = null;
            activeSelection = null;
            activeContext = null;
        }
        if (current == null || context == null) return Result.pass("already_released");
        CameraOperationResult release = current.release(context);
        if (release.outcome().requiresRuntimeRecovery()) {
            return Result.recoveryRequired("release_failed:" + release.detail());
        }
        return Result.pass("released");
    }

    @SuppressWarnings("java:S3776")
    private void executeStartRecording(Command command, Completion completion) {
        long startedAtNanos = System.nanoTime();
        SharedCameraCapturePipeline current = requirePipeline(command, completion, RECORDING);
        if (current == null) return;
        CameraRuntimeSelection original = Objects.requireNonNull(
                activeSelection, ACTIVE_SELECTION);
        CameraRuntimeSelection recordingSelection = command.target().orElse(original);
        RecordingMode mode;
        synchronized (this) {
            mode = requestedRecordingMode;
        }
        if (recordingStartCancelled()) {
            clearRequestedRecordingMode();
            clearRecordingPreparationNotice();
            complete(command, completion, Result.cancelled(RECORDING_START_CANCELLED));
            return;
        }
        if (mode == null || mode == RecordingMode.IDLE) {
            clearRequestedRecordingMode();
            clearRecordingPreparationNotice();
            captureEvents.captureFailed(RECORDING, "Recording request missing");
            complete(command, completion, Result.blocked("recording_request_missing"));
            return;
        }
        SharedCameraMediaLifecycle.RecordingCapture prepared;
        try {
            prepared = mediaLifecycle.prepareRecording(
                    mode, recordingBitrateBitsPerSecond(recordingSelection));
        } catch (SharedCameraMediaLifecycle.PreparationException error) {
            clearRequestedRecordingMode();
            if (error.retryable()) {
                showRecordingPreparationNotice(error.getMessage());
                captureEvents.recordingStartBlocked();
                logger.warn(LogCategory.CAMERA, "capture_start_rejected", "STORAGE_PREPARING", "Block " + mode + " recording start because SD card preparation is in progress. "
                        + "No command retained; press record again after storage is ready.", error);
                complete(command, completion, Result.blocked(
                        "record_prepare:storage_preparing"));
                return;
            }
            if (error.unavailable()) {
                showRecordingPreparationUnavailable(error.getMessage());
                captureEvents.captureStorageUnavailable();
                captureEvents.recordingStartBlocked();
                logger.warn(LogCategory.CAMERA, "capture_start_rejected", "STORAGE_UNAVAILABLE", "Block " + mode + " recording start because SD card is unavailable. "
                        + "No command retained.", error);
                complete(command, completion, Result.blocked(
                        "record_prepare:storage_unavailable"));
                return;
            }
            clearRecordingPreparationNotice();
            captureEvents.captureFailed(error.operation(), error.getMessage());
            logger.warn(LogCategory.CAMERA, "capture_start_rejected", null, logPrefix(command) + " stage=record_prepare outcome=blocked", error);
            complete(command, completion, Result.blocked("record_prepare:" + error.getMessage()));
            return;
        }
        if (recordingStartCancelled()) {
            mediaLifecycle.abortRecordingStart(prepared);
            clearRequestedRecordingMode();
            clearRecordingPreparationNotice();
            complete(command, completion, Result.cancelled(RECORDING_START_CANCELLED));
            return;
        }
        long mediaPreparedAtNanos = System.nanoTime();
        if (!recordingSelection.equals(original)) {
            SelectionBindResult recordingBinding = bindSelectionAttempt(
                    command, recordingSelection, false);
            if (recordingBinding.result().outcome() != Outcome.READY) {
                mediaLifecycle.abortRecordingStart(prepared);
                clearRequestedRecordingMode();
                clearRecordingPreparationNotice();
                completeRecordingBindingFailure(command, completion, original,
                        recordingBinding.result());
                return;
            }
            current = Objects.requireNonNull(pipeline, "pipeline");
        }
        if (recordingStartCancelled()) {
            mediaLifecycle.abortRecordingStart(prepared);
            clearRequestedRecordingMode();
            clearRecordingPreparationNotice();
            complete(command, completion, cancelledWithActiveBinding(
                    RECORDING_START_CANCELLED));
            return;
        }
        clearRequestedRecordingMode();
        clearRecordingPreparationNotice();
        CameraRuntimeSelection selection = Objects.requireNonNull(
                activeSelection, ACTIVE_SELECTION);
        int recordingRotationDegrees = pipelineProvider.mediaRotationDegrees(
                selection, current.outputRotationDegrees());
        long sessionReadyAtNanos = System.nanoTime();
        CameraOperationResult start = prepared.recordingOutput() == null
                ? current.startEncoder(runtimeContext(), prepared.outputFile(),
                        prepared.fileSizeLimitBytes(), this::recordingStorageLimitReached,
                        recordingRotationDegrees)
                : current.startEncoder(runtimeContext(), prepared.recordingOutput(),
                        prepared.fileSizeLimitBytes(), this::recordingStorageLimitReached,
                        recordingRotationDegrees);
        long encoderCompletedAtNanos = System.nanoTime();
        logger.info(LogCategory.CAMERA, "unspecified", "Complete recording startup pipeline. Mode: " + mode
                + ". Media preparation: "
                + elapsedMillis(startedAtNanos, mediaPreparedAtNanos)
                + " ms. Session selection: "
                + elapsedMillis(mediaPreparedAtNanos, sessionReadyAtNanos)
                + " ms. Encoder startup: "
                + elapsedMillis(sessionReadyAtNanos, encoderCompletedAtNanos)
                + TOTAL_DURATION + elapsedMillis(startedAtNanos, encoderCompletedAtNanos)
                + " ms. Outcome: " + start.outcome() + ".");
        if (commitStartedRecording(prepared, start)) {
            CameraOperationContext cancelContext = runtimeContext();
            current.stopEncoder(cancelContext);
            current.finalizeEncoder(cancelContext);
            mediaLifecycle.abortRecordingStart(prepared);
            complete(command, completion, cancelledWithActiveBinding(
                    RECORDING_START_CANCELLED));
            return;
        }
        if (start.outcome() != CameraOperationOutcome.PASS) {
            mediaLifecycle.abortRecordingStart(prepared);
            captureEvents.captureFailed(RECORDING, start.detail());
            Result result = start.outcome().requiresRuntimeRecovery()
                    ? Result.recoveryRequired("record_start:" + start.detail())
                    : blockedWithActiveBinding("record_start:" + start.detail());
            complete(command, completion, result);
            return;
        }
        captureEvents.recordingStarted(mode, prepared.mediaFile().getFileName());
        complete(command, completion, passWithActiveBinding("recording_started"));
    }

    private void completeRecordingBindingFailure(Command command, Completion completion,
            CameraRuntimeSelection original, Result bindingFailure) {
        captureEvents.captureFailed(RECORDING, bindingFailure.detail());
        if (bindingFailure.outcome() == Outcome.RECOVERY_REQUIRED) {
            complete(command, completion, bindingFailure);
            return;
        }
        Result restored = bindSelection(command, original, true);
        if (restored.outcome() != Outcome.ROLLED_BACK_READY) {
            complete(command, completion, Result.recoveryRequired(
                    "recording_restore:" + restored.detail()));
            return;
        }
        complete(command, completion, blockedWithActiveBinding(
                "recording_bind_failed:" + bindingFailure.detail()));
    }

    private void executeStopRecording(Command command, Completion completion) {
        long stopStartedAtNanos = System.nanoTime();
        SharedCameraCapturePipeline current = requirePipeline(command, completion, RECORDING);
        if (current == null) return;
        SharedCameraMediaLifecycle.RecordingCapture recording;
        boolean handoff;
        synchronized (this) {
            recording = activeRecording;
            handoff = impHandoffRequested;
            impHandoffRequested = false;
        }
        if (recording == null) {
            complete(command, completion, Result.pass("recording_already_stopped"));
            return;
        }
        CameraOperationContext operationContext = runtimeContext();
        CameraOperationResult stop = current.stopEncoder(operationContext);
        long inputStoppedAtNanos = System.nanoTime();
        Result runtimeResult = resultForRecordingInputStop(stop);
        if (runtimeResult.outcome() != Outcome.PASS) {
            clearActiveRecording(recording);
            takeRecordingStorageLimitRequested();
            logger.info(LogCategory.CAMERA, "unspecified", "Stop recording '" + recording.mediaFile().getFileName()
                    + "' failed before encoder finalization. Stop outcome: " + stop.outcome()
                    + ". Beginning staged-output failure cleanup.");
            mediaLifecycle.failRecording(recording);
            captureEvents.captureFailed(RECORDING, runtimeResult.detail());
            complete(command, completion, runtimeResult);
            return;
        }
        AtomicBoolean runtimeReleased = new AtomicBoolean();
        AtomicBoolean finalizationSucceeded = new AtomicBoolean();
        AtomicBoolean handoffStarted = new AtomicBoolean();
        PendingRecordingFinalization pendingFinalization =
                beginRecordingFinalizationHandoff(recording);
        Runnable startHandoffWhenReady = () -> {
            if (handoff && runtimeReleased.get() && finalizationSucceeded.get()
                    && handoffStarted.compareAndSet(false, true)) {
                impHandoff.startImpRecording();
            }
        };
        RecordingFinalizationRequest finalizationRequest = new RecordingFinalizationRequest(
                command, current, operationContext, recording, pendingFinalization,
                stopStartedAtNanos,
                inputStoppedAtNanos, finalizationSucceeded, startHandoffWhenReady);
        try {
            recordingFinalizationExecutor.execute(
                    () -> finalizeStoppedRecording(finalizationRequest));
        } catch (RuntimeException error) {
            clearActiveRecording(recording);
            takeRecordingStorageLimitRequested();
            mediaLifecycle.failRecording(recording);
            logger.error(LogCategory.CAMERA, "media_finalization_failed", null, logPrefix(command)
                    + " stage=record_finalize_schedule outcome=failed", error);
            captureEvents.captureFailed(RECORDING,
                    "Could not schedule recording finalization: " + message(error));
            completeRecordingFinalizationHandoff(pendingFinalization);
            complete(command, completion,
                    Result.recoveryRequired("record_finalize_schedule:" + message(error)));
            return;
        }
        complete(command, completion, runtimeResult);
        runtimeReleased.set(true);
        startHandoffWhenReady.run();
    }

    private void finalizeStoppedRecording(RecordingFinalizationRequest request) {
        try {
            finalizeStoppedRecordingHandoff(request);
        } finally {
            completeRecordingFinalizationHandoff(request.pendingFinalization());
        }
    }

    private void finalizeStoppedRecordingHandoff(RecordingFinalizationRequest request) {
        Command command = request.command();
        SharedCameraCapturePipeline current = request.current();
        CameraOperationContext operationContext = request.operationContext();
        SharedCameraMediaLifecycle.RecordingCapture recording = request.recording();
        long stopStartedAtNanos = request.stopStartedAtNanos();
        long inputStoppedAtNanos = request.inputStoppedAtNanos();
        AtomicBoolean finalizationSucceeded = request.finalizationSucceeded();
        Runnable startHandoffWhenReady = request.startHandoffWhenReady();
        CameraOperationResult finalize;
        try {
            finalize = current.finalizeEncoder(operationContext, true);
        } catch (RuntimeException error) {
            clearActiveRecording(recording);
            failStoppedRecordingFinalization(command, recording,
                    "Encoder finalization threw an exception", error);
            return;
        }
        long encoderCompletedAtNanos = System.nanoTime();
        clearActiveRecording(recording);
        if (finalize.outcome() != CameraOperationOutcome.PASS) {
            takeRecordingStorageLimitRequested();
            logger.info(LogCategory.CAMERA, "media_finalization_failed", "Finalize recording '" + recording.mediaFile().getFileName()
                    + "' failed after camera input stopped. Outcome: " + finalize.outcome()
                    + ". Beginning staged-output failure cleanup.");
            mediaLifecycle.failRecording(recording);
            captureEvents.captureFailed(RECORDING,
                    "record_finalize:" + finalize.detail());
            return;
        }
        try {
            mediaLifecycle.finalizeRecording(recording, current.finalizedDurationUs(),
                    new SharedCameraMediaLifecycle.Completion() {
                        @Override public void onSuccess(java.io.File finalFile) {
                            long completedAtNanos = System.nanoTime();
                            logger.info(LogCategory.CAMERA, "media_finalization_completed", "Complete recording media finalization for '"
                                    + recording.mediaFile().getFileName()
                                    + "'. Camera input stop: "
                                    + elapsedMillis(stopStartedAtNanos, inputStoppedAtNanos)
                                    + " ms. Encoder drain and finish: "
                                    + elapsedMillis(inputStoppedAtNanos, encoderCompletedAtNanos)
                                    + " ms. Media finalization: "
                                    + elapsedMillis(encoderCompletedAtNanos, completedAtNanos)
                                    + TOTAL_DURATION
                                    + elapsedMillis(stopStartedAtNanos, completedAtNanos) + " ms.");
                            boolean stoppedForStorage = takeRecordingStorageLimitRequested();
                            if (stoppedForStorage) {
                                captureEvents.recordingStoppedForStorage(
                                        recording.mediaFile().getFileName());
                            } else {
                                captureEvents.recordingCompleted(
                                        recording.mediaFile().getFileName());
                            }
                            finalizationSucceeded.set(true);
                            startHandoffWhenReady.run();
                        }

                        @Override public void onFailure(Exception failure) {
                            takeRecordingStorageLimitRequested();
                            String detail = recording.outputFile().isFile()
                                    ? "Finalization failed; staged media preserved: "
                                            + message(failure)
                                    : "Finalization failed after staged media left Temp; "
                                            + "recovery will reconcile final publication: "
                                            + message(failure);
                            logger.error(LogCategory.CAMERA, "media_finalization_failed", null, logPrefix(command)
                                    + " stage=record_finalize outcome=failed", failure);
                            captureEvents.captureFailed(FINALIZATION, detail);
                        }
                    });
        } catch (RuntimeException error) {
            failStoppedRecordingFinalization(command, recording,
                    "Media finalization handoff threw an exception", error);
        }
    }

    private void failStoppedRecordingFinalization(
            Command command,
            SharedCameraMediaLifecycle.RecordingCapture recording,
            String stage,
            RuntimeException error) {
        takeRecordingStorageLimitRequested();
        mediaLifecycle.failRecording(recording);
        logger.error(LogCategory.CAMERA, "media_finalization_failed", null, logPrefix(command) + " stage=record_finalize outcome=failed", error);
        captureEvents.captureFailed(FINALIZATION, stage + ": " + message(error));
    }

    private synchronized void clearActiveRecording(
            SharedCameraMediaLifecycle.RecordingCapture recording) {
        if (activeRecording == recording) activeRecording = null;
    }

    private synchronized PendingRecordingFinalization beginRecordingFinalizationHandoff(
            SharedCameraMediaLifecycle.RecordingCapture recording) {
        if (pendingRecordingFinalization != null) {
            throw new IllegalStateException("recording_finalization_handoff_already_pending");
        }
        PendingRecordingFinalization pending = new PendingRecordingFinalization(recording);
        pendingRecordingFinalization = pending;
        return pending;
    }

    private boolean deferPipelineReplacementUntilRecordingFinalizationHandoff(
            Command command, Completion completion) {
        if (!replacesActivePipeline(command.operation())) return false;
        String recordingName;
        synchronized (this) {
            PendingRecordingFinalization pending = pendingRecordingFinalization;
            if (pending == null) return false;
            if (pending.deferredCommand != null) {
                throw new IllegalStateException("recording_finalization_command_already_deferred");
            }
            pending.deferredCommand = new DeferredBackendCommand(command, completion);
            recordingName = pending.recording.mediaFile().getFileName();
        }
        logger.info(LogCategory.CAMERA, "unspecified", "Defer " + command.operation() + " camera command until encoder "
                + "finalization hands recording '" + recordingName + "' to media storage.");
        return true;
    }

    private static boolean replacesActivePipeline(Operation operation) {
        return switch (operation) {
            case INITIALIZE, SWITCH_CAMERA, VERIFY_SETTING, BIND_COMMITTED,
                    RESTORE_EXACT, RECOVER, RELEASE -> true;
            case START_RECORDING, STOP_RECORDING, CAPTURE_PHOTO -> false;
        };
    }

    private void completeRecordingFinalizationHandoff(
            PendingRecordingFinalization completed) {
        DeferredBackendCommand deferredCommand;
        synchronized (this) {
            if (pendingRecordingFinalization != completed) return;
            pendingRecordingFinalization = null;
            deferredCommand = completed.deferredCommand;
        }
        if (deferredCommand == null) return;
        logger.info(LogCategory.CAMERA, "unspecified", "Resume deferred " + deferredCommand.command().operation()
                + " camera command after encoder finalization handed recording '"
                + completed.recording.mediaFile().getFileName() + "' to media storage.");
        execute(deferredCommand.command(), deferredCommand.completion());
    }

    private synchronized boolean takeRecordingStorageLimitRequested() {
        boolean requested = recordingStorageLimitRequested;
        recordingStorageLimitRequested = false;
        return requested;
    }

    private Result resultForRecordingInputStop(CameraOperationResult stop) {
        if (stop.outcome().requiresRuntimeRecovery()) {
            return Result.recoveryRequired("record_stop:" + stop.detail());
        }
        if (stop.outcome() != CameraOperationOutcome.PASS) {
            return Result.blocked("record_stop:" + stop.detail());
        }
        return Result.pass("recording_input_stopped");
    }

    private void executeCapturePhoto(Command command, Completion completion) {
        if (requirePipeline(command, completion, PHOTO) == null) return;
        CameraRuntimeSelection original = Objects.requireNonNull(activeSelection, ACTIVE_SELECTION);
        boolean retainedRecordingBinding;
        synchronized (this) {
            retainedRecordingBinding = activeRecording != null
                    || pendingRecordingFinalization != null;
        }
        Optional<CandidateKey> requestedPhotoCandidate = retainedRecordingBinding
                || capabilities == null
                ? Optional.empty()
                : capabilities.requestedCandidate(original.cameraId().value());
        if (!retainedRecordingBinding && capabilities != null
                && requestedPhotoCandidate.isEmpty()) {
            captureEvents.photoFailed(PHOTO, "Selected photo quality is unavailable");
            complete(command, completion,
                    Result.blocked("standalone_photo_selection_unavailable"));
            return;
        }
        executeCapturePhotoCandidate(command, completion, original,
                requestedPhotoCandidate.orElse(null), new HashSet<>());
    }

    @SuppressWarnings({"java:S6541", "java:S3776"})
    // Keep photo fallback, restoration, and asynchronous finalization in one state boundary.
    private void executeCapturePhotoCandidate(Command command, Completion completion,
            CameraRuntimeSelection original, CandidateKey requestedPhoto,
            Set<CandidateKey> attemptedPhotoCandidates) {
        long startedAtNanos = System.nanoTime();
        SharedCameraCapturePipeline current = requirePipeline(command, completion, PHOTO);
        if (current == null) return;
        if (requestedPhoto != null && !attemptedPhotoCandidates.add(requestedPhoto)) {
            captureEvents.photoFailed(PHOTO, "No lower supported photo resolution remains");
            complete(command, completion,
                    Result.blocked("standalone_photo_fallback_exhausted"));
            return;
        }
        boolean verifyStandaloneImage = requestedPhoto != null
                && standaloneImageUnverified(requestedPhoto);
        boolean temporaryBinding = requestedPhoto != null
                && !requestedPhoto.tuple().orElseThrow().imageMode()
                        .equals(original.tuple().imageMode());
        if (temporaryBinding) {
            try {
                mediaLifecycle.requirePhotoStorage();
            } catch (SharedCameraMediaLifecycle.PreparationException error) {
                handlePhotoPreparationFailure(command, completion, error,
                        Result.pass(PHOTO_BINDING_UNCHANGED));
                return;
            }
            SelectionBindResult photoBinding = bindSelectionAttempt(
                    command, runtimeSelection(requestedPhoto), false);
            boolean bindFailureConfirmed = requestedPhoto != null
                    && photoBinding.failureOutcome()
                            .filter(SharedCameraRuntimeBackend::isDurableStandaloneFailure)
                            .isPresent();
            if (photoBinding.result().outcome() != Outcome.READY
                    && requestedPhoto != null
                    && photoBinding.failureOutcome()
                            .filter(outcome -> outcome == CameraOperationOutcome.CANDIDATE_SUSPECT)
                            .isPresent()) {
                logger.info(LogCategory.CAMERA, "unspecified", logPrefix(command)
                        + " stage=standalone_photo_bind_confirmation action=retry");
                photoBinding = bindSelectionAttempt(
                        command, runtimeSelection(requestedPhoto), false);
                bindFailureConfirmed = photoBinding.result().outcome() != Outcome.READY
                        && photoBinding.failureOutcome()
                                .filter(CameraOperationOutcome::isCandidateFailure)
                                .isPresent();
            }
            if (photoBinding.result().outcome() != Outcome.READY) {
                Optional<CandidateKey> fallback = Optional.empty();
                if (bindFailureConfirmed) {
                    fallback = recordStandaloneImageOutcome(requestedPhoto,
                            VerificationOutcome.DEFINITIVE_UNSUPPORTED);
                }
                Result restored = bindSelection(command, original, true);
                Optional<Result> restoreFailure = restorationFailure(restored);
                if (restoreFailure.isPresent()) {
                    captureEvents.photoFailed(PHOTO,
                            "Camera restore failed after photo binding failed");
                    complete(command, completion, restoreFailure.orElseThrow());
                    return;
                }
                if (retryPhotoFallback(command, completion, original, requestedPhoto,
                        fallback, attemptedPhotoCandidates)) return;
                captureEvents.photoFailed(PHOTO, photoBinding.result().detail());
                complete(command, completion, Result.blocked("standalone_photo_bind_failed"));
                return;
            }
            current = Objects.requireNonNull(pipeline, "pipeline");
        }
        SharedCameraMediaLifecycle.PhotoCapture photo;
        try {
            photo = mediaLifecycle.preparePhoto();
        } catch (SharedCameraMediaLifecycle.PreparationException error) {
            Result restored = temporaryBinding ? bindSelection(command, original, true)
                    : Result.pass(PHOTO_BINDING_UNCHANGED);
            handlePhotoPreparationFailure(command, completion, error, restored);
            return;
        }
        clearRecordingPreparationNotice();

        CameraRuntimeSelection photoSelection = Objects.requireNonNull(
                activeSelection, ACTIVE_SELECTION);
        int photoRotationDegrees = pipelineProvider.mediaRotationDegrees(
                photoSelection, current.outputRotationDegrees());
        long preparedAtNanos = System.nanoTime();
        CameraOperationResult capture = photo.segmentedAesGcmOutput() == null
                ? current.captureJpeg(
                        runtimeContext(), photo.outputFile(), photoRotationDegrees)
                : current.captureJpeg(
                        runtimeContext(), photo.segmentedAesGcmOutput(), photoRotationDegrees);
        boolean captureFailureConfirmed = requestedPhoto != null
                && isDurableStandaloneFailure(capture.outcome());
        if (requestedPhoto != null
                && capture.outcome() == CameraOperationOutcome.CANDIDATE_SUSPECT) {
            photo.discard();
            logger.info(LogCategory.CAMERA, "unspecified", logPrefix(command)
                    + " stage=standalone_photo_capture_confirmation action=retry");
            capture = photo.segmentedAesGcmOutput() == null
                    ? current.captureJpeg(
                            runtimeContext(), photo.outputFile(), photoRotationDegrees)
                    : current.captureJpeg(
                            runtimeContext(), photo.segmentedAesGcmOutput(), photoRotationDegrees);
            captureFailureConfirmed = capture.outcome().isCandidateFailure();
        }
        long capturedAtNanos = System.nanoTime();
        logger.info(LogCategory.CAMERA, "unspecified", "Complete photo capture pipeline. Preparation: "
                + elapsedMillis(startedAtNanos, preparedAtNanos)
                + " ms. Camera capture: " + elapsedMillis(preparedAtNanos, capturedAtNanos)
                + TOTAL_DURATION + elapsedMillis(startedAtNanos, capturedAtNanos)
                + " ms. Outcome: " + capture.outcome() + ".");
        if (capture.outcome() != CameraOperationOutcome.PASS) {
            Optional<CandidateKey> fallback = Optional.empty();
            if (captureFailureConfirmed) {
                fallback = recordStandaloneImageOutcome(requestedPhoto,
                        VerificationOutcome.DEFINITIVE_UNSUPPORTED);
            }
            photo.discard();
            Result restored = temporaryBinding ? bindSelection(command, original, true)
                    : Result.pass(PHOTO_BINDING_UNCHANGED);
            CameraOperationResult failedCapture = capture;
            Optional<Result> restoreFailure = restorationFailure(restored);
            if (restoreFailure.isEmpty()
                    && retryPhotoFallback(command, completion, original, requestedPhoto,
                            fallback, attemptedPhotoCandidates)) return;
            if (failedCapture.outcome() == CameraOperationOutcome.BLOCKED_EXTERNAL
                    && failedCapture.failureClass() == CameraFailureClass.JPEG_OUTPUT) {
                captureEvents.captureStorageUnavailable();
            } else {
                captureEvents.photoFailed(PHOTO, failedCapture.detail());
            }
            Result result = restoreFailure.orElseGet(() ->
                    failedCapture.outcome().requiresRuntimeRecovery()
                            ? Result.recoveryRequired(
                                    "photo_capture:" + failedCapture.detail())
                            : Result.blocked("photo_capture:" + failedCapture.detail()));
            complete(command, completion, result);
            return;
        }
        if (verifyStandaloneImage) {
            var expected = requestedPhoto.imageMode().orElseThrow().resolution().actual();
            OptionalInt sensorOrientationDegrees =
                    capabilities.sensorOrientationDegrees(requestedPhoto.cameraId());
            if (sensorOrientationDegrees.isEmpty()) {
                logger.warn(LogCategory.CAMERA, "unspecified", null, "Could not verify captured photo dimensions for camera "
                        + requestedPhoto.cameraId().value()
                        + " because sensor orientation is unavailable.", null);
            } else {
                Optional<CameraResolution> actual = photo.segmentedAesGcmOutput() == null
                        ? JpegDimensions.read(photo.outputFile())
                                .map(size -> new CameraResolution(size.width(), size.height()))
                        : current.diagnostics(runtimeContext()).capturedJpegResolution();
                int outputRotationDegrees = photoRotationDegrees;
                boolean matches = actual.filter(value ->
                        expected.matchesConsideringRotation(value,
                                outputRotationDegrees)).isPresent();
                if (!matches) {
                    logger.warn(LogCategory.CAMERA, "unspecified", null, "Captured photo dimensions do not match selected quality for camera "
                            + requestedPhoto.cameraId().value() + ". Expected: " + expected
                            + ". Actual: " + actual.map(Object::toString).orElse("unavailable")
                            + ". Output rotation: " + outputRotationDegrees + " degrees.", null);
                    Optional<CandidateKey> fallback = recordStandaloneImageOutcome(
                            requestedPhoto, VerificationOutcome.DEFINITIVE_UNSUPPORTED);
                    photo.discard();
                    Result restored = temporaryBinding
                            ? bindSelection(command, original, true)
                            : Result.pass(PHOTO_BINDING_UNCHANGED);
                    Optional<Result> restoreFailure = restorationFailure(restored);
                    if (restoreFailure.isEmpty()
                            && retryPhotoFallback(command, completion, original, requestedPhoto,
                                    fallback, attemptedPhotoCandidates)) return;
                    String detail = "Captured photo dimensions did not match the selected quality";
                    captureEvents.photoFailed(PHOTO, detail);
                    complete(command, completion, restoreFailure.orElseGet(() ->
                            Result.blocked("standalone_photo_dimensions_mismatch")));
                    return;
                }
                recordStandaloneImageOutcome(
                        requestedPhoto, VerificationOutcome.VERIFIED_PASS);
            }
        }
        captureEvents.photoSaving();
        mediaLifecycle.finalizePhoto(photo, new SharedCameraMediaLifecycle.Completion() {
            @Override public void onSuccess(java.io.File finalFile) {
                captureEvents.photoSaved(photo.mediaFile().getFileName());
                complete(command, completion, passWithActiveBinding("photo_finalized"));
            }

            @Override public void onFailure(Exception failure) {
                String detail = "Finalization failed; staged media preserved: "
                        + message(failure);
                logger.error(LogCategory.CAMERA, "media_finalization_failed", null, logPrefix(command) + " stage=photo_finalize outcome=failed", failure);
                captureEvents.photoFailed(FINALIZATION, detail);
                complete(command, completion, blockedWithActiveBinding(
                        "photo_finalize_failed"));
            }
        });
    }

    private boolean standaloneImageUnverified(CandidateKey requested) {
        try {
            return capabilities.standaloneImageOutcome(requested) == VerificationOutcome.UNKNOWN;
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, "standalone_photo_evidence_lookup_failed", error);
            return true;
        }
    }

    private static boolean isDurableStandaloneFailure(CameraOperationOutcome outcome) {
        return outcome == CameraOperationOutcome.DEFINITIVE_CANDIDATE_FAILURE;
    }

    private static Optional<Result> restorationFailure(Result restored) {
        return switch (restored.outcome()) {
            case PASS, ROLLED_BACK_READY -> Optional.empty();
            case RECOVERY_REQUIRED -> Optional.of(restored);
            default -> Optional.of(Result.recoveryRequired(
                    "photo_restore:" + restored.detail()));
        };
    }
    private void handlePhotoPreparationFailure(
            Command command,
            Completion completion,
            SharedCameraMediaLifecycle.PreparationException error,
            Result restored) {
        Optional<Result> restorationFailure = restorationFailure(restored);
        if (restorationFailure.isPresent()) {
            clearRecordingPreparationNotice();
            String detail = "Photo capture stopped because camera restore failed while storage was checked: "
                    + restorationFailure.get().detail();
            captureEvents.photoFailed(PHOTO, detail);
            logger.warn(LogCategory.CAMERA, "unspecified", null, detail, error);
            complete(command, completion, restorationFailure.get());
            return;
        }
        if (error.retryable()) {
            showRecordingPreparationNotice(error.getMessage());
            logger.warn(LogCategory.CAMERA, "capture_start_rejected", "STORAGE_PREPARING", "Block photo capture because SD card preparation is in progress. "
                    + "No command retained; press photo again after storage is ready.", error);
            complete(command, completion, Result.blocked(
                    "photo_prepare:storage_preparing"));
            return;
        }
        if (error.unavailable()) {
            showRecordingPreparationUnavailable(error.getMessage());
            captureEvents.captureStorageUnavailable();
            logger.warn(LogCategory.CAMERA, "capture_start_rejected", "STORAGE_UNAVAILABLE", "Block photo capture because SD card is unavailable. "
                    + "No command retained.", error);
            complete(command, completion, Result.blocked(
                    "photo_prepare:storage_unavailable"));
            return;
        }
        clearRecordingPreparationNotice();
        captureEvents.photoFailed(error.operation(), error.getMessage());
        logger.warn(LogCategory.CAMERA, "capture_start_rejected", null, "Block photo capture before shutter. Operation: " + error.operation()
                + ". Message: " + error.getMessage()
                + ". Retryable: " + error.retryable()
                + ". Unavailable: " + error.unavailable() + ".", error);
        complete(command, completion, Result.blocked("photo_prepare:" + error.getMessage()));
    }

    private boolean retryPhotoFallback(Command command, Completion completion,
            CameraRuntimeSelection original, CandidateKey requested,
            Optional<CandidateKey> fallback,
            Set<CandidateKey> attemptedPhotoCandidates) {
        if (requested == null || fallback.isEmpty()) return false;
        CandidateKey candidate = fallback.orElseThrow();
        if (attemptedPhotoCandidates.contains(candidate)) return false;
        logger.info(LogCategory.CAMERA, "unspecified", "Retry photo capture at a lower resolution after the requested image "
                + "profile failed. Requested: "
                + requested.imageMode().orElseThrow().resolution().actual()
                + ". Fallback: "
                + candidate.imageMode().orElseThrow().resolution().actual() + ".");
        executeCapturePhotoCandidate(command, completion, original, candidate,
                attemptedPhotoCandidates);
        return true;
    }

    private Optional<CandidateKey> recordStandaloneImageOutcome(CandidateKey requested,
            VerificationOutcome outcome) {
        if (capabilities == null) return Optional.empty();
        try {
            return capabilities.recordStandaloneImageOutcome(requested, outcome);
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, "standalone_photo_evidence_failed outcome=" + outcome, error);
            return Optional.empty();
        }
    }

    private SharedCameraCapturePipeline requirePipeline(
            Command command, Completion completion, String operation) {
        SharedCameraCapturePipeline current = pipeline;
        if (current != null && activeContext != null) return current;
        captureEvents.captureFailed(operation, "Camera is not ready");
        complete(command, completion, Result.blocked("camera_not_ready"));
        return null;
    }

    private synchronized Result passWithActiveBinding(String detail) {
        return Result.pass(
                Objects.requireNonNull(activeSelection, ACTIVE_SELECTION),
                Objects.requireNonNull(activeContext, ACTIVE_CONTEXT), detail);
    }

    private synchronized Result blockedWithActiveBinding(String detail) {
        return Result.blocked(
                Objects.requireNonNull(activeSelection, ACTIVE_SELECTION),
                Objects.requireNonNull(activeContext, ACTIVE_CONTEXT), detail);
    }

    private synchronized Result cancelledWithActiveBinding(String detail) {
        return Result.cancelled(
                Objects.requireNonNull(activeSelection, ACTIVE_SELECTION),
                Objects.requireNonNull(activeContext, ACTIVE_CONTEXT), detail);
    }

    private synchronized boolean commitStartedRecording(
            SharedCameraMediaLifecycle.RecordingCapture recording,
            CameraOperationResult start) {
        boolean cancelled = recordingStartCancelled;
        recordingStartCancelled = false;
        if (!cancelled && start.outcome() == CameraOperationOutcome.PASS) {
            activeRecording = recording;
        }
        return cancelled;
    }

    private synchronized void clearRequestedRecordingMode() {
        requestedRecordingMode = null;
    }
    private void showRecordingPreparationNotice(String message) {
        recordingPreparationListener.onPreparing(message);
    }

    private void clearRecordingPreparationNotice() {
        recordingPreparationListener.onCleared();
    }

    private void showRecordingPreparationUnavailable(String message) {
        recordingPreparationListener.onUnavailable(message);
    }

    private synchronized boolean recordingStartCancelled() {
        boolean cancelled = recordingStartCancelled;
        if (cancelled) recordingStartCancelled = false;
        return cancelled;
    }

    private CameraOperationContext runtimeContext() {
        CameraOperationContext context = Objects.requireNonNull(activeContext, ACTIVE_CONTEXT);
        long startedAt = System.currentTimeMillis();
        return new CameraOperationContext(context.cameraId(), context.verificationPipelineId(),
                context.codec(), context.tuple(), context.sessionGeneration(),
                context.cameraHealthGeneration(), CameraOperationDeadline.forCandidate(startedAt));
    }
    private CameraOperationContext operationContext(
            Command command, CameraRuntimeSelection selection) {
        long startedAt = System.currentTimeMillis();
        return new CameraOperationContext(selection.cameraId(),
                selection.verificationPipelineId(), selection.codec(), selection.tuple(),
                command.operationSequence(), command.healthGeneration(),
                CameraOperationDeadline.forCandidate(startedAt));
    }

    private void complete(Command command, Completion completion, Result result) {
        String camera = command.target().map(value -> value.cameraId().value())
                .or(() -> command.activeBinding().map(value -> value.cameraId().value()))
                .orElse("none");
        if (command.operation() != Operation.CAPTURE_PHOTO
                || result.outcome() != Outcome.PASS) {
            logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime operation=" + command.operation()
                    + " camera=" + camera
                    + " outcome=" + result.outcome().name().toLowerCase(
                            java.util.Locale.ROOT)
                    + " detail=" + result.detail());
        }
        completion.complete(result);
    }

    private String logPrefix(Command command) {
        String camera = command.target().map(value -> value.cameraId().value())
                .or(() -> command.activeBinding().map(value -> value.cameraId().value()))
                .orElse("none");
        return "shared_camera_gateway_backend operation=" + command.operation()
                + " camera=" + camera
                + " transitionGeneration=" + command.transitionGeneration()
                + " healthGeneration=" + command.healthGeneration()
                + " sequence=" + command.operationSequence();
    }


    private static long elapsedMillis(long startedAtNanos, long completedAtNanos) {
        return Math.max(0L, completedAtNanos - startedAtNanos) / 1_000_000L;
    }

    private static String message(Throwable error) {
        String value = error == null ? null : error.getMessage();
        String fallback = error == null ? "unknown" : error.getClass().getSimpleName();
        return value == null || value.isBlank() ? fallback : value;
    }
}
