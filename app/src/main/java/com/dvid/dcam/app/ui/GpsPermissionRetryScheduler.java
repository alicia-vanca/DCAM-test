package com.dvid.dcam.app.ui;

import java.util.function.BooleanSupplier;

public final class GpsPermissionRetryScheduler {
    public interface Scheduler {
        void schedule(Runnable action, long delayMillis);
        void cancel(Runnable action);
    }

    private final long delayMillis;
    private final Scheduler scheduler;
    private final BooleanSupplier eligible;
    private final Runnable permissionRequest;
    private final Runnable retry = this::runRetry;
    private boolean scheduled;

    public GpsPermissionRetryScheduler(long delayMillis, Scheduler scheduler,
            BooleanSupplier eligible, Runnable permissionRequest) {
        this.delayMillis = delayMillis;
        this.scheduler = scheduler;
        this.eligible = eligible;
        this.permissionRequest = permissionRequest;
    }

    public void refresh() {
        if (!eligible.getAsBoolean()) {
            stop();
            return;
        }
        if (scheduled) return;
        scheduled = true;
        scheduler.schedule(retry, delayMillis);
    }

    public void stop() {
        if (!scheduled) return;
        scheduled = false;
        scheduler.cancel(retry);
    }

    private void runRetry() {
        scheduled = false;
        if (eligible.getAsBoolean()) permissionRequest.run();
    }
}
