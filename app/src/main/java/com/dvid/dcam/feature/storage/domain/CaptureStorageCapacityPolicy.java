package com.dvid.dcam.feature.storage.domain;

/** Free-space policy for capture and finalization. */
public final class CaptureStorageCapacityPolicy {
    public static final long MIN_CAPTURE_FREE_BYTES = 500L * 1024L * 1024L;
    public static final long MIN_NEW_CAPTURE_AVAILABLE_BYTES = MIN_CAPTURE_FREE_BYTES + 1L;
    public static final long RECORDING_START_ESTIMATE_SECONDS = 30L * 60L;

    public CaptureStorageCheck check(boolean mounted, boolean writable, long availableBytes) {
        return check(mounted, writable, availableBytes, MIN_NEW_CAPTURE_AVAILABLE_BYTES);
    }

    public CaptureStorageCheck check(
            boolean mounted, boolean writable, long availableBytes, long requiredBytes) {
        if (requiredBytes < MIN_CAPTURE_FREE_BYTES) {
            throw new IllegalArgumentException("requiredBytes must preserve capture safety margin");
        }
        if (!mounted) {
            return CaptureStorageCheck.rejected(
                    availableBytes, requiredBytes, "Storage is unavailable");
        }
        if (!writable) {
            return CaptureStorageCheck.rejected(
                    availableBytes, requiredBytes, "Storage is not writable");
        }
        if (availableBytes < requiredBytes) {
            return CaptureStorageCheck.lowCapacity(
                    availableBytes, requiredBytes, "Not enough free storage");
        }
        return CaptureStorageCheck.ready(availableBytes, requiredBytes);
    }

    public long estimatedRecordingBytes(long bitrateBitsPerSecond) {
        if (bitrateBitsPerSecond <= 0L) {
            throw new IllegalArgumentException("bitrateBitsPerSecond must be positive");
        }
        long estimatedBits = Math.multiplyExact(
                bitrateBitsPerSecond, RECORDING_START_ESTIMATE_SECONDS);
        return Math.addExact(estimatedBits, 7L) / 8L;
    }

    public long minimumRecordingStartFreeBytes(long bitrateBitsPerSecond) {
        return Math.addExact(
                estimatedRecordingBytes(bitrateBitsPerSecond), MIN_CAPTURE_FREE_BYTES);
    }

    /** Maximum bytes camera capture may consume while preserving the hard free-space floor. */
    public long recordingFileSizeLimit(long availableBytes) {
        return Math.max(1L, availableBytes - MIN_CAPTURE_FREE_BYTES);
    }
}
