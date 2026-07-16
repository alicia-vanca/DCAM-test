package com.dvid.dcam.feature.location.domain;

/** Product positioning modes; GMAP prefers Google Fused and falls back to Android system fused. */
public enum GpsMode {
    GPS,
    GPS_AGPS,
    GMAP
}
