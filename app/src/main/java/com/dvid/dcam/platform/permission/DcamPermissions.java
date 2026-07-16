package com.dvid.dcam.platform.permission;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DcamPermissions {
    private DcamPermissions() {}

    public static String[] runtime() {
        return coreRuntime();
    }

    /** Permissions required for the camera/audio application shell. */
    public static String[] coreRuntime() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (Build.VERSION.SDK_INT <= 28) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return permissions.toArray(new String[0]);
    }

    /** Optional permissions requested only when the GPS feature is enabled. */
    public static String[] locationRuntime() {
        return new String[] {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
    }

    /** Permissions managed by the device-owner kiosk policy. */
    public static String[] allRuntime() {
        List<String> permissions = new ArrayList<>(Arrays.asList(coreRuntime()));
        permissions.addAll(Arrays.asList(locationRuntime()));
        return permissions.toArray(new String[0]);
    }

    public static boolean allRuntimeGranted(Context context) {
        return coreRuntimeGranted(context);
    }

    public static boolean coreRuntimeGranted(Context context) {
        for (String permission : coreRuntime()) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    public static boolean cameraGranted(Context context) {
        return context.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean fineLocationGranted(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean locationGranted(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }
}
