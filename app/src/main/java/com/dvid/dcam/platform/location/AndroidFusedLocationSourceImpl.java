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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.util.function.Consumer;

/** Google Fused Location Provider adapter used by the GMAP mode when GMS is available. */
public final class AndroidFusedLocationSourceImpl implements LocationSource {
    private final Context context;
    private final FusedLocationProviderClient client;
    private final GoogleApiAvailability availability;
    private final LocationCallback callback = new LocationCallback() {
        @Override public void onLocationResult(LocationResult result) {
            if (result == null) return;
            for (Location location : result.getLocations()) accept(location);
        }
    };
    private volatile GpsCoordinate latest;
    private Consumer<GpsCoordinate> consumer;

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
        if (settings == null || onCoordinate == null) throw new IllegalArgumentException("GPS arguments are required");
        if (onStateChanged == null) throw new IllegalArgumentException("GPS state callback is required");
        stop();
        consumer = onCoordinate;
        if (!hasPermission()) return LocationTrackingState.PERMISSION_REQUIRED;
        if (!isGooglePlayServicesAvailable()) {
            return LocationTrackingState.LOCATION_UNAVAILABLE;
        }
        long intervalMs = settings.getReportIntervalSeconds() * 1000L;
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs)
                .setMinUpdateDistanceMeters(settings.getUpdateDistanceMeters())
                .setWaitForAccurateLocation(false)
                .build();
        try {
            client.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) accept(location);
            });
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener(error -> onStateChanged.accept(LocationTrackingState.ERROR));
            return LocationTrackingState.WAITING_FOR_FIX;
        } catch (SecurityException error) {
            return LocationTrackingState.PERMISSION_REQUIRED;
        } catch (IllegalArgumentException error) {
            return LocationTrackingState.ERROR;
        }
    }

    @Override public synchronized void stop() {
        consumer = null;
        try { client.removeLocationUpdates(callback); }
        catch (RuntimeException ignored) { }
        latest = null;
    }

    @Override public GpsCoordinate latestCoordinate() { return latest; }

    /** Returns whether Google Play Services fused location is available on this device. */
    public boolean isGooglePlayServicesAvailable() {
        return availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }

    private boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void accept(Location location) {
        if (location == null) return;
        try {
            GpsCoordinate coordinate = new GpsCoordinate(location.getLatitude(), location.getLongitude());
            latest = coordinate;
            Consumer<GpsCoordinate> callbackConsumer = consumer;
            if (callbackConsumer != null) callbackConsumer.accept(coordinate);
        } catch (IllegalArgumentException ignored) { }
    }
}
