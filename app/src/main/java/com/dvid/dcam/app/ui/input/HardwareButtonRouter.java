package com.dvid.dcam.app.ui.input;

import com.dvid.dcam.core.featuregate.application.FeatureGates;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.HardwareButtonBinding;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.core.input.domain.PhysicalButtonType;
import com.dvid.dcam.feature.auth.application.usecase.OperatorSessionUseCase;
import com.dvid.dcam.feature.capture.application.usecase.AudioRecordingUseCase;
import com.dvid.dcam.feature.capture.application.usecase.PhotoCaptureUseCase;
import com.dvid.dcam.feature.capture.application.usecase.RecordingCommands;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.Objects;

/** Applies Java button behavior policy to model-specific physical bindings. */
public final class HardwareButtonRouter {
    public static final long SOS_HOLD_MS = 2000L;
    private final PhotoCaptureUseCase photos;
    private final RecordingCommands videos;
    private final AudioRecordingUseCase audio;
    private final FeatureGates featureGates;
    private final OperatorSessionUseCase operatorSession;
    private final Runnable currentLocationRequest;
    private final Runnable commandHaptic;
    private volatile HardwareButtonLayout layout;
    private HardwareButtonBinding heldButton;
    private HardwareButtonBinding activeRecordSwitch;
    private long holdStartedAtMs;
    private boolean holdHandled;

    public HardwareButtonRouter(
            PhotoCaptureUseCase photos,
            RecordingCommands videos,
            AudioRecordingUseCase audio,
            FeatureGates featureGates,
            OperatorSessionUseCase operatorSession,
            HardwareButtonLayout layout) {
        this(photos, videos, audio, featureGates, operatorSession, layout, () -> {}, () -> {});
    }

    public HardwareButtonRouter(
            PhotoCaptureUseCase photos,
            RecordingCommands videos,
            AudioRecordingUseCase audio,
            FeatureGates featureGates,
            OperatorSessionUseCase operatorSession,
            HardwareButtonLayout layout,
            Runnable currentLocationRequest) {
        this(photos, videos, audio, featureGates, operatorSession, layout,
                currentLocationRequest, () -> {});
    }

    public HardwareButtonRouter(
            PhotoCaptureUseCase photos,
            RecordingCommands videos,
            AudioRecordingUseCase audio,
            FeatureGates featureGates,
            OperatorSessionUseCase operatorSession,
            HardwareButtonLayout layout,
            Runnable currentLocationRequest,
            Runnable commandHaptic) {
        this.photos = Objects.requireNonNull(photos, "photos");
        this.videos = Objects.requireNonNull(videos, "videos");
        this.audio = Objects.requireNonNull(audio, "audio");
        this.featureGates = Objects.requireNonNull(featureGates, "featureGates");
        this.operatorSession = Objects.requireNonNull(operatorSession, "operatorSession");
        this.currentLocationRequest = currentLocationRequest == null ? () -> {} : currentLocationRequest;
        this.commandHaptic = commandHaptic == null ? () -> {} : commandHaptic;
        if (layout == null) throw new IllegalArgumentException("layout is required");
        this.layout = layout;
    }

    public void updateLayout(HardwareButtonLayout layout) {
        if (layout == null) throw new IllegalArgumentException("layout is required");
        this.layout = layout;
        clearHoldState();
        activeRecordSwitch = null;
    }

    public boolean onKeyDown(int buttonKeyCode, int repeatCount, long eventTimeMs) {
        return handleButtonDown(
                layout.findByButtonKeyCode(buttonKeyCode), repeatCount, eventTimeMs);
    }

    public boolean onFirmwareBroadcastDown(
            String action, int repeatCount, long eventTimeMs) {
        return handleButtonDown(
                layout.findByFirmwareBroadcastDownAction(action), repeatCount, eventTimeMs);
    }

    private boolean handleButtonDown(
            HardwareButtonBinding binding, int repeatCount, long eventTimeMs) {
        if (binding == null) return false;
        switch (binding.role()) {
            case RECORD:
                return handleRecordDown(binding, repeatCount);
            case IMPORTANT_RECORDING:
                if (repeatCount == 0) {
                    handleImportantRecording();
                }
                return true;
            case PHOTO_CAPTURE:
                if (repeatCount == 0) {
                    commandHaptic.run();
                    runIfEnabled(FeatureGate.IMAGE_CAPTURE, () -> {
                        currentLocationRequest.run();
                        photos.takePhoto();
                    });
                }
                return true;
            case AUDIO_CAPTURE:
                if (repeatCount == 0) {
                    commandHaptic.run();
                    if (audio.isAudioRecording()) toggleAudioRecording();
                    else runIfEnabled(FeatureGate.AUDIO_CAPTURE, this::toggleAudioRecording);
                }
                return true;
            case SOS:
                return handleSosDown(binding, repeatCount, eventTimeMs);
            case PTT:
                return false;
            default:
                throw new IllegalArgumentException("Unsupported button role " + binding.role());
        }
    }

    public boolean onKeyUp(int buttonKeyCode) {
        return onKeyUp(buttonKeyCode, false);
    }

