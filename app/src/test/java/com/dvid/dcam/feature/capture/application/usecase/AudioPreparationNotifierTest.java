package com.dvid.dcam.feature.capture.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.capture.application.port.AudioPreparationEvents;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AudioPreparationNotifierTest {
    @Test void bindingReplaysPersistentPreparingNotice() {
        AudioPreparationNotifier notifier = new AudioPreparationNotifier();
        notifier.onPreparing("SD card is being prepared. Please wait.");
        FakeEvents events = new FakeEvents();

        notifier.bind(events);

        assertEquals(List.of("preparing:SD card is being prepared. Please wait."), events.values);
    }

    @Test void unavailableReplacesPreparingAndPersistsUntilCleared() {
        AudioPreparationNotifier notifier = new AudioPreparationNotifier();
        FakeEvents first = new FakeEvents();
        notifier.bind(first);
        notifier.onPreparing("SD card is being prepared. Please wait.");
        notifier.onUnavailable("SD card unavailable.");
        FakeEvents rebound = new FakeEvents();

        notifier.bind(rebound);
        notifier.onCleared();

        assertEquals(List.of("preparing:SD card is being prepared. Please wait.",
                "unavailable:SD card unavailable."), first.values);
        assertEquals(List.of("unavailable:SD card unavailable.", "cleared"), rebound.values);
    }

    @Test void closingBindingStopsDelayedNoticeDeliveryToReleasedPreview() {
        AudioPreparationNotifier notifier = new AudioPreparationNotifier();
        FakeEvents released = new FakeEvents();
        AudioPreparationEvents.Binding binding = notifier.bind(released);

        binding.close();
        notifier.onPreparing("SD card is being prepared. Please wait.");

        assertEquals(List.of(), released.values);
    }

    @Test void closingOldBindingDoesNotUnbindReplacementPreview() {
        AudioPreparationNotifier notifier = new AudioPreparationNotifier();
        FakeEvents oldPreview = new FakeEvents();
        AudioPreparationEvents.Binding oldBinding = notifier.bind(oldPreview);
        FakeEvents currentPreview = new FakeEvents();
        notifier.bind(currentPreview);

        oldBinding.close();
        notifier.onUnavailable("SD card unavailable.");

        assertEquals(List.of(), oldPreview.values);
        assertEquals(List.of("unavailable:SD card unavailable."), currentPreview.values);
    }

    private static final class FakeEvents implements AudioPreparationEvents {
        private final List<String> values = new ArrayList<>();

        @Override public void onPreparing(String message) { values.add("preparing:" + message); }
        @Override public void onCleared() { values.add("cleared"); }
        @Override public void onUnavailable(String message) { values.add("unavailable:" + message); }
    }
}