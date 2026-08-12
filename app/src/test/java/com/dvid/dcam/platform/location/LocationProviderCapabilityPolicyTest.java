package com.dvid.dcam.platform.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.LocationProviderAvailability;
import org.junit.jupiter.api.Test;

final class LocationProviderCapabilityPolicyTest {
    @Test void fusedWithoutNetworkIsNotExposedAsNetworkSource() {
        LocationProviderAvailability availability =
                LocationProviderCapabilityPolicy.availability(true, false, true, false);

        assertTrue(availability.isModeAvailable(GpsMode.SATELLITE));
        assertTrue(availability.isModeAvailable(GpsMode.AUTOMATIC));
        assertFalse(availability.isModeAvailable(GpsMode.NETWORK));
        assertEquals(LocationProviderCapabilityPolicy.SystemNetworkBackend.NONE,
                LocationProviderCapabilityPolicy.systemNetworkBackend(true, false));
    }

    @Test void validSystemFusedAndDirectNetworkBackendsRemainAvailable() {
        assertEquals(LocationProviderCapabilityPolicy.SystemNetworkBackend.FUSED,
                LocationProviderCapabilityPolicy.systemNetworkBackend(true, true));
        assertEquals(LocationProviderCapabilityPolicy.SystemNetworkBackend.NETWORK,
                LocationProviderCapabilityPolicy.systemNetworkBackend(false, true));
        assertTrue(LocationProviderCapabilityPolicy.availability(
                false, false, true, true).isModeAvailable(GpsMode.NETWORK));
    }

    @Test void googleFusedDoesNotRequireFrameworkNetworkProvider() {
        assertTrue(LocationProviderCapabilityPolicy.availability(
                false, true, false, false).isModeAvailable(GpsMode.NETWORK));
    }
}