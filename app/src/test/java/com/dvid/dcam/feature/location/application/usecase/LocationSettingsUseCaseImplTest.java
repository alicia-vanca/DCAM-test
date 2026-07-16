package com.dvid.dcam.feature.location.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LocationSettingsUseCaseImplTest {
    @Test void changesModeAndSamplingValuesInTheStore() {
        FakeStore store = new FakeStore();
        LocationSettingsUseCase settings = new LocationSettingsUseCaseImpl(store);

        settings.changeMode(GpsMode.AUTOMATIC);
        settings.changeUpdateDistanceMeters(10);
        settings.changeReportIntervalSeconds(5);

        assertEquals(GpsMode.AUTOMATIC, settings.currentSettings().getMode());
        assertEquals(10, settings.currentSettings().getUpdateDistanceMeters());
        assertEquals(5, settings.currentSettings().getReportIntervalSeconds());
        assertEquals(List.of(GpsMode.AUTOMATIC, GpsMode.SATELLITE, GpsMode.NETWORK),
                settings.supportedModes());
        assertEquals(30, settings.supportedSamplingValues().size());
    }

    @Test void rejectsUnsupportedSamplingValues() {
        LocationSettingsUseCase settings = new LocationSettingsUseCaseImpl(new FakeStore());

        assertThrows(IllegalArgumentException.class,
                () -> settings.changeUpdateDistanceMeters(0));
        assertThrows(IllegalArgumentException.class,
                () -> settings.changeReportIntervalSeconds(31));
    }

    private static final class FakeStore implements GpsSettingsStore {
        private GpsSettings settings = new GpsSettings(
                GpsMode.SATELLITE, 1, 1, LocationSystemState.UNKNOWN);

        @Override public GpsSettings load() { return settings; }
        @Override public void save(GpsSettings value) { settings = value; }
        @Override public void saveSystemState(LocationSystemState state) {
            settings = settings.withSystemState(state);
        }
    }
}
