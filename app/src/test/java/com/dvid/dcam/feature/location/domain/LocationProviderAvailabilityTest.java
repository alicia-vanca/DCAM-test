package com.dvid.dcam.feature.location.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LocationProviderAvailabilityTest {
    @Test void explicitModesFollowTheirOwnProviderAvailability() {
        LocationProviderAvailability satelliteOnly =
                new LocationProviderAvailability(true, false);
        LocationProviderAvailability fusedOnly =
                new LocationProviderAvailability(false, true);

        assertTrue(satelliteOnly.isModeAvailable(GpsMode.SATELLITE));
        assertFalse(satelliteOnly.isModeAvailable(GpsMode.FUSED));
        assertFalse(fusedOnly.isModeAvailable(GpsMode.SATELLITE));
        assertTrue(fusedOnly.isModeAvailable(GpsMode.FUSED));
        assertTrue(satelliteOnly.isAnyAvailable());
        assertTrue(fusedOnly.isAnyAvailable());
    }

    @Test void everyModeIsUnavailableWithoutProviders() {
        LocationProviderAvailability unavailable =
                new LocationProviderAvailability(false, false);

        assertFalse(unavailable.isModeAvailable(GpsMode.FUSED));
        assertFalse(unavailable.isModeAvailable(GpsMode.SATELLITE));
        assertFalse(unavailable.isAnyAvailable());
    }
}