package com.dvid.dcam.feature.capture.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dvid.dcam.feature.capture.application.port.CameraGateway;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

final class SerializedRecordingCoordinatorTest {
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

    @Test void twoVideoTogglePulsesBeforeQueueDrainsStartThenStop() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.toggleVideo();
        coordinator.toggleVideo();
        executor.runAll();

        assertEquals(List.of("start-video", "stop"), camera.calls);
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

    @Test void sosHandoffDoesNotAllowAnUnrelatedVideoStart() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.startVideo();
        coordinator.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        coordinator.startSos();
        coordinator.startVideo();
        executor.runAll();

        assertEquals(1, camera.videoStarts);
        assertEquals(1, camera.sosStarts);
    }

    @Test void secondSosToggleStopsWhileFirstStartIsPending() {
        ManualExecutor executor = new ManualExecutor();
        FakeCamera camera = new FakeCamera();
        SerializedRecordingCoordinator coordinator = coordinator(executor, camera);

        coordinator.toggleSos();
        coordinator.toggleSos();
        executor.runAll();

        assertEquals(List.of("start-sos", "stop"), camera.calls);
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
        private int sosStarts;

        @Override public void takePhoto() {
            calls.add("photo");
        }

        @Override public void startVideo() {
            videoStarts++;
            calls.add("start-video");
        }

        @Override public void startSos() {
            sosStarts++;
            calls.add("start-sos");
        }

        @Override public void stopRecording() {
            calls.add("stop");
        }
    }
}
