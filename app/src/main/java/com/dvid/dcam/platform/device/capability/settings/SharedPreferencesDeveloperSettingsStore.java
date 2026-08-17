package com.dvid.dcam.platform.device.capability.settings;

import android.content.Context;
import android.content.SharedPreferences;
import com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class SharedPreferencesDeveloperSettingsStore implements DeveloperSettingsStore {
    private static final String PREFERENCES = "dcam_developer_camera_settings";
    private static final String MODE = "mode";
    private static final String RELEASE_CAMERA_WHEN_SCREEN_OFF =
            "release_camera_when_screen_off";
    private static final String RESOURCE_MONITOR_ENABLED =
            "resource_monitor_enabled";

    private final PreferenceAccess preferences;
    private volatile DeveloperSettingsStore.Mode sessionMode;

    public SharedPreferencesDeveloperSettingsStore(Context context) {
        Context applicationContext = Objects.requireNonNull(context, "context").getApplicationContext();
        Context checkedContext = applicationContext == null ? context : applicationContext;
        SharedPreferences sharedPreferences = checkedContext.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        this.preferences = new SharedPreferencesAccess(sharedPreferences);
    }

    SharedPreferencesDeveloperSettingsStore(PreferenceAccess preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    @Override public DeveloperSettingsStore.Mode mode() {
        DeveloperSettingsStore.Mode current = sessionMode;
        return current == null
                ? parseMode(preferences.get(MODE)).orElse(DeveloperSettingsStore.Mode.A)
                : current;
    }

    @Override public DeveloperSettingsStore.Mode mode(String cameraId) {
        return parseMode(preferences.get(cameraModeKey(cameraId))).orElseGet(this::mode);
    }

    @Override public void setMode(DeveloperSettingsStore.Mode mode) {
        DeveloperSettingsStore.Mode value = Objects.requireNonNull(mode, "mode");
        sessionMode = value;
        preferences.put(MODE, value.name());
        sessionMode = null;
    }

    @Override public void setMode(String cameraId, DeveloperSettingsStore.Mode mode) {
        DeveloperSettingsStore.Mode value = Objects.requireNonNull(mode, "mode");
        preferences.put(cameraModeKey(cameraId), value.name());
    }

    @Override public boolean releaseCameraWhenScreenOff() {
        return parseBoolean(preferences.get(RELEASE_CAMERA_WHEN_SCREEN_OFF)).orElse(true);
    }

    @Override public void setReleaseCameraWhenScreenOff(boolean enabled) {
        preferences.put(RELEASE_CAMERA_WHEN_SCREEN_OFF, Boolean.toString(enabled));
    }

    @Override public boolean resourceMonitorEnabled() {
        return parseBoolean(preferences.get(RESOURCE_MONITOR_ENABLED)).orElse(false);
    }

    @Override public void setResourceMonitorEnabled(boolean enabled) {
        preferences.put(RESOURCE_MONITOR_ENABLED, Boolean.toString(enabled));
    }

    private static String cameraModeKey(String cameraId) {
        if (cameraId == null || cameraId.isBlank()) {
            throw new IllegalArgumentException("cameraId is required");
        }
        return MODE + "_camera_" + cameraId.trim();
    }

    private static Optional<DeveloperSettingsStore.Mode> parseMode(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(DeveloperSettingsStore.Mode.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    private static Optional<Boolean> parseBoolean(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true")) return Optional.of(true);
        if (normalized.equals("false")) return Optional.of(false);
        return Optional.empty();
    }

    interface PreferenceAccess {
        String get(String key);
        void put(String key, String value);
    }

    private static final class SharedPreferencesAccess implements PreferenceAccess {
        private final SharedPreferences preferences;

        private SharedPreferencesAccess(SharedPreferences preferences) {
            this.preferences = Objects.requireNonNull(preferences, "preferences");
        }

        @Override public String get(String key) {
            return preferences.getString(key, null);
        }

        @Override public void put(String key, String value) {
            if (!preferences.edit().putString(key, value).commit()) {
                throw new IllegalStateException("developer settings write failed");
            }
        }
    }
}