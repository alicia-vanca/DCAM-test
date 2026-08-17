package com.dvid.dcam.app.ui;

import com.dvid.dcam.feature.location.application.usecase.LocationControlUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationSettingsUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationTrackingUseCase;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Combines feature, permission, system and provider policy for the GPS presentation. */
public final class LocationTrackingCoordinator {
    private final LocationSettingsUseCase settings;
    private final LocationControlUseCase control;
    private final LocationTrackingUseCase tracking;
    private final BooleanSupplier featureEnabled;
    private final BooleanSupplier finePermissionGranted;
    private final Runnable onStateChanged;
    private GpsCoordinate coordinate;
    private LocationTrackingState state = LocationTrackingState.STOPPED;

    public LocationTrackingCoordinator(
            LocationSettingsUseCase settings,
            LocationControlUseCase control,
            LocationTrackingUseCase tracking,
            BooleanSupplier featureEnabled,
            BooleanSupplier finePermissionGranted,
            Runnable onStateChanged) {
        this.settings = Objects.requireNonNull(settings);
        this.control = Objects.requireNonNull(control);
        this.tracking = Objects.requireNonNull(tracking);
        this.featureEnabled = Objects.requireNonNull(featureEnabled);
        this.finePermissionGranted = Objects.requireNonNull(finePermissionGranted);
        this.onStateChanged = Objects.requireNonNull(onStateChanged);
    }

    public LocationTrackingState refresh() {
        if (!featureEnabled.getAsBoolean()) return stopAs(LocationTrackingState.STOPPED);
        GpsSettings current = settings.currentSettings();
        if (!finePermissionGranted.getAsBoolean()) {
            return stopAs(LocationTrackingState.PERMISSION_REQUIRED);
        }

        LocationSystemState systemState = control.currentState();
        if (systemState == LocationSystemState.DISABLED) {
            return stopAs(LocationTrackingState.LOCATION_DISABLED);
        }
        if (systemState != LocationSystemState.ENABLED
                || !control.isModeAvailable(current.getMode())) {
            return stopAs(LocationTrackingState.LOCATION_UNAVAILABLE);
        }

        LocationTrackingState started = tracking.start(this::acceptCoordinate, this::acceptState);
        LocationTrackingState result;
        synchronized (this) {
            if (state != LocationTrackingState.AVAILABLE) state = started;
            result = state;
        }
        onStateChanged.run();
        return result;
    }

    public void stop() { stopAs(LocationTrackingState.STOPPED); }

    public void requestCurrentLocation() { tracking.requestCurrentLocation(); }

    public synchronized LocationTrackingState currentState() { return state; }
    public synchronized GpsCoordinate currentCoordinate() { return coordinate; }

    public synchronized boolean shouldShowOnCamera() {
        return coordinate != null || state == LocationTrackingState.PERMISSION_REQUIRED
                || state == LocationTrackingState.WAITING_FOR_LOCATION_INFO
                || state == LocationTrackingState.AVAILABLE;
    }

    private LocationTrackingState stopAs(LocationTrackingState stoppedState) {
        synchronized (this) {
            if (state == stoppedState && coordinate == null) return stoppedState;
        }
        tracking.stop();
        synchronized (this) {
            state = stoppedState;
            if (stoppedState != LocationTrackingState.STOPPED) coordinate = null;
        }
        onStateChanged.run();
        return stoppedState;
    }

    private void acceptCoordinate(GpsCoordinate value) {
        if (value == null) return;
        synchronized (this) {
            coordinate = value;
            state = LocationTrackingState.AVAILABLE;
        }
        onStateChanged.run();
    }

    private void acceptState(LocationTrackingState value) {
        if (value == null) return;
        synchronized (this) {
            state = value;
        }
        onStateChanged.run();
    }
}
