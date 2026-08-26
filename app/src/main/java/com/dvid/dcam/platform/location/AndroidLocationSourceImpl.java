package com.dvid.dcam.platform.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import com.dvid.dcam.feature.location.application.port.LocationSource;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import java.util.function.Consumer;

/** Foreground Activity-bound source for explicit Google Fused or satellite GNSS tracking. */
public final class AndroidLocationSourceImpl implements LocationSource {
    private final Context context;
    private final LocationManager locationManager;
    private final AndroidFusedLocationSourceImpl fusedSource;
    private final AndroidLocationProviderCapabilities capabilities;
    private final LocationSessionGuard sessions = new LocationSessionGuard();
    private LocationListener satelliteListener;
    private volatile GpsCoordinate latest;
    private Consumer<GpsCoordinate> consumer;
    private GpsMode activeMode;
    private boolean satelliteActive;

    public AndroidLocationSourceImpl(
            Context context, AndroidLocationProviderCapabilities capabilities) {
        if (context == null || capabilities == null) {
            throw new IllegalArgumentException("Location dependencies are required");
        }
        this.context = context.getApplicationContext();
        locationManager = this.context.getSystemService(LocationManager.class);
        fusedSource = new AndroidFusedLocationSourceImpl(this.context);
        this.capabilities = capabilities;
    }

    @Override public synchronized LocationTrackingState start(
            GpsSettings settings, Consumer<GpsCoordinate> onCoordinate) {
        return start(settings, onCoordinate, ignored -> { });
    }

    @Override public synchronized LocationTrackingState start(
            GpsSettings settings,
            Consumer<GpsCoordinate> onCoordinate,
            Consumer<LocationTrackingState> onStateChanged) {
        if (settings == null || onCoordinate == null) {
            throw new IllegalArgumentException("Location arguments are required");
        }
        if (onStateChanged == null) {
            throw new IllegalArgumentException("Location state callback is required");
        }
        stopSources();
        long generation = sessions.startNewSession();
        consumer = onCoordinate;
        activeMode = settings.getMode();
        if (!hasFinePermission()) return LocationTrackingState.PERMISSION_REQUIRED;
        switch (activeMode) {
            case FUSED:
                return fusedSource.start(settings,
                        coordinate -> acceptCoordinate(generation, coordinate),
                        state -> acceptState(generation, state, onStateChanged));
            case SATELLITE:
                satelliteListener = location -> accept(generation, location);
                return startSatellite(settings);
            default:
                return LocationTrackingState.NO_PROVIDER;
        }
    }

    private LocationTrackingState startSatellite(GpsSettings settings) {
        String provider = capabilities.satelliteProvider();
        if (provider == null || locationManager == null) return LocationTrackingState.NO_PROVIDER;
        satelliteActive = register(provider, satelliteListener, settings);
        return satelliteActive
                ? LocationTrackingState.WAITING_FOR_LOCATION_INFO : LocationTrackingState.ERROR;
    }

    private boolean register(String provider, LocationListener listener, GpsSettings settings) {
        try {
            locationManager.requestLocationUpdates(provider,
                    settings.getReportIntervalSeconds() * 1000L,
                    settings.getUpdateDistanceMeters(), listener, Looper.getMainLooper());
            return true;
        } catch (SecurityException | IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override public synchronized void requestCurrentLocation(GpsSettings settings) {
        if (settings == null || !hasFinePermission()) return;
        if (activeMode == GpsMode.FUSED) fusedSource.requestCurrentLocation(settings);
    }

    @Override public synchronized void stop() {
        sessions.stopSession();
        stopSources();
        consumer = null;
        activeMode = null;
        latest = null;
    }

    @Override public GpsCoordinate latestCoordinate() { return latest; }

    private void accept(long generation, Location location) {
        if (location == null) return;
        try {
            acceptCoordinate(generation,
                    new GpsCoordinate(location.getLatitude(), location.getLongitude()));
        } catch (IllegalArgumentException ignored) {
            // Ignore a malformed platform coordinate and wait for the next location update.
        }
    }

    private synchronized void acceptCoordinate(long generation, GpsCoordinate coordinate) {
        if (coordinate == null || !sessions.isCurrent(generation)) return;
        latest = coordinate;
        if (consumer != null) consumer.accept(coordinate);
    }

    private synchronized void acceptState(long generation, LocationTrackingState state,
            Consumer<LocationTrackingState> onStateChanged) {
        if (state == null || !sessions.isCurrent(generation)) return;
        onStateChanged.accept(state);
    }

    private synchronized void stopSources() {
        if (locationManager != null && satelliteListener != null) {
            try { locationManager.removeUpdates(satelliteListener); }
            catch (RuntimeException ignored) {
                // Continue teardown even when the framework rejects listener removal.
            }
        }
        fusedSource.stop();
        satelliteListener = null;
        satelliteActive = false;
    }

    private boolean hasFinePermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
