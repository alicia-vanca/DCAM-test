package com.dvid.dcam.app.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    @Test void precisePermissionIsRequiredForGpsProviderModes() {
        Fixture fixture = new Fixture();
        fixture.coarsePermission = true;

        assertEquals(LocationTrackingState.PERMISSION_REQUIRED,
                fixture.coordinator().refresh());
        assertEquals(1, fixture.tracking.stopCount);
    }

    @Test void disabledSystemLocationIsDistinctFromMissingProvider() {
        Fixture fixture = new Fixture();
        fixture.finePermission = true;
        fixture.control.state = LocationSystemState.DISABLED;

        assertEquals(LocationTrackingState.LOCATION_DISABLED,
                fixture.coordinator().refresh());
    }

    @Test void providerFixChangesWaitingStateToAvailable() {
        Fixture fixture = new Fixture();
        fixture.finePermission = true;
        LocationTrackingCoordinator coordinator = fixture.coordinator();

        assertEquals(LocationTrackingState.WAITING_FOR_FIX, coordinator.refresh());
        fixture.tracking.emit(new GpsCoordinate(10, 106));

        assertEquals(LocationTrackingState.AVAILABLE, coordinator.currentState());
        assertEquals("10\u00B000'00.00\"N 106\u00B000'00.00\"E",
                coordinator.currentCoordinate().toString());
    }

    private static final class Fixture {
        private final FakeSettings settings = new FakeSettings();
        private final FakeControl control = new FakeControl();
        private final FakeTracking tracking = new FakeTracking();
        private boolean featureEnabled = true;
        private boolean coarsePermission;
        private boolean finePermission;

        private LocationTrackingCoordinator coordinator() {
            return new LocationTrackingCoordinator(
                    settings, control, tracking,
                    () -> featureEnabled,
                    () -> coarsePermission,
                    () -> finePermission,
                    () -> { });
        }
    }

    private static final class FakeSettings implements LocationSettingsUseCase {
        private GpsSettings current = new GpsSettings(
                GpsMode.GPS, 1, 1, LocationSystemState.ENABLED);

        @Override public GpsSettings currentSettings() { return current; }
        @Override public List<GpsMode> supportedModes() { return List.of(GpsMode.values()); }
        @Override public List<Integer> supportedSamplingValues() { return List.of(1); }
        @Override public void changeMode(GpsMode mode) { current = current.withMode(mode); }
        @Override public void changeUpdateDistanceMeters(int meters) {
            current = current.withUpdateDistanceMeters(meters);
        }
        @Override public void changeReportIntervalSeconds(int seconds) {
            current = current.withReportIntervalSeconds(seconds);
        }
    }

    private static final class FakeControl implements LocationControlUseCase {
        private LocationSystemState state = LocationSystemState.ENABLED;

        @Override public LocationSystemState currentState() { return state; }
        @Override public boolean isModeAvailable(GpsMode mode) { return true; }
        @Override public boolean setEnabledIfPermitted(boolean enabled) { return false; }
    }

    private static final class FakeTracking implements LocationTrackingUseCase {
        private Consumer<GpsCoordinate> callback;
        private int stopCount;

        @Override public LocationTrackingState start(Consumer<GpsCoordinate> onCoordinate) {
            callback = onCoordinate;
            return LocationTrackingState.WAITING_FOR_FIX;
        }
        @Override public LocationTrackingState restart() {
            return LocationTrackingState.WAITING_FOR_FIX;
        }
        @Override public void stop() { stopCount++; }
        @Override public GpsCoordinate latestCoordinate() { return null; }
        @Override public LocationTrackingState currentState() {
            return LocationTrackingState.WAITING_FOR_FIX;
        }
        private void emit(GpsCoordinate coordinate) { callback.accept(coordinate); }
    }
}
