package com.dvid.dcam.feature.capture.domain;

/** Immutable capture state safe to expose to presentation code. */
public final class CaptureState {
    private final RecordingMode mode;
    private final String currentFileName;
    private final Long startedAtMillis;
    private final boolean saving;
    private final Long interruptedAtMillis;

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
        this.mode = mode;
        this.currentFileName = currentFileName;
        this.startedAtMillis = startedAtMillis;
        this.saving = saving;
        this.interruptedAtMillis = interruptedAtMillis;
    }

    public RecordingMode getMode() { return mode; }
    public String getCurrentFileName() { return currentFileName; }
    public Long getStartedAtMillis() { return startedAtMillis; }
    public boolean isSaving() { return saving; }
    public Long getInterruptedAtMillis() { return interruptedAtMillis; }
    public boolean isInterrupted() { return interruptedAtMillis != null; }
}
