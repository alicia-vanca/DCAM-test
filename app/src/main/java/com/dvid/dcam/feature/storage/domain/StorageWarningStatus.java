package com.dvid.dcam.feature.storage.domain;

/** Application-computed low-storage warning state. */
public final class StorageWarningStatus {
    private final boolean visible;
    private final long freeBytes;

    public StorageWarningStatus(boolean visible, long freeBytes) {
        this.visible = visible;
        this.freeBytes = Math.max(0L, freeBytes);
    }

    public boolean isVisible() { return visible; }
    public long getFreeBytes() { return freeBytes; }
}
