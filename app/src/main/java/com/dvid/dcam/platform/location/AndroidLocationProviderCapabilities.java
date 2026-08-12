package com.dvid.dcam.platform.location;

import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import com.dvid.dcam.feature.location.domain.LocationProviderAvailability;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import java.util.List;

/** Single Android provider capability policy shared by location control and tracking. */
public final class AndroidLocationProviderCapabilities {
    private final Context context;
    private final LocationManager locationManager;
    private final GoogleApiAvailability googleApiAvailability;

    public AndroidLocationProviderCapabilities(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        this.context = context.getApplicationContext();
        locationManager = this.context.getSystemService(LocationManager.class);
        googleApiAvailability = GoogleApiAvailability.getInstance();
    }

    public LocationProviderAvailability availability() {
        return LocationProviderCapabilityPolicy.availability(
                hasProvider(LocationManager.GPS_PROVIDER),
                isGoogleFusedAvailable(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && hasProvider(LocationManager.FUSED_PROVIDER),
                hasProvider(LocationManager.NETWORK_PROVIDER));
    }

    public boolean isGoogleFusedAvailable() {
        return googleApiAvailability.isGooglePlayServicesAvailable(context)
                == ConnectionResult.SUCCESS;
    }

    public String satelliteProvider() {
        return enabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
    }

    public String systemNetworkProvider() {
        LocationProviderCapabilityPolicy.SystemNetworkBackend backend =
                LocationProviderCapabilityPolicy.systemNetworkBackend(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                && enabled(LocationManager.FUSED_PROVIDER),
                        enabled(LocationManager.NETWORK_PROVIDER));
        switch (backend) {
            case FUSED: return LocationManager.FUSED_PROVIDER;
            case NETWORK: return LocationManager.NETWORK_PROVIDER;
            default: return null;
        }
    }

    public boolean anyProviderEnabled() {
        return enabled(LocationManager.GPS_PROVIDER)
                || enabled(LocationManager.NETWORK_PROVIDER)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && enabled(LocationManager.FUSED_PROVIDER));
    }

    private boolean hasProvider(String provider) {
        if (locationManager == null) return false;
        try {
            List<String> providers = locationManager.getAllProviders();
            return providers != null && providers.contains(provider);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean enabled(String provider) {
        if (!hasProvider(provider)) return false;
        try {
            return locationManager.isProviderEnabled(provider);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}