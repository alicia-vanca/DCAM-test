package com.dvid.dcam.platform.camera.shared.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.platform.storage.DcamFileType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

final class ProcessCameraRuntimeOwnerTest {
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");

    @Test void capabilityScanReservationBlocksCameraInitialization() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = owner(backend);
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);

        assertTrue(owner.beginCapabilityScan());
        assertFalse(owner.beginCapabilityScan());
        assertEquals(ProcessCameraRuntimeOwner.Submission.REJECTED_TRANSITION,
                owner.initialize(selection));
        assertEquals(ProcessCameraRuntimeOwner.Submission.REJECTED_TRANSITION,
                owner.bindCommitted());
        assertEquals(ProcessCameraRuntimeOwner.Submission.REJECTED_TRANSITION,
                owner.recover());
        assertTrue(backend.operations().isEmpty());

        owner.endCapabilityScan();

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED,
                owner.initialize(selection));
        assertEquals(List.of(ProcessCameraRuntimeBackend.Operation.INITIALIZE),
                backend.operations());
    }

    @Test void persistedSelectionCanBindFromClosedWithoutVerification() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = owner(backend);
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED,
                owner.bindCommitted(selection));
        assertEquals(CameraRuntimeState.BINDING, owner.snapshot().state());
        assertEquals(List.of(ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED),
                backend.operations());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "bound"));

        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
        assertEquals(selection, owner.snapshot().committedSelection().orElseThrow());
    }

    @Test void heldCommandsCoalesceAndDrainOnceInArrivalOrder() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = owner(backend);
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED,
                owner.initialize(selection));
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.startRecording());
        assertEquals(ProcessCameraRuntimeOwner.Submission.COALESCED, owner.startRecording());
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.capturePhoto());
        assertEquals(ProcessCameraRuntimeOwner.Submission.COALESCED, owner.capturePhoto());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "ready"));
        assertEquals(List.of(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                ProcessCameraRuntimeBackend.Operation.START_RECORDING), backend.operations());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("recording"));
        assertEquals(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                backend.lastCommand().operation());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("photo"));

        assertEquals(CameraRuntimeState.RECORDING, owner.snapshot().state());
        assertFalse(owner.snapshot().pendingRecordStart());
        assertFalse(owner.snapshot().pendingPhoto());
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.START_RECORDING));
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
        assertEquals(1, backend.maxPending());
    }

    @Test void photoDuringSettingVerificationRunsWithTheVerifiedTargetTuple() {
        ManualBackend backend = new ManualBackend();
        CameraRuntimeSelection original = selection("0", StandardResolutionLabel.FHD);
        CameraRuntimeSelection updated = selection("0", StandardResolutionLabel.HD, 24);
        ProcessCameraRuntimeOwner owner = readyOwner(backend, original);

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED,
                owner.verifySetting(updated));
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD,
                owner.capturePhoto());
        assertTrue(owner.snapshot().pendingPhoto());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                updated, context(updated, 2), "setting_ready"));

        assertEquals(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                backend.lastCommand().operation());
        assertEquals(updated, owner.snapshot().committedSelection().orElseThrow());
        assertFalse(owner.snapshot().pendingPhoto());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("photo"));

        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
    }

    @Test void recordStartDuringActivePhotoIsHeldAndRunsAfterPhoto() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.capturePhoto());
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD,
                owner.startRecording());
        assertTrue(owner.snapshot().pendingRecordStart());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("photo"));

        assertEquals(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                backend.lastCommand().operation());
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.START_RECORDING));
        assertFalse(owner.snapshot().pendingRecordStart());
    }

    @Test void photoDuringRecordingStartIsHeldAndRunsAfterRecordingStarts() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.startRecording());
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.capturePhoto());
        assertTrue(owner.snapshot().pendingPhoto());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("recording"));

        assertEquals(CameraRuntimeState.RECORDING, owner.snapshot().state());
        assertEquals(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                backend.lastCommand().operation());
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
        assertFalse(owner.snapshot().pendingPhoto());
    }

    @Test void photoDuringRecordingStopIsHeldAndRunsAfterEncoderStops() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));
        owner.startRecording();
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("recording_started"));

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.stopRecording());
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.capturePhoto());
        assertTrue(owner.snapshot().pendingPhoto());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("recording_input_stopped"));

        assertEquals(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                backend.lastCommand().operation());
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
        assertFalse(owner.snapshot().pendingPhoto());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("photo_finalized"));
        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
    }

    @Test void photoUpdatesOnlyActiveBindingAndRecordingRestoresCommittedBinding() {
        ManualBackend backend = new ManualBackend();
        CameraRuntimeSelection committed = selection("0", StandardResolutionLabel.FHD);
        StandardResolution hd = new StandardResolution(StandardResolutionLabel.HD,
                new CameraResolution(1280, 720));
        CameraRuntimeSelection photoSelection = new CameraRuntimeSelection(
                committed.cameraId(), committed.verificationPipelineId(), committed.codec(),
                new CaptureModeTuple(committed.tuple().videoMode(), new ImageMode(hd)));
        ProcessCameraRuntimeOwner owner = readyOwner(backend, committed);

        owner.capturePhoto();
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass(
                photoSelection, context(photoSelection, 2), "photo_retained"));

        assertEquals(photoSelection, owner.snapshot().activeSelection().orElseThrow());
        assertEquals(committed, owner.snapshot().committedSelection().orElseThrow());

        owner.startRecording();
        assertEquals(committed, backend.lastCommand().target().orElseThrow());
        assertTrue(photoSelection.matches(
                backend.lastCommand().activeBinding().orElseThrow()));
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass(
                committed, context(committed, 3), "recording_started"));

        assertEquals(CameraRuntimeState.RECORDING, owner.snapshot().state());
        assertEquals(committed, owner.snapshot().activeSelection().orElseThrow());
        assertEquals(committed, owner.snapshot().committedSelection().orElseThrow());
    }

    @Test void recordingReservationWaitsWithoutStartingBackend() {
        ManualBackend backend = new ManualBackend();
        ManualScheduler scheduler = new ManualScheduler();
        AtomicLong delayMillis = new AtomicLong(750L);
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), Runnable::run,
                CameraAvailabilityMonitor.none(), scheduler);
        owner.installBackend(backend);
        owner.installMediaReservation(type -> delayMillis.get());
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);
        owner.initialize(selection);
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "ready"));

        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD,
                owner.startRecording(DcamFileType.IMP));
        assertTrue(owner.snapshot().pendingRecordStart());
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.START_RECORDING));
        assertTrue(scheduler.hasDelay(750L));

        delayMillis.set(0L);
        scheduler.runDelay(750L);

        assertFalse(owner.snapshot().pendingRecordStart());
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.START_RECORDING));
    }

    @Test void recordingTypeReplacementKeepsOlderPhotoFirst() {
        ManualBackend backend = new ManualBackend();
        ManualScheduler scheduler = new ManualScheduler();
        AtomicLong imageDelayMillis = new AtomicLong(800L);
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), Runnable::run,
                CameraAvailabilityMonitor.none(), scheduler);
        owner.installBackend(backend);
        owner.installMediaReservation(type -> type == DcamFileType.IMAGE
                ? imageDelayMillis.get() : type == DcamFileType.VIDEO ? 900L : 0L);
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);
        owner.initialize(selection);
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "ready"));

        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.capturePhoto());
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD,
                owner.startRecording(DcamFileType.VIDEO));
        assertEquals(ProcessCameraRuntimeOwner.Submission.COALESCED,
                owner.startRecording(DcamFileType.IMP));

        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.START_RECORDING));
        assertTrue(scheduler.hasDelay(800L));

        imageDelayMillis.set(0L);
        scheduler.runDelay(800L);

        assertEquals(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                backend.lastCommand().operation());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("photo"));
        assertEquals(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                backend.lastCommand().operation());
    }

    @Test void releaseCancelsPhotoWaitingForReservation() {
        ManualBackend backend = new ManualBackend();
        ManualScheduler scheduler = new ManualScheduler();
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), Runnable::run,
                CameraAvailabilityMonitor.none(), scheduler);
        owner.installBackend(backend);
        owner.installMediaReservation(type -> 600L);
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);
        owner.initialize(selection);
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "ready"));

        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.capturePhoto());
        assertTrue(scheduler.hasDelay(600L));

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.releaseCamera());

        assertFalse(owner.snapshot().pendingPhoto());
        assertFalse(scheduler.hasDelay(600L));
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
        assertEquals(ProcessCameraRuntimeBackend.Operation.RELEASE,
                backend.lastCommand().operation());
    }

    @Test void recordStartThenStopBeforeReadyConsumesBoth() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = owner(backend);
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);

        owner.initialize(selection);
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.startRecording());
        assertEquals(ProcessCameraRuntimeOwner.Submission.CONSUMED, owner.stopRecording());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "ready"));

        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.START_RECORDING));
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING));
    }

    @Test void staleCallbackCannotReplaceNewerSettingTransition() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = owner(backend);
        CameraRuntimeSelection original = selection("0", StandardResolutionLabel.FHD);
        CameraRuntimeSelection updated = selection("0", StandardResolutionLabel.HD);

        owner.initialize(original);
        ManualBackend.Invocation startup = backend.takeNext();
        backend.complete(startup, ProcessCameraRuntimeBackend.Result.ready(
                original, context(original, 1), "startup_ready"));
        owner.verifySetting(updated);

        backend.replay(startup, ProcessCameraRuntimeBackend.Result.ready(
                original, context(original, 1), "late_startup_callback"));
        assertEquals(CameraRuntimeState.VERIFYING, owner.snapshot().state());
        assertEquals(original, owner.snapshot().committedSelection().orElseThrow());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                updated, context(updated, 2), "setting_ready"));
        assertEquals(updated, owner.snapshot().committedSelection().orElseThrow());
    }

    @Test void rapidSettingReplacementCancelsCurrentAndRunsLatest() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));
        CameraRuntimeSelection first = selection("0", StandardResolutionLabel.HD, 24);
        CameraRuntimeSelection middle = selection("0", StandardResolutionLabel.HD, 60);
        CameraRuntimeSelection latest = selection("0", StandardResolutionLabel.HD, 15);

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED,
                owner.verifySetting(first));
        ManualBackend.Invocation firstRun = backend.takeNext();
        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED,
                owner.verifySetting(middle));
        assertEquals(ProcessCameraRuntimeOwner.Submission.COALESCED,
                owner.verifySetting(latest));

        assertEquals(List.of(firstRun.command(), firstRun.command()), backend.cancelled);
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.VERIFY_SETTING));

        backend.complete(firstRun,
                ProcessCameraRuntimeBackend.Result.cancelled("transition_superseded"));

        assertEquals(ProcessCameraRuntimeBackend.Operation.VERIFY_SETTING,
                backend.lastCommand().operation());
        assertEquals(latest, backend.lastCommand().target().orElseThrow());
        assertEquals(CameraRuntimeState.VERIFYING, owner.snapshot().state());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                latest, context(latest, 3), "latest_ready"));

        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
        assertEquals(latest, owner.snapshot().committedSelection().orElseThrow());
    }

    @Test void replacementDuringRestoreRunsAfterBindWithoutCancellingBind() {
        ManualBackend backend = new ManualBackend();
        CameraRuntimeSelection original = selection("0", StandardResolutionLabel.FHD);
        ProcessCameraRuntimeOwner owner = readyOwner(backend, original);
        CameraRuntimeSelection latest = selection("0", StandardResolutionLabel.HD, 24);

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED,
                owner.restoreExact(original));
        ManualBackend.Invocation restore = backend.takeNext();
        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED,
                owner.verifySetting(latest));
        assertTrue(backend.cancelled.isEmpty());

        backend.complete(restore, ProcessCameraRuntimeBackend.Result.ready(
                original, context(original, 2), "restored"));

        assertEquals(ProcessCameraRuntimeBackend.Operation.VERIFY_SETTING,
                backend.lastCommand().operation());
        assertEquals(latest, backend.lastCommand().target().orElseThrow());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                latest, context(latest, 3), "latest_ready"));
        assertEquals(latest, owner.snapshot().committedSelection().orElseThrow());
    }

    @Test void cameraSwitchFailureRebindsExactPreviousSelection() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));
        CameraRuntimeSelection previous = owner.snapshot().committedSelection().orElseThrow();
        CameraRuntimeSelection requested = selection("1", StandardResolutionLabel.HD);

        owner.switchCamera(requested);
        owner.capturePhoto();
        long transitionGeneration = owner.snapshot().transitionGeneration();
        backend.completeNext(ProcessCameraRuntimeBackend.Result.targetFailed(
                "fallback_exhausted"));

        ProcessCameraRuntimeBackend.Command rollback = backend.lastCommand();
        assertEquals(ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED,
                rollback.operation());
        assertEquals(previous, rollback.target().orElseThrow());
        assertEquals(transitionGeneration, rollback.transitionGeneration());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                previous, context(previous, 3), "rollback_ready"));

        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
        assertEquals(previous, owner.snapshot().committedSelection().orElseThrow());
        assertFalse(owner.snapshot().pendingPhoto());
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
    }

    @Test void rollbackRebindFailureEntersRecoveryWithoutChangingCommittedTuple() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));
        CameraRuntimeSelection previous = owner.snapshot().committedSelection().orElseThrow();

        owner.verifySetting(selection("0", StandardResolutionLabel.HD));
        backend.completeNext(ProcessCameraRuntimeBackend.Result.targetFailed(
                "target_failed"));
        backend.completeNext(ProcessCameraRuntimeBackend.Result.recoveryRequired(
                "rollback_bind_failed"));

        assertEquals(CameraRuntimeState.RECOVERING, owner.snapshot().state());
        assertEquals(previous, owner.snapshot().committedSelection().orElseThrow());
        assertTrue(owner.snapshot().activeSelection().isEmpty());
    }

    @Test void processDeathDropsHeldCommandsAndIgnoresLateReadyResult() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = owner(backend);
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);

        owner.initialize(selection);
        owner.startRecording();
        owner.capturePhoto();
        owner.cancelForProcessDeath();
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "late_ready"));

        assertTrue(owner.snapshot().processCancelled());
        assertEquals(CameraRuntimeState.CLOSED, owner.snapshot().state());
        assertFalse(owner.snapshot().pendingRecordStart());
        assertFalse(owner.snapshot().pendingPhoto());
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.START_RECORDING));
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
    }

    @Test void activityReattachObservesSameTransitionAndDoesNotDuplicateCommand() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = owner(backend);
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);
        List<ProcessCameraRuntimeOwner.RuntimeSnapshot> firstActivity = new ArrayList<>();
        ProcessCameraRuntimeOwner.Attachment first = owner.attach(firstActivity::add);

        owner.initialize(selection);
        owner.capturePhoto();
        long generation = owner.snapshot().transitionGeneration();
        first.close();
        List<ProcessCameraRuntimeOwner.RuntimeSnapshot> recreatedActivity = new ArrayList<>();
        owner.attach(recreatedActivity::add);

        ProcessCameraRuntimeOwner.RuntimeSnapshot attached =
                recreatedActivity.get(recreatedActivity.size() - 1);
        assertEquals(generation, attached.transitionGeneration());
        assertTrue(attached.pendingPhoto());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "ready"));
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("photo"));

        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.INITIALIZE));
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
    }

    @Test void committedRebindAlsoHoldsPhotoUntilReady() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));
        CameraRuntimeSelection committed = owner.snapshot().committedSelection().orElseThrow();

        owner.releaseCamera();
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("released"));
        assertEquals(ProcessCameraRuntimeOwner.Submission.REJECTED_NOT_READY,
                owner.capturePhoto());
        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.bindCommitted());
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.capturePhoto());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                committed, context(committed, 2), "rebound"));
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("photo"));

        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
    }

    @Test void stopDuringRecordingPhotoWaitsThenRunsExactlyOnce() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));
        owner.startRecording();
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("recording"));

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.capturePhoto());
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.capturePhoto());
        assertTrue(owner.snapshot().pendingPhoto());
        assertEquals(ProcessCameraRuntimeOwner.Submission.HELD, owner.stopRecording());
        assertFalse(owner.snapshot().pendingPhoto());
        assertEquals(ProcessCameraRuntimeOwner.Submission.COALESCED, owner.stopRecording());
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING));

        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("photo"));

        assertEquals(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                backend.lastCommand().operation());
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO));
        assertEquals(1, backend.count(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING));
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("stopped"));
        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
        assertEquals(1, backend.maxPending());
    }

    @Test void releaseDuringRecordingPhotoStopsBeforeRelease() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));
        owner.startRecording();
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("recording"));
        owner.capturePhoto();

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.releaseCamera());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("photo"));
        assertEquals(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                backend.lastCommand().operation());

        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("stopped"));
        assertEquals(ProcessCameraRuntimeBackend.Operation.RELEASE,
                backend.lastCommand().operation());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("released"));

        assertEquals(CameraRuntimeState.CLOSED, owner.snapshot().state());
        assertEquals(List.of(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                ProcessCameraRuntimeBackend.Operation.RELEASE), backend.operations());
        assertEquals(1, backend.maxPending());
    }

    @Test void recordingRejectsCameraAndSettingChanges() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));
        owner.startRecording();
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("recording"));

        assertEquals(ProcessCameraRuntimeOwner.Submission.REJECTED_RECORDING,
                owner.verifySetting(selection("0", StandardResolutionLabel.HD)));
        assertEquals(ProcessCameraRuntimeOwner.Submission.REJECTED_RECORDING,
                owner.switchCamera(selection("1", StandardResolutionLabel.HD)));
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.VERIFY_SETTING));
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.SWITCH_CAMERA));
    }

    @Test void releaseWaitsForTransitionThenRunsAsOnlyNextCameraOperation() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = owner(backend);
        CameraRuntimeSelection selection = selection("0", StandardResolutionLabel.FHD);

        owner.initialize(selection);
        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.releaseCamera());
        assertEquals(1, backend.operations().size());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "ready_before_release"));

        assertEquals(ProcessCameraRuntimeBackend.Operation.RELEASE,
                backend.lastCommand().operation());
        assertEquals(CameraRuntimeState.RELEASING, owner.snapshot().state());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("released"));
        assertEquals(CameraRuntimeState.CLOSED, owner.snapshot().state());
        assertEquals(1, backend.maxPending());
    }

    @Test void failedInitialBindClearsReleaseRequestBeforeNextInitialization() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = owner(backend);
        CameraRuntimeSelection first = selection("0", StandardResolutionLabel.FHD);
        CameraRuntimeSelection second = selection("0", StandardResolutionLabel.HD);

        owner.initialize(first);
        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.releaseCamera());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.targetFailed("bind_failed"));
        assertEquals(CameraRuntimeState.CLOSED, owner.snapshot().state());

        owner.initialize(second);
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                second, context(second, 2), "ready"));

        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.RELEASE));
    }

    @Test void globalFailureCancelsTargetAndRecoveryUsesCommittedSelection() {
        ManualBackend backend = new ManualBackend();
        ProcessCameraRuntimeOwner owner = readyOwner(backend,
                selection("0", StandardResolutionLabel.FHD));
        CameraRuntimeSelection committed = owner.snapshot().committedSelection().orElseThrow();

        owner.verifySetting(selection("0", StandardResolutionLabel.HD));
        owner.startRecording();
        owner.reportGlobalFailure("camera_disconnected");
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection("0", StandardResolutionLabel.HD),
                context(selection("0", StandardResolutionLabel.HD), 2),
                "stale_target_ready"));

        assertEquals(CameraRuntimeState.RECOVERING, owner.snapshot().state());
        assertFalse(owner.snapshot().pendingRecordStart());
        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED, owner.recover());
        assertEquals(committed, backend.lastCommand().target().orElseThrow());
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                committed, context(committed, 3), "recovered"));
        assertEquals(CameraRuntimeState.READY, owner.snapshot().state());
        assertEquals(0, backend.count(ProcessCameraRuntimeBackend.Operation.START_RECORDING));
    }

    private static final class ManualScheduler implements CameraRuntimeRecoveryScheduler {
        private final List<ScheduledTask> tasks = new ArrayList<>();

        @Override public Task schedule(Runnable action, long delayMillis) {
            ScheduledTask task = new ScheduledTask(action, delayMillis);
            tasks.add(task);
            return () -> task.cancelled = true;
        }

        private boolean hasDelay(long delayMillis) {
            return tasks.stream().anyMatch(task -> !task.cancelled && !task.ran
                    && task.delayMillis == delayMillis);
        }

        private void runDelay(long delayMillis) {
            for (ScheduledTask task : tasks) {
                if (!task.cancelled && !task.ran && task.delayMillis == delayMillis) {
                    task.ran = true;
                    task.action.run();
                    return;
                }
            }
            throw new AssertionError("no pending scheduler task delay=" + delayMillis);
        }

        @Override public void close() {}

        private static final class ScheduledTask {
            private final Runnable action;
            private final long delayMillis;
            private boolean cancelled;
            private boolean ran;

            private ScheduledTask(Runnable action, long delayMillis) {
                this.action = action;
                this.delayMillis = delayMillis;
            }
        }
    }

    private static ProcessCameraRuntimeOwner owner(ManualBackend backend) {
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), Runnable::run);
        owner.installBackend(backend);
        return owner;
    }

    private static ProcessCameraRuntimeOwner readyOwner(ManualBackend backend,
            CameraRuntimeSelection selection) {
        ProcessCameraRuntimeOwner owner = owner(backend);
        owner.initialize(selection);
        backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 1), "ready"));
        return owner;
    }

    private static CameraRuntimeSelection selection(
            String cameraId, StandardResolutionLabel label) {
        return selection(cameraId, label, 30);
    }

    private static CameraRuntimeSelection selection(
            String cameraId, StandardResolutionLabel label, int frameRate) {
        CameraResolution resolution = switch (label) {
            case HD -> new CameraResolution(1280, 720);
            case FHD -> new CameraResolution(1920, 1080);
            default -> throw new IllegalArgumentException("unsupported test label");
        };
        StandardResolution standard = new StandardResolution(label, resolution);
        return new CameraRuntimeSelection(new CameraId(cameraId), PIPELINE,
                VideoCodec.H264, new CaptureModeTuple(
                new VideoMode(standard, frameRate), new ImageMode(standard)));
    }

    private static CameraOperationContext context(
            CameraRuntimeSelection selection, long generation) {
        return new CameraOperationContext(selection.cameraId(),
                selection.verificationPipelineId(), selection.codec(), selection.tuple(),
                generation, 0, CameraOperationDeadline.forCandidate(0));
    }

    private static final class ManualBackend implements ProcessCameraRuntimeBackend {
        private final List<Command> history = new ArrayList<>();
        private final List<Invocation> pending = new ArrayList<>();
        private final List<Command> cancelled = new ArrayList<>();
        private int maxPending;

        @Override public void cancel(Command command) {
            cancelled.add(command);
        }

        @Override public void execute(Command command, Completion completion) {
            history.add(command);
            pending.add(new Invocation(command, completion));
            maxPending = Math.max(maxPending, pending.size());
        }

        private Invocation takeNext() {
            return pending.remove(0);
        }

        private void completeNext(Result result) {
            complete(takeNext(), result);
        }

        private void complete(Invocation invocation, Result result) {
            invocation.completion.complete(result);
        }

        private void replay(Invocation invocation, Result result) {
            invocation.completion.complete(result);
        }

        private List<Operation> operations() {
            return history.stream().map(Command::operation).toList();
        }

        private Command lastCommand() {
            return history.get(history.size() - 1);
        }

        private long count(Operation operation) {
            return history.stream().filter(command -> command.operation() == operation).count();
        }

        private int maxPending() {
            return maxPending;
        }

        private record Invocation(Command command, Completion completion) {}
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }
}
