package com.dvid.dcam.app.ui.settings;

import com.dvid.dcam.R;
import com.dvid.dcam.app.ui.MainScreen;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/** Immutable registry of routes rendered by the shared settings-detail screen. */
public final class SettingsScreenCatalog {
    private static final Map<MainScreen, Integer> TITLES;
    private static final Map<MainScreen, Integer> ITEMS;
    private static final Set<MainScreen> SCREENS;

    static {
        EnumMap<MainScreen, Integer> titles = new EnumMap<>(MainScreen.class);
        titles.put(MainScreen.RECORD_SETTINGS, R.string.record_settings);
        titles.put(MainScreen.CAMERA_SETTINGS, R.string.camera_settings_short);
        titles.put(MainScreen.VIDEO_STREAM_SETTINGS, R.string.video_stream_settings);
        titles.put(MainScreen.AUDIO_SETTINGS, R.string.audio_settings);
        titles.put(MainScreen.STORAGE_SETTINGS, R.string.storage_settings);
        titles.put(MainScreen.GPS_SETTINGS, R.string.gps_settings);
        titles.put(MainScreen.DEVICE_SETTINGS, R.string.device_settings_short);
        titles.put(MainScreen.USER_SETTINGS, R.string.security_settings);
        titles.put(MainScreen.SERVER_SETTINGS, R.string.network_settings);
        titles.put(MainScreen.TRANSFER_SETTINGS, R.string.transfer_settings);
        titles.put(MainScreen.ABOUT, R.string.about);
        titles.put(MainScreen.DEVELOPER_SETTINGS, R.string.developer_mode);
        titles.put(MainScreen.DEVELOPER_BUTTON_BINDINGS, R.string.button_role_bindings);
        TITLES = Collections.unmodifiableMap(titles);
        SCREENS = Collections.unmodifiableSet(EnumSet.copyOf(titles.keySet()));

        EnumMap<MainScreen, Integer> items = new EnumMap<>(MainScreen.class);
        items.put(MainScreen.RECORD_SETTINGS, R.array.record_settings_items);
        items.put(MainScreen.CAMERA_SETTINGS, R.array.camera_settings_items);
        items.put(MainScreen.VIDEO_STREAM_SETTINGS, R.array.video_stream_settings_items);
        items.put(MainScreen.AUDIO_SETTINGS, R.array.audio_settings_items);
        items.put(MainScreen.STORAGE_SETTINGS, R.array.storage_settings_items);
        items.put(MainScreen.GPS_SETTINGS, R.array.gps_settings_items);
        items.put(MainScreen.DEVICE_SETTINGS, R.array.device_settings_items);
        items.put(MainScreen.USER_SETTINGS, R.array.security_settings_items);
        items.put(MainScreen.SERVER_SETTINGS, R.array.network_settings_items);
        items.put(MainScreen.TRANSFER_SETTINGS, R.array.transfer_settings_items);
        items.put(MainScreen.ABOUT, R.array.about_settings_items);
        ITEMS = Collections.unmodifiableMap(items);
    }

    public Set<MainScreen> screens() {
        return SCREENS;
    }

    public boolean isSettingsScreen(MainScreen screen) {
        return SCREENS.contains(screen);
    }

    public int titleResource(MainScreen screen) {
        Integer resource = TITLES.get(screen);
        if (resource == null) {
            throw new IllegalArgumentException("No settings title for " + screen);
        }
        return resource;
    }

    public OptionalInt itemsResource(MainScreen screen) {
        if (!isSettingsScreen(screen)) {
            throw new IllegalArgumentException("No settings screen for " + screen);
        }
        Integer resource = ITEMS.get(screen);
        return resource == null ? OptionalInt.empty() : OptionalInt.of(resource);
    }
}
