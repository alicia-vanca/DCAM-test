package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.ConcurrentCameraCombination;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderSizeCapabilities;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.FrameRateRange;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.RawCatalog;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.StreamSize;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.BatchResult;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.CameraResult;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.CancellationSignal;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.Request;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.FrameRatePolicy;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionMapper;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class BuildFastCameraCapabilitiesUseCase {
    private static final int LENS_FACING_BACK = 1;
    private static final double NANOS_PER_SECOND = 1_000_000_000d;
    private static final Comparator<CaptureModeTuple> BEST_TUPLE_ORDER =
            BuildFastCameraCapabilitiesUseCase::compareBestTuple;

    public record Progress(CameraId cameraId, int completed, int total, String detail) {
        public Progress {
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            detail = Objects.requireNonNull(detail, "detail");
            if (completed < 0 || total <= 0 || completed > total || detail.isBlank()) {
                throw new IllegalArgumentException("invalid fast-build progress");
            }
        }
    }

    private final CameraCatalogSource catalogSource;
    private final Logger logger;
    private final Set<DisabledCombination> disabledConcurrentCombinations = new HashSet<>();

    public BuildFastCameraCapabilitiesUseCase(
            CameraCatalogSource catalogSource, Logger logger) {
        this.catalogSource = Objects.requireNonNull(catalogSource, "catalogSource");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized Result execute(FastCameraCapabilityProbe selectedPipelineProbe) {
        return execute(selectedPipelineProbe, CancellationSignal.NEVER, ignored -> {});
    }

    public synchronized Result execute(FastCameraCapabilityProbe selectedPipelineProbe,
            Consumer<Progress> progressListener) {
        return execute(selectedPipelineProbe, CancellationSignal.NEVER, progressListener);
    }

    public synchronized Result execute(
            FastCameraCapabilityProbe selectedPipelineProbe,
            CancellationSignal cancellationSignal) {
        return execute(selectedPipelineProbe, cancellationSignal, ignored -> {});
    }

    public synchronized Result execute(
            FastCameraCapabilityProbe selectedPipelineProbe,
            CancellationSignal cancellationSignal, Consumer<Progress> progressListener) {
        Objects.requireNonNull(selectedPipelineProbe, "selectedPipelineProbe");
        Objects.requireNonNull(cancellationSignal, "cancellationSignal");
        Objects.requireNonNull(progressListener, "progressListener");
        VerificationPipelineId pipelineId = Objects.requireNonNull(
                selectedPipelineProbe.pipelineId(), "pipelineId");
        long startedNanos = System.nanoTime();
        long catalogLoadMillis = 0;
        long schedulingMillis = 0;
        List<RoundTiming> rounds = new ArrayList<>();
        TreeMap<CameraId, CameraFastSnapshot> scanned = new TreeMap<>();

        if (cancellationSignal.isCancellationRequested()) {
            return incomplete(pipelineId, RunCompletion.INCOMPLETE_CANCELLED, scanned.values(), rounds,
                    catalogLoadMillis, schedulingMillis, startedNanos,
                    "cancelled_before_catalog");
        }

        RawCatalog catalog;
        long catalogStartedNanos = System.nanoTime();
        try {
            catalog = Objects.requireNonNull(catalogSource.load(), "catalog");
            catalogLoadMillis = elapsedMillis(catalogStartedNanos);
        } catch (CameraCatalogSource.CatalogException | RuntimeException error) {
            catalogLoadMillis = elapsedMillis(catalogStartedNanos);
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, prefix(pipelineId)
                    + " stage=catalog outcome=incomplete_global"
                    + " elapsedMs=" + catalogLoadMillis, error);
            return incomplete(pipelineId, RunCompletion.INCOMPLETE_GLOBAL, scanned.values(), rounds,
                    catalogLoadMillis, schedulingMillis, startedNanos,
                    "catalog:" + error.getClass().getSimpleName());
        }

        if (cancellationSignal.isCancellationRequested()) {
            return incomplete(pipelineId, RunCompletion.INCOMPLETE_CANCELLED, scanned.values(), rounds,
                    catalogLoadMillis, schedulingMillis, startedNanos,
                    "cancelled_after_catalog");
        }

        List<PreparedCamera> preparedCameras;
        try {
            preparedCameras = prepareCameras(catalog, pipelineId);
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, prefix(pipelineId)
                    + " stage=candidate_preparation outcome=incomplete_global", error);
            return incomplete(pipelineId, RunCompletion.INCOMPLETE_GLOBAL, scanned.values(), rounds,
                    catalogLoadMillis, schedulingMillis, startedNanos,
                    "candidate_preparation:" + error.getClass().getSimpleName());
        }

        TreeMap<CameraId, PreparedCamera> preparedById = new TreeMap<>();
        for (PreparedCamera prepared : preparedCameras) {
            if (preparedById.put(prepared.cameraFacts().cameraId(), prepared) != null) {
                return incomplete(pipelineId, RunCompletion.INCOMPLETE_GLOBAL, scanned.values(), rounds,
                        catalogLoadMillis, schedulingMillis, startedNanos,
                        "duplicate_camera_id");
            }
        }

        long schedulingStartedNanos = System.nanoTime();
        List<CombinationKey> combinations = usableCombinations(
                catalog, preparedById.keySet());
        schedulingMillis = elapsedMillis(schedulingStartedNanos);

        TreeSet<CameraId> unscanned = new TreeSet<>(preparedById.keySet());
        int roundIndex = 0;
        while (!unscanned.isEmpty()) {
            if (cancellationSignal.isCancellationRequested()) {
                return incomplete(pipelineId, RunCompletion.INCOMPLETE_CANCELLED, scanned.values(), rounds,
                        catalogLoadMillis, schedulingMillis, startedNanos,
                        "cancelled_before_round");
            }

            ScheduledBatch concurrentBatch = nextConcurrentBatch(
                    unscanned, combinations, catalog.hardwareSignatureInput(), pipelineId);
            if (concurrentBatch == null) {
                CameraId cameraId = unscanned.first();
                PreparedCamera prepared = preparedById.get(cameraId);
                ScanAttempt singleton = invoke(selectedPipelineProbe,
                        List.of(prepared.request()), cancellationSignal);
                rounds.add(singleton.timing(++roundIndex, false, false));
                logRound(pipelineId, rounds.get(rounds.size() - 1));
                Optional<RunCompletion> failure = singletonFailure(singleton);
                if (failure.isPresent()) {
                    return incomplete(pipelineId, failure.orElseThrow(), scanned.values(), rounds,
                            catalogLoadMillis, schedulingMillis, startedNanos,
                            singleton.detail());
                }
                try {
                    CameraFastSnapshot snapshot = decodeTerminalResults(
                            singleton.result(), List.of(prepared), pipelineId).get(cameraId);
                    scanned.put(cameraId, snapshot);
                } catch (RuntimeException error) {
                    logger.warn(LogCategory.CAPABILITY, "unspecified", null, prefix(pipelineId)
                            + " stage=singleton_decode outcome=incomplete_global"
                            + " cameraId=" + cameraId, error);
                    return incomplete(pipelineId, RunCompletion.INCOMPLETE_GLOBAL, scanned.values(), rounds,
                            catalogLoadMillis, schedulingMillis, startedNanos,
                            "singleton_contract:" + error.getClass().getSimpleName());
                }
                unscanned.remove(cameraId);
                publishProgress(progressListener, pipelineId, cameraId,
                        scanned.size(), preparedById.size());
                continue;
            }

            List<PreparedCamera> batchPrepared = preparedFor(
                    concurrentBatch.members(), preparedById);
            List<Request> requests = batchPrepared.stream()
                    .map(PreparedCamera::request)
                    .collect(java.util.stream.Collectors.toList());
            ScanAttempt batchAttempt = invoke(
                    selectedPipelineProbe, requests, cancellationSignal);
            rounds.add(batchAttempt.timing(++roundIndex, true, false));
            logRound(pipelineId, rounds.get(rounds.size() - 1));

            if (batchAttempt.cancelled(cancellationSignal)) {
                return incomplete(pipelineId, RunCompletion.INCOMPLETE_CANCELLED, scanned.values(), rounds,
                        catalogLoadMillis, schedulingMillis, startedNanos,
                        batchAttempt.detail());
            }
            if (batchAttempt.error() != null) {
                return incomplete(pipelineId, RunCompletion.INCOMPLETE_GLOBAL, scanned.values(), rounds,
                        catalogLoadMillis, schedulingMillis, startedNanos,
                        batchAttempt.detail());
            }

            BatchResult batchResult = batchAttempt.result();
            if (batchResult.complete()) {
                try {
                    Map<CameraId, CameraFastSnapshot> snapshots = decodeTerminalResults(
                            batchResult, batchPrepared, pipelineId);
                    for (CameraId cameraId : new TreeSet<>(snapshots.keySet())) {
                        scanned.put(cameraId, snapshots.get(cameraId));
                        unscanned.remove(cameraId);
                        publishProgress(progressListener, pipelineId, cameraId,
                                scanned.size(), preparedById.size());
                    }
                    continue;
                } catch (RuntimeException error) {
                    logger.warn(LogCategory.CAPABILITY, "unspecified", null, prefix(pipelineId)
                            + " stage=batch_decode outcome=incomplete_global"
                            + " cameras=" + cameraIds(concurrentBatch.members()), error);
                    return incomplete(pipelineId, RunCompletion.INCOMPLETE_GLOBAL, scanned.values(), rounds,
                            catalogLoadMillis, schedulingMillis, startedNanos,
                            "batch_contract:" + error.getClass().getSimpleName());
                }
            }

            if (batchResult.completion() == FastCameraCapabilityProbe.Completion.COMPLETE) {
                disabledConcurrentCombinations.add(new DisabledCombination(
                        catalog.hardwareSignatureInput(), pipelineId,
                        concurrentBatch.combination()));
                return incomplete(pipelineId, RunCompletion.INCOMPLETE_TRANSIENT,
                        scanned.values(), rounds, catalogLoadMillis, schedulingMillis,
                        startedNanos, "batch_cleanup_incomplete:" + batchResult.detail());
            }
            if (batchResult.completion()
                    != FastCameraCapabilityProbe.Completion.INCOMPLETE_TRANSIENT) {
                return incomplete(pipelineId, mapIncomplete(batchResult.completion()),
                        scanned.values(), rounds, catalogLoadMillis, schedulingMillis,
                        startedNanos, batchResult.detail());
            }

            disabledConcurrentCombinations.add(new DisabledCombination(
                    catalog.hardwareSignatureInput(), pipelineId,
                    concurrentBatch.combination()));
            logger.info(LogCategory.CAPABILITY, "unspecified", prefix(pipelineId)
                    + " stage=batch_disable outcome=complete"
                    + " cameras=" + cameraIds(concurrentBatch.combination().cameraIds())
                    + " reason=" + batchResult.detail());
            if (!batchResult.cleanupComplete()) {
                return incomplete(pipelineId, RunCompletion.INCOMPLETE_TRANSIENT,
                        scanned.values(), rounds, catalogLoadMillis, schedulingMillis,
                        startedNanos, "batch_cleanup_incomplete:" + batchResult.detail());
            }

            for (PreparedCamera prepared : batchPrepared) {
                if (cancellationSignal.isCancellationRequested()) {
                    return incomplete(pipelineId, RunCompletion.INCOMPLETE_CANCELLED, scanned.values(), rounds,
                            catalogLoadMillis, schedulingMillis, startedNanos,
                            "cancelled_before_singleton_retry");
                }
                ScanAttempt retry = invoke(selectedPipelineProbe,
                        List.of(prepared.request()), cancellationSignal);
                rounds.add(retry.timing(++roundIndex, false, true));
                logRound(pipelineId, rounds.get(rounds.size() - 1));
                Optional<RunCompletion> failure = singletonFailure(retry);
                if (failure.isPresent()) {
                    return incomplete(pipelineId, failure.orElseThrow(), scanned.values(), rounds,
                            catalogLoadMillis, schedulingMillis, startedNanos,
                            retry.detail());
                }
                try {
                    CameraId cameraId = prepared.cameraFacts().cameraId();
                    CameraFastSnapshot snapshot = decodeTerminalResults(
                            retry.result(), List.of(prepared), pipelineId).get(cameraId);
                    scanned.put(cameraId, snapshot);
                    unscanned.remove(cameraId);
                    publishProgress(progressListener, pipelineId, cameraId,
                            scanned.size(), preparedById.size());
                } catch (RuntimeException error) {
                    logger.warn(LogCategory.CAPABILITY, "unspecified", null, prefix(pipelineId)
                            + " stage=singleton_retry_decode outcome=incomplete_global"
                            + " cameraId=" + prepared.cameraFacts().cameraId(), error);
                    return incomplete(pipelineId, RunCompletion.INCOMPLETE_GLOBAL, scanned.values(), rounds,
                            catalogLoadMillis, schedulingMillis, startedNanos,
                            "singleton_retry_contract:"
                                    + error.getClass().getSimpleName());
                }
            }
        }

        if (cancellationSignal.isCancellationRequested()) {
            return incomplete(pipelineId, RunCompletion.INCOMPLETE_CANCELLED, scanned.values(), rounds,
                    catalogLoadMillis, schedulingMillis, startedNanos,
                    "cancelled_before_ranking");
        }

        List<CameraFastSnapshot> cameraSnapshots = List.copyOf(scanned.values());
        long allFastReadyMillis = elapsedMillis(startedNanos);
        logger.info(LogCategory.CAPABILITY, "unspecified", prefix(pipelineId)
                + " stage=all_fast_ready outcome=complete"
                + " cameraCount=" + cameraSnapshots.size()
                + " elapsedMs=" + allFastReadyMillis);

        List<CameraId> autoCameraOrder = rank(cameraSnapshots);

        FastSnapshot snapshot = new FastSnapshot(
                catalog, pipelineId, cameraSnapshots, autoCameraOrder);
        long totalElapsedMillis = elapsedMillis(startedNanos);
        BenchmarkData benchmark = new BenchmarkData(
                catalogLoadMillis, schedulingMillis, rounds,
                OptionalLong.of(allFastReadyMillis), totalElapsedMillis);
        return new Result(RunCompletion.COMPLETE, Optional.of(snapshot),
                cameraSnapshots, benchmark, "complete");
    }
    private static void publishProgress(Consumer<Progress> progressListener,
            VerificationPipelineId pipelineId, CameraId cameraId, int completed, int total) {
        progressListener.accept(new Progress(cameraId, completed, total,
                "pipeline=" + pipelineId.value() + " camera=" + cameraId.value()));
    }

    private List<PreparedCamera> prepareCameras(
            RawCatalog catalog, VerificationPipelineId pipelineId) {
        List<PreparedCamera> result = new ArrayList<>();
        Set<CameraId> cameraIds = new HashSet<>();
        for (CameraFacts camera : catalog.cameras()) {
            if (!cameraIds.add(camera.cameraId())) {
                throw new IllegalArgumentException("duplicate camera ID");
            }
            List<VideoMode> videoModes = videoModes(camera, catalog.h264Encoders());
            List<ImageMode> imageModes = imageModes(camera);

            result.add(new PreparedCamera(camera,
                    new Request(camera.cameraId(), videoModes, imageModes)));
        }
        result.sort(Comparator.comparing(value -> value.cameraFacts().cameraId()));
        return List.copyOf(result);
    }

    private static List<VideoMode> videoModes(
            CameraFacts camera, List<EncoderFacts> encoders) {
        Map<CameraResolution, List<Integer>> supportedFrameRates =
                new LinkedHashMap<>();
        for (StreamSize output : camera.privateOutputs()) {
            CameraResolution resolution = output.resolution();
            supportedFrameRates.computeIfAbsent(resolution, candidate ->
                    retainedFrameRates(camera, encoders, candidate));
        }
        supportedFrameRates.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        List<VideoMode> result = new ArrayList<>();
        Set<CameraResolution> represented = new HashSet<>();
        for (StandardResolutionLabel label : StandardResolutionLabel.values()) {
            if (label == StandardResolutionLabel.MAX) continue;
            for (StandardResolution resolution : StandardResolutionMapper.rankedMatches(
                    label, supportedFrameRates.keySet())) {
                represented.add(resolution.actual());
                for (int framesPerSecond : supportedFrameRates.get(resolution.actual())) {
                    result.add(new VideoMode(resolution, framesPerSecond));
                }
            }
        }
        CameraResolution maximum = supportedFrameRates.keySet().stream()
                .filter(resolution -> !represented.contains(resolution))
                .max(CameraResolution::compareTo).orElse(null);
        if (maximum != null && StandardResolutionMapper.qualifiesAsMaximum(
                maximum, represented)) {
            StandardResolution resolution = new StandardResolution(
                    StandardResolutionLabel.MAX, maximum);
            for (int framesPerSecond : supportedFrameRates.get(maximum)) {
                result.add(new VideoMode(resolution, framesPerSecond));
            }
        }
        return List.copyOf(result);
    }

    private static List<ImageMode> imageModes(CameraFacts camera) {
        TreeSet<CameraResolution> rawResolutions = camera.jpegOutputs().stream()
                .map(StreamSize::resolution)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        List<ImageMode> result = new ArrayList<>();
        Set<CameraResolution> represented = new HashSet<>();
        for (StandardResolutionLabel label : StandardResolutionLabel.values()) {
            if (label == StandardResolutionLabel.MAX) continue;
            for (StandardResolution resolution : StandardResolutionMapper.rankedMatches(
                    label, rawResolutions)) {
                represented.add(resolution.actual());
                result.add(new ImageMode(resolution));
            }
        }
        CameraResolution maximum = rawResolutions.stream()
                .filter(resolution -> !represented.contains(resolution))
                .max(CameraResolution::compareTo).orElse(null);
        if (maximum != null && StandardResolutionMapper.qualifiesAsMaximum(
                maximum, represented)) {
            result.add(new ImageMode(new StandardResolution(
                    StandardResolutionLabel.MAX, maximum)));
        }
        return List.copyOf(result);
    }

    private static List<Integer> retainedFrameRates(
            CameraFacts camera,
            List<EncoderFacts> encoders,
            CameraResolution resolution) {
        List<IntRange> cameraRanges = normalizedRanges(camera.aeTargetFpsRanges());
        Set<Integer> encoderFrameRates = encoderFrameRates(encoders, resolution);
        OptionalInt cameraMaximum = cameraMaximum(camera.privateOutputs(), resolution);
        if ((cameraRanges.isEmpty() && cameraMaximum.isEmpty())
                || encoderFrameRates.isEmpty()) {
            return List.of();
        }

        TreeSet<Integer> finiteCandidates = new TreeSet<>(encoderFrameRates);
        addEndpoints(finiteCandidates, cameraRanges);
        cameraMaximum.ifPresent(finiteCandidates::add);
        finiteCandidates.removeIf(framesPerSecond ->
                !encoderFrameRates.contains(framesPerSecond)
                        || !cameraSupports(framesPerSecond, cameraRanges, cameraMaximum));
        return FrameRatePolicy.retainSupported(finiteCandidates);
    }

    private static Set<Integer> encoderFrameRates(
            List<EncoderFacts> encoders, CameraResolution resolution) {
        TreeSet<Integer> result = new TreeSet<>();
        for (EncoderFacts encoder : encoders) {
            if (encoder.videoCapabilities() == null) continue;
            for (EncoderSizeCapabilities size :
                    encoder.videoCapabilities().cameraSizeCapabilities()) {
                if (size.resolution().equals(resolution)) {
                    result.addAll(size.supportedFrameRates());
                }
            }
        }
        return Set.copyOf(result);
    }

    private static OptionalInt cameraMaximum(
            List<StreamSize> outputs, CameraResolution resolution) {
        int maximum = 0;
        for (StreamSize output : outputs) {
            if (!output.resolution().equals(resolution)
                    || output.minimumFrameDurationNanos() == null
                    || output.minimumFrameDurationNanos() <= 0) {
                continue;
            }
            int candidate = FrameRatePolicy.normalize(
                    NANOS_PER_SECOND / output.minimumFrameDurationNanos());
            maximum = Math.max(maximum, candidate);
        }
        return maximum == 0 ? OptionalInt.empty() : OptionalInt.of(maximum);
    }

    private static List<IntRange> normalizedRanges(List<FrameRateRange> ranges) {
        List<IntRange> result = new ArrayList<>();
        for (FrameRateRange range : ranges) result.add(normalizedRange(range));
        return List.copyOf(result);
    }

    private static IntRange normalizedRange(FrameRateRange range) {
        return new IntRange(
                FrameRatePolicy.normalize(range.lower().doubleValue()),
                FrameRatePolicy.normalize(range.upper().doubleValue()));
    }

    private static void addEndpoints(Set<Integer> values, List<IntRange> ranges) {
        for (IntRange range : ranges) {
            values.add(range.lower());
            values.add(range.upper());
        }
    }

    private static boolean cameraSupports(
            int framesPerSecond,
            List<IntRange> ranges,
            OptionalInt maximum) {
        boolean rangeSupported = ranges.isEmpty() || supports(framesPerSecond, ranges);
        boolean durationSupported = maximum.isEmpty()
                || framesPerSecond <= maximum.orElseThrow();
        return rangeSupported && durationSupported;
    }

    private static boolean supports(int framesPerSecond, List<IntRange> ranges) {
        return ranges.stream().anyMatch(range -> range.contains(framesPerSecond));
    }

    private List<CombinationKey> usableCombinations(
            RawCatalog catalog, Set<CameraId> inventory) {
        if (!catalog.concurrency().queryAvailable()
                || catalog.concurrency().combinations().isEmpty()) {
            return List.of();
        }
        TreeSet<CombinationKey> result = new TreeSet<>();
        for (ConcurrentCameraCombination raw : catalog.concurrency().combinations()) {
            TreeSet<CameraId> unique = new TreeSet<>(raw.cameraIds());
            if (unique.size() != raw.cameraIds().size()
                    || unique.size() < 2
                    || !inventory.containsAll(unique)) {
                continue;
            }
            result.add(new CombinationKey(unique));
        }
        return List.copyOf(result);
    }

    private ScheduledBatch nextConcurrentBatch(
            Set<CameraId> unscanned,
            List<CombinationKey> combinations,
            String hardwareSignatureInput,
            VerificationPipelineId pipelineId) {
        ScheduledBatch best = null;
        for (CombinationKey combination : combinations) {
            DisabledCombination disabled = new DisabledCombination(
                    hardwareSignatureInput, pipelineId, combination);
            if (disabledConcurrentCombinations.contains(disabled)) continue;
            List<CameraId> members = combination.cameraIds().stream()
                    .filter(unscanned::contains)
                    .collect(java.util.stream.Collectors.toList());
            if (members.size() < 2) continue;
            ScheduledBatch candidate = new ScheduledBatch(combination, members);
            if (best == null
                    || candidate.members().size() > best.members().size()
                    || (candidate.members().size() == best.members().size()
                    && compareCameraIds(candidate.members(), best.members()) < 0)) {
                best = candidate;
            }
        }
        return best;
    }

    private static List<PreparedCamera> preparedFor(
            List<CameraId> cameraIds,
            Map<CameraId, PreparedCamera> preparedById) {
        List<PreparedCamera> result = new ArrayList<>();
        for (CameraId cameraId : cameraIds) {
            result.add(Objects.requireNonNull(preparedById.get(cameraId), "prepared camera"));
        }
        return List.copyOf(result);
    }

    private ScanAttempt invoke(
            FastCameraCapabilityProbe probe,
            List<Request> requests,
            CancellationSignal cancellationSignal) {
        long startedNanos = System.nanoTime();
        try {
            BatchResult result = Objects.requireNonNull(
                    probe.probeBatch(requests, cancellationSignal), "batch result");
            return new ScanAttempt(requests.stream().map(Request::cameraId).collect(java.util.stream.Collectors.toList()),
                    result, null, elapsedMillis(startedNanos));
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, prefix(probe.pipelineId())
                    + " stage=probe_call outcome=incomplete_global"
                    + " cameras=" + cameraIds(
                            requests.stream().map(Request::cameraId).collect(java.util.stream.Collectors.toList()))
                    + " elapsedMs=" + elapsedMillis(startedNanos), error);
            return new ScanAttempt(requests.stream().map(Request::cameraId).collect(java.util.stream.Collectors.toList()),
                    null, error, elapsedMillis(startedNanos));
        }
    }

    private static Optional<RunCompletion> singletonFailure(ScanAttempt attempt) {
        if (attempt.error() != null) return Optional.of(RunCompletion.INCOMPLETE_GLOBAL);
        BatchResult result = attempt.result();
        if (result.completion() == FastCameraCapabilityProbe.Completion.CANCELLED) {
            return Optional.of(RunCompletion.INCOMPLETE_CANCELLED);
        }
        if (result.completion() != FastCameraCapabilityProbe.Completion.COMPLETE) {
            return Optional.of(mapIncomplete(result.completion()));
        }
        if (!result.cleanupComplete()) {
            return Optional.of(RunCompletion.INCOMPLETE_TRANSIENT);
        }
        return Optional.empty();
    }

    private static Map<CameraId, CameraFastSnapshot> decodeTerminalResults(
            BatchResult result,
            List<PreparedCamera> preparedCameras,
            VerificationPipelineId pipelineId) {
        if (!result.complete()) throw new IllegalArgumentException("batch is incomplete");
        TreeMap<CameraId, PreparedCamera> preparedById = new TreeMap<>();
        for (PreparedCamera prepared : preparedCameras) {
            preparedById.put(prepared.cameraFacts().cameraId(), prepared);
        }
        if (result.cameraResults().size() != preparedById.size()) {
            throw new IllegalArgumentException("batch result count mismatch");
        }

        TreeMap<CameraId, CameraFastSnapshot> snapshots = new TreeMap<>();
        for (CameraResult cameraResult : result.cameraResults()) {
            CameraId cameraId = cameraResult.evidence().cameraId();
            PreparedCamera prepared = preparedById.get(cameraId);
            if (prepared == null || !cameraResult.terminal()) {
                throw new IllegalArgumentException("unexpected camera result");
            }
            PipelineEvidence merged = mergeEvidence(
                    cameraResult, prepared.request(), pipelineId);
            Optional<CaptureModeTuple> bestTuple = bestEffectiveTuple(merged);
            snapshots.put(cameraId, new CameraFastSnapshot(
                    prepared.cameraFacts(),
                    prepared.request().videoModes(),
                    prepared.request().imageModes(),
                    merged,
                    bestTuple,
                    cameraResult.elapsedMillis(),
                    cameraResult.detail()));
        }
        if (!snapshots.keySet().equals(preparedById.keySet())) {
            throw new IllegalArgumentException("batch result camera mismatch");
        }
        return Map.copyOf(snapshots);
    }
    private static PipelineEvidence mergeEvidence(
            CameraResult result,
            Request request,
            VerificationPipelineId pipelineId) {
        PipelineEvidence evidence = result.evidence();
        if (!evidence.cameraId().equals(request.cameraId())
                || evidence.codec() != VideoCodec.H264
                || !evidence.verificationPipelineId().equals(pipelineId)
                || !evidence.candidateEvidence().isEmpty()) {
            throw new IllegalArgumentException("probe evidence identity mismatch");
        }

        PipelineAvailability expectedAvailability = switch (result.completion()) {
            case COMPLETE -> PipelineAvailability.AVAILABLE;
            case PIPELINE_UNAVAILABLE -> PipelineAvailability.UNAVAILABLE;
            case INCOMPLETE_TRANSIENT, INCOMPLETE_GLOBAL, BLOCKED_EXTERNAL, CANCELLED ->
                    throw new IllegalArgumentException("camera result is incomplete");
        };
        if (evidence.availability() != expectedAvailability) {
            throw new IllegalArgumentException("probe availability mismatch");
        }
        if (expectedAvailability == PipelineAvailability.UNAVAILABLE) {
            if (!evidence.rawFastCandidates().isEmpty()) {
                throw new IllegalArgumentException(
                        "unavailable pipeline contains fast candidates");
            }
            return evidence;
        }

        Set<VideoMode> videos = Set.copyOf(request.videoModes());
        Set<ImageMode> images = Set.copyOf(request.imageModes());
        for (CandidateKey candidate : evidence.rawFastCandidates()) {
            validateProbeCandidate(candidate, request.cameraId(), pipelineId, videos, images);
        }
        return new PipelineEvidence(request.cameraId(), VideoCodec.H264, pipelineId,
                PipelineAvailability.AVAILABLE,
                evidence.rawFastCandidates(), List.of());
    }

    private static void validateProbeCandidate(
            CandidateKey candidate,
            CameraId cameraId,
            VerificationPipelineId pipelineId,
            Set<VideoMode> videos,
            Set<ImageMode> images) {
        if (!candidate.cameraId().equals(cameraId)
                || candidate.codec() != VideoCodec.H264
                || !candidate.verificationPipelineId().equals(pipelineId)) {
            throw new IllegalArgumentException("probe candidate identity mismatch");
        }
        boolean valid = switch (candidate.kind()) {
            case VIDEO -> videos.contains(candidate.videoMode().orElseThrow());
            case IMAGE -> images.contains(candidate.imageMode().orElseThrow());
            case TUPLE -> videos.contains(candidate.videoMode().orElseThrow())
                    && images.contains(candidate.imageMode().orElseThrow());
        };
        if (!valid) throw new IllegalArgumentException("probe candidate outside request");
    }

    private static Optional<CaptureModeTuple> bestEffectiveTuple(
            PipelineEvidence evidence) {
        return evidence.effectiveCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .map(candidate -> candidate.tuple().orElseThrow())
                .max(BEST_TUPLE_ORDER);
    }

    private static List<CameraId> rank(List<CameraFastSnapshot> cameras) {
        List<CameraFastSnapshot> ordered = new ArrayList<>(cameras);
        ordered.sort(BuildFastCameraCapabilitiesUseCase::compareCameraCapability);
        if (ordered.size() > 1) {
            int preferredBackIndex = -1;
            for (int index = 0; index < ordered.size(); index++) {
                if (isBackFacing(ordered.get(index))) {
                    preferredBackIndex = index;
                    break;
                }
            }
            if (preferredBackIndex > 0) {
                CameraFastSnapshot preferredBack = ordered.remove(preferredBackIndex);
                ordered.add(0, preferredBack);
            }
        }
        return ordered.stream()
                .map(camera -> camera.cameraFacts().cameraId())
                .collect(java.util.stream.Collectors.toList());
    }

    private static int compareCameraCapability(
            CameraFastSnapshot left, CameraFastSnapshot right) {
        if (left.bestEffectiveTuple().isPresent()
                != right.bestEffectiveTuple().isPresent()) {
            return left.bestEffectiveTuple().isPresent() ? -1 : 1;
        }
        if (left.bestEffectiveTuple().isPresent()) {
            int tupleOrder = BEST_TUPLE_ORDER.compare(
                    right.bestEffectiveTuple().orElseThrow(),
                    left.bestEffectiveTuple().orElseThrow());
            if (tupleOrder != 0) return tupleOrder;
        }
        return left.cameraFacts().cameraId().compareTo(
                right.cameraFacts().cameraId());
    }

    private static boolean isBackFacing(CameraFastSnapshot camera) {
        return Integer.valueOf(LENS_FACING_BACK).equals(camera.cameraFacts().lensFacing());
    }

    private static int compareBestTuple(
            CaptureModeTuple left, CaptureModeTuple right) {
        int order = com.dvid.dcam.feature.device.domain.camera.CameraModeOrder
                .tuples().compare(left, right);
        return order != 0 ? order : left.compareTo(right);
    }

    private static int compareCameraIds(List<CameraId> left, List<CameraId> right) {
        int shared = Math.min(left.size(), right.size());
        for (int index = 0; index < shared; index++) {
            int order = left.get(index).compareTo(right.get(index));
            if (order != 0) return order;
        }
        return Integer.compare(left.size(), right.size());
    }

    private static RunCompletion mapIncomplete(
            FastCameraCapabilityProbe.Completion completion) {
        return switch (completion) {
            case INCOMPLETE_TRANSIENT -> RunCompletion.INCOMPLETE_TRANSIENT;
            case INCOMPLETE_GLOBAL -> RunCompletion.INCOMPLETE_GLOBAL;
            case BLOCKED_EXTERNAL -> RunCompletion.INCOMPLETE_BLOCKED_EXTERNAL;
            case CANCELLED -> RunCompletion.INCOMPLETE_CANCELLED;
            case COMPLETE, PIPELINE_UNAVAILABLE -> throw new IllegalArgumentException(
                    "completion is not incomplete");
        };
    }

    private Result incomplete(
            VerificationPipelineId pipelineId,
            RunCompletion completion,
            Collection<CameraFastSnapshot> partialCameras,
            List<RoundTiming> rounds,
            long catalogLoadMillis,
            long schedulingMillis,
            long startedNanos,
            String detail) {
        long totalElapsedMillis = elapsedMillis(startedNanos);
        logger.info(LogCategory.CAPABILITY, "unspecified", prefix(pipelineId) + " stage=complete outcome="
                + completion.name().toLowerCase(java.util.Locale.ROOT)
                + " partialCameraCount=" + partialCameras.size()
                + " elapsedMs=" + totalElapsedMillis
                + " detail=" + detail);
        return new Result(completion, Optional.empty(), List.copyOf(partialCameras),
                new BenchmarkData(catalogLoadMillis, schedulingMillis, rounds,
                        OptionalLong.empty(), totalElapsedMillis), detail);
    }

    private void logRound(VerificationPipelineId pipelineId, RoundTiming timing) {
        if (timing.completion() == FastCameraCapabilityProbe.Completion.COMPLETE) return;
        logger.info(LogCategory.CAPABILITY, "unspecified", prefix(pipelineId)
                + " stage=scan_round outcome="
                + timing.completion().name().toLowerCase(java.util.Locale.ROOT)
                + " round=" + timing.round()
                + " cameras=" + cameraIds(timing.cameraIds())
                + " concurrent=" + timing.concurrent()
                + " singletonRetry=" + timing.singletonRetry()
                + " cleanupComplete=" + timing.cleanupComplete()
                + " wallElapsedMs=" + timing.wallElapsedMillis()
                + " probeElapsedMs=" + timing.probeElapsedMillis()
                + " detail=" + timing.detail());
    }

    private static String prefix(VerificationPipelineId pipelineId) {
        return "camera_fast_scan pipeline=" + pipelineId + " codec=h264";
    }

    private static String cameraIds(Collection<CameraId> cameraIds) {
        return cameraIds.stream().map(CameraId::value)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    public enum RunCompletion {
        COMPLETE,
        INCOMPLETE_CANCELLED,
        INCOMPLETE_TRANSIENT,
        INCOMPLETE_GLOBAL,
        INCOMPLETE_BLOCKED_EXTERNAL
    }

    public record Result(
            RunCompletion completion,
            Optional<FastSnapshot> authoritativeSnapshot,
            List<CameraFastSnapshot> partialCameras,
            BenchmarkData benchmark,
            String detail) {
        public Result {
            completion = Objects.requireNonNull(completion, "completion");
            authoritativeSnapshot = Objects.requireNonNull(
                    authoritativeSnapshot, "authoritativeSnapshot");
            partialCameras = immutableUniqueCameraSnapshots(partialCameras);
            benchmark = Objects.requireNonNull(benchmark, "benchmark");
            detail = required(detail, "detail");
            if ((completion == RunCompletion.COMPLETE)
                    != authoritativeSnapshot.isPresent()) {
                throw new IllegalArgumentException(
                        "only complete result may contain authoritative snapshot");
            }
        }

        public boolean complete() { return completion == RunCompletion.COMPLETE; }
    }

    public record FastSnapshot(
            RawCatalog rawCatalog,
            VerificationPipelineId pipelineId,
            List<CameraFastSnapshot> cameras,
            List<CameraId> autoCameraOrder) {
        public FastSnapshot {
            rawCatalog = Objects.requireNonNull(rawCatalog, "rawCatalog");
            pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
            cameras = immutableUniqueCameraSnapshots(cameras);
            autoCameraOrder = List.copyOf(
                    Objects.requireNonNull(autoCameraOrder, "autoCameraOrder"));
            Set<CameraId> cameraIds = new HashSet<>();
            for (CameraFastSnapshot camera : cameras) {
                cameraIds.add(camera.cameraFacts().cameraId());
                if (!camera.evidence().verificationPipelineId().equals(pipelineId)) {
                    throw new IllegalArgumentException("camera belongs to another pipeline");
                }
            }
            if (autoCameraOrder.size() != cameraIds.size()
                    || !new HashSet<>(autoCameraOrder).equals(cameraIds)) {
                throw new IllegalArgumentException("camera order must cover inventory once");
            }
        }
    }
    public record CameraFastSnapshot(
            CameraFacts cameraFacts,
            List<VideoMode> videoModes,
            List<ImageMode> imageModes,
            PipelineEvidence evidence,
            Optional<CaptureModeTuple> bestEffectiveTuple,
            long probeElapsedMillis,
            String probeDetail) {
        public CameraFastSnapshot {
            cameraFacts = Objects.requireNonNull(cameraFacts, "cameraFacts");
            videoModes = immutableUniqueInOrder(videoModes, "videoModes");
            imageModes = immutableUniqueInOrder(imageModes, "imageModes");
            evidence = Objects.requireNonNull(evidence, "evidence");
            bestEffectiveTuple = Objects.requireNonNull(
                    bestEffectiveTuple, "bestEffectiveTuple");
            if (!cameraFacts.cameraId().equals(evidence.cameraId())) {
                throw new IllegalArgumentException("camera evidence identity mismatch");
            }
            if (probeElapsedMillis < 0) {
                throw new IllegalArgumentException(
                        "probeElapsedMillis must not be negative");
            }
            probeDetail = required(probeDetail, "probeDetail");
        }
    }

    public record BenchmarkData(
            long catalogLoadMillis,
            long concurrencySchedulingMillis,
            List<RoundTiming> rounds,
            OptionalLong allFastReadyMillis,
            long totalElapsedMillis) {
        public BenchmarkData {
            if (catalogLoadMillis < 0
                    || concurrencySchedulingMillis < 0
                    || totalElapsedMillis < 0) {
                throw new IllegalArgumentException("timing must not be negative");
            }
            rounds = List.copyOf(Objects.requireNonNull(rounds, "rounds"));
            allFastReadyMillis = Objects.requireNonNull(
                    allFastReadyMillis, "allFastReadyMillis");
            if (allFastReadyMillis.orElse(0) < 0) {
                throw new IllegalArgumentException(
                        "allFastReadyMillis must not be negative");
            }
        }
    }

    public record RoundTiming(
            int round,
            List<CameraId> cameraIds,
            boolean concurrent,
            boolean singletonRetry,
            FastCameraCapabilityProbe.Completion completion,
            boolean cleanupComplete,
            long wallElapsedMillis,
            long probeElapsedMillis,
            String detail) {
        public RoundTiming {
            if (round <= 0) throw new IllegalArgumentException("round must be positive");
            cameraIds = List.copyOf(Objects.requireNonNull(cameraIds, "cameraIds"));
            if (cameraIds.isEmpty()) {
                throw new IllegalArgumentException("round camera IDs are empty");
            }
            completion = Objects.requireNonNull(completion, "completion");
            if (wallElapsedMillis < 0 || probeElapsedMillis < 0) {
                throw new IllegalArgumentException("round timing must not be negative");
            }
            detail = required(detail, "detail");
        }
    }

    private record PreparedCamera(CameraFacts cameraFacts, Request request) {}

    private record IntRange(int lower, int upper) {
        private IntRange {
            if (lower <= 0 || lower > upper) {
                throw new IllegalArgumentException("invalid normalized frame-rate range");
            }
        }

        private boolean contains(int value) { return value >= lower && value <= upper; }
    }

    private record CombinationKey(List<CameraId> cameraIds)
            implements Comparable<CombinationKey> {
        private CombinationKey(Collection<CameraId> cameraIds) {
            this(new ArrayList<>(new TreeSet<>(cameraIds)));
        }

        private CombinationKey {
            cameraIds = List.copyOf(cameraIds);
            if (cameraIds.size() < 2) {
                throw new IllegalArgumentException("concurrent combination is too small");
            }
        }

        @Override public int compareTo(CombinationKey other) {
            return compareCameraIds(cameraIds, other.cameraIds);
        }
    }

    private record DisabledCombination(
            String hardwareSignatureInput,
            VerificationPipelineId pipelineId,
            CombinationKey combination) {
        private DisabledCombination {
            hardwareSignatureInput = required(
                    hardwareSignatureInput, "hardwareSignatureInput");
            pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
            combination = Objects.requireNonNull(combination, "combination");
        }
    }

    private record ScheduledBatch(
            CombinationKey combination, List<CameraId> members) {
        private ScheduledBatch {
            combination = Objects.requireNonNull(combination, "combination");
            members = List.copyOf(Objects.requireNonNull(members, "members"));
        }
    }

    private record ScanAttempt(
            List<CameraId> cameraIds,
            BatchResult result,
            RuntimeException error,
            long wallElapsedMillis) {
        private ScanAttempt {
            cameraIds = List.copyOf(cameraIds);
            if ((result == null) == (error == null)) {
                throw new IllegalArgumentException("scan attempt needs result or error");
            }
        }

        private boolean cancelled(CancellationSignal cancellationSignal) {
            return cancellationSignal.isCancellationRequested()
                    || (result != null
                    && result.completion() == FastCameraCapabilityProbe.Completion.CANCELLED);
        }

        private String detail() {
            return result == null
                    ? "probe_exception:" + error.getClass().getSimpleName()
                    : result.detail();
        }

        private RoundTiming timing(
                int round, boolean concurrent, boolean singletonRetry) {
            FastCameraCapabilityProbe.Completion completion = result == null
                    ? FastCameraCapabilityProbe.Completion.INCOMPLETE_GLOBAL
                    : result.completion();
            return new RoundTiming(round, cameraIds, concurrent, singletonRetry,
                    completion, result != null && result.cleanupComplete(),
                    wallElapsedMillis, result == null ? 0 : result.elapsedMillis(),
                    detail());
        }
    }

    private static <T> List<T> immutableUniqueInOrder(
            Collection<T> values, String name) {
        List<T> result = new ArrayList<>();
        Set<T> seen = new HashSet<>();
        for (T value : Objects.requireNonNull(values, name)) {
            T checked = Objects.requireNonNull(value, name + " element");
            if (seen.add(checked)) result.add(checked);
        }
        return List.copyOf(result);
    }

    private static List<CameraFastSnapshot> immutableUniqueCameraSnapshots(
            Collection<CameraFastSnapshot> values) {
        Map<CameraId, CameraFastSnapshot> result = new LinkedHashMap<>();
        for (CameraFastSnapshot value : Objects.requireNonNull(values, "cameras")) {
            CameraFastSnapshot checked = Objects.requireNonNull(value, "camera");
            CameraId cameraId = checked.cameraFacts().cameraId();
            if (result.put(cameraId, checked) != null) {
                throw new IllegalArgumentException("duplicate camera snapshot");
            }
        }
        return List.copyOf(result.values());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}