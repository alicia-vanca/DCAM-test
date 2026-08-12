package com.dvid.dcam.platform.location;

import com.dvid.dcam.feature.location.domain.LocationProviderAvailability;

/** Provider matrix policy independent from Android service lookups. */
final class LocationProviderCapabilityPolicy {
    enum SystemNetworkBackend {
        FUSED,
        NETWORK,
        NONE
    }

    private LocationProviderCapabilityPolicy() { }

    static LocationProviderAvailability availability(
            boolean satelliteProviderPresent,
            boolean googleFusedAvailable,
            boolean systemFusedProviderPresent,
            boolean networkProviderPresent) {
        SystemNetworkBackend backend = systemNetworkBackend(
                systemFusedProviderPresent, networkProviderPresent);
        return new LocationProviderAvailability(satelliteProviderPresent,
                googleFusedAvailable || backend != SystemNetworkBackend.NONE);
    }

    static SystemNetworkBackend systemNetworkBackend(
            boolean systemFusedAvailable, boolean networkAvailable) {
        if (!networkAvailable) return SystemNetworkBackend.NONE;
        return systemFusedAvailable
                ? SystemNetworkBackend.FUSED : SystemNetworkBackend.NETWORK;
    }
}