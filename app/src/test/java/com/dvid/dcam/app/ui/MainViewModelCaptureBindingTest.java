package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import com.dvid.dcam.feature.capture.application.port.AudioRecorder;
import com.dvid.dcam.feature.capture.application.usecase.AudioRecordingUseCase;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEvents;
import com.dvid.dcam.feature.capture.application.usecase.SerializedRecordingCoordinator;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import java.util.function.Consumer;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.application.port.DeviceRepository;
import com.dvid.dcam.feature.device.application.usecase.RefreshDeviceStatusUseCase;
import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import com.dvid.dcam.feature.media.application.usecase.BrowseMediaUseCase;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class MainViewModelCaptureBindingTest {
    @BeforeEach void runLiveDataSynchronously() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable runnable) { runnable.run(); }
            @Override public void postToMainThread(Runnable runnable) { runnable.run(); }
            @Override public boolean isMainThread() { return true; }
        });
    }

    @AfterEach void restoreLiveDataExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test void recordingStateStartsOnlyAfterCameraConfirmsStart() {
        MainViewModel viewModel = viewModel();
        com.dvid.dcam.feature.capture.application.usecase.SerializedRecordingCoordinator events =
                new com.dvid.dcam.feature.capture.application.usecase.SerializedRecordingCoordinator(
                        Runnable::run);
        events.bindCamera(new com.dvid.dcam.feature.capture.application.port.CameraGateway() {
            @Override public void takePhoto() {}
            @Override public void startVideo() {}
            @Override public void startImp() {}
            @Override public void stopRecording() {}
        });
        viewModel.bindCaptureEvents(events);

        events.startVideo();
        assertEquals(RecordingMode.IDLE, viewModel.state().getValue().getCapture().getMode());
        assertEquals(null, viewModel.state().getValue().getCapture().getStartedAtMillis());
        assertEquals("Starting", viewModel.state().getValue().getMessage());

        events.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        assertEquals(RecordingMode.VIDEO, viewModel.state().getValue().getCapture().getMode());
        assertEquals("Recording", viewModel.state().getValue().getMessage());
        viewModel.onCleared();
    }

    @Test void storageBlockedStartClearsStartingWithoutShowingError() {
        MainViewModel viewModel = viewModel();
        SerializedRecordingCoordinator events = new SerializedRecordingCoordinator(Runnable::run);
        events.bindCamera(new com.dvid.dcam.feature.capture.application.port.CameraGateway() {
            @Override public void takePhoto() {}
            @Override public void startVideo() {}
            @Override public void startImp() {}
            @Override public void stopRecording() {}
        });
        viewModel.bindCaptureEvents(events);

        events.startVideo();
        assertEquals("Starting", viewModel.state().getValue().getMessage());

        viewModel.unbindCaptureEvents(events);
        events.recordingStartBlocked();
        viewModel.bindCaptureEvents(events);

        assertEquals(RecordingMode.IDLE, viewModel.state().getValue().getCapture().getMode());
        assertEquals(null, viewModel.state().getValue().getMessage());
        viewModel.onCleared();
    }
    @Test void photoSavingStateLastsUntilSuccessOrFailure() {
        MainViewModel viewModel = viewModel();
        SerializedRecordingCoordinator events = new SerializedRecordingCoordinator(Runnable::run);
        viewModel.bindCaptureEvents(events);

        events.photoSaving();
        assertEquals(true, viewModel.state().getValue().getCapture().isPhotoSaving());

        events.photoSaved("photo.jpg");
        assertEquals(false, viewModel.state().getValue().getCapture().isPhotoSaving());
        assertEquals("Saved photo.jpg", viewModel.state().getValue().getMessage());

        events.photoSaving();
        events.photoFailed("Finalization", "write failed");
        assertEquals(false, viewModel.state().getValue().getCapture().isPhotoSaving());
        assertEquals("Finalization failed: write failed",
                viewModel.state().getValue().getMessage());
        viewModel.onCleared();
    }

    @Test void observesOnlyTheCurrentlyBoundCaptureRuntime() {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents first = new TestCaptureEvents();
        TestCaptureEvents second = new TestCaptureEvents();

        viewModel.bindCaptureEvents(first);
        first.recordingStarted(RecordingMode.VIDEO, "first.mp4");
        assertEquals(RecordingMode.VIDEO, viewModel.state().getValue().getCapture().getMode());

        viewModel.bindCaptureEvents(second);
        first.recordingCompleted("first.mp4");
        assertEquals(RecordingMode.VIDEO, viewModel.state().getValue().getCapture().getMode());

        second.recordingStarted(RecordingMode.IMP, "second.mp4");
        assertEquals(RecordingMode.IMP, viewModel.state().getValue().getCapture().getMode());

        viewModel.unbindCaptureEvents(second);
        second.recordingCompleted("second.mp4");
        assertEquals(RecordingMode.IMP, viewModel.state().getValue().getCapture().getMode());
        viewModel.onCleared();
    }

    @Test void activityRecreationPreservesOneRecordingAuthority() {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents events = new TestCaptureEvents();
        viewModel.bindCaptureEvents(events);
        events.recordingStarted(RecordingMode.VIDEO, "video.mp4");

        viewModel.unbindCaptureEvents(events);
        viewModel.bindCaptureEvents(events);

        MainUiState state = viewModel.state().getValue();
        assertEquals(RecordingMode.VIDEO, state.getCapture().getMode());
        events.recordingCompleted("video.mp4");
        assertEquals(RecordingMode.IDLE, viewModel.state().getValue().getCapture().getMode());
        viewModel.onCleared();
    }

    @Test void audioStoppingStopsDurationAndShowsSavingUntilSaved() {
        MainViewModel viewModel = viewModel();
        SerializedRecordingCoordinator events = new SerializedRecordingCoordinator(Runnable::run);
        viewModel.bindCaptureEvents(events);
        events.audioRecordingStarted("audio.aac", 123L);

        events.audioRecordingStopping();

        MainUiState saving = viewModel.state().getValue();
        assertEquals(false, saving.getCapture().isAudioRecording());
        assertEquals(true, saving.getCapture().isAudioSaving());
        assertEquals(null, saving.getCapture().getAudioStartedAtMillis());
        assertEquals("Saving", saving.getMessage());
        events.audioRecordingStopped("audio.aac");
        MainUiState saved = viewModel.state().getValue();
        assertEquals(false, saved.getCapture().isAudioSaving());
        assertEquals("Saved audio.aac", saved.getMessage());
        viewModel.onCleared();
    }

    @Test void audioStateSharesCaptureStreamAcrossActivityRecreation() {
        MainViewModel viewModel = viewModel();
        SerializedRecordingCoordinator events = new SerializedRecordingCoordinator(Runnable::run);
        events.bindCamera(new com.dvid.dcam.feature.capture.application.port.CameraGateway() {
            @Override public void takePhoto() {}
            @Override public void startVideo() {}
            @Override public void startImp() {}
            @Override public void stopRecording() {}
        });
        viewModel.bindCaptureEvents(events);
        events.startVideo();
        events.recordingStarted(RecordingMode.VIDEO, "video.mp4");
        events.audioRecordingStarted("audio.aac", 123L);

        assertEquals(RecordingMode.VIDEO, viewModel.state().getValue().getCapture().getMode());
        assertEquals(true, viewModel.state().getValue().getCapture().isAudioRecording());
        assertEquals(123L,
                viewModel.state().getValue().getCapture().getAudioStartedAtMillis());
        assertEquals(false, viewModel.state().getValue().isCaptureHapticSuppressed());

        viewModel.unbindCaptureEvents(events);
        events.audioRecordingStopped("audio.aac");
        viewModel.bindCaptureEvents(events);

        assertEquals(RecordingMode.VIDEO, viewModel.state().getValue().getCapture().getMode());
        assertEquals(false, viewModel.state().getValue().getCapture().isAudioRecording());
        assertEquals("Saved audio.aac", viewModel.state().getValue().getMessage());
        assertEquals(true, viewModel.state().getValue().isCaptureHapticSuppressed());
        viewModel.onCleared();
    }

    @Test void audioUseCasePublishesStateThroughSharedCaptureEvents() {
        MainViewModel viewModel = viewModel();
        SerializedRecordingCoordinator events = new SerializedRecordingCoordinator(Runnable::run);
        boolean[] recording = { false };
        AudioRecordingUseCase audio = new AudioRecordingUseCase(new AudioRecorder() {
            @Override public String toggle() {
                recording[0] = !recording[0];
                return "audio.aac";
            }
            @Override public boolean isRecording() { return recording[0]; }
            @Override public long recordingStartedAtMillis() { return 456L; }
            @Override public void release() {}
        }, events);
        viewModel.bindCaptureEvents(events);

        audio.toggleAudio();
        assertEquals(true, viewModel.state().getValue().getCapture().isAudioRecording());
        assertEquals(456L,
                viewModel.state().getValue().getCapture().getAudioStartedAtMillis());

        audio.toggleAudio();
        assertEquals(false, viewModel.state().getValue().getCapture().isAudioRecording());
        assertEquals("Saved audio.aac", viewModel.state().getValue().getMessage());
        viewModel.onCleared();
    }

    @Test void lowStorageFailureSurvivesActivityRecreation() {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents events = new TestCaptureEvents();
        viewModel.bindCaptureEvents(events);
        events.captureFailed("Storage",
                "Low storage. Cannot record video.");

        viewModel.unbindCaptureEvents(events);
        viewModel.bindCaptureEvents(events);

        assertEquals(
                "Storage failed: Low storage. Cannot record video.",
                viewModel.state().getValue().getMessage());

        viewModel.onCleared();
    }

    @Test void repeatedStorageFailuresEachPublishNoticeButReplayDoesNot() {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents events = new TestCaptureEvents();
        viewModel.bindCaptureEvents(events);

        events.captureStorageUnavailable();
        String firstNotice = viewModel.takeCaptureFailureNotice();

        assertEquals("Storage failed: Capture storage unavailable",
                viewModel.state().getValue().getMessage());
        assertEquals("Storage failed: Capture storage unavailable", firstNotice);
        assertEquals(null, viewModel.takeCaptureFailureNotice());

        viewModel.unbindCaptureEvents(events);
        viewModel.bindCaptureEvents(events);
        assertEquals(null, viewModel.takeCaptureFailureNotice());

        events.captureStorageUnavailable();
        assertEquals(firstNotice, viewModel.takeCaptureFailureNotice());
        viewModel.onCleared();
    }

    @Test void photoAndRecordingFailuresEachPublishNotice() {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents events = new TestCaptureEvents();
        viewModel.bindCaptureEvents(events);

        events.photoFailed("Photo", "capture_failed:reason=3");
        assertEquals("Photo failed: capture_failed:reason=3",
                viewModel.takeCaptureFailureNotice());

        events.captureFailed("Recording", "Camera is not ready");
        assertEquals("Recording failed: Camera is not ready",
                viewModel.takeCaptureFailureNotice());
        assertEquals(null, viewModel.takeCaptureFailureNotice());
        viewModel.onCleared();
    }

    @Test void genuineSavedEventsRecordNoticeTime() {
        assertGenuineSavedEventRecordsTime(
                events -> events.recordingCompleted("video.mp4"));
        assertGenuineSavedEventRecordsTime(
                events -> events.recordingStoppedForStorage("video.mp4"));
        assertGenuineSavedEventRecordsTime(
                events -> events.photoSaved("photo.jpg"));
        assertGenuineSavedEventRecordsTime(
                events -> events.audioRecordingStopped("audio.aac"));
    }

    @Test void storageStopUsesDedicatedNoticeMarker() {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents events = new TestCaptureEvents();
        viewModel.bindCaptureEvents(events);

        events.recordingStoppedForStorage("video.mp4");

        assertEquals(MainViewModel.STORAGE_STOPPED_MESSAGE_PREFIX + "video.mp4",
                viewModel.state().getValue().getMessage());
        assertTrue(viewModel.lastSavedNoticeAtMillis() > 0L);
        viewModel.onCleared();
    }

    @Test void replayedSavedEventDoesNotRecordNoticeTime() {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents events = new TestCaptureEvents();
        events.photoSaved("photo.jpg");

        viewModel.bindCaptureEvents(events);

        assertEquals(0L, viewModel.lastSavedNoticeAtMillis());
        assertEquals(true, viewModel.state().getValue().isCaptureHapticSuppressed());
        viewModel.onCleared();
    }

    @Test void audioStopWithoutFileDoesNotRecordNoticeTime() {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents events = new TestCaptureEvents();
        viewModel.bindCaptureEvents(events);

        events.audioRecordingStopped(null);

        assertEquals(0L, viewModel.lastSavedNoticeAtMillis());
        viewModel.onCleared();
    }
    @Test void completionDuringActivityRecreationIsReplayedOnRebind() {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents events = new TestCaptureEvents();
        viewModel.bindCaptureEvents(events);
        events.recordingStarted(RecordingMode.VIDEO, "video.mp4");

        viewModel.unbindCaptureEvents(events);
        events.recordingCompleted("video.mp4");
        assertEquals(RecordingMode.VIDEO, viewModel.state().getValue().getCapture().getMode());

        viewModel.bindCaptureEvents(events);
        assertEquals(RecordingMode.IDLE, viewModel.state().getValue().getCapture().getMode());
        assertEquals("Saved video.mp4", viewModel.state().getValue().getMessage());
        viewModel.onCleared();
    }

    private static final class TestCaptureEvents implements CaptureEvents {
        private static final Consumer<CaptureEvent> NONE = event -> {};
        private Consumer<CaptureEvent> listener = NONE;
        private RecordingMode currentMode = RecordingMode.IDLE;
        private CaptureEvent snapshot;

        @Override public void setListener(Consumer<CaptureEvent> listener) {
            this.listener = listener == null ? NONE : listener;
            if (snapshot != null && snapshot.getType() != CaptureEvent.Type.ERROR) {
                this.listener.accept(snapshot.asReplay());
            }
        }
        @Override public void clearListener() { listener = NONE; }
        @Override public RecordingMode currentMode() { return currentMode; }
        @Override public void recordingStarted(RecordingMode mode, String fileName) {
            currentMode = mode;
            emit(CaptureEvent.recordingStarted(mode, fileName));
        }
        @Override public void recordingInterrupted(String message) {
            emit(CaptureEvent.recordingInterrupted(currentMode, message));
        }
        @Override public void recordingResumed() {
            emit(CaptureEvent.recordingResumed(currentMode));
        }
        @Override public void recordingCompleted(String fileName) {
            currentMode = RecordingMode.IDLE;
            emit(CaptureEvent.recordingCompleted(fileName));
        }
        @Override public void recordingStoppedForStorage(String fileName) {
            currentMode = RecordingMode.IDLE;
            emit(CaptureEvent.recordingStoppedForStorage(fileName));
        }
        @Override public void audioRecordingStarted(String fileName, long startedAtMillis) {
            emit(CaptureEvent.audioRecordingStarted(fileName, startedAtMillis));
        }
        @Override public void audioRecordingStopped(String fileName) {
            emit(CaptureEvent.audioRecordingStopped(fileName));
        }
        @Override public void photoSaved(String fileName) {
            emit(CaptureEvent.photoSaved(fileName));
        }
        @Override public void photoFailed(String operation, String message) {
            emit(CaptureEvent.photoFailed(operation, message));
        }
        @Override public void captureStorageUnavailable() {
            emit(CaptureEvent.storageUnavailable());
        }
        @Override public void captureFailed(String operation, String message) {
            currentMode = RecordingMode.IDLE;
            emit(CaptureEvent.error(operation, message));
        }
        private void emit(CaptureEvent event) {
            snapshot = event;
            listener.accept(event);
        }
    }

    private static void assertGenuineSavedEventRecordsTime(
            Consumer<TestCaptureEvents> completeCapture) {
        MainViewModel viewModel = viewModel();
        TestCaptureEvents events = new TestCaptureEvents();
        viewModel.bindCaptureEvents(events);
        long beforeCompletion = System.currentTimeMillis();

        completeCapture.accept(events);

        long savedNoticeAtMillis = viewModel.lastSavedNoticeAtMillis();
        assertTrue(savedNoticeAtMillis >= beforeCompletion);
        assertTrue(savedNoticeAtMillis <= System.currentTimeMillis());
        viewModel.onCleared();
    }
    private static MainViewModel viewModel() {
        return new MainViewModel(
                DeviceStatus.unknown(),
                new RefreshDeviceStatusUseCase(new DeviceRepository() {
                    @Override public DeviceInfo readInfo() { return null; }
                    @Override public DeviceStatus readStatus() { return DeviceStatus.unknown(); }
                }),
                new BrowseMediaUseCase(relativePath -> Collections.emptyList()));
    }
}
