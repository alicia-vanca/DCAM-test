package com.dvid.dcam.platform.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.app.Service;
import android.content.SharedPreferences;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class RecordingForegroundStateStoreTest {
    @Test void stateSurvivesStoreRecreationAndTracksBothCaptureKinds() {
        SharedPreferences preferences = new InMemoryPreferences();
        RecordingForegroundStateStore first = new RecordingForegroundStateStore(preferences);

        assertTrue(first.startVideo("first.mp4", RecordingMode.VIDEO, 100L));
        assertTrue(first.startAudio("first.aac", 200L));
        assertTrue(first.startVideo("second.mp4", RecordingMode.IMP, 300L));

        RecordingForegroundStateStore.Snapshot restored =
                new RecordingForegroundStateStore(preferences).snapshot();
        assertTrue(restored.hasVideo());
        assertTrue(restored.hasAudio());
        assertEquals("second.mp4", restored.videoFileName());
        assertEquals(RecordingMode.IMP, restored.videoMode());
        assertEquals("first.aac", restored.audioFileName());
        assertEquals(100L, restored.startedAtMillis());

        assertTrue(first.markVideoFinalizing());
        assertFalse(first.snapshot().shouldRecoverVideo());
        assertEquals(RecordingMode.IDLE, first.snapshot().videoMode());

        assertTrue(first.stopVideo());
        RecordingForegroundStateStore.Snapshot audioOnly = first.snapshot();
        assertFalse(audioOnly.hasVideo());
        assertTrue(audioOnly.hasAudio());
        assertEquals(200L, audioOnly.startedAtMillis());

        assertTrue(first.stopAudio());
        assertFalse(first.snapshot().isActive());
    }

    @Test void legacySosModeRestoresAsImp() {
        SharedPreferences preferences = new InMemoryPreferences();
        preferences.edit()
                .putBoolean("video_active", true)
                .putBoolean("video_recoverable", true)
                .putString("video_mode", "SOS")
                .commit();

        RecordingForegroundStateStore.Snapshot snapshot =
                new RecordingForegroundStateStore(preferences).snapshot();

        assertEquals(RecordingMode.IMP, snapshot.videoMode());
    }

    @Test void startupMarksActiveCaptureForFinalizationWithoutRecovery() {
        SharedPreferences preferences = new InMemoryPreferences();
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(preferences);

        assertTrue(store.startVideo("unfinished.mp4", RecordingMode.IMP, 100L));
        assertTrue(store.startAudio("unfinished.aac", 200L));
        assertTrue(store.markUnfinishedCaptureFinalizing());

        RecordingForegroundStateStore.Snapshot snapshot = store.snapshot();
        assertTrue(snapshot.hasVideo());
        assertTrue(snapshot.hasAudio());
        assertFalse(snapshot.needsRecovery());
        assertFalse(snapshot.shouldRecoverVideo());
        assertFalse(snapshot.shouldRecoverAudio());
        assertEquals(RecordingMode.IDLE, snapshot.videoMode());
    }

    @Test void newVideoAfterInterruptedFinalizationUsesNewStartTime() {
        SharedPreferences preferences = new InMemoryPreferences();
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(preferences);

        assertTrue(store.startVideo("old.mp4", RecordingMode.VIDEO, 100L));
        assertTrue(store.markVideoFinalizing());
        assertTrue(store.startVideo("new.mp4", RecordingMode.VIDEO, 200L));

        RecordingForegroundStateStore.Snapshot snapshot = store.snapshot();
        assertTrue(snapshot.shouldRecoverVideo());
        assertEquals("new.mp4", snapshot.videoFileName());
        assertEquals(200L, snapshot.startedAtMillis());
    }

    @Test void finalizationCompletionDoesNotClearNewRecording() {
        SharedPreferences preferences = new InMemoryPreferences();
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(preferences);

        assertTrue(store.startVideo("old.mp4", RecordingMode.VIDEO, 100L));
        assertTrue(store.markVideoFinalizing());
        assertTrue(store.startVideo("new.mp4", RecordingMode.IMP, 200L));

        assertFalse(store.completeVideoFinalization());
        RecordingForegroundStateStore.Snapshot snapshot = store.snapshot();
        assertTrue(snapshot.hasVideo());
        assertTrue(snapshot.shouldRecoverVideo());
        assertEquals("new.mp4", snapshot.videoFileName());

        assertTrue(store.markVideoFinalizing());
        assertTrue(store.completeVideoFinalization());
        assertFalse(store.snapshot().hasVideo());
    }

    @Test void audioFinalizationDoesNotRestartOrClearNewAudio() {
        SharedPreferences preferences = new InMemoryPreferences();
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(preferences);

        assertTrue(store.startAudio("old.aac", 100L));
        assertTrue(store.markAudioFinalizing());
        assertFalse(store.snapshot().needsRecovery());
        assertTrue(store.startAudio("new.aac", 200L));

        assertFalse(store.completeAudioFinalization());
        RecordingForegroundStateStore.Snapshot snapshot = store.snapshot();
        assertTrue(snapshot.hasAudio());
        assertTrue(snapshot.shouldRecoverAudio());
        assertEquals("new.aac", snapshot.audioFileName());

        assertTrue(store.markAudioFinalizing());
        assertTrue(store.completeAudioFinalization());
        assertFalse(store.snapshot().hasAudio());
    }

    @Test void finalizingCaptureStopsForegroundServicePolicy() {
        SharedPreferences preferences = new InMemoryPreferences();
        RecordingForegroundStateStore store = new RecordingForegroundStateStore(preferences);
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicBoolean kept = new AtomicBoolean();

        assertTrue(store.startVideo("unfinished.mp4", RecordingMode.VIDEO, 100L));
        assertEquals(Service.START_STICKY, RecordingForegroundService.applyForegroundPolicy(
                store.snapshot(), () -> stopped.set(true), () -> kept.set(true)));
        assertFalse(stopped.get());
        assertTrue(kept.get());

        stopped.set(false);
        kept.set(false);
        assertTrue(store.markVideoFinalizing());
        assertEquals(Service.START_NOT_STICKY, RecordingForegroundService.applyForegroundPolicy(
                store.snapshot(), () -> stopped.set(true), () -> kept.set(true)));
        assertTrue(stopped.get());
        assertFalse(kept.get());
    }

    @Test void cameraReadyKeepsServiceStickyWithoutRecording() {
        RecordingForegroundStateStore store =
                new RecordingForegroundStateStore(new InMemoryPreferences());
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicBoolean kept = new AtomicBoolean();

        assertTrue(RecordingForegroundService.shouldKeepForeground(store.snapshot(), true));
        assertEquals(Service.START_STICKY, RecordingForegroundService.applyForegroundPolicy(
                store.snapshot(), true, () -> stopped.set(true), () -> kept.set(true)));
        assertFalse(stopped.get());
        assertTrue(kept.get());
        assertFalse(RecordingForegroundService.shouldKeepForeground(store.snapshot(), false));
    }

    @Test void cameraTypeRequiresCameraPermission() {
        assertTrue(RecordingForegroundService.useCameraForegroundType(true, false, true));
        assertTrue(RecordingForegroundService.useCameraForegroundType(false, true, true));
        assertFalse(RecordingForegroundService.useCameraForegroundType(true, true, false));
    }

    @Test void activeCaptureRequestsStickyServiceRestart() {
        assertEquals(Service.START_STICKY, RecordingForegroundService.restartMode(true));
        assertEquals(Service.START_NOT_STICKY, RecordingForegroundService.restartMode(false));
    }

    private static final class InMemoryPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override public Map<String, ?> getAll() { return Map.copyOf(values); }
        @Override public String getString(String key, String fallback) {
            return values.containsKey(key) ? (String) values.get(key) : fallback;
        }
        @SuppressWarnings("unchecked")
        @Override public Set<String> getStringSet(String key, Set<String> fallback) {
            return values.containsKey(key)
                    ? Set.copyOf((Set<String>) values.get(key)) : fallback;
        }
        @Override public int getInt(String key, int fallback) {
            return values.containsKey(key) ? (int) values.get(key) : fallback;
        }
        @Override public long getLong(String key, long fallback) {
            return values.containsKey(key) ? (long) values.get(key) : fallback;
        }
        @Override public float getFloat(String key, float fallback) {
            return values.containsKey(key) ? (float) values.get(key) : fallback;
        }
        @Override public boolean getBoolean(String key, boolean fallback) {
            return values.containsKey(key) ? (boolean) values.get(key) : fallback;
        }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { return new InMemoryEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}

        private final class InMemoryEditor implements Editor {
            private final Map<String, Object> updates = new HashMap<>();
            private final Set<String> removals = new HashSet<>();
            private boolean clear;

            @Override public Editor putString(String key, String value) {
                updates.put(key, value);
                return this;
            }
            @Override public Editor putStringSet(String key, Set<String> value) {
                updates.put(key, value == null ? null : Set.copyOf(value));
                return this;
            }
            @Override public Editor putInt(String key, int value) {
                updates.put(key, value);
                return this;
            }
            @Override public Editor putLong(String key, long value) {
                updates.put(key, value);
                return this;
            }
            @Override public Editor putFloat(String key, float value) {
                updates.put(key, value);
                return this;
            }
            @Override public Editor putBoolean(String key, boolean value) {
                updates.put(key, value);
                return this;
            }
            @Override public Editor remove(String key) {
                removals.add(key);
                return this;
            }
            @Override public Editor clear() {
                clear = true;
                return this;
            }
            @Override public boolean commit() {
                apply();
                return true;
            }
            @Override public void apply() {
                if (clear) values.clear();
                for (String key : removals) values.remove(key);
                values.putAll(updates);
            }
        }
    }
}