package com.dvid.dcam.feature.storage.domain;

/** Free-space policy for capture and finalization. */
public final class CaptureStorageCapacityPolicy {
    public static final long MIN_CAPTURE_FREE_BYTES = 200L * 1024L * 1024L;

    public CaptureStorageCheck check(boolean mounted, boolean writable, long availableBytes) {
        if (!mounted) {
            return CaptureStorageCheck.rejected(
                    availableBytes, MIN_CAPTURE_FREE_BYTES, "Storage is unavailable");
        }
        if (!writable) {
            return CaptureStorageCheck.rejected(
                    availableBytes, MIN_CAPTURE_FREE_BYTES, "Storage is not writable");
        }
        if (availableBytes < MIN_CAPTURE_FREE_BYTES) {
            return CaptureStorageCheck.rejected(
                    availableBytes, MIN_CAPTURE_FREE_BYTES, "Not enough free storage");
        }
        return CaptureStorageCheck.ready(availableBytes, MIN_CAPTURE_FREE_BYTES);
    }

    /** Maximum bytes camera capture may consume while preserving the hard free-space floor. */
    public long recordingFileSizeLimit(long availableBytes) {
        return Math.max(1L, availableBytes - MIN_CAPTURE_FREE_BYTES);
    }
}
