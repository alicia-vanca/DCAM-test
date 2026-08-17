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
    private GpsCoordinate latestCoordinate;
    private long sessionId;
    private boolean sessionCallbackReceived;

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
        return startSession();
    }
    public synchronized LocationTrackingState restart() {
        if (consumer == null) return state;
        return startSession();
    }
    public synchronized void stop() {
        sessionId++;
        source.stop();
        consumer = null;
        stateConsumer = null;
        latestCoordinate = null;
        sessionCallbackReceived = false;
        state = LocationTrackingState.STOPPED;
    }
    public synchronized void requestCurrentLocation() {
        source.requestCurrentLocation(settings.currentSettings());
    }
    public synchronized GpsCoordinate latestCoordinate() {
        return state == LocationTrackingState.AVAILABLE ? latestCoordinate : null;
    }
    public synchronized LocationTrackingState currentState() { return state; }

    private LocationTrackingState startSession() {
        long activeSession = ++sessionId;
        latestCoordinate = null;
        sessionCallbackReceived = false;
        LocationTrackingState started = source.start(
                settings.currentSettings(),
                coordinate -> acceptCoordinate(activeSession, coordinate),
                nextState -> acceptState(activeSession, nextState));
        if (sessionId == activeSession && !sessionCallbackReceived && started != null) {
            state = started;
        }
        return state;
    }

    private synchronized void acceptCoordinate(long activeSession, GpsCoordinate coordinate) {
        if (sessionId != activeSession || coordinate == null) return;
        sessionCallbackReceived = true;
        latestCoordinate = coordinate;
        state = LocationTrackingState.AVAILABLE;
        Consumer<GpsCoordinate> callback = consumer;
        if (callback != null) callback.accept(coordinate);
    }

    private synchronized void acceptState(
            long activeSession, LocationTrackingState nextState) {
        if (sessionId != activeSession || nextState == null) return;
        sessionCallbackReceived = true;
        state = nextState;
        if (nextState != LocationTrackingState.AVAILABLE) latestCoordinate = null;
        Consumer<LocationTrackingState> callback = stateConsumer;
        if (callback != null) callback.accept(nextState);
    }
}
