package com.dvid.dcam.platform.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Looper;
import com.dvid.dcam.feature.location.application.port.LocationSource;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Foreground Activity-bound LocationManager source. */
public final class AndroidLocationSourceImpl implements LocationSource {
    private final Context context;
    private final LocationManager locationManager;
    private final AndroidFusedLocationSourceImpl fusedSource;
    private final LocationListener listener = this::accept;
    private volatile GpsCoordinate latest;
    private Consumer<GpsCoordinate> consumer;

    public AndroidLocationSourceImpl(Context context) {
        this.context = context.getApplicationContext();
        locationManager = this.context.getSystemService(LocationManager.class);
        fusedSource = new AndroidFusedLocationSourceImpl(this.context);
    }

    @Override public synchronized LocationTrackingState start(
            GpsSettings settings, Consumer<GpsCoordinate> onCoordinate) {
        return start(settings, onCoordinate, ignored -> { });
    }

    @Override public synchronized LocationTrackingState start(
            GpsSettings settings,
            Consumer<GpsCoordinate> onCoordinate,
            Consumer<LocationTrackingState> onStateChanged) {
        if (settings == null || onCoordinate == null) throw new IllegalArgumentException("Location arguments are required");
        if (onStateChanged == null) throw new IllegalArgumentException("Location state callback is required");
        stopUpdates();
        fusedSource.stop();
        consumer = onCoordinate;
        if (!hasCoarsePermission()
                || settings.getMode() == GpsMode.SATELLITE && !hasFinePermission()) {
            return LocationTrackingState.PERMISSION_REQUIRED;
        }
        if (settings.getMode() == GpsMode.NETWORK) {
            return startNetworkLocation(settings, onStateChanged);
        }
        if (settings.getMode() == GpsMode.AUTOMATIC) {
            LocationTrackingState networkState = startNetworkLocation(settings, onStateChanged);
            if (networkState != LocationTrackingState.LOCATION_UNAVAILABLE
                    && networkState != LocationTrackingState.NO_PROVIDER) {
                return networkState;
            }
            fusedSource.stop();
            if (!hasFinePermission()) return LocationTrackingState.PERMISSION_REQUIRED;
        }
        if (locationManager == null) return LocationTrackingState.LOCATION_UNAVAILABLE;
        List<String> selectedProviders = providers(settings.getMode());
        if (selectedProviders.isEmpty()) return LocationTrackingState.NO_PROVIDER;
        return startLocationManager(settings, selectedProviders);
    }

    private LocationTrackingState startNetworkLocation(
            GpsSettings settings, Consumer<LocationTrackingState> onStateChanged) {
        if (fusedSource.isGooglePlayServicesAvailable()) {
            return fusedSource.start(settings, this::acceptCoordinate, onStateChanged);
        }
        return startSystemFused(settings);
    }
    private LocationTrackingState startSystemFused(GpsSettings settings) {
        if (!hasCoarsePermission()) return LocationTrackingState.PERMISSION_REQUIRED;
        if (locationManager == null) {
            return LocationTrackingState.LOCATION_UNAVAILABLE;
        }
        List<String> selectedProviders = systemFusedProviders();
        if (selectedProviders.isEmpty()) return LocationTrackingState.LOCATION_UNAVAILABLE;
        return startLocationManager(settings, selectedProviders);
    }

    private LocationTrackingState startLocationManager(
            GpsSettings settings, List<String> selectedProviders) {
        boolean registered = false;
        for (String provider : selectedProviders) {
            try {
                locationManager.requestLocationUpdates(provider,
                        settings.getReportIntervalSeconds() * 1000L,
                        settings.getUpdateDistanceMeters(), listener, Looper.getMainLooper());
                registered = true;
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        return registered ? LocationTrackingState.WAITING_FOR_FIX : LocationTrackingState.ERROR;
    }

    @Override public synchronized void requestCurrentLocation(GpsSettings settings) {
        if (settings == null) return;
        if (settings.getMode() != GpsMode.SATELLITE && fusedSource.isGooglePlayServicesAvailable()) {
            fusedSource.requestCurrentLocation(settings);
            return;
        }
        if (locationManager == null || !hasCoarsePermission()) return;
        for (String provider : systemFusedProviders()) {
            try {
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
    }

    @Override public synchronized void stop() {
        stopUpdates();
        fusedSource.stop();
        consumer = null;
        latest = null;
    }

    @Override public GpsCoordinate latestCoordinate() { return latest; }

    private List<String> providers(GpsMode mode) {
        List<String> result = new ArrayList<>();
        if (hasFinePermission() && enabled(LocationManager.GPS_PROVIDER)) result.add(LocationManager.GPS_PROVIDER);
        if (mode == GpsMode.AUTOMATIC && hasCoarsePermission()
                && enabled(LocationManager.NETWORK_PROVIDER)) result.add(LocationManager.NETWORK_PROVIDER);
        return result;
    }

    /**
     * Providers usable for network location when Google Play Services is absent.
     * The framework fused provider is preferred; network is a vendor-backed fallback
     * on devices that expose it.
     */
    private List<String> systemFusedProviders() {
        List<String> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && enabled(LocationManager.FUSED_PROVIDER)) {
            result.add(LocationManager.FUSED_PROVIDER);
        }
        if (enabled(LocationManager.NETWORK_PROVIDER)) {
            result.add(LocationManager.NETWORK_PROVIDER);
        }
        return result;
    }

    private boolean enabled(String provider) {
        try { return locationManager.isProviderEnabled(provider); }
        catch (RuntimeException ignored) { return false; }
    }
    private boolean hasFinePermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    private boolean hasCoarsePermission() {
        return hasFinePermission() || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    private void accept(Location location) {
        if (location == null) return;
        try {
            GpsCoordinate coordinate = new GpsCoordinate(location.getLatitude(), location.getLongitude());
            latest = coordinate;
            Consumer<GpsCoordinate> callback = consumer;
            if (callback != null) callback.accept(coordinate);
        } catch (IllegalArgumentException ignored) { }
    }

    private void acceptCoordinate(GpsCoordinate coordinate) {
        if (coordinate == null) return;
        latest = coordinate;
        Consumer<GpsCoordinate> callback = consumer;
        if (callback != null) callback.accept(coordinate);
    }
    private void stopUpdates() {
        if (locationManager == null) return;
        try { locationManager.removeUpdates(listener); }
        catch (RuntimeException ignored) { }
    }
}
