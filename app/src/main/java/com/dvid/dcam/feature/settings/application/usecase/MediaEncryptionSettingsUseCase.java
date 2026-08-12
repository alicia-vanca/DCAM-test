package com.dvid.dcam.feature.settings.application.usecase;

import com.dvid.dcam.feature.settings.application.port.MediaEncryptionPreferenceStore;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class MediaEncryptionSettingsUseCase {
    private final MediaEncryptionPreferenceStore preferences;
    private final BooleanSupplier featureEnabled;

    public MediaEncryptionSettingsUseCase(
            MediaEncryptionPreferenceStore preferences, BooleanSupplier featureEnabled) {
        this.preferences = Objects.requireNonNull(preferences);
        this.featureEnabled = Objects.requireNonNull(featureEnabled);
    }

    public boolean isMediaEncryptionEnabled() {
        return featureEnabled.getAsBoolean() && preferences.isMediaEncryptionEnabled();
    }

    public void setMediaEncryptionEnabled(boolean enabled) {
        preferences.setMediaEncryptionEnabled(enabled);
    }
}