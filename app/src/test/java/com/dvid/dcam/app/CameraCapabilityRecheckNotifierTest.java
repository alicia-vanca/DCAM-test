package com.dvid.dcam.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

final class CameraCapabilityRecheckNotifierTest {
    @Test void terminalSummaryMovesToRecreatedObserverWhenOldActivityClosesFirst() {
        ManualExecutor executor = new ManualExecutor();
        CameraCapabilityRecheckNotifier notifier = new CameraCapabilityRecheckNotifier(executor);
        List<AppComposition.CameraCapabilityRecheckStatus> oldUpdates = new ArrayList<>();
        List<AppComposition.CameraCapabilityRecheckStatus> newUpdates = new ArrayList<>();
        CameraCapabilityRecheckNotifier.Subscription oldSubscription = notifier.observe(
                update -> oldUpdates.add(update.status()), () -> null);

        notifier.publish(terminal());
        oldSubscription.close();
        notifier.observe(update -> newUpdates.add(update.status()), () -> null);
        executor.runAll();

        assertEquals(List.of(), oldUpdates);
        assertEquals(List.of(AppComposition.CameraCapabilityRecheckStatus.SUCCEEDED), newUpdates);
    }

    @Test void activeRunningProgressReplaysToNewObserver() {
        ManualExecutor executor = new ManualExecutor();
        CameraCapabilityRecheckNotifier notifier = new CameraCapabilityRecheckNotifier(executor);
        List<AppComposition.CameraCapabilityRecheckUpdate> updates = new ArrayList<>();
        notifier.publish(progress());
        notifier.observe(updates::add, () -> progress());
        executor.runAll();

        assertEquals(1, updates.size());
        assertEquals(3, updates.get(0).progress().orElseThrow().completed());
    }

    @Test void runningThenTerminalReachCurrentObserverInOrder() {
        ManualExecutor executor = new ManualExecutor();
        CameraCapabilityRecheckNotifier notifier = new CameraCapabilityRecheckNotifier(executor);
        List<AppComposition.CameraCapabilityRecheckStatus> updates = new ArrayList<>();
        notifier.observe(update -> updates.add(update.status()), () -> null);

        notifier.publish(running());
        notifier.publish(terminal());
        executor.runAll();

        assertEquals(List.of(AppComposition.CameraCapabilityRecheckStatus.RUNNING,
                AppComposition.CameraCapabilityRecheckStatus.SUCCEEDED), updates);
    }

    @Test void terminalPublishedDuringActiveSnapshotBeatsStaleRunningReplay() {
        ManualExecutor executor = new ManualExecutor();
        CameraCapabilityRecheckNotifier notifier = new CameraCapabilityRecheckNotifier(executor);
        List<AppComposition.CameraCapabilityRecheckStatus> updates = new ArrayList<>();

        notifier.observe(update -> updates.add(update.status()), () -> {
            notifier.publish(terminal());
            return running();
        });
        executor.runAll();

        assertEquals(List.of(AppComposition.CameraCapabilityRecheckStatus.SUCCEEDED), updates);
    }

    @Test void terminalReplaysToReplacementObserverDuringLifecycleOverlap() {
        ManualExecutor executor = new ManualExecutor();
        CameraCapabilityRecheckNotifier notifier = new CameraCapabilityRecheckNotifier(executor);
        List<AppComposition.CameraCapabilityRecheckStatus> oldUpdates = new ArrayList<>();
        List<AppComposition.CameraCapabilityRecheckStatus> newUpdates = new ArrayList<>();
        notifier.observe(update -> oldUpdates.add(update.status()), () -> null);

        notifier.publish(terminal());
        notifier.observe(update -> newUpdates.add(update.status()), () -> null);
        executor.runAll();

        assertEquals(List.of(AppComposition.CameraCapabilityRecheckStatus.SUCCEEDED), oldUpdates);
        assertEquals(List.of(AppComposition.CameraCapabilityRecheckStatus.SUCCEEDED), newUpdates);
    }

    @Test void deliveredTerminalSummaryDoesNotReplayAfterRecreation() {
        ManualExecutor executor = new ManualExecutor();
        CameraCapabilityRecheckNotifier notifier = new CameraCapabilityRecheckNotifier(executor);
        List<AppComposition.CameraCapabilityRecheckStatus> oldUpdates = new ArrayList<>();
        List<AppComposition.CameraCapabilityRecheckStatus> newUpdates = new ArrayList<>();
        CameraCapabilityRecheckNotifier.Subscription oldSubscription = notifier.observe(
                update -> oldUpdates.add(update.status()), () -> null);

        notifier.publish(terminal());
        executor.runAll();
        notifier.acknowledgeTerminal();
        oldSubscription.close();
        notifier.observe(update -> newUpdates.add(update.status()), () -> null);
        executor.runAll();

        assertEquals(List.of(AppComposition.CameraCapabilityRecheckStatus.SUCCEEDED), oldUpdates);
        assertEquals(List.of(), newUpdates);
    }

    private static AppComposition.CameraCapabilityRecheckUpdate running() {
        return new AppComposition.CameraCapabilityRecheckUpdate(
                AppComposition.CameraCapabilityRecheckStatus.RUNNING,
                OptionalInt.empty(), OptionalInt.empty(),
                OptionalLong.empty(), OptionalLong.empty(), Optional.empty(), "recheck_started");
    }

    private static AppComposition.CameraCapabilityRecheckUpdate progress() {
        return new AppComposition.CameraCapabilityRecheckUpdate(
                AppComposition.CameraCapabilityRecheckStatus.RUNNING,
                OptionalInt.empty(), OptionalInt.empty(),
                OptionalLong.empty(), OptionalLong.empty(),
                Optional.of(new AppComposition.CameraCapabilityRecheckProgress(
                        "A", "0", "exhaustive_real_verify", 3, 10, "tuple=x")),
                "recheck_in_progress");
    }

    private static AppComposition.CameraCapabilityRecheckUpdate terminal() {
        return new AppComposition.CameraCapabilityRecheckUpdate(
                AppComposition.CameraCapabilityRecheckStatus.SUCCEEDED,
                OptionalInt.of(44), OptionalInt.of(41),
                OptionalLong.of(1200), OptionalLong.of(1300), Optional.empty(), "ready");
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override public void execute(Runnable command) { tasks.add(command); }

        private void runAll() {
            while (!tasks.isEmpty()) tasks.remove().run();
        }
    }
}