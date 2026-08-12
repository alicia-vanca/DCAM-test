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
import com.dvid.dcam.feature.location.domain.LocationProviderAvailability;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import java.util.function.Consumer;

/** Foreground Activity-bound source that races available providers in automatic mode. */
public final class AndroidLocationSourceImpl implements LocationSource {
    private final Context context;
    private final LocationManager locationManager;
    private final AndroidFusedLocationSourceImpl fusedSource;
    private final AndroidLocationProviderCapabilities capabilities;
    private final LocationSourceRace race = new LocationSourceRace();
    private LocationListener satelliteListener;
    private LocationListener networkListener;
    private volatile GpsCoordinate latest;
    private Consumer<GpsCoordinate> consumer;
    private Consumer<LocationTrackingState> stateConsumer;
    private GpsMode activeMode;
    private boolean satelliteActive;
    private boolean networkActive;
    private String activeNetworkProvider;

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
        long generation = race.startNewSession();
        satelliteListener = location -> accept(generation,
                LocationSourceRace.Source.SATELLITE, location);
        networkListener = location -> accept(generation,
                LocationSourceRace.Source.NETWORK, location);
        consumer = onCoordinate;
        stateConsumer = onStateChanged;
        activeMode = settings.getMode();
        if (!hasFinePermission()) return LocationTrackingState.PERMISSION_REQUIRED;
        switch (activeMode) {
            case SATELLITE: return startSatellite(settings);
            case NETWORK: return startNetwork(settings, generation);
            case AUTOMATIC: return startAutomatic(settings, generation);
            default: return LocationTrackingState.NO_PROVIDER;
        }
    }

    private LocationTrackingState startAutomatic(GpsSettings settings, long generation) {
        LocationProviderAvailability availability = capabilities.availability();
        boolean attempted = false;
        boolean started = false;
        if (availability.isSatelliteAvailable()) {
            attempted = true;
            started = startSatellite(settings) == LocationTrackingState.WAITING_FOR_LOCATION_INFO;
        }
        if (availability.isNetworkAvailable()) {
            attempted = true;
            LocationTrackingState networkState = startNetwork(settings, generation);
            started = started || networkState == LocationTrackingState.WAITING_FOR_LOCATION_INFO;
        }
        if (started) return LocationTrackingState.WAITING_FOR_LOCATION_INFO;
        return attempted ? LocationTrackingState.ERROR : LocationTrackingState.NO_PROVIDER;
    }

    private LocationTrackingState startSatellite(GpsSettings settings) {
        String provider = capabilities.satelliteProvider();
        if (provider == null || locationManager == null) return LocationTrackingState.NO_PROVIDER;
        satelliteActive = register(provider, satelliteListener, settings);
        return satelliteActive
                ? LocationTrackingState.WAITING_FOR_LOCATION_INFO : LocationTrackingState.ERROR;
    }

    private LocationTrackingState startNetwork(GpsSettings settings, long generation) {
        if (!capabilities.availability().isNetworkAvailable()) {
            return LocationTrackingState.NO_PROVIDER;
        }
        if (capabilities.isGoogleFusedAvailable()) {
            networkActive = true;
            LocationTrackingState state = fusedSource.start(settings,
                    coordinate -> acceptCoordinate(generation, LocationSourceRace.Source.NETWORK,
                            coordinate),
                    nextState -> acceptNetworkState(generation, nextState));
            if (state != LocationTrackingState.WAITING_FOR_LOCATION_INFO
                    && state != LocationTrackingState.AVAILABLE) {
                networkActive = false;
            }
            return state;
        }
        activeNetworkProvider = capabilities.systemNetworkProvider();
        if (activeNetworkProvider == null || locationManager == null) {
            return LocationTrackingState.NO_PROVIDER;
        }
        networkActive = register(activeNetworkProvider, networkListener, settings);
        return networkActive
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
        if (settings == null || locationManager == null || !hasFinePermission()) return;
        LocationSourceRace.Source winner = race.winner();
        if (satelliteActive && (winner == null
                || winner == LocationSourceRace.Source.SATELLITE)) {
            requestSingleUpdate(LocationManager.GPS_PROVIDER, satelliteListener);
        }
        if (!networkActive || (winner != null
                && winner != LocationSourceRace.Source.NETWORK)) return;
        if (activeNetworkProvider == null && capabilities.isGoogleFusedAvailable()) {
            fusedSource.requestCurrentLocation(settings);
        } else if (activeNetworkProvider != null) {
            requestSingleUpdate(activeNetworkProvider, networkListener);
        }
    }

    private void requestSingleUpdate(String provider, LocationListener listener) {
        if (provider == null || listener == null) return;
        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException ignored) {
        }
    }

    @Override public synchronized void stop() {
        stopSources();
        race.stopSession();
        consumer = null;
        stateConsumer = null;
        activeMode = null;
        latest = null;
    }

    @Override public GpsCoordinate latestCoordinate() { return latest; }

    private void accept(long generation, LocationSourceRace.Source source, Location location) {
        if (location == null) return;
        try {
            acceptCoordinate(generation, source,
                    new GpsCoordinate(location.getLatitude(), location.getLongitude()));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private synchronized void acceptCoordinate(long generation,
            LocationSourceRace.Source source, GpsCoordinate coordinate) {
        if (coordinate == null || !race.isCurrent(generation)) return;
        boolean firstResult = race.tryWin(generation, source);
        if (!firstResult && !race.accepts(generation, source)) return;
        latest = coordinate;
        if (firstResult) stopLosingSource(generation, source);
        if (consumer != null) consumer.accept(coordinate);
    }

    private synchronized void acceptNetworkState(
            long generation, LocationTrackingState state) {
        if (state == null || !race.isCurrent(generation)) return;
        if (state == LocationTrackingState.ERROR
                || state == LocationTrackingState.LOCATION_UNAVAILABLE
                || state == LocationTrackingState.NO_PROVIDER) {
            networkActive = false;
        }
        if (activeMode == GpsMode.AUTOMATIC && satelliteActive
                && race.winner() != LocationSourceRace.Source.NETWORK) return;
        if (stateConsumer != null) stateConsumer.accept(state);
    }

    private synchronized void stopLosingSource(long generation,
            LocationSourceRace.Source winner) {
        if (!race.isCurrent(generation)) return;
        if (winner == LocationSourceRace.Source.SATELLITE) stopNetworkSource();
        else stopSatelliteSource();
    }

    private synchronized void stopSources() {
        stopSatelliteSource();
        stopNetworkSource();
    }

    private void stopSatelliteSource() {
        if (locationManager != null && satelliteListener != null) {
            try { locationManager.removeUpdates(satelliteListener); }
            catch (RuntimeException ignored) { }
        }
        satelliteActive = false;
    }

    private void stopNetworkSource() {
        if (locationManager != null && networkListener != null) {
            try { locationManager.removeUpdates(networkListener); }
            catch (RuntimeException ignored) { }
        }
        fusedSource.stop();
        networkActive = false;
        activeNetworkProvider = null;
    }

    private boolean hasFinePermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
