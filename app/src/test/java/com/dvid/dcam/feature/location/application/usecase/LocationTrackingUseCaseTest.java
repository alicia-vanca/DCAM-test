package com.dvid.dcam.feature.location.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                new LocationSettingsUseCase(new FakeSettingsStore(), ignored -> true), source);
        List<GpsCoordinate> coordinates = new ArrayList<>();
        Consumer<GpsCoordinate> callback = coordinates::add;
        GpsCoordinate coordinate = new GpsCoordinate(21.034918, 105.767322);

        assertEquals(LocationTrackingState.WAITING_FOR_LOCATION_INFO, tracking.start(callback));
        source.emitCoordinate(coordinate);
        assertEquals(List.of(coordinate), coordinates);
        source.nextState = LocationTrackingState.NO_PROVIDER;
        assertEquals(LocationTrackingState.NO_PROVIDER, tracking.restart());
    }

    @Test void stopClearsStateAndStopsTheSource() {
        FakeSource source = new FakeSource();
        LocationTrackingUseCase tracking = new LocationTrackingUseCase(
                new LocationSettingsUseCase(new FakeSettingsStore(), ignored -> true), source);
        tracking.start(ignored -> { });

        tracking.stop();

        assertEquals(LocationTrackingState.STOPPED, tracking.currentState());
        assertNull(tracking.latestCoordinate());
        assertEquals(1, source.stopCount);
    }

    @Test void restartClearsCoordinateAndRejectsPreviousSessionCallback() {
        FakeSource source = new FakeSource();
        LocationTrackingUseCase tracking = new LocationTrackingUseCase(
                new LocationSettingsUseCase(new FakeSettingsStore(), ignored -> true), source);
        GpsCoordinate first = new GpsCoordinate(21.034918, 105.767322);
        GpsCoordinate second = new GpsCoordinate(10.776889, 106.700806);
        tracking.start(ignored -> { });
        Consumer<GpsCoordinate> previousSession = source.callback;
        source.emitCoordinate(first);
        assertSame(first, tracking.latestCoordinate());

        tracking.restart();
        previousSession.accept(first);

        assertNull(tracking.latestCoordinate());
        source.emitCoordinate(second);
        assertSame(second, tracking.latestCoordinate());
    }

    @Test void asynchronousProviderFailureIsExposedToTheCaller() {
        FakeSource source = new FakeSource();
        LocationTrackingUseCase tracking = new LocationTrackingUseCase(
                new LocationSettingsUseCase(new FakeSettingsStore(), ignored -> true), source);
        List<LocationTrackingState> states = new ArrayList<>();
        tracking.start(ignored -> { }, states::add);
        source.emitCoordinate(new GpsCoordinate(21.034918, 105.767322));

        source.emitState(LocationTrackingState.ERROR);

        assertEquals(LocationTrackingState.ERROR, tracking.currentState());
        assertNull(tracking.latestCoordinate());
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
        private void emitCoordinate(GpsCoordinate coordinate) { callback.accept(coordinate); }
        private void emitState(LocationTrackingState state) { stateCallback.accept(state); }
    }
}
