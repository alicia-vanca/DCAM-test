package com.dvid.dcam.feature.storage.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class CaptureStorageCapacityPolicyTest {
    private final CaptureStorageCapacityPolicy policy = new CaptureStorageCapacityPolicy();

    @Test void requiresFreeSpaceAboveFiveHundredMegabytesForNewCapture() {
        assertEquals(500L * 1024L * 1024L,
                CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES);
        assertEquals(CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES + 1L,
                CaptureStorageCapacityPolicy.MIN_NEW_CAPTURE_AVAILABLE_BYTES);
    }

    @Test void rejectsUnavailableUnwritableAndLowCapacityStorage() {
        CaptureStorageCheck unavailable = policy.check(false, true, 0L);
        CaptureStorageCheck unwritable = policy.check(true, false, 0L);
        CaptureStorageCheck lowCapacity = policy.check(true, true,
                CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES);

        assertFalse(unavailable.isReady());
        assertFalse(unavailable.isLowCapacity());
        assertFalse(unwritable.isReady());
        assertFalse(unwritable.isLowCapacity());
        assertFalse(lowCapacity.isReady());
        assertTrue(lowCapacity.isLowCapacity());
        assertTrue(policy.check(true, true,
                CaptureStorageCapacityPolicy.MIN_NEW_CAPTURE_AVAILABLE_BYTES).isReady());
    }

    @Test void lowCapacityClassificationDoesNotDependOnReasonText() {
        assertTrue(CaptureStorageCheck.lowCapacity(
                1L, 2L, "localized reason").isLowCapacity());
        assertFalse(CaptureStorageCheck.rejected(
                1L, 2L, "Not enough free storage").isLowCapacity());
    }

    @Test void recordingStartRequiresThirtyMinutesAtActiveBitratePlusSafetyMargin() {
        long bitrateBitsPerSecond = 12_000_000L;
        long estimatedRecordingBytes = 2_700_000_000L;
        long requiredBytes = estimatedRecordingBytes
                + CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES;

        assertEquals(estimatedRecordingBytes,
                policy.estimatedRecordingBytes(bitrateBitsPerSecond));
        assertEquals(requiredBytes,
                policy.minimumRecordingStartFreeBytes(bitrateBitsPerSecond));
        assertFalse(policy.check(true, true, requiredBytes - 1L, requiredBytes).isReady());
        assertTrue(policy.check(true, true, requiredBytes, requiredBytes).isReady());
    }

    @Test void oneBitPerSecondStillReservesTheFullThirtyMinuteEstimate() {
        assertEquals(225L, policy.estimatedRecordingBytes(1L));
    }

    @Test void fileLimitUsesAllSpaceAboveHardFreeSpaceFloor() {
        long available = 600L * 1024L * 1024L;
        assertEquals(100L * 1024L * 1024L, policy.recordingFileSizeLimit(available));
    }
}
