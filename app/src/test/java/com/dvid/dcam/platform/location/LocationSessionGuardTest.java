package com.dvid.dcam.platform.location;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LocationSessionGuardTest {
    @Test void restartRejectsPreviousSessionToken() {
        LocationSessionGuard guard = new LocationSessionGuard();
        long oldSession = guard.startNewSession();
        long currentSession = guard.startNewSession();

        assertFalse(guard.isCurrent(oldSession));
        assertTrue(guard.isCurrent(currentSession));
    }

    @Test void stopRejectsPendingCallbacks() {
        LocationSessionGuard guard = new LocationSessionGuard();
        long session = guard.startNewSession();

        guard.stopSession();

        assertFalse(guard.isCurrent(session));
        assertTrue(guard.currentSession() < 0L);
    }
}