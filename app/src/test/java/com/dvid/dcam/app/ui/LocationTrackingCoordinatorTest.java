package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.application.port.LocationControlGateway;
import com.dvid.dcam.feature.location.application.port.LocationSource;
import com.dvid.dcam.feature.location.application.usecase.LocationControlUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationSettingsUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationTrackingUseCase;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class LocationTrackingCoordinatorTest {
    @Test void precisePermissionIsRequiredForEveryLocationMode() {
        for (GpsMode mode : GpsMode.values()) {
            Fixture fixture = new Fixture();
            fixture.settingsStore.settings = new GpsSettings(
                    mode, 1, 1, LocationSystemState.ENABLED);
            LocationTrackingCoordinator coordinator = fixture.coordinator();

            assertEquals(LocationTrackingState.PERMISSION_REQUIRED, coordinator.refresh());
            assertEquals(1, fixture.source.stopCount);
            assertEquals(true, coordinator.shouldShowOnCamera());
            coordinator.refresh();
            assertEquals(1, fixture.source.stopCount);
        }
    }

    @Test void disabledSystemLocationIsDistinctFromMissingProvider() {
        Fixture fixture = new Fixture();
        fixture.finePermission = true;
        fixture.controlGateway.state = LocationSystemState.DISABLED;

        assertEquals(LocationTrackingState.LOCATION_DISABLED,
                fixture.coordinator().refresh());
    }

    @Test void providerFixChangesWaitingStateToAvailable() {
        Fixture fixture = new Fixture();
        fixture.finePermission = true;
        LocationTrackingCoordinator coordinator = fixture.coordinator();

        assertEquals(LocationTrackingState.WAITING_FOR_LOCATION_INFO, coordinator.refresh());
        assertEquals(true, coordinator.shouldShowOnCamera());
        fixture.source.emit(new GpsCoordinate(10, 106));

        assertEquals(LocationTrackingState.AVAILABLE, coordinator.currentState());
        assertEquals("10\u00B000'00.00\"N 106\u00B000'00.00\"E",
                coordinator.currentCoordinate().toString());
    }

    @Test void currentLocationRequestDelegatesToActiveTracking() {
        Fixture fixture = new Fixture();
        fixture.finePermission = true;
        fixture.coordinator().refresh();

        fixture.coordinator().requestCurrentLocation();

        assertEquals(1, fixture.source.currentLocationRequestCount);
    }

    @Test void lastCoordinateRemainsVisibleWhileTrackingRestarts() {
        Fixture fixture = new Fixture();
        fixture.finePermission = true;
        LocationTrackingCoordinator coordinator = fixture.coordinator();
        GpsCoordinate coordinate = new GpsCoordinate(10, 106);

        coordinator.refresh();
        fixture.source.emit(coordinate);
        coordinator.stop();

        assertEquals(coordinate, coordinator.currentCoordinate());
        assertEquals(true, coordinator.shouldShowOnCamera());

        coordinator.refresh();

        assertEquals(coordinate, coordinator.currentCoordinate());
        assertEquals(true, coordinator.shouldShowOnCamera());
    }

    private static final class Fixture {
        private final FakeSettingsStore settingsStore = new FakeSettingsStore();
        private final LocationSettingsUseCase settings = new LocationSettingsUseCase(settingsStore);
        private final FakeControlGateway controlGateway = new FakeControlGateway();
        private final LocationControlUseCase control = new LocationControlUseCase(
                controlGateway, settingsStore);
        private final FakeSource source = new FakeSource();
        private final LocationTrackingUseCase tracking = new LocationTrackingUseCase(settings, source);
        private boolean featureEnabled = true;
        private boolean finePermission;

        private LocationTrackingCoordinator coordinator() {
            return new LocationTrackingCoordinator(
                    settings, control, tracking,
                    () -> featureEnabled,
                    () -> finePermission,
                    () -> { });
        }
    }

    private static final class FakeSettingsStore implements GpsSettingsStore {
        private GpsSettings settings = new GpsSettings(
                GpsMode.SATELLITE, 1, 1, LocationSystemState.ENABLED);

        @Override public GpsSettings load() { return settings; }
        @Override public void save(GpsSettings settings) { this.settings = settings; }
        @Override public void saveSystemState(LocationSystemState state) {
            settings = settings.withSystemState(state);
        }
    }

    private static final class FakeControlGateway implements LocationControlGateway {
        private LocationSystemState state = LocationSystemState.ENABLED;

        @Override public LocationSystemState currentState() { return state; }
        @Override public boolean isModeAvailable(GpsMode mode) { return true; }
        @Override public boolean setEnabled(boolean enabled) {
            state = enabled ? LocationSystemState.ENABLED : LocationSystemState.DISABLED;
            return true;
        }
    }

    private static final class FakeSource implements LocationSource {
        private Consumer<GpsCoordinate> callback;
        private int stopCount;
        private int currentLocationRequestCount;

        @Override public LocationTrackingState start(
                GpsSettings settings, Consumer<GpsCoordinate> onCoordinate) {
            callback = onCoordinate;
            return LocationTrackingState.WAITING_FOR_LOCATION_INFO;
        }
        @Override public LocationTrackingState start(
                GpsSettings settings,
                Consumer<GpsCoordinate> onCoordinate,
                Consumer<LocationTrackingState> onStateChanged) {
            callback = onCoordinate;
            return LocationTrackingState.WAITING_FOR_LOCATION_INFO;
        }
        @Override public void stop() { stopCount++; }
        @Override public void requestCurrentLocation(GpsSettings settings) {
            currentLocationRequestCount++;
        }
        @Override public GpsCoordinate latestCoordinate() { return null; }
        private void emit(GpsCoordinate coordinate) { callback.accept(coordinate); }
    }
}
