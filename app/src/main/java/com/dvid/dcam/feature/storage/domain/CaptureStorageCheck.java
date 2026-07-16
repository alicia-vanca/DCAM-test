package com.dvid.dcam.feature.storage.domain;

/** Result of the synchronous storage precheck performed before visual capture starts. */
public final class CaptureStorageCheck {
    private final boolean ready;
    private final long availableBytes;
    private final long requiredBytes;
    private final String reason;

    private CaptureStorageCheck(boolean ready, long availableBytes, long requiredBytes, String reason) {
        this.ready = ready;
        this.availableBytes = availableBytes;
        this.requiredBytes = requiredBytes;
        this.reason = reason;
    }

    public static CaptureStorageCheck ready(long availableBytes, long requiredBytes) {
        return new CaptureStorageCheck(true, availableBytes, requiredBytes, "");
    }

    public static CaptureStorageCheck rejected(long availableBytes, long requiredBytes, String reason) {
        return new CaptureStorageCheck(false, availableBytes, requiredBytes, reason);
    }

    public boolean isReady() { return ready; }
    public long getAvailableBytes() { return availableBytes; }
    public long getRequiredBytes() { return requiredBytes; }
    public String getReason() { return reason; }
}
