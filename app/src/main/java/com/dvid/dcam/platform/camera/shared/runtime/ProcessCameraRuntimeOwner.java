package com.dvid.dcam.platform.camera.shared.runtime;

import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
import com.dvid.dcam.platform.storage.DcamFileType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProcessCameraRuntimeOwner implements AutoCloseable {
    public enum Submission {
        ACCEPTED,
        HELD,
        COALESCED,
        CONSUMED,
        NO_OP,
        REJECTED_TRANSITION,
        REJECTED_RECORDING,
        REJECTED_NOT_READY,
        REJECTED_BACKEND,
        REJECTED_PROCESS_CANCELLED
    }

    public record RuntimeSnapshot(
            CameraRuntimeState state,
            Optional<CameraRuntimeSelection> activeSelection,
            Optional<CameraRuntimeSelection> committedSelection,
            long transitionGeneration,
            long healthGeneration,
            Optional<ProcessCameraRuntimeBackend.Operation> inFlight,
            boolean settingsEnabled,
            boolean recordingEnabled,
            boolean photoEnabled,
            boolean pendingRecordStart,
            boolean pendingPhoto,
            boolean processCancelled) {
        public RuntimeSnapshot {
            state = Objects.requireNonNull(state, "state");
            activeSelection = Objects.requireNonNull(activeSelection, "activeSelection");
            committedSelection = Objects.requireNonNull(
                    committedSelection, "committedSelection");
            inFlight = Objects.requireNonNull(inFlight, "inFlight");
            if (transitionGeneration < 0 || healthGeneration < 0) {
                throw new IllegalArgumentException("generations must not be negative");
            }
        }
    }

    @FunctionalInterface
    public interface Listener {
        void onStateChanged(RuntimeSnapshot snapshot);
    }

    public interface Attachment extends AutoCloseable {
        @Override
        void close();
    }

    @FunctionalInterface
    public interface MediaReservationGate {
        long delayMillis(DcamFileType type);
    }

    private final Logger logger;
    private final Executor executor;
    private static final long WATCHDOG_DELAY_MILLIS = 3000L;
    private static final String TARGET_ARGUMENT = "target";
    private static final String HELD_ACTION_COALESCE = "coalesce";
    private static final String HELD_ACTION_ENQUEUE = "enqueue";
    private static final String HELD_ACTION_EXECUTE = "execute";
    private static final String HELD_COMMAND_RECORD_START = "command=record_start";
    private static final String HELD_COMMAND_PHOTO = "command=photo";

    private final ExecutorService ownedExecutor;
    private final CameraRuntimeRecoveryScheduler recoveryScheduler;
    private final CameraAvailabilityMonitor availabilityMonitor;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private ProcessCameraRuntimeBackend backend;
    private CameraRuntimeState state = CameraRuntimeState.CLOSED;
    private CameraRuntimeSelection activeSelection;
    private CameraRuntimeSelection committedSelection;
    private CameraOperationContext activeBinding;
    private ProcessCameraRuntimeBackend.Command inFlight;
    private PendingTransition pendingTransition;

    private CameraRuntimeSelection transitionTarget;
    private CameraRuntimeSelection transitionPrevious;
    private CameraRuntimeSelection recoverySelection;
    private CameraId availabilityCamera;
    private CameraAvailabilityMonitor.Attachment availabilityAttachment;
    private CameraRuntimeRecoveryScheduler.Task watchdogTask;
    private CameraRuntimeRecoveryScheduler.Task mediaReservationDrainTask;
    private ProcessCameraRuntimeBackend.HealthSnapshot lastHealthSnapshot;
    private String pendingRecoveryDetail;
    private long availabilityGeneration;
    private long watchdogGeneration;
    private long mediaReservationDrainGeneration;

    private MediaReservationGate mediaReservationGate = type -> 0L;
    private boolean releaseRequested;
    private boolean globalFailureRequested;
    private boolean processCancelled;
    private boolean capabilityScanReserved;
    private boolean cameraExpectedActive;
    private boolean cameraUseAllowed = true;
    private boolean previewExpected;
    private boolean recoveryAttemptInFlight;
    private HeldCommand pendingRecordStart;
    private HeldCommand pendingPhoto;
    private boolean pendingRecordingStop;
    private long transitionGeneration;
    private long healthGeneration;
    private long operationSequence;

    public ProcessCameraRuntimeOwner(Logger logger) {
        this(logger, CameraAvailabilityMonitor.none(),
                new ScheduledCameraRuntimeRecoveryScheduler());
    }

    public ProcessCameraRuntimeOwner(Logger logger,
            CameraAvailabilityMonitor availabilityMonitor) {
        this(logger, availabilityMonitor, new ScheduledCameraRuntimeRecoveryScheduler());
    }

    private ProcessCameraRuntimeOwner(Logger logger,
            CameraAvailabilityMonitor availabilityMonitor,
            CameraRuntimeRecoveryScheduler recoveryScheduler) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.availabilityMonitor = Objects.requireNonNull(
                availabilityMonitor, "availabilityMonitor");
        this.recoveryScheduler = Objects.requireNonNull(
                recoveryScheduler, "recoveryScheduler");
        ownedExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dcam-process-camera-owner");
            thread.setDaemon(true);
            return thread;
        });
        executor = ownedExecutor;
    }

    ProcessCameraRuntimeOwner(Logger logger, Executor executor) {
        this(logger, executor, CameraAvailabilityMonitor.none(),
                CameraRuntimeRecoveryScheduler.none());
    }

    ProcessCameraRuntimeOwner(Logger logger, Executor executor,
            CameraAvailabilityMonitor availabilityMonitor,
            CameraRuntimeRecoveryScheduler recoveryScheduler) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.availabilityMonitor = Objects.requireNonNull(
                availabilityMonitor, "availabilityMonitor");
        this.recoveryScheduler = Objects.requireNonNull(
                recoveryScheduler, "recoveryScheduler");
        ownedExecutor = null;
    }

    public synchronized void installBackend(ProcessCameraRuntimeBackend value) {
        Objects.requireNonNull(value, "backend");
        if (backend != null && backend != value) {
            throw new IllegalStateException("camera runtime backend already installed");
        }
        backend = value;
        backend.setPreviewExpected(previewExpected);
    }

    public synchronized boolean hasBackend() {
        return backend != null;
    }

    public synchronized void installMediaReservation(MediaReservationGate value) {
        mediaReservationGate = Objects.requireNonNull(value, "mediaReservationGate");
    }

    public synchronized Attachment attach(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.onStateChanged(snapshot());
        return () -> listeners.remove(listener);
    }

    public synchronized Optional<CameraRuntimeSelection> recordingSelection() {
        if (pendingTransition != null) return Optional.of(pendingTransition.target());
        if (transitionTarget != null) return Optional.of(transitionTarget);
        if (activeSelection != null) return Optional.of(activeSelection);
        if (committedSelection != null) return Optional.of(committedSelection);
        return Optional.ofNullable(recoverySelection);
    }

    public synchronized RuntimeSnapshot snapshot() {
        boolean transition = inFlight != null && isTransition(inFlight.operation());
        boolean recording = state == CameraRuntimeState.RECORDING;
        boolean ready = state == CameraRuntimeState.READY || recording;
        return new RuntimeSnapshot(state, Optional.ofNullable(activeSelection),
                Optional.ofNullable(committedSelection), transitionGeneration,
                healthGeneration, inFlight == null
                        ? Optional.empty() : Optional.of(inFlight.operation()),
                ready && !transition && !recording && !releaseRequested,
                ready && !transition && !releaseRequested,
                ready && !transition && !releaseRequested,
                pendingRecordStart != null, pendingPhoto != null, processCancelled);
    }

    public synchronized boolean beginCapabilityScan() {
        if (processCancelled || capabilityScanReserved || inFlight != null
                || state != CameraRuntimeState.CLOSED) {
            return false;
        }
        capabilityScanReserved = true;
        logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime capability_scan action=reserve");
        return true;
    }

    public synchronized void endCapabilityScan() {
        if (!capabilityScanReserved) return;
        capabilityScanReserved = false;
        logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime capability_scan action=release");
    }

    public synchronized Submission initialize(CameraRuntimeSelection target) {
        Objects.requireNonNull(target, TARGET_ARGUMENT);
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (capabilityScanReserved) return Submission.REJECTED_TRANSITION;
        if (state != CameraRuntimeState.CLOSED && state != CameraRuntimeState.RECOVERING) {
            return Submission.REJECTED_TRANSITION;
        }
        cameraExpectedActive = true;
        if (committedSelection == null) recoverySelection = target;
        return beginTransition(ProcessCameraRuntimeBackend.Operation.INITIALIZE, target);
    }

    public synchronized Submission switchCamera(CameraRuntimeSelection target) {
        Objects.requireNonNull(target, TARGET_ARGUMENT);
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (state == CameraRuntimeState.RECORDING) return Submission.REJECTED_RECORDING;
        if (inFlight != null) {
            return supersedeTransition(ProcessCameraRuntimeBackend.Operation.SWITCH_CAMERA, target);
        }
        if (state != CameraRuntimeState.READY) return Submission.REJECTED_NOT_READY;
        return beginTransition(ProcessCameraRuntimeBackend.Operation.SWITCH_CAMERA, target);
    }

    public synchronized Submission verifySetting(CameraRuntimeSelection target) {
        Objects.requireNonNull(target, TARGET_ARGUMENT);
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (state == CameraRuntimeState.RECORDING) return Submission.REJECTED_RECORDING;
        if (inFlight != null) {
            return supersedeTransition(ProcessCameraRuntimeBackend.Operation.VERIFY_SETTING, target);
        }
        if (state != CameraRuntimeState.READY) return Submission.REJECTED_NOT_READY;
        return beginTransition(ProcessCameraRuntimeBackend.Operation.VERIFY_SETTING, target);
    }

    private Submission supersedeTransition(ProcessCameraRuntimeBackend.Operation operation,
            CameraRuntimeSelection target) {
        ProcessCameraRuntimeBackend.Operation current = inFlight.operation();
        boolean cancellable = isTargetTransition(current)
                && current != ProcessCameraRuntimeBackend.Operation.INITIALIZE;
        boolean afterBind = current == ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED
                || current == ProcessCameraRuntimeBackend.Operation.RESTORE_EXACT;
        if (!cancellable && !afterBind) return Submission.REJECTED_TRANSITION;
        boolean coalesced = pendingTransition != null;
        pendingTransition = new PendingTransition(operation, target);
        if (cancellable) backend.cancel(inFlight);
        logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime transition action="
                + (cancellable ? "supersede" : "replace_after_bind")
                + " operation=" + operation + " sequence=" + inFlight.operationSequence());
        return coalesced ? Submission.COALESCED : Submission.ACCEPTED;
    }

    public synchronized Submission bindCommitted() {
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (capabilityScanReserved) return Submission.REJECTED_TRANSITION;
        if (state == CameraRuntimeState.READY || state == CameraRuntimeState.RECORDING) {
            return Submission.NO_OP;
        }
        if (committedSelection == null) return Submission.REJECTED_NOT_READY;
        if (state != CameraRuntimeState.CLOSED && state != CameraRuntimeState.RECOVERING) {
            return Submission.REJECTED_TRANSITION;
        }
        cameraExpectedActive = true;
        recoverySelection = committedSelection;
        return beginTransition(ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED,
                committedSelection);
    }

    public synchronized Submission bindCommitted(CameraRuntimeSelection selection) {
        Objects.requireNonNull(selection, "selection");
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (capabilityScanReserved) return Submission.REJECTED_TRANSITION;
        if (state == CameraRuntimeState.READY || state == CameraRuntimeState.RECORDING) {
            return Submission.NO_OP;
        }
        if (state != CameraRuntimeState.CLOSED && state != CameraRuntimeState.RECOVERING) {
            return Submission.REJECTED_TRANSITION;
        }
        cameraExpectedActive = true;
        recoverySelection = selection;
        return beginTransition(ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, selection);
    }

    public synchronized Submission restoreExact(CameraRuntimeSelection target) {
        Objects.requireNonNull(target, TARGET_ARGUMENT);
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (state == CameraRuntimeState.RECORDING) return Submission.REJECTED_RECORDING;
        if (state != CameraRuntimeState.READY) return Submission.REJECTED_NOT_READY;
        return beginTransition(ProcessCameraRuntimeBackend.Operation.RESTORE_EXACT, target);
    }

    public synchronized Submission recover() {
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (capabilityScanReserved) return Submission.REJECTED_TRANSITION;
        if (state != CameraRuntimeState.RECOVERING) return Submission.NO_OP;
        return attemptRecoveryLocked("manual");
    }

    public synchronized void setCameraUseAllowed(boolean allowed) {
        if (cameraUseAllowed == allowed) return;
        cameraUseAllowed = allowed;
        resetHealthBaseline();
        logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime lifecycle cameraUseAllowed=" + allowed);
        if (allowed) {
            scheduleWatchdogLocked();
            if (state == CameraRuntimeState.RECOVERING) {
                attemptRecoveryLocked("lifecycle_resume");
            }
        } else {
            cancelWatchdogLocked();
        }
    }

    public synchronized void setPreviewExpected(boolean expected) {
        if (previewExpected == expected) return;
        previewExpected = expected;
        if (backend != null) backend.setPreviewExpected(expected);
        resetHealthBaseline();
        logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime preview_expected=" + expected);
        scheduleWatchdogLocked();
    }

    public synchronized Submission releaseCamera() {
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        cameraExpectedActive = false;
        cancelWatchdogLocked();
        cancelMediaReservationDrainLocked();
        detachAvailabilityLocked();
        pendingRecordStart = null;
        pendingPhoto = null;
        pendingRecordingStop = false;
        pendingTransition = null;
        releaseRequested = true;
        logHeld("cancel", "reason=release");
        if (inFlight != null) {
            if (backend != null) backend.cancel(inFlight);
            return Submission.ACCEPTED;
        }
        if (state == CameraRuntimeState.RECORDING) {
            return beginImmediate(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING);
        }
        if (activeBinding == null) {
            state = CameraRuntimeState.CLOSED;
            releaseRequested = false;
            notifyListeners();
            return Submission.NO_OP;
        }
        return beginImmediate(ProcessCameraRuntimeBackend.Operation.RELEASE);
    }

    public synchronized Submission startRecording() {
        return startRecording(DcamFileType.VIDEO);
    }

    public synchronized Submission startRecording(DcamFileType type) {
        if (type != DcamFileType.VIDEO && type != DcamFileType.IMP) {
            throw new IllegalArgumentException("Recording media type required");
        }
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (state == CameraRuntimeState.RECORDING) return Submission.NO_OP;
        if (pendingRecordStart != null) {
            return coalescePendingRecordStart(type);
        }
        if (inFlight != null
                && inFlight.operation() == ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO) {
            return holdRecordStart(committedSelection, type, " reason=active_capture");
        }
        if (canHoldCommands()) {
            return holdRecordStart(transitionTarget, type, "");
        }
        if (state != CameraRuntimeState.READY || inFlight != null) {
            return inFlight != null ? Submission.REJECTED_TRANSITION
                    : Submission.REJECTED_NOT_READY;
        }
        return startRecordingAfterMediaReservation(type);
    }

    private Submission coalescePendingRecordStart(DcamFileType type) {
        if (pendingRecordStart.mediaType != type) {
            pendingRecordStart = new HeldCommand(pendingRecordStart.cameraId,
                    pendingRecordStart.transitionGeneration, pendingRecordStart.sequence,
                    true, type);
            if (inFlight == null) {
                cancelMediaReservationDrainLocked();
                drainHeld();
            }
        }
        logHeld(HELD_ACTION_COALESCE, HELD_COMMAND_RECORD_START);
        return Submission.COALESCED;
    }

    private Submission holdRecordStart(CameraRuntimeSelection selection, DcamFileType type,
            String reason) {
        pendingRecordStart = heldCommand(selection, true, type);
        logHeld(HELD_ACTION_ENQUEUE, HELD_COMMAND_RECORD_START + reason);
        notifyListeners();
        return Submission.HELD;
    }

    private Submission startRecordingAfterMediaReservation(DcamFileType type) {
        long delayMillis = mediaReservationDelayMillis(type);
        if (delayMillis > 0L) {
            pendingRecordStart = heldCommand(committedSelection, true, type);
            notifyListeners();
            scheduleMediaReservationDrainLocked(delayMillis);
            return Submission.HELD;
        }
        return beginImmediate(ProcessCameraRuntimeBackend.Operation.START_RECORDING);
    }

    public synchronized Submission stopRecording() {
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (pendingRecordStart != null && state != CameraRuntimeState.RECORDING) {
            pendingRecordStart = null;
            cancelMediaReservationDrainLocked();
            logHeld("consume", HELD_COMMAND_RECORD_START + " reason=stop_before_ready");
            notifyListeners();
            if (inFlight == null) drainHeld();
            return Submission.CONSUMED;
        }
        if (state != CameraRuntimeState.RECORDING) return Submission.NO_OP;
        if (inFlight != null) {
            if (inFlight.operation() == ProcessCameraRuntimeBackend.Operation.STOP_RECORDING) {
                return Submission.COALESCED;
            }
            if (inFlight.operation() != ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO) {
                return Submission.REJECTED_TRANSITION;
            }
            if (pendingRecordingStop) {
                logHeld(HELD_ACTION_COALESCE, "command=record_stop reason=active_capture");
                return Submission.COALESCED;
            }
            pendingPhoto = null;
            pendingRecordingStop = true;
            logHeld(HELD_ACTION_ENQUEUE, "command=record_stop reason=active_capture");
            notifyListeners();
            return Submission.HELD;
        }
        pendingPhoto = null;
        cancelMediaReservationDrainLocked();
        return beginImmediate(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING);
    }

    public synchronized Submission capturePhoto() {
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        if (inFlight != null
                && inFlight.operation() == ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO) {
            return holdPhoto(committedSelection, " reason=active_capture");
        }
        if (inFlight != null
                && inFlight.operation() == ProcessCameraRuntimeBackend.Operation.START_RECORDING) {
            return holdPhoto(committedSelection, " reason=recording_start");
        }
        if (inFlight != null
                && inFlight.operation() == ProcessCameraRuntimeBackend.Operation.STOP_RECORDING) {
            return holdPhoto(committedSelection, " reason=recording_stop");
        }
        if (canHoldCommands()) {
            return holdPhoto(transitionTarget, "");
        }
        if (pendingPhoto != null) {
            logHeld(HELD_ACTION_COALESCE, HELD_COMMAND_PHOTO + " reason=media_reservation");
            return Submission.COALESCED;
        }
        if ((state != CameraRuntimeState.READY && state != CameraRuntimeState.RECORDING)
                || inFlight != null) {
            return inFlight != null ? Submission.REJECTED_TRANSITION
                    : Submission.REJECTED_NOT_READY;
        }
        long delayMillis = mediaReservationDelayMillis(DcamFileType.IMAGE);
        if (delayMillis > 0L) {
            pendingPhoto = heldCommand(committedSelection, false, DcamFileType.IMAGE);
            notifyListeners();
            scheduleMediaReservationDrainLocked(delayMillis);
            return Submission.HELD;
        }
        return beginImmediate(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO);
    }

    private Submission holdPhoto(CameraRuntimeSelection selection, String reason) {
        if (pendingPhoto != null) {
            logHeld(HELD_ACTION_COALESCE, HELD_COMMAND_PHOTO + reason);
            return Submission.COALESCED;
        }
        pendingPhoto = heldCommand(selection, false, DcamFileType.IMAGE);
        logHeld(HELD_ACTION_ENQUEUE, HELD_COMMAND_PHOTO + reason);
        notifyListeners();
        return Submission.HELD;
    }

    public synchronized Submission reportGlobalFailure(String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail is required");
        }
        if (processCancelled) return Submission.REJECTED_PROCESS_CANCELLED;
        requestRecoveryLocked(detail);
        return Submission.ACCEPTED;
    }

    public synchronized void cancelForProcessDeath() {
        if (processCancelled) return;
        processCancelled = true;
        cameraExpectedActive = false;
        cancelWatchdogLocked();
        detachAvailabilityLocked();
        transitionGeneration++;
        healthGeneration++;
        cancelHeld("process_death");
        pendingTransition = null;
        releaseRequested = true;
        globalFailureRequested = false;
        if (inFlight == null) {
            if (activeBinding != null && backend != null) {
                beginImmediate(ProcessCameraRuntimeBackend.Operation.RELEASE);
            } else {
                activeBinding = null;
                activeSelection = null;
                state = CameraRuntimeState.CLOSED;
                releaseRequested = false;
                notifyListeners();
            }
        } else {
            if (backend != null) backend.cancel(inFlight);
            notifyListeners();
        }
    }

    @Override
    public synchronized void close() {
        cancelForProcessDeath();
        detachAvailabilityLocked();
        recoveryScheduler.close();
        availabilityMonitor.close();
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
    }

    private Submission beginTransition(ProcessCameraRuntimeBackend.Operation operation,
            CameraRuntimeSelection target) {
        if (backend == null) return Submission.REJECTED_BACKEND;
        if (inFlight != null) return Submission.REJECTED_TRANSITION;
        if (state == CameraRuntimeState.RECORDING) return Submission.REJECTED_RECORDING;
        transitionGeneration++;
        transitionTarget = target;
        transitionPrevious = committedSelection;
        recoverySelection = recoverySelectionFor(operation, target);
        watchAvailabilityLocked(recoverySelection);
        state = transitionStateFor(operation);
        scheduleWatchdogLocked();
        return begin(operation, Optional.of(target), Optional.ofNullable(committedSelection));
    }

    private CameraRuntimeSelection recoverySelectionFor(
            ProcessCameraRuntimeBackend.Operation operation, CameraRuntimeSelection target) {
        if (operation == ProcessCameraRuntimeBackend.Operation.RESTORE_EXACT
                || committedSelection == null) {
            return target;
        }
        return committedSelection;
    }

    private static CameraRuntimeState transitionStateFor(
            ProcessCameraRuntimeBackend.Operation operation) {
        if (operation == ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED
                || operation == ProcessCameraRuntimeBackend.Operation.RESTORE_EXACT) {
            return CameraRuntimeState.BINDING;
        }
        if (operation == ProcessCameraRuntimeBackend.Operation.RECOVER) {
            return CameraRuntimeState.RECOVERING;
        }
        return CameraRuntimeState.VERIFYING;
    }

    private boolean beginPendingTransition() {
        PendingTransition pending = pendingTransition;
        pendingTransition = null;
        if (pending == null || releaseRequested || processCancelled) return false;
        return beginTransition(pending.operation(), pending.target()) == Submission.ACCEPTED;
    }

    private Submission beginImmediate(ProcessCameraRuntimeBackend.Operation operation) {
        if (backend == null) return Submission.REJECTED_BACKEND;
        if (inFlight != null) return Submission.REJECTED_TRANSITION;
        if (operation == ProcessCameraRuntimeBackend.Operation.RELEASE) {
            state = CameraRuntimeState.RELEASING;
        }
        return begin(operation, Optional.ofNullable(committedSelection),
                Optional.ofNullable(committedSelection));
    }

    private Submission begin(ProcessCameraRuntimeBackend.Operation operation,
            Optional<CameraRuntimeSelection> target,
            Optional<CameraRuntimeSelection> previous) {
        ProcessCameraRuntimeBackend.Command command = new ProcessCameraRuntimeBackend.Command(
                operation, ++operationSequence, transitionGeneration, healthGeneration,
                target, previous, Optional.ofNullable(activeBinding));
        inFlight = command;
        notifyListeners();
        executor.execute(() -> invoke(command));
        return Submission.ACCEPTED;
    }

    private void invoke(ProcessCameraRuntimeBackend.Command command) {
        synchronized (this) {
            if (inFlight != command) return;
            if (processCancelled && isTransition(command.operation())) {
                complete(command, ProcessCameraRuntimeBackend.Result.cancelled(
                        "process_cancelled_before_dispatch"));
                return;
            }
        }
        try {
            backend.execute(command, result -> executor.execute(
                    () -> complete(command, Objects.requireNonNull(result, "result"))));
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, "camera_runtime backend_exception operation=" + command.operation(), error);
            executor.execute(() -> complete(command,
                    ProcessCameraRuntimeBackend.Result.recoveryRequired("backend_exception")));
        }
    }

    private void complete(ProcessCameraRuntimeBackend.Command command,
            ProcessCameraRuntimeBackend.Result result) {
        synchronized (this) {
            if (inFlight != command) {
                logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime stale_callback operation=" + command.operation()
                        + " sequence=" + command.operationSequence());
                return;
            }

            inFlight = null;
            if (processCancelled) {
                if (command.operation() == ProcessCameraRuntimeBackend.Operation.RELEASE) {
                    activeBinding = null;
                    activeSelection = null;
                    state = CameraRuntimeState.CLOSED;
                    releaseRequested = false;
                    notifyListeners();
                } else {
                    finishProcessCancellation();
                }
                return;
            }
            if (globalFailureRequested) {
                globalFailureRequested = false;
                String failure = pendingRecoveryDetail == null
                        ? result.detail() : pendingRecoveryDetail
                                + ";completion=" + result.detail();
                pendingRecoveryDetail = null;
                cancelHeld("global_failure");
                enterRecovery(failure);
                return;
            }
            switch (command.operation()) {
                case INITIALIZE, SWITCH_CAMERA, VERIFY_SETTING -> finishTargetTransition(result);
                case BIND_COMMITTED, RESTORE_EXACT, RECOVER -> finishBind(command, result);
                case RELEASE -> finishRelease(result);
                case START_RECORDING -> finishStartRecording(command, result);
                case STOP_RECORDING -> finishStopRecording(result);
                case CAPTURE_PHOTO -> finishPhoto(result);
            }
        }
    }

    private void finishTargetTransition(ProcessCameraRuntimeBackend.Result result) {
        if (result.outcome() == ProcessCameraRuntimeBackend.Outcome.READY) {
            finishReadyTargetTransition(result);
            return;
        }
        if (result.outcome() == ProcessCameraRuntimeBackend.Outcome.ROLLED_BACK_READY) {
            finishRolledBackTargetTransition(result);
            return;
        }
        finishTerminalTargetTransition(result);
    }

    private void finishReadyTargetTransition(ProcessCameraRuntimeBackend.Result result) {
        if (!acceptTargetResult(result)) {
            enterRecovery("target_binding_mismatch");
            return;
        }
        acceptReady(result);
    }

    private void finishRolledBackTargetTransition(ProcessCameraRuntimeBackend.Result result) {
        if (transitionPrevious == null || !sameSelection(
                transitionPrevious, result.selection().orElseThrow())) {
            enterRecovery("rollback_binding_mismatch");
            return;
        }
        acceptRollbackReady(result);
    }

    private void finishTerminalTargetTransition(ProcessCameraRuntimeBackend.Result result) {
        CameraRuntimeSelection previous = transitionPrevious;
        clearTransition();
        activeBinding = null;
        activeSelection = null;
        cancelHeld("target_terminal");
        if (result.outcome() == ProcessCameraRuntimeBackend.Outcome.CANCELLED
                && beginPendingTransition()) return;
        if (isTargetTerminalFailure(result)) {
            restorePreviousTargetSelection(result, previous);
            return;
        }
        enterRecovery(result.detail());
    }

    private static boolean isTargetTerminalFailure(ProcessCameraRuntimeBackend.Result result) {
        return result.outcome() == ProcessCameraRuntimeBackend.Outcome.TARGET_FAILED
                || result.outcome() == ProcessCameraRuntimeBackend.Outcome.CANCELLED;
    }

    private void restorePreviousTargetSelection(ProcessCameraRuntimeBackend.Result result,
            CameraRuntimeSelection previous) {
        if (previous == null) {
            if (recoveryAttemptInFlight) {
                enterRecovery(result.detail());
            } else {
                releaseRequested = false;
                state = CameraRuntimeState.CLOSED;
                notifyListeners();
            }
            return;
        }
        transitionTarget = previous;
        transitionPrevious = previous;
        state = CameraRuntimeState.BINDING;
        begin(ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED,
                Optional.of(previous), Optional.of(previous));
    }

    private void finishBind(ProcessCameraRuntimeBackend.Command command,
            ProcessCameraRuntimeBackend.Result result) {
        if (result.outcome() == ProcessCameraRuntimeBackend.Outcome.READY
                || result.outcome() == ProcessCameraRuntimeBackend.Outcome.ROLLED_BACK_READY) {
            if (result.selection().isEmpty()
                    || transitionTarget == null
                    || !sameSelection(transitionTarget, result.selection().orElseThrow())) {
                enterRecovery("bind_selection_mismatch");
                return;
            }
            acceptReady(result);
            return;
        }
        CameraRuntimeSelection recoveryTarget = command.operation()
                == ProcessCameraRuntimeBackend.Operation.RESTORE_EXACT
                ? transitionTarget : null;
        activeBinding = null;
        activeSelection = null;
        clearTransition();
        cancelHeld("bind_failure");
        enterRecovery(result.detail(), recoveryTarget);
    }

    private void finishRelease(ProcessCameraRuntimeBackend.Result result) {
        activeBinding = null;
        activeSelection = null;
        releaseRequested = false;
        clearTransition();
        if (result.outcome() == ProcessCameraRuntimeBackend.Outcome.PASS
                || result.outcome() == ProcessCameraRuntimeBackend.Outcome.CANCELLED) {
            state = CameraRuntimeState.CLOSED;
            notifyListeners();
        } else {
            enterRecovery(result.detail());
        }
    }

    private void finishStartRecording(ProcessCameraRuntimeBackend.Command command,
            ProcessCameraRuntimeBackend.Result result) {
        boolean recordingStarted = result.outcome() == ProcessCameraRuntimeBackend.Outcome.PASS;
        if (!acceptRetainedBinding(result, recordingStarted
                ? command.target().orElse(null) : null)) {
            enterRecovery("recording_binding_mismatch");
            return;
        }
        if (result.outcome() == ProcessCameraRuntimeBackend.Outcome.PASS) {
            state = CameraRuntimeState.RECORDING;
            notifyListeners();
            if (releaseRequested) beginImmediate(
                    ProcessCameraRuntimeBackend.Operation.STOP_RECORDING);
            else drainHeld();
        } else if (result.outcome() == ProcessCameraRuntimeBackend.Outcome.RECOVERY_REQUIRED) {
            cancelHeld("recording_global_failure");
            requestRecoveryLocked(result.detail());
        } else {
            state = CameraRuntimeState.READY;
            notifyListeners();
            if (releaseRequested) {
                beginImmediate(ProcessCameraRuntimeBackend.Operation.RELEASE);
            } else {
                drainHeld();
            }
        }
    }

    private void finishStopRecording(ProcessCameraRuntimeBackend.Result result) {
        if (result.outcome() == ProcessCameraRuntimeBackend.Outcome.RECOVERY_REQUIRED) {
            cancelHeld("stop_global_failure");
            enterRecovery(result.detail());
            return;
        }
        state = CameraRuntimeState.READY;
        notifyListeners();
        if (releaseRequested) {
            beginImmediate(ProcessCameraRuntimeBackend.Operation.RELEASE);
        } else {
            drainHeld();
        }
    }

    private void finishPhoto(ProcessCameraRuntimeBackend.Result result) {
        if (result.outcome() == ProcessCameraRuntimeBackend.Outcome.RECOVERY_REQUIRED) {
            cancelHeld("photo_global_failure");
            requestRecoveryLocked(result.detail());
            return;
        }
        if (!acceptRetainedBinding(result, null)) {
            enterRecovery("photo_binding_mismatch");
            return;
        }
        notifyListeners();
        if (releaseRequested) {
            beginImmediate(state == CameraRuntimeState.RECORDING
                    ? ProcessCameraRuntimeBackend.Operation.STOP_RECORDING
                    : ProcessCameraRuntimeBackend.Operation.RELEASE);
        } else if (pendingRecordingStop) {
            pendingRecordingStop = false;
            logHeld(HELD_ACTION_EXECUTE, "command=record_stop");
            beginImmediate(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING);
        } else {
            drainHeld();
        }
    }

    private void acceptRollbackReady(ProcessCameraRuntimeBackend.Result result) {
        boolean releaseAfterReady = releaseRequested;
        cancelHeld("rollback");
        committedSelection = result.selection().orElseThrow();
        recoverySelection = committedSelection;
        recoveryAttemptInFlight = false;
        activeSelection = committedSelection;
        activeBinding = result.activeBinding().orElseThrow();
        state = CameraRuntimeState.READY;
        cameraExpectedActive = true;
        watchAvailabilityLocked(committedSelection);
        resetHealthBaseline();
        scheduleWatchdogLocked();
        releaseRequested = false;
        clearTransition();
        if (releaseAfterReady || !beginPendingTransition()) {
            notifyListeners();
            if (releaseAfterReady) beginImmediate(
                    ProcessCameraRuntimeBackend.Operation.RELEASE);
        }
    }

    private void acceptReady(ProcessCameraRuntimeBackend.Result result) {
        boolean releaseAfterReady = releaseRequested;
        committedSelection = result.selection().orElseThrow();
        recoverySelection = committedSelection;
        recoveryAttemptInFlight = false;
        activeSelection = committedSelection;
        activeBinding = result.activeBinding().orElseThrow();
        state = CameraRuntimeState.READY;
        cameraExpectedActive = true;
        watchAvailabilityLocked(committedSelection);
        resetHealthBaseline();
        scheduleWatchdogLocked();
        releaseRequested = false;
        clearTransition();
        if (releaseAfterReady || !beginPendingTransition()) {
            notifyListeners();
            if (releaseAfterReady) beginImmediate(
                    ProcessCameraRuntimeBackend.Operation.RELEASE);
            else drainHeld();
        }
    }

    private boolean acceptTargetResult(ProcessCameraRuntimeBackend.Result result) {
        if (transitionTarget == null || result.selection().isEmpty()) return false;
        CameraRuntimeSelection value = result.selection().orElseThrow();
        return transitionTarget.cameraId().equals(value.cameraId())
                && transitionTarget.verificationPipelineId().equals(
                        value.verificationPipelineId())
                && transitionTarget.codec() == value.codec();
    }

    private void drainHeld() {
        if (releaseRequested || processCancelled || inFlight != null
                || (state != CameraRuntimeState.READY
                && state != CameraRuntimeState.RECORDING)
                || committedSelection == null) return;
        HeldCommand next = nextHeld();
        if (next == null || (next.recordStart
                && state != CameraRuntimeState.READY)) return;
        if (next.transitionGeneration != transitionGeneration
                || !next.cameraId.equals(committedSelection.cameraId())) {
            cancelHeld("stale_generation");
            return;
        }
        long delayMillis = mediaReservationDelayMillis(next.mediaType);
        if (delayMillis > 0L) {
            scheduleMediaReservationDrainLocked(delayMillis);
            return;
        }
        cancelMediaReservationDrainLocked();
        if (next.recordStart) {
            pendingRecordStart = null;
            logHeld(HELD_ACTION_EXECUTE, HELD_COMMAND_RECORD_START);
            beginImmediate(ProcessCameraRuntimeBackend.Operation.START_RECORDING);
        } else {
            pendingPhoto = null;
            logHeld(HELD_ACTION_EXECUTE, HELD_COMMAND_PHOTO);
            beginImmediate(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO);
        }
    }

    private HeldCommand nextHeld() {
        if (pendingRecordStart == null) return pendingPhoto;
        if (pendingPhoto == null) return pendingRecordStart;
        return pendingRecordStart.sequence < pendingPhoto.sequence
                ? pendingRecordStart : pendingPhoto;
    }

    private HeldCommand heldCommand(CameraRuntimeSelection selection, boolean recordStart,
            DcamFileType mediaType) {
        return new HeldCommand(selection.cameraId(), transitionGeneration,
                ++operationSequence, recordStart, mediaType);
    }

    private void clearTransition() {
        transitionTarget = null;
        transitionPrevious = null;
    }

    private void enterRecovery(String detail) {
        enterRecovery(detail, null);
    }

    private void enterRecovery(String detail, CameraRuntimeSelection preferredSelection) {
        CameraRuntimeSelection candidate = recoveryCandidate(preferredSelection);
        if (candidate != null) recoverySelection = candidate;
        if (state != CameraRuntimeState.RECOVERING) healthGeneration++;
        recoveryAttemptInFlight = false;
        pendingTransition = null;
        cancelHeld("recovery");
        state = CameraRuntimeState.RECOVERING;
        activeBinding = null;
        activeSelection = null;
        clearTransition();
        releaseRequested = false;
        resetHealthBaseline();
        watchAvailabilityLocked(recoverySelection);
        notifyListeners();
        logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime recovery camera=" + cameraId()
                + " transitionGeneration=" + transitionGeneration
                + " healthGeneration=" + healthGeneration + " detail=" + detail);
        scheduleWatchdogLocked();
    }

    private CameraRuntimeSelection recoveryCandidate(CameraRuntimeSelection preferredSelection) {
        if (preferredSelection != null) return preferredSelection;
        if (committedSelection != null) return committedSelection;
        if (transitionTarget != null) return transitionTarget;
        return activeSelection;
    }

    private void finishProcessCancellation() {
        cancelHeld("process_cancelled");
        if (inFlight != null) return;
        if (activeBinding != null && backend != null) {
            releaseRequested = false;
            begin(ProcessCameraRuntimeBackend.Operation.RELEASE,
                    Optional.empty(), Optional.empty());
            return;
        }
        activeBinding = null;
        activeSelection = null;
        state = CameraRuntimeState.CLOSED;
        releaseRequested = false;
        notifyListeners();
    }

    private void cancelHeld(String reason) {
        cancelMediaReservationDrainLocked();
        if (pendingRecordStart == null && pendingPhoto == null && !pendingRecordingStop) return;
        pendingRecordStart = null;
        pendingPhoto = null;
        pendingRecordingStop = false;
        logHeld("cancel", "reason=" + reason);
        notifyListeners();
    }

    private long mediaReservationDelayMillis(DcamFileType type) {
        return Math.max(0L, mediaReservationGate.delayMillis(type));
    }

    private void scheduleMediaReservationDrainLocked(long delayMillis) {
        if (mediaReservationDrainTask != null) mediaReservationDrainTask.cancel();
        long token = ++mediaReservationDrainGeneration;
        mediaReservationDrainTask = recoveryScheduler.schedule(
                () -> mediaReservationDrainTick(token), delayMillis);
    }

    private void mediaReservationDrainTick(long token) {
        executor.execute(() -> {
            synchronized (this) {
                if (token != mediaReservationDrainGeneration || processCancelled) return;
                mediaReservationDrainTask = null;
                drainHeld();
            }
        });
    }

    private void cancelMediaReservationDrainLocked() {
        mediaReservationDrainGeneration++;
        if (mediaReservationDrainTask != null) mediaReservationDrainTask.cancel();
        mediaReservationDrainTask = null;
    }

    private boolean canHoldCommands() {
        return !processCancelled && inFlight != null
                && isHoldableTransition(inFlight.operation());
    }

    private static boolean isHoldableTransition(
            ProcessCameraRuntimeBackend.Operation operation) {
        return isTargetTransition(operation)
                || operation == ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED
                || operation == ProcessCameraRuntimeBackend.Operation.RESTORE_EXACT;
    }

    private static boolean isTargetTransition(ProcessCameraRuntimeBackend.Operation operation) {
        return operation == ProcessCameraRuntimeBackend.Operation.INITIALIZE
                || operation == ProcessCameraRuntimeBackend.Operation.SWITCH_CAMERA
                || operation == ProcessCameraRuntimeBackend.Operation.VERIFY_SETTING;
    }

    private static boolean isTransition(ProcessCameraRuntimeBackend.Operation operation) {
        return isTargetTransition(operation)
                || operation == ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED
                || operation == ProcessCameraRuntimeBackend.Operation.RESTORE_EXACT
                || operation == ProcessCameraRuntimeBackend.Operation.RECOVER
                || operation == ProcessCameraRuntimeBackend.Operation.RELEASE;
    }

    private static boolean sameSelection(CameraRuntimeSelection first,
            CameraRuntimeSelection second) {
        return first.cameraId().equals(second.cameraId())
                && first.verificationPipelineId().equals(second.verificationPipelineId())
                && first.codec() == second.codec()
                && first.tuple().equals(second.tuple());
    }

    private static boolean sameCameraPipeline(CameraRuntimeSelection first,
            CameraRuntimeSelection second) {
        return first.cameraId().equals(second.cameraId())
                && first.verificationPipelineId().equals(second.verificationPipelineId())
                && first.codec() == second.codec();
    }

    private void requestRecoveryLocked(String detail) {
        pendingRecoveryDetail = detail;
        pendingTransition = null;
        globalFailureRequested = true;
        cancelHeld("global_failure");
        if (inFlight != null) return;
        if (state == CameraRuntimeState.RECORDING) {
            logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime recovery_recording_stop detail=" + detail);
            beginImmediate(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING);
            return;
        }
        globalFailureRequested = false;
        pendingRecoveryDetail = null;
        enterRecovery(detail);
    }

    private Submission attemptRecoveryLocked(String trigger) {
        if (capabilityScanReserved) return Submission.REJECTED_TRANSITION;
        if (processCancelled || !cameraUseAllowed || !cameraExpectedActive) {
            logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime recovery_attempt action=paused trigger=" + trigger);
            return Submission.NO_OP;
        }
        if (state != CameraRuntimeState.RECOVERING || inFlight != null) {
            return inFlight != null ? Submission.REJECTED_TRANSITION : Submission.NO_OP;
        }
        CameraRuntimeSelection target = committedSelection != null
                ? committedSelection : recoverySelection;
        if (target == null || backend == null) {
            scheduleWatchdogLocked();
            return target == null ? Submission.REJECTED_NOT_READY
                    : Submission.REJECTED_BACKEND;
        }
        recoverySelection = target;
        recoveryAttemptInFlight = true;
        transitionGeneration++;
        transitionTarget = target;
        transitionPrevious = committedSelection;
        state = CameraRuntimeState.RECOVERING;
        watchAvailabilityLocked(target);
        resetHealthBaseline();
        logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime recovery_attempt trigger=" + trigger
                + " operation=" + (committedSelection == null ? "INITIALIZE" : "RECOVER"));
        return begin(committedSelection == null
                        ? ProcessCameraRuntimeBackend.Operation.INITIALIZE
                        : ProcessCameraRuntimeBackend.Operation.RECOVER,
                Optional.of(target), Optional.ofNullable(committedSelection));
    }

    private void watchdogTick(long token) {
        executor.execute(() -> {
            synchronized (this) {
                watchdogTickLocked(token);
            }
        });
    }

    private void watchdogTickLocked(long token) {
        if (token != watchdogGeneration || processCancelled || !cameraUseAllowed
                || !cameraExpectedActive) return;
        if (state == CameraRuntimeState.RECOVERING) {
            handleRecoveryWatchdogTick();
            return;
        }
        if (inFlight != null || activeBinding == null || backend == null) {
            scheduleWatchdogLocked();
            return;
        }
        probeWatchdogHealth();
    }

    private void handleRecoveryWatchdogTick() {
        if (inFlight == null) {
            attemptRecoveryLocked("watchdog");
        } else {
            scheduleWatchdogLocked();
        }
    }

    private boolean acceptRetainedBinding(ProcessCameraRuntimeBackend.Result result,
            CameraRuntimeSelection exactExpected) {
        if (result.selection().isEmpty()) return true;
        CameraRuntimeSelection retained = result.selection().orElseThrow();
        if (committedSelection == null
                || (exactExpected != null && !sameSelection(exactExpected, retained))
                || (exactExpected == null
                && !sameCameraPipeline(committedSelection, retained))) {
            return false;
        }
        activeSelection = retained;
        activeBinding = result.activeBinding().orElseThrow();
        cameraExpectedActive = true;
        watchAvailabilityLocked(retained);
        resetHealthBaseline();
        scheduleWatchdogLocked();
        return true;
    }

    private void probeWatchdogHealth() {
        Optional<ProcessCameraRuntimeBackend.HealthSnapshot> probe;
        try {
            probe = backend.healthSnapshot(activeBinding);
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, "camera_runtime watchdog health_probe_failed", error);
            requestRecoveryLocked("watchdog_probe_exception");
            return;
        }
        if (probe.isEmpty()) {
            scheduleWatchdogLocked();
            return;
        }
        evaluateWatchdogHealth(probe.orElseThrow());
    }

    private void evaluateWatchdogHealth(ProcessCameraRuntimeBackend.HealthSnapshot current) {
        ProcessCameraRuntimeBackend.HealthSnapshot previous = lastHealthSnapshot;
        lastHealthSnapshot = current;
        if (current.recoveryRequired() || !current.sessionBound()) {
            requestRecoveryLocked("watchdog:" + current.detail());
            return;
        }
        boolean sourceStalled = previous != null
                && current.sourceFrameCount() == previous.sourceFrameCount();
        boolean previewStalled = previewExpected && previous != null
                && current.previewFrameCount() == previous.previewFrameCount();
        if (sourceStalled || previewStalled
                || (previewExpected && !current.previewSignalAvailable())) {
            requestRecoveryLocked("watchdog_stall:source=" + sourceStalled
                    + ";preview=" + previewStalled);
            return;
        }
        scheduleWatchdogLocked();
    }

    private void scheduleWatchdogLocked() {
        cancelWatchdogLocked();
        if (!cameraUseAllowed || !cameraExpectedActive || processCancelled
                || state == CameraRuntimeState.CLOSED
                || state == CameraRuntimeState.RELEASING) return;
        long token = ++watchdogGeneration;
        watchdogTask = recoveryScheduler.schedule(
                () -> watchdogTick(token), WATCHDOG_DELAY_MILLIS);
    }

    private void cancelWatchdogLocked() {
        watchdogGeneration++;
        if (watchdogTask != null) watchdogTask.cancel();
        watchdogTask = null;
    }

    private void resetHealthBaseline() {
        lastHealthSnapshot = null;
    }

    private void watchAvailabilityLocked(CameraRuntimeSelection selection) {
        if (selection == null || processCancelled || !cameraExpectedActive) return;
        if (availabilityCamera != null && availabilityCamera.equals(selection.cameraId())
                && availabilityAttachment != null) return;
        detachAvailabilityLocked();
        long token = ++availabilityGeneration;
        availabilityCamera = selection.cameraId();
        availabilityAttachment = availabilityMonitor.watch(availabilityCamera,
                new CameraAvailabilityMonitor.Listener() {
                    @Override public void onAvailable(CameraId cameraId) {
                        executor.execute(() -> ProcessCameraRuntimeOwner.this.onAvailable(token, cameraId));
                    }

                    @Override public void onUnavailable(CameraId cameraId) {
                        // The app owns this camera; loss of availability alone is not a failure.
                    }
                });
    }

    private void onAvailable(long token, CameraId cameraId) {
        synchronized (this) {
            if (token != availabilityGeneration || availabilityCamera == null
                    || !availabilityCamera.equals(cameraId) || !cameraExpectedActive) return;
            if (state == CameraRuntimeState.RECOVERING && inFlight == null) {
                attemptRecoveryLocked("availability");
            } else {
                resetHealthBaseline();
                scheduleWatchdogLocked();
            }
        }
    }


    private void detachAvailabilityLocked() {
        availabilityGeneration++;
        if (availabilityAttachment != null) availabilityAttachment.close();
        availabilityAttachment = null;
        availabilityCamera = null;
    }
    private String cameraId() {
        CameraRuntimeSelection current = activeSelection != null
                ? activeSelection : recoverySelection;
        return current == null ? "none" : current.cameraId().value();
    }

    private void logHeld(String action, String detail) {
        logger.info(LogCategory.CAMERA, "unspecified", "camera_runtime held_command action=" + action
                + " camera=" + cameraId() + " transitionGeneration="
                + transitionGeneration + " detail=" + detail);
    }

    private void notifyListeners() {
        RuntimeSnapshot value = snapshot();
        for (Listener listener : new ArrayList<>(listeners)) {
            try {
                listener.onStateChanged(value);
            } catch (RuntimeException error) {
                logger.warn(LogCategory.CAMERA, "unspecified", null, "camera_runtime listener_failed", error);
            }
        }
    }

    private record PendingTransition(
            ProcessCameraRuntimeBackend.Operation operation,
            CameraRuntimeSelection target) {}

    private record HeldCommand(
            com.dvid.dcam.feature.device.domain.camera.CameraId cameraId,
            long transitionGeneration,
            long sequence,
            boolean recordStart,
            DcamFileType mediaType) {}
}
