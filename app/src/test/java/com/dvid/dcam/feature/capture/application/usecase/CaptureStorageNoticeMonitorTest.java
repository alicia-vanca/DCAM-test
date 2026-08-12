package com.dvid.dcam.feature.capture.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.dvid.dcam.feature.capture.application.port.AudioPreparationEvents;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class CaptureStorageNoticeMonitorTest {
    @Test void readyStorageClearsPersistentPreparingNotice() {
        AtomicReference<CaptureStorageNoticeMonitor.Status> status = new AtomicReference<>(
                CaptureStorageNoticeMonitor.Status.preparing("preparing"));
        Queue<Runnable> polls = new ArrayDeque<>();
        FakeEvents events = new FakeEvents();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                status::get, (action, delay) -> polls.add(action), events,
                "unavailable", () -> 0L);

        monitor.onPreparing("preparing");
        status.set(CaptureStorageNoticeMonitor.Status.ready());
        polls.remove().run();

        assertEquals(List.of("preparing:preparing", "cleared"), events.values);
        assertTrue(polls.isEmpty());
    }

    @Test void repeatedPreparingPressDoesNotRestartTimeout() {
        AtomicLong clock = new AtomicLong();
        Queue<Runnable> polls = new ArrayDeque<>();
        FakeEvents events = new FakeEvents();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                () -> CaptureStorageNoticeMonitor.Status.preparing("preparing"),
                (action, delay) -> polls.add(action), events,
                "unavailable", clock::get);

        monitor.onPreparing("preparing");
        clock.set(9_000L);
        polls.remove().run();
        monitor.onPreparing("preparing");
        clock.set(10_000L);
        polls.remove().run();

        assertEquals(List.of("preparing:preparing", "unavailable:unavailable"),
                events.values);
        assertEquals(1, polls.size());
    }

    @Test void unavailableNoticePersistsUntilStorageIsReady() {
        AtomicReference<CaptureStorageNoticeMonitor.Status> status = new AtomicReference<>(
                CaptureStorageNoticeMonitor.Status.preparing("preparing"));
        Queue<Runnable> polls = new ArrayDeque<>();
        FakeEvents events = new FakeEvents();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                status::get, (action, delay) -> polls.add(action), events,
                "unavailable", () -> 0L);

        monitor.onUnavailable("unavailable");
        polls.remove().run();
        status.set(CaptureStorageNoticeMonitor.Status.ready());
        polls.remove().run();

        assertEquals(List.of("unavailable:unavailable", "cleared"), events.values);
        assertTrue(polls.isEmpty());
    }

    @Test void externalCaptureStartsBlockedUntilFirstEvaluation() {
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                CaptureStorageNoticeMonitor.Status::ready,
                (action, delay) -> {}, new FakeEvents(),
                "unavailable", () -> 0L);

        assertTrue(monitor.isUnavailable());
        monitor.evaluate();
        assertFalse(monitor.isUnavailable());
    }

    @Test void evaluateShowsUnavailableWithoutCaptureAttemptAndClearsWhenReady() {
        AtomicReference<CaptureStorageNoticeMonitor.Status> status = new AtomicReference<>(
                CaptureStorageNoticeMonitor.Status.ready());
        Queue<Runnable> polls = new ArrayDeque<>();
        FakeEvents events = new FakeEvents();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                status::get, (action, delay) -> polls.add(action), events,
                "unavailable", () -> 0L);

        status.set(CaptureStorageNoticeMonitor.Status.unavailable("unavailable"));
        monitor.evaluate();
        assertTrue(monitor.isUnavailable());
        status.set(CaptureStorageNoticeMonitor.Status.ready());
        monitor.evaluate();
        assertFalse(monitor.isUnavailable());
        polls.remove().run();

        assertEquals(List.of("unavailable:unavailable", "cleared"), events.values);
        assertTrue(polls.isEmpty());
    }

    @Test void preparingCallbackCannotOverrideInvalidatedFailClosedState() {
        AtomicReference<CaptureStorageNoticeMonitor.Status> status = new AtomicReference<>(
                CaptureStorageNoticeMonitor.Status.ready());
        Queue<Runnable> polls = new ArrayDeque<>();
        FakeEvents events = new FakeEvents();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                status::get, (action, delay) -> polls.add(action), events,
                "unavailable", () -> 0L);

        monitor.evaluate();
        monitor.invalidate();
        status.set(CaptureStorageNoticeMonitor.Status.unavailable("unavailable"));
        monitor.onPreparing("preparing");

        assertTrue(monitor.isUnavailable());
        monitor.evaluate();
        assertTrue(monitor.isUnavailable());
        assertEquals(List.of("preparing:preparing", "unavailable:unavailable"),
                events.values);
    }

    @Test void lateClearedCallbackCannotOverrideUnavailableEvaluation() {
        AtomicReference<CaptureStorageNoticeMonitor.Status> status = new AtomicReference<>(
                CaptureStorageNoticeMonitor.Status.unavailable("unavailable"));
        Queue<Runnable> polls = new ArrayDeque<>();
        FakeEvents events = new FakeEvents();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                status::get, (action, delay) -> polls.add(action), events,
                "unavailable", () -> 0L);

        monitor.evaluate();
        monitor.onCleared();

        assertTrue(monitor.isUnavailable());
        assertEquals(List.of("unavailable:unavailable"), events.values);
        status.set(CaptureStorageNoticeMonitor.Status.ready());
        polls.remove().run();
        assertFalse(monitor.isUnavailable());
        assertEquals(List.of("unavailable:unavailable", "cleared"), events.values);
    }

    @Test void lateClearedCallbackAfterPauseDoesNotReopenCapture() {
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                CaptureStorageNoticeMonitor.Status::ready,
                (action, delay) -> {}, new FakeEvents(),
                "unavailable", () -> 0L);

        monitor.pause();
        monitor.onCleared();

        assertTrue(monitor.isUnavailable());
    }

    @Test void evaluateFailureBlocksCaptureAndRetries() {
        Queue<Runnable> polls = new ArrayDeque<>();
        FakeEvents events = new FakeEvents();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                () -> { throw new IllegalStateException("failed"); },
                (action, delay) -> polls.add(action), events,
                "unavailable", () -> 0L);

        monitor.evaluate();

        assertTrue(monitor.isUnavailable());
        assertEquals(List.of("unavailable:unavailable"), events.values);
        assertEquals(1, polls.size());
    }

    @Test void pauseCancelsUnavailablePolling() {
        Queue<Runnable> polls = new ArrayDeque<>();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                () -> CaptureStorageNoticeMonitor.Status.unavailable("unavailable"),
                (action, delay) -> polls.add(action), new FakeEvents(),
                "unavailable", () -> 0L);

        monitor.evaluate();
        monitor.pause();
        polls.remove().run();

        assertTrue(polls.isEmpty());
        assertTrue(monitor.isUnavailable());
    }

    @Test void queuedEvaluateAfterPauseDoesNotReadStorageOrRestartPolling() {
        AtomicLong reads = new AtomicLong();
        Queue<Runnable> polls = new ArrayDeque<>();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                () -> {
                    reads.incrementAndGet();
                    return CaptureStorageNoticeMonitor.Status.unavailable("unavailable");
                }, (action, delay) -> polls.add(action), new FakeEvents(),
                "unavailable", () -> 0L);
        Runnable queuedEvaluation = monitor::evaluate;

        monitor.pause();
        queuedEvaluation.run();

        assertEquals(0L, reads.get());
        assertTrue(polls.isEmpty());
        assertTrue(monitor.isUnavailable());
    }

    @Test void lateUnavailableCallbackAfterPauseDoesNotRestartPolling() {
        Queue<Runnable> polls = new ArrayDeque<>();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                CaptureStorageNoticeMonitor.Status::ready,
                (action, delay) -> polls.add(action), new FakeEvents(),
                "unavailable", () -> 0L);

        monitor.pause();
        monitor.onUnavailable("unavailable");

        assertTrue(polls.isEmpty());
    }

    @Test void failedPreparingPollExpiresToUnavailableAtTimeout() {
        AtomicLong clock = new AtomicLong();
        Queue<Runnable> polls = new ArrayDeque<>();
        FakeEvents events = new FakeEvents();
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                () -> { throw new IllegalStateException("failed"); },
                (action, delay) -> polls.add(action), events,
                "unavailable", clock::get);

        monitor.onPreparing("preparing");
        clock.set(10_000L);
        polls.remove().run();

        assertTrue(monitor.isUnavailable());
        assertEquals(List.of("preparing:preparing", "unavailable:unavailable"),
                events.values);
        assertEquals(1, polls.size());
    }

    @Test void storageReadDoesNotHoldMonitorStateLock() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CaptureStorageNoticeMonitor monitor = new CaptureStorageNoticeMonitor(
                () -> {
                    entered.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("storage read timed out");
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                    return CaptureStorageNoticeMonitor.Status.ready();
                }, (action, delay) -> {}, new FakeEvents(),
                "unavailable", () -> 0L);
        Thread worker = new Thread(monitor::evaluate);
        worker.start();
        assertTrue(entered.await(1, TimeUnit.SECONDS));

        assertTimeoutPreemptively(Duration.ofMillis(250), monitor::isUnavailable);
        release.countDown();
        worker.join(2_000L);
        assertFalse(worker.isAlive());
    }

    private static final class FakeEvents implements AudioPreparationEvents {
        private final List<String> values = new ArrayList<>();

        @Override public void onPreparing(String message) { values.add("preparing:" + message); }
        @Override public void onCleared() { values.add("cleared"); }
        @Override public void onUnavailable(String message) {
            values.add("unavailable:" + message);
        }
    }
}