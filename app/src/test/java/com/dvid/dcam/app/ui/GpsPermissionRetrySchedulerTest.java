package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

final class GpsPermissionRetrySchedulerTest {
    @Test void requestsAfterDelayOnlyWhileEligible() {
        FakeScheduler scheduler = new FakeScheduler();
        boolean[] eligible = { true };
        int[] requests = { 0 };
        GpsPermissionRetryScheduler retries = new GpsPermissionRetryScheduler(
                300_000L, scheduler, () -> eligible[0], () -> requests[0]++);

        retries.refresh();
        assertEquals(300_000L, scheduler.delayMillis);
        assertNotNull(scheduler.action);

        eligible[0] = false;
        retries.refresh();
        assertFalse(scheduler.scheduled);

        eligible[0] = true;
        retries.refresh();
        scheduler.runScheduled();
        assertEquals(1, requests[0]);
    }

    @Test void doesNotRestartFiveMinuteClockWhileAlreadyIdle() {
        FakeScheduler scheduler = new FakeScheduler();
        GpsPermissionRetryScheduler retries = new GpsPermissionRetryScheduler(
                300_000L, scheduler, () -> true, () -> { });

        retries.refresh();
        Runnable firstAction = scheduler.action;
        retries.refresh();

        assertEquals(firstAction, scheduler.action);
        assertEquals(1, scheduler.scheduleCount);
    }

    private static final class FakeScheduler implements GpsPermissionRetryScheduler.Scheduler {
        private Runnable action;
        private long delayMillis;
        private boolean scheduled;
        private int scheduleCount;

        @Override public void schedule(Runnable action, long delayMillis) {
            this.action = action;
            this.delayMillis = delayMillis;
            scheduled = true;
            scheduleCount++;
        }

        @Override public void cancel(Runnable action) {
            if (this.action == action) scheduled = false;
        }

        private void runScheduled() {
            scheduled = false;
            action.run();
        }
    }
}
