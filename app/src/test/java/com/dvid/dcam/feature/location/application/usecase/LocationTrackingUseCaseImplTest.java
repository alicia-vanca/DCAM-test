package com.dvid.dcam.feature.location.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

final class LocationTrackingUseCaseImplTest {
    @Test void startAndRestartExposeProviderStateAndKeepCallback() {
        FakeSource source = new FakeSource();
        LocationTrackingUseCase tracking = new LocationTrackingUseCaseImpl(
                new FakeSettings(), source);
        Consumer<GpsCoordinate> callback = ignored -> { };

        assertEquals(LocationTrackingState.WAITING_FOR_FIX, tracking.start(callback));
        assertSame(callback, source.callback);
        source.nextState = LocationTrackingState.NO_PROVIDER;
        assertEquals(LocationTrackingState.NO_PROVIDER, tracking.restart());
    }

    @Test void stopClearsStateAndStopsTheSource() {
        FakeSource source = new FakeSource();
        LocationTrackingUseCase tracking = new LocationTrackingUseCaseImpl(
                new FakeSettings(), source);
        tracking.start(ignored -> { });

        tracking.stop();

        assertEquals(LocationTrackingState.STOPPED, tracking.currentState());
        assertEquals(1, source.stopCount);
    }

    @Test void asynchronousProviderFailureIsExposedToTheCaller() {
        FakeSource source = new FakeSource();
        LocationTrackingUseCase tracking = new LocationTrackingUseCaseImpl(
                new FakeSettings(), source);
        List<LocationTrackingState> states = new ArrayList<>();
        tracking.start(ignored -> { }, states::add);

        source.emitState(LocationTrackingState.ERROR);

        assertEquals(LocationTrackingState.ERROR, tracking.currentState());
        assertEquals(List.of(LocationTrackingState.ERROR), states);
    }

    private static final class FakeSettings implements LocationSettingsUseCase {
        private GpsSettings settings = new GpsSettings(
                GpsMode.GPS, 1, 1, LocationSystemState.ENABLED);

        @Override public GpsSettings currentSettings() { return settings; }
        @Override public java.util.List<GpsMode> supportedModes() {
            return java.util.List.of(GpsMode.GPS, GpsMode.GPS_AGPS, GpsMode.GMAP);
        }
        @Override public java.util.List<Integer> supportedSamplingValues() {
            return java.util.List.of(1);
        }
        @Override public void changeMode(GpsMode mode) { settings = settings.withMode(mode); }
        @Override public void changeUpdateDistanceMeters(int meters) {
            settings = settings.withUpdateDistanceMeters(meters);
        }
        @Override public void changeReportIntervalSeconds(int seconds) {
            settings = settings.withReportIntervalSeconds(seconds);
        }
    }

    private static final class FakeSource implements LocationSource {
        private LocationTrackingState nextState = LocationTrackingState.WAITING_FOR_FIX;
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
        @Override public GpsCoordinate latestCoordinate() { return null; }
        private void emitState(LocationTrackingState state) { stateCallback.accept(state); }
    }
}
