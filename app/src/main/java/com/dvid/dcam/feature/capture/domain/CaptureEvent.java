package com.dvid.dcam.feature.capture.domain;

/** Immutable application event emitted after platform capture callbacks are translated. */
public final class CaptureEvent {
    public enum Type {
        RECORDING_STARTING,
        RECORDING_START_CANCELLED,
        RECORDING_STARTED,
        RECORDING_INTERRUPTED,
        RECORDING_RESUMED,
        RECORDING_STOPPING,
        RECORDING_COMPLETED,
        RECORDING_STOPPED_FOR_STORAGE,
        AUDIO_RECORDING_STARTED,
        AUDIO_RECORDING_STOPPING,
        AUDIO_RECORDING_STOPPED,
        PHOTO_SAVING,
        PHOTO_SAVED,
        PHOTO_FAILED,
        ERROR
    }

    private final Type type;
    private final RecordingMode mode;
    private final String fileName;
    private final String operation;
    private final String message;
    private final Long startedAtMillis;
    private final boolean replay;

    private CaptureEvent(
            Type type, RecordingMode mode, String fileName, String operation, String message) {
        this(type, mode, fileName, operation, message, null, false);
    }

    private CaptureEvent(
            Type type, RecordingMode mode, String fileName, String operation, String message,
            Long startedAtMillis) {
        this(type, mode, fileName, operation, message, startedAtMillis, false);
    }

    private CaptureEvent(
            Type type, RecordingMode mode, String fileName, String operation, String message,
            Long startedAtMillis, boolean replay) {
        this.type = type;
        this.mode = mode;
        this.fileName = fileName;
        this.operation = operation;
        this.message = message;
        this.startedAtMillis = startedAtMillis;
        this.replay = replay;
    }

    public static CaptureEvent recordingStarted(RecordingMode mode, String fileName) {
        return new CaptureEvent(Type.RECORDING_STARTED, mode, fileName, null, null);
    }

    public static CaptureEvent recordingInterrupted(RecordingMode mode, String message) {
        return new CaptureEvent(Type.RECORDING_INTERRUPTED, mode, null, null, message);
    }

    public static CaptureEvent recordingResumed(RecordingMode mode) {
        return new CaptureEvent(Type.RECORDING_RESUMED, mode, null, null, null);
    }
    public static CaptureEvent recordingStarting(RecordingMode mode) {
        return new CaptureEvent(Type.RECORDING_STARTING, mode, null, null, null);
    }

    public static CaptureEvent recordingStartCancelled() {
        return new CaptureEvent(
                Type.RECORDING_START_CANCELLED, RecordingMode.IDLE, null, null, null);
    }

    public static CaptureEvent recordingStopping(RecordingMode mode) {
        return new CaptureEvent(Type.RECORDING_STOPPING, mode, null, null, null);
    }

    public static CaptureEvent recordingCompleted(String fileName) {
        return new CaptureEvent(Type.RECORDING_COMPLETED, RecordingMode.IDLE, fileName, null, null);
    }
    public static CaptureEvent recordingStoppedForStorage(String fileName) {
        return new CaptureEvent(Type.RECORDING_STOPPED_FOR_STORAGE, RecordingMode.IDLE, fileName, null, null);
    }

    public static CaptureEvent audioRecordingStarted(
            String fileName, long startedAtMillis) {
        return new CaptureEvent(Type.AUDIO_RECORDING_STARTED, null, fileName, null, null,
                startedAtMillis);
    }

    public static CaptureEvent audioRecordingStopping() {
        return new CaptureEvent(Type.AUDIO_RECORDING_STOPPING, null, null, null, null);
    }

    public static CaptureEvent audioRecordingStopped(String fileName) {
        return new CaptureEvent(Type.AUDIO_RECORDING_STOPPED, null, fileName, null, null);
    }

    public static CaptureEvent photoSaving() {
        return new CaptureEvent(Type.PHOTO_SAVING, null, null, null, null);
    }

    public static CaptureEvent photoSaved(String fileName) {
        return new CaptureEvent(Type.PHOTO_SAVED, null, fileName, null, null);
    }

    public static CaptureEvent photoFailed(String operation, String message) {
        return new CaptureEvent(Type.PHOTO_FAILED, null, null, operation, message);
    }

    public static CaptureEvent error(String operation, String message) {
        return new CaptureEvent(Type.ERROR, RecordingMode.IDLE, null, operation, message);
    }

    public Type getType() { return type; }
    public RecordingMode getMode() { return mode; }
    public String getFileName() { return fileName; }
    public String getOperation() { return operation; }
    public String getMessage() { return message; }
    public Long getStartedAtMillis() { return startedAtMillis; }
    public boolean isReplay() { return replay; }

    public CaptureEvent asReplay() {
        return replay ? this : new CaptureEvent(
                type, mode, fileName, operation, message, startedAtMillis, true);
    }
}
