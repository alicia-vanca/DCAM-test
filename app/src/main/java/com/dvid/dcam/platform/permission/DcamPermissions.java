package com.dvid.dcam.platform.permission;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DcamPermissions {
    private DcamPermissions() {}

    public static String[] runtime() {
        return coreRuntime();
    }

    /** Permissions required to preview and capture camera/audio media. */
    public static String[] captureRuntime() {
        return new String[] {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        };
    }

    /** Capture permissions plus runtime notification access. */
    public static String[] coreRuntime() {
        List<String> permissions = new ArrayList<>(Arrays.asList(captureRuntime()));
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
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
        permissions.addAll(Arrays.asList(legacyStorageRuntime()));
        permissions.addAll(Arrays.asList(locationRuntime()));
        return permissions.toArray(new String[0]);
    }

    public static String[] missing(Context context, String[] permissions) {
        List<String> missing = new ArrayList<>();
        for (String permission : permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing.toArray(new String[0]);
    }

    public static boolean allRuntimeGranted(Context context) {
        return coreRuntimeGranted(context);
    }

    public static boolean allFilesAccessGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return missing(context, legacyStorageRuntime()).length == 0;
    }

    public static String[] legacyStorageRuntime() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT <= 29) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return permissions.toArray(new String[0]);
    }

    public static boolean coreRuntimeGranted(Context context) {
        return missing(context, coreRuntime()).length == 0;
    }

    public static boolean cameraGranted(Context context) {
        return context.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean captureRuntimeGranted(Context context) {
        return missing(context, captureRuntime()).length == 0;
    }

    public static boolean fineLocationGranted(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

}
