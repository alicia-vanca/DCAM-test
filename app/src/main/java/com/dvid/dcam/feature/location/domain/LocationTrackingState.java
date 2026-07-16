package com.dvid.dcam.feature.location.domain;

/** Result/state of the location provider registration and first-fix lifecycle. */
public enum LocationTrackingState {
    STOPPED,
    PERMISSION_REQUIRED,
    LOCATION_DISABLED,
    LOCATION_UNAVAILABLE,
    WAITING_FOR_FIX,
    AVAILABLE,
    NO_PROVIDER,
    ERROR
}
