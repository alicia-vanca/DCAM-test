package com.dvid.dcam.feature.capture.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.capture.application.port.AudioPreparationEvents;
import com.dvid.dcam.feature.capture.application.port.AudioRecorder;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class AudioRecordingUseCaseTest {
    @Test void asyncToggleRunsOnProvidedExecutorAndReportsStart() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        List<CaptureEvent> received = new ArrayList<>();
        SerializedRecordingCoordinator events = events(received);
        AudioRecordingUseCase useCase =
                new AudioRecordingUseCase(recorder, tasks::add, events);

        assertTrue(useCase.toggleAudioAsync());

        assertEquals(0, recorder.toggles);
        assertEquals(List.of(), received);
        tasks.remove().run();
        assertEquals(1, recorder.toggles);
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STARTED, received.get(0).getType());
        assertEquals("audio.aac", received.get(0).getFileName());
        assertEquals(123L, received.get(0).getStartedAtMillis());
    }

    @Test void queuedAudioToggleCountsAsPendingWork() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(
                recorder, tasks::add, events(new ArrayList<>()));

        assertTrue(useCase.toggleAudioAsync());
        assertTrue(useCase.hasPendingWork());

        tasks.remove().run();
        assertTrue(useCase.hasPendingWork());
    }

    @Test void asyncAudioCompletionNotifiesIdleObserversAfterPendingWorkClears() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(
                recorder, tasks::add, events(new ArrayList<>()));
        List<Boolean> pendingStates = new ArrayList<>();
        Runnable releaseObserver =
                useCase.observeStateChanges(() -> pendingStates.add(useCase.hasPendingWork()));

        assertTrue(useCase.toggleAudioAsync());
        tasks.remove().run();
        assertTrue(useCase.toggleAudioAsync());
        tasks.remove().run();
        releaseObserver.run();

        assertEquals(List.of(true, false), pendingStates);
    }

    @Test void asyncStopReportsSavingBeforeFinalizationTaskRuns() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        recorder.recording = true;
        List<CaptureEvent> received = new ArrayList<>();
        SerializedRecordingCoordinator events = events(received);
        events.audioRecordingStarted("audio.aac", 123L);
        received.clear();
        AudioRecordingUseCase useCase =
                new AudioRecordingUseCase(recorder, tasks::add, events);

        assertTrue(useCase.toggleAudioAsync());

        assertTrue(recorder.recording);
        assertEquals(1, received.size());
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STOPPING,
                received.get(0).getType());
        tasks.remove().run();
        assertFalse(recorder.recording);
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STOPPED,
                received.get(1).getType());
    }
    @Test void preparingAudioBlocksCurrentPressUntilFreshPress() {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        recorder.retryablePreparationFailures = 1;
        List<CaptureEvent> received = new ArrayList<>();
        FakePreparationListener listener = new FakePreparationListener();
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(recorder, Runnable::run,
                () -> true, events(received), listener);

        assertTrue(useCase.toggleAudioAsync());

        assertEquals(1, recorder.toggles);
        assertEquals(List.of("preparing:SD card is being prepared. Please wait."),
                listener.events);
        assertTrue(received.isEmpty());
        assertFalse(recorder.recording);

        assertTrue(useCase.toggleAudioAsync());

        assertEquals(2, recorder.toggles);
        assertEquals(List.of("preparing:SD card is being prepared. Please wait.", "cleared"),
                listener.events);
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STARTED, received.get(0).getType());
    }

    @Test void preparingAudioDoesNotRetryOrBecomeUnavailableAutomatically() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        recorder.alwaysRetryablePreparation = true;
        List<CaptureEvent> received = new ArrayList<>();
        FakePreparationListener listener = new FakePreparationListener();
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(recorder, tasks::add,
                () -> true, events(received), listener);

        assertTrue(useCase.toggleAudioAsync());
        tasks.remove().run();

        assertTrue(tasks.isEmpty());
        assertTrue(useCase.toggleAudioAsync());
        tasks.remove().run();

        assertEquals(2, recorder.toggles);
        assertEquals(List.of("preparing:SD card is being prepared. Please wait.",
                "preparing:SD card is being prepared. Please wait."), listener.events);
        assertTrue(received.isEmpty());
        assertFalse(recorder.recording);
    }

    @Test void terminalAudioStorageStateFailsWithoutRetry() {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        recorder.storageUnavailable = true;
        FakePreparationListener listener = new FakePreparationListener();
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(recorder, Runnable::run,
                () -> true, events(new ArrayList<>()), listener);

        assertTrue(useCase.toggleAudioAsync());

        assertEquals(1, recorder.toggles);
        assertEquals(List.of("unavailable:SD card unavailable."), listener.events);
        assertFalse(recorder.recording);
    }
    @Test void audioRequiresFreshPressAfterUnavailableAndPreparingStates() {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        recorder.storageUnavailable = true;
        FakePreparationListener listener = new FakePreparationListener();
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(recorder, Runnable::run,
                () -> true, events(new ArrayList<>()), listener);

        assertTrue(useCase.toggleAudioAsync());
        recorder.storageUnavailable = false;
        recorder.retryablePreparationFailures = 1;
        assertTrue(useCase.toggleAudioAsync());
        assertFalse(recorder.recording);
        assertTrue(useCase.toggleAudioAsync());

        assertEquals(List.of("unavailable:SD card unavailable.",
                "preparing:SD card is being prepared. Please wait.", "cleared"), listener.events);
        assertEquals(3, recorder.toggles);
        assertTrue(recorder.recording);
    }
    @Test void permissionGateBlocksStartButAllowsStop() {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        AtomicBoolean startAllowed = new AtomicBoolean(false);
        List<CaptureEvent> received = new ArrayList<>();
        SerializedRecordingCoordinator events = events(received);
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(
                recorder, Runnable::run, startAllowed::get, events);

        assertNull(useCase.toggleAudio());
        assertFalse(useCase.toggleAudioAsync());
        assertEquals(0, recorder.toggles);

        recorder.recording = true;
        events.audioRecordingStarted("audio.aac", 123L);
        received.clear();
        assertTrue(useCase.toggleAudioAsync());
        assertFalse(recorder.recording);
        assertEquals(1, recorder.toggles);
        assertEquals(2, received.size());
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STOPPING,
                received.get(0).getType());
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STOPPED,
                received.get(1).getType());
    }

    @Test void duplicateAsyncToggleSharesUseCaseSingleFlightState() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(
                recorder, tasks::add, events(new ArrayList<>()));

        assertTrue(useCase.toggleAudioAsync());
        assertFalse(useCase.toggleAudioAsync());
        tasks.remove().run();
        assertTrue(useCase.toggleAudioAsync());

        assertEquals(1, recorder.toggles);
    }

    @Test void synchronousToggleReportsStartAndStop() {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        List<CaptureEvent> received = new ArrayList<>();
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(recorder, events(received));

        assertEquals("audio.aac", useCase.toggleAudio());
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STARTED, received.get(0).getType());
        received.clear();

        assertEquals("audio.aac", useCase.toggleAudio());
        assertEquals(2, received.size());
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STOPPING,
                received.get(0).getType());
        assertEquals(CaptureEvent.Type.AUDIO_RECORDING_STOPPED,
                received.get(1).getType());
    }

    @Test void synchronousToggleRequiresFreshPressAfterPreparationBlock() {
        FakeAudioRecorder recorder = new FakeAudioRecorder();
        recorder.retryablePreparationFailures = 1;
        FakePreparationListener listener = new FakePreparationListener();
        AudioRecordingUseCase useCase = new AudioRecordingUseCase(recorder, Runnable::run,
                () -> true, events(new ArrayList<>()), listener);

        assertNull(useCase.toggleAudio());
        assertEquals("audio.aac", useCase.toggleAudio());

        assertEquals(2, recorder.toggles);
        assertEquals(List.of("preparing:SD card is being prepared. Please wait.", "cleared"),
                listener.events);
    }

    private static SerializedRecordingCoordinator events(List<CaptureEvent> received) {
        SerializedRecordingCoordinator events = new SerializedRecordingCoordinator(Runnable::run);
        events.setListener(received::add);
        received.clear();
        return events;
    }

    private static final class FakePreparationListener
            implements AudioPreparationEvents {
        private final List<String> events = new ArrayList<>();

        @Override public void onPreparing(String message) { events.add("preparing:" + message); }
        @Override public void onCleared() { events.add("cleared"); }
        @Override public void onUnavailable(String message) { events.add("unavailable:" + message); }
    }
    private static final class FakeAudioRecorder implements AudioRecorder {
        private int toggles;
        private boolean recording;
        private int retryablePreparationFailures;
        private boolean alwaysRetryablePreparation;
        private boolean storageUnavailable;

        @Override public String toggle() {
            toggles++;
            if (storageUnavailable) {
                throw AudioRecorder.PreparationException.unavailable("SD card unavailable.");
            }
            if (alwaysRetryablePreparation || retryablePreparationFailures > 0) {
                if (retryablePreparationFailures > 0) retryablePreparationFailures--;
                throw AudioRecorder.PreparationException.retryable(
                        "SD card is being prepared. Please wait.", "SD card unavailable.", null);
            }
            recording = !recording;
            return "audio.aac";
        }
        @Override public boolean isRecording() { return recording; }
        @Override public long recordingStartedAtMillis() { return recording ? 123L : -1L; }
        @Override public void release() {}
    }
}
