package com.dvid.dcam.platform.logging.loggly;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.room.InvalidationTracker;
import com.dvid.dcam.R;
import com.dvid.dcam.platform.device.DcamDeviceAdminReceiver;
import com.dvid.dcam.platform.database.AppDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Foreground :loggly owner for normal Room and crash-spool delivery. */
public final class LogglyUploadService extends Service {
    private static final String TAG = "LogglyUpload";
    private static final String CHANNEL_ID = "dcam_background";
    private static final String LEGACY_CHANNEL_ID = "dcam_loggly";
    private static final int NOTIFICATION_ID = 1002;
    private static final long OBSERVER_RECHECK_MS = 30_000L;
    private static final long MIN_RETRY_WAIT_MS = 2_000L;
    private final ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();
    private final WakeSignal wakeSignal = new WakeSignal();
    private final IBinder binder = new LocalBinder();
    private volatile boolean stopped;
    private volatile Network activeNetwork;
    private volatile boolean networkAvailable;
    private boolean keepAliveWhenIdle;
    private InvalidationTracker.Observer observer;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    public static boolean start(Context context) {
        logLaunchEvent("start request pid=" + android.os.Process.myPid());
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, LogglyUploadService.class));
            logLaunchEvent("start request accepted");
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not start foreground Loggly upload service", error);
            return false;
        }
    }

    public static void logLaunchEvent(String message) {
        Log.i(TAG, "launch " + message);
    }

    public static boolean shouldStayBound(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
                || isSystemExemptedEligible(context);
    }

    static boolean isSystemExemptedEligible(Context context) {
        DevicePolicyManager policy = context.getSystemService(DevicePolicyManager.class);
        if (policy == null) return false;
        ComponentName admin = new ComponentName(context, DcamDeviceAdminReceiver.class);
        return policy.isDeviceOwnerApp(context.getPackageName()) || policy.isAdminActive(admin);
    }

    @Override public void onCreate() {
        logLaunchEvent("service onCreate pid=" + android.os.Process.myPid());
        super.onCreate();
        createNotificationChannel();
        ensureForeground();
        keepAliveWhenIdle = shouldStayBound(this);
        stopped = false;
        ensureObserver();
        registerNetworkCallback();
        uploadExecutor.execute(this::drainLoop);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForeground();
        logLaunchEvent("service onStartCommand startId=" + startId);
        wakeSignal.wake();
        return START_STICKY;
    }

    private void ensureForeground() {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_recording_notification)
                .setContentTitle(getString(R.string.loggly_notification_title))
                .setContentText(getString(R.string.loggly_notification_text))
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType());
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override public void onTimeout(int startId, int fgsType) {
        Log.w(TAG, "Foreground Loggly upload timed out type=" + fgsType);
        stopped = true;
        wakeSignal.wake();
        LogglyUploadScheduler.scheduleJobNow(getApplicationContext());
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private int foregroundServiceType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && isSystemExemptedEligible(this)) {
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED;
        }
        return ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
    }

    private void drainLoop() {
        while (!stopped) {
            ensureObserver();
            long observedWake = wakeSignal.generation();
            if (!networkAvailable) {
                if (!keepAliveWhenIdle) {
                    LogglyUploadScheduler.scheduleJobNow(getApplicationContext());
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                    return;
                }
                waitForWake(observedWake, 0L);
                continue;
            }
            Long nextRetryAtMillis;
            try {
                Long crashRetryAtMillis = LogglyCrashSpool.uploadPending(
                        getApplicationContext(), () -> stopped);
                Long roomRetryAtMillis = RoomLogUploader.uploadPending(
                        getApplicationContext(), () -> stopped);
                nextRetryAtMillis = earliest(crashRetryAtMillis, roomRetryAtMillis);
            } catch (RuntimeException error) {
                Log.e(TAG, "Loggly foreground upload failed", error);
                nextRetryAtMillis = System.currentTimeMillis() + 10_000L;
            }
            if (!keepAliveWhenIdle) {
                LogglyUploadScheduler.scheduleRetry(getApplicationContext(), nextRetryAtMillis);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return;
            }
            long waitMillis;
            if (nextRetryAtMillis == null) {
                waitMillis = observer == null ? OBSERVER_RECHECK_MS : 0L;
            } else {
                waitMillis = Math.max(MIN_RETRY_WAIT_MS,
                        nextRetryAtMillis - System.currentTimeMillis());
            }
            waitForWake(observedWake, waitMillis);
        }
    }

    private static Long earliest(Long first, Long second) {
        if (first == null) return second;
        if (second == null) return first;
        return Math.min(first, second);
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.loggly_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.loggly_notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    private void registerNetworkCallback() {
        connectivityManager = getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                activeNetwork = network;
                networkAvailable = false;
                wakeSignal.wake();
            }

            @Override public void onLost(Network network) {
                if (!network.equals(activeNetwork)) return;
                activeNetwork = null;
                networkAvailable = false;
                wakeSignal.wake();
            }

            @Override public void onCapabilitiesChanged(
                    Network network, NetworkCapabilities capabilities) {
                if (!network.equals(activeNetwork)) return;
                networkAvailable = hasValidatedNetwork(capabilities);
                wakeSignal.wake();
            }
        };
        try {
            connectivityManager.registerDefaultNetworkCallback(
                    networkCallback, new Handler(Looper.getMainLooper()));
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not observe network state", error);
            networkCallback = null;
        }
    }

    private static boolean hasValidatedNetwork(NetworkCapabilities capabilities) {
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void ensureObserver() {
        if (observer != null) return;
        try {
            InvalidationTracker.Observer next = new InvalidationTracker.Observer("pending_logs") {
                @Override public void onInvalidated(java.util.Set<String> tables) {
                    wakeSignal.wake();
                }
            };
            AppDatabase.get(getApplicationContext()).getInvalidationTracker().addObserver(next);
            observer = next;
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not observe Room log queue", error);
            observer = null;
        }
    }

    private void waitForWake(long observedWake, long waitMillis) {
        try {
            wakeSignal.await(observedWake, waitMillis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            stopped = true;
        }
    }

    @Override public void onDestroy() {
        stopped = true;
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not remove network observer", error);
            }
        }
        if (observer != null) {
            try {
                AppDatabase.get(getApplicationContext()).getInvalidationTracker().removeObserver(observer);
            } catch (RuntimeException error) {
                Log.w(TAG, "Could not remove Room log observer", error);
            }
        }
        wakeSignal.wake();
        uploadExecutor.shutdownNow();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
        logLaunchEvent("service onBind");
        wakeSignal.wake();
        return binder;
    }

    @Override public boolean onUnbind(Intent intent) {
        wakeSignal.wake();
        return false;
    }

    static final class WakeSignal {
        private long generation;

        synchronized long generation() { return generation; }

        synchronized boolean changedSince(long observedGeneration) {
            return generation != observedGeneration;
        }

        synchronized void wake() {
            generation++;
            notifyAll();
        }

        synchronized void await(long observedGeneration, long waitMillis)
                throws InterruptedException {
            if (generation != observedGeneration) return;
            if (waitMillis == 0L) {
                while (generation == observedGeneration) {
                    wait();
                }
                return;
            }
            long startedAtNanos = System.nanoTime();
            long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(waitMillis);
            while (generation == observedGeneration) {
                long remainingNanos = timeoutNanos - (System.nanoTime() - startedAtNanos);
                if (remainingNanos <= 0L) return;
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
                int remainingNanoPart = (int) (remainingNanos
                        - TimeUnit.MILLISECONDS.toNanos(remainingMillis));
                wait(remainingMillis, remainingNanoPart);
            }
        }
    }

    private static final class LocalBinder extends android.os.Binder { }
}
