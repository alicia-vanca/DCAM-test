package com.dvid.dcam.app.ui;

import android.view.View;

import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.databinding.ActivityMainBinding;
import com.dvid.dcam.databinding.ScreenCameraBinding;
import com.dvid.dcam.app.ui.MainUiState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

public final class RecordingStatusRenderer {
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ActivityMainBinding activityBinding;
    private final Supplier<ScreenCameraBinding> cameraScreen;
    private final Supplier<MainUiState> latestState;
    private final Supplier<MainScreen> renderedScreen;
    private boolean recordingStateInitialized;
    private boolean videoDurationActive;
    private boolean audioDurationActive;
    private long videoDurationSeconds;
    private long audioDurationSeconds;

    public RecordingStatusRenderer(
            ActivityMainBinding activityBinding,
            Supplier<ScreenCameraBinding> cameraScreen,
            Supplier<MainUiState> latestState,
            Supplier<MainScreen> renderedScreen) {
        this.activityBinding = activityBinding;
        this.cameraScreen = cameraScreen;
        this.latestState = latestState;
        this.renderedScreen = renderedScreen;
    }

    public void updateCameraClock() {
        MainUiState state = latestState.get();
        synchronizeRecordingState(state);
        renderCameraClock(state);
    }

    public void tickRecordingDurations() {
        MainUiState state = latestState.get();
        synchronizeRecordingState(state);
        if (videoDurationActive && !state.getCapture().isInterrupted()) {
            videoDurationSeconds++;
        }
        if (audioDurationActive) audioDurationSeconds++;
        renderCameraClock(state);
    }

    public void resyncRecordingDurations() {
        recordingStateInitialized = false;
        updateCameraClock();
    }

    public void updateFloatingRecordingStatus(MainUiState state) {
        synchronizeRecordingState(state);
        renderFloatingRecordingStatus(state);
    }

    private void renderCameraClock(MainUiState state) {
        renderFloatingRecordingStatus(state);
        ScreenCameraBinding screen = cameraScreen.get();
        if (screen == null) return;
        boolean videoRecording = isVideoRecording(state);
        boolean audioRecording = isAudioRecording(state);
        screen.currentTime.setText(CLOCK.format(LocalDateTime.now()));
        screen.videoRecordingStatus.setVisibility(videoRecording ? View.VISIBLE : View.GONE);
        screen.audioRecordingStatus.setVisibility(audioRecording ? View.VISIBLE : View.GONE);
        screen.recordingTimer.setText(videoRecording ? formatDuration(videoDurationSeconds) : "");
        screen.audioRecordingTimer.setText(
                audioRecording ? formatDuration(audioDurationSeconds) : "");
    }

    private void renderFloatingRecordingStatus(MainUiState state) {
        boolean videoRecording = isVideoRecording(state);
        boolean audioRecording = isAudioRecording(state);
        boolean recording = videoRecording || audioRecording;
        boolean showFloatingRecording = recording && renderedScreen.get() != MainScreen.CAMERA;
        activityBinding.customStatusBar.setVisibility(showFloatingRecording ? View.VISIBLE : View.GONE);
        activityBinding.customStatusVideoRow.setVisibility(videoRecording ? View.VISIBLE : View.GONE);
        activityBinding.customStatusAudioRow.setVisibility(audioRecording ? View.VISIBLE : View.GONE);
        activityBinding.customStatusVideoDuration.setText(
                videoRecording ? formatDuration(videoDurationSeconds) : "");
        activityBinding.customStatusAudioDuration.setText(
                audioRecording ? formatDuration(audioDurationSeconds) : "");
    }

    private void synchronizeRecordingState(MainUiState state) {
        boolean videoRecording = isVideoRecording(state);
        boolean audioRecording = isAudioRecording(state);
        if (!recordingStateInitialized) {
            videoDurationSeconds = videoRecording ? currentVideoDurationSeconds(state) : 0L;
            audioDurationSeconds = audioRecording ? currentAudioDurationSeconds(state) : 0L;
        } else {
            if (videoRecording && !videoDurationActive) videoDurationSeconds = 0L;
            else if (!videoRecording) videoDurationSeconds = 0L;
            if (audioRecording && !audioDurationActive) audioDurationSeconds = 0L;
            else if (!audioRecording) audioDurationSeconds = 0L;
        }
        if (videoRecording && state.getCapture().isInterrupted()) {
            videoDurationSeconds = currentVideoDurationSeconds(state);
        }
        videoDurationActive = videoRecording;
        audioDurationActive = audioRecording;
        recordingStateInitialized = true;
    }

    private boolean isAudioRecording(MainUiState state) {
        return state != null && state.getCapture().isAudioRecording();
    }

    private boolean isVideoRecording(MainUiState state) {
        return state != null
                && state.getCapture().isVideoRecording()
                && !state.getCapture().isSaving();
    }

    private static long currentVideoDurationSeconds(MainUiState state) {
        Long interruptedAtMillis = state.getCapture().getInterruptedAtMillis();
        return durationSecondsAt(state.getCapture().getStartedAtMillis(),
                interruptedAtMillis == null ? System.currentTimeMillis() : interruptedAtMillis);
    }

    private static long currentAudioDurationSeconds(MainUiState state) {
        return durationSecondsAt(
                state.getCapture().getAudioStartedAtMillis(), System.currentTimeMillis());
    }

    private static long durationSecondsAt(Long startedAtMillis, long nowMillis) {
        return startedAtMillis == null
                ? 0L : Math.max(0L, nowMillis - startedAtMillis) / 1_000L;
    }

    private static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}