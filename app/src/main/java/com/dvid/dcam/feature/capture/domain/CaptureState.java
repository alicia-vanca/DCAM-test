package com.dvid.dcam.feature.capture.domain;

/** Immutable capture state safe to expose to presentation code. */
public final class CaptureState {
    private final RecordingMode mode;
    private final String currentFileName;
    private final Long startedAtMillis;
    private final boolean saving;
    private final boolean photoSaving;
    private final Long interruptedAtMillis;
    private final boolean audioRecording;
    private final Long audioStartedAtMillis;

    public CaptureState() {
        this(RecordingMode.IDLE, null, null, false, null);
    }

    public CaptureState(RecordingMode mode, String currentFileName, Long startedAtMillis) {
        this(mode, currentFileName, startedAtMillis, false, null);
    }

    public CaptureState(
            RecordingMode mode, String currentFileName, Long startedAtMillis, boolean saving) {
        this(mode, currentFileName, startedAtMillis, saving, null);
    }

    public CaptureState(
            RecordingMode mode, String currentFileName, Long startedAtMillis, boolean saving,
            Long interruptedAtMillis) {
        this(mode, currentFileName, startedAtMillis, saving, false, interruptedAtMillis, false, null);
    }

    private CaptureState(
            RecordingMode mode, String currentFileName, Long startedAtMillis, boolean saving,
            boolean photoSaving, Long interruptedAtMillis, boolean audioRecording,
            Long audioStartedAtMillis) {
        this.mode = mode;
        this.currentFileName = currentFileName;
        this.startedAtMillis = startedAtMillis;
        this.saving = saving;
        this.photoSaving = photoSaving;
        this.interruptedAtMillis = interruptedAtMillis;
        this.audioRecording = audioRecording;
        this.audioStartedAtMillis = audioStartedAtMillis;
    }

    public RecordingMode getMode() { return mode; }
    public boolean isVideoRecording() { return mode != RecordingMode.IDLE; }
    public String getCurrentFileName() { return currentFileName; }
    public Long getStartedAtMillis() { return startedAtMillis; }
    public boolean isSaving() { return saving; }
    public boolean isPhotoSaving() { return photoSaving; }
    public Long getInterruptedAtMillis() { return interruptedAtMillis; }
    public boolean isInterrupted() { return interruptedAtMillis != null; }
    public boolean isAudioRecording() { return audioRecording; }
    public Long getAudioStartedAtMillis() { return audioStartedAtMillis; }

    public CaptureState withVideo(
            RecordingMode mode, String fileName, Long startedAtMillis, boolean saving,
            Long interruptedAtMillis) {
        return new CaptureState(mode, fileName, startedAtMillis, saving, photoSaving,
                interruptedAtMillis, audioRecording, audioStartedAtMillis);
    }

    public CaptureState withoutVideo() {
        return withVideo(RecordingMode.IDLE, null, null, false, null);
    }

    public CaptureState withPhotoSaving(boolean saving) {
        return new CaptureState(mode, currentFileName, startedAtMillis, this.saving, saving,
                interruptedAtMillis, audioRecording, audioStartedAtMillis);
    }

    public CaptureState withAudio(boolean recording, Long startedAtMillis) {
        return new CaptureState(mode, currentFileName, this.startedAtMillis, saving, photoSaving,
                interruptedAtMillis, recording, recording ? startedAtMillis : null);
    }
}
