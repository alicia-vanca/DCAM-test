package com.dvid.dcam.feature.capture.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.capture.application.port.CameraGateway;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class SerializedRecordingCoordinatorTest {
    @Test void permissionBlockRejectsStartWithoutQueueingIt() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean permissionGranted = new AtomicBoolean(false);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, () -> true, () -> !permissionGranted.get());
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        coordinator.startImp();
        executor.runAll();
        permissionGranted.set(true);
        coordinator.resumePendingStart();
        executor.runAll();

        assertEquals(List.of(), camera.calls);
        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
    }

    @Test void captureRecheckBlocksRecordingCommandsWithoutQueueingThem() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean blocked = new AtomicBoolean(true);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, () -> true, blocked::get);
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        coordinator.startImp();
        executor.runAll();
        blocked.set(false);
        coordinator.resumePendingStart();
        executor.runAll();

        assertEquals(List.of(), camera.calls);
        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
    }

    @Test void captureBlockKeepsActiveVideoDuringImpCommands() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean blocked = new AtomicBoolean(false);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, () -> true, blocked::get);
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        executor.runAll();
        blocked.set(true);

        coordinator.startImp();
        coordinator.toggleImp();
        executor.runAll();

        assertEquals(List.of("start-video"), camera.calls);
        assertEquals(RecordingMode.VIDEO, coordinator.currentMode());
    }

    @Test void unavailableCameraBlocksRecordingWithoutCreatingPendingMode() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean unavailable = new AtomicBoolean(true);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, () -> false, unavailable::get);
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        coordinator.startImp();
        executor.runAll();
        unavailable.set(false);
        coordinator.resumePendingStart();
        executor.runAll();

        assertEquals(List.of(), camera.calls);
        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
    }

    @Test void startPressedDuringCameraTransitionRunsAfterTransitionCompletes() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean startAllowed = new AtomicBoolean(false);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, startAllowed::get);
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        executor.runAll();

        assertEquals(List.of(), camera.calls);
        assertEquals(RecordingMode.VIDEO, coordinator.currentMode());

        startAllowed.set(true);
        coordinator.resumePendingStart();
        executor.runAll();

        assertEquals(List.of("start-video"), camera.calls);
        assertEquals(RecordingMode.VIDEO, coordinator.currentMode());
    }

    @Test void blockedStartRequestsCameraPreparationThenResumes() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean startAllowed = new AtomicBoolean(false);
        AtomicBoolean preparationRequested = new AtomicBoolean();
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, startAllowed::get, () -> false,
                () -> preparationRequested.set(true));
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        executor.runAll();

        assertTrue(preparationRequested.get());
        assertTrue(coordinator.isRecordingStartPending());
        assertEquals(List.of(), camera.calls);

        startAllowed.set(true);
        coordinator.resumePendingStart();
        executor.runAll();

        assertEquals(List.of("start-video"), camera.calls);
    }

    @Test void repeatedPendingStartPressRequestsCameraRecoveryAgain() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        int[] preparationRequests = new int[1];
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, () -> false, () -> false, () -> preparationRequests[0]++);
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        executor.runAll();
        coordinator.startVideo();
        executor.runAll();

        assertEquals(2, preparationRequests[0]);
        assertTrue(coordinator.isRecordingStartPending());
        assertEquals(List.of(), camera.calls);
    }
    @Test void reportsStartPendingUntilRecordingStarts() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean startAllowed = new AtomicBoolean(false);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, startAllowed::get);
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        executor.runAll();
        assertTrue(coordinator.isRecordingStartPending());

        startAllowed.set(true);
        coordinator.resumePendingStart();
        executor.runAll();
        assertTrue(coordinator.isRecordingStartPending());

        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        executor.runAll();
        assertFalse(coordinator.isRecordingStartPending());
    }

    @Test void repeatedStartPressesDuringCameraTransitionQueueOneStart() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean startAllowed = new AtomicBoolean(false);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, startAllowed::get);
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        coordinator.startVideo();
        coordinator.startVideo();
        executor.runAll();

        assertEquals(List.of(), camera.calls);

        startAllowed.set(true);
        coordinator.resumePendingStart();
        executor.runAll();

        assertEquals(List.of("start-video"), camera.calls);
    }

    @Test void queuedStartWaitsWhenTransitionBeginsBeforeExecution() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean startAllowed = new AtomicBoolean(true);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, startAllowed::get);
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        startAllowed.set(false);
        executor.runAll();

        assertEquals(List.of(), camera.calls);
        assertEquals(RecordingMode.VIDEO, coordinator.currentMode());

        startAllowed.set(true);
        coordinator.resumePendingStart();
        executor.runAll();

        assertEquals(List.of("start-video"), camera.calls);
    }

    @Test void stopCancelsStartQueuedDuringCameraTransition() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean startAllowed = new AtomicBoolean(false);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, startAllowed::get);
        coordinator.bindCamera(camera);

        coordinator.startVideo();
        coordinator.stopRecording();
        executor.runAll();
        startAllowed.set(true);
        coordinator.resumePendingStart();
        executor.runAll();

        assertEquals(List.of(), camera.calls);
        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
    }

    @Test void impQueuedDuringCameraTransitionStartsAfterCompletion() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        AtomicBoolean startAllowed = new AtomicBoolean(false);
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(
                executor, startAllowed::get);
        coordinator.bindCamera(camera);

        coordinator.startImp();
        executor.runAll();
        startAllowed.set(true);
        coordinator.resumePendingStart();
        executor.runAll();

        assertEquals(List.of("start-imp"), camera.calls);
        assertEquals(RecordingMode.IMP, coordinator.currentMode());
    }
    @Test void duplicateStartIsRejectedBeforeCameraCallbackArrives() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.startVideo();
        coordinator.startVideo();

        assertEquals(0, camera.videoStarts);
        executor.runAll();
        assertEquals(1, camera.videoStarts);
    }

    @Test void startAndStopCommandsAreProcessedInSubmissionOrder() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.startVideo();
        coordinator.stopRecording();
        coordinator.stopRecording();
        executor.runAll();

        assertEquals(List.of("start-video", "stop"), camera.calls);
    }

    @Test void repeatedVideoToggleDuringStartupIsIgnored() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.toggleVideo();
        coordinator.toggleVideo();
        executor.runAll();

        assertEquals(List.of("start-video"), camera.calls);
    }

    @Test void videoToggleStartsFreshAfterStartupFailure() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.toggleVideo();
        executor.runAll();
        coordinator.captureFailed("Storage", "SD card unavailable.");
        executor.runAll();
        coordinator.toggleVideo();
        executor.runAll();

        assertEquals(List.of("start-video", "start-video"), camera.calls);
    }

    @Test void cameraEventsShareTheQueueAndCompletionAllowsTheNextStart() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);
        List<CaptureEvent.Type> events = new ArrayList<>();
        coordinator.setListener(event -> events.add(event.getType()));

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "first.mp4");
        coordinator.recordingCompleted("first.mp4");
        coordinator.startVideo();
        executor.runAll();

        assertEquals(2, camera.videoStarts);
        assertEquals(RecordingMode.VIDEO, coordinator.currentMode());
        assertEquals(List.of(
                CaptureEvent.Type.RECORDING_STARTING,
                CaptureEvent.Type.RECORDING_STARTED,
                CaptureEvent.Type.RECORDING_COMPLETED,
                CaptureEvent.Type.RECORDING_STARTING), events);
    }

    @Test void photoSavingReplaysUntilTerminalEvent() {
        ManualExecutor executor = new ManualExecutor();
        SerializedRecordingCoordinator coordinator = coordinator(executor, new FakeCamera());
        List<CaptureEvent.Type> events = new ArrayList<>();
        coordinator.setListener(event -> events.add(event.getType()));

        coordinator.photoSaving();
        executor.runAll();
        assertEquals(List.of(CaptureEvent.Type.PHOTO_SAVING), events);

        coordinator.clearListener();
        executor.runAll();
        events.clear();
        coordinator.setListener(event -> events.add(event.getType()));
        executor.runAll();
        assertEquals(List.of(CaptureEvent.Type.PHOTO_SAVING), events);

        coordinator.photoSaved("photo.jpg");
        executor.runAll();
        assertEquals(List.of(CaptureEvent.Type.PHOTO_SAVING,
                CaptureEvent.Type.PHOTO_SAVED), events);
    }

    @Test void photoFailureDoesNotResetActiveVideoState() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        coordinator.photoFailed("Storage", "Photo failed");
        executor.runAll();

        assertEquals(RecordingMode.VIDEO, coordinator.currentMode());
        coordinator.stopRecording();
        coordinator.recordingCompleted("video.mp4");
        coordinator.startImp();
        executor.runAll();

        assertEquals(List.of("start-video", "stop", "start-imp"), camera.calls);
        assertEquals(RecordingMode.IMP, coordinator.currentMode());
    }

    @Test void rejectedStartThenSwitchReleaseDoesNotEnterStopping() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);
        List<CaptureEvent.Type> events = new ArrayList<>();
        coordinator.setListener(event -> events.add(event.getType()));

        coordinator.startVideo();
        executor.runAll();
        coordinator.captureFailed("Recording", "Video recording unavailable on this camera");
        coordinator.stopRecording();
        executor.runAll();

        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
        assertEquals(List.of("start-video"), camera.calls);
        assertEquals(List.of(
                CaptureEvent.Type.RECORDING_STARTING,
                CaptureEvent.Type.ERROR), events);
    }
    @Test void storageBlockedStartReturnsToIdleWithoutErrorEvent() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);
        List<CaptureEvent.Type> events = new ArrayList<>();
        coordinator.setListener(event -> events.add(event.getType()));

        coordinator.startVideo();
        executor.runAll();
        assertTrue(coordinator.isRecordingStartPending());

        coordinator.recordingStartBlocked();
        executor.runAll();

        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
        assertFalse(coordinator.isRecordingStartPending());
        assertEquals(List.of(CaptureEvent.Type.RECORDING_STARTING,
                CaptureEvent.Type.RECORDING_START_CANCELLED), events);
        assertEquals(List.of("start-video"), camera.calls);
    }

    @Test void stopDuringStartupDoesNotEmitSavingTransition() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);
        List<CaptureEvent.Type> events = new ArrayList<>();
        coordinator.setListener(event -> events.add(event.getType()));

        coordinator.startVideo();
        coordinator.stopRecording();
        executor.runAll();

        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
        assertEquals(List.of("start-video", "stop"), camera.calls);
        assertEquals(List.of(CaptureEvent.Type.RECORDING_STARTING), events);
    }
    @Test void lateStartAfterStartupCancellationIsIgnored() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);
        List<CaptureEvent.Type> events = new ArrayList<>();
        coordinator.setListener(event -> events.add(event.getType()));

        coordinator.startVideo();
        coordinator.stopRecording();
        executor.runAll();
        coordinator.recordingStarted(RecordingMode.VIDEO, "late.mp4");
        executor.runAll();

        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
        assertEquals(List.of(CaptureEvent.Type.RECORDING_STARTING), events);
    }
    @Test void acceptedStopEmitsUiTransitionBeforeCameraStop() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);
        List<CaptureEvent.Type> events = new ArrayList<>();
        coordinator.setListener(event -> events.add(event.getType()));

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        coordinator.stopRecording();
        executor.runAll();

        assertEquals(List.of(
                CaptureEvent.Type.RECORDING_STARTING,
                CaptureEvent.Type.RECORDING_STARTED,
                CaptureEvent.Type.RECORDING_STOPPING), events);
        assertEquals(List.of("start-video", "stop"), camera.calls);
    }

    @Test void impHandoffDoesNotAllowAnUnrelatedVideoStart() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        coordinator.startImp();
        coordinator.startVideo();
        executor.runAll();

        assertEquals(1, camera.videoStarts);
        assertEquals(1, camera.impStarts);
    }

    @Test void impHandoffKeepsAuthorityAfterPreviousRecordingFailure() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        coordinator.startImp();
        executor.runAll();

        coordinator.captureFailed("Finalization", "previous segment failed");
        coordinator.recordingStarted(RecordingMode.IMP, "imp.mp4");
        executor.runAll();

        assertEquals(RecordingMode.IMP, coordinator.currentMode());
        coordinator.stopRecording();
        executor.runAll();
        assertEquals(List.of("start-video", "start-imp", "stop"), camera.calls);
    }

    @Test void impHandoffKeepsAuthorityAfterPreviousStorageStop() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        coordinator.startImp();
        executor.runAll();

        coordinator.recordingStoppedForStorage("video.mp4");
        coordinator.recordingStarted(RecordingMode.IMP, "imp.mp4");
        executor.runAll();

        assertEquals(RecordingMode.IMP, coordinator.currentMode());
        coordinator.stopRecording();
        executor.runAll();
        assertEquals(List.of("start-video", "start-imp", "stop"), camera.calls);
    }

    @Test void repeatedImpToggleDuringStartupIsIgnored() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.toggleImp();
        coordinator.toggleImp();
        executor.runAll();

        assertEquals(List.of("start-imp"), camera.calls);
    }

    @Test void clearingActivityListenerDoesNotStopActiveRecording() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        executor.runAll();

        coordinator.clearListener();
        executor.runAll();

        assertEquals(RecordingMode.VIDEO, coordinator.currentMode());
        assertEquals(List.of("start-video"), camera.calls);
    }

    @Test void interruptedRecordingResumesAndStopWhilePausedStopsCurrentFile() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        coordinator.recordingInterrupted("Camera interrupted by another application");
        executor.runAll();
        assertEquals(RecordingMode.VIDEO, coordinator.currentMode());

        coordinator.recordingResumed();
        executor.runAll();
        assertEquals(RecordingMode.VIDEO, coordinator.currentMode());

        coordinator.recordingInterrupted("Camera interrupted by another application");
        coordinator.stopRecording();
        executor.runAll();
        assertEquals(List.of("start-video", "stop"), camera.calls);
    }
    @Test void listenerRebindReplaysAudioSavingAndCancellationRestoresStart() {
        SerializedRecordingCoordinator coordinator =
                new SerializedRecordingCoordinator(Runnable::run);
        List<CaptureEvent> events = new ArrayList<>();
        coordinator.setListener(events::add);
        events.clear();
        coordinator.audioRecordingStarted("audio.aac", 123L);
        events.clear();

        coordinator.audioRecordingStopping();

        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STOPPING, events.get(0).getType());
        coordinator.clearListener();
        events.clear();
        coordinator.setListener(events::add);
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STOPPING, events.get(0).getType());
        assertTrue(events.get(0).isReplay());
        events.clear();

        coordinator.audioRecordingStopCancelled();

        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STARTED, events.get(0).getType());
        assertEquals("audio.aac", events.get(0).getFileName());
        assertEquals(123L, events.get(0).getStartedAtMillis());
    }

    @Test void listenerRebindReplaysLatestAudioState() {
        SerializedRecordingCoordinator coordinator =
                new SerializedRecordingCoordinator(Runnable::run);
        List<CaptureEvent> events = new ArrayList<>();
        coordinator.setListener(events::add);

        coordinator.audioRecordingStarted("audio.aac", 123L);
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STARTED, events.get(0).getType());
        assertEquals(false, events.get(0).isReplay());

        coordinator.clearListener();
        events.clear();
        coordinator.setListener(events::add);
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STARTED, events.get(0).getType());
        assertEquals("audio.aac", events.get(0).getFileName());
        assertEquals(123L, events.get(0).getStartedAtMillis());
        assertEquals(true, events.get(0).isReplay());

        coordinator.clearListener();
        events.clear();
        coordinator.audioRecordingStopped("audio.aac");
        coordinator.setListener(events::add);
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STOPPED, events.get(0).getType());
        assertEquals("audio.aac", events.get(0).getFileName());
        assertEquals(true, events.get(0).isReplay());
    }

    @Test void listenerRebindDoesNotReplayTerminalError() {
        ManualExecutor executor = new ManualExecutor();
        SerializedRecordingCoordinator coordinator = coordinator(executor, new FakeCamera());
        List<CaptureEvent.Type> replayed = new ArrayList<>();

        coordinator.startVideo();
        coordinator.captureFailed("Storage", "Low storage");
        executor.runAll();
        coordinator.clearListener();
        executor.runAll();

        coordinator.setListener(event -> replayed.add(event.getType()));
        executor.runAll();

        assertEquals(List.of(), replayed);
        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
    }
    @Test void listenerRebindReplaysCompletionMissedDuringActivityRecreation() {
        ManualExecutor executor = new ManualExecutor();
        SerializedRecordingCoordinator coordinator = coordinator(executor, new FakeCamera());
        List<CaptureEvent.Type> replayed = new ArrayList<>();

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        executor.runAll();
        coordinator.clearListener();
        coordinator.recordingCompleted("video.mp4");
        executor.runAll();

        coordinator.setListener(event -> replayed.add(event.getType()));
        executor.runAll();

        assertEquals(List.of(CaptureEvent.Type.RECORDING_COMPLETED), replayed);
        assertEquals(RecordingMode.IDLE, coordinator.currentMode());
    }

    @Test void listenerRebindReconstructsInterruptedRecordingSnapshot() {
        ManualExecutor executor = new ManualExecutor();
        SerializedRecordingCoordinator coordinator = coordinator(executor, new FakeCamera());
        List<CaptureEvent.Type> replayed = new ArrayList<>();

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        coordinator.recordingInterrupted("camera busy");
        executor.runAll();
        coordinator.clearListener();
        executor.runAll();

        coordinator.setListener(event -> replayed.add(event.getType()));
        executor.runAll();

        assertEquals(List.of(
                CaptureEvent.Type.RECORDING_STARTED,
                CaptureEvent.Type.RECORDING_INTERRUPTED), replayed);
    }

    @Test void cameraCanOnlyBeBoundOnce() {
        SerializedRecordingCoordinator coordinator =
                new SerializedRecordingCoordinator(Runnable::run);
        coordinator.bindCamera(new FakeCamera());

        assertThrows(IllegalStateException.class,
                () -> coordinator.bindCamera(new FakeCamera()));
    }

    private static SerializedRecordingCoordinator coordinator(
            ManualExecutor executor, FakeCamera camera) {
        SerializedRecordingCoordinator coordinator = new SerializedRecordingCoordinator(executor);
        coordinator.bindCamera(camera);
        return coordinator;
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override public void execute(Runnable command) {
            tasks.offer(command);
        }

        private void runAll() {
            Runnable task;
            while ((task = tasks.poll()) != null) task.run();
        }
    }

    private static final class FakeCamera implements CameraGateway {
        private final List<String> calls = new ArrayList<>();
        private int videoStarts;
        private int impStarts;

        @Override public void takePhoto() {
            calls.add("photo");
        }

        @Override public void startVideo() {
            videoStarts++;
            calls.add("start-video");
        }

        @Override public void startImp() {
            impStarts++;
            calls.add("start-imp");
        }

        @Override public void stopRecording() {
            calls.add("stop");
        }
    }
}
