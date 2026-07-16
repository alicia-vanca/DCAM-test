package com.dvid.dcam.platform.device;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import com.dvid.dcam.platform.logging.DcamLogger;
import com.dvid.dcam.platform.permission.DcamPermissions;
import java.util.List;

/** Coordinates soft launcher behavior and real Device Owner kiosk policy. */
public final class DcamKioskController {
    private final Context appContext;
    private final DevicePolicyManager devicePolicyManager;
    private final ComponentName admin;
    private final String packageName;

    public DcamKioskController(Context context) {
        appContext = context.getApplicationContext();
        devicePolicyManager = appContext.getSystemService(DevicePolicyManager.class);
        admin = new ComponentName(appContext, DcamDeviceAdminReceiver.class);
        packageName = appContext.getPackageName();
    }

    public boolean isDeviceOwner() {
        return devicePolicyManager != null && devicePolicyManager.isDeviceOwnerApp(packageName);
    }

    public boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = appContext.getPackageManager().resolveActivity(
                home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null
                && resolved.activityInfo != null
                && packageName.equals(resolved.activityInfo.packageName);
    }

    public void applyActiveKioskPolicy() {
        if (!isDeviceOwner()) return;
        applyManagedKioskPolicy();
    }

    public void applyManagedKioskPolicy() {
        if (!isDeviceOwner()) return;
        try {
            devicePolicyManager.setPermissionPolicy(
                    admin, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT);
            grantRuntimePermissions();
            ComponentName homeActivity = homeActivityComponent();
            if (homeActivity != null) {
                devicePolicyManager.addPersistentPreferredActivity(
                        admin, homeIntentFilter(), homeActivity);
            } else {
                DcamLogger.i("No DCAM Home activity found for persistent preferred policy");
            }
            devicePolicyManager.setLockTaskPackages(admin, new String[] { packageName });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                devicePolicyManager.setLockTaskFeatures(
                        admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                                | DevicePolicyManager.LOCK_TASK_FEATURE_HOME);
            }
            devicePolicyManager.setStatusBarDisabled(admin, true);
            devicePolicyManager.setUninstallBlocked(admin, packageName, true);
            DcamLogger.i("Managed kiosk policy applied");
        } catch (SecurityException error) {
            DcamLogger.w("Managed kiosk policy rejected by device policy", error);
        }
    }

    public void clearManagedKioskPolicy() {
        if (!isDeviceOwner()) return;
        try {
            devicePolicyManager.setUninstallBlocked(admin, packageName, false);
            devicePolicyManager.setStatusBarDisabled(admin, false);
            devicePolicyManager.setLockTaskPackages(admin, new String[0]);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                devicePolicyManager.setLockTaskFeatures(
                        admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }
            devicePolicyManager.clearPackagePersistentPreferredActivities(admin, packageName);
            devicePolicyManager.setPermissionPolicy(
                    admin, DevicePolicyManager.PERMISSION_POLICY_PROMPT);
            for (String permission : DcamPermissions.allRuntime()) {
                devicePolicyManager.setPermissionGrantState(
                        admin, packageName, permission,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
            }
            DcamLogger.i("Managed kiosk policy cleared");
        } catch (SecurityException error) {
            DcamLogger.w("Managed kiosk policy clear rejected by device policy", error);
        }
    }

    public void enterLockTaskIfAllowed(Activity activity) {
        if (!isDeviceOwner() || !devicePolicyManager.isLockTaskPermitted(packageName)) return;
        if (lockTaskActive(activity)) return;
        try {
            activity.startLockTask();
            devicePolicyManager.setStatusBarDisabled(admin, true);
            DcamLogger.i("DCAM entered lock task mode");
        } catch (RuntimeException error) {
            DcamLogger.w("Could not enter lock task mode", error);
        }
    }

    private void grantRuntimePermissions() {
        for (String permission : DcamPermissions.allRuntime()) {
            boolean granted = devicePolicyManager.setPermissionGrantState(
                    admin, packageName, permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            if (!granted) DcamLogger.i("Runtime permission not policy-granted: " + permission);
        }
    }

    private boolean lockTaskActive(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager != null
                && activityManager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
    }

    private static IntentFilter homeIntentFilter() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        return filter;
    }

    private ComponentName homeActivityComponent() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setPackage(packageName);
        List<ResolveInfo> candidates = appContext.getPackageManager().queryIntentActivities(
                home, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo candidate : candidates) {
            if (candidate.activityInfo != null && packageName.equals(candidate.activityInfo.packageName)) {
                return new ComponentName(packageName, candidate.activityInfo.name);
            }
        }
        return null;
    }
}
