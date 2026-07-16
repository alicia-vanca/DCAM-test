package com.dvid.dcam.platform.config;

import android.content.Context;
import android.content.SharedPreferences;
import com.dvid.dcam.feature.storage.application.port.StorageWarningPreferenceStore;

public final class AndroidStorageWarningPreferenceStoreImpl implements StorageWarningPreferenceStore {
    private static final String PREFS_NAME = "dcam_storage";
    private static final String KEY_WARNING_GB = "warning_gb";
    private static final int DEFAULT_WARNING_GB = 2;

    private final SharedPreferences preferences;

    public AndroidStorageWarningPreferenceStoreImpl(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override public int warningGb() {
        return clamp(preferences.getInt(KEY_WARNING_GB, DEFAULT_WARNING_GB));
    }

    @Override public void setWarningGb(int value) {
        preferences.edit().putInt(KEY_WARNING_GB, clamp(value)).apply();
    }

    private static int clamp(int value) { return Math.max(1, Math.min(20, value)); }
}
