package com.dvid.dcam.platform.logging.loggly;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import com.dvid.dcam.BuildSecrets;

/** Main-process supervisor that keeps the separate :loggly uploader running. */
public final class LogglyProcessSupervisor implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "LogglyUpload";
    private static final long HEALTH_CHECK_MS = 10_000L;
    private static final long VISIBLE_RETRY_MS = 2_000L;
    private static final long BACKGROUND_RETRY_MS = 15_000L;
    private static final long BOOTSTRAP_COOLDOWN_MS = 5_000L;

    private final Application application;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            binder = service;
            bindingRegistered = true;
            bootstrapPending = false;
            restartScheduled = false;
            mainHandler.removeCallbacks(restart);
            LogglyUploadService.logLaunchEvent("supervisor connected name="
                    + name.flattenToShortString());
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            binder = null;
            bootstrapPending = true;
            LogglyUploadService.logLaunchEvent("supervisor disconnected name="
                    + name.flattenToShortString());
            ensureAlive("service disconnected");
        }

        @Override public void onBindingDied(ComponentName name) {
            binder = null;
            bootstrapPending = true;
            releaseBinding();
            LogglyUploadService.logLaunchEvent("supervisor binding died name="
                    + name.flattenToShortString());
            ensureAlive("binding died");
        }

        @Override public void onNullBinding(ComponentName name) {
            binder = null;
            bootstrapPending = true;
            releaseBinding();
            LogglyUploadService.logLaunchEvent("supervisor null binding name="
                    + name.flattenToShortString());
            scheduleRestart();
        }
    };
    private final Runnable restart = () -> {
        restartScheduled = false;
        if (!isConnected()) ensureAlive("scheduled retry");
    };
    private final Runnable healthCheck = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (!isConnected()) ensureAlive("health check");
            mainHandler.postDelayed(this, HEALTH_CHECK_MS);
        }
    };

    private Activity resumedActivity;
    private IBinder binder;
    private boolean started;
    private boolean bindingRegistered;
    private boolean bootstrapPending = true;
    private boolean restartScheduled;
    private long lastBootstrapAtMillis;

    public LogglyProcessSupervisor(Application application) {
        this.application = application;
    }

    public void start() {
        if (started || !BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) return;
        started = true;
        application.registerActivityLifecycleCallbacks(this);
        ensureAlive("application start");
        mainHandler.postDelayed(healthCheck, HEALTH_CHECK_MS);
    }

    private void ensureAlive(String reason) {
        if (!started || !BuildSecrets.LOGGLY_TOKEN_CONFIGURED() || isConnected()) return;
        LogglyUploadService.logLaunchEvent("supervisor ensure alive reason=" + reason);
        LogglyUploadService.start(application);
        LogglyUploadScheduler.scheduleJobNow(application);
        ensureBound();
        requestBootstrapWhenVisible();
        scheduleRestart();
    }

    private void ensureBound() {
        if (bindingRegistered) return;
        try {
            int flags = Context.BIND_AUTO_CREATE;
            bindingRegistered = application.bindService(
                    new Intent(application, LogglyUploadService.class), connection, flags);
            LogglyUploadService.logLaunchEvent("supervisor bind result=" + bindingRegistered);
        } catch (RuntimeException error) {
            bindingRegistered = false;
            Log.e(TAG, "Could not bind supervised Loggly process", error);
        }
    }

    private void releaseBinding() {
        if (!bindingRegistered) return;
        try {
            application.unbindService(connection);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not release dead Loggly binding", error);
        } finally {
            bindingRegistered = false;
        }
    }

    private boolean isConnected() {
        IBinder current = binder;
        return current != null && current.isBinderAlive() && current.pingBinder();
    }

    private void requestBootstrapWhenVisible() {
        bootstrapPending = true;
        Activity activity = resumedActivity;
        if (activity == null) return;
        long now = SystemClock.elapsedRealtime();
        if (lastBootstrapAtMillis != 0L
                && now - lastBootstrapAtMillis < BOOTSTRAP_COOLDOWN_MS) return;
        if (LogglyProcessBootstrapActivity.start(activity)) {
            lastBootstrapAtMillis = now;
        }
    }

    private void scheduleRestart() {
        if (restartScheduled || isConnected()) return;
        restartScheduled = true;
        long delay = resumedActivity == null ? BACKGROUND_RETRY_MS : VISIBLE_RETRY_MS;
        mainHandler.postDelayed(restart, delay);
    }

    @Override public void onActivityResumed(Activity activity) {
        resumedActivity = activity;
        if (bootstrapPending || !isConnected()) ensureAlive("activity resumed");
    }

    @Override public void onActivityPaused(Activity activity) {
        if (resumedActivity == activity) resumedActivity = null;
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (resumedActivity == activity) resumedActivity = null;
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityStarted(Activity activity) { }
    @Override public void onActivityStopped(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
}