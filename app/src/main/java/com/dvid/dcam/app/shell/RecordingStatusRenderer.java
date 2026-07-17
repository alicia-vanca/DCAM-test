package com.dvid.dcam.app.shell;

import android.view.View;

import com.dvid.dcam.app.navigation.MainScreen;
import com.dvid.dcam.databinding.ActivityMainBinding;
import com.dvid.dcam.databinding.ScreenCameraBinding;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.app.presentation.MainUiState;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class RecordingStatusRenderer {
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ActivityMainBinding activityBinding;
    private final Supplier<ScreenCameraBinding> cameraScreen;
    private final Supplier<MainUiState> latestState;
    private final BooleanSupplier audioRecording;
    private final LongSupplier audioStartedAtMillis;
    private final Supplier<MainScreen> renderedScreen;

    public RecordingStatusRenderer(
            ActivityMainBinding activityBinding,
            Supplier<ScreenCameraBinding> cameraScreen,
            Supplier<MainUiState> latestState,
            BooleanSupplier audioRecording,
            LongSupplier audioStartedAtMillis,
            Supplier<MainScreen> renderedScreen) {
        this.activityBinding = activityBinding;
        this.cameraScreen = cameraScreen;
        this.latestState = latestState;
        this.audioRecording = audioRecording;
        this.audioStartedAtMillis = audioStartedAtMillis;
        this.renderedScreen = renderedScreen;
    }

    public void updateCameraClock() {
        MainUiState state = latestState.get();
        updateFloatingRecordingStatus(state);
        ScreenCameraBinding screen = cameraScreen.get();
        if (screen == null) return;
        boolean videoRecording = isVideoRecording(state);
        screen.currentTime.setText(CLOCK.format(LocalDateTime.now()));
        screen.videoRecordingStatus.setVisibility(videoRecording ? View.VISIBLE : View.GONE);
        screen.audioRecordingStatus.setVisibility(audioRecording.getAsBoolean() ? View.VISIBLE : View.GONE);
        screen.recordingTimer.setText(videoRecording ? videoDurationText(state) : "");
        screen.audioRecordingTimer.setText(audioRecording.getAsBoolean() ? audioDurationText() : "");
    }

    public void updateFloatingRecordingStatus(MainUiState state) {
        boolean videoRecording = isVideoRecording(state);
        boolean audioIsRecording = audioRecording.getAsBoolean();
        boolean recording = videoRecording || audioIsRecording;
        boolean showFloatingRecording = recording && renderedScreen.get() != MainScreen.CAMERA;
        activityBinding.customStatusBar.setVisibility(showFloatingRecording ? View.VISIBLE : View.GONE);
        activityBinding.customStatusVideoRow.setVisibility(videoRecording ? View.VISIBLE : View.GONE);
        activityBinding.customStatusAudioRow.setVisibility(audioIsRecording ? View.VISIBLE : View.GONE);
        activityBinding.customStatusVideoDuration.setText(videoRecording ? videoDurationText(state) : "");
        activityBinding.customStatusAudioDuration.setText(audioIsRecording ? audioDurationText() : "");
    }

    private boolean isVideoRecording(MainUiState state) {
        return state != null
                && state.getCapture().getMode() != RecordingMode.IDLE
                && !state.getCapture().isSaving();
    }

    private String videoDurationText(MainUiState state) {
        if (state == null) return "";
        Long interruptedAt = state.getCapture().getInterruptedAtMillis();
        return formatDurationAt(state.getCapture().getStartedAtMillis(),
                interruptedAt == null ? System.currentTimeMillis() : interruptedAt);
    }

    private String audioDurationText() {
        Long startedAt = audioStartedAtMillis.getAsLong() < 0L
                ? null : audioStartedAtMillis.getAsLong();
        return formatDurationAt(startedAt, System.currentTimeMillis());
    }

    private static String formatDurationAt(Long startedAtMillis, long nowMillis) {
        if (startedAtMillis == null) return "";
        long elapsedMs = Math.max(0L, nowMillis - startedAtMillis);
        long totalSeconds = elapsedMs / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}