package com.dvid.dcam.platform.location;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import com.dvid.dcam.feature.location.application.port.LocationControlGateway;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import com.dvid.dcam.platform.device.DcamDeviceAdminReceiver;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import java.util.List;

/** Reads Android Location state and changes it only when Device Owner policy permits it. */
public final class AndroidLocationControlGatewayImpl implements LocationControlGateway {
    private final Context context;
    private final LocationManager locationManager;
    private final DevicePolicyManager devicePolicyManager;
    private final ComponentName admin;
    private final GoogleApiAvailability googleApiAvailability;

    public AndroidLocationControlGatewayImpl(Context context) {
        this.context = context.getApplicationContext();
        locationManager = this.context.getSystemService(LocationManager.class);
        devicePolicyManager = this.context.getSystemService(DevicePolicyManager.class);
        admin = new ComponentName(this.context, DcamDeviceAdminReceiver.class);
        googleApiAvailability = GoogleApiAvailability.getInstance();
    }

    @Override public LocationSystemState currentState() {
        if (locationManager == null || !hasLocationCapability()) return LocationSystemState.UNAVAILABLE;
        try {
            boolean enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? locationManager.isLocationEnabled() : anyProviderEnabled();
            return enabled ? LocationSystemState.ENABLED : LocationSystemState.DISABLED;
        } catch (RuntimeException ignored) {
            return LocationSystemState.UNKNOWN;
        }
    }

    @Override public boolean isModeAvailable(GpsMode mode) {
        if (mode == null) return false;
        PackageManager packageManager = context.getPackageManager();
        if (mode == GpsMode.GMAP) {
            return hasLocationCapability()
                    && (googleApiAvailability.isGooglePlayServicesAvailable(context)
                    == ConnectionResult.SUCCESS || hasSystemFusedCapability());
        }
        if (mode == GpsMode.GPS || mode == GpsMode.GPS_AGPS) {
            return packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        }
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK);
    }

    @Override public boolean setEnabled(boolean enabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || devicePolicyManager == null
                || !devicePolicyManager.isDeviceOwnerApp(context.getPackageName())) return false;
        try {
            devicePolicyManager.setLocationEnabled(admin, enabled);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean hasLocationCapability() {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK);
    }

    private boolean hasSystemFusedCapability() {
        if (locationManager == null) return false;
        try {
            List<String> providers = locationManager.getAllProviders();
            if (providers == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && providers.contains(LocationManager.FUSED_PROVIDER)) return true;
            return providers.contains(LocationManager.NETWORK_PROVIDER);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean anyProviderEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
}
