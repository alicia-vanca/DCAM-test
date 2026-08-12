package com.dvid.dcam.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AndroidMediaEncryptionPreferenceStoreImplTest {
    @Test void defaultsDisabledAndPersistsOnlyEnablePreference() {
        MemoryPreferences preferences = new MemoryPreferences();
        AndroidMediaEncryptionPreferenceStoreImpl store =
                new AndroidMediaEncryptionPreferenceStoreImpl(preferences);

        assertFalse(store.isMediaEncryptionEnabled());
        store.setMediaEncryptionEnabled(true);

        assertTrue(store.isMediaEncryptionEnabled());
        assertEquals(Map.of("enabled", true), preferences.values);
    }

    @Test void disablingDoesNotCreatePasswordOrLegacyMigrationState() {
        MemoryPreferences preferences = new MemoryPreferences();
        AndroidMediaEncryptionPreferenceStoreImpl store =
                new AndroidMediaEncryptionPreferenceStoreImpl(preferences);

        store.setMediaEncryptionEnabled(false);

        assertFalse(store.isMediaEncryptionEnabled());
        assertFalse(preferences.values.containsKey("password"));
        assertFalse(preferences.values.containsKey("legacy_cson_migrated"));
    }

    private static final class MemoryPreferences
            implements AndroidMediaEncryptionPreferenceStoreImpl.PreferenceAccess {
        private final Map<String, Object> values = new HashMap<>();

        @Override public boolean getBoolean(String key, boolean fallback) {
            return values.get(key) instanceof Boolean value ? value : fallback;
        }
        @Override public void putBoolean(String key, boolean value) {
            values.put(key, value);
        }
    }
}
