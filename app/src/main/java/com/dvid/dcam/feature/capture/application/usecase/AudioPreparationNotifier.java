package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.application.port.AudioPreparationEvents;
import java.util.Objects;

public final class AudioPreparationNotifier implements AudioPreparationEvents {
    private static final AudioPreparationEvents NO_EVENTS = new AudioPreparationEvents() {
        @Override public void onPreparing(String message) {}
        @Override public void onCleared() {}
        @Override public void onUnavailable(String message) {}
    };

    private AudioPreparationEvents events = NO_EVENTS;
    private String message;
    private boolean unavailable;

    public synchronized AudioPreparationEvents.Binding bind(AudioPreparationEvents events) {
        AudioPreparationEvents checked = Objects.requireNonNull(events, "events");
        this.events = checked;
        if (message != null) {
            if (unavailable) checked.onUnavailable(message);
            else checked.onPreparing(message);
        }
        return () -> unbind(checked);
    }

    private synchronized void unbind(AudioPreparationEvents expected) {
        if (events == expected) events = NO_EVENTS;
    }

    @Override public synchronized void onPreparing(String message) {
        this.message = message;
        unavailable = false;
        events.onPreparing(message);
    }

    @Override public synchronized void onCleared() {
        if (message == null) return;
        message = null;
        unavailable = false;
        events.onCleared();
    }

    @Override public synchronized void onUnavailable(String message) {
        this.message = message;
        unavailable = true;
        events.onUnavailable(message);
    }
}