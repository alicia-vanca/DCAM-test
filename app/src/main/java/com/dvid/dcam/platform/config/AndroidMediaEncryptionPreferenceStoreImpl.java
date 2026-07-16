package com.dvid.dcam.platform.config;

import com.dvid.dcam.feature.settings.application.port.MediaEncryptionPreferenceStore;
import android.content.Context;
import android.content.SharedPreferences;
import com.dvid.dcam.core.config.domain.DcamConfig;

/** Android SharedPreferences implementation of media encryption preference. */
public final class AndroidMediaEncryptionPreferenceStoreImpl implements MediaEncryptionPreferenceStore {
    private static final String PREFS_NAME = "dcam_media_encryption";
    private static final String KEY_ENABLED = "enabled";

    private final Context context;
    private final boolean defaultEnabled;

    public AndroidMediaEncryptionPreferenceStoreImpl(Context context, DcamConfig config) {
        this.context = context.getApplicationContext();
        this.defaultEnabled = config.isVideoEncrypted();
    }

    @Override public boolean isMediaEncryptionEnabled() {
        return prefs().getBoolean(KEY_ENABLED, defaultEnabled);
    }

    @Override public void setMediaEncryptionEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
