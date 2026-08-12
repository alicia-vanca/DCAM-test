package com.dvid.dcam.feature.location.application.usecase;

import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import java.util.List;

public final class LocationSettingsUseCase {
    private static final List<GpsMode> MODES =
            List.of(GpsMode.AUTOMATIC, GpsMode.SATELLITE, GpsMode.NETWORK);
    private static final List<Integer> VALUES = List.of(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30);
    private final GpsSettingsStore store;

    public LocationSettingsUseCase(GpsSettingsStore store) {
        if (store == null) throw new IllegalArgumentException("store is required");
        this.store = store;
    }

    public GpsSettings currentSettings() { return store.load(); }
    public List<GpsMode> supportedModes() { return MODES; }
    public List<Integer> supportedSamplingValues() { return VALUES; }

    public void changeMode(GpsMode mode) {
        if (!MODES.contains(mode)) throw new IllegalArgumentException("Unsupported GPS mode");
        store.save(currentSettings().withMode(mode));
    }
    public void changeUpdateDistanceMeters(int meters) {
        requireValue(meters);
        store.save(currentSettings().withUpdateDistanceMeters(meters));
    }
    public void changeReportIntervalSeconds(int seconds) {
        requireValue(seconds);
        store.save(currentSettings().withReportIntervalSeconds(seconds));
    }
    private static void requireValue(int value) {
        if (!VALUES.contains(value)) throw new IllegalArgumentException("Unsupported GPS sampling value");
    }
}
