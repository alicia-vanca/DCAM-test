package com.dvid.dcam.feature.capture.application.usecase;

import com.dvid.dcam.feature.capture.application.port.AudioPreparationEvents;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class CaptureStorageNoticeMonitor implements AudioPreparationEvents {
    static final long POLL_INTERVAL_MILLIS = 1_000L;
    static final long PREPARING_TIMEOUT_MILLIS = 10_000L;

    public enum State { READY, PREPARING, UNAVAILABLE }

    public record Status(State state, String message) {
        public Status {
            Objects.requireNonNull(state, "state");
            message = message == null ? "" : message;
        }

        public static Status ready() { return new Status(State.READY, ""); }
        public static Status preparing(String message) {
            return new Status(State.PREPARING, message);
        }
        public static Status unavailable(String message) {
            return new Status(State.UNAVAILABLE, message);
        }
    }

    private final Supplier<Status> statusSource;
    private final BiConsumer<Runnable, Long> delayedExecutor;
    private final AudioPreparationEvents events;
    private final String unavailableMessage;
    private final LongSupplier currentTimeMillis;
    private Status visible = Status.ready();
    private long preparingDeadlineMillis;
    private long generation;
    private boolean evaluated;
    private boolean monitoring = true;
    private boolean pollScheduled;

    public CaptureStorageNoticeMonitor(
            Supplier<Status> statusSource,
            BiConsumer<Runnable, Long> delayedExecutor,
            AudioPreparationEvents events,
            String unavailableMessage) {
        this(statusSource, delayedExecutor, events, unavailableMessage,
                System::currentTimeMillis);
    }

    CaptureStorageNoticeMonitor(
            Supplier<Status> statusSource,
            BiConsumer<Runnable, Long> delayedExecutor,
            AudioPreparationEvents events,
            String unavailableMessage,
            LongSupplier currentTimeMillis) {
        this.statusSource = Objects.requireNonNull(statusSource, "statusSource");
        this.delayedExecutor = Objects.requireNonNull(delayedExecutor, "delayedExecutor");
        this.events = Objects.requireNonNull(events, "events");
        this.unavailableMessage = Objects.requireNonNull(
                unavailableMessage, "unavailableMessage");
        this.currentTimeMillis = Objects.requireNonNull(
                currentTimeMillis, "currentTimeMillis");
    }

    @Override public synchronized void onPreparing(String message) {
        if (visible.state() == State.UNAVAILABLE) {
            schedulePoll();
            return;
        }
        if (visible.state() == State.READY) {
            if (evaluated) generation++;
            preparingDeadlineMillis = currentTimeMillis.getAsLong()
                    + PREPARING_TIMEOUT_MILLIS;
        }
        Status next = Status.preparing(message);
        if (!next.equals(visible)) {
            visible = next;
            events.onPreparing(message);
        }
        schedulePoll();
    }

    @Override public synchronized void onUnavailable(String message) {
        if (evaluated && visible.state() == State.READY) generation++;
        Status next = Status.unavailable(message);
        if (!next.equals(visible)) {
            visible = next;
            events.onUnavailable(message);
        }
        schedulePoll();
    }

    @Override public synchronized void onCleared() {
        schedulePoll();
    }

    private void clearVisible() {
        evaluated = true;
        if (visible.state() == State.READY) return;
        visible = Status.ready();
        generation++;
        pollScheduled = false;
        events.onCleared();
    }

    public synchronized boolean isUnavailable() {
        return !evaluated || visible.state() == State.UNAVAILABLE;
    }

    public synchronized void invalidate() {
        monitoring = true;
        evaluated = false;
        generation++;
        pollScheduled = false;
    }

    public synchronized void pause() {
        monitoring = false;
        evaluated = false;
        generation++;
        pollScheduled = false;
    }

    public void evaluate() {
        long expectedGeneration;
        synchronized (this) {
            if (!monitoring) return;
            expectedGeneration = generation;
        }
        Status current;
        try {
            current = Objects.requireNonNull(statusSource.get(), "storage status");
        } catch (RuntimeException ignored) {
            applyEvaluationFailure(expectedGeneration);
            return;
        }
        applyEvaluation(expectedGeneration, current);
    }

    private synchronized void applyEvaluation(
            long expectedGeneration, Status current) {
        if (!monitoring || expectedGeneration != generation) return;
        evaluated = true;
        switch (current.state()) {
            case READY -> clearVisible();
            case PREPARING -> onPreparing(current.message());
            case UNAVAILABLE -> onUnavailable(current.message());
        }
    }

    private synchronized void applyEvaluationFailure(long expectedGeneration) {
        if (!monitoring || expectedGeneration != generation) return;
        evaluated = true;
        onUnavailable(unavailableMessage);
    }

    private void refresh(long expectedGeneration) {
        synchronized (this) {
            if (!monitoring || expectedGeneration != generation
                    || visible.state() == State.READY) return;
            pollScheduled = false;
        }
        Status current;
        try {
            current = Objects.requireNonNull(statusSource.get(), "storage status");
        } catch (RuntimeException ignored) {
            rescheduleAfterFailure(expectedGeneration);
            return;
        }
        applyRefresh(expectedGeneration, current);
    }

    private synchronized void rescheduleAfterFailure(long expectedGeneration) {
        if (!monitoring || expectedGeneration != generation
                || visible.state() == State.READY) return;
        if (visible.state() == State.PREPARING
                && currentTimeMillis.getAsLong() >= preparingDeadlineMillis) {
            visible = Status.unavailable(unavailableMessage);
            evaluated = true;
            events.onUnavailable(unavailableMessage);
        }
        schedulePoll();
    }

    private synchronized void applyRefresh(
            long expectedGeneration, Status current) {
        if (!monitoring || expectedGeneration != generation
                || visible.state() == State.READY) return;
        evaluated = true;
        if (current.state() == State.READY) {
            clearVisible();
            return;
        }
        if (visible.state() == State.UNAVAILABLE
                || current.state() == State.UNAVAILABLE
                || currentTimeMillis.getAsLong() >= preparingDeadlineMillis) {
            String message = current.state() == State.UNAVAILABLE && !current.message().isEmpty()
                    ? current.message() : unavailableMessage;
            Status next = Status.unavailable(message);
            if (!next.equals(visible)) {
                visible = next;
                events.onUnavailable(message);
            }
        } else {
            Status next = Status.preparing(current.message());
            if (!next.equals(visible)) {
                visible = next;
                events.onPreparing(current.message());
            }
        }
        schedulePoll();
    }

    private synchronized void schedulePoll() {
        if (!monitoring || pollScheduled || visible.state() == State.READY) return;
        pollScheduled = true;
        long expectedGeneration = generation;
        delayedExecutor.accept(
                () -> refresh(expectedGeneration), POLL_INTERVAL_MILLIS);
    }
}