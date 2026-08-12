package com.dvid.dcam.platform.device.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CameraCapabilityAuthorityTest {
    @Test void transientFailurePreservesLastValidSnapshot() {
        CameraCapabilityAuthority authority = new CameraCapabilityAuthority();
        Snapshot snapshot = Snapshot.current("hardware", List.of(), List.of());

        authority.current(snapshot);
        authority.loading();
        authority.unavailable("transient_scan_failure");

        assertEquals(CameraCapabilityAuthority.State.CURRENT, authority.state());
        assertSame(snapshot, authority.snapshot().orElseThrow());
        assertEquals("transient_scan_failure", authority.unavailableReason());
    }

    @Test void unavailableWithoutPriorSnapshotIsExplicit() {
        CameraCapabilityAuthority authority = new CameraCapabilityAuthority();

        authority.unavailable("corrupt_snapshot_and_scan_failed");

        assertEquals(CameraCapabilityAuthority.State.UNAVAILABLE, authority.state());
        assertEquals("corrupt_snapshot_and_scan_failed", authority.unavailableReason());
    }
}
