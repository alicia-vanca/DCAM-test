package com.dvid.dcam.feature.location.application.usecase;

import com.dvid.dcam.feature.location.application.port.LocationSource;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import java.util.function.Consumer;

public final class LocationTrackingUseCase {
    private final LocationSettingsUseCase settings;
    private final LocationSource source;
    private Consumer<GpsCoordinate> consumer;
    private Consumer<LocationTrackingState> stateConsumer;
    private LocationTrackingState state = LocationTrackingState.STOPPED;

    public LocationTrackingUseCase(LocationSettingsUseCase settings, LocationSource source) {
        if (settings == null || source == null) throw new IllegalArgumentException("GPS dependencies are required");
        this.settings = settings;
        this.source = source;
    }
    public synchronized LocationTrackingState start(Consumer<GpsCoordinate> callback) {
        return start(callback, ignored -> { });
    }
    public synchronized LocationTrackingState start(
            Consumer<GpsCoordinate> callback,
            Consumer<LocationTrackingState> onStateChanged) {
        if (callback == null) throw new IllegalArgumentException("callback is required");
        if (onStateChanged == null) throw new IllegalArgumentException("state callback is required");
        consumer = callback;
        stateConsumer = onStateChanged;
        state = source.start(settings.currentSettings(), callback, this::acceptState);
        return state;
    }
    public synchronized LocationTrackingState restart() {
        if (consumer == null) return state;
        state = source.start(settings.currentSettings(), consumer, this::acceptState);
        return state;
    }
    public synchronized void stop() {
        source.stop();
        consumer = null;
        stateConsumer = null;
        state = LocationTrackingState.STOPPED;
    }
    public synchronized void requestCurrentLocation() {
        source.requestCurrentLocation(settings.currentSettings());
    }
    public GpsCoordinate latestCoordinate() { return source.latestCoordinate(); }
    public synchronized LocationTrackingState currentState() { return state; }

    private synchronized void acceptState(LocationTrackingState nextState) {
        if (nextState == null) return;
        state = nextState;
        Consumer<LocationTrackingState> callback = stateConsumer;
        if (callback != null) callback.accept(nextState);
    }
}
