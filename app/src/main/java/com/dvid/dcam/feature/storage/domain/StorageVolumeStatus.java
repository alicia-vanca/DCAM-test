package com.dvid.dcam.feature.storage.domain;

/** Capacity snapshot for one selectable storage volume. */
public final class StorageVolumeStatus {
    private final MediaPartitionLocation mode;
    private final boolean available;
    private final long totalBytes;
    private final long freeBytes;

    public StorageVolumeStatus(MediaPartitionLocation mode, boolean available, long totalBytes, long freeBytes) {
        this.mode = mode;
        this.available = available;
        this.totalBytes = Math.max(0L, totalBytes);
        this.freeBytes = Math.max(0L, freeBytes);
    }

    public MediaPartitionLocation getMode() { return mode; }
    public boolean isAvailable() { return available; }
    public long getTotalBytes() { return totalBytes; }
    public long getFreeBytes() { return freeBytes; }
    public long getUsedBytes() { return Math.max(0L, totalBytes - freeBytes); }
}
