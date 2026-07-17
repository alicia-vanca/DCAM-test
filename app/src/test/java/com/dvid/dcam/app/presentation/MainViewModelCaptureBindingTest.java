package com.dvid.dcam.app.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;
import com.dvid.dcam.core.config.domain.DcamConfig;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEventUseCaseImpl;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
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

    @Test void observesOnlyTheCurrentlyBoundCaptureRuntime() {
        MainViewModel viewModel = viewModel();
        CaptureEventUseCaseImpl first = new CaptureEventUseCaseImpl();
        CaptureEventUseCaseImpl second = new CaptureEventUseCaseImpl();

        viewModel.bindCaptureEvents(first);
        first.recordingStarted(RecordingMode.VIDEO, "first.mp4");
        assertEquals(RecordingMode.VIDEO, viewModel.state().getValue().getCapture().getMode());

        viewModel.bindCaptureEvents(second);
        first.recordingCompleted("first.mp4");
        assertEquals(RecordingMode.VIDEO, viewModel.state().getValue().getCapture().getMode());

        second.recordingStarted(RecordingMode.SOS, "second.mp4");
        assertEquals(RecordingMode.SOS, viewModel.state().getValue().getCapture().getMode());

        viewModel.unbindCaptureEvents(second);
        second.recordingCompleted("second.mp4");
        assertEquals(RecordingMode.SOS, viewModel.state().getValue().getCapture().getMode());
        viewModel.onCleared();
    }

    @Test void activityRecreationPreservesOneRecordingAuthority() {
        MainViewModel viewModel = viewModel();
        CaptureEventUseCaseImpl events = new CaptureEventUseCaseImpl();
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

    @Test void completionDuringActivityRecreationIsReplayedOnRebind() {
        MainViewModel viewModel = viewModel();
        CaptureEventUseCaseImpl events = new CaptureEventUseCaseImpl();
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

    private static MainViewModel viewModel() {
        return new MainViewModel(
                new DcamConfig(),
                DeviceStatus.unknown(),
                DeviceStatus::unknown,
                relativePath -> Collections.emptyList());
    }
}
