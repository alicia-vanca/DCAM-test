package com.dvid.dcam.platform.config;

import android.content.Context;
import android.content.SharedPreferences;
import com.dvid.dcam.feature.settings.application.port.MediaEncryptionPreferenceStore;
import java.util.Objects;

/** Android SharedPreferences implementation of media encryption preference. */
public final class AndroidMediaEncryptionPreferenceStoreImpl
        implements MediaEncryptionPreferenceStore {
    private static final String PREFS_NAME = "dcam_media_encryption";
    private static final String KEY_ENABLED = "enabled";

    private final PreferenceAccess preferences;

    public AndroidMediaEncryptionPreferenceStoreImpl(Context context) {
        Context applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        Context checkedContext = applicationContext == null ? context : applicationContext;
        preferences = new SharedPreferencesAccess(
                checkedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE));
    }

    AndroidMediaEncryptionPreferenceStoreImpl(PreferenceAccess preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    @Override public boolean isMediaEncryptionEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    @Override public void setMediaEncryptionEnabled(boolean enabled) {
        preferences.putBoolean(KEY_ENABLED, enabled);
    }

    interface PreferenceAccess {
        boolean getBoolean(String key, boolean fallback);
        void putBoolean(String key, boolean value);
    }

    private static final class SharedPreferencesAccess implements PreferenceAccess {
        private final SharedPreferences preferences;

        private SharedPreferencesAccess(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override public boolean getBoolean(String key, boolean fallback) {
            return preferences.getBoolean(key, fallback);
        }
        @Override public void putBoolean(String key, boolean value) {
            if (!preferences.edit().putBoolean(key, value).commit()) {
                throw new IllegalStateException("media encryption preference write failed");
            }
        }
    }
}
