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
            Result result = unavailable(cameraId, apiLevel, maxSharedSurfaceCount,
                    pipelineUnavailableReason(apiLevel, maxSharedSurfaceCount, true),
                    startedNanos);
            logComplete(result, imageProfileCount);
            return result;
        }

        if (apiLevel >= Build.VERSION_CODES.P) {
            try {
                maxSharedSurfaceCount = OptionalInt.of(maxSharedSurfaceCount());
            } catch (RuntimeException error) {
                Result result = incomplete(cameraId, apiLevel, maxSharedSurfaceCount,
                        List.of(), List.of(), Completion.INCOMPLETE_GLOBAL,
                        "shared_surface_count_query:" + error.getClass().getSimpleName(),
                        startedNanos);
                logger.warn(logPrefix(cameraId)
                        + " stage=eligibility result=incomplete_global apiLevel=" + apiLevel,
                        error);
                logComplete(result, imageProfileCount);
                return result;
            }
            if (maxSharedSurfaceCount.orElseThrow() < 2) {
                Result result = unavailable(cameraId, apiLevel, maxSharedSurfaceCount,
                        pipelineUnavailableReason(apiLevel, maxSharedSurfaceCount, true),
                        startedNanos);
                logComplete(result, imageProfileCount);
                return result;
            }
        }

        try {
            if (!hasH264Encoder()) {
                Result result = unavailable(cameraId, apiLevel, maxSharedSurfaceCount,
                        pipelineUnavailableReason(apiLevel, maxSharedSurfaceCount, false),
                        startedNanos);
                logComplete(result, imageProfileCount);
                return result;
            }
        } catch (RuntimeException error) {
            Result result = incomplete(cameraId, apiLevel, maxSharedSurfaceCount,
                    List.of(), List.of(), Completion.INCOMPLETE_GLOBAL,
                    "encoder_catalog:" + error.getClass().getSimpleName(), startedNanos);
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
            Result result = incomplete(cameraId, apiLevel, maxSharedSurfaceCount,
                    List.of(), List.of(), failure.completion(), failure.detail(), startedNanos);
            logFailure(cameraId, "open", failure);
            logComplete(result, imageProfileCount);
            return result;
        }

        Result result = runMatrix(apiLevel, maxSharedSurfaceCount, cameraId,
                videoModes, imageModes,
                (tuple, attempt, confirmation) -> queryTuple(camera, tuple, apiLevel),
                logger, startedNanos);

        ProbeFailure releaseFailure = camera.release();
        if (releaseFailure != null) {
            result = incomplete(cameraId, apiLevel, maxSharedSurfaceCount,
                    result.evidence().rawFastCandidates(), result.attempts(),
                    releaseFailure.completion(), releaseFailure.detail(), startedNanos);
            logFailure(cameraId, "release", releaseFailure);
        }
        logComplete(result, imageProfileCount);
        return result;
    }
    static Result runMatrix(int apiLevel, OptionalInt maxSharedSurfaceCount,
            CameraId cameraId, Collection<VideoMode> videoModes,
            Collection<ImageMode> imageModes, TupleQuery query, Logger logger,
            long startedNanos) {
        Objects.requireNonNull(maxSharedSurfaceCount, "maxSharedSurfaceCount");
        Objects.requireNonNull(cameraId, "cameraId");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(logger, "logger");

        TreeSet<VideoMode> videos = immutableSorted(videoModes, "videoModes");
        TreeSet<ImageMode> images = immutableSorted(imageModes, "imageModes");
        List<CaptureModeTuple> universe = new ArrayList<>();
        for (VideoMode video : videos) {
            for (ImageMode image : images) universe.add(new CaptureModeTuple(video, image));
        }

        Map<CaptureModeTuple, AttemptResult> states = new TreeMap<>();
        List<Attempt> attempts = new ArrayList<>();
        for (CaptureModeTuple tuple : universe) {
            Result incomplete = attempt(cameraId, apiLevel, maxSharedSurfaceCount,
                    tuple, 1, "initial", query, logger, states, attempts, startedNanos);
            if (incomplete != null) return incomplete;
        }

        Set<CaptureModeTuple> confirmed = new HashSet<>();
        for (VideoMode video : videos) {
            List<CaptureModeTuple> row = universe.stream()
                    .filter(tuple -> tuple.videoMode().equals(video))
                    .collect(java.util.stream.Collectors.toList());
            if (!allRejected(row, states)) continue;
            Result incomplete = confirmRejected(cameraId, apiLevel, maxSharedSurfaceCount,
                    row, "fps_row", query, logger, states, attempts, confirmed, startedNanos);
            if (incomplete != null) return incomplete;
        }

        TreeSet<StandardResolution> resolutions = new TreeSet<>();
        for (VideoMode video : videos) resolutions.add(video.resolution());
        for (StandardResolution resolution : resolutions) {
            List<CaptureModeTuple> mode = universe.stream()
                    .filter(tuple -> tuple.videoMode().resolution().equals(resolution))
                    .collect(java.util.stream.Collectors.toList());
            if (!allRejected(mode, states)) continue;
            Result incomplete = confirmRejected(cameraId, apiLevel, maxSharedSurfaceCount,
                    mode, "video_mode", query, logger, states, attempts, confirmed, startedNanos);
            if (incomplete != null) return incomplete;
        }

        return complete(cameraId, apiLevel, maxSharedSurfaceCount,
                supportedCandidates(cameraId, states), attempts, startedNanos);
    }

    private static Result confirmRejected(CameraId cameraId, int apiLevel,
            OptionalInt maxSharedSurfaceCount, List<CaptureModeTuple> tuples,
            String confirmation, TupleQuery query, Logger logger,
            Map<CaptureModeTuple, AttemptResult> states, List<Attempt> attempts,
            Set<CaptureModeTuple> confirmed, long startedNanos) {
        for (CaptureModeTuple tuple : tuples) {
            if (states.get(tuple) != AttemptResult.REJECTED || !confirmed.add(tuple)) continue;
            Result incomplete = attempt(cameraId, apiLevel, maxSharedSurfaceCount,
                    tuple, 2, confirmation, query, logger, states, attempts, startedNanos);
            if (incomplete != null) return incomplete;
        }
        return null;
    }

    private static Result attempt(CameraId cameraId, int apiLevel,
            OptionalInt maxSharedSurfaceCount, CaptureModeTuple tuple, int attempt,
            String confirmation, TupleQuery query, Logger logger,
            Map<CaptureModeTuple, AttemptResult> states, List<Attempt> attempts,
            long startedNanos) {
        long attemptStartedNanos = System.nanoTime();
        ProbeDecision decision = Objects.requireNonNull(
                query.query(tuple, attempt, confirmation), "probe decision");
        long elapsedMillis = elapsedMillis(attemptStartedNanos);
        Attempt recorded = new Attempt(tuple, decision.result(), attempt, confirmation,
                decision.cameraOutputCount(), decision.sharedSurfaceCount(),
                decision.surfaceSourceClasses(), elapsedMillis, decision.detail());
        attempts.add(recorded);

        if (decision.result() == AttemptResult.SUPPORTED
                || decision.result() == AttemptResult.REJECTED) {
            states.put(tuple, decision.result());
            return null;
        }
        return incomplete(cameraId, apiLevel, maxSharedSurfaceCount,
                supportedCandidates(cameraId, states), attempts,
                completion(decision.result()), decision.detail(), startedNanos);
    }

    private ProbeDecision queryTuple(OpenedCamera camera, CaptureModeTuple tuple,
            int apiLevel) {
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
            boolean supported = apiLevel >= Build.VERSION_CODES.P
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
    private static Result complete(CameraId cameraId, int apiLevel,
            OptionalInt maxSharedSurfaceCount, Collection<CandidateKey> candidates,
            Collection<Attempt> attempts, long startedNanos) {
        return result(cameraId, PipelineAvailability.AVAILABLE, Completion.COMPLETE,
                apiLevel, maxSharedSurfaceCount, candidates, attempts,
                "matrix_complete", startedNanos);
    }

    private static Result unavailable(CameraId cameraId, int apiLevel,
            OptionalInt maxSharedSurfaceCount, String detail, long startedNanos) {
        return result(cameraId, PipelineAvailability.UNAVAILABLE,
                Completion.PIPELINE_UNAVAILABLE, apiLevel, maxSharedSurfaceCount,
                List.of(), List.of(), detail, startedNanos);
    }

    private static Result incomplete(CameraId cameraId, int apiLevel,
            OptionalInt maxSharedSurfaceCount, Collection<CandidateKey> candidates,
            Collection<Attempt> attempts, Completion completion, String detail,
            long startedNanos) {
        return result(cameraId, PipelineAvailability.UNKNOWN, completion,
                apiLevel, maxSharedSurfaceCount, candidates, attempts, detail, startedNanos);
    }

    private static Result result(CameraId cameraId, PipelineAvailability availability,
            Completion completion, int apiLevel, OptionalInt maxSharedSurfaceCount,
            Collection<CandidateKey> candidates, Collection<Attempt> attempts,
            String detail, long startedNanos) {
        PipelineEvidence evidence = new PipelineEvidence(cameraId, VideoCodec.H264,
                PIPELINE_ID, availability, candidates, List.of());
        return new Result(evidence, completion, apiLevel, maxSharedSurfaceCount,
                List.copyOf(attempts), elapsedMillis(startedNanos), detail);
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
        Objects.requireNonNull(maxSharedSurfaceCount, "maxSharedSurfaceCount");
        if (apiLevel < Build.VERSION_CODES.O) return "api_below_26";
        if (apiLevel >= Build.VERSION_CODES.P
                && maxSharedSurfaceCount.isPresent()
                && maxSharedSurfaceCount.orElseThrow() < 2) {
            return "max_shared_surface_count_below_2";
        }
        return h264EncoderAvailable ? null : "h264_encoder_unavailable";
    }

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

    private static ProbeFailure cameraStateFailure(String stage, int errorCode) {
        return switch (errorCode) {
            case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> ProbeFailure.blocked(
                    stage + ":camera_disabled", null);
            case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE,
                    CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE ->
                    ProbeFailure.transientFailure(stage + ":camera_error_" + errorCode, null);
            default -> ProbeFailure.global(stage + ":camera_error_" + errorCode, null);
        };
    }

    private static ProbeFailure firstFailure(ProbeFailure first, ProbeFailure second) {
        return first == null ? second : first;
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
            confirmation = required(confirmation, "confirmation");
            if (cameraOutputCount < 0 || sharedSurfaceCount < 0) {
                throw new IllegalArgumentException("surface counts must not be negative");
            }
            surfaceSourceClasses = required(surfaceSourceClasses, "surfaceSourceClasses");
            if (elapsedMillis < 0) {
                throw new IllegalArgumentException("elapsedMillis must not be negative");
            }
            detail = required(detail, "detail");
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
            Objects.requireNonNull(maxSharedSurfaceCount, "maxSharedSurfaceCount");
            attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
            if (elapsedMillis < 0) {
                throw new IllegalArgumentException("elapsedMillis must not be negative");
            }
            detail = required(detail, "detail");
        }

        public boolean complete() { return completion == Completion.COMPLETE; }
    }

    @FunctionalInterface
    interface TupleQuery {
        ProbeDecision query(CaptureModeTuple tuple, int attempt, String confirmation);
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
            surfaceSourceClasses = required(surfaceSourceClasses, "surfaceSourceClasses");
            detail = required(detail, "detail");
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

        private static OpenedCamera open(Context context, CameraId cameraId) throws ProbeFailure {
            CameraManager manager = context.getSystemService(CameraManager.class);
            if (manager == null) throw ProbeFailure.global("open:camera_manager_unavailable", null);

            HandlerThread thread = new HandlerThread("dcam-native-fast-" + cameraId.value());
            thread.start();
            Handler handler = new Handler(thread.getLooper());
            CountDownLatch opened = new CountDownLatch(1);
            CountDownLatch closed = new CountDownLatch(1);
            OpenGuard<CameraDevice> guard = new OpenGuard<>();
            AtomicReference<ProbeFailure> failure = new AtomicReference<>();
            boolean requestSubmitted = false;
            try {
                manager.openCamera(cameraId.value(), new CameraDevice.StateCallback() {
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
                }, handler);
                requestSubmitted = true;
                if (!opened.await(OPEN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    ProbeFailure cleanup = abandonOpen(guard, closed, thread);
                    if (cleanup != null) throw cleanup;
                    throw ProbeFailure.transientFailure("open:timeout", null);
                }
                CameraDevice openedCamera = guard.value();
                ProbeFailure openFailure = failure.get();
                if (openedCamera == null || openFailure != null) {
                    ProbeFailure cleanup = abandonOpen(guard, closed, thread);
                    if (cleanup != null) throw cleanup;
                    throw openFailure == null
                            ? ProbeFailure.global("open:unknown_failure", null)
                            : openFailure;
                }
                return new OpenedCamera(openedCamera, thread, handler, failure, closed);
            } catch (SecurityException error) {
                ProbeFailure cleanup = requestSubmitted
                        ? abandonOpen(guard, closed, thread)
                        : stopThread(thread, "open_thread");
                if (cleanup != null) throw cleanup;
                throw ProbeFailure.blocked("open:camera_permission", error);
            } catch (CameraAccessException error) {
                ProbeFailure cleanup = requestSubmitted
                        ? abandonOpen(guard, closed, thread)
                        : stopThread(thread, "open_thread");
                if (cleanup != null) throw cleanup;
                throw cameraAccessFailure("open", error);
            } catch (InterruptedException error) {
                ProbeFailure cleanup = requestSubmitted
                        ? abandonOpen(guard, closed, thread)
                        : stopThread(thread, "open_thread");
                Thread.currentThread().interrupt();
                if (cleanup != null) throw cleanup;
                throw ProbeFailure.transientFailure("open:interrupted", error);
            } catch (RuntimeException error) {
                ProbeFailure cleanup = requestSubmitted
                        ? abandonOpen(guard, closed, thread)
                        : stopThread(thread, "open_thread");
                if (cleanup != null) throw cleanup;
                throw ProbeFailure.global("open:" + error.getClass().getSimpleName(), error);
            }
        }

        private static ProbeFailure abandonOpen(OpenGuard<CameraDevice> guard,
                CountDownLatch closed, HandlerThread thread) {
            ProbeFailure failure = null;
            CameraDevice owned = guard.abandon();
            if (owned != null) {
                try {
                    owned.close();
                } catch (RuntimeException error) {
                    failure = ProbeFailure.transientFailure(
                            "open_cleanup:" + error.getClass().getSimpleName(), error);
                }
            }
            if (closed.getCount() == 0) {
                failure = firstFailure(failure, stopThread(thread, "open_thread"));
            }
            return failure;
        }

        private boolean supports(List<OutputConfiguration> outputs)
                throws CameraAccessException, ProbeFailure {
            ensureAvailable();
            SessionConfiguration configuration = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputs,
                    command -> handler.post(command),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession session) { }
                        @Override public void onConfigureFailed(CameraCaptureSession session) { }
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
            ProbeFailure failure = null;
            try {
                camera.close();
                if (!closed.await(RELEASE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    failure = ProbeFailure.transientFailure(
                            "release:camera_close_timeout", null);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                failure = ProbeFailure.transientFailure("release:interrupted", error);
            } catch (RuntimeException error) {
                failure = ProbeFailure.transientFailure(
                        "release:" + error.getClass().getSimpleName(), error);
            }
            return firstFailure(failure, stopThread(thread, "release_thread"));
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
                if (!codec.isEncoder()) continue;
                MediaCodecInfo.CodecCapabilities capabilities;
                try {
                    capabilities = codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                MediaCodecInfo.VideoCapabilities video = capabilities.getVideoCapabilities();
                if (video == null) continue;
                try {
                    if (!video.areSizeAndRateSupported(
                            width, height, framesPerSecond)) continue;
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                Range<Integer> bitrates = video.getBitrateRange();
                long target = Math.max(1L,
                        (long) width * height * framesPerSecond / 4L);
                int bitrate = (int) Math.max(bitrates.getLower(),
                        Math.min((long) bitrates.getUpper(), target));
                return new EncoderSelection(codec.getName(), bitrate);
            }
            throw new TupleRejected("encoder_setup:no_matching_h264_encoder");
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
            RuntimeException failure = null;
            if (previewSurface != null) {
                try { previewSurface.release(); }
                catch (RuntimeException error) { failure = error; }
            }
            if (previewTexture != null) {
                try { previewTexture.release(); }
                catch (RuntimeException error) { if (failure == null) failure = error; }
            }
            if (encoderSurface != null) {
                try { encoderSurface.release(); }
                catch (RuntimeException error) { if (failure == null) failure = error; }
            }
            if (encoder != null) {
                try { encoder.release(); }
                catch (RuntimeException error) { if (failure == null) failure = error; }
            }
            if (imageReader != null) {
                try { imageReader.close(); }
                catch (RuntimeException error) { if (failure == null) failure = error; }
            }
            if (failure != null) throw failure;
        }
    }

    private record EncoderSelection(String name, int bitrate) {
        private EncoderSelection {
            name = required(name, "name");
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