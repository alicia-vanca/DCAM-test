package com.dvid.dcam.platform.location;

import android.content.Context;
import android.content.SharedPreferences;
import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import java.util.Locale;

/** SharedPreferences-backed GPS settings. No GPS data is stored in Room. */
public final class OperationalGpsSettingsStoreImpl implements GpsSettingsStore {
    private static final String PREFS = "dcam_location";
    private static final String MODE = "location.gps.mode";
    private static final String UPDATE_DISTANCE = "location.gps.update_distance_meters";
    private static final String REPORT_INTERVAL = "location.gps.report_interval_seconds";
    private static final String SYSTEM_STATE = "location.gps.system_state";
    private final SharedPreferences preferences;

    public OperationalGpsSettingsStoreImpl(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override public GpsSettings load() {
        return new GpsSettings(
                parseMode(preferences.getString(MODE, GpsMode.GPS.name())),
                positive(preferences.getInt(UPDATE_DISTANCE, 1)),
                positive(preferences.getInt(REPORT_INTERVAL, 1)),
                parseState(preferences.getString(SYSTEM_STATE, LocationSystemState.UNKNOWN.name())));
    }

    @Override public void save(GpsSettings settings) {
        if (settings == null) throw new IllegalArgumentException("settings is required");
        preferences.edit()
                .putString(MODE, settings.getMode().name())
                .putInt(UPDATE_DISTANCE, settings.getUpdateDistanceMeters())
                .putInt(REPORT_INTERVAL, settings.getReportIntervalSeconds())
                .putString(SYSTEM_STATE, settings.getSystemState().name())
                .apply();
    }

    @Override public void saveSystemState(LocationSystemState state) {
        if (state == null) throw new IllegalArgumentException("state is required");
        preferences.edit().putString(SYSTEM_STATE, state.name()).apply();
    }

    private static int positive(int value) { return value > 0 ? value : 1; }

    private static GpsMode parseMode(String value) {
        try { return GpsMode.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ignored) { return GpsMode.GPS; }
    }

    private static LocationSystemState parseState(String value) {
        try { return LocationSystemState.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ignored) { return LocationSystemState.UNKNOWN; }
    }
}
