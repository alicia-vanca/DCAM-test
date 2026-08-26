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
import android.os.PowerManager;
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
            restartScheduled = false;
            mainHandler.removeCallbacks(restart);
            LogglyUploadService.logLaunchEvent("supervisor connected name="
                    + name.flattenToShortString());
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            binder = null;
            LogglyUploadService.logLaunchEvent("supervisor disconnected name="
                    + name.flattenToShortString());
            ensureAlive("service disconnected");
        }

        @Override public void onBindingDied(ComponentName name) {
            binder = null;
            releaseBinding();
            LogglyUploadService.logLaunchEvent("supervisor binding died name="
                    + name.flattenToShortString());
            ensureAlive("binding died");
        }

        @Override public void onNullBinding(ComponentName name) {
            binder = null;
            releaseBinding();
            LogglyUploadService.logLaunchEvent("supervisor null binding name="
                    + name.flattenToShortString());
            scheduleRestart();
        }

        private void releaseBinding() {
            if (!bindingRegistered) return;
            try {
                application.unbindService(this);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not release dead Loggly binding", error);
            } finally {
                bindingRegistered = false;
            }
        }
    };
    private final Runnable restart = () -> {
        restartScheduled = false;
        if (!isConnected()) ensureAlive("scheduled retry");
    };
    private final Runnable healthCheck = new Runnable() {
        @Override public void run() {
            if (!started) return;
            boolean interactive = isScreenInteractive();
            if (!isConnected() || interactive != lastInteractive) {
                ensureAlive(interactive ? "screen became interactive" : "screen became non-interactive");
            }
            lastInteractive = interactive;
            mainHandler.postDelayed(this, HEALTH_CHECK_MS);
        }
    };

    private Activity resumedActivity;
    private IBinder binder;
    private boolean started;
    private boolean bindingRegistered;
    private boolean restartScheduled;
    private boolean lastInteractive;
    private long lastBootstrapAtMillis;

    public LogglyProcessSupervisor(Application application) {
        this.application = application;
    }

    public void start() {
        if (started || !BuildSecrets.LOGGLY_TOKEN_CONFIGURED()) return;
        started = true;
        application.registerActivityLifecycleCallbacks(this);
        lastInteractive = isScreenInteractive();
        ensureAlive("application start");
        mainHandler.postDelayed(healthCheck, HEALTH_CHECK_MS);
    }

    private void ensureAlive(String reason) {
        boolean visible = isVisibleActivity();
        boolean connected = isConnected();
        if (!started || !BuildSecrets.LOGGLY_TOKEN_CONFIGURED()
                || (connected && !visible)) return;
        LogglyUploadService.logLaunchEvent("supervisor ensure alive reason=" + reason);
        if (visible) {
            LogglyUploadService.start(application);
            if (!connected) {
                ensureBound();
                requestBootstrapWhenVisible();
            }
        }
        LogglyUploadScheduler.scheduleJobNow(application);
        scheduleRestart();
    }

    private boolean isVisibleActivity() {
        return resumedActivity != null && isScreenInteractive();
    }

    private boolean isScreenInteractive() {
        PowerManager power = application.getSystemService(PowerManager.class);
        return power != null && power.isInteractive();
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

    private boolean isConnected() {
        IBinder current = binder;
        return current != null && current.isBinderAlive() && current.pingBinder();
    }

    private void requestBootstrapWhenVisible() {
        if (!isVisibleActivity()) return;
        Activity activity = resumedActivity;
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
        long delay = isVisibleActivity() ? VISIBLE_RETRY_MS : BACKGROUND_RETRY_MS;
        mainHandler.postDelayed(restart, delay);
    }

    @Override public void onActivityResumed(Activity activity) {
        resumedActivity = activity;
        ensureAlive("activity resumed");
    }

    @Override public void onActivityPaused(Activity activity) {
        if (resumedActivity == activity) resumedActivity = null;
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (resumedActivity == activity) resumedActivity = null;
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {
        // The supervisor only needs the resumed activity to launch a bootstrap activity.
    }

    @Override public void onActivityStarted(Activity activity) {
        // The resumed callback, rather than started, defines a visible interactive activity.
    }

    @Override public void onActivityStopped(Activity activity) {
        // Paused and destroyed callbacks already clear the tracked activity when needed.
    }

    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {
        // Process supervision does not persist or mutate activity state.
    }
}
