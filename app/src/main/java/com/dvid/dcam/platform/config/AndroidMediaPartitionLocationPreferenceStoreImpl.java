package com.dvid.dcam.platform.config;

import android.content.Context;
import android.content.SharedPreferences;
import com.dvid.dcam.feature.storage.application.port.MediaPartitionLocationPreferenceStore;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;

/** Android persistence for the media storage selection. */
public final class AndroidMediaPartitionLocationPreferenceStoreImpl implements MediaPartitionLocationPreferenceStore {
    private static final String PREFS_NAME = "dcam_storage";
    private static final String KEY_MODE = "mode";

    private final Context context;
    private final MediaPartitionLocation defaultMode;

    public AndroidMediaPartitionLocationPreferenceStoreImpl(Context context, String defaultMode) {
        this.context = context.getApplicationContext();
        this.defaultMode = MediaPartitionLocation.from(defaultMode);
    }

    @Override public MediaPartitionLocation currentMediaPartitionLocation() {
        return MediaPartitionLocation.from(prefs().getString(KEY_MODE, defaultMode.name()));
    }

    @Override public void selectMediaPartitionLocation(MediaPartitionLocation mode) {
        prefs().edit().putString(KEY_MODE, mode.name()).apply();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}




