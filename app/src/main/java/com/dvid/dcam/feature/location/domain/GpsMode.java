package com.dvid.dcam.feature.location.domain;

/** User-facing location source policy; platform adapters choose concrete Android providers. */
public enum GpsMode {
    AUTOMATIC,
    SATELLITE,
    NETWORK
}