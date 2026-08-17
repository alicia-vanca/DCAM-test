package com.dvid.dcam.platform.camera.shared.egl;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.SystemClock;
import android.view.Surface;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.platform.camera.shared.AbstractSharedCameraPipeline;
import com.dvid.dcam.platform.camera.shared.CameraPipelineFailureClassifier;
import com.dvid.dcam.platform.camera.shared.CameraPipelineIds;
import com.dvid.dcam.platform.camera.shared.SharedCameraPipelineSupport;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativePreviewFrameSignal;
import java.io.File;
import java.util.function.Supplier;

public final class EglFanOutPipeline extends AbstractSharedCameraPipeline {
    private final NativePreviewFrameSignal externalPreviewFrameSignal;
    private ImageReader standalonePreviewReader;
    private EglFrameFanOut fanOut;
    private Surface sourceSurface;
    private long firstSourceFrameAtNanos = -1;
    private long firstPreviewFrameAtNanos = -1;
    private long firstEncodedSampleAtNanos = -1;

    EglFanOutPipeline(Context context, Logger logger,
            File outputDirectory, Surface externalPreviewSurface,
            NativePreviewFrameSignal externalPreviewFrameSignal, boolean recycleEncoder,
            int rotationDegrees, long preRecordGopDurationMillis,
            Supplier<GpsCoordinate> captureLocation) {
        super(context, logger, outputDirectory, externalPreviewSurface, recycleEncoder,
                rotationDegrees, preRecordGopDurationMillis, captureLocation,
                CameraPipelineIds.EGL_FAN_OUT, "dcam-egl-camera",
                "dcam-egl-avc", "downstreamSurfaces");
        this.externalPreviewFrameSignal = externalPreviewFrameSignal;
        if ((externalPreviewSurface == null) != (externalPreviewFrameSignal == null)) {
            throw new IllegalArgumentException(
                    "external preview requires a frame-progress signal");
        }
    }

    int cameraOpenCount() { return cameraOpenCountValue(); }

    int sessionCreateCount() { return sessionCreateCountValue(); }

    boolean encoderTargetEnabled() {
        EglFrameFanOut current = fanOut;
        return current != null && current.encoderTargetEnabled();
    }

    @Override protected void prepareTopology(CameraOperationContext value,
            boolean standaloneImage, int width, int height, int framesPerSecond)
            throws PipelineFailure, InterruptedException {
        configureFps(value);
        if (standaloneImage) {
            standalonePreviewReader = ImageReader.newInstance(
                    width, height, ImageFormat.PRIVATE, 4);
            CameraOperationContext boundContext = value;
            standalonePreviewReader.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    if (image == null || !isCurrent(boundContext)) return;
                    long timestampNanos = image.getTimestamp();
                    if (firstSourceFrameAtNanos < 0) {
                        firstSourceFrameAtNanos = SystemClock.elapsedRealtimeNanos();
                    }
                    if (firstPreviewFrameAtNanos < 0) {
                        firstPreviewFrameAtNanos = SystemClock.elapsedRealtimeNanos();
                    }
                    signalSourceFrame(timestampNanos);
                    signalPreviewFrame();
                } catch (RuntimeException error) {
                    recordAsyncFailure(CameraOperationOutcome.GLOBAL_FAILURE,
                            "standalone_preview_reader:"
                                    + error.getClass().getSimpleName(), error);
                }
            }, cameraHandler());
            sourceSurface = standalonePreviewReader.getSurface();
            return;
        }
        openEncoder(width, height, framesPerSecond);
        if (externalPreviewFrameSignal != null) {
            CameraOperationContext boundContext = value;
            externalPreviewFrameSignal.start(cameraHandler(), () -> {
                if (!isCurrent(boundContext)) return;
                if (firstPreviewFrameAtNanos < 0) {
                    firstPreviewFrameAtNanos = SystemClock.elapsedRealtimeNanos();
                }
                signalPreviewFrame();
            });
        }
        try {
            fanOut = EglFrameFanOut.start(width, height, externalPreviewSurface(),
                    encoder().inputSurface(), logger(), new EglFrameFanOut.Listener() {
                        @Override public void onSourceFrame(long timestampNanos) {
                            if (!isCurrent(value)) return;
                            if (firstSourceFrameAtNanos < 0) {
                                firstSourceFrameAtNanos = SystemClock.elapsedRealtimeNanos();
                            }
                            signalSourceFrame(timestampNanos);
                        }

                        @Override public void onPreviewFrame(long timestampNanos) {
                            if (!isCurrent(value)) return;
                            if (externalPreviewFrameSignal != null) return;
                            if (firstPreviewFrameAtNanos < 0) {
                                firstPreviewFrameAtNanos = SystemClock.elapsedRealtimeNanos();
                            }
                            signalPreviewFrame();
                        }

                        @Override public void onPreviewDrop(long totalDrops, String detail) {
                            if (!isCurrent(value)) return;
                            setPreviewDropCount(totalDrops);
                            logger().info(prefix(value, "preview_backpressure")
                                    + " outcome=drop droppedFrames=" + totalDrops
                                    + " detail=" + detail);
                        }

                        @Override public void onEglError(
                                CameraPipelineFailureClassifier.Signal signal,
                                String detail, Throwable error) {
                            if (!isCurrent(value)) return;
                            recordAsyncFailure(CameraPipelineFailureClassifier.classify(signal),
                                    detail, error);
                        }
                    });
        } catch (EglFrameFanOut.EglException error) {
            throw topologyFailure(
                    CameraPipelineFailureClassifier.classify(error.signal()),
                    error.getMessage());
        }
        sourceSurface = fanOut.sourceSurface();
    }

    @Override protected void finishTopologyPreparation(
            CameraOperationContext value, boolean standaloneImage) {
        log(value, "egl_setup", "pass",
                "standaloneImage=" + standaloneImage
                        + ",configuredFpsRange=" + configuredFpsRange()
                        + (fanOut == null ? "" : "," + fanOut.metrics()));
    }

    @Override protected OutputConfiguration cameraOutputConfiguration() {
        return new OutputConfiguration(sourceSurface);
    }

    @Override protected int topologyDownstreamSurfaceCount() {
        return standaloneImageSession() ? 1 : fanOut.downstreamSurfaceCount();
    }

    @Override protected int repeatingTemplate(boolean includeEncoder) {
        return standaloneImageSession()
                ? CameraDevice.TEMPLATE_PREVIEW : CameraDevice.TEMPLATE_RECORD;
    }

    @Override protected void addRepeatingTargets(
            CaptureRequest.Builder builder, boolean includeEncoder) {
        builder.addTarget(sourceSurface);
    }

    @Override protected void addJpegTargets(
            CaptureRequest.Builder builder, boolean encoderWasActive) {
        builder.addTarget(sourceSurface);
    }

    @Override protected void startTopologyRecording()
            throws PipelineFailure, InterruptedException {
        try {
            fanOut.attachEncoderSurface();
        } catch (EglFrameFanOut.EglException error) {
            throw topologyFailure(
                    CameraPipelineFailureClassifier.classify(error.signal()),
                    error.getMessage());
        }
    }


    @Override protected String encoderStartDetail() {
        firstEncodedSampleAtNanos = SystemClock.elapsedRealtimeNanos();
        return "encoder_started_without_camera_rebind:cameraOpenCount="
                + cameraOpenCountValue() + ",sessionCreateCount=" + sessionCreateCountValue();
    }

    @Override protected String topologyDiagnostics() {
        return ",firstSourceFrameAtNanos=" + firstSourceFrameAtNanos
                + ",firstPreviewFrameAtNanos=" + firstPreviewFrameAtNanos
                + ",firstEncodedSampleAtNanos=" + firstEncodedSampleAtNanos
                + "," + (fanOut == null ? "egl=inactive" : fanOut.metrics())
                + "," + resourceMetrics();
    }

    @Override protected boolean releaseTopology(long deadlineMillis) {
        boolean released = true;
        if (externalPreviewFrameSignal != null) {
            try { externalPreviewFrameSignal.stop(); }
            catch (RuntimeException error) { released = false; }
        }
        if (standalonePreviewReader != null) {
            try { standalonePreviewReader.close(); standalonePreviewReader = null; }
            catch (RuntimeException error) { released = false; }
        }
        if (fanOut != null) {
            boolean fanOutReleased;
            try {
                fanOutReleased = fanOut.closeAndAwait(
                        SharedCameraPipelineSupport.remainingReleaseMillis(deadlineMillis));
            } catch (RuntimeException error) {
                fanOutReleased = false;
            }
            released &= fanOutReleased;
            if (fanOutReleased) {
                fanOut = null;
                sourceSurface = null;
            }
        } else {
            sourceSurface = null;
        }
        return released;
    }

    @Override protected void resetTopologyEvidence() {
        firstSourceFrameAtNanos = -1;
        firstPreviewFrameAtNanos = -1;
        firstEncodedSampleAtNanos = -1;
    }

    private String resourceMetrics() {
        long processCpuMillis = android.os.Process.getElapsedCpuTime();
        long nativeHeapBytes = android.os.Debug.getNativeHeapAllocatedSize();
        String thermalStatus = "unavailable";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.os.PowerManager powerManager = (android.os.PowerManager)
                    context().getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                thermalStatus = Integer.toString(powerManager.getCurrentThermalStatus());
            }
        }
        return "processCpuMs=" + processCpuMillis
                + ",nativeHeapBytes=" + nativeHeapBytes
                + ",thermalStatus=" + thermalStatus;
    }
}