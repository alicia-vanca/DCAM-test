package com.dvid.dcam.feature.storage.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

final class CaptureStorageCapacityPolicyTest {
    private final CaptureStorageCapacityPolicy policy = new CaptureStorageCapacityPolicy();

    @Test void requiresTwoHundredMegabytesForAnyNewCapture() {
        assertEquals(200L * 1024L * 1024L,
                CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES);
    }

    @Test void rejectsUnavailableUnwritableAndLowCapacityStorage() {
        assertFalse(policy.check(false, true, Long.MAX_VALUE).isReady());
        assertFalse(policy.check(true, false, Long.MAX_VALUE).isReady());
        assertFalse(policy.check(true, true,
                CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES - 1L).isReady());
        assertTrue(policy.check(true, true,
                CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES).isReady());
    }

    @Test void fileLimitPreservesHardFreeSpaceFloor() {
        long available = 600L * 1024L * 1024L;
        assertEquals(400L * 1024L * 1024L, policy.recordingFileSizeLimit(available));
    }
}
