package com.dvid.dcam.feature.location.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LocationProviderAvailabilityTest {
    @Test void automaticIsAvailableWhenEitherProviderIsAvailable() {
        LocationProviderAvailability satelliteOnly =
                new LocationProviderAvailability(true, false);
        LocationProviderAvailability networkOnly =
                new LocationProviderAvailability(false, true);

        assertTrue(satelliteOnly.isModeAvailable(GpsMode.AUTOMATIC));
        assertTrue(networkOnly.isModeAvailable(GpsMode.AUTOMATIC));
        assertTrue(satelliteOnly.isModeAvailable(GpsMode.SATELLITE));
        assertFalse(satelliteOnly.isModeAvailable(GpsMode.NETWORK));
        assertFalse(networkOnly.isModeAvailable(GpsMode.SATELLITE));
        assertTrue(networkOnly.isModeAvailable(GpsMode.NETWORK));
    }

    @Test void everyModeIsUnavailableWithoutProviders() {
        LocationProviderAvailability unavailable =
                new LocationProviderAvailability(false, false);

        assertFalse(unavailable.isModeAvailable(GpsMode.AUTOMATIC));
        assertFalse(unavailable.isModeAvailable(GpsMode.SATELLITE));
        assertFalse(unavailable.isModeAvailable(GpsMode.NETWORK));
    }
}