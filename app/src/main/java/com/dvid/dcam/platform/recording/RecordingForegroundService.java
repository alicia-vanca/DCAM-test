package com.dvid.dcam.platform.recording;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.dvid.dcam.R;
import com.dvid.dcam.feature.capture.domain.RecordingMode;

/** Keeps interrupted capture protected while staged media is finalized. */
public final class RecordingForegroundService extends Service {
    private static final String CHANNEL_ID = "dcam_recording";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_STATE_CHANGED = "dcam.recording.STATE_CHANGED";
    private static final String ACTION_CAMERA_READY = "dcam.camera.READY";
    private static final String ACTION_CAMERA_RELEASED = "dcam.camera.RELEASED";

    private RecordingForegroundStateStore stateStore;
    private PowerManager.WakeLock videoWakeLock;
    private boolean cameraReady;

    public static boolean keepCameraReady(Context context) {
        return dispatchCameraReady(context, true);
    }

    public static void releaseCameraReady(Context context) {
        dispatchCameraReady(context, false);
    }

    public static boolean startVideo(Context context, String fileName) {
        return startVideo(context, fileName, RecordingMode.VIDEO);
    }

    public static boolean startVideo(Context context, String fileName, RecordingMode mode) {
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(context);
        boolean alreadyActive = store.snapshot().hasVideo();
        if (!store.startVideo(fileName, mode, System.currentTimeMillis()) && !alreadyActive) return false;
        if (dispatchForeground(context)) return true;
        if (!alreadyActive) store.stopVideo();
        return false;
    }

    public static boolean updateVideoMode(Context context, RecordingMode mode) {
        return new RecordingForegroundStateStore(context).updateVideoMode(mode);
    }

    public static void markVideoFinalizing(Context context) {
        new RecordingForegroundStateStore(context).markVideoFinalizing();
    }

    public static void markAudioFinalizing(Context context) {
        new RecordingForegroundStateStore(context).markAudioFinalizing();
    }

    public static void markUnfinishedCaptureFinalizing(Context context) {
        new RecordingForegroundStateStore(context).markUnfinishedCaptureFinalizing();
    }

    public static void completeVideoFinalization(Context context) {
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(context);
        if (store.completeVideoFinalization()) dispatchStateChanged(context);
    }

    public static void completeAudioFinalization(Context context) {
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(context);
        if (store.completeAudioFinalization()) dispatchStateChanged(context);
    }

    public static boolean startAudio(Context context, String fileName) {
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(context);
        boolean alreadyActive = store.snapshot().hasAudio();
        if (!store.startAudio(fileName, System.currentTimeMillis()) && !alreadyActive) return false;
        if (dispatchForeground(context)) return true;
        if (!alreadyActive) store.stopAudio();
        return false;
    }

    public static void stopVideo(Context context) {
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(context);
        if (store.stopVideo()) dispatchStateChanged(context);
    }

    public static void stopAudio(Context context) {
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(context);
        if (store.stopAudio()) dispatchStateChanged(context);
    }


    public static boolean hasUnfinishedCapture(Context context) {
        return new RecordingForegroundStateStore(context).snapshot().isActive();
    }


    public static String interruptedVideoFinalizationFileName(Context context) {
        RecordingForegroundStateStore.Snapshot snapshot =
                new RecordingForegroundStateStore(context).snapshot();
        return snapshot.hasVideo() && !snapshot.shouldRecoverVideo()
                ? snapshot.videoFileName() : null;
    }

    public static String interruptedAudioFinalizationFileName(Context context) {
        RecordingForegroundStateStore.Snapshot snapshot =
                new RecordingForegroundStateStore(context).snapshot();
        return snapshot.hasAudio() && !snapshot.shouldRecoverAudio()
                ? snapshot.audioFileName() : null;
    }


    private static boolean dispatchCameraReady(Context context, boolean ready) {
        if (ready && ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) return false;
        Intent intent = new Intent(context, RecordingForegroundService.class)
                .setAction(ready ? ACTION_CAMERA_READY : ACTION_CAMERA_RELEASED);
        try {
            if (ready) ContextCompat.startForegroundService(context, intent);
            else context.startService(intent);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean dispatchForeground(Context context) {
        Intent intent = new Intent(context, RecordingForegroundService.class)
                .setAction(ACTION_STATE_CHANGED);
        try {
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static void dispatchStateChanged(Context context) {
        try {
            context.startService(new Intent(context, RecordingForegroundService.class)
                    .setAction(ACTION_STATE_CHANGED));
        } catch (RuntimeException ignored) {}
    }

    @Override public void onCreate() {
        super.onCreate();
        stateStore = new RecordingForegroundStateStore(this);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.recording_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.recording_notification_channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CAMERA_READY.equals(action)) {
            cameraReady = checkSelfPermission(android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
        } else if (ACTION_CAMERA_RELEASED.equals(action)) cameraReady = false;
        RecordingForegroundStateStore.Snapshot snapshot = stateStore.snapshot();
        return applyForegroundPolicy(snapshot, cameraReady,
                () -> stopForegroundAndSelf(startId),
                () -> {
                    if (cameraReady || snapshot.hasVideo()) acquireVideoWakeLock();
                    else releaseVideoWakeLock();
                    startForeground(snapshot, cameraReady);
                });
    }

    static int restartMode(boolean active) {
        return active ? START_STICKY : START_NOT_STICKY;
    }

    static boolean shouldKeepForeground(RecordingForegroundStateStore.Snapshot snapshot) {
        return shouldKeepForeground(snapshot, false);
    }

    static boolean shouldKeepForeground(
            RecordingForegroundStateStore.Snapshot snapshot, boolean cameraReady) {
        return cameraReady
                || snapshot != null && snapshot.isActive() && snapshot.needsRecovery();
    }

    static int applyForegroundPolicy(
            RecordingForegroundStateStore.Snapshot snapshot, Runnable stop, Runnable keep) {
        return applyForegroundPolicy(snapshot, false, stop, keep);
    }

    static int applyForegroundPolicy(RecordingForegroundStateStore.Snapshot snapshot,
            boolean cameraReady, Runnable stop, Runnable keep) {
        if (!shouldKeepForeground(snapshot, cameraReady)) {
            stop.run();
            return restartMode(false);
        }
        keep.run();
        return restartMode(true);
    }


    static boolean useCameraForegroundType(boolean cameraReady, boolean hasVideo,
            boolean cameraPermissionGranted) {
        return cameraPermissionGranted && (cameraReady || hasVideo);
    }

    private void startForeground(
            RecordingForegroundStateStore.Snapshot snapshot, boolean cameraReady) {
        boolean cameraStandby = cameraReady && !snapshot.isActive();
        boolean audioOnly = snapshot.hasAudio() && !snapshot.hasVideo();
        long startedAtMillis = snapshot.startedAtMillis();
        int title = cameraStandby ? R.string.camera_ready_notification_title
                : audioOnly ? R.string.audio_recording_status
                : R.string.recording_notification_title;
        int text = cameraStandby ? R.string.camera_ready_notification_text
                : audioOnly ? R.string.audio_recording_notification_text
                : R.string.recording_notification_text;
        Notification.Builder notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_recording_notification)
                .setContentTitle(getString(title))
                .setContentText(getString(text))
                .setWhen(startedAtMillis > 0L ? startedAtMillis : System.currentTimeMillis())
                .setUsesChronometer(!cameraStandby)
                .setShowWhen(!cameraStandby)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean cameraPermissionGranted = checkSelfPermission(android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED;
            int serviceType = useCameraForegroundType(
                    cameraReady, snapshot.hasVideo(), cameraPermissionGranted)
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA : 0;
            if ((snapshot.hasAudio() || snapshot.hasVideo())
                    && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(NOTIFICATION_ID, notification.build(), serviceType);
        } else {
            startForeground(NOTIFICATION_ID, notification.build());
        }
    }

    private void stopForegroundAndSelf(int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelfResult(startId);
    }

    @Override public void onDestroy() {
        cameraReady = false;
        releaseVideoWakeLock();
        super.onDestroy();
    }

    private void acquireVideoWakeLock() {
        if (videoWakeLock != null && videoWakeLock.isHeld()) return;
        PowerManager power = getSystemService(PowerManager.class);
        videoWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dcam:video-recording");
        videoWakeLock.setReferenceCounted(false);
        videoWakeLock.acquire();
    }

    private void releaseVideoWakeLock() {
        if (videoWakeLock != null && videoWakeLock.isHeld()) videoWakeLock.release();
        videoWakeLock = null;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}