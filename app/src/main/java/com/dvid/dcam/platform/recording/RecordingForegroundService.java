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

/**
 * Keeps an active recording visible to Android and reduces background process risk.
 * Camera ownership remains in the CameraX adapter until recovery design is approved.
 */
public final class RecordingForegroundService extends Service {
    private static final String CHANNEL_ID = "dcam_recording";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_START_VIDEO = "dcam.recording.START_VIDEO";
    private static final String ACTION_START_AUDIO = "dcam.recording.START_AUDIO";
    private static final String ACTION_STOP_VIDEO = "dcam.recording.STOP_VIDEO";
    private static final String ACTION_STOP_AUDIO = "dcam.recording.STOP_AUDIO";
    private final RecordingForegroundOwners owners = RecordingForegroundOwners.processOwners();
    private PowerManager.WakeLock videoWakeLock;

    public static boolean startVideo(Context context, String fileName) {
        return start(context, ACTION_START_VIDEO);
    }

    public static boolean startAudio(Context context, String fileName) {
        return start(context, ACTION_START_AUDIO);
    }

    private static boolean start(Context context, String action) {
        Intent intent = new Intent(context, RecordingForegroundService.class)
                .setAction(action);
        try {
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    public static void stopVideo(Context context) {
        stop(context, ACTION_STOP_VIDEO);
    }

    public static void stopAudio(Context context) {
        stop(context, ACTION_STOP_AUDIO);
    }

    private static void stop(Context context, String action) {
        try {
            context.startService(new Intent(context, RecordingForegroundService.class).setAction(action));
        } catch (RuntimeException ignored) {}
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.recording_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.recording_notification_channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START_VIDEO.equals(action)) {
            owners.startVideo();
            acquireVideoWakeLock();
        }
        else if (ACTION_START_AUDIO.equals(action)) owners.startAudio();
        else if (ACTION_STOP_VIDEO.equals(action)) {
            owners.stopVideo();
            releaseVideoWakeLock();
        }
        else if (ACTION_STOP_AUDIO.equals(action)) owners.stopAudio();
        if (!owners.isActive()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        boolean audioOnly = owners.hasAudio() && !owners.hasVideo();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_recording_notification)
                .setContentTitle(getString(audioOnly
                        ? R.string.audio_recording_status
                        : R.string.recording_notification_title))
                .setContentText(getString(audioOnly
                        ? R.string.audio_recording_notification_text
                        : R.string.recording_notification_text))
                .setWhen(System.currentTimeMillis())
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int serviceType = owners.hasVideo()
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA : 0;
            if ((owners.hasAudio() || owners.hasVideo())
                    && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
            startForeground(NOTIFICATION_ID, notification, serviceType);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        releaseVideoWakeLock();
        super.onDestroy();
    }

    private void acquireVideoWakeLock() {
        if (videoWakeLock != null && videoWakeLock.isHeld()) return;
        PowerManager powerManager = getSystemService(PowerManager.class);
        videoWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "DCAM:video-recording");
        videoWakeLock.setReferenceCounted(false);
        videoWakeLock.acquire();
    }

    private void releaseVideoWakeLock() {
        if (videoWakeLock == null || !videoWakeLock.isHeld()) return;
        videoWakeLock.release();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
