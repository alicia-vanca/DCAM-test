package com.dvid.dcam.feature.storage.domain;

import java.util.Locale;

/** Media publication policy: app-private data or public DCIM. */
public enum StorageMode {
    APP_DATA,
    PUBLIC_DCIM;

    public static StorageMode from(String value) {
        if (value == null || value.isBlank()) return APP_DATA;
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        for (StorageMode mode : values()) {
            if (mode.name().equals(normalized)) return mode;
        }
        return APP_DATA;
    }
}
