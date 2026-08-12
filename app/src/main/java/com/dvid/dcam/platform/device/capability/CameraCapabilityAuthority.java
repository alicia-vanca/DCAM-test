package com.dvid.dcam.platform.device.capability;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import java.util.Objects;
import java.util.Optional;

final class CameraCapabilityAuthority {
    enum State { LOADING, CURRENT, UNAVAILABLE }

    private State state = State.LOADING;
    private Snapshot snapshot;
    private String unavailableReason = "not_started";

    synchronized void loading() {
        state = State.LOADING;
        unavailableReason = "";
    }

    synchronized void current(Snapshot value) {
        snapshot = Objects.requireNonNull(value, "snapshot");
        state = State.CURRENT;
        unavailableReason = "";
    }

    synchronized void unavailable(String reason) {
        unavailableReason = required(reason);
        state = snapshot == null ? State.UNAVAILABLE : State.CURRENT;
    }

    synchronized State state() { return state; }

    synchronized Optional<Snapshot> snapshot() { return Optional.ofNullable(snapshot); }

    synchronized String unavailableReason() { return unavailableReason; }

    synchronized boolean hasCurrent() {
        return state == State.CURRENT && snapshot != null && !snapshot.cameras().isEmpty();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("reason is required");
        return value;
    }
}
