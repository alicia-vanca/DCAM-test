package com.dvid.dcam.platform.config;

import com.dvid.dcam.feature.settings.application.port.VideoMd5PreferenceStore;
import android.content.Context;
import android.content.SharedPreferences;

public final class AndroidVideoMd5PreferenceStoreImpl implements VideoMd5PreferenceStore {
    private static final String PREFS_NAME = "dcam_video_md5";
    private static final String KEY_ENABLED = "enabled";
    private final Context context;

    public AndroidVideoMd5PreferenceStoreImpl(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public boolean isVideoMd5Enabled() {
        return prefs().getBoolean(KEY_ENABLED, true);
    }

    @Override public void setVideoMd5Enabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
