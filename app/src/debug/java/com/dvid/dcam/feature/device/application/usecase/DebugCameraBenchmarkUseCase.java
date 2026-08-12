package com.dvid.dcam.feature.device.application.usecase;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class DebugCameraBenchmarkUseCase {
    private final Runner runner;
    private final BooleanSupplier cameraIdle;
    private final BooleanSupplier reserveCamera;
    private final Runnable releaseCamera;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private final AtomicReference<Progress> latestProgress = new AtomicReference<>();
    private final AtomicReference<RunResult> latestResult = new AtomicReference<>();
    private final CopyOnWriteArrayList<Consumer<Progress>> progressObservers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<RunResult>> resultObservers = new CopyOnWriteArrayList<>();

    public DebugCameraBenchmarkUseCase(Runner runner, BooleanSupplier cameraIdle) {
        this(runner, cameraIdle, () -> true, () -> {});
    }

    public DebugCameraBenchmarkUseCase(Runner runner, BooleanSupplier cameraIdle,
            BooleanSupplier reserveCamera, Runnable releaseCamera) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.cameraIdle = Objects.requireNonNull(cameraIdle, "cameraIdle");
        this.reserveCamera = Objects.requireNonNull(reserveCamera, "reserveCamera");
        this.releaseCamera = Objects.requireNonNull(releaseCamera, "releaseCamera");
    }

    public List<String> cameraIds() {
        return runner.cameraIds();
    }

    public RunResult execute(Request request, Consumer<Progress> progressListener) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(progressListener, "progressListener");
        if (request.codec() == Codec.H265) {
            return finish(RunResult.rejected("chưa hỗ trợ H.265"));
        }
        if (!running.compareAndSet(false, true)) {
            return finish(RunResult.rejected("benchmark already running"));
        }
        boolean reserved = false;
        try {
            if (!cameraIdle.getAsBoolean()) {
                return finish(RunResult.rejected("camera owner is not idle"));
            }
            if (!reserveCamera.getAsBoolean()) {
                return finish(RunResult.rejected("camera owner already active"));
            }
            reserved = true;
            cancellationRequested.set(false);
            Consumer<Progress> publishProgress = value -> {
                latestProgress.set(value);
                progressListener.accept(value);
                for (Consumer<Progress> observer : progressObservers) observer.accept(value);
            };
            return finish(runner.run(request, cancellationRequested::get, publishProgress));
        } finally {
            try {
                if (reserved) releaseCamera.run();
            } finally {
                running.set(false);
            }
        }
    }

    public void cancel() {
        if (running.get()) cancellationRequested.set(true);
    }

    public boolean isRunning() {
        return running.get();
    }

    public Optional<Progress> latestProgress() {
        return Optional.ofNullable(latestProgress.get());
    }

    public Optional<RunResult> latestResult() {
        return Optional.ofNullable(latestResult.get());
    }

    public void subscribeProgress(Consumer<Progress> observer) {
        Consumer<Progress> checked = Objects.requireNonNull(observer, "observer");
        progressObservers.addIfAbsent(checked);
        Progress value = latestProgress.get();
        if (value != null) checked.accept(value);
    }

    public void unsubscribeProgress(Consumer<Progress> observer) {
        progressObservers.remove(observer);
    }

    public void subscribeResult(Consumer<RunResult> observer) {
        Consumer<RunResult> checked = Objects.requireNonNull(observer, "observer");
        resultObservers.addIfAbsent(checked);
        RunResult value = latestResult.get();
        if (value != null) checked.accept(value);
    }

    public void unsubscribeResult(Consumer<RunResult> observer) {
        resultObservers.remove(observer);
    }

    public ExportResult exportLastReport() {
        return runner.exportLastReport();
    }


    private RunResult finish(RunResult result) {
        RunResult checked = Objects.requireNonNull(result, "result");
        latestResult.set(checked);
        for (Consumer<RunResult> observer : resultObservers) observer.accept(checked);
        return checked;
    }

    public enum PipelineSelection { A, B, BOTH }
    public enum Scope { ALL_CAMERAS, SINGLE_CAMERA }
    public enum Codec { H264, H265 }
    public enum InitialOrder { A, B }
    public enum Status { COMPLETE, INCOMPLETE, CANCELLED, FAILED, REJECTED }

    public record Request(
            PipelineSelection pipelineSelection,
            Scope scope,
            Optional<String> cameraId,
            Codec codec,
            int measuredBlocks,
            InitialOrder initialOrder,
            long cooldownMillis) {
        public Request {
            pipelineSelection = Objects.requireNonNull(pipelineSelection, "pipelineSelection");
            scope = Objects.requireNonNull(scope, "scope");
            cameraId = Objects.requireNonNull(cameraId, "cameraId")
                    .map(String::trim).filter(value -> !value.isEmpty());
            codec = Objects.requireNonNull(codec, "codec");
            initialOrder = Objects.requireNonNull(initialOrder, "initialOrder");
            if (scope == Scope.SINGLE_CAMERA && cameraId.isEmpty()) {
                throw new IllegalArgumentException("single-camera scope needs cameraId");
            }
            if (measuredBlocks <= 0) {
                throw new IllegalArgumentException("measuredBlocks must be positive");
            }
            if (cooldownMillis < 0) {
                throw new IllegalArgumentException("cooldownMillis must not be negative");
            }
        }
    }

    public record Progress(String stage, int completed, int total, String detail) {
        public Progress {
            stage = required(stage, "stage");
            detail = required(detail, "detail");
            if (completed < 0 || total < 0 || completed > total) {
                throw new IllegalArgumentException("progress");
            }
        }
    }

    public record RunResult(Status status, String summary, Optional<String> reportPath) {
        public RunResult {
            status = Objects.requireNonNull(status, "status");
            summary = required(summary, "summary");
            reportPath = Objects.requireNonNull(reportPath, "reportPath");
        }

        public static RunResult rejected(String summary) {
            return new RunResult(Status.REJECTED, summary, Optional.empty());
        }
    }

    public record ExportResult(boolean exported, String detail, Optional<String> reportPath) {
        public ExportResult {
            detail = required(detail, "detail");
            reportPath = Objects.requireNonNull(reportPath, "reportPath");
            if (exported != reportPath.isPresent()) {
                throw new IllegalArgumentException("export state mismatch");
            }
        }
    }

    public interface Runner {
        List<String> cameraIds();
        RunResult run(Request request, BooleanSupplier cancellationSignal,
                Consumer<Progress> progressListener);
        ExportResult exportLastReport();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
