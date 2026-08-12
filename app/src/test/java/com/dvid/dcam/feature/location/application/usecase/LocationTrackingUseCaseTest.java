package com.dvid.dcam.feature.location.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.application.port.LocationSource;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class LocationTrackingUseCaseTest {
    @Test void startAndRestartExposeProviderStateAndKeepCallback() {
        FakeSource source = new FakeSource();
        LocationTrackingUseCase tracking = new LocationTrackingUseCase(
                new LocationSettingsUseCase(new FakeSettingsStore()), source);
        Consumer<GpsCoordinate> callback = ignored -> { };

        assertEquals(LocationTrackingState.WAITING_FOR_LOCATION_INFO, tracking.start(callback));
        assertSame(callback, source.callback);
        source.nextState = LocationTrackingState.NO_PROVIDER;
        assertEquals(LocationTrackingState.NO_PROVIDER, tracking.restart());
    }

    @Test void stopClearsStateAndStopsTheSource() {
        FakeSource source = new FakeSource();
        LocationTrackingUseCase tracking = new LocationTrackingUseCase(
                new LocationSettingsUseCase(new FakeSettingsStore()), source);
        tracking.start(ignored -> { });

        tracking.stop();

        assertEquals(LocationTrackingState.STOPPED, tracking.currentState());
        assertEquals(1, source.stopCount);
    }

    @Test void asynchronousProviderFailureIsExposedToTheCaller() {
        FakeSource source = new FakeSource();
        LocationTrackingUseCase tracking = new LocationTrackingUseCase(
                new LocationSettingsUseCase(new FakeSettingsStore()), source);
        List<LocationTrackingState> states = new ArrayList<>();
        tracking.start(ignored -> { }, states::add);

        source.emitState(LocationTrackingState.ERROR);

        assertEquals(LocationTrackingState.ERROR, tracking.currentState());
        assertEquals(List.of(LocationTrackingState.ERROR), states);
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
    private static final class FakeSource implements LocationSource {
        private LocationTrackingState nextState = LocationTrackingState.WAITING_FOR_LOCATION_INFO;
        private Consumer<GpsCoordinate> callback;
        private Consumer<LocationTrackingState> stateCallback;
        private int stopCount;

        @Override public LocationTrackingState start(
                GpsSettings settings, Consumer<GpsCoordinate> onCoordinate) {
            callback = onCoordinate;
            return nextState;
        }
        @Override public LocationTrackingState start(
                GpsSettings settings,
                Consumer<GpsCoordinate> onCoordinate,
                Consumer<LocationTrackingState> onStateChanged) {
            callback = onCoordinate;
            stateCallback = onStateChanged;
            return nextState;
        }
        @Override public void stop() { stopCount++; }
        @Override public void requestCurrentLocation(GpsSettings settings) { }
        @Override public GpsCoordinate latestCoordinate() { return null; }
        private void emitState(LocationTrackingState state) { stateCallback.accept(state); }
    }
}
