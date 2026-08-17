package com.dvid.dcam.feature.location.application.usecase;

import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import java.util.List;
import java.util.function.Predicate;

public final class LocationSettingsUseCase {
    private static final List<GpsMode> MODES = List.of(GpsMode.FUSED, GpsMode.SATELLITE);
    private static final List<Integer> VALUES = List.of(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30);
    private final GpsSettingsStore store;
    private final Predicate<GpsMode> modeAvailable;


    public LocationSettingsUseCase(
            GpsSettingsStore store, Predicate<GpsMode> modeAvailable) {
        if (store == null || modeAvailable == null) {
            throw new IllegalArgumentException("Location settings dependencies are required");
        }
        this.store = store;
        this.modeAvailable = modeAvailable;
    }

    public GpsSettings currentSettings() {
        GpsSettings current = store.load();
        if (modeAvailable.test(current.getMode())) return current;
        for (GpsMode mode : MODES) {
            if (!modeAvailable.test(mode)) continue;
            GpsSettings fallback = current.withMode(mode);
            store.save(fallback);
            return fallback;
        }
        return current;
    }

    public List<GpsMode> supportedModes() { return MODES; }
    public List<Integer> supportedSamplingValues() { return VALUES; }

    public void changeMode(GpsMode mode) {
        if (!MODES.contains(mode)) throw new IllegalArgumentException("Unsupported GPS mode");
        if (!modeAvailable.test(mode)) throw new IllegalArgumentException("Unavailable GPS mode");
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