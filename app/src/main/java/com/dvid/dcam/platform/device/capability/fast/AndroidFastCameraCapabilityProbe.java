package com.dvid.dcam.platform.device.capability.fast;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.platform.device.capability.probe.egl.EglFanOutFastProbe;
import com.dvid.dcam.platform.device.capability.probe.nativesharing.NativeSurfaceSharingFastProbe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class AndroidFastCameraCapabilityProbe
        implements FastCameraCapabilityProbe {
    private static final long POLL_MILLIS = 50;
    private static final long EXECUTOR_RELEASE_TIMEOUT_MILLIS = 2_000;

    private final VerificationPipelineId pipelineId;
    private final SingleCameraOperation operation;
    private final Logger logger;

    public AndroidFastCameraCapabilityProbe(
            NativeSurfaceSharingFastProbe delegate, Logger logger) {
        this(NativeSurfaceSharingFastProbe.PIPELINE_ID,
                nativeOperation(delegate), logger);
    }

    public AndroidFastCameraCapabilityProbe(
            EglFanOutFastProbe delegate, Logger logger) {
        this(EglFanOutFastProbe.PIPELINE_ID, eglOperation(delegate), logger);
    }

    private static SingleCameraOperation nativeOperation(
            NativeSurfaceSharingFastProbe delegate) {
        NativeSurfaceSharingFastProbe checked = Objects.requireNonNull(
                delegate, "delegate");
        return request -> nativeOutcome(checked.probe(
                request.cameraId(), request.videoModes(), request.imageModes()));
    }

    private static SingleCameraOperation eglOperation(EglFanOutFastProbe delegate) {
        EglFanOutFastProbe checked = Objects.requireNonNull(delegate, "delegate");
        return request -> eglOutcome(checked.probe(
                request.cameraId(), request.videoModes(), request.imageModes()));
    }

    AndroidFastCameraCapabilityProbe(
            VerificationPipelineId pipelineId,
            SingleCameraOperation operation,
            Logger logger) {
        this.pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override public VerificationPipelineId pipelineId() { return pipelineId; }

    @Override public BatchResult probeBatch(
            List<Request> requests, CancellationSignal cancellationSignal) {
        List<Request> checked = checkedRequests(requests);
        Objects.requireNonNull(cancellationSignal, "cancellationSignal");
        if (cancellationSignal.isCancellationRequested()) {
            return cancelled(0, true, "cancelled_before_probe");
        }
        return probeConcurrent(checked, cancellationSignal);
    }

    private BatchResult probeConcurrent(
            List<Request> requests, CancellationSignal cancellationSignal) {
        long startedNanos = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(
                requests.size(), threadFactory());
        ExecutorCompletionService<ProbeOutcome> completionService =
                new ExecutorCompletionService<>(executor);
        List<Future<ProbeOutcome>> futures = new ArrayList<>();
        List<WorkerState> workers = new ArrayList<>();
        for (Request request : requests) {
            WorkerState worker = new WorkerState();
            workers.add(worker);
            futures.add(completionService.submit(() -> {
                worker.started().set(true);
                ProbeOutcome outcome = safeProbe(request);
                worker.outcome().set(outcome);
                return outcome;
            }));
        }

        TreeMap<CameraId, ProbeOutcome> outcomes = new TreeMap<>();
        RuntimeException executionFailure = null;
        boolean cancelled = false;
        int remaining = requests.size();
        try {
            while (remaining > 0) {
                if (cancellationSignal.isCancellationRequested()) {
                    cancelled = true;
                    break;
                }
                Future<ProbeOutcome> completed = completionService.poll(
                        POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (completed == null) continue;
                remaining--;
                try {
                    ProbeOutcome outcome = completed.get();
                    outcomes.put(outcome.evidence().cameraId(), outcome);
                } catch (ExecutionException error) {
                    executionFailure = new IllegalStateException(
                            "fast probe worker failed", error.getCause());
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            cancelled = true;
        } finally {
            if (cancelled || executionFailure != null) {
                for (Future<ProbeOutcome> future : futures) future.cancel(true);
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
        }

        boolean executorReleased = awaitRelease(executor);
        long elapsedMillis = elapsedMillis(startedNanos);
        boolean cleanupComplete = executorReleased
                && workers.stream().allMatch(WorkerState::cleanupComplete);
        if (cancelled) {
            return cancelled(elapsedMillis, cleanupComplete,
                    "cancelled_during_concurrent_probe");
        }
        if (executionFailure != null) {
            logger.warn(prefix() + " stage=concurrent_probe outcome=incomplete_global"
                    + " elapsedMs=" + elapsedMillis, executionFailure);
            return new BatchResult(Completion.INCOMPLETE_GLOBAL, List.of(),
                    cleanupComplete, elapsedMillis,
                    "worker:" + executionFailure.getClass().getSimpleName());
        }
        if (outcomes.size() != requests.size()) {
            return new BatchResult(Completion.INCOMPLETE_GLOBAL, List.of(),
                    cleanupComplete, elapsedMillis, "missing_worker_result");
        }

        List<CameraResult> cameraResults = outcomes.values().stream()
                .map(AndroidFastCameraCapabilityProbe::cameraResult)
                .collect(java.util.stream.Collectors.toList());
        Completion completion = batchCompletion(outcomes.values());
        return new BatchResult(completion, cameraResults, cleanupComplete,
                elapsedMillis, batchDetail(completion));
    }

    private ProbeOutcome safeProbe(Request request) {
        try {
            ProbeOutcome outcome = Objects.requireNonNull(
                    operation.probe(request), "probe outcome");
            if (!outcome.evidence().cameraId().equals(request.cameraId())
                    || !outcome.evidence().verificationPipelineId().equals(pipelineId)) {
                throw new IllegalArgumentException("probe outcome identity mismatch");
            }
            return outcome;
        } catch (RuntimeException error) {
            logger.warn(prefix() + " stage=single_camera_probe outcome=incomplete_global"
                    + " cameraId=" + request.cameraId(), error);
            PipelineEvidence evidence = new PipelineEvidence(
                    request.cameraId(), VideoCodec.H264, pipelineId,
                    PipelineAvailability.UNKNOWN, List.of(), List.of());
            return new ProbeOutcome(evidence, Completion.INCOMPLETE_GLOBAL,
                    false, 0, "probe:" + error.getClass().getSimpleName());
        }
    }

    private static ProbeOutcome nativeOutcome(
            NativeSurfaceSharingFastProbe.Result result) {
        return new ProbeOutcome(result.evidence(), switch (result.completion()) {
            case COMPLETE -> Completion.COMPLETE;
            case PIPELINE_UNAVAILABLE -> Completion.PIPELINE_UNAVAILABLE;
            case INCOMPLETE_TRANSIENT -> Completion.INCOMPLETE_TRANSIENT;
            case INCOMPLETE_GLOBAL -> Completion.INCOMPLETE_GLOBAL;
            case BLOCKED_EXTERNAL -> Completion.BLOCKED_EXTERNAL;
        }, nativeCleanupComplete(result), result.elapsedMillis(), result.detail());
    }

    static boolean nativeCleanupComplete(NativeSurfaceSharingFastProbe.Result result) {
        String detail = Objects.requireNonNull(result, "result").detail();
        return !detail.startsWith("release:")
                && !detail.startsWith("release_thread:")
                && !detail.startsWith("session_release:")
                && !detail.startsWith("tuple_release:");
    }

    private static ProbeOutcome eglOutcome(EglFanOutFastProbe.Result result) {
        return new ProbeOutcome(result.evidence(), switch (result.completion()) {
            case COMPLETE -> Completion.COMPLETE;
            case PIPELINE_UNAVAILABLE -> Completion.PIPELINE_UNAVAILABLE;
            case INCOMPLETE_TRANSIENT -> Completion.INCOMPLETE_TRANSIENT;
            case INCOMPLETE_GLOBAL -> Completion.INCOMPLETE_GLOBAL;
            case BLOCKED_EXTERNAL -> Completion.BLOCKED_EXTERNAL;
        }, result.cleanupComplete(), result.elapsedMillis(), result.detail());
    }

    private static Completion batchCompletion(Iterable<ProbeOutcome> outcomes) {
        Completion result = Completion.COMPLETE;
        for (ProbeOutcome outcome : outcomes) {
            result = combine(result, outcome.completion());
        }
        return result;
    }

    private static Completion combine(Completion current, Completion next) {
        if (next == Completion.CANCELLED) return Completion.CANCELLED;
        if (current == Completion.CANCELLED) return current;
        if (next == Completion.INCOMPLETE_GLOBAL) return Completion.INCOMPLETE_GLOBAL;
        if (current == Completion.INCOMPLETE_GLOBAL) return current;
        if (next == Completion.INCOMPLETE_TRANSIENT) return Completion.INCOMPLETE_TRANSIENT;
        if (current == Completion.INCOMPLETE_TRANSIENT) return current;
        if (next == Completion.BLOCKED_EXTERNAL) return Completion.BLOCKED_EXTERNAL;
        if (current == Completion.BLOCKED_EXTERNAL) return current;
        return Completion.COMPLETE;
    }

    private static CameraResult cameraResult(ProbeOutcome outcome) {
        return new CameraResult(outcome.evidence(), outcome.completion(),
                outcome.elapsedMillis(), outcome.detail());
    }

    private static BatchResult cancelled(
            long elapsedMillis, boolean cleanupComplete, String detail) {
        return new BatchResult(Completion.CANCELLED, List.of(), cleanupComplete,
                elapsedMillis, detail);
    }

    private static String batchDetail(Completion completion) {
        return "batch_" + completion.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static List<Request> checkedRequests(List<Request> requests) {
        List<Request> result = List.copyOf(Objects.requireNonNull(requests, "requests"));
        if (result.isEmpty()) throw new IllegalArgumentException("requests are empty");
        Set<CameraId> cameraIds = new HashSet<>();
        for (Request request : result) {
            if (!cameraIds.add(Objects.requireNonNull(request, "request").cameraId())) {
                throw new IllegalArgumentException("duplicate camera request");
            }
        }
        return result;
    }

    private static boolean awaitRelease(ExecutorService executor) {
        try {
            if (executor.awaitTermination(
                    EXECUTOR_RELEASE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                return true;
            }
            executor.shutdownNow();
            return executor.awaitTermination(
                    EXECUTOR_RELEASE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            return false;
        }
    }

    private ThreadFactory threadFactory() {
        AtomicInteger number = new AtomicInteger();
        return runnable -> new Thread(runnable,
                "dcam-fast-probe-" + pipelineId.value() + "-" + number.incrementAndGet());
    }

    private String prefix() {
        return "camera_fast_probe_adapter pipeline=" + pipelineId + " codec=h264";
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private record WorkerState(
            AtomicBoolean started,
            AtomicReference<ProbeOutcome> outcome) {
        private WorkerState() {
            this(new AtomicBoolean(), new AtomicReference<>());
        }

        private boolean cleanupComplete() {
            if (!started.get()) return true;
            ProbeOutcome completed = outcome.get();
            return completed != null && completed.cleanupComplete();
        }
    }

    @FunctionalInterface
    interface SingleCameraOperation {
        ProbeOutcome probe(Request request);
    }

    record ProbeOutcome(
            PipelineEvidence evidence,
            Completion completion,
            boolean cleanupComplete,
            long elapsedMillis,
            String detail) {
        ProbeOutcome {
            evidence = Objects.requireNonNull(evidence, "evidence");
            completion = Objects.requireNonNull(completion, "completion");
            if (elapsedMillis < 0) {
                throw new IllegalArgumentException("elapsedMillis must not be negative");
            }
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }
    }
}
