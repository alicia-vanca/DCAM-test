package com.dvid.dcam.feature.location.domain;

/** Device provider capabilities used by location mode policy and presentation. */
public final class LocationProviderAvailability {
    private final boolean satelliteAvailable;
    private final boolean fusedAvailable;

    public LocationProviderAvailability(boolean satelliteAvailable, boolean fusedAvailable) {
        this.satelliteAvailable = satelliteAvailable;
        this.fusedAvailable = fusedAvailable;
    }

    public boolean isSatelliteAvailable() { return satelliteAvailable; }
    public boolean isFusedAvailable() { return fusedAvailable; }
    public boolean isAnyAvailable() { return satelliteAvailable || fusedAvailable; }

    public boolean isModeAvailable(GpsMode mode) {
        if (mode == null) return false;
        switch (mode) {
            case FUSED: return fusedAvailable;
            case SATELLITE: return satelliteAvailable;
            default: return false;
        }
    }
}