package com.dvid.dcam.platform.device;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.provider.Settings;

/** Android system rotation and Device Owner Wi-Fi control. */
public final class AndroidDeviceSettings {
    private final Context context;

    public AndroidDeviceSettings(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isAutoRotateEnabled() {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 1) == 1;
    }

    public boolean canWriteSystemSettings() {
        return Settings.System.canWrite(context);
    }

    public boolean setAutoRotateEnabled(boolean enabled) {
        if (!canWriteSystemSettings()) return false;
        try {
            return Settings.System.putInt(context.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, enabled ? 1 : 0);
        } catch (SecurityException denied) {
            return false;
        }
    }

    public boolean isWifiEnabled() {
        WifiManager wifi = context.getSystemService(WifiManager.class);
        return wifi != null && wifi.isWifiEnabled();
    }

    public boolean isDeviceOwner() {
        DevicePolicyManager policy = context.getSystemService(DevicePolicyManager.class);
        return policy != null && policy.isDeviceOwnerApp(context.getPackageName());
    }

    @SuppressWarnings("deprecation")
    public boolean setWifiEnabled(boolean enabled) {
        DevicePolicyManager policy = context.getSystemService(DevicePolicyManager.class);
        WifiManager wifi = context.getSystemService(WifiManager.class);
        return isDeviceOwner() && wifi != null && wifi.setWifiEnabled(enabled);
    }
}
