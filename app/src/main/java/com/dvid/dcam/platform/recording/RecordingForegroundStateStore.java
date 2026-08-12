package com.dvid.dcam.platform.recording;

import android.content.Context;
import android.content.SharedPreferences;
import com.dvid.dcam.feature.capture.domain.RecordingMode;

/** Durable foreground-capture state used when Android recreates the app process. */
final class RecordingForegroundStateStore {
    private static final String PREFERENCES = "dcam_recording_foreground";
    private static final String VIDEO_ACTIVE = "video_active";
    private static final String VIDEO_RECOVERABLE = "video_recoverable";
    private static final String VIDEO_MODE = "video_mode";
    private static final String VIDEO_FILE_NAME = "video_file_name";
    private static final String VIDEO_STARTED_AT = "video_started_at";
    private static final String AUDIO_ACTIVE = "audio_active";
    private static final String AUDIO_RECOVERABLE = "audio_recoverable";
    private static final String AUDIO_FILE_NAME = "audio_file_name";
    private static final String AUDIO_STARTED_AT = "audio_started_at";

    private static final Object STATE_LOCK = new Object();
    private final SharedPreferences preferences;

    RecordingForegroundStateStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE));
    }

    RecordingForegroundStateStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    boolean startVideo(String fileName, RecordingMode mode, long startedAtMillis) {
        synchronized (STATE_LOCK) {
            boolean continuesActiveCapture = preferences.getBoolean(VIDEO_ACTIVE, false)
                    && preferences.getBoolean(VIDEO_RECOVERABLE, false);
            long persistedStart = continuesActiveCapture
                    ? preferences.getLong(VIDEO_STARTED_AT, startedAtMillis) : startedAtMillis;
            return preferences.edit()
                    .putBoolean(VIDEO_ACTIVE, true)
                    .putBoolean(VIDEO_RECOVERABLE, true)
                    .putString(VIDEO_MODE, videoMode(mode).name())
                    .putString(VIDEO_FILE_NAME, value(fileName))
                    .putLong(VIDEO_STARTED_AT, persistedStart)
                    .commit();
        }
    }

    boolean updateVideoMode(RecordingMode mode) {
        synchronized (STATE_LOCK) {
            if (!preferences.getBoolean(VIDEO_ACTIVE, false)) return false;
            return preferences.edit()
                    .putBoolean(VIDEO_RECOVERABLE, true)
                    .putString(VIDEO_MODE, videoMode(mode).name())
                    .commit();
        }
    }

    boolean markVideoFinalizing() {
        synchronized (STATE_LOCK) {
            if (!preferences.getBoolean(VIDEO_ACTIVE, false)) return false;
            return preferences.edit().putBoolean(VIDEO_RECOVERABLE, false).commit();
        }
    }

    boolean completeVideoFinalization() {
        synchronized (STATE_LOCK) {
            if (!preferences.getBoolean(VIDEO_ACTIVE, false)
                    || preferences.getBoolean(VIDEO_RECOVERABLE, true)) return false;
            return stopVideo();
        }
    }

    boolean markAudioFinalizing() {
        synchronized (STATE_LOCK) {
            if (!preferences.getBoolean(AUDIO_ACTIVE, false)) return false;
            return preferences.edit().putBoolean(AUDIO_RECOVERABLE, false).commit();
        }
    }

    boolean markUnfinishedCaptureFinalizing() {
        synchronized (STATE_LOCK) {
            boolean video = preferences.getBoolean(VIDEO_ACTIVE, false);
            boolean audio = preferences.getBoolean(AUDIO_ACTIVE, false);
            if (!video && !audio) return false;
            SharedPreferences.Editor editor = preferences.edit();
            if (video) editor.putBoolean(VIDEO_RECOVERABLE, false);
            if (audio) editor.putBoolean(AUDIO_RECOVERABLE, false);
            return editor.commit();
        }
    }

    boolean completeAudioFinalization() {
        synchronized (STATE_LOCK) {
            if (!preferences.getBoolean(AUDIO_ACTIVE, false)
                    || preferences.getBoolean(AUDIO_RECOVERABLE, true)) return false;
            return stopAudio();
        }
    }

    boolean startAudio(String fileName, long startedAtMillis) {
        synchronized (STATE_LOCK) {
            long persistedStart = preferences.getBoolean(AUDIO_ACTIVE, false)
                    ? preferences.getLong(AUDIO_STARTED_AT, startedAtMillis) : startedAtMillis;
            return preferences.edit()
                    .putBoolean(AUDIO_ACTIVE, true)
                    .putBoolean(AUDIO_RECOVERABLE, true)
                    .putString(AUDIO_FILE_NAME, value(fileName))
                    .putLong(AUDIO_STARTED_AT, persistedStart)
                    .commit();
        }
    }

    boolean stopVideo() {
        synchronized (STATE_LOCK) {
            boolean wasActive = preferences.getBoolean(VIDEO_ACTIVE, false);
            preferences.edit()
                    .remove(VIDEO_ACTIVE)
                    .remove(VIDEO_RECOVERABLE)
                    .remove(VIDEO_MODE)
                    .remove(VIDEO_FILE_NAME)
                    .remove(VIDEO_STARTED_AT)
                    .commit();
            return wasActive;
        }
    }

    boolean stopAudio() {
        synchronized (STATE_LOCK) {
            boolean wasActive = preferences.getBoolean(AUDIO_ACTIVE, false);
            preferences.edit()
                    .remove(AUDIO_ACTIVE)
                    .remove(AUDIO_RECOVERABLE)
                    .remove(AUDIO_FILE_NAME)
                    .remove(AUDIO_STARTED_AT)
                    .commit();
            return wasActive;
        }
    }

    Snapshot snapshot() {
        boolean video = preferences.getBoolean(VIDEO_ACTIVE, false);
        boolean videoRecoverable = video && preferences.getBoolean(VIDEO_RECOVERABLE, false);
        return new Snapshot(
                video,
                videoRecoverable,
                storedVideoMode(),
                preferences.getString(VIDEO_FILE_NAME, ""),
                preferences.getLong(VIDEO_STARTED_AT, -1L),
                preferences.getBoolean(AUDIO_ACTIVE, false),
                preferences.getBoolean(AUDIO_ACTIVE, false)
                        && preferences.getBoolean(AUDIO_RECOVERABLE, false),
                preferences.getString(AUDIO_FILE_NAME, ""),
                preferences.getLong(AUDIO_STARTED_AT, -1L));
    }

    private RecordingMode storedVideoMode() {
        String stored = preferences.getString(VIDEO_MODE, RecordingMode.VIDEO.name());
        if ("SOS".equals(stored)) return RecordingMode.IMP;
        try {
            return videoMode(RecordingMode.valueOf(stored));
        } catch (IllegalArgumentException error) {
            return RecordingMode.VIDEO;
        }
    }

    private static RecordingMode videoMode(RecordingMode mode) {
        return mode == RecordingMode.IMP ? RecordingMode.IMP : RecordingMode.VIDEO;
    }

    private static String value(String value) { return value == null ? "" : value; }

    static final class Snapshot {
        private final boolean video;
        private final boolean videoRecoverable;
        private final RecordingMode videoMode;
        private final String videoFileName;
        private final long videoStartedAtMillis;
        private final boolean audio;
        private final boolean audioRecoverable;
        private final String audioFileName;
        private final long audioStartedAtMillis;

        private Snapshot(
                boolean video,
                boolean videoRecoverable,
                RecordingMode videoMode,
                String videoFileName,
                long videoStartedAtMillis,
                boolean audio,
                boolean audioRecoverable,
                String audioFileName,
                long audioStartedAtMillis) {
            this.video = video;
            this.videoRecoverable = videoRecoverable;
            this.videoMode = videoMode;
            this.videoFileName = videoFileName;
            this.videoStartedAtMillis = videoStartedAtMillis;
            this.audio = audio;
            this.audioRecoverable = audioRecoverable;
            this.audioFileName = audioFileName;
            this.audioStartedAtMillis = audioStartedAtMillis;
        }

        boolean hasVideo() { return video; }
        boolean shouldRecoverVideo() { return videoRecoverable; }
        RecordingMode videoMode() {
            return videoRecoverable ? videoMode : RecordingMode.IDLE;
        }
        String videoFileName() { return videoFileName; }
        boolean hasAudio() { return audio; }
        boolean shouldRecoverAudio() { return audioRecoverable; }
        String audioFileName() { return audioFileName; }
        boolean isActive() { return video || audio; }
        boolean needsRecovery() { return videoRecoverable || audioRecoverable; }

        long startedAtMillis() {
            long videoStart = video && videoStartedAtMillis > 0L
                    ? videoStartedAtMillis : Long.MAX_VALUE;
            long audioStart = audio && audioStartedAtMillis > 0L
                    ? audioStartedAtMillis : Long.MAX_VALUE;
            long earliest = Math.min(videoStart, audioStart);
            return earliest == Long.MAX_VALUE ? -1L : earliest;
        }
    }
}