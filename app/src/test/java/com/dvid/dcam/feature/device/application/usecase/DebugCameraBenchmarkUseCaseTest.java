package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class DebugCameraBenchmarkUseCaseTest {
    @Test void h265IsRejectedWithoutRunningGateway() {
        RecordingRunner runner = new RecordingRunner();
        DebugCameraBenchmarkUseCase useCase = new DebugCameraBenchmarkUseCase(runner, () -> true);

        DebugCameraBenchmarkUseCase.RunResult result = useCase.execute(
                request(DebugCameraBenchmarkUseCase.Codec.H265), ignored -> {});

        assertEquals(DebugCameraBenchmarkUseCase.Status.REJECTED, result.status());
        assertEquals("chưa hỗ trợ H.265", result.summary());
        assertFalse(runner.called.get());
    }

    @Test void idleGateRejectsRun() {
        RecordingRunner runner = new RecordingRunner();
        DebugCameraBenchmarkUseCase useCase = new DebugCameraBenchmarkUseCase(runner, () -> false);

        DebugCameraBenchmarkUseCase.RunResult result = useCase.execute(
                request(DebugCameraBenchmarkUseCase.Codec.H264), ignored -> {});

        assertEquals(DebugCameraBenchmarkUseCase.Status.REJECTED, result.status());
        assertFalse(runner.called.get());
    }

    @Test void cancelSignalsActiveRunAndAllowsNextRun() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean reservation = new AtomicBoolean();
        RecordingRunner runner = new RecordingRunner(entered);
        DebugCameraBenchmarkUseCase useCase = reservedUseCase(runner, reservation);
        Thread worker = new Thread(() -> useCase.execute(
                request(DebugCameraBenchmarkUseCase.Codec.H264), ignored -> {}));
        worker.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        useCase.cancel();
        worker.join(2_000);

        assertFalse(worker.isAlive());
        assertFalse(useCase.isRunning());
        assertFalse(reservation.get());
        DebugCameraBenchmarkUseCase.RunResult next = useCase.execute(
                request(DebugCameraBenchmarkUseCase.Codec.H264), ignored -> {});
        assertEquals(DebugCameraBenchmarkUseCase.Status.COMPLETE, next.status());
    }

    @Test void resultObserverReceivesCompletionForReattach() {
        RecordingRunner runner = new RecordingRunner();
        DebugCameraBenchmarkUseCase useCase = new DebugCameraBenchmarkUseCase(runner, () -> true);
        AtomicReference<DebugCameraBenchmarkUseCase.RunResult> observed = new AtomicReference<>();
        Consumer<DebugCameraBenchmarkUseCase.RunResult> observer = observed::set;
        useCase.subscribeResult(observer);

        DebugCameraBenchmarkUseCase.RunResult result = useCase.execute(
                request(DebugCameraBenchmarkUseCase.Codec.H264), ignored -> {});

        assertEquals(result, observed.get());
        assertEquals(result, useCase.latestResult().orElseThrow());
        useCase.unsubscribeResult(observer);
    }

    @Test void processGuardRejectsSecondOwner() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean reservation = new AtomicBoolean();
        RecordingRunner firstRunner = new RecordingRunner(entered);
        DebugCameraBenchmarkUseCase first = reservedUseCase(firstRunner, reservation);
        DebugCameraBenchmarkUseCase second = reservedUseCase(new RecordingRunner(), reservation);
        Thread worker = new Thread(() -> first.execute(
                request(DebugCameraBenchmarkUseCase.Codec.H264), ignored -> {}));
        worker.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        DebugCameraBenchmarkUseCase.RunResult rejected = second.execute(
                request(DebugCameraBenchmarkUseCase.Codec.H264), ignored -> {});

        assertEquals(DebugCameraBenchmarkUseCase.Status.REJECTED, rejected.status());
        assertEquals("camera owner already active", rejected.summary());
        first.cancel();
        worker.join(2_000);
        assertFalse(worker.isAlive());
        assertFalse(reservation.get());
    }

    @Test void reservationReleasesWhenRunnerFails() {
        AtomicBoolean reservation = new AtomicBoolean();
        DebugCameraBenchmarkUseCase useCase = reservedUseCase(
                new RecordingRunner(true), reservation);

        assertThrows(IllegalStateException.class, () -> useCase.execute(
                request(DebugCameraBenchmarkUseCase.Codec.H264), ignored -> {}));

        assertFalse(reservation.get());
        assertFalse(useCase.isRunning());
    }

    private static DebugCameraBenchmarkUseCase reservedUseCase(
            DebugCameraBenchmarkUseCase.Runner runner, AtomicBoolean reservation) {
        return new DebugCameraBenchmarkUseCase(runner, () -> true,
                () -> reservation.compareAndSet(false, true), () -> reservation.set(false));
    }

    private static DebugCameraBenchmarkUseCase.Request request(
            DebugCameraBenchmarkUseCase.Codec codec) {
        return new DebugCameraBenchmarkUseCase.Request(
                DebugCameraBenchmarkUseCase.PipelineSelection.BOTH,
                DebugCameraBenchmarkUseCase.Scope.ALL_CAMERAS,
                Optional.empty(), codec, 1,
                DebugCameraBenchmarkUseCase.InitialOrder.A, 0);
    }

    private static final class RecordingRunner implements DebugCameraBenchmarkUseCase.Runner {
        private final AtomicBoolean called = new AtomicBoolean();
        private final CountDownLatch entered;
        private final boolean fail;

        private RecordingRunner() { this(null, false); }
        private RecordingRunner(CountDownLatch entered) { this(entered, false); }
        private RecordingRunner(boolean fail) { this(null, fail); }
        private RecordingRunner(CountDownLatch entered, boolean fail) {
            this.entered = entered;
            this.fail = fail;
        }

        @Override public List<String> cameraIds() { return List.of("0"); }

        @Override public DebugCameraBenchmarkUseCase.RunResult run(
                DebugCameraBenchmarkUseCase.Request request,
                java.util.function.BooleanSupplier cancellationSignal,
                java.util.function.Consumer<DebugCameraBenchmarkUseCase.Progress> progressListener) {
            called.set(true);
            if (fail) throw new IllegalStateException("benchmark failed");
            if (entered != null && entered.getCount() > 0) {
                entered.countDown();
                while (!cancellationSignal.getAsBoolean()) Thread.onSpinWait();
                return new DebugCameraBenchmarkUseCase.RunResult(
                        DebugCameraBenchmarkUseCase.Status.CANCELLED,
                        "cancelled", Optional.empty());
            }
            return new DebugCameraBenchmarkUseCase.RunResult(
                    DebugCameraBenchmarkUseCase.Status.COMPLETE,
                    "complete", Optional.of("report.json"));
        }

        @Override public DebugCameraBenchmarkUseCase.ExportResult exportLastReport() {
            return new DebugCameraBenchmarkUseCase.ExportResult(
                    false, "missing", Optional.empty());
        }
    }
}