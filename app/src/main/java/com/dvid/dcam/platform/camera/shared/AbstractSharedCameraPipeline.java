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
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Range;
import android.view.Surface;
import com.dvid.dcam.core.logging.application.port.Logger;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
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
    private final Object operationLock = new Object();
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
    private String sessionUpdateDetail = "not_requested";
    private String availablePhysicalCameraIds = "unresolved";
    private String activePhysicalCameraId = "unresolved";
    private boolean standaloneImageSession;

    protected AbstractSharedCameraPipeline(Context context, Logger logger,
            File outputDirectory, Surface externalPreviewSurface, boolean recycleEncoder,
            int rotationDegrees, long preRecordGopDurationMillis,
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
        this.rotationDegrees = CameraOrientation.normalize(rotationDegrees);
        this.pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
        this.cameraThreadName = Objects.requireNonNull(cameraThreadName, "cameraThreadName");
        this.encoderThreadName = Objects.requireNonNull(encoderThreadName, "encoderThreadName");
        this.surfaceMetricName = Objects.requireNonNull(surfaceMetricName, "surfaceMetricName");
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
                logger.warn("shared_camera_capture stage=orientation_update"
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
            logger.warn(captureProfileValidationMessage(result), null);
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return result(value, CameraPipelineOperation.BIND_SESSION,
                        CameraOperationOutcome.BLOCKED_EXTERNAL, started,
                        "pipeline_unavailable:api_level=" + Build.VERSION.SDK_INT);
            }
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
        logger.info("Prime video encoder success. Pipeline: " + pipelineId
                + ". Elapsed: " + elapsed(started) + " ms. Pre-record GOP duration: "
                + preRecordGopDurationMillis + " ms.");
    }

    @Override public final CameraOperationResult updateSession(CameraOperationContext value) {
        synchronized (operationLock) {
            long started = SystemClock.elapsedRealtime();
            CameraOperationResult invalid = validateBound(value,
                    CameraPipelineOperation.UPDATE_SESSION, started);
            if (invalid != null) return invalid;
            try {
                sessionUpdateDetail = updateTopologySession(value, started);
                return result(value, CameraPipelineOperation.UPDATE_SESSION,
                        CameraOperationOutcome.PASS, started, sessionUpdateDetail);
            } catch (CameraAccessException error) {
                return cameraAccessFailure(value, CameraPipelineOperation.UPDATE_SESSION,
                        started, "session_update", error);
            }
        }
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
            Objects.requireNonNull(value, "operationContext");
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
            String detail = !sessionBound
                    ? "session_not_bound"
                    : encoderFailure != null
                            ? "encoder_failure:" + encoderFailure
                            : asyncOutcome != null
                                    ? (asynchronousDetail == null
                                            ? "asynchronous_failure" : asynchronousDetail)
                                    : !previewSignalAvailable
                                            ? "external_preview_frame_signal_unavailable" : "healthy";
            return new SharedCameraCapturePipeline.HealthSnapshot(
                    sessionBound,
                    encoderFailure != null || asyncOutcome != null
                            && asyncOutcome.requiresRuntimeRecovery(),
                    previewSignalAvailable, sourceFrameCount.get(), previewFrameCount.get(),
                    detail);
        }
    }

    private void discardEncoderSegment() {
        if (encoder != null) encoder.discard();
        deleteQuietly(videoArtifact);
        videoArtifact = null;
        retainVideoArtifactOnRelease = false;
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
        return startEncoder(value, Objects.requireNonNull(outputFile, "outputFile"), null,
                fileSizeLimitBytes, limitListener);
    }

    @Override public final CameraOperationResult startEncoder(
            CameraOperationContext value,
            DcamRecordingOutput recordingOutput,
            long fileSizeLimitBytes,
            RecordingLimitListener limitListener) {
        DcamRecordingOutput output = Objects.requireNonNull(
                recordingOutput, "recordingOutput");
        return startEncoder(value, output.file(), output, fileSizeLimitBytes, limitListener);
    }

    private CameraOperationResult startEncoder(
            CameraOperationContext value,
            File outputFile,
            DcamRecordingOutput recordingOutput,
            long fileSizeLimitBytes,
            RecordingLimitListener limitListener) {
        synchronized (operationLock) {
            long started = SystemClock.elapsedRealtime();
            CameraOperationResult invalid = validateBound(value,
                    CameraPipelineOperation.START_ENCODER, started);
            if (invalid != null) return invalid;
            if (encoderActive) return result(value, CameraPipelineOperation.START_ENCODER,
                    CameraOperationOutcome.PASS, started, "encoder_already_active");
            try {
                encoderFinalized = false;
                finalizedDurationUs = 0L;
                retainVideoArtifactOnRelease = false;
                encodedVideoResolution = null;
                capturedJpegResolution = null;
                jpegCapturedWhileEncoderActive = false;
                videoArtifact = Objects.requireNonNull(outputFile, "outputFile");
                int encoderRotation = rotationDegrees;
                if (!encoder.setRotation(encoderRotation)) {
                    return result(value, CameraPipelineOperation.START_ENCODER,
                            CameraOperationOutcome.GLOBAL_FAILURE, started,
                            "encoder_rotation_update_failed");
                }

                boolean includeAudio = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
                if (recordingOutput == null) {
                    encoder.begin(videoArtifact, fileSizeLimitBytes,
                            limitListener::onLimitReached, includeAudio);
                } else {
                    encoder.begin(recordingOutput, fileSizeLimitBytes,
                            limitListener::onLimitReached, includeAudio);
                }
                if (!encoderInputActive) {
                    startTopologyRecording();
                    encoderInputActive = true;
                }

                encoderActive = true;
                long readyDeadline = SystemClock.elapsedRealtime()
                        + waitMillis(value, FRAME_TIMEOUT_MILLIS);
                boolean videoReady = encoder.awaitFirstSample(Math.max(
                        1L, readyDeadline - SystemClock.elapsedRealtime()));
                boolean audioReady = !includeAudio || encoder.awaitAudioCaptureReady(Math.max(
                        1L, readyDeadline - SystemClock.elapsedRealtime()));
                if (!videoReady || !audioReady) {
                    encoderActive = false;
                    discardEncoderSegment();
                    CameraOperationOutcome outcome = encoder.callbackError() != null
                            ? CameraOperationOutcome.GLOBAL_FAILURE
                            : deadlineExpired(value)
                            ? CameraOperationOutcome.TIMEOUT_UNKNOWN
                            : CameraOperationOutcome.CANDIDATE_SUSPECT;
                    String detail = !videoReady
                            ? "first_encoded_sample_missing" : "audio_capture_not_ready";
                    return result(value, CameraPipelineOperation.START_ENCODER, outcome, started,
                            detail + ":codec=" + encoder.callbackError());
                }
                return result(value, CameraPipelineOperation.START_ENCODER,
                        CameraOperationOutcome.PASS, started, encoderStartDetail());
            } catch (InterruptedException error) {
                encoderActive = false;
                discardEncoderSegment();
                Thread.currentThread().interrupt();
                return result(value, CameraPipelineOperation.START_ENCODER,
                        CameraOperationOutcome.CANCELLED_UNKNOWN, started,
                        "encoder_start_interrupted");
            } catch (PipelineFailure error) {
                encoderActive = false;
                discardEncoderSegment();
                return result(value, CameraPipelineOperation.START_ENCODER,
                        error.outcome, started, error.getMessage());
            } catch (IOException error) {
                encoderActive = false;
                discardEncoderSegment();
                return failure(value, CameraPipelineOperation.START_ENCODER,
                        CameraPipelineFailureClassifier.Signal.STORAGE_BLOCKED,
                        started, "encoder_output", error);
            } catch (CameraAccessException error) {
                encoderActive = false;
                discardEncoderSegment();
                return cameraAccessFailure(value, CameraPipelineOperation.START_ENCODER,
                        started, "encoder_repeating", error);
            } catch (RuntimeException error) {
                encoderActive = false;
                discardEncoderSegment();
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
        synchronized (operationLock) {
            long started = SystemClock.elapsedRealtime();
            CameraOperationResult invalid = validateBound(value,
                    CameraPipelineOperation.FINALIZE_ENCODER, started);
            if (invalid != null) return invalid;
            if (encoderActive) return result(value, CameraPipelineOperation.FINALIZE_ENCODER,
                    CameraOperationOutcome.CANDIDATE_SUSPECT, started,
                    "stop_encoder_before_finalize");
            if (encoderFinalized) return result(value, CameraPipelineOperation.FINALIZE_ENCODER,
                    CameraOperationOutcome.PASS, started, "encoder_already_finalized");
            SharedAvcEncoder.Segment segment = encoder.finish();
            if (!"segment_finalized".equals(segment.detail())) {
                retainVideoArtifactOnRelease = segment.file() != null;
                return result(value, CameraPipelineOperation.FINALIZE_ENCODER,
                        CameraOperationOutcome.GLOBAL_FAILURE, started,
                        "encoder_finalize_failed:" + segment.detail());
            }
            VideoInspection inspection = cleanStop
                    ? SharedCameraPipelineSupport.inspectCleanVideo(
                            segment.file(), segment.sampleCount(), encoder.encodedVideoResolution())
                    : SharedCameraPipelineSupport.inspectVideo(segment.file());
            CameraResolution expected = value.tuple().videoMode().resolution().actual();
            if (!inspection.valid() || !expected.equals(inspection.resolution())) {
                if (cleanStop) retainVideoArtifactOnRelease = segment.file() != null;
                else deleteQuietly(segment.file());
                return result(value, CameraPipelineOperation.FINALIZE_ENCODER,
                        CameraOperationOutcome.CANDIDATE_SUSPECT, started,
                        "invalid_video:expected=" + expected + ",actual="
                                + inspection.resolution() + ",samples=" + inspection.sampleCount()
                                + ",detail=" + inspection.detail());
            }

            encoderFinalized = true;
            finalizedDurationUs = segment.durationUs();
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

    @Override public final CameraOperationResult captureJpeg(CameraOperationContext value) {
        return captureJpeg(value, artifact(value, ".jpg"));
    }

    @Override public final CameraOperationResult captureJpeg(
            CameraOperationContext value, File outputFile) {
        synchronized (operationLock) {
            long started = SystemClock.elapsedRealtime();
            CameraOperationResult invalid = validateBound(value,
                    CameraPipelineOperation.CAPTURE_JPEG, started);
            if (invalid != null) return invalid;
            File output = Objects.requireNonNull(outputFile, "outputFile");
            long requestId = jpegRequestSequence.incrementAndGet();
            boolean encoderWasActive = encoderActive;
            PendingJpeg request = new PendingJpeg(value, output, encoderWasActive, requestId);
            pendingJpeg = request;
            try {
                CaptureRequest.Builder builder = camera.createCaptureRequest(
                        encoderWasActive
                                ? CameraDevice.TEMPLATE_VIDEO_SNAPSHOT
                                : CameraDevice.TEMPLATE_STILL_CAPTURE);
                SharedCameraPipelineSupport.applyFps(builder, configuredFpsRange);
                int jpegRotation = rotationDegrees;
                builder.set(CaptureRequest.JPEG_ORIENTATION, jpegRotation);
                builder.setTag(new CaptureTag(value, requestId, true));
                addJpegTargets(builder, encoderWasActive);
                builder.addTarget(jpegReader.getSurface());
                session.capture(builder.build(), captureCallback, cameraHandler);
                boolean complete = request.completed.await(
                        waitMillis(value, FRAME_TIMEOUT_MILLIS), TimeUnit.MILLISECONDS);
                if (complete) {
                    pendingJpeg = null;
                } else {
                    cancelPendingJpeg(request);
                }
                if (!complete) {
                    CameraOperationOutcome outcome = deadlineExpired(value)
                            ? CameraOperationOutcome.TIMEOUT_UNKNOWN
                            : CameraOperationOutcome.CANDIDATE_SUSPECT;
                    return result(value, CameraPipelineOperation.CAPTURE_JPEG, outcome, started,
                            "jpeg_callback_missing");
                }
                if (request.error != null) {
                    deleteQuietly(output);
                    return result(value, CameraPipelineOperation.CAPTURE_JPEG,
                            request.outcome, started, request.error);
                }
                CameraResolution decodedResolution = JpegDimensions.read(output)
                        .map(size -> new CameraResolution(size.width(), size.height()))
                        .orElse(null);
                capturedJpegResolution = decodedResolution;
                jpegArtifact = output;
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
    }

    @Override public final CameraOperationResult release(CameraOperationContext value) {
        synchronized (operationLock) {
            long started = SystemClock.elapsedRealtime();
            Objects.requireNonNull(value, "operationContext");
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

    @Override public final CameraPipelineDiagnostics diagnostics(CameraOperationContext value) {
        Objects.requireNonNull(value, "operationContext");
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
        String detail = "pipeline=" + pipelineId()
                + ",cameraOutputs=" + cameraOutputCount
                + "," + surfaceMetricName + "=" + downstreamSurfaceCount
                + ",jpegSurfaces=" + jpegSurfaceCount
                + ",cameraOpenCount=" + cameraOpenCount
                + ",sessionCreateCount=" + sessionCreateCount
                + ",sessionUpdate=" + sessionUpdateDetail
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
                    preRecordGopDurationMillis);
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
            logger.warn("shared_camera_capture stage=camera_characteristics"
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
            logger.info("Use camera " + value.cameraId().value() + " frame timestamps from "
                    + (sensorTimestampRealtime
                    ? "elapsed realtime clock."
                    : "camera-specific clock calibrated against elapsed realtime."));
        } catch (CameraAccessException | RuntimeException error) {
            logger.warn("shared_camera_capture stage=camera_characteristics"
                    + " outcome=unavailable cameraId=" + value.cameraId().value(), error);
        }
    }

    private void logActivePhysicalCamera(TotalCaptureResult result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || result == null) return;
        String reported = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
        String resolved = reported != null ? reported
                : "[]".equals(availablePhysicalCameraIds) ? "not_applicable" : "unreported";
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
                    + cameraId + ". Available physical cameras: " + availablePhysicalCameraIds + ".";
        }
        logger.info(message + " Pipeline: " + pipeline + ".");
    }

    private void openCamera(CameraOperationContext value)
            throws PipelineFailure, InterruptedException {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) throw new PipelineFailure(
                CameraOperationOutcome.GLOBAL_FAILURE, "camera_manager_missing");
        CountDownLatch opened = new CountDownLatch(1);
        AtomicReference<PipelineFailure> failure = new AtomicReference<>();
        CountDownLatch closeSignal = new CountDownLatch(1);
        cameraClosed = closeSignal;
        try {
            manager.openCamera(value.cameraId().value(), new CameraDevice.StateCallback() {
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
                    CameraOperationOutcome outcome = CameraPipelineFailureClassifier.classifyCameraStateError(errorCode);
                    String detail = "camera_state_error:" + errorCode;
                    recordAsyncFailure(outcome, detail, null);
                    failure.compareAndSet(null, new PipelineFailure(outcome, detail));
                    opened.countDown();
                }

                @Override public void onClosed(CameraDevice closedCamera) {
                    closeSignal.countDown();
                }
            }, cameraHandler);
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
                        jpeg.markStarted(timestamp);
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
            };
    private boolean callbackIsCurrent(CaptureRequest request) {
        Object tag = request == null ? null : request.getTag();
        if (tag instanceof CameraOperationContext) {
            return isCurrent((CameraOperationContext) tag);
        }
        return tag instanceof CaptureTag && isCurrent(((CaptureTag) tag).context);
    }

    private static CaptureTag captureTag(CaptureRequest request) {
        Object tag = request == null ? null : request.getTag();
        return tag instanceof CaptureTag ? (CaptureTag) tag : null;
    }

    private void cancelPendingJpeg(PendingJpeg request) {
        if (pendingJpeg == request) pendingJpeg = null;
        request.cancel();
        deleteQuietly(request.file);
    }

    private void onJpegAvailable(ImageReader reader) {
        PendingJpeg request = pendingJpeg;
        boolean writing = false;
        try (Image image = reader.acquireNextImage()) {
            if (image == null) return;
            if (request == null || !isCurrent(request.context)
                    || !request.accepts(image.getTimestamp())) return;
            writing = request.tryBeginWrite();
            if (!writing) return;
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            try (FileOutputStream output = new FileOutputStream(request.file)) {
                output.write(bytes);
                output.getFD().sync();
            }
            if (!request.isCancelled()) {
                request.complete(new CameraResolution(image.getWidth(), image.getHeight()));
            }
        } catch (IOException error) {
            if (request != null) request.fail(CameraOperationOutcome.BLOCKED_EXTERNAL,
                    "jpeg_write:" + error.getClass().getSimpleName());
            logger.warn(activeContext == null
                    ? "pipeline=b-camera2-egl-fanout-v1 stage=jpeg_write"
                    : prefix(activeContext, "jpeg_write"), error);
        } catch (RuntimeException error) {
            if (request != null) request.fail(CameraOperationOutcome.GLOBAL_FAILURE,
                    "jpeg_reader:" + error.getClass().getSimpleName());
            logger.warn(activeContext == null
                    ? "pipeline=b-camera2-egl-fanout-v1 stage=jpeg_reader"
                    : prefix(activeContext, "jpeg_reader"), error);
        } finally {
            if (writing) request.finishWrite();
            if (request != null && request.isCancelled()) deleteQuietly(request.file);
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
        Objects.requireNonNull(value, "operationContext");
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
                ? "pipeline=" + pipelineId().value() + " stage=asynchronous"
                        + " outcome=" + outcome + " detail=" + detail
                : prefix(activeContext, "asynchronous")
                        + " outcome=" + outcome + " detail=" + detail;
        if (error == null) logger.info(message); else logger.warn(message, error);
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
        logger.warn(prefix(value, stage) + " outcome=" + failure.outcome
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
        logger.warn(prefix(value, stage) + " outcome=" + outcome
                + " elapsedMs=" + elapsed(started) + " detail=" + detail, error);
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
        logger.info(prefix(value, stage) + " outcome=" + outcome + " detail=" + detail);
    }

    protected final String prefix(CameraOperationContext value, String stage) {
        return "pipeline=" + pipelineId() + " cameraId=" + value.cameraId()
                + operationContextDetail(value) + " codec=" + value.codec().id()
                + " sessionGeneration=" + value.sessionGeneration()
                + " cameraHealthGeneration=" + value.cameraHealthGeneration()
                + " stage=" + stage;
    }

    private boolean releaseResources(boolean enforceTimeout) {
        long started = SystemClock.elapsedRealtime();
        long deadline = started + RELEASE_TIMEOUT_MILLIS;
        activeContext = null;
        CountDownLatch closeSignal = cameraClosed;
        sessionBound = false;
        previewProgressing = false;
        encoderActive = false;
        encoderInputActive = false;
        PendingJpeg jpeg = pendingJpeg;
        if (jpeg != null) cancelPendingJpeg(jpeg);
        boolean released = true;
        CameraCaptureSession currentSession = session;
        if (currentSession != null) {
            try { currentSession.stopRepeating(); } catch (Exception ignored) {}
            try { currentSession.abortCaptures(); } catch (Exception ignored) {}
            try { currentSession.close(); session = null; }
            catch (RuntimeException error) { released = false; }
        }
        CameraDevice currentCamera = camera;
        if (currentCamera != null) {
            try { currentCamera.close(); camera = null; }
            catch (RuntimeException error) { released = false; }
        }
        if (enforceTimeout && closeSignal.getCount() > 0) {
            try {
                released &= closeSignal.await(
                        remainingReleaseMillis(deadline), TimeUnit.MILLISECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                released = false;
            }
        }
        if (jpegReader != null) {
            try { jpegReader.close(); jpegReader = null; }
            catch (RuntimeException error) { released = false; }
        }
        released &= releaseTopology(deadline);
        if (encoder != null) {
            boolean encoderReleased;
            try { encoderReleased = encoder.closeAndAwait(remainingReleaseMillis(deadline)); }
            catch (RuntimeException error) { encoderReleased = false; }
            released &= encoderReleased;
            if (encoderReleased) encoder = null;
        }
        boolean videoRetained = retainVideoArtifactOnRelease && videoArtifact != null;
        boolean videoDeleted = videoRetained || deleteArtifact(videoArtifact);
        boolean jpegDeleted = deleteArtifact(jpegArtifact);
        if (videoRetained) {
            logger.info("Preserve failed recording staging artifact for recovery: "
                    + videoArtifact.getAbsolutePath() + ".");
            videoArtifact = null;
            retainVideoArtifactOnRelease = false;
        } else if (videoDeleted) {
            videoArtifact = null;
        }
        if (jpegDeleted) jpegArtifact = null;
        if (!videoDeleted || !jpegDeleted) {
            logger.info("pipeline=" + pipelineId()
                    + " stage=cleanup outcome=artifact_retry"
                    + " videoDeleted=" + videoDeleted + " jpegDeleted=" + jpegDeleted);
        }
        if (cameraThread != null) {
            HandlerThread closingThread = cameraThread;
            closingThread.quitSafely();
            boolean joined = SharedCameraPipelineSupport.joinThread(
                    closingThread, remainingReleaseMillis(deadline));
            released &= joined;
            if (joined) {
                cameraThread = null;
                cameraHandler = null;
            }
        }
        asynchronousOutcome = null;
        asynchronousDetail = null;
        if (!released) {
            logger.info("pipeline=" + pipelineId()
                    + " stage=cleanup outcome=release_timeout elapsedMs=" + elapsed(started));
        }
        return released;
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
        sessionUpdateDetail = "not_requested";
        availablePhysicalCameraIds = "unresolved";
        activePhysicalCameraId = "unresolved";
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
            logger.info(prefix(value, "fps_measurement") + " outcome=warning"
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

    protected String updateTopologySession(
            CameraOperationContext value, long started) throws CameraAccessException {
        return "downstream_encoder_attach_detach_supported";
    }

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

    private static final class PendingJpeg {
        private final CameraOperationContext context;
        private final File file;
        private final boolean encoderWasActive;
        private final long requestId;
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile long expectedTimestamp = Long.MIN_VALUE;
        private volatile CameraResolution resolution;
        private volatile CameraOperationOutcome outcome = CameraOperationOutcome.PASS;
        private volatile String error;

        private PendingJpeg(CameraOperationContext context,
                File file, boolean encoderWasActive, long requestId) {
            this.context = context;
            this.file = file;
            this.encoderWasActive = encoderWasActive;
            this.requestId = requestId;
        }

        private boolean cancelled;
        private boolean writing;

        private synchronized void markStarted(long timestamp) {
            expectedTimestamp = timestamp;
        }

        private synchronized boolean accepts(long timestamp) {
            return !cancelled
                    && expectedTimestamp != Long.MIN_VALUE && expectedTimestamp == timestamp;
        }

        private synchronized boolean tryBeginWrite() {
            if (cancelled || writing) return false;
            writing = true;
            return true;
        }

        private synchronized void finishWrite() {
            writing = false;
        }

        private synchronized boolean isCancelled() {
            return cancelled;
        }

        private synchronized void cancel() {
            cancelled = true;
            outcome = CameraOperationOutcome.CANCELLED_UNKNOWN;
            error = "jpeg_cancelled";
            completed.countDown();
        }

        private synchronized void complete(CameraResolution value) {
            if (cancelled) return;
            resolution = value;
            completed.countDown();
        }

        private synchronized void fail(CameraOperationOutcome value, String detail) {
            if (cancelled) return;
            outcome = value;
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