    public boolean onKeyUp(int buttonKeyCode, boolean canceled) {
        return handleButtonUp(layout.findByButtonKeyCode(buttonKeyCode), canceled);
    }

    public boolean onFirmwareBroadcastUp(String action, boolean canceled) {
        return handleButtonUp(layout.findByFirmwareBroadcastUpAction(action), canceled);
    }

    private boolean handleButtonUp(HardwareButtonBinding binding, boolean canceled) {
        if (binding == null) return false;
        if (binding.role() == ButtonRole.SOS) {
            if (heldButton == binding) clearHoldState();
            return true;
        }
        if (binding.role() == ButtonRole.RECORD
                && binding.type() == PhysicalButtonType.SWITCH) {
            if (canceled) return true;
            commandHaptic.run();
            if (activeRecordSwitch == binding) {
                activeRecordSwitch = null;
                stopVideoRecording();
            }
            return true;
        }
        return binding.role() != ButtonRole.PTT;
    }

    public java.util.List<String> firmwareBroadcastActions() {
        return layout.firmwareBroadcastActions();
    }

    public void clearTransientState() {
        clearHoldState();
        activeRecordSwitch = null;
    }

    public void clearFocusTransientState() {
        clearHoldState();
    }

    public boolean isSosButton(int buttonKeyCode) {
        HardwareButtonBinding binding = layout.findByButtonKeyCode(buttonKeyCode);
        return binding != null && binding.role() == ButtonRole.SOS;
    }

    public boolean isSosFirmwareBroadcastDownAction(String action) {
        HardwareButtonBinding binding = layout.findByFirmwareBroadcastDownAction(action);
        return binding != null && binding.role() == ButtonRole.SOS;
    }

    public boolean isSosFirmwareBroadcastUpAction(String action) {
        HardwareButtonBinding binding = layout.findByFirmwareBroadcastUpAction(action);
        return binding != null && binding.role() == ButtonRole.SOS;
    }

    public boolean onSosHoldThreshold(int buttonKeyCode) {
        return onSosHoldThreshold(layout.findByButtonKeyCode(buttonKeyCode));
    }

    public boolean onSosHoldThresholdForFirmwareBroadcast(String action) {
        return onSosHoldThreshold(layout.findByFirmwareBroadcastDownAction(action));
    }

    private boolean onSosHoldThreshold(HardwareButtonBinding binding) {
        if (binding != null && !holdHandled && heldButton == binding) {
            commandHaptic.run();
            holdHandled = runIfEnabledOrStop(FeatureGate.VIDEO_CAPTURE, this::startImpRecording);
            return holdHandled;
        }
        return false;
    }

    private boolean handleRecordDown(HardwareButtonBinding binding, int repeatCount) {
        if (binding.type() == PhysicalButtonType.SWITCH) {
            commandHaptic.run();
            if (activeRecordSwitch == binding) return true;
            activeRecordSwitch = binding;
            runIfEnabled(FeatureGate.VIDEO_CAPTURE, this::startVideoRecording);
        } else if (repeatCount == 0) {
            commandHaptic.run();
            runIfEnabledOrStop(FeatureGate.VIDEO_CAPTURE, this::startVideoRecording);
        }
        return true;
    }

    private void handleImportantRecording() {
        commandHaptic.run();
        runIfEnabledOrStop(FeatureGate.VIDEO_CAPTURE, this::startImpRecording);
    }

    private boolean handleSosDown(
            HardwareButtonBinding binding, int repeatCount, long eventTimeMs) {
        if (repeatCount == 0) {
            heldButton = binding;
            holdStartedAtMs = eventTimeMs;
            holdHandled = false;
        }
        if (!holdHandled && heldButton == binding
                && eventTimeMs - holdStartedAtMs >= SOS_HOLD_MS) {
            commandHaptic.run();
            holdHandled = runIfEnabledOrStop(FeatureGate.VIDEO_CAPTURE, this::startImpRecording);
        }
        return true;
    }

    private void clearHoldState() {
        heldButton = null;
        holdStartedAtMs = 0L;
        holdHandled = false;
    }

    private void startVideoRecording() {
        currentLocationRequest.run();
        videos.startVideo();
    }

    private void startImpRecording() {
        currentLocationRequest.run();
        videos.startImp();
    }

    private void toggleAudioRecording() {
        boolean starting = !audio.isAudioRecording();
        if (!audio.toggleAudioAsync()) return;
        if (starting) currentLocationRequest.run();
    }

    private boolean runIfEnabledOrStop(FeatureGate feature, Runnable startAction) {
        if (videos.isRecordingStartPending()) return true;
        if (videos.currentMode() != RecordingMode.IDLE) {
            stopVideoRecording();
            return true;
        }
        return runIfEnabled(feature, startAction);
    }

    private void stopVideoRecording() {
        videos.stopRecording();
    }

    private boolean runIfEnabled(FeatureGate feature, Runnable action) {
        if (!operatorSession.hasActiveSession()) return false;
        return featureGates.runIfEnabled(feature, action);
    }
}
