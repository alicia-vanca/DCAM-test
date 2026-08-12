package com.dvid.dcam.platform.device.capability.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SharedPreferencesDeveloperSettingsStoreTest {
    @Test void missingAndCorruptModeDefaultToPipelineA() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedPreferencesDeveloperSettingsStore store =
                new SharedPreferencesDeveloperSettingsStore(preferences);

        assertEquals(DeveloperSettingsStore.Mode.A, store.mode());
        assertEquals(DeveloperSettingsStore.Mode.A, store.mode("0"));
        preferences.put("mode_camera_0", "not-a-mode");
        assertEquals(DeveloperSettingsStore.Mode.A, store.mode("0"));
        preferences.put("mode", "not-a-mode");
        assertEquals(DeveloperSettingsStore.Mode.A, store.mode());
        preferences.put("mode", "LEGACY");
        assertEquals(DeveloperSettingsStore.Mode.A, store.mode());
        preferences.put("mode", "SHADOW");
        assertEquals(DeveloperSettingsStore.Mode.A, store.mode());
    }

    @Test void modeRoundTripSupportsOnlyAutoAndForcedPipelines() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedPreferencesDeveloperSettingsStore store =
                new SharedPreferencesDeveloperSettingsStore(preferences);

        store.setMode(DeveloperSettingsStore.Mode.B);
        assertEquals(DeveloperSettingsStore.Mode.B, store.mode());
        store.setMode(DeveloperSettingsStore.Mode.A);
        assertEquals(DeveloperSettingsStore.Mode.A, store.mode());
        store.setMode(DeveloperSettingsStore.Mode.AUTO);
        assertEquals(DeveloperSettingsStore.Mode.AUTO, store.mode());
    }

    @Test void cameraModesPersistIndependentlyAndFallbackToLegacyMode() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedPreferencesDeveloperSettingsStore store =
                new SharedPreferencesDeveloperSettingsStore(preferences);

        store.setMode(DeveloperSettingsStore.Mode.A);
        assertEquals(DeveloperSettingsStore.Mode.A, store.mode("0"));
        assertEquals(DeveloperSettingsStore.Mode.A, store.mode("1"));

        store.setMode("0", DeveloperSettingsStore.Mode.B);
        store.setMode("1", DeveloperSettingsStore.Mode.AUTO);
        assertEquals(DeveloperSettingsStore.Mode.B, store.mode("0"));
        assertEquals(DeveloperSettingsStore.Mode.AUTO, store.mode("1"));
        assertEquals(DeveloperSettingsStore.Mode.A, store.mode());
        assertEquals("B", preferences.get("mode_camera_0"));
        assertEquals("AUTO", preferences.get("mode_camera_1"));
    }

    @Test void screenOffReleaseDefaultsOnAndCorruptValuesStayOn() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedPreferencesDeveloperSettingsStore store =
                new SharedPreferencesDeveloperSettingsStore(preferences);

        assertTrue(store.releaseCameraWhenScreenOff());
        preferences.put("release_camera_when_screen_off", "not-a-boolean");
        assertTrue(store.releaseCameraWhenScreenOff());
    }

    @Test void screenOffReleaseRoundTrips() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedPreferencesDeveloperSettingsStore store =
                new SharedPreferencesDeveloperSettingsStore(preferences);

        store.setReleaseCameraWhenScreenOff(false);
        assertFalse(store.releaseCameraWhenScreenOff());
        assertEquals("false", preferences.get("release_camera_when_screen_off"));

        store.setReleaseCameraWhenScreenOff(true);
        assertTrue(store.releaseCameraWhenScreenOff());
        assertEquals("true", preferences.get("release_camera_when_screen_off"));
    }

    @Test void failedWriteKeepsSessionModeUntilDurableRetrySucceeds() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedPreferencesDeveloperSettingsStore store =
                new SharedPreferencesDeveloperSettingsStore(preferences);
        preferences.failWrites = true;

        assertThrows(IllegalStateException.class,
                () -> store.setMode(DeveloperSettingsStore.Mode.B));

        assertEquals(DeveloperSettingsStore.Mode.B, store.mode());
        assertNull(preferences.get("mode"));

        preferences.failWrites = false;
        store.setMode(DeveloperSettingsStore.Mode.B);
        assertEquals(DeveloperSettingsStore.Mode.B, store.mode());
        assertEquals("B", preferences.get("mode"));
    }

    private static final class MemoryPreferences
            implements SharedPreferencesDeveloperSettingsStore.PreferenceAccess {
        private final Map<String, String> values = new HashMap<>();
        private boolean failWrites;

        @Override public String get(String key) { return values.get(key); }
        @Override public void put(String key, String value) {
            if (failWrites) throw new IllegalStateException("write failed");
            values.put(key, value);
        }
    }
}