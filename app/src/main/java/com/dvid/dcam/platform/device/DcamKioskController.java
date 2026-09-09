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
import android.os.Looper;
import android.os.SystemClock;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Coordinates soft launcher behavior and real Device Owner kiosk policy. */
public final class DcamKioskController {
    private static final long SLOW_POLICY_CALL_MS = 1_000L;
    private static final ExecutorService POLICY_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dcam-kiosk-policy");
        thread.setDaemon(true);
        return thread;
    });
    private final Context appContext;
    private final DevicePolicyManager devicePolicyManager;
    private final ComponentName admin;
    private final String packageName;
    private final Logger logger;

    public DcamKioskController(Context context, Logger logger) {
        appContext = context.getApplicationContext();
        this.logger = Objects.requireNonNull(logger, "logger");
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

    public boolean removeDeviceOwner() {
        if (!isDeviceOwner()) return false;
        logger.info(LogCategory.POLICY, "unspecified", "KIOSK_TRACE remove-device-owner begin");
        devicePolicyManager.setLockTaskPackages(admin, new String[0]);
        devicePolicyManager.clearDeviceOwnerApp(packageName);
        boolean removed = !isDeviceOwner();
        logger.info(LogCategory.POLICY, "unspecified", "KIOSK_TRACE remove-device-owner complete removed=" + removed);
        return removed;
    }

    public static void applyActiveKioskPolicyAsync(
            Context context, Logger logger, Runnable completion) {
        Objects.requireNonNull(logger, "logger");
        Context appContext = context.getApplicationContext();
        try {
            POLICY_EXECUTOR.execute(() -> {
                try {
                    DcamKioskController controller = new DcamKioskController(appContext, logger);
                    boolean deviceOwner = controller.isDeviceOwner();
                    controller.logPolicyEntry(
                            "applyActiveKioskPolicy", "deviceOwner=" + deviceOwner);
                    if (deviceOwner) controller.applyManagedKioskPolicy();
                } catch (RuntimeException error) {
                    logger.error(LogCategory.POLICY, "unspecified", null, "KIOSK_TRACE async policy failed", error);
                } finally {
                    complete(completion, logger);
                }
            });
        } catch (RuntimeException error) {
            logger.error(LogCategory.POLICY, "unspecified", null, "KIOSK_TRACE policy scheduling failed", error);
            complete(completion, logger);
        }
    }

    public void enterLockTaskIfAllowed(Activity activity) {
        enterLockTaskIfAllowed(activity, null);
    }

    public void enterLockTaskIfAllowed(Activity activity, Runnable onPolicyStateReady) {
        if (activity == null) return;
        try {
            POLICY_EXECUTOR.execute(() -> {
                try {
                    boolean deviceOwner = isDeviceOwner();
                    boolean permitted = deviceOwner
                            && devicePolicyManager.isLockTaskPermitted(packageName);
                    boolean active = lockTaskActive(activity);
                    logger.info(LogCategory.POLICY, "unspecified", "Lock task mode is " + (active ? "active" : "inactive")
                            + " for " + activity.getClass().getSimpleName()
                            + ". This app is " + (deviceOwner ? "" : "not ")
                            + "the device owner, and Android "
                            + (permitted ? "permits" : "does not permit")
                            + " lock task mode. Policy check ran on "
                            + (isMainThread() ? "main thread." : "background thread."));
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        if (permitted && !active) {
                            try {
                                logger.info(LogCategory.POLICY, "unspecified", "KIOSK_TRACE begin operation=Activity.startLockTask"
                                        + " mainThread=" + isMainThread());
                                activity.startLockTask();
                                logger.info(LogCategory.POLICY, "unspecified", "KIOSK_TRACE end operation=Activity.startLockTask");
                                logger.info(LogCategory.POLICY, "unspecified", "DCAM entered lock task mode");
                            } catch (RuntimeException error) {
                                logger.warn(LogCategory.POLICY, "unspecified", null, "Could not enter lock task mode", error);
                            }
                        }
                        complete(onPolicyStateReady, logger);
                    });
                } catch (RuntimeException error) {
                    logger.error(LogCategory.POLICY, "unspecified", null, "KIOSK_TRACE lock-task check failed", error);
                    try {
                        activity.runOnUiThread(() -> complete(onPolicyStateReady, logger));
                    } catch (RuntimeException callbackError) {
                        logger.error(LogCategory.POLICY, "unspecified", null, "KIOSK_TRACE lock-task callback failed", callbackError);
                    }
                }
            });
        } catch (RuntimeException error) {
            logger.error(LogCategory.POLICY, "unspecified", null, "KIOSK_TRACE lock-task scheduling failed", error);
            complete(onPolicyStateReady, logger);
        }
    }

    private void applyManagedKioskPolicy() {
        boolean deviceOwner = isDeviceOwner();
        long startedAt = SystemClock.elapsedRealtime();
        logger.info(LogCategory.POLICY, "unspecified", "KIOSK_TRACE managed-policy begin deviceOwner=" + deviceOwner
                + " package=" + packageName + " mainThread=" + isMainThread());
        if (!deviceOwner) return;
        try {
            runPolicyCall("setPermissionPolicy policy=AUTO_GRANT", () ->
                    devicePolicyManager.setPermissionPolicy(
                            admin, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT));
            ComponentName homeActivity = homeActivityComponent();
            if (homeActivity != null) {
                runPolicyCall("addPersistentPreferredActivity component="
                        + homeActivity.flattenToShortString(), () ->
                        devicePolicyManager.addPersistentPreferredActivity(
                                admin, homeIntentFilter(), homeActivity));
            } else {
                logger.info(LogCategory.POLICY, "unspecified", "No DCAM Home activity found for persistent preferred policy");
            }
            runPolicyCall("setLockTaskPackages", () ->
                    devicePolicyManager.setLockTaskPackages(admin, new String[] { packageName }));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runPolicyCall("setLockTaskFeatures", () ->
                        devicePolicyManager.setLockTaskFeatures(
                                admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                                        | DevicePolicyManager.LOCK_TASK_FEATURE_HOME));
            }
            runPolicyCall("setStatusBarDisabled disabled=true", () -> {
                if (!devicePolicyManager.setStatusBarDisabled(admin, true)) {
                    throw new IllegalStateException("Device policy rejected status bar disable");
                }
            });
            runPolicyCall("setUninstallBlocked blocked=true", () ->
                    devicePolicyManager.setUninstallBlocked(admin, packageName, true));
            logger.info(LogCategory.POLICY, "unspecified", "KIOSK_TRACE managed-policy complete elapsedMs="
                    + elapsedSince(startedAt));
            logger.info(LogCategory.POLICY, "unspecified", "Managed kiosk policy applied");
        } catch (SecurityException error) {
            logger.warn(LogCategory.POLICY, "unspecified", null, "Managed kiosk policy rejected by device policy elapsedMs="
                    + elapsedSince(startedAt), error);
        }
    }

    private void runPolicyCall(String operation, Runnable action) {
        long startedAt = SystemClock.elapsedRealtime();
        logger.info(LogCategory.POLICY, "unspecified", "KIOSK_TRACE begin operation=" + operation
                + " mainThread=" + isMainThread()
                + " caller=" + callerFrame());
        try {
            action.run();
            long elapsedMs = elapsedSince(startedAt);
            String message = "KIOSK_TRACE end operation=" + operation
                    + " elapsedMs=" + elapsedMs;
            if (isSlowPolicyCall(elapsedMs)) {
                logger.warn(LogCategory.POLICY, "unspecified", null, message + " slow=true", stackTrace(
                        "KIOSK_TRACE slow policy call operation=" + operation));
            } else {
                logger.info(LogCategory.POLICY, "unspecified", message);
            }
        } catch (RuntimeException error) {
            logger.error(LogCategory.POLICY, "unspecified", null, "KIOSK_TRACE failed operation=" + operation
                    + " elapsedMs=" + elapsedSince(startedAt), error);
            throw error;
        }
    }

    static boolean isSlowPolicyCall(long elapsedMs) {
        return elapsedMs >= SLOW_POLICY_CALL_MS;
    }

    private void logPolicyEntry(String operation, String details) {
        logger.info(LogCategory.POLICY, "unspecified", "KIOSK_TRACE entry operation=" + operation + " " + details
                + " mainThread=" + isMainThread() + " caller=" + callerFrame());
    }

    private static void complete(Runnable completion, Logger logger) {
        if (completion == null) return;
        try {
            completion.run();
        } catch (RuntimeException error) {
            logger.error(LogCategory.POLICY, "unspecified", null, "KIOSK_TRACE completion failed", error);
        }
    }

    private static RuntimeException stackTrace(String message) {
        return new RuntimeException(message);
    }

    private static boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private static String callerFrame() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName();
            if (!Thread.class.getName().equals(className)
                    && !DcamKioskController.class.getName().equals(className)
                    && !"dalvik.system.VMStack".equals(className)) {
                return frame.toString();
            }
        }
        return "unknown";
    }

    private static long elapsedSince(long startedAt) {
        return SystemClock.elapsedRealtime() - startedAt;
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