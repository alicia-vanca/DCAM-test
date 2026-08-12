package com.dvid.dcam.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

final class CameraCapabilityRecheckNotifier {
    interface Subscription {
        void close();
    }

    private final Executor executor;
    private final Object lock = new Object();
    private final List<AppComposition.CameraCapabilityRecheckObserver> observers =
            new ArrayList<>();
    private AppComposition.CameraCapabilityRecheckUpdate pendingTerminal;
    private long publicationGeneration;

    CameraCapabilityRecheckNotifier(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    Subscription observe(
            AppComposition.CameraCapabilityRecheckObserver observer,
            Supplier<AppComposition.CameraCapabilityRecheckUpdate> activeUpdate) {
        AppComposition.CameraCapabilityRecheckObserver checked =
                Objects.requireNonNull(observer, "observer");
        Supplier<AppComposition.CameraCapabilityRecheckUpdate> checkedActive =
                Objects.requireNonNull(activeUpdate, "activeUpdate");
        AppComposition.CameraCapabilityRecheckUpdate replay;
        long replayGeneration;
        synchronized (lock) {
            observers.add(checked);
            replay = pendingTerminal;
            replayGeneration = publicationGeneration;
        }
        if (replay == null) replay = checkedActive.get();
        if (replay != null) deliverReplay(checked, replay, replayGeneration);
        return () -> {
            synchronized (lock) {
                observers.remove(checked);
            }
        };
    }

    void acknowledgeTerminal() {
        synchronized (lock) {
            pendingTerminal = null;
        }
    }

    void publish(AppComposition.CameraCapabilityRecheckUpdate update) {
        Objects.requireNonNull(update, "update");
        List<AppComposition.CameraCapabilityRecheckObserver> current;
        synchronized (lock) {
            publicationGeneration++;
            if (terminal(update)) pendingTerminal = update;
            else pendingTerminal = null;
            current = List.copyOf(observers);
        }
        for (AppComposition.CameraCapabilityRecheckObserver observer : current) {
            deliverPublished(observer, update);
        }
    }

    private void deliverPublished(
            AppComposition.CameraCapabilityRecheckObserver observer,
            AppComposition.CameraCapabilityRecheckUpdate update) {
        executor.execute(() -> deliverIfObserved(observer, update));
    }

    private void deliverReplay(
            AppComposition.CameraCapabilityRecheckObserver observer,
            AppComposition.CameraCapabilityRecheckUpdate update,
            long replayGeneration) {
        executor.execute(() -> {
            synchronized (lock) {
                if (publicationGeneration != replayGeneration) return;
            }
            deliverIfObserved(observer, update);
        });
    }

    private void deliverIfObserved(
            AppComposition.CameraCapabilityRecheckObserver observer,
            AppComposition.CameraCapabilityRecheckUpdate update) {
        synchronized (lock) {
            if (!observers.contains(observer)) return;
        }
        observer.onUpdate(update);
    }

    private static boolean terminal(AppComposition.CameraCapabilityRecheckUpdate update) {
        return update.status() != AppComposition.CameraCapabilityRecheckStatus.RUNNING;
    }
}