package com.dvid.dcam.feature.location.domain;

/** Device provider capabilities used by location mode policy and presentation. */
public final class LocationProviderAvailability {
    private final boolean satelliteAvailable;
    private final boolean networkAvailable;

    public LocationProviderAvailability(boolean satelliteAvailable, boolean networkAvailable) {
        this.satelliteAvailable = satelliteAvailable;
        this.networkAvailable = networkAvailable;
    }

    public boolean isSatelliteAvailable() { return satelliteAvailable; }
    public boolean isNetworkAvailable() { return networkAvailable; }

    public boolean isModeAvailable(GpsMode mode) {
        if (mode == null) return false;
        switch (mode) {
            case SATELLITE: return satelliteAvailable;
            case NETWORK: return networkAvailable;
            case AUTOMATIC: return satelliteAvailable || networkAvailable;
            default: return false;
        }
    }
}