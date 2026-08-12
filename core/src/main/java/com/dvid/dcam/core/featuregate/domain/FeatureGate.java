package com.dvid.dcam.core.featuregate.domain;

/** Opt-in developer switches for product-facing features or surfaces, never shared infrastructure. */
public enum FeatureGate {
    IMAGE_CAPTURE(true),
    VIDEO_CAPTURE(true),
    AUDIO_CAPTURE(true),
    MEDIA_ENCRYPTION(false),
    VIDEO_MD5(true, VIDEO_CAPTURE),
    MEDIA_BROWSER(true),
    AUTHENTICATION(false),
    STORAGE_SETTINGS(true),
    DEVICE_SETTINGS(true),
    GPS(true),
    SECURITY_SETTINGS(false, "SECURITY_ENCRYPTION"),
    CLOUD_SETTINGS(false, "CLOUD_NETWORK"),
    TRANSFER(false),
    VIDEO_STREAMING(false, VIDEO_CAPTURE);

    private final boolean defaultEnabled;
    private final String persistedKey;
    private final FeatureGate parent;

    FeatureGate(boolean defaultEnabled) {
        this(defaultEnabled, null, null);
    }

    FeatureGate(boolean defaultEnabled, String persistedKey) {
        this(defaultEnabled, persistedKey, null);
    }

    FeatureGate(boolean defaultEnabled, FeatureGate parent) {
        this(defaultEnabled, null, parent);
    }

    FeatureGate(boolean defaultEnabled, String persistedKey, FeatureGate parent) {
        this.defaultEnabled = defaultEnabled;
        this.persistedKey = persistedKey;
        this.parent = parent;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }

    /** Stable storage key, including compatibility with gates renamed for clearer semantics. */
    public String persistedKey() {
        return persistedKey == null ? name() : persistedKey;
    }

    public FeatureGate parent() {
        return parent;
    }
}
