package com.dvid.dcam.feature.storage.domain;

/** Result of the synchronous storage precheck performed before visual capture starts. */
public final class CaptureStorageCheck {
    private final boolean ready;
    private final boolean preparing;
    private final boolean unavailable;
    private final long availableBytes;
    private final long requiredBytes;
    private final String reason;

    private CaptureStorageCheck(
            boolean ready, boolean preparing, boolean unavailable,
            long availableBytes, long requiredBytes, String reason) {
        this.ready = ready;
        this.preparing = preparing;
        this.unavailable = unavailable;
        this.availableBytes = availableBytes;
        this.requiredBytes = requiredBytes;
        this.reason = reason;
    }

    public static CaptureStorageCheck ready(long availableBytes, long requiredBytes) {
        return new CaptureStorageCheck(true, false, false, availableBytes, requiredBytes, "");
    }

    public static CaptureStorageCheck preparing(
            long availableBytes, long requiredBytes, String reason) {
        return new CaptureStorageCheck(false, true, false, availableBytes, requiredBytes, reason);
    }

    public static CaptureStorageCheck unavailable(
            long availableBytes, long requiredBytes, String reason) {
        return new CaptureStorageCheck(false, false, true, availableBytes, requiredBytes, reason);
    }

    public static CaptureStorageCheck rejected(long availableBytes, long requiredBytes, String reason) {
        return new CaptureStorageCheck(false, false, false, availableBytes, requiredBytes, reason);
    }

    public boolean isReady() { return ready; }
    public boolean isPreparing() { return preparing; }
    public boolean isUnavailable() { return unavailable; }
    public long getAvailableBytes() { return availableBytes; }
    public long getRequiredBytes() { return requiredBytes; }
    public String getReason() { return reason; }
}
