package com.dvid.dcam.platform.location;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;
import com.dvid.dcam.feature.location.application.port.LocationControlGateway;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import com.dvid.dcam.platform.device.DcamDeviceAdminReceiver;

/** Reads Android Location state and changes it only when Device Owner policy permits it. */
public final class AndroidLocationControlGatewayImpl implements LocationControlGateway {
    private final Context context;
    private final LocationManager locationManager;
    private final DevicePolicyManager devicePolicyManager;
    private final ComponentName admin;
    private final AndroidLocationProviderCapabilities capabilities;

    public AndroidLocationControlGatewayImpl(
            Context context, AndroidLocationProviderCapabilities capabilities) {
        if (context == null || capabilities == null) {
            throw new IllegalArgumentException("Location dependencies are required");
        }
        this.context = context.getApplicationContext();
        locationManager = this.context.getSystemService(LocationManager.class);
        devicePolicyManager = this.context.getSystemService(DevicePolicyManager.class);
        admin = new ComponentName(this.context, DcamDeviceAdminReceiver.class);
        this.capabilities = capabilities;
    }

    @Override public LocationSystemState currentState() {
        if (locationManager == null
                || !capabilities.hasAnySource()) {
            return LocationSystemState.UNAVAILABLE;
        }
        try {
            boolean enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? locationManager.isLocationEnabled() : capabilities.anyProviderEnabled();
            return enabled ? LocationSystemState.ENABLED : LocationSystemState.DISABLED;
        } catch (RuntimeException ignored) {
            return LocationSystemState.UNKNOWN;
        }
    }

    @Override public boolean isModeAvailable(GpsMode mode) {
        return capabilities.availability().isModeAvailable(mode);
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
}