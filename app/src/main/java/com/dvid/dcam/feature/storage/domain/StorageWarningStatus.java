package com.dvid.dcam.feature.storage.domain;

/** Application-computed low-storage warning state. */
public final class StorageWarningStatus {
    private final boolean visible;
    private final boolean recordingBlocked;
    private final long freeBytes;

    public StorageWarningStatus(boolean visible, boolean recordingBlocked, long freeBytes) {
        this.visible = visible;
        this.recordingBlocked = recordingBlocked;
        this.freeBytes = Math.max(0L, freeBytes);
    }

    public boolean isVisible() { return visible; }
    public boolean isRecordingBlocked() { return recordingBlocked; }
    public long getFreeBytes() { return freeBytes; }
}
