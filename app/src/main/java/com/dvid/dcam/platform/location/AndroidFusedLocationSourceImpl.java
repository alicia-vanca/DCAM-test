package com.dvid.dcam.platform.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import com.dvid.dcam.feature.location.application.port.LocationSource;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.util.function.Consumer;

/** Google Fused Location Provider adapter used when GMS is available. */
public final class AndroidFusedLocationSourceImpl implements LocationSource {
    private final Context context;
    private final FusedLocationProviderClient client;
    private final GoogleApiAvailability availability;
    private final LocationSessionGuard sessions = new LocationSessionGuard();
    private volatile GpsCoordinate latest;
    private Consumer<GpsCoordinate> consumer;
    private LocationCallback callback;

    public AndroidFusedLocationSourceImpl(Context context) {
        this.context = context.getApplicationContext();
        client = LocationServices.getFusedLocationProviderClient(this.context);
        availability = GoogleApiAvailability.getInstance();
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
            throw new IllegalArgumentException("GPS arguments are required");
        }
        if (onStateChanged == null) {
            throw new IllegalArgumentException("GPS state callback is required");
        }
        stop();
        consumer = onCoordinate;
        if (!hasPermission()) return LocationTrackingState.PERMISSION_REQUIRED;
        if (!isGooglePlayServicesAvailable()) {
            return LocationTrackingState.LOCATION_UNAVAILABLE;
        }
        long generation = sessions.startNewSession();
        long intervalMs = settings.getReportIntervalSeconds() * 1000L;
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                .setMinUpdateDistanceMeters(settings.getUpdateDistanceMeters())
                .setWaitForAccurateLocation(false)
                .build();
        LocationCallback sessionCallback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                if (result == null) return;
                for (Location location : result.getLocations()) accept(generation, location);
            }
        };
        callback = sessionCallback;
        try {
            client.requestLocationUpdates(request, sessionCallback, Looper.getMainLooper())
                    .addOnFailureListener(error -> acceptFailure(
                            generation, onStateChanged));
            return LocationTrackingState.WAITING_FOR_LOCATION_INFO;
        } catch (SecurityException error) {
            stop();
            return LocationTrackingState.PERMISSION_REQUIRED;
        } catch (IllegalArgumentException error) {
            stop();
            return LocationTrackingState.ERROR;
        }
    }

    @Override public synchronized void requestCurrentLocation(GpsSettings ignored) {
        if (!hasPermission() || !isGooglePlayServicesAvailable()) return;
        long generation = sessions.currentSession();
        if (generation < 0L) return;
        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0)
                .build();
        try {
            client.getCurrentLocation(request, null)
                    .addOnSuccessListener(location -> accept(generation, location));
        } catch (SecurityException revokedPermission) {
        }
    }

    @Override public synchronized void stop() {
        sessions.stopSession();
        consumer = null;
        LocationCallback activeCallback = callback;
        callback = null;
        if (activeCallback != null) {
            try { client.removeLocationUpdates(activeCallback); }
            catch (RuntimeException ignored) { }
        }
        latest = null;
    }

    @Override public GpsCoordinate latestCoordinate() { return latest; }

    public boolean isGooglePlayServicesAvailable() {
        return availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }

    private boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private synchronized void accept(long generation, Location location) {
        if (location == null || !sessions.isCurrent(generation)) return;
        GpsCoordinate coordinate;
        try {
            coordinate = new GpsCoordinate(location.getLatitude(), location.getLongitude());
        } catch (IllegalArgumentException ignored) {
            return;
        }
        latest = coordinate;
        if (consumer != null) consumer.accept(coordinate);
    }

    private synchronized void acceptFailure(
            long generation, Consumer<LocationTrackingState> onStateChanged) {
        if (!sessions.isCurrent(generation)) return;
        onStateChanged.accept(LocationTrackingState.ERROR);
    }
}
