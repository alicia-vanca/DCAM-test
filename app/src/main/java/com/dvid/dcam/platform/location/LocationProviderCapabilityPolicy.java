package com.dvid.dcam.platform.location;

import com.dvid.dcam.feature.location.domain.LocationProviderAvailability;

/** Provider matrix policy independent from Android service lookups. */
final class LocationProviderCapabilityPolicy {
    private LocationProviderCapabilityPolicy() { }

    static LocationProviderAvailability availability(
            int androidApiLevel,
            boolean satelliteProviderPresent,
            boolean satelliteProviderEnabled,
            boolean googleFusedAvailable) {
        boolean satelliteAvailable = satelliteProviderPresent
                && (androidApiLevel >= 28 || satelliteProviderEnabled);
        return new LocationProviderAvailability(satelliteAvailable, googleFusedAvailable);
    }
}