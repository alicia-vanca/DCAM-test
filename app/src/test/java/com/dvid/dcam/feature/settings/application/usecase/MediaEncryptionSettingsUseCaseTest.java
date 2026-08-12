package com.dvid.dcam.feature.settings.application.usecase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.settings.application.port.MediaEncryptionPreferenceStore;
import org.junit.jupiter.api.Test;

final class MediaEncryptionSettingsUseCaseTest {
    @Test void effectiveStateRequiresGateAndStoredPreference() {
        FakeStore store = new FakeStore();
        boolean[] gate = {true};
        MediaEncryptionSettingsUseCase useCase = new MediaEncryptionSettingsUseCase(store, () -> gate[0]);

        store.enabled = true;
        assertTrue(useCase.isMediaEncryptionEnabled());
        gate[0] = false;
        assertFalse(useCase.isMediaEncryptionEnabled());
        gate[0] = true;
        useCase.setMediaEncryptionEnabled(false);
        assertFalse(useCase.isMediaEncryptionEnabled());
    }

    private static final class FakeStore implements MediaEncryptionPreferenceStore {
        private boolean enabled;
        @Override public boolean isMediaEncryptionEnabled() { return enabled; }
        @Override public void setMediaEncryptionEnabled(boolean enabled) { this.enabled = enabled; }
    }
}