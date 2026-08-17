package com.dvid.dcam.platform.location;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.LocationProviderAvailability;
import org.junit.jupiter.api.Test;

final class LocationProviderCapabilityPolicyTest {
    @Test void googleFusedRequiresGooglePlayServices() {
        LocationProviderAvailability withoutGms =
                LocationProviderCapabilityPolicy.availability(28, true, false, false);

        assertTrue(withoutGms.isModeAvailable(GpsMode.SATELLITE));
        assertFalse(withoutGms.isModeAvailable(GpsMode.FUSED));
    }

    @Test void googlePlayServicesExposeFusedWithoutSatelliteProvider() {
        LocationProviderAvailability withGms =
                LocationProviderCapabilityPolicy.availability(28, false, false, true);

        assertFalse(withGms.isModeAvailable(GpsMode.SATELLITE));
        assertTrue(withGms.isModeAvailable(GpsMode.FUSED));
    }

    @Test void api26And27RequireEnabledGpsProviderForSatellite() {
        assertFalse(LocationProviderCapabilityPolicy.availability(
                26, true, false, false).isModeAvailable(GpsMode.SATELLITE));
        assertFalse(LocationProviderCapabilityPolicy.availability(
                27, true, false, false).isModeAvailable(GpsMode.SATELLITE));
        assertTrue(LocationProviderCapabilityPolicy.availability(
                26, true, true, false).isModeAvailable(GpsMode.SATELLITE));
        assertTrue(LocationProviderCapabilityPolicy.availability(
                27, true, true, false).isModeAvailable(GpsMode.SATELLITE));
    }

    @Test void api28UsesGpsProviderPresenceForSatellite() {
        assertTrue(LocationProviderCapabilityPolicy.availability(
                28, true, false, false).isModeAvailable(GpsMode.SATELLITE));
        assertFalse(LocationProviderCapabilityPolicy.availability(
                28, false, true, false).isModeAvailable(GpsMode.SATELLITE));
    }
}