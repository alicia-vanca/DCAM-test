package com.dvid.dcam.feature.storage.domain;

import java.util.Locale;

/** Physical media partition preference, independent from publication policy. */
public enum MediaPartitionLocation {
    INTERNAL,
    EXTERNAL,
    AUTO;

    public static MediaPartitionLocation from(String value) {
        if (value == null || value.isBlank()) return AUTO;
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        for (MediaPartitionLocation location : values()) {
            if (location.name().equals(normalized)) return location;
        }
        return AUTO;
    }
}
