package com.dvid.dcam.platform.camera.shared;

import static com.dvid.dcam.platform.camera.shared.SharedCameraPipelineSupport.deadlineExpired;
import static com.dvid.dcam.platform.camera.shared.SharedCameraPipelineSupport.deleteArtifact;
import static com.dvid.dcam.platform.camera.shared.SharedCameraPipelineSupport.deleteQuietly;
import static com.dvid.dcam.platform.camera.shared.SharedCameraPipelineSupport.elapsed;
import static com.dvid.dcam.platform.camera.shared.SharedCameraPipelineSupport.remainingReleaseMillis;
import static com.dvid.dcam.platform.camera.shared.SharedCameraPipelineSupport.waitMillis;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Range;
import android.view.Surface;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.device.domain.camera.CameraFailureClass;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.platform.camera.shared.SharedCameraPipelineSupport.VideoInspection;
import com.dvid.dcam.platform.storage.DcamRecordingOutput;
import com.dvid.dcam.platform.storage.SegmentedAesGcmJpegOutput;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractSharedCameraPipeline implements SharedCameraCapturePipeline {
    private static final long OPEN_TIMEOUT_MILLIS = 5_000;
    private static final long SESSION_TIMEOUT_MILLIS = 5_000;
    private static final long FRAME_TIMEOUT_MILLIS = 3_000;
    private static final long RELEASE_TIMEOUT_MILLIS = 2_000;
    private static final String UNRESOLVED = "unresolved";
    private static final String OPERATION_CONTEXT = "operationContext";
    private static final String OUTPUT_FILE = "outputFile";
    private static final String LOG_PIPELINE_PREFIX = "pipeline=";
    private static final String LOG_OUTCOME = " outcome=";
    private static final String LOG_DETAIL = " detail=";
    private final Object operationLock = new Object();
    private final Object encoderFinalizationLock = new Object();
    private final Object previewFrameLock = new Object();
    private final Context context;
    private final Logger logger;
    private final File outputDirectory;
    private final Surface externalPreviewSurface;
    private final boolean recycleEncoder;
    private final long preRecordGopDurationMillis;
    private final VerificationPipelineId pipelineId;
    private final String cameraThreadName;
    private final String encoderThreadName;
    private final String surfaceMetricName;
    private final Supplier<GpsCoordinate> captureLocation;
    private volatile int rotationDegrees;
    private volatile boolean previewExpected = true;
    private final AtomicLong sourceFrameCount = new AtomicLong();
    private final AtomicLong previewFrameCount = new AtomicLong();
    private final AtomicLong previewDropCount = new AtomicLong();
    private final AtomicLong jpegRequestSequence = new AtomicLong();
    private volatile CameraOperationContext activeContext;
    private volatile CameraOperationOutcome asynchronousOutcome;
    private volatile String asynchronousDetail;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private CountDownLatch cameraClosed = new CountDownLatch(0);
    private ImageReader jpegReader;
    private SharedAvcEncoder encoder;
    private CountDownLatch firstSourceFrame = new CountDownLatch(0);
    private PendingJpeg pendingJpeg;
    private Range<Integer> configuredFpsRange;
    private boolean sessionBound;
    private boolean previewProgressing;
    private boolean encoderActive;
    private boolean encoderInputActive;
    private boolean encoderFinalized;
    private long finalizedDurationUs;
    private boolean retainVideoArtifactOnRelease;
    private boolean jpegCapturedWhileEncoderActive;
    private CameraResolution encodedVideoResolution;
    private CameraResolution capturedJpegResolution;
    private File videoArtifact;
    private File jpegArtifact;
    private long firstSensorTimestampNanos = -1;
    private long lastSensorTimestampNanos = -1;
    private boolean sensorTimestampRealtime;
    private int cameraOpenCount;
    private int sessionCreateCount;
    private int cameraOutputCount;
    private int downstreamSurfaceCount;
    private int jpegSurfaceCount;
    private String availablePhysicalCameraIds = UNRESOLVED;
    private String activePhysicalCameraId = UNRESOLVED;
    private boolean standaloneImageSession;

    protected AbstractSharedCameraPipeline(Context context, Logger logger,
            File outputDirectory, Surface externalPreviewSurface, boolean recycleEncoder,
            int rotationDegrees, long preRecordGopDurationMillis,
            Supplier<GpsCoordinate> captureLocation,
            VerificationPipelineId pipelineId, String cameraThreadName,
            String encoderThreadName, String surfaceMetricName) {
        this.context = Objects.requireNonNull(context, "context");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        this.externalPreviewSurface = externalPreviewSurface;
        this.recycleEncoder = recycleEncoder;
        if (preRecordGopDurationMillis < 0L) {
            throw new IllegalArgumentException(
                    "preRecordGopDurationMillis must be non-negative");
        }
        if (preRecordGopDurationMillis != 0L) {
            throw new IllegalArgumentException(
                    "Pre-record requires a disk-backed GOP store; only 0 ms is supported");
        }
        this.preRecordGopDurationMillis = preRecordGopDurationMillis;
        this.captureLocation = Objects.requireNonNull(captureLocation, "captureLocation");
        this.rotationDegrees = CameraOrientation.normalize(rotationDegrees);
        this.pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
        this.cameraThreadName = Objects.requireNonNull(cameraThreadName, "cameraThreadName");
        this.encoderThreadName = Objects.requireNonNull(encoderThreadName, "encoderThreadName");
        this.surfaceMetricName = Objects.requireNonNull(surfaceMetricName, "surfaceMetricName");
    }


    private GpsCoordinate currentCaptureLocation(String mediaType) {
        try {
            return captureLocation.get();
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, "Capture " + mediaType
                    + " without GPS metadata because current location lookup failed.", error);
            return null;
        }
    }

    private static Location jpegLocation(GpsCoordinate coordinate) {
        Location location = new Location("dcam");
        location.setLatitude(coordinate.getLatitude());
        location.setLongitude(coordinate.getLongitude());
        location.setTime(System.currentTimeMillis());
        return location;
    }

    @Override public final VerificationPipelineId pipelineId() {
        return pipelineId;
    }
    @Override public final void setRotation(int rotationDegrees) {
        synchronized (operationLock) {
            int normalized = CameraOrientation.normalize(
                    rotationDegrees);
            int previous = this.rotationDegrees;
            if (previous == normalized) return;
            this.rotationDegrees = normalized;
            boolean encoderMetadataUpdated = encoder == null || encoder.setRotation(normalized);
            if (!encoderMetadataUpdated) {
                logger.warn(LogCategory.CAMERA, "unspecified", null, "shared_camera_capture stage=orientation_update"
                        + " outcome=encoder_metadata_failed"
                        + " pipeline=" + pipelineId().value()
                        + " previousDegrees=" + previous + " outputDegrees=" + normalized
                        + " encoderActive=" + encoderActive, null);
            }
        }
    }

    @Override public final void setPreviewExpected(boolean expected) {
        synchronized (previewFrameLock) {
            previewExpected = expected;
            previewFrameLock.notifyAll();
        }
    }

    private boolean awaitPreviewReady(CameraOperationContext value)
            throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime()
                + waitMillis(value, FRAME_TIMEOUT_MILLIS);
        synchronized (previewFrameLock) {
            while (previewExpected && hasPreviewEvidence() && previewFrameCount.get() == 0) {
                long remaining = deadline - SystemClock.elapsedRealtime();
                if (remaining <= 0) return false;
                previewFrameLock.wait(remaining);
            }
            return true;
        }
    }

    @Override public final int outputRotationDegrees() {
        return rotationDegrees;
    }

    @Override public final long finalizedDurationUs() {
        synchronized (operationLock) {
            return encoderFinalized ? finalizedDurationUs : 0L;
        }
    }

    @Override public final CameraOperationResult bindSession(CameraOperationContext value) {
        return validateCaptureProfile(value, false);
    }

    @Override public final CameraOperationResult bindStandaloneImageSession(
            CameraOperationContext value) {
        return validateCaptureProfile(value, true);
    }

    private CameraOperationResult validateCaptureProfile(
            CameraOperationContext value, boolean standaloneImage) {
        CameraOperationResult result = bindSession(value, standaloneImage);
        if (result.outcome() != CameraOperationOutcome.PASS) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, captureProfileValidationMessage(result), null);
        }
        return result;
    }

    private CameraOperationResult bindSession(
            CameraOperationContext value, boolean standaloneImage) {
        synchronized (operationLock) {
            long started = SystemClock.elapsedRealtime();
            CameraOperationResult invalid = validateIdentity(value,
                    CameraPipelineOperation.BIND_SESSION, started, false);
            if (invalid != null) return invalid;
            if (deadlineExpired(value)) return timeout(value,
                    CameraPipelineOperation.BIND_SESSION, started, "deadline_before_bind");
            if (sessionBound && value.matchesCurrentOperation(activeContext)
                    && standaloneImageSession == standaloneImage) {
                return result(value, CameraPipelineOperation.BIND_SESSION,
                        CameraOperationOutcome.PASS, started, "session_already_bound");
            }
            if (!releaseResources(true)) {
                return result(value, CameraPipelineOperation.BIND_SESSION,
                        CameraOperationOutcome.TRANSIENT_RETRYABLE, started,
                        "previous_release_timeout");
            }
            activeContext = value;
            resetEvidence();
            standaloneImageSession = standaloneImage;
            try {
                return bindPreparedSession(value, standaloneImage, started);
            } catch (CameraAccessException error) {
                CameraOperationResult failure = cameraAccessFailure(value,
                        CameraPipelineOperation.BIND_SESSION,
                        started, "bind_repeating", error);
                return releaseAfterBindFailure(value, started, failure);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                CameraOperationResult failure = result(value,
                        CameraPipelineOperation.BIND_SESSION,
                        CameraOperationOutcome.CANCELLED_UNKNOWN, started, "bind_interrupted");
                return releaseAfterBindFailure(value, started, failure);
            } catch (PipelineFailure error) {
                CameraOperationResult failure = result(value,
                        CameraPipelineOperation.BIND_SESSION,
                        error.outcome, started, error.getMessage());
                return releaseAfterBindFailure(value, started, failure);
            } catch (SecurityException error) {
                CameraOperationResult failure = failure(value,
                        CameraPipelineOperation.BIND_SESSION,
                        CameraPipelineFailureClassifier.Signal.PERMISSION_BLOCKED,
                        started, "bind_permission", error);
                return releaseAfterBindFailure(value, started, failure);
            } catch (IOException error) {
                CameraOperationResult failure = failure(value,
                        CameraPipelineOperation.BIND_SESSION,
                        CameraPipelineFailureClassifier.Signal.STORAGE_BLOCKED,
                        started, "bind_io", error);
                return releaseAfterBindFailure(value, started, failure);
            } catch (RuntimeException error) {
                CameraOperationResult failure = failure(value,
                        CameraPipelineOperation.BIND_SESSION,
                        CameraPipelineFailureClassifier.Signal.UNKNOWN_GLOBAL,
                        started, "bind_runtime", error);
                return releaseAfterBindFailure(value, started, failure);
            }
        }
    }

    private void primeVideoEncoder(CameraOperationContext value)
            throws CameraAccessException, PipelineFailure, InterruptedException {
        long started = SystemClock.elapsedRealtime();
        encoder.requestKeyFrame();
        startTopologyRecording();
        encoderInputActive = true;
        boolean ready = encoder.awaitOutputFormat(waitMillis(value, FRAME_TIMEOUT_MILLIS));
        if (!ready) {
            CameraOperationOutcome outcome = encoder.callbackError() == null
                    ? CameraOperationOutcome.CANDIDATE_SUSPECT
                    : CameraOperationOutcome.GLOBAL_FAILURE;
            throw new PipelineFailure(outcome,
                    "encoder_prime_failed:" + encoder.callbackError());
        }
        encoder.suspendInput();
        logger.info(LogCategory.CAMERA, "unspecified", "Prime video encoder success. Pipeline: " + pipelineId
                + ". Elapsed: " + elapsed(started) + " ms. Pre-record GOP duration: "
                + preRecordGopDurationMillis + " ms.");
    }

    @Override public final CameraOperationResult previewProgress(CameraOperationContext value) {
        synchronized (operationLock) {
            long started = SystemClock.elapsedRealtime();
            CameraOperationResult invalid = validateBound(value,
                    CameraPipelineOperation.PREVIEW_PROGRESS, started);
            if (invalid != null) return invalid;
            long source = sourceFrameCount.get();
            long preview = previewFrameCount.get();
            if (!previewExpected) {
                return result(value, CameraPipelineOperation.PREVIEW_PROGRESS,
                        CameraOperationOutcome.PASS, started, "preview_not_expected");
            }
            if (!hasPreviewEvidence()) {
                return result(value, CameraPipelineOperation.PREVIEW_PROGRESS,
                        CameraOperationOutcome.BLOCKED_EXTERNAL, started,
                        "external_preview_frame_signal_unavailable");
            }
            previewProgressing = source > 0 && preview > 0;
            CameraOperationOutcome outcome = previewProgressing
                    ? CameraOperationOutcome.PASS : CameraOperationOutcome.CANDIDATE_SUSPECT;
            return result(value, CameraPipelineOperation.PREVIEW_PROGRESS, outcome, started,
                    "sourceFrames=" + source + ",previewFrames=" + preview
                            + ",previewDrops=" + previewDropCount.get()
                            + previewProgressDetail());
        }
    }

    @Override public final SharedCameraCapturePipeline.HealthSnapshot healthSnapshot(
            CameraOperationContext value) {
        synchronized (operationLock) {
            Objects.requireNonNull(value, OPERATION_CONTEXT);
            CameraOperationContext current = activeContext;
            if (!pipelineId().equals(value.verificationPipelineId())
                    || current == null || !value.matchesCurrentOperation(current)) {
                return new SharedCameraCapturePipeline.HealthSnapshot(
                        false, true, false, 0L, 0L, "stale_or_released_context");
            }
            CameraOperationOutcome asyncOutcome = asynchronousOutcome;
            String encoderFailure = encoder == null ? null : encoder.callbackError();
            boolean previewSignalAvailable = !previewExpected || hasPreviewEvidence()
                    && asyncOutcome != CameraOperationOutcome.BLOCKED_EXTERNAL;
            String detail = healthDetail(sessionBound, encoderFailure, asyncOutcome,
                    previewSignalAvailable, asynchronousDetail);
            return new SharedCameraCapturePipeline.HealthSnapshot(
                    sessionBound,
                    encoderFailure != null || asyncOutcome != null
                            && asyncOutcome.requiresRuntimeRecovery(),
                    previewSignalAvailable, sourceFrameCount.get(), previewFrameCount.get(),
                    detail);
        }
    }

    private CameraOperationResult bindPreparedSession(
            CameraOperationContext value, boolean standaloneImage, long started)
            throws CameraAccessException, IOException, InterruptedException, PipelineFailure {
        prepareResources(value, standaloneImage);
        openCamera(value);
        createSession(value);
        startRepeating(false);
        boolean sourceReady = firstSourceFrame.await(
                waitMillis(value, FRAME_TIMEOUT_MILLIS), TimeUnit.MILLISECONDS);
        boolean previewReady = awaitPreviewReady(value);
        CameraOperationResult async = asynchronousFailure(
                value, CameraPipelineOperation.BIND_SESSION, started);
        if (async != null) return releaseAfterBindFailure(value, started, async);
        if (!sourceReady || !previewReady) {
            CameraOperationOutcome outcome = deadlineExpired(value)
                    ? CameraOperationOutcome.TIMEOUT_UNKNOWN
                    : CameraOperationOutcome.CANDIDATE_SUSPECT;
            CameraOperationResult failure = result(value,
                    CameraPipelineOperation.BIND_SESSION, outcome, started,
                    "first_frame_missing:source=" + sourceReady
                            + ",preview=" + previewReady
                            + ",previewExpected=" + previewExpected);
            return releaseAfterBindFailure(value, started, failure);
        }
        if (!standaloneImage) primeVideoEncoder(value);
        sessionBound = true;
        previewProgressing = previewExpected && hasPreviewEvidence()
                && previewFrameCount.get() > 0;
        return result(value, CameraPipelineOperation.BIND_SESSION,
                CameraOperationOutcome.PASS, started,
                "bound:cameraOutputs=" + cameraOutputCount
                        + "," + surfaceMetricName + "=" + downstreamSurfaceCount
                        + ",jpegSurfaces=" + jpegSurfaceCount
                        + ",configuredFpsRange=" + configuredFpsRange);
    }

    private static String healthDetail(boolean sessionBound, String encoderFailure,
            CameraOperationOutcome asyncOutcome, boolean previewSignalAvailable,
            String asynchronousDetail) {
        if (!sessionBound) return "session_not_bound";
        if (encoderFailure != null) return "encoder_failure:" + encoderFailure;
        if (asyncOutcome != null) {
            return asynchronousDetail == null ? "asynchronous_failure" : asynchronousDetail;
        }
        return previewSignalAvailable
                ? "healthy" : "external_preview_frame_signal_unavailable";
    }

    private void discardEncoderSegment() {
        if (encoder != null) encoder.discard();
        deleteQuietly(videoArtifact);
        videoArtifact = null;
        retainVideoArtifactOnRelease = false;
    }


    @Override public final long recordingBitrateBitsPerSecond() {
        SharedAvcEncoder current = encoder;
        if (current == null) return 0L;
        boolean includeAudio = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        return current.recordingBitrateBitsPerSecond(includeAudio);
    }

    @Override public final CameraOperationResult startEncoder(CameraOperationContext value) {
        return startEncoder(value, artifact(value, ".mp4"));
    }

    @Override public final CameraOperationResult startEncoder(
            CameraOperationContext value, File outputFile) {
        return startEncoder(value, outputFile, 0L, () -> {});
    }

    @Override public final CameraOperationResult startEncoder(
            CameraOperationContext value,
            File outputFile,
            long fileSizeLimitBytes,
            RecordingLimitListener limitListener) {
        return startEncoder(value, outputFile, fileSizeLimitBytes,
                limitListener, rotationDegrees);
    }

    @Override public final CameraOperationResult startEncoder(
            CameraOperationContext value,
            File outputFile,
            long fileSizeLimitBytes,
            RecordingLimitListener limitListener,
            int rotationDegrees) {
        return startEncoder(value, Objects.requireNonNull(outputFile, OUTPUT_FILE), null,
                fileSizeLimitBytes, limitListener, rotationDegrees);
    }

    @Override public final CameraOperationResult startEncoder(
            CameraOperationContext value,
            DcamRecordingOutput recordingOutput,
            long fileSizeLimitBytes,
            RecordingLimitListener limitListener) {
        return startEncoder(value, recordingOutput, fileSizeLimitBytes,
                limitListener, rotationDegrees);
    }

    @Override public final CameraOperationResult startEncoder(
            CameraOperationContext value,
            DcamRecordingOutput recordingOutput,
            long fileSizeLimitBytes,
            RecordingLimitListener limitListener,
            int rotationDegrees) {
        DcamRecordingOutput output = Objects.requireNonNull(
                recordingOutput, "recordingOutput");
        return startEncoder(value, output.file(), output, fileSizeLimitBytes,
                limitListener, rotationDegrees);
    }

    private CameraOperationResult startEncoder(
            CameraOperationContext value,
            File outputFile,
            DcamRecordingOutput recordingOutput,
            long fileSizeLimitBytes,
            RecordingLimitListener limitListener,
            int requestedRotationDegrees) {
        synchronized (operationLock) {
            long started = SystemClock.elapsedRealtime();
            CameraOperationResult invalid = validateBound(value,
                    CameraPipelineOperation.START_ENCODER, started);
            if (invalid != null) return invalid;
            if (encoderActive) return result(value, CameraPipelineOperation.START_ENCODER,
                    CameraOperationOutcome.PASS, started, "encoder_already_active");
            try {
                return beginEncoderSegment(value, outputFile, recordingOutput, fileSizeLimitBytes,
                        limitListener, requestedRotationDegrees, started);
            } catch (InterruptedException error) {
                discardFailedEncoderSegment();
                Thread.currentThread().interrupt();
                return result(value, CameraPipelineOperation.START_ENCODER,
                        CameraOperationOutcome.CANCELLED_UNKNOWN, started,
                        "encoder_start_interrupted");
            } catch (PipelineFailure error) {
                discardFailedEncoderSegment();
                return result(value, CameraPipelineOperation.START_ENCODER,
                        error.outcome, started, error.getMessage());
            } catch (IOException error) {
                discardFailedEncoderSegment();
                return failure(value, CameraPipelineOperation.START_ENCODER,
                        CameraPipelineFailureClassifier.Signal.STORAGE_BLOCKED,
                        started, "encoder_output", error);
            } catch (CameraAccessException error) {
                discardFailedEncoderSegment();
                return cameraAccessFailure(value, CameraPipelineOperation.START_ENCODER,
                        started, "encoder_repeating", error);
            } catch (RuntimeException error) {
                discardFailedEncoderSegment();
                return failure(value, CameraPipelineOperation.START_ENCODER,
                        CameraPipelineFailureClassifier.Signal.UNKNOWN_GLOBAL,
                        started, "encoder_start", error);
            }
        }
    }
    @Override public final CameraOperationResult stopEncoder(CameraOperationContext value) {
        synchronized (operationLock) {
            long started = SystemClock.elapsedRealtime();
            CameraOperationResult invalid = validateBound(value,
                    CameraPipelineOperation.STOP_ENCODER, started);
            if (invalid != null) return invalid;
            if (!encoderActive) return result(value, CameraPipelineOperation.STOP_ENCODER,
                    CameraOperationOutcome.PASS, started, "encoder_already_stopped");
            encoder.pause();
            encoderActive = false;
            retainVideoArtifactOnRelease = videoArtifact != null;
            return result(value, CameraPipelineOperation.STOP_ENCODER,
                    CameraOperationOutcome.PASS, started,
                    "encoder_stopped_input_retained");
        }
    }
    @Override public final CameraOperationResult finalizeEncoder(CameraOperationContext value) {
        return finalizeEncoder(value, false);
    }

    @Override public final CameraOperationResult finalizeEncoder(
            CameraOperationContext value, boolean cleanStop) {
        synchronized (encoderFinalizationLock) {
            long started = SystemClock.elapsedRealtime();
            SharedAvcEncoder currentEncoder;
            synchronized (operationLock) {
                CameraOperationResult invalid = validateBound(value,
                        CameraPipelineOperation.FINALIZE_ENCODER, started);
                if (invalid != null) return invalid;
                if (cleanStop) retainVideoArtifactOnRelease = videoArtifact != null;
                if (encoderActive) return result(value, CameraPipelineOperation.FINALIZE_ENCODER,
                        CameraOperationOutcome.CANDIDATE_SUSPECT, started,
                        "stop_encoder_before_finalize");
                if (encoderFinalized) {
                    if (cleanStop) videoArtifact = null;
                    retainVideoArtifactOnRelease = false;
                    return result(value, CameraPipelineOperation.FINALIZE_ENCODER,
                            CameraOperationOutcome.PASS, started, "encoder_already_finalized");
                }
                currentEncoder = Objects.requireNonNull(encoder, "encoder");
            }

            SharedAvcEncoder.Segment segment = currentEncoder.finish();
            VideoInspection inspection = "segment_finalized".equals(segment.detail())
                    ? inspectFinalizedVideo(segment, currentEncoder, cleanStop) : null;
            synchronized (operationLock) {
                if (inspection == null) {
                    retainVideoArtifactOnRelease = segment.file() != null;
                    return result(value, CameraPipelineOperation.FINALIZE_ENCODER,
                            CameraOperationOutcome.GLOBAL_FAILURE, started,
                            "encoder_finalize_failed:" + segment.detail());
                }
                CameraResolution expected = value.tuple().videoMode().resolution().actual();
                if (!inspection.valid() || !expected.equals(inspection.resolution())) {
                    if (cleanStop) retainVideoArtifactOnRelease = segment.file() != null;
                    else deleteQuietly(segment.file());
                    return result(value, CameraPipelineOperation.FINALIZE_ENCODER,
                            CameraOperationOutcome.CANDIDATE_SUSPECT, started,
                            "invalid_video:expected=" + expected + ",actual="
                                    + inspection.resolution() + ",samples="
                                    + inspection.sampleCount() + ",detail=" + inspection.detail());
                }

                encoderFinalized = true;
                finalizedDurationUs = segment.durationUs();
                if (cleanStop) videoArtifact = null;
                retainVideoArtifactOnRelease = false;
                encodedVideoResolution = inspection.resolution();
                return result(value, CameraPipelineOperation.FINALIZE_ENCODER,
                        CameraOperationOutcome.PASS, started,
                        "video_finalized:resolution=" + encodedVideoResolution
                                + ",samples=" + inspection.sampleCount()
                                + ",durationUs=" + finalizedDurationUs
                                + ",metadata=" + inspection.detail()
                                + ",artifact=" + segment.file().getAbsolutePath());
            }
        }
    }

    private static VideoInspection inspectFinalizedVideo(
            SharedAvcEncoder.Segment segment,
            SharedAvcEncoder encoder,
            boolean cleanStop) {
        return cleanStop
                ? SharedCameraPipelineSupport.inspectCleanVideo(
                        segment.file(), segment.sampleCount(), encoder.encodedVideoResolution())
                : SharedCameraPipelineSupport.inspectVideo(segment.file());
    }

    @Override public final CameraOperationResult captureJpeg(CameraOperationContext value) {
        synchronized (operationLock) {
            return captureJpeg(value, artifact(value, ".jpg"), null,
                    rotationDegrees, true);
        }
    }

    @Override public final CameraOperationResult captureJpeg(
            CameraOperationContext value, File outputFile) {
        return captureJpeg(value, outputFile, rotationDegrees);
    }

    @Override public final CameraOperationResult captureJpeg(
            CameraOperationContext value, File outputFile, int jpegRotationDegrees) {
        synchronized (operationLock) {
            return captureJpeg(value, Objects.requireNonNull(outputFile, OUTPUT_FILE), null,
                    jpegRotationDegrees, false);
        }
    }

    @Override public final CameraOperationResult captureJpeg(
            CameraOperationContext value, SegmentedAesGcmJpegOutput output) {
        return captureJpeg(value, output, rotationDegrees);
    }

    @Override public final CameraOperationResult captureJpeg(
            CameraOperationContext value,
            SegmentedAesGcmJpegOutput output,
            int jpegRotationDegrees) {
        synchronized (operationLock) {
            SegmentedAesGcmJpegOutput encryptedOutput =
                    Objects.requireNonNull(output, "output");
            return captureJpeg(value, encryptedOutput.file(), encryptedOutput,
                    jpegRotationDegrees, false);
        }
    }

    private CameraOperationResult captureJpeg(
            CameraOperationContext value,
            File output,
            SegmentedAesGcmJpegOutput encryptedOutput,
            int jpegRotationDegrees,
            boolean pipelineOwnedOutput) {
        long started = SystemClock.elapsedRealtime();
        CameraOperationResult invalid = validateBound(value,
                CameraPipelineOperation.CAPTURE_JPEG, started);
        if (invalid != null) return invalid;
        long requestId = jpegRequestSequence.incrementAndGet();
        boolean encoderWasActive = encoderActive;
        int requestedJpegRotation = CameraOrientation.normalize(jpegRotationDegrees);
        PendingJpeg request = new PendingJpeg(value, output, encryptedOutput,
                encoderWasActive, requestedJpegRotation, requestId);
        pendingJpeg = request;
        try {
            CameraOperationResult captureResult = submitAndAwaitJpegCapture(
                    value, request, encoderWasActive, requestedJpegRotation, requestId, started);
            if (captureResult != null) return captureResult;
            if (encoderWasActive && encryptedOutput == null) {
                CameraOperationResult orientationResult = applyJpegOrientationMetadata(
                        value, request, output, requestedJpegRotation, started);
                if (orientationResult != null) return orientationResult;
            }
            CameraResolution decodedResolution = encryptedOutput == null
                    ? JpegDimensions.read(output)
                            .map(size -> new CameraResolution(size.width(), size.height()))
                            .orElse(null)
                    : request.resolution;
            capturedJpegResolution = decodedResolution;
            jpegArtifact = pipelineOwnedOutput ? output : null;
            jpegCapturedWhileEncoderActive = request.encoderWasActive;
            return result(value, CameraPipelineOperation.CAPTURE_JPEG,
                    CameraOperationOutcome.PASS, started,
                    "jpeg_captured:resolution=" + capturedJpegResolution
                            + ",encoderActive=" + jpegCapturedWhileEncoderActive
                            + ",artifact=" + output.getAbsolutePath());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            cancelPendingJpeg(request);
            return result(value, CameraPipelineOperation.CAPTURE_JPEG,
                    CameraOperationOutcome.CANCELLED_UNKNOWN, started, "jpeg_interrupted");
        } catch (CameraAccessException error) {
            cancelPendingJpeg(request);
            return cameraAccessFailure(value, CameraPipelineOperation.CAPTURE_JPEG,
                    started, "jpeg_capture", error);
        } catch (RuntimeException error) {
            cancelPendingJpeg(request);
            return failure(value, CameraPipelineOperation.CAPTURE_JPEG,
                    CameraPipelineFailureClassifier.Signal.UNKNOWN_GLOBAL,
                    started, "jpeg_runtime", error);
        }
    }

    private CameraOperationResult submitAndAwaitJpegCapture(
            CameraOperationContext value,
            PendingJpeg request,
            boolean encoderWasActive,
            int requestedJpegRotation,
            long requestId,
            long started) throws CameraAccessException, InterruptedException {
        CaptureRequest.Builder builder = camera.createCaptureRequest(
                encoderWasActive
                        ? CameraDevice.TEMPLATE_VIDEO_SNAPSHOT
                        : CameraDevice.TEMPLATE_STILL_CAPTURE);
        SharedCameraPipelineSupport.applyFps(builder, configuredFpsRange);
        int cameraJpegRotation = encoderWasActive ? 0 : requestedJpegRotation;
        builder.set(CaptureRequest.JPEG_ORIENTATION, cameraJpegRotation);
        GpsCoordinate location = currentCaptureLocation("photo");
        if (location != null) applyJpegLocation(builder, location);
        if (encoderWasActive) {
            logger.info(LogCategory.CAMERA, "unspecified", "Capture recording photo with stable camera orientation. "
                    + "Requested output rotation: " + requestedJpegRotation
                    + " degrees. Camera request rotation: 0 degrees.");
        }
        builder.setTag(new CaptureTag(value, requestId, true));
        addJpegTargets(builder, encoderWasActive);
        builder.addTarget(jpegReader.getSurface());
        session.capture(builder.build(), captureCallback, cameraHandler);
        boolean complete = request.completed.await(
                waitMillis(value, FRAME_TIMEOUT_MILLIS), TimeUnit.MILLISECONDS);
        if (complete) pendingJpeg = null;
        else cancelPendingJpeg(request);
        if (!complete) {
            CameraOperationOutcome outcome = deadlineExpired(value)
                    ? CameraOperationOutcome.TIMEOUT_UNKNOWN
                    : CameraOperationOutcome.CANDIDATE_SUSPECT;
            return result(value, CameraPipelineOperation.CAPTURE_JPEG, outcome, started,
                    "jpeg_callback_missing");
        }
        if (request.error == null) return null;
        request.discardOutput();
        return result(value, CameraPipelineOperation.CAPTURE_JPEG,
                request.outcome, request.failureClass, started, request.error);
    }

    private void beginEncoderOutput(
            DcamRecordingOutput recordingOutput,
            long fileSizeLimitBytes,
            RecordingLimitListener limitListener,
            boolean includeAudio) throws IOException {
        if (recordingOutput == null) {
            encoder.begin(videoArtifact, fileSizeLimitBytes,
                    limitListener::onLimitReached, includeAudio);
        } else {
            encoder.begin(recordingOutput, fileSizeLimitBytes,
                    limitListener::onLimitReached, includeAudio);
        }
    }

    private CameraOperationResult beginEncoderSegment(
            CameraOperationContext value,
            File outputFile,
            DcamRecordingOutput recordingOutput,
            long fileSizeLimitBytes,
            RecordingLimitListener limitListener,
            int requestedRotationDegrees,
            long started)
            throws CameraAccessException, IOException, InterruptedException, PipelineFailure {
        encoderFinalized = false;
        finalizedDurationUs = 0L;
        retainVideoArtifactOnRelease = false;
        encodedVideoResolution = null;
        capturedJpegResolution = null;
        jpegCapturedWhileEncoderActive = false;
        videoArtifact = Objects.requireNonNull(outputFile, OUTPUT_FILE);
        int encoderRotation = CameraOrientation.normalize(requestedRotationDegrees);
        if (!encoder.setRotation(encoderRotation)) {
            return result(value, CameraPipelineOperation.START_ENCODER,
                    CameraOperationOutcome.GLOBAL_FAILURE, started,
                    "encoder_rotation_update_failed");
        }

        boolean includeAudio = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        beginEncoderOutput(recordingOutput, fileSizeLimitBytes, limitListener, includeAudio);
        if (!encoderInputActive) {
            startTopologyRecording();
            encoderInputActive = true;
        }

        encoderActive = true;
        return awaitEncoderReadiness(value, includeAudio, started);
    }

    private CameraOperationResult awaitEncoderReadiness(
            CameraOperationContext value, boolean includeAudio, long started)
            throws InterruptedException {
        long readyDeadline = SystemClock.elapsedRealtime()
                + waitMillis(value, FRAME_TIMEOUT_MILLIS);
        boolean videoReady = encoder.awaitFirstSample(Math.max(
                1L, readyDeadline - SystemClock.elapsedRealtime()));
        boolean audioReady = !includeAudio || encoder.awaitAudioCaptureReady(Math.max(
                1L, readyDeadline - SystemClock.elapsedRealtime()));
        if (videoReady && audioReady) {
            return result(value, CameraPipelineOperation.START_ENCODER,
                    CameraOperationOutcome.PASS, started, encoderStartDetail());
        }
        encoderActive = false;
        discardEncoderSegment();
        CameraOperationOutcome outcome;
        if (encoder.callbackError() != null) {
            outcome = CameraOperationOutcome.GLOBAL_FAILURE;
        } else if (deadlineExpired(value)) {
            outcome = CameraOperationOutcome.TIMEOUT_UNKNOWN;
        } else {
            outcome = CameraOperationOutcome.CANDIDATE_SUSPECT;
        }
        String detail = !videoReady
                ? "first_encoded_sample_missing" : "audio_capture_not_ready";
        return result(value, CameraPipelineOperation.START_ENCODER, outcome, started,
                detail + ":codec=" + encoder.callbackError());
    }

    private void discardFailedEncoderSegment() {
        encoderActive = false;
        discardEncoderSegment();
    }

    private void applyJpegLocation(CaptureRequest.Builder builder, GpsCoordinate location) {
        try {
            builder.set(CaptureRequest.JPEG_GPS_LOCATION, jpegLocation(location));
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, "Capture photo without GPS metadata because camera request "
                    + "rejected current location.", error);
        }
    }

    private CameraOperationResult applyJpegOrientationMetadata(
            CameraOperationContext value, PendingJpeg request, File output,
            int requestedJpegRotation, long started) {
        try {
            CameraOrientation.applyJpegOrientationMetadata(output, requestedJpegRotation);
            return null;
        } catch (IOException error) {
            request.discardOutput();
            logger.warn(LogCategory.CAMERA, "unspecified", null, "Write recording photo orientation metadata failed. "
                    + "Requested rotation: " + requestedJpegRotation
                    + " degrees. Staged JPEG removed.", error);
            return result(value, CameraPipelineOperation.CAPTURE_JPEG,
                    CameraOperationOutcome.BLOCKED_EXTERNAL,
                    CameraFailureClass.JPEG_OUTPUT, started,
                    "jpeg_orientation_metadata:" + error.getClass().getSimpleName());
        }
    }

    @Override public final CameraOperationResult release(CameraOperationContext value) {
        synchronized (encoderFinalizationLock) {
            synchronized (operationLock) {
                long started = SystemClock.elapsedRealtime();
                Objects.requireNonNull(value, OPERATION_CONTEXT);
                if (activeContext != null && !value.matchesCurrentOperation(activeContext)) {
                    return result(value, CameraPipelineOperation.RELEASE,
                            CameraOperationOutcome.STALE, started,
                            "release_rejected_for_newer_generation");
                }
                boolean released = releaseResources(true);
                return result(value, CameraPipelineOperation.RELEASE,
                        released ? CameraOperationOutcome.PASS
                                : CameraOperationOutcome.TRANSIENT_RETRYABLE,
                        started, released ? "release_complete" : "release_timeout");
            }
        }
    }

    @Override public final CameraPipelineDiagnostics diagnostics(CameraOperationContext value) {
        Objects.requireNonNull(value, OPERATION_CONTEXT);
        CameraOperationContext current = activeContext;
        if (!pipelineId().equals(value.verificationPipelineId())
                || current == null || !value.matchesCurrentOperation(current)) {
            return staleDiagnostics(value);
        }
        long source = sourceFrameCount.get();
        long preview = previewFrameCount.get();
        long encoded = encoder == null ? 0 : encoder.totalSamples();
        long measuredFps = measuredFps(source);
        logFpsMeasurement(value, measuredFps);
        String detail = LOG_PIPELINE_PREFIX + pipelineId()
                + ",cameraOutputs=" + cameraOutputCount
                + "," + surfaceMetricName + "=" + downstreamSurfaceCount
                + ",jpegSurfaces=" + jpegSurfaceCount
                + ",cameraOpenCount=" + cameraOpenCount
                + ",sessionCreateCount=" + sessionCreateCount
                + ",configuredFpsRange=" + configuredFpsRange
                + ",measuredFps=" + measuredFps
                + ",previewDrops=" + previewDropCount.get()
                + ",asyncOutcome=" + asynchronousOutcome
                + ",asyncDetail=" + asynchronousDetail
                + topologyDiagnostics();
        return new CameraPipelineDiagnostics(value, sessionBound, previewProgressing,
                encoderActive, encoderFinalized, source, preview, encoded,
                cameraOutputCount,
                downstreamSurfaceCount,
                Optional.ofNullable(encodedVideoResolution),
                Optional.ofNullable(capturedJpegResolution), jpegCapturedWhileEncoderActive,
                Optional.ofNullable(videoArtifact).filter(file -> encoderFinalized)
                        .map(File::getAbsolutePath),
                Optional.ofNullable(jpegArtifact).map(File::getAbsolutePath), detail);
    }
    private static CameraPipelineDiagnostics staleDiagnostics(
            CameraOperationContext value) {
        return new CameraPipelineDiagnostics(value, false, false, false, false,
                0, 0, 0, 0, 0, Optional.empty(), Optional.empty(), false,
                Optional.empty(), Optional.empty(), "stale_or_released_context");
    }
    protected final int cameraOpenCountValue() { return cameraOpenCount; }

    protected final int sessionCreateCountValue() { return sessionCreateCount; }

    protected final Context context() { return context; }

    protected final Logger logger() { return logger; }

    protected final Surface externalPreviewSurface() { return externalPreviewSurface; }

    protected final Handler cameraHandler() { return cameraHandler; }

    protected final SharedAvcEncoder encoder() { return encoder; }

    protected final boolean standaloneImageSession() { return standaloneImageSession; }

    protected final CameraCaptureSession cameraSession() { return session; }

    protected final boolean cameraSessionReleased() { return session == null; }

    protected final Surface jpegSurface() { return jpegReader.getSurface(); }

    protected final Range<Integer> configuredFpsRange() { return configuredFpsRange; }

    protected final long previewFrameCount() { return previewFrameCount.get(); }

    protected final long incrementPreviewDropCount() {
        return previewDropCount.incrementAndGet();
    }

    protected final void setPreviewDropCount(long value) { previewDropCount.set(value); }

    protected final CameraOperationContext activeContext() { return activeContext; }

    protected final long signalSourceFrame() {
        long frames = sourceFrameCount.incrementAndGet();
        firstSourceFrame.countDown();
        return frames;
    }

    protected final void signalSourceFrame(long timestampNanos) {
        signalSourceFrame();
        recordSensorTimestamp(timestampNanos);
        SharedAvcEncoder current = encoder;
        if (current != null) {
            current.observeVideoFrameTimestamp(timestampNanos, sensorTimestampRealtime);
        }
    }

    protected final void recordSensorTimestamp(long timestampNanos) {
        if (firstSensorTimestampNanos < 0) firstSensorTimestampNanos = timestampNanos;
        lastSensorTimestampNanos = timestampNanos;
    }

    protected final void signalPreviewFrame() {
        synchronized (previewFrameLock) {
            previewFrameCount.incrementAndGet();
            previewFrameLock.notifyAll();
        }
    }

    protected final void openEncoder(int width, int height, int framesPerSecond)
            throws PipelineFailure {
        int encoderRotation = rotationDegrees;
        try {
            encoder = SharedAvcEncoder.open(width, height, framesPerSecond, logger,
                    pipelineId().value(), encoderThreadName, recycleEncoder, encoderRotation,
                    preRecordGopDurationMillis, captureLocation);
        } catch (IOException error) {
            throw new PipelineFailure(CameraOperationOutcome.CANDIDATE_SUSPECT,
                    "encoder_prepare:" + error.getMessage());
        }
    }

    protected final void configureFps(CameraOperationContext value) throws PipelineFailure {
        configuredFpsRange = selectFpsRange(value);
    }
    private void prepareResources(CameraOperationContext value, boolean standaloneImage)
            throws IOException, PipelineFailure, InterruptedException {
        int width = value.tuple().videoMode().resolution().actual().width();
        int height = value.tuple().videoMode().resolution().actual().height();
        int imageWidth = value.tuple().imageMode().resolution().actual().width();
        int imageHeight = value.tuple().imageMode().resolution().actual().height();
        int framesPerSecond = value.tuple().videoMode().framesPerSecond();
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new IOException("cannot create output directory " + outputDirectory);
        }
        cameraThread = new HandlerThread(cameraThreadName);
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        logCameraCharacteristics(value);
        firstSourceFrame = new CountDownLatch(1);
        prepareTopology(value, standaloneImage, width, height, framesPerSecond);
        jpegReader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.JPEG, 2);
        jpegReader.setOnImageAvailableListener(this::onJpegAvailable, cameraHandler);
        finishTopologyPreparation(value, standaloneImage);
        if (configuredFpsRange == null) configureFps(value);
    }
    private void logCameraCharacteristics(CameraOperationContext value) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, "shared_camera_capture stage=camera_characteristics"
                    + " outcome=unavailable cameraId=" + value.cameraId().value(), null);
            return;
        }
        try {
            CameraCharacteristics characteristics =
                    manager.getCameraCharacteristics(value.cameraId().value());
            availablePhysicalCameraIds = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? characteristics.getPhysicalCameraIds().toString() : "unsupported";
            Integer timestampSource = characteristics.get(
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
            sensorTimestampRealtime = timestampSource != null
                    && timestampSource
                    == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;
            logger.info(LogCategory.CAMERA, "unspecified", "Use camera " + value.cameraId().value() + " frame timestamps from "
                    + (sensorTimestampRealtime
                    ? "elapsed realtime clock."
                    : "camera-specific clock calibrated against elapsed realtime."));
        } catch (CameraAccessException | RuntimeException error) {
            logger.warn(LogCategory.CAMERA, "unspecified", null, "shared_camera_capture stage=camera_characteristics"
                    + " outcome=unavailable cameraId=" + value.cameraId().value(), error);
        }
    }

    // Android lint requires the permission-specific catch; keep it separate from the
    // RuntimeException fallback even though SecurityException is a RuntimeException.
    @SuppressWarnings("java:S2147")
    private void openCamera(CameraOperationContext value)
            throws PipelineFailure, InterruptedException {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) throw new PipelineFailure(
                CameraOperationOutcome.GLOBAL_FAILURE, "camera_manager_missing");
        CountDownLatch opened = new CountDownLatch(1);
        AtomicReference<PipelineFailure> failure = new AtomicReference<>();
        CountDownLatch closeSignal = new CountDownLatch(1);
        cameraClosed = closeSignal;
        if (context.checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            closeSignal.countDown();
            throw new SecurityException("camera_permission_denied");
        }
        try {
            manager.openCamera(value.cameraId().value(), cameraStateCallback(
                    value, opened, failure, closeSignal), cameraHandler);
        } catch (CameraAccessException error) {
            closeSignal.countDown();
            throw cameraAccessPipelineFailure("camera_open", error);
        } catch (SecurityException error) {
            closeSignal.countDown();
            throw error;
        } catch (RuntimeException error) {
            closeSignal.countDown();
            throw error;
        }
        if (!opened.await(waitMillis(value, OPEN_TIMEOUT_MILLIS), TimeUnit.MILLISECONDS)) {
            throw new PipelineFailure(deadlineExpired(value)
                    ? CameraOperationOutcome.TIMEOUT_UNKNOWN
                    : CameraOperationOutcome.TRANSIENT_RETRYABLE, "camera_open_timeout");
        }
        if (failure.get() != null) throw failure.get();
        if (camera == null) throw new PipelineFailure(
                CameraOperationOutcome.GLOBAL_FAILURE, "camera_open_without_device");
    }

    private CameraDevice.StateCallback cameraStateCallback(
            CameraOperationContext value,
            CountDownLatch opened,
            AtomicReference<PipelineFailure> failure,
            CountDownLatch closeSignal) {
        return new CameraDevice.StateCallback() {
            @Override public void onOpened(CameraDevice openedCamera) {
                if (!isCurrent(value)) {
                    openedCamera.close();
                    failure.compareAndSet(null,
                            new PipelineFailure(CameraOperationOutcome.STALE,
                                    "late_camera_open"));
                } else {
                    camera = openedCamera;
                    cameraOpenCount++;
                }
                opened.countDown();
            }

            @Override public void onDisconnected(CameraDevice disconnectedCamera) {
                disconnectedCamera.close();
                if (!isCurrent(value)) {
                    failure.compareAndSet(null, new PipelineFailure(
                            CameraOperationOutcome.STALE, "late_camera_disconnect"));
                    opened.countDown();
                    return;
                }
                recordAsyncFailure(CameraOperationOutcome.TRANSIENT_RETRYABLE,
                        "camera_disconnected", null);
                failure.compareAndSet(null, new PipelineFailure(
                        CameraOperationOutcome.TRANSIENT_RETRYABLE, "camera_disconnected"));
                opened.countDown();
            }

            @Override public void onError(CameraDevice errorCamera, int errorCode) {
                errorCamera.close();
                if (!isCurrent(value)) {
                    failure.compareAndSet(null, new PipelineFailure(
                            CameraOperationOutcome.STALE, "late_camera_error"));
                    opened.countDown();
                    return;
                }
                CameraOperationOutcome outcome =
                        CameraPipelineFailureClassifier.classifyCameraStateError(errorCode);
                String detail = "camera_state_error:" + errorCode;
                recordAsyncFailure(outcome, detail, null);
                failure.compareAndSet(null, new PipelineFailure(outcome, detail));
                opened.countDown();
            }

            @Override public void onClosed(CameraDevice closedCamera) {
                closeSignal.countDown();
            }
        };
    }

    private void createSession(CameraOperationContext value)
            throws PipelineFailure, InterruptedException {
        CountDownLatch configured = new CountDownLatch(1);
        AtomicReference<PipelineFailure> failure = new AtomicReference<>();
        try {
            List<OutputConfiguration> outputs = List.of(
                    cameraOutputConfiguration(),
                    new OutputConfiguration(jpegReader.getSurface()));
            cameraOutputCount = outputs.size();
            downstreamSurfaceCount = topologyDownstreamSurfaceCount();
            jpegSurfaceCount = outputs.size() - 1;
            CameraSurfaceTopology topology = new CameraSurfaceTopology(
                    cameraOutputCount, downstreamSurfaceCount, jpegSurfaceCount);
            boolean validTopology = standaloneImageSession
                    ? topology.isExactStandaloneImage()
                    : topology.isExactPrivateSourcePlusJpeg();
            if (!validTopology) {
                throw new PipelineFailure(CameraOperationOutcome.GLOBAL_FAILURE,
                        "camera_output_topology_mismatch:" + topology);
            }
            camera.createCaptureSessionByOutputConfigurations(
                    outputs,
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession configuredSession) {
                            if (!isCurrent(value)) {
                                configuredSession.close();
                                failure.compareAndSet(null, new PipelineFailure(
                                        CameraOperationOutcome.STALE,
                                        "late_session_configured"));
                            } else {
                                session = configuredSession;
                                sessionCreateCount++;
                            }
                            configured.countDown();
                        }

                        @Override public void onConfigureFailed(
                                CameraCaptureSession failedSession) {
                            failedSession.close();
                            CameraOperationOutcome outcome = isCurrent(value)
                                    ? CameraOperationOutcome.CANDIDATE_SUSPECT
                                    : CameraOperationOutcome.STALE;
                            failure.compareAndSet(null, new PipelineFailure(outcome,
                                    isCurrent(value) ? "shared_session_rejected"
                                            : "late_session_rejected"));
                            configured.countDown();
                        }
                    }, cameraHandler);
        } catch (CameraAccessException error) {
            throw cameraAccessPipelineFailure("session_create", error);
        }
        if (!configured.await(waitMillis(value, SESSION_TIMEOUT_MILLIS), TimeUnit.MILLISECONDS)) {
            throw new PipelineFailure(deadlineExpired(value)
                    ? CameraOperationOutcome.TIMEOUT_UNKNOWN
                    : CameraOperationOutcome.TRANSIENT_RETRYABLE,
                    "session_configure_timeout");
        }
        if (failure.get() != null) throw failure.get();
        if (session == null) throw new PipelineFailure(
                CameraOperationOutcome.GLOBAL_FAILURE, "session_configured_without_session");
    }
    protected final void startRepeating(boolean includeEncoder) throws CameraAccessException {
        CaptureRequest.Builder builder = camera.createCaptureRequest(
                repeatingTemplate(includeEncoder));
        SharedCameraPipelineSupport.applyFps(builder, configuredFpsRange);
        builder.setTag(activeContext);
        addRepeatingTargets(builder, includeEncoder);
        session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler);
    }

    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession ignored,
                        CaptureRequest request, TotalCaptureResult result) {
                    if (!callbackIsCurrent(request)) return;
                    logActivePhysicalCamera(result);
                    AbstractSharedCameraPipeline.this.onCaptureCompleted(result);
                }

                @Override public void onCaptureStarted(CameraCaptureSession ignored,
                        CaptureRequest request, long timestamp, long frameNumber) {
                    CaptureTag tag = captureTag(request);
                    PendingJpeg jpeg = pendingJpeg;
                    if (tag != null && tag.jpeg && jpeg != null
                            && jpeg.requestId == tag.requestId) {
                        JpegPayload early = jpeg.markStarted(timestamp);
                        if (early != null) {
                            logger.info(LogCategory.CAMERA, "unspecified", "Recover JPEG capture callback order for request "
                                    + tag.requestId + ". Buffered image timestamp matched "
                                    + "shutter timestamp: " + timestamp + " ns.");
                            writeJpeg(jpeg, early);
                        }
                    }
                }

                @Override public void onCaptureFailed(CameraCaptureSession ignored,
                        CaptureRequest request, CaptureFailure failure) {
                    if (!callbackIsCurrent(request)) return;
                    CaptureTag tag = captureTag(request);
                    PendingJpeg jpeg = pendingJpeg;
                    if (tag != null && tag.jpeg) {
                        if (jpeg != null && jpeg.requestId == tag.requestId) {
                            jpeg.fail(CameraOperationOutcome.CANDIDATE_SUSPECT,
                                    "capture_failed:reason=" + failure.getReason());
                        }
                    } else {
                        recordAsyncFailure(CameraOperationOutcome.CANDIDATE_SUSPECT,
                                "capture_failed:reason=" + failure.getReason(), null);
                    }
                }

                private boolean callbackIsCurrent(CaptureRequest request) {
                    Object tag = request == null ? null : request.getTag();
                    if (tag instanceof CameraOperationContext operationContext) {
                        return isCurrent(operationContext);
                    }
                    return tag instanceof CaptureTag captureTag && isCurrent(captureTag.context);
                }

                private void logActivePhysicalCamera(TotalCaptureResult result) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || result == null) return;
                    String reported = result.get(
                            CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
                    String resolved;
                    if (reported != null) {
                        resolved = reported;
                    } else if ("[]".equals(availablePhysicalCameraIds)) {
                        resolved = "not_applicable";
                    } else {
                        resolved = "unreported";
                    }
                    if (resolved.equals(activePhysicalCameraId)) return;
                    activePhysicalCameraId = resolved;
                    CameraOperationContext value = activeContext;
                    if (value == null) return;
                    String cameraId = value.cameraId().value();
                    String pipeline = pipelineId().value();
                    String message;
                    if ("not_applicable".equals(resolved)) {
                        message = "Camera2 opened camera " + cameraId + ".";
                    } else if ("unreported".equals(resolved)) {
                        message = "Camera " + cameraId + " exposes physical sub-cameras "
                                + availablePhysicalCameraIds
                                + ", but Camera2 did not report which one is active.";
                    } else {
                        message = "Camera2 selected physical camera " + resolved + " for camera "
                                + cameraId + ". Available physical cameras: "
                                + availablePhysicalCameraIds + ".";
                    }
                    logger.info(LogCategory.CAMERA, "unspecified", message + " Pipeline: " + pipeline + ".");
                }
            };

    private static CaptureTag captureTag(CaptureRequest request) {
        Object tag = request == null ? null : request.getTag();
        return tag instanceof CaptureTag captureTag ? captureTag : null;
    }

    private void cancelPendingJpeg(PendingJpeg request) {
        if (pendingJpeg == request) pendingJpeg = null;
        request.cancel();
        request.discardOutput();
    }

    private void onJpegAvailable(ImageReader reader) {
        PendingJpeg request = pendingJpeg;
        try (Image image = reader.acquireNextImage()) {
            if (image == null || request == null || !isCurrent(request.context)) return;
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            JpegPayload ready = request.accept(new JpegPayload(
                    image.getTimestamp(), bytes,
                    new CameraResolution(image.getWidth(), image.getHeight())));
            if (ready != null) writeJpeg(request, ready);
        } catch (RuntimeException error) {
            if (request != null) request.fail(CameraOperationOutcome.GLOBAL_FAILURE,
                    "jpeg_reader:" + error.getClass().getSimpleName());
            logger.warn(LogCategory.CAMERA, "unspecified", null, activeContext == null
                    ? "pipeline=b-camera2-egl-fanout-v1 stage=jpeg_reader"
                    : prefix(activeContext, "jpeg_reader"), error);
        }
    }

    private void writeJpeg(PendingJpeg request, JpegPayload payload) {
        try {
            writeJpegOutput(request, payload);
            if (!request.isCancelled()) request.complete(payload.resolution());
        } catch (IOException error) {
            request.fail(CameraOperationOutcome.BLOCKED_EXTERNAL,
                    CameraFailureClass.JPEG_OUTPUT,
                    "jpeg_write:" + error.getClass().getSimpleName());
            logger.warn(LogCategory.CAMERA, "unspecified", null, activeContext == null
                    ? "pipeline=b-camera2-egl-fanout-v1 stage=jpeg_write"
                    : prefix(activeContext, "jpeg_write"), error);
        } catch (RuntimeException error) {
            request.fail(CameraOperationOutcome.GLOBAL_FAILURE,
                    CameraFailureClass.JPEG_OUTPUT,
                    "jpeg_write:" + error.getClass().getSimpleName());
            logger.warn(LogCategory.CAMERA, "unspecified", null, activeContext == null
                    ? "pipeline=b-camera2-egl-fanout-v1 stage=jpeg_write"
                    : prefix(activeContext, "jpeg_write"), error);
        } finally {
            request.finishWrite();
            if (request.isCancelled()) request.discardOutput();
        }
    }

    private Range<Integer> selectFpsRange(CameraOperationContext value) throws PipelineFailure {
        int selected = value.tuple().videoMode().framesPerSecond();
        try {
            Range<Integer> best = SharedCameraPipelineSupport.selectFpsRange(context, value);
            if (best == null) throw new PipelineFailure(
                    CameraOperationOutcome.CANDIDATE_SUSPECT,
                    "fps_range_missing:selected=" + selected);
            return best;
        } catch (CameraAccessException error) {
            throw cameraAccessPipelineFailure("fps_query", error);
        }
    }

    private CameraOperationResult validateIdentity(CameraOperationContext value,
            CameraPipelineOperation operation, long started, boolean currentRequired) {
        Objects.requireNonNull(value, OPERATION_CONTEXT);
        if (!pipelineId().equals(value.verificationPipelineId())) {
            return result(value, operation, CameraOperationOutcome.STALE, started,
                    "pipeline_identity_mismatch:expected=" + pipelineId());
        }
        if (value.codec() != VideoCodec.H264) return result(value, operation,
                CameraOperationOutcome.BLOCKED_EXTERNAL, started,
                "codec_not_implemented:" + value.codec().id());
        if (currentRequired && (activeContext == null
                || !value.matchesCurrentOperation(activeContext))) {
            return result(value, operation, CameraOperationOutcome.STALE, started,
                    "operation_generation_stale");
        }
        return null;
    }

    private CameraOperationResult validateBound(CameraOperationContext value,
            CameraPipelineOperation operation, long started) {
        CameraOperationResult invalid = validateIdentity(value, operation, started, true);
        if (invalid != null) return invalid;
        if (deadlineExpired(value)) return timeout(value, operation, started,
                "operation_deadline_expired");
        CameraOperationResult async = asynchronousFailure(value, operation, started);
        if (async != null) return async;
        if (!sessionBound || camera == null || session == null) return result(value, operation,
                CameraOperationOutcome.TRANSIENT_RETRYABLE, started, "session_not_bound");
        return null;
    }

    private CameraOperationResult asynchronousFailure(CameraOperationContext value,
            CameraPipelineOperation operation, long started) {
        if (asynchronousOutcome == null) return null;
        return result(value, operation, asynchronousOutcome, started,
                asynchronousDetail == null ? "asynchronous_failure" : asynchronousDetail);
    }

    protected final void recordAsyncFailure(CameraOperationOutcome outcome,
            String detail, Throwable error) {
        asynchronousOutcome = outcome;
        asynchronousDetail = detail;
        String message = activeContext == null
                ? LOG_PIPELINE_PREFIX + pipelineId().value() + " stage=asynchronous"
                        + LOG_OUTCOME + outcome + LOG_DETAIL + detail
                : prefix(activeContext, "asynchronous")
                        + LOG_OUTCOME + outcome + LOG_DETAIL + detail;
        if (error == null) logger.info(LogCategory.CAMERA, "unspecified", message); else logger.warn(LogCategory.CAMERA, "unspecified", null, message, error);
    }
    private CameraOperationResult releaseAfterBindFailure(
            CameraOperationContext value, long started, CameraOperationResult failure) {
        boolean cleanupCompleted = releaseResources(true);
        CameraOperationOutcome outcome = CameraPipelineFailureClassifier.withCleanupResult(
                failure.outcome(), cleanupCompleted);
        if (outcome == failure.outcome()) return failure;
        return result(value, CameraPipelineOperation.BIND_SESSION, outcome, started,
                "bind_cleanup_timeout:originalOutcome=" + failure.outcome()
                        + ",originalDetail=" + failure.detail());
    }

    private CameraOperationResult cameraAccessFailure(CameraOperationContext value,
            CameraPipelineOperation operation, long started,
            String stage, CameraAccessException error) {
        PipelineFailure failure = cameraAccessPipelineFailure(stage, error);
        logger.warn(LogCategory.CAMERA, "unspecified", null, prefix(value, stage) + LOG_OUTCOME + failure.outcome
                + " elapsedMs=" + elapsed(started), error);
        return result(value, operation, failure.outcome, started, failure.getMessage());
    }

    private static PipelineFailure cameraAccessPipelineFailure(
            String stage, CameraAccessException error) {
        return new PipelineFailure(
                CameraPipelineFailureClassifier.classifyCameraAccessReason(error.getReason()),
                stage + ":camera_access_reason=" + error.getReason());
    }

    private CameraOperationResult failure(CameraOperationContext value,
            CameraPipelineOperation operation,
            CameraPipelineFailureClassifier.Signal signal,
            long started, String stage, Throwable error) {
        CameraOperationOutcome outcome = CameraPipelineFailureClassifier.classify(signal);
        String detail = stage + ":" + error.getClass().getSimpleName();
        logger.warn(LogCategory.CAMERA, "unspecified", null, prefix(value, stage) + LOG_OUTCOME + outcome
                + " elapsedMs=" + elapsed(started) + LOG_DETAIL + detail, error);
        return result(value, operation, outcome, started, detail);
    }

    private CameraOperationResult timeout(CameraOperationContext value,
            CameraPipelineOperation operation, long started, String detail) {
        return result(value, operation, CameraOperationOutcome.TIMEOUT_UNKNOWN,
                started, detail);
    }

    private CameraOperationResult result(CameraOperationContext value,
            CameraPipelineOperation operation, CameraOperationOutcome outcome,
            long started, String detail) {
        return new CameraOperationResult(
                value, operation, outcome, elapsed(started), detail);
    }

    private CameraOperationResult result(CameraOperationContext value,
            CameraPipelineOperation operation, CameraOperationOutcome outcome,
            CameraFailureClass failureClass, long started, String detail) {
        return new CameraOperationResult(
                value, operation, outcome, failureClass, elapsed(started), detail);
    }

    static String captureProfileValidationMessage(CameraOperationResult result) {
        CameraOperationContext value = result.context();
        String message = "Capture profile validation "
                + (result.outcome() == CameraOperationOutcome.PASS
                        ? "succeeded" : "failed")
                + " for camera " + value.cameraId()
                + ": video " + value.tuple().videoMode().resolution()
                + " at " + value.tuple().videoMode().framesPerSecond() + " fps"
                + ", photo " + value.tuple().imageMode().resolution()
                + ", codec " + value.codec().id();
        return result.outcome() == CameraOperationOutcome.PASS
                ? message : message + ". Detail: " + result.detail();
    }

    protected final void log(CameraOperationContext value,
            String stage, String outcome, String detail) {
        logger.info(LogCategory.CAMERA, "unspecified", prefix(value, stage) + LOG_OUTCOME + outcome + LOG_DETAIL + detail);
    }

    protected final String prefix(CameraOperationContext value, String stage) {
        return LOG_PIPELINE_PREFIX + pipelineId() + " cameraId=" + value.cameraId()
                + operationContextDetail(value) + " codec=" + value.codec().id()
                + " sessionGeneration=" + value.sessionGeneration()
                + " cameraHealthGeneration=" + value.cameraHealthGeneration()
                + " stage=" + stage;
    }

    private boolean releaseResources(boolean enforceTimeout) {
        long started = SystemClock.elapsedRealtime();
        long deadline = started + RELEASE_TIMEOUT_MILLIS;
        CountDownLatch closeSignal = beginRelease();
        boolean released = closeCameraSession();
        released &= closeCameraDevice();
        released &= awaitCameraClose(closeSignal, enforceTimeout, deadline);
        released &= closeJpegReader();
        released &= releaseTopology(deadline);
        released &= closeEncoder(deadline);
        releaseArtifacts();
        released &= stopCameraThread(deadline);
        asynchronousOutcome = null;
        asynchronousDetail = null;
        if (!released) {
            logger.info(LogCategory.CAMERA, "unspecified", LOG_PIPELINE_PREFIX + pipelineId()
                    + " stage=cleanup outcome=release_timeout elapsedMs=" + elapsed(started));
        }
        return released;
    }

    private CountDownLatch beginRelease() {
        activeContext = null;
        CountDownLatch closeSignal = cameraClosed;
        sessionBound = false;
        previewProgressing = false;
        encoderActive = false;
        encoderInputActive = false;
        PendingJpeg jpeg = pendingJpeg;
        if (jpeg != null) cancelPendingJpeg(jpeg);
        return closeSignal;
    }

    private boolean closeCameraSession() {
        CameraCaptureSession currentSession = session;
        if (currentSession == null) return true;
        try { currentSession.stopRepeating(); } catch (Exception ignored) {
            // Best-effort cleanup continues with abort and close.
        }
        try { currentSession.abortCaptures(); } catch (Exception ignored) {
            // Best-effort cleanup continues with close.
        }
        try {
            currentSession.close();
            session = null;
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private boolean closeCameraDevice() {
        CameraDevice currentCamera = camera;
        if (currentCamera == null) return true;
        try {
            currentCamera.close();
            camera = null;
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean awaitCameraClose(
            CountDownLatch closeSignal, boolean enforceTimeout, long deadline) {
        if (!enforceTimeout || closeSignal.getCount() == 0) return true;
        try {
            return closeSignal.await(remainingReleaseMillis(deadline), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean closeJpegReader() {
        if (jpegReader == null) return true;
        try {
            jpegReader.close();
            jpegReader = null;
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private boolean closeEncoder(long deadline) {
        if (encoder == null) return true;
        boolean encoderReleased;
        try {
            encoderReleased = encoder.closeAndAwait(remainingReleaseMillis(deadline));
        } catch (RuntimeException error) {
            encoderReleased = false;
        }
        if (encoderReleased) encoder = null;
        return encoderReleased;
    }

    private void releaseArtifacts() {
        boolean videoRetained = retainVideoArtifactOnRelease && videoArtifact != null;
        boolean videoDeleted = videoRetained || deleteArtifact(videoArtifact);
        boolean jpegDeleted = deleteArtifact(jpegArtifact);
        if (videoRetained) {
            logger.info(LogCategory.CAMERA, "unspecified", "Preserve failed recording staging artifact for recovery: "
                    + videoArtifact.getAbsolutePath() + ".");
            videoArtifact = null;
            retainVideoArtifactOnRelease = false;
        } else if (videoDeleted) {
            videoArtifact = null;
        }
        if (jpegDeleted) jpegArtifact = null;
        if (!videoDeleted || !jpegDeleted) {
            logger.info(LogCategory.CAMERA, "unspecified", LOG_PIPELINE_PREFIX + pipelineId()
                    + " stage=cleanup outcome=artifact_retry"
                    + " videoDeleted=" + videoDeleted + " jpegDeleted=" + jpegDeleted);
        }
    }

    private boolean stopCameraThread(long deadline) {
        if (cameraThread == null) return true;
        HandlerThread closingThread = cameraThread;
        closingThread.quitSafely();
        boolean joined = SharedCameraPipelineSupport.joinThread(
                closingThread, remainingReleaseMillis(deadline));
        if (joined) {
            cameraThread = null;
            cameraHandler = null;
        }
        return joined;
    }
    private void resetEvidence() {
        sourceFrameCount.set(0);
        previewFrameCount.set(0);
        previewDropCount.set(0);
        asynchronousOutcome = null;
        asynchronousDetail = null;
        sessionBound = false;
        previewProgressing = false;
        encoderActive = false;
        encoderInputActive = false;
        encoderFinalized = false;
        finalizedDurationUs = 0L;
        retainVideoArtifactOnRelease = false;
        jpegCapturedWhileEncoderActive = false;
        encodedVideoResolution = null;
        capturedJpegResolution = null;
        videoArtifact = null;
        jpegArtifact = null;
        firstSensorTimestampNanos = -1;
        lastSensorTimestampNanos = -1;
        sensorTimestampRealtime = false;
        cameraOpenCount = 0;
        sessionCreateCount = 0;
        cameraOutputCount = 0;
        downstreamSurfaceCount = 0;
        jpegSurfaceCount = 0;
        availablePhysicalCameraIds = UNRESOLVED;
        activePhysicalCameraId = UNRESOLVED;
        standaloneImageSession = false;
        resetTopologyEvidence();
    }
    private File artifact(CameraOperationContext value, String extension) {
        String cameraId = value.cameraId().value().replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(outputDirectory, pipelineId().value() + "-camera-" + cameraId
                + "-session-" + value.sessionGeneration() + extension);
    }

    private void logFpsMeasurement(CameraOperationContext value, long measuredFps) {
        int selectedFps = value.tuple().videoMode().framesPerSecond();
        if (measuredFps > 0 && measuredFps != selectedFps) {
            logger.info(LogCategory.CAMERA, "unspecified", prefix(value, "fps_measurement") + " outcome=warning"
                    + " configuredFps=" + selectedFps + " measuredFps=" + measuredFps);
        }
    }
    private long measuredFps(long frames) {
        long duration = lastSensorTimestampNanos - firstSensorTimestampNanos;
        if (frames < 2 || duration <= 0) return 0;
        return Math.round((frames - 1) * 1_000_000_000.0 / duration);
    }

    protected final boolean isCurrent(CameraOperationContext value) {
        CameraOperationContext current = activeContext;
        return current != null && value.matchesCurrentOperation(current);
    }






    protected final PipelineFailure topologyFailure(
            CameraOperationOutcome outcome, String detail) {
        return new PipelineFailure(outcome, detail);
    }


    protected abstract void prepareTopology(CameraOperationContext value,
            boolean standaloneImage, int width, int height, int framesPerSecond)
            throws IOException, PipelineFailure, InterruptedException;

    protected abstract void finishTopologyPreparation(
            CameraOperationContext value, boolean standaloneImage) throws PipelineFailure;

    protected abstract OutputConfiguration cameraOutputConfiguration();

    protected abstract int topologyDownstreamSurfaceCount();

    protected abstract int repeatingTemplate(boolean includeEncoder);

    protected abstract void addRepeatingTargets(
            CaptureRequest.Builder builder, boolean includeEncoder);

    protected abstract void addJpegTargets(
            CaptureRequest.Builder builder, boolean encoderWasActive);

    protected abstract void startTopologyRecording()
            throws CameraAccessException, PipelineFailure, InterruptedException;


    protected abstract String encoderStartDetail();

    protected void onCaptureCompleted(TotalCaptureResult result) {}

    protected boolean hasPreviewEvidence() { return true; }

    protected String previewProgressDetail() { return ""; }

    protected String topologyDiagnostics() { return ""; }

    protected String operationContextDetail(CameraOperationContext value) {
        return " tuple=" + value.tuple();
    }

    protected abstract boolean releaseTopology(long deadlineMillis);

    protected void resetTopologyEvidence() {}
    private record CaptureTag(
            CameraOperationContext context, long requestId, boolean jpeg) {}

    static record JpegPayload(
            long timestamp, byte[] bytes, CameraResolution resolution) {
        // Record patterns require Java 21; this Android module compiles with Java 17.
        @SuppressWarnings("java:S6878")
        @Override public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof JpegPayload payload)) return false;
            return timestamp == payload.timestamp
                    && Arrays.equals(bytes, payload.bytes)
                    && Objects.equals(resolution, payload.resolution);
        }

        @Override public int hashCode() {
            int result = Long.hashCode(timestamp);
            result = 31 * result + Arrays.hashCode(bytes);
            return 31 * result + Objects.hashCode(resolution);
        }

        @Override public String toString() {
            return "JpegPayload[timestamp=" + timestamp
                    + ", bytes=" + Arrays.toString(bytes)
                    + ", resolution=" + resolution + "]";
        }
    }

    private void writeJpegOutput(PendingJpeg request, JpegPayload payload) throws IOException {
        if (request.encryptedOutput == null) {
            writePlainJpeg(request.file, payload.bytes());
            return;
        }
        request.encryptedOutput.write(jpegBytesForOutput(request, payload));
    }

    private static void writePlainJpeg(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.getFD().sync();
        }
    }

    private static byte[] jpegBytesForOutput(PendingJpeg request, JpegPayload payload)
            throws IOException {
        if (!request.encoderWasActive) return payload.bytes();
        return JpegExifOrientation.apply(payload.bytes(),
                CameraOrientation.exifOrientation(request.requestedJpegRotation));
    }

    static final class PendingJpeg {
        private final CameraOperationContext context;
        private final File file;
        private final SegmentedAesGcmJpegOutput encryptedOutput;
        private final boolean encoderWasActive;
        private final int requestedJpegRotation;
        private final long requestId;
        private final CountDownLatch completed = new CountDownLatch(1);
        private final List<JpegPayload> earlyPayloads = new ArrayList<>();
        private volatile long expectedTimestamp = Long.MIN_VALUE;
        private volatile CameraResolution resolution;
        private volatile CameraOperationOutcome outcome = CameraOperationOutcome.PASS;
        private volatile CameraFailureClass failureClass = CameraFailureClass.NONE;
        private volatile String error;

        PendingJpeg(
                CameraOperationContext context,
                File file,
                SegmentedAesGcmJpegOutput encryptedOutput,
                boolean encoderWasActive,
                int requestedJpegRotation,
                long requestId) {
            this.context = context;
            this.file = file;
            this.encryptedOutput = encryptedOutput;
            this.encoderWasActive = encoderWasActive;
            this.requestedJpegRotation = requestedJpegRotation;
            this.requestId = requestId;
        }

        private boolean cancelled;
        private boolean writing;

        synchronized JpegPayload markStarted(long timestamp) {
            if (cancelled || completed.getCount() == 0) {
                earlyPayloads.clear();
                return null;
            }
            expectedTimestamp = timestamp;
            JpegPayload matching = null;
            for (JpegPayload payload : earlyPayloads) {
                if (payload.timestamp() == timestamp) {
                    matching = payload;
                    break;
                }
            }
            earlyPayloads.clear();
            if (writing || matching == null) return null;
            writing = true;
            return matching;
        }

        synchronized JpegPayload accept(JpegPayload payload) {
            if (cancelled || writing || completed.getCount() == 0) return null;
            if (expectedTimestamp == Long.MIN_VALUE) {
                earlyPayloads.add(payload);
                return null;
            }
            if (expectedTimestamp != payload.timestamp()) return null;
            writing = true;
            return payload;
        }

        private synchronized void finishWrite() {
            writing = false;
        }

        private void discardOutput() {
            if (encryptedOutput == null) deleteQuietly(file);
            else encryptedOutput.discard();
        }

        private synchronized boolean isCancelled() {
            return cancelled;
        }

        private synchronized void cancel() {
            cancelled = true;
            earlyPayloads.clear();
            outcome = CameraOperationOutcome.CANCELLED_UNKNOWN;
            failureClass = CameraFailureClass.NONE;
            error = "jpeg_cancelled";
            completed.countDown();
        }

        private synchronized void complete(CameraResolution value) {
            if (cancelled || completed.getCount() == 0) return;
            earlyPayloads.clear();
            resolution = value;
            completed.countDown();
        }

        private synchronized void fail(CameraOperationOutcome value, String detail) {
            fail(value, value.isCandidateFailure()
                    ? CameraFailureClass.UNKNOWN : CameraFailureClass.NONE, detail);
        }

        private synchronized void fail(
                CameraOperationOutcome value, CameraFailureClass classification, String detail) {
            if (cancelled || completed.getCount() == 0) return;
            earlyPayloads.clear();
            outcome = value;
            failureClass = classification;
            error = detail;
            completed.countDown();
        }
    }

    protected static final class PipelineFailure extends Exception {
        private final CameraOperationOutcome outcome;

        protected PipelineFailure(CameraOperationOutcome outcome, String message) {
            super(message);
            this.outcome = outcome;
        }
    }
}
