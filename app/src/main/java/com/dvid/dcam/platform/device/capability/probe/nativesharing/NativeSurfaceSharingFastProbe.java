package com.dvid.dcam.platform.device.capability.probe.nativesharing;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.view.Surface;
import androidx.annotation.RequiresApi;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.platform.camera.shared.CameraPipelineIds;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class NativeSurfaceSharingFastProbe {
    public static final VerificationPipelineId PIPELINE_ID =
            CameraPipelineIds.NATIVE_SURFACE_SHARING;

    private static final long OPEN_TIMEOUT_MILLIS = 5_000;
    private static final long SESSION_TIMEOUT_MILLIS = 3_000;
    private static final long RELEASE_TIMEOUT_MILLIS = 2_000;
    private static final String SURFACE_SOURCE_CLASSES =
            "SurfaceTexture+MediaCodec|ImageReader";
    private static final String MAX_SHARED_SURFACE_COUNT = "maxSharedSurfaceCount";
    private static final String DETAIL = "detail";
    private static final String OPEN_THREAD = "open_thread";

    private final Context context;
    private final Logger logger;

    public NativeSurfaceSharingFastProbe(Context context, Logger logger) {
        Context applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Result probe(CameraId cameraId, Collection<VideoMode> videoModes,
            Collection<ImageMode> imageModes) {
        Objects.requireNonNull(cameraId, "cameraId");
        Objects.requireNonNull(videoModes, "videoModes");
        Objects.requireNonNull(imageModes, "imageModes");
        long imageProfileCount = imageModes.stream().distinct().count();
        long startedNanos = System.nanoTime();
        int apiLevel = Build.VERSION.SDK_INT;
        OptionalInt maxSharedSurfaceCount = OptionalInt.empty();

        if (apiLevel < Build.VERSION_CODES.O) {
            Result result = unavailable(new ResultContext(cameraId, apiLevel,
                    maxSharedSurfaceCount, startedNanos),
                    pipelineUnavailableReason(apiLevel, maxSharedSurfaceCount, true));
            logComplete(result, imageProfileCount);
            return result;
        }

        if (apiLevel >= Build.VERSION_CODES.P) {
            try {
                maxSharedSurfaceCount = OptionalInt.of(maxSharedSurfaceCount());
            } catch (RuntimeException error) {
                Result result = incomplete(new ResultContext(cameraId, apiLevel,
                                maxSharedSurfaceCount, startedNanos),
                        List.of(), List.of(), Completion.INCOMPLETE_GLOBAL,
                        "shared_surface_count_query:" + error.getClass().getSimpleName());
                logger.warn(logPrefix(cameraId)
                        + " stage=eligibility result=incomplete_global apiLevel=" + apiLevel,
                        error);
                logComplete(result, imageProfileCount);
                return result;
            }
            if (maxSharedSurfaceCount.orElseThrow() < 2) {
                Result result = unavailable(new ResultContext(cameraId, apiLevel,
                                maxSharedSurfaceCount, startedNanos),
                        pipelineUnavailableReason(apiLevel, maxSharedSurfaceCount, true));
                logComplete(result, imageProfileCount);
                return result;
            }
        }

        try {
            if (!hasH264Encoder()) {
                Result result = unavailable(new ResultContext(cameraId, apiLevel,
                                maxSharedSurfaceCount, startedNanos),
                        pipelineUnavailableReason(apiLevel, maxSharedSurfaceCount, false));
                logComplete(result, imageProfileCount);
                return result;
            }
        } catch (RuntimeException error) {
            Result result = incomplete(new ResultContext(cameraId, apiLevel,
                            maxSharedSurfaceCount, startedNanos),
                    List.of(), List.of(), Completion.INCOMPLETE_GLOBAL,
                    "encoder_catalog:" + error.getClass().getSimpleName());
            logger.warn(logPrefix(cameraId)
                    + " stage=eligibility result=incomplete_global apiLevel=" + apiLevel,
                    error);
            logComplete(result, imageProfileCount);
            return result;
        }

        OpenedCamera camera;
        try {
            camera = OpenedCamera.open(context, cameraId);
        } catch (ProbeFailure failure) {
            Result result = incomplete(new ResultContext(cameraId, apiLevel,
                            maxSharedSurfaceCount, startedNanos),
                    List.of(), List.of(), failure.completion(), failure.detail());
            logFailure(cameraId, "open", failure);
            logComplete(result, imageProfileCount);
            return result;
        }

        Result result = runMatrix(apiLevel, maxSharedSurfaceCount, cameraId,
                videoModes, imageModes,
                (tuple, attempt, confirmation) -> queryTuple(camera, tuple), startedNanos);

        ProbeFailure releaseFailure = camera.release();
        if (releaseFailure != null) {
            result = incomplete(new ResultContext(cameraId, apiLevel,
                            maxSharedSurfaceCount, startedNanos),
                    result.evidence().rawFastCandidates(), result.attempts(),
                    releaseFailure.completion(), releaseFailure.detail());
            logFailure(cameraId, "release", releaseFailure);
        }
        logComplete(result, imageProfileCount);
        return result;
    }
    static Result runMatrix(int apiLevel, OptionalInt maxSharedSurfaceCount,
            CameraId cameraId, Collection<VideoMode> videoModes,
            Collection<ImageMode> imageModes, TupleQuery query, long startedNanos) {
        MatrixContext context = new MatrixContext(apiLevel, maxSharedSurfaceCount,
                cameraId, query, startedNanos);
        TreeSet<VideoMode> videos = immutableSorted(videoModes, "videoModes");
        TreeSet<ImageMode> images = immutableSorted(imageModes, "imageModes");
        List<CaptureModeTuple> universe = universe(videos, images);
        Result incomplete = runInitialAttempts(context, universe);
        if (incomplete != null) return incomplete;
        incomplete = confirmFpsRows(context, videos, universe);
        if (incomplete != null) return incomplete;
        incomplete = confirmVideoModes(context, videos, universe);
        if (incomplete != null) return incomplete;
        return complete(context, supportedCandidates(context.cameraId, context.states));
    }

    private static List<CaptureModeTuple> universe(
            Collection<VideoMode> videos, Collection<ImageMode> images) {
        List<CaptureModeTuple> result = new ArrayList<>();
        for (VideoMode video : videos) {
            for (ImageMode image : images) result.add(new CaptureModeTuple(video, image));
        }
        return result;
    }

    private static Result runInitialAttempts(MatrixContext context,
            List<CaptureModeTuple> universe) {
        for (CaptureModeTuple tuple : universe) {
            Result incomplete = attempt(context, tuple, 1, "initial");
            if (incomplete != null) return incomplete;
        }
        return null;
    }

    private static Result confirmFpsRows(MatrixContext context,
            Collection<VideoMode> videos, List<CaptureModeTuple> universe) {
        for (VideoMode video : videos) {
            List<CaptureModeTuple> row = tuplesForVideo(universe, video);
            if (!allRejected(row, context.states)) continue;
            Result incomplete = confirmRejected(context, row, "fps_row");
            if (incomplete != null) return incomplete;
        }
        return null;
    }

    private static Result confirmVideoModes(MatrixContext context,
            Collection<VideoMode> videos, List<CaptureModeTuple> universe) {
        TreeSet<StandardResolution> resolutions = new TreeSet<>();
        for (VideoMode video : videos) resolutions.add(video.resolution());
        for (StandardResolution resolution : resolutions) {
            List<CaptureModeTuple> mode = tuplesForResolution(universe, resolution);
            if (!allRejected(mode, context.states)) continue;
            Result incomplete = confirmRejected(context, mode, "video_mode");
            if (incomplete != null) return incomplete;
        }
        return null;
    }

    private static List<CaptureModeTuple> tuplesForVideo(
            List<CaptureModeTuple> universe, VideoMode video) {
        return universe.stream()
                .filter(tuple -> tuple.videoMode().equals(video))
                .collect(java.util.stream.Collectors.toList());
    }

    private static List<CaptureModeTuple> tuplesForResolution(
            List<CaptureModeTuple> universe, StandardResolution resolution) {
        return universe.stream()
                .filter(tuple -> tuple.videoMode().resolution().equals(resolution))
                .collect(java.util.stream.Collectors.toList());
    }

    private static Result confirmRejected(MatrixContext context,
            List<CaptureModeTuple> tuples, String confirmation) {
        for (CaptureModeTuple tuple : tuples) {
            if (context.states.get(tuple) != AttemptResult.REJECTED
                    || !context.confirmed.add(tuple)) continue;
            Result incomplete = attempt(context, tuple, 2, confirmation);
            if (incomplete != null) return incomplete;
        }
        return null;
    }

    private static Result attempt(MatrixContext context, CaptureModeTuple tuple,
            int attempt, String confirmation) {
        long attemptStartedNanos = System.nanoTime();
        ProbeDecision decision = Objects.requireNonNull(
                context.query.query(tuple, attempt, confirmation), "probe decision");
        long elapsedMillis = elapsedMillis(attemptStartedNanos);
        Attempt recorded = new Attempt(tuple, decision.result(), attempt, confirmation,
                decision.cameraOutputCount(), decision.sharedSurfaceCount(),
                decision.surfaceSourceClasses(), elapsedMillis, decision.detail());
        context.attempts.add(recorded);

        if (decision.result() == AttemptResult.SUPPORTED
                || decision.result() == AttemptResult.REJECTED) {
            context.states.put(tuple, decision.result());
            return null;
        }
        return incomplete(context.resultContext,
                supportedCandidates(context.cameraId, context.states), context.attempts,
                completion(decision.result()), decision.detail());
    }

    private ProbeDecision queryTuple(OpenedCamera camera, CaptureModeTuple tuple) {
        TupleResources resources;
        try {
            camera.ensureAvailable();
            resources = TupleResources.open(tuple);
        } catch (TupleRejected rejection) {
            return ProbeDecision.rejectedBeforeTopology(rejection.getMessage());
        } catch (ProbeFailure failure) {
            return ProbeDecision.failureBeforeTopology(failure);
        } catch (SecurityException error) {
            return ProbeDecision.unbuilt(AttemptResult.BLOCKED_EXTERNAL,
                    "surface_setup:" + error.getClass().getSimpleName());
        } catch (RuntimeException error) {
            return ProbeDecision.unbuilt(AttemptResult.INCOMPLETE_GLOBAL,
                    "surface_setup:" + error.getClass().getSimpleName());
        }

        ProbeDecision decision;
        try {
            boolean supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? camera.supports(resources.outputs())
                    : camera.configures(resources.outputs());
            camera.ensureAvailable();
            decision = supported
                    ? ProbeDecision.supported("session_supported")
                    : ProbeDecision.rejected("session_rejected");
        } catch (ProbeFailure failure) {
            decision = ProbeDecision.failure(failure);
        } catch (SecurityException error) {
            decision = ProbeDecision.blocked("session_query:" + error.getClass().getSimpleName());
        } catch (CameraAccessException error) {
            decision = ProbeDecision.failure(cameraAccessFailure("session_query", error));
        } catch (RuntimeException error) {
            decision = ProbeDecision.global("session_query:" + error.getClass().getSimpleName());
        }

        ProbeFailure releaseFailure = resources.release();
        if (releaseFailure != null) return ProbeDecision.failure(releaseFailure);
        return decision;
    }

    private static boolean allRejected(List<CaptureModeTuple> tuples,
            Map<CaptureModeTuple, AttemptResult> states) {
        return !tuples.isEmpty() && tuples.stream()
                .allMatch(tuple -> states.get(tuple) == AttemptResult.REJECTED);
    }

    private static List<CandidateKey> supportedCandidates(CameraId cameraId,
            Map<CaptureModeTuple, AttemptResult> states) {
        List<CandidateKey> supported = new ArrayList<>();
        for (Map.Entry<CaptureModeTuple, AttemptResult> entry : states.entrySet()) {
            if (entry.getValue() == AttemptResult.SUPPORTED) {
                supported.add(CandidateKey.forTuple(
                        cameraId, VideoCodec.H264, PIPELINE_ID, entry.getKey()));
            }
        }
        return List.copyOf(supported);
    }

    private static <T extends Comparable<? super T>> TreeSet<T> immutableSorted(
            Collection<T> values, String name) {
        TreeSet<T> result = new TreeSet<>();
        for (T value : Objects.requireNonNull(values, name)) {
            result.add(Objects.requireNonNull(value, name + " element"));
        }
        return result;
    }
    private static Result complete(MatrixContext context,
            Collection<CandidateKey> candidates) {
        return result(context.resultContext, PipelineAvailability.AVAILABLE,
                Completion.COMPLETE, candidates, context.attempts, "matrix_complete");
    }

    private static Result unavailable(ResultContext context, String detail) {
        return result(context, PipelineAvailability.UNAVAILABLE,
                Completion.PIPELINE_UNAVAILABLE, List.of(), List.of(), detail);
    }

    private static Result incomplete(ResultContext context,
            Collection<CandidateKey> candidates, Collection<Attempt> attempts,
            Completion completion, String detail) {
        return result(context, PipelineAvailability.UNKNOWN, completion,
                candidates, attempts, detail);
    }

    private static Result result(ResultContext context, PipelineAvailability availability,
            Completion completion, Collection<CandidateKey> candidates,
            Collection<Attempt> attempts, String detail) {
        PipelineEvidence evidence = new PipelineEvidence(context.cameraId, VideoCodec.H264,
                PIPELINE_ID, availability, candidates, List.of());
        return new Result(evidence, completion, context.apiLevel,
                context.maxSharedSurfaceCount, List.copyOf(attempts),
                elapsedMillis(context.startedNanos), detail);
    }

    private static Completion completion(AttemptResult result) {
        return switch (result) {
            case INCOMPLETE_TRANSIENT -> Completion.INCOMPLETE_TRANSIENT;
            case INCOMPLETE_GLOBAL -> Completion.INCOMPLETE_GLOBAL;
            case BLOCKED_EXTERNAL -> Completion.BLOCKED_EXTERNAL;
            case SUPPORTED, REJECTED -> throw new IllegalArgumentException(
                    "terminal fast result is not incomplete");
        };
    }

    static String pipelineUnavailableReason(int apiLevel,
            OptionalInt maxSharedSurfaceCount, boolean h264EncoderAvailable) {
        Objects.requireNonNull(maxSharedSurfaceCount, MAX_SHARED_SURFACE_COUNT);
        if (apiLevel < Build.VERSION_CODES.O) return "api_below_26";
        if (apiLevel >= Build.VERSION_CODES.P
                && maxSharedSurfaceCount.isPresent()
                && maxSharedSurfaceCount.orElseThrow() < 2) {
            return "max_shared_surface_count_below_2";
        }
        return h264EncoderAvailable ? null : "h264_encoder_unavailable";
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private int maxSharedSurfaceCount() {
        SurfaceTexture texture = new SurfaceTexture(false);
        texture.setDefaultBufferSize(1, 1);
        Surface surface = new Surface(texture);
        try {
            return new OutputConfiguration(surface).getMaxSharedSurfaceCount();
        } finally {
            surface.release();
            texture.release();
        }
    }

    private static boolean hasH264Encoder() {
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.ALL_CODECS)
                .getCodecInfos()) {
            if (!codec.isEncoder()) continue;
            for (String type : codec.getSupportedTypes()) {
                if (MediaFormat.MIMETYPE_VIDEO_AVC.equalsIgnoreCase(type)) return true;
            }
        }
        return false;
    }

    private void logComplete(Result result, long imageProfileCount) {
        logger.info(completeLog(result, imageProfileCount));
    }

    static String completeLog(Result result, long imageProfileCount) {
        PipelineEvidence evidence = result.evidence();
        return logPrefix(evidence.cameraId())
                + " stage=complete result=" + completionId(result.completion())
                + " apiLevel=" + result.apiLevel()
                + " maxSharedSurfaceCount=" + optional(result.maxSharedSurfaceCount())
                + " vfProfileCount=" + vfProfileCount(evidence)
                + " imageProfileCount=" + fastBuildImageProfileCount(evidence, imageProfileCount)
                + " tupleProfileCount=" + tupleProfileCount(evidence)
                + " attemptCount=" + result.attempts().size()
                + " elapsedMs=" + result.elapsedMillis()
                + " detail=" + result.detail();
    }

    private static long vfProfileCount(PipelineEvidence evidence) {
        return evidence.rawFastCandidates().stream()
                .filter(candidate -> candidate.videoMode().isPresent())
                .map(candidate -> candidate.videoMode().orElseThrow())
                .distinct()
                .count();
    }

    private static long fastBuildImageProfileCount(
            PipelineEvidence evidence, long imageProfileCount) {
        return evidence.availability() == PipelineAvailability.UNAVAILABLE
                ? 0 : imageProfileCount;
    }

    private static long tupleProfileCount(PipelineEvidence evidence) {
        return evidence.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .count();
    }

    private void logFailure(CameraId cameraId, String stage, ProbeFailure failure) {
        logger.warn(logPrefix(cameraId)
                + " stage=" + stage
                + " result=" + completionId(failure.completion())
                + " detail=" + failure.detail(), failure);
    }

    private static String logPrefix(CameraId cameraId) {
        return "camera_fast_probe cameraId=" + cameraId
                + " pipeline=" + PIPELINE_ID
                + " codec=h264";
    }

    private static String optional(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.orElseThrow()) : "unknown";
    }


    private static String completionId(Completion completion) {
        return completion.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static ProbeFailure cameraAccessFailure(String stage, CameraAccessException error) {
        return switch (error.getReason()) {
            case CameraAccessException.CAMERA_DISABLED -> ProbeFailure.blocked(
                    stage + ":camera_disabled", error);
            case CameraAccessException.CAMERA_IN_USE,
                    CameraAccessException.MAX_CAMERAS_IN_USE,
                    CameraAccessException.CAMERA_DISCONNECTED -> ProbeFailure.transientFailure(
                    stage + ":camera_access_" + error.getReason(), error);
            default -> ProbeFailure.global(
                    stage + ":camera_access_" + error.getReason(), error);
        };
    }

    public enum Completion {
        COMPLETE,
        PIPELINE_UNAVAILABLE,
        INCOMPLETE_TRANSIENT,
        INCOMPLETE_GLOBAL,
        BLOCKED_EXTERNAL
    }

    public enum AttemptResult {
        SUPPORTED,
        REJECTED,
        INCOMPLETE_TRANSIENT,
        INCOMPLETE_GLOBAL,
        BLOCKED_EXTERNAL
    }

    public record Attempt(
            CaptureModeTuple tuple,
            AttemptResult result,
            int attempt,
            String confirmation,
            int cameraOutputCount,
            int sharedSurfaceCount,
            String surfaceSourceClasses,
            long elapsedMillis,
            String detail) {
        public Attempt {
            Objects.requireNonNull(tuple, "tuple");
            Objects.requireNonNull(result, "result");
            if (attempt <= 0) throw new IllegalArgumentException("attempt must be positive");
            required(confirmation, "confirmation");
            if (cameraOutputCount < 0 || sharedSurfaceCount < 0) {
                throw new IllegalArgumentException("surface counts must not be negative");
            }
            required(surfaceSourceClasses, "surfaceSourceClasses");
            if (elapsedMillis < 0) {
                throw new IllegalArgumentException("elapsedMillis must not be negative");
            }
            required(detail, DETAIL);
        }
    }

    public record Result(
            PipelineEvidence evidence,
            Completion completion,
            int apiLevel,
            OptionalInt maxSharedSurfaceCount,
            List<Attempt> attempts,
            long elapsedMillis,
            String detail) {
        public Result {
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(completion, "completion");
            if (apiLevel <= 0) throw new IllegalArgumentException("apiLevel must be positive");
            Objects.requireNonNull(maxSharedSurfaceCount, MAX_SHARED_SURFACE_COUNT);
            attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
            if (elapsedMillis < 0) {
                throw new IllegalArgumentException("elapsedMillis must not be negative");
            }
            required(detail, DETAIL);
        }

        public boolean complete() { return completion == Completion.COMPLETE; }
    }

    @FunctionalInterface
    interface TupleQuery {
        ProbeDecision query(CaptureModeTuple tuple, int attempt, String confirmation);
    }

    private record ResultContext(
            CameraId cameraId,
            int apiLevel,
            OptionalInt maxSharedSurfaceCount,
            long startedNanos) {
    }

    private static final class MatrixContext {
        private final ResultContext resultContext;
        private final CameraId cameraId;
        private final TupleQuery query;
        private final Map<CaptureModeTuple, AttemptResult> states = new TreeMap<>();
        private final List<Attempt> attempts = new ArrayList<>();
        private final Set<CaptureModeTuple> confirmed = new HashSet<>();

        private MatrixContext(int apiLevel, OptionalInt maxSharedSurfaceCount,
                CameraId cameraId, TupleQuery query, long startedNanos) {
            OptionalInt checkedMaxSharedSurfaceCount = Objects.requireNonNull(
                    maxSharedSurfaceCount, MAX_SHARED_SURFACE_COUNT);
            this.cameraId = Objects.requireNonNull(cameraId, "cameraId");
            this.query = Objects.requireNonNull(query, "query");
            this.resultContext = new ResultContext(this.cameraId, apiLevel,
                    checkedMaxSharedSurfaceCount,
                    startedNanos);
        }
    }

    record ProbeDecision(
            AttemptResult result,
            int cameraOutputCount,
            int sharedSurfaceCount,
            String surfaceSourceClasses,
            String detail) {
        ProbeDecision {
            Objects.requireNonNull(result, "result");
            if (cameraOutputCount < 0 || sharedSurfaceCount < 0) {
                throw new IllegalArgumentException("surface counts must not be negative");
            }
            required(surfaceSourceClasses, "surfaceSourceClasses");
            required(detail, DETAIL);
        }

        static ProbeDecision supported(String detail) {
            return topology(AttemptResult.SUPPORTED, detail);
        }

        static ProbeDecision rejectedBeforeTopology(String detail) {
            return unbuilt(AttemptResult.REJECTED, detail);
        }

        static ProbeDecision failureBeforeTopology(ProbeFailure failure) {
            return switch (failure.completion()) {
                case INCOMPLETE_TRANSIENT -> unbuilt(
                        AttemptResult.INCOMPLETE_TRANSIENT, failure.detail());
                case INCOMPLETE_GLOBAL -> unbuilt(
                        AttemptResult.INCOMPLETE_GLOBAL, failure.detail());
                case BLOCKED_EXTERNAL -> unbuilt(
                        AttemptResult.BLOCKED_EXTERNAL, failure.detail());
                case COMPLETE, PIPELINE_UNAVAILABLE -> throw new IllegalArgumentException(
                        "invalid tuple failure completion");
            };
        }

        static ProbeDecision rejected(String detail) {
            return topology(AttemptResult.REJECTED, detail);
        }

        static ProbeDecision failure(ProbeFailure failure) {
            return switch (failure.completion()) {
                case INCOMPLETE_TRANSIENT -> transientFailure(failure.detail());
                case INCOMPLETE_GLOBAL -> global(failure.detail());
                case BLOCKED_EXTERNAL -> blocked(failure.detail());
                case COMPLETE, PIPELINE_UNAVAILABLE -> throw new IllegalArgumentException(
                        "invalid tuple failure completion");
            };
        }

        static ProbeDecision transientFailure(String detail) {
            return topology(AttemptResult.INCOMPLETE_TRANSIENT, detail);
        }

        static ProbeDecision global(String detail) {
            return topology(AttemptResult.INCOMPLETE_GLOBAL, detail);
        }

        static ProbeDecision blocked(String detail) {
            return topology(AttemptResult.BLOCKED_EXTERNAL, detail);
        }

        static ProbeDecision unbuilt(AttemptResult result, String detail) {
            return new ProbeDecision(result, 0, 0, "unbuilt", detail);
        }

        private static ProbeDecision topology(AttemptResult result, String detail) {
            return new ProbeDecision(result, 2, 2, SURFACE_SOURCE_CLASSES, detail);
        }
    }

    static final class OpenGuard<T> {
        private T value;
        private boolean abandoned;

        synchronized boolean accept(T candidate) {
            if (abandoned) return false;
            value = candidate;
            return true;
        }

        synchronized T abandon() {
            abandoned = true;
            T owned = value;
            value = null;
            return owned;
        }

        synchronized T value() { return value; }
        synchronized boolean abandoned() { return abandoned; }
    }

    private static final class OpenedCamera {
        private final CameraDevice camera;
        private final HandlerThread thread;
        private final Handler handler;
        private final AtomicReference<ProbeFailure> failure;
        private final CountDownLatch closed;

        private OpenedCamera(CameraDevice camera, HandlerThread thread, Handler handler,
                AtomicReference<ProbeFailure> failure, CountDownLatch closed) {
            this.camera = camera;
            this.thread = thread;
            this.handler = handler;
            this.failure = failure;
            this.closed = closed;
        }

        private static ProbeFailure firstFailure(ProbeFailure first, ProbeFailure second) {
            return first == null ? second : first;
        }

        private static OpenedCamera open(Context context, CameraId cameraId) throws ProbeFailure {
            CameraManager manager = context.getSystemService(CameraManager.class);
            if (manager == null) throw ProbeFailure.global("open:camera_manager_unavailable", null);

            OpenRequest request = new OpenRequest(cameraId);
            try {
                manager.openCamera(cameraId.value(), request.callback(), request.handler);
                request.requestSubmitted = true;
                return request.awaitOpened();
            } catch (SecurityException error) {
                return failOpen(request, ProbeFailure.blocked("open:camera_permission", error));
            } catch (CameraAccessException error) {
                return failOpen(request, cameraAccessFailure("open", error));
            } catch (InterruptedException error) {
                return failOpenInterrupted(request, error);
            } catch (RuntimeException error) {
                return failOpen(request,
                        ProbeFailure.global("open:" + error.getClass().getSimpleName(), error));
            }
        }

        private static OpenedCamera failOpen(OpenRequest request, ProbeFailure failure)
                throws ProbeFailure {
            ProbeFailure cleanup = request.cleanupAfterFailure();
            if (cleanup != null) throw cleanup;
            throw failure;
        }

        private static OpenedCamera failOpenInterrupted(OpenRequest request,
                InterruptedException error) throws ProbeFailure {
            ProbeFailure cleanup = request.cleanupAfterFailure();
            Thread.currentThread().interrupt();
            if (cleanup != null) throw cleanup;
            throw ProbeFailure.transientFailure("open:interrupted", error);
        }

        private static final class OpenRequest {
            private final HandlerThread thread;
            private final Handler handler;
            private final CountDownLatch opened = new CountDownLatch(1);
            private final CountDownLatch closed = new CountDownLatch(1);
            private final OpenGuard<CameraDevice> guard = new OpenGuard<>();
            private final AtomicReference<ProbeFailure> failure = new AtomicReference<>();
            private boolean requestSubmitted;

            private OpenRequest(CameraId cameraId) {
                thread = new HandlerThread("dcam-native-fast-" + cameraId.value());
                thread.start();
                handler = new Handler(thread.getLooper());
            }

            private static ProbeFailure cameraStateFailure(String stage, int errorCode) {
                return switch (errorCode) {
                    case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> ProbeFailure.blocked(
                            stage + ":camera_disabled", null);
                    case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE,
                            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE ->
                            ProbeFailure.transientFailure(
                                    stage + ":camera_error_" + errorCode, null);
                    default -> ProbeFailure.global(stage + ":camera_error_" + errorCode, null);
                };
            }

            private CameraDevice.StateCallback callback() {
                return new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice value) {
                        if (!guard.accept(value)) value.close();
                        opened.countDown();
                    }

                    @Override public void onDisconnected(CameraDevice value) {
                        failure.compareAndSet(null, ProbeFailure.transientFailure(
                                "camera_state:disconnected", null));
                        value.close();
                        opened.countDown();
                    }

                    @Override public void onError(CameraDevice value, int errorCode) {
                        failure.compareAndSet(null,
                                cameraStateFailure("camera_state", errorCode));
                        value.close();
                        opened.countDown();
                    }

                    @Override public void onClosed(CameraDevice value) {
                        closed.countDown();
                        if (guard.abandoned()) thread.quitSafely();
                    }
                };
            }

            private OpenedCamera awaitOpened() throws ProbeFailure, InterruptedException {
                if (!opened.await(OPEN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    ProbeFailure cleanup = abandonOpen();
                    if (cleanup != null) throw cleanup;
                    throw ProbeFailure.transientFailure("open:timeout", null);
                }
                CameraDevice openedCamera = guard.value();
                ProbeFailure openFailure = failure.get();
                if (openedCamera == null || openFailure != null) {
                    ProbeFailure cleanup = abandonOpen();
                    if (cleanup != null) throw cleanup;
                    throw openFailure == null
                            ? ProbeFailure.global("open:unknown_failure", null)
                            : openFailure;
                }
                return new OpenedCamera(openedCamera, thread, handler, failure, closed);
            }

            private ProbeFailure cleanupAfterFailure() {
                return requestSubmitted
                        ? abandonOpen()
                        : stopThread(thread, OPEN_THREAD);
            }

            private ProbeFailure abandonOpen() {
                ProbeFailure cleanupFailure = null;
                CameraDevice owned = guard.abandon();
                if (owned != null) {
                    try {
                        owned.close();
                    } catch (RuntimeException error) {
                        cleanupFailure = ProbeFailure.transientFailure(
                                "open_cleanup:" + error.getClass().getSimpleName(), error);
                    }
                }
                if (closed.getCount() == 0) {
                    cleanupFailure = firstFailure(
                            cleanupFailure, stopThread(thread, OPEN_THREAD));
                }
                return cleanupFailure;
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private boolean supports(List<OutputConfiguration> outputs)
                throws CameraAccessException, ProbeFailure {
            ensureAvailable();
            SessionConfiguration configuration = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputs,
                    command -> handler.post(command),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession session) {
                            // The support query does not create a live session.
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession session) {
                            // The support query does not create a live session.
                        }
                    });
            return camera.isSessionConfigurationSupported(configuration);
        }

        private boolean configures(List<OutputConfiguration> outputs) throws ProbeFailure {
            ensureAvailable();
            CountDownLatch configured = new CountDownLatch(1);
            CountDownLatch sessionClosed = new CountDownLatch(1);
            AtomicReference<Boolean> supported = new AtomicReference<>();
            AtomicReference<CameraCaptureSession> session = new AtomicReference<>();
            try {
                camera.createCaptureSessionByOutputConfigurations(outputs,
                        new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession value) {
                                session.set(value);
                                supported.set(true);
                                configured.countDown();
                            }

                            @Override public void onConfigureFailed(CameraCaptureSession value) {
                                session.set(value);
                                supported.set(false);
                                value.close();
                                configured.countDown();
                            }

                            @Override public void onClosed(CameraCaptureSession value) {
                                sessionClosed.countDown();
                            }
                        }, handler);
                if (!configured.await(SESSION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    CameraCaptureSession value = session.get();
                    if (value != null) value.close();
                    throw ProbeFailure.transientFailure("session_query:timeout", null);
                }
                if (Boolean.TRUE.equals(supported.get())) session.get().close();
                if (session.get() != null
                        && !sessionClosed.await(RELEASE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    throw ProbeFailure.transientFailure("session_release:timeout", null);
                }
                ensureAvailable();
                return Boolean.TRUE.equals(supported.get());
            } catch (CameraAccessException error) {
                throw cameraAccessFailure("session_query", error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw ProbeFailure.transientFailure("session_query:interrupted", error);
            }
        }

        private void ensureAvailable() throws ProbeFailure {
            ProbeFailure stateFailure = failure.get();
            if (stateFailure != null) throw stateFailure;
        }

        private ProbeFailure release() {
            ProbeFailure releaseFailure = null;
            try {
                camera.close();
                if (!closed.await(RELEASE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    releaseFailure = ProbeFailure.transientFailure(
                            "release:camera_close_timeout", null);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                releaseFailure = ProbeFailure.transientFailure("release:interrupted", error);
            } catch (RuntimeException error) {
                releaseFailure = ProbeFailure.transientFailure(
                        "release:" + error.getClass().getSimpleName(), error);
            }
            return firstFailure(releaseFailure, stopThread(thread, "release_thread"));
        }

        private static ProbeFailure stopThread(HandlerThread thread, String detail) {
            thread.quitSafely();
            try {
                thread.join(RELEASE_TIMEOUT_MILLIS);
                if (thread.isAlive()) {
                    thread.quit();
                    thread.join(RELEASE_TIMEOUT_MILLIS);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return ProbeFailure.transientFailure(detail + ":interrupted", error);
            }
            return thread.isAlive()
                    ? ProbeFailure.transientFailure(detail + ":timeout", null)
                    : null;
        }
    }

    private static final class TupleResources {
        private final SurfaceTexture previewTexture;
        private final Surface previewSurface;
        private final MediaCodec encoder;
        private final Surface encoderSurface;
        private final ImageReader imageReader;
        private final List<OutputConfiguration> outputs;

        private TupleResources(SurfaceTexture previewTexture, Surface previewSurface,
                MediaCodec encoder, Surface encoderSurface, ImageReader imageReader,
                List<OutputConfiguration> outputs) {
            this.previewTexture = previewTexture;
            this.previewSurface = previewSurface;
            this.encoder = encoder;
            this.encoderSurface = encoderSurface;
            this.imageReader = imageReader;
            this.outputs = outputs;
        }

        private static TupleResources open(CaptureModeTuple tuple)
                throws TupleRejected, ProbeFailure {
            int videoWidth = tuple.videoMode().resolution().actual().width();
            int videoHeight = tuple.videoMode().resolution().actual().height();
            int imageWidth = tuple.imageMode().resolution().actual().width();
            int imageHeight = tuple.imageMode().resolution().actual().height();

            EncoderSelection selection = encoderSelection(tuple.videoMode());
            SurfaceTexture previewTexture = null;
            Surface previewSurface = null;
            MediaCodec encoder = null;
            Surface encoderSurface = null;
            ImageReader imageReader = null;
            try {
                previewTexture = new SurfaceTexture(false);
                previewTexture.setDefaultBufferSize(videoWidth, videoHeight);
                previewSurface = new Surface(previewTexture);

                encoder = MediaCodec.createByCodecName(selection.name());
                MediaFormat format = MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, selection.bitrate());
                format.setInteger(MediaFormat.KEY_FRAME_RATE,
                        tuple.videoMode().framesPerSecond());
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                encoderSurface = encoder.createInputSurface();

                imageReader = ImageReader.newInstance(
                        imageWidth, imageHeight, ImageFormat.JPEG, 2);
                OutputConfiguration shared = new OutputConfiguration(previewSurface);
                shared.enableSurfaceSharing();
                shared.addSurface(encoderSurface);
                OutputConfiguration jpeg = new OutputConfiguration(imageReader.getSurface());
                return new TupleResources(previewTexture, previewSurface, encoder,
                        encoderSurface, imageReader, List.of(shared, jpeg));
            } catch (MediaCodec.CodecException error) {
                release(previewSurface, previewTexture, encoderSurface, encoder, imageReader);
                if (error.isTransient() || error.isRecoverable()) {
                    throw ProbeFailure.transientFailure(
                            "encoder_setup:" + error.getDiagnosticInfo(), error);
                }
                throw new TupleRejected("encoder_setup:" + error.getDiagnosticInfo());
            } catch (IllegalArgumentException error) {
                release(previewSurface, previewTexture, encoderSurface, encoder, imageReader);
                throw new TupleRejected("surface_setup:" + error.getClass().getSimpleName());
            } catch (IOException error) {
                release(previewSurface, previewTexture, encoderSurface, encoder, imageReader);
                throw ProbeFailure.transientFailure("encoder_create:io", error);
            } catch (RuntimeException error) {
                release(previewSurface, previewTexture, encoderSurface, encoder, imageReader);
                throw error;
            }
        }

        private static EncoderSelection encoderSelection(VideoMode videoMode)
                throws TupleRejected {
            int width = videoMode.resolution().actual().width();
            int height = videoMode.resolution().actual().height();
            int framesPerSecond = videoMode.framesPerSecond();
            for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.ALL_CODECS)
                    .getCodecInfos()) {
                Optional<EncoderSelection> selection = supportedEncoderSelection(
                        codec, width, height, framesPerSecond);
                if (selection.isPresent()) return selection.orElseThrow();
            }
            throw new TupleRejected("encoder_setup:no_matching_h264_encoder");
        }

        private static Optional<EncoderSelection> supportedEncoderSelection(
                MediaCodecInfo codec, int width, int height, int framesPerSecond) {
            if (!codec.isEncoder()) return Optional.empty();
            MediaCodecInfo.CodecCapabilities capabilities;
            try {
                capabilities = codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
            MediaCodecInfo.VideoCapabilities video = capabilities.getVideoCapabilities();
            if (video == null) return Optional.empty();
            try {
                if (!video.areSizeAndRateSupported(width, height, framesPerSecond)) {
                    return Optional.empty();
                }
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
            Range<Integer> bitrates = video.getBitrateRange();
            long target = (long) width * height * framesPerSecond / 4L;
            if (target < 1L) target = 1L;
            long upperBoundedTarget = target > bitrates.getUpper()
                    ? bitrates.getUpper() : target;
            long boundedTarget = upperBoundedTarget < bitrates.getLower()
                    ? bitrates.getLower() : upperBoundedTarget;
            return Optional.of(new EncoderSelection(codec.getName(), (int) boundedTarget));
        }

        private List<OutputConfiguration> outputs() { return outputs; }

        private ProbeFailure release() {
            try {
                release(previewSurface, previewTexture, encoderSurface, encoder, imageReader);
                return null;
            } catch (RuntimeException error) {
                return ProbeFailure.transientFailure(
                        "tuple_release:" + error.getClass().getSimpleName(), error);
            }
        }

        private static void release(Surface previewSurface, SurfaceTexture previewTexture,
                Surface encoderSurface, MediaCodec encoder, ImageReader imageReader) {
            RuntimeException failure = release(previewSurface);
            failure = firstRuntimeFailure(failure, release(previewTexture));
            failure = firstRuntimeFailure(failure, release(encoderSurface));
            failure = firstRuntimeFailure(failure, release(encoder));
            failure = firstRuntimeFailure(failure, release(imageReader));
            if (failure != null) throw failure;
        }

        private static RuntimeException release(Surface surface) {
            if (surface == null) return null;
            try {
                surface.release();
                return null;
            } catch (RuntimeException error) {
                return error;
            }
        }

        private static RuntimeException release(SurfaceTexture texture) {
            if (texture == null) return null;
            try {
                texture.release();
                return null;
            } catch (RuntimeException error) {
                return error;
            }
        }

        private static RuntimeException release(MediaCodec encoder) {
            if (encoder == null) return null;
            try {
                encoder.release();
                return null;
            } catch (RuntimeException error) {
                return error;
            }
        }

        private static RuntimeException release(ImageReader imageReader) {
            if (imageReader == null) return null;
            try {
                imageReader.close();
                return null;
            } catch (RuntimeException error) {
                return error;
            }
        }

        private static RuntimeException firstRuntimeFailure(
                RuntimeException first, RuntimeException second) {
            return first == null ? second : first;
        }
    }

    private record EncoderSelection(String name, int bitrate) {
        private EncoderSelection {
            required(name, "name");
            if (bitrate <= 0) throw new IllegalArgumentException("bitrate must be positive");
        }
    }

    private static final class TupleRejected extends Exception {
        private TupleRejected(String detail) { super(detail); }
    }

    private static final class ProbeFailure extends Exception {
        private final Completion completion;
        private final String detail;

        private ProbeFailure(Completion completion, String detail, Throwable cause) {
            super(detail, cause);
            this.completion = completion;
            this.detail = detail;
        }

        private static ProbeFailure transientFailure(String detail, Throwable cause) {
            return new ProbeFailure(Completion.INCOMPLETE_TRANSIENT, detail, cause);
        }

        private static ProbeFailure global(String detail, Throwable cause) {
            return new ProbeFailure(Completion.INCOMPLETE_GLOBAL, detail, cause);
        }

        private static ProbeFailure blocked(String detail, Throwable cause) {
            return new ProbeFailure(Completion.BLOCKED_EXTERNAL, detail, cause);
        }

        private Completion completion() { return completion; }
        private String detail() { return detail; }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
