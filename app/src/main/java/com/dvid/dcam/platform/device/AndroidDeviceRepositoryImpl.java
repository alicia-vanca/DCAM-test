package com.dvid.dcam.platform.device;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.StatFs;
import com.dvid.dcam.feature.device.application.port.DeviceRepository;
import com.dvid.dcam.feature.device.domain.CapabilityStatus;
import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.feature.device.domain.DeviceStatus;
import java.io.File;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/** Android implementation of the device repository. */
public final class AndroidDeviceRepositoryImpl implements DeviceRepository {
    private final Context context;
    private final Supplier<File> storageRoot;

    public AndroidDeviceRepositoryImpl(Context context) {
        this(context, () -> context.getExternalFilesDir(null));
    }

    public AndroidDeviceRepositoryImpl(Context context, File storageRoot) {
        this(context, () -> storageRoot);
    }

    public AndroidDeviceRepositoryImpl(Context context, Supplier<File> storageRoot) {
        this.context = context.getApplicationContext();
        this.storageRoot = storageRoot;
    }

    @Override public DeviceInfo readInfo() {
        String platformSerial = platformSerialNumber();
        String hardwareId = platformSerial == null ? "unknown" : platformSerial;
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        return new DeviceInfo(hardwareId, model.trim());
    }

    @Override public DeviceStatus readStatus() {
        int batteryPercent = -1;
        long availableBytes = -1L;
        CapabilityStatus gpsStatus = CapabilityStatus.UNKNOWN;
        try {
            BatteryManager battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (battery != null) batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (RuntimeException ignored) {
            // Keep the battery status unknown when the system service cannot be read.
        }
        try {
            File storage = storageRoot.get();
            if (storage == null) storage = context.getFilesDir();
            availableBytes = new StatFs(storage.getAbsolutePath()).getAvailableBytes();
        } catch (RuntimeException ignored) {
            // Keep the available storage unknown when the filesystem cannot be queried.
        }
        try {
            LocationManager location = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (location == null || !context.getPackageManager().hasSystemFeature("android.hardware.location.gps")) {
                gpsStatus = CapabilityStatus.UNAVAILABLE;
            } else {
                gpsStatus = location.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        ? CapabilityStatus.AVAILABLE : CapabilityStatus.DISABLED;
            }
        } catch (RuntimeException ignored) {
            // Keep the GPS status unknown when the location service cannot be queried.
        }
        return new DeviceStatus(batteryPercent, availableBytes, gpsStatus);
    }

    private static String platformSerialNumber() {
        String buildSerial = buildSerial();
        if (buildSerial != null) return buildSerial;

        for (String property : new String[] {
                "ro.serialno",
                "ro.boot.serialno",
                "vendor.gsm.serial"
        }) {
            String serial = cleanSerial(systemProperty(property));
            if (serial != null) return serial;
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    private static String buildSerial() {
        try {
            return cleanSerial(Build.getSerial());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String systemProperty(String name) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class);
            Object value = get.invoke(null, name);
            return value instanceof String string ? string : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String cleanSerial(String serial) {
        if (serial == null) return null;
        String cleaned = serial.trim();
        if (cleaned.isBlank() || "unknown".equalsIgnoreCase(cleaned)) return null;
        String[] tokens = cleaned.split("\\s+");
        return tokens.length == 0 || tokens[0].isBlank() ? null : tokens[0];
    }
}
