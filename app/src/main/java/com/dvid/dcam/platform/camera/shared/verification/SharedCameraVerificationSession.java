package com.dvid.dcam.platform.camera.shared.verification;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.platform.camera.shared.SharedCameraCapturePipeline;
import com.dvid.dcam.platform.camera.shared.SharedCameraPipelineProvider;
import com.dvid.dcam.platform.camera.shared.runtime.CameraRuntimeSelection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SharedCameraVerificationSession implements CameraRuntimeOperations {
    private final Function<CameraRuntimeSelection, SharedCameraCapturePipeline> pipelineFactory;
    private final Consumer<SharedCameraCapturePipeline> pipelineObserver;
    private final Logger logger;
    private final CameraVerificationClock clock;
    private SharedCameraCapturePipeline pipeline;
    private SharedCameraVerificationRuntime runtime;
    private CameraOperationContext activeContext;
    private BindingMode activeMode;

    private enum BindingMode { COMBO, STANDALONE_IMAGE }

    public SharedCameraVerificationSession(SharedCameraPipelineProvider pipelines,
            Logger logger, CameraVerificationClock clock) {
        this(Objects.requireNonNull(pipelines, "pipelines")::createVerification,
                logger, clock, ignored -> {});
    }

    public SharedCameraVerificationSession(
            Function<CameraRuntimeSelection, SharedCameraCapturePipeline> pipelineFactory,
            Logger logger, CameraVerificationClock clock,
            Consumer<SharedCameraCapturePipeline> pipelineObserver) {
        this.pipelineFactory = Objects.requireNonNull(pipelineFactory, "pipelineFactory");
        this.pipelineObserver = Objects.requireNonNull(pipelineObserver, "pipelineObserver");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<SharedCameraCapturePipeline> activePipeline() {
        return Optional.ofNullable(pipeline);
    }

    public Optional<CameraOperationContext> activeContext() {
        return Optional.ofNullable(activeContext);
    }

    @Override public CameraOperationResult bindSession(CameraOperationContext context) {
        CameraOperationResult releaseFailure = releaseDifferentBinding(
                context, BindingMode.COMBO);
        if (releaseFailure != null) return bindBlockedByRelease(context, releaseFailure);
        CameraRuntimeSelection selection = new CameraRuntimeSelection(context.cameraId(),
                context.verificationPipelineId(), context.codec(), context.tuple());
        installPipeline(selection);
        CameraOperationResult result = runtime.bindSession(context);
        if (result.outcome() == CameraOperationOutcome.PASS) {
            activeContext = context;
            activeMode = BindingMode.COMBO;
        } else clear();
        return result;
    }

    @Override public CameraOperationResult bindStandaloneImageSession(
            CameraOperationContext context) {
        CameraOperationResult releaseFailure = releaseDifferentBinding(
                context, BindingMode.STANDALONE_IMAGE);
        if (releaseFailure != null) return bindBlockedByRelease(context, releaseFailure);
        CameraRuntimeSelection selection = new CameraRuntimeSelection(context.cameraId(),
                context.verificationPipelineId(), context.codec(), context.tuple());
        installPipeline(selection);
        CameraOperationResult result = runtime.bindStandaloneImageSession(context);
        if (result.outcome() == CameraOperationOutcome.PASS) {
            activeContext = context;
            activeMode = BindingMode.STANDALONE_IMAGE;
        } else clear();
        return result;
    }

    @Override public CameraOperationResult previewProgress(CameraOperationContext context) {
        return runtime().previewProgress(context);
    }

    @Override public CameraOperationResult startEncoder(CameraOperationContext context) {
        return runtime().startEncoder(context);
    }

    @Override public CameraOperationResult stopEncoder(CameraOperationContext context) {
        return runtime().stopEncoder(context);
    }

    @Override public CameraOperationResult finalizeEncoder(CameraOperationContext context) {
        return runtime().finalizeEncoder(context);
    }

    @Override public CameraOperationResult captureJpeg(CameraOperationContext context) {
        return runtime().captureJpeg(context);
    }

    @Override public CameraOperationResult release(CameraOperationContext context) {
        CameraOperationResult result = runtime().release(context);
        if (result.outcome() == CameraOperationOutcome.PASS) clear();
        return result;
    }

    @Override public CameraPipelineDiagnostics diagnostics(CameraOperationContext context) {
        return runtime().diagnostics(context);
    }

    public SharedCameraCapturePipeline detachActiveBinding(CameraOperationContext context) {
        Objects.requireNonNull(context, "context");
        if (activeMode != BindingMode.COMBO || activeContext == null || pipeline == null
                || !context.matchesCurrentOperation(activeContext)) {
            throw new IllegalStateException("verification session has no matching combo binding");
        }
        SharedCameraCapturePipeline retained = pipeline;
        clear(false);
        return retained;
    }

    private void installPipeline(CameraRuntimeSelection selection) {
        pipeline = Objects.requireNonNull(pipelineFactory.apply(selection), "pipeline");
        pipelineObserver.accept(pipeline);
        runtime = new SharedCameraVerificationRuntime(pipeline, logger, clock);
    }

    private CameraOperationResult releaseDifferentBinding(
            CameraOperationContext context, BindingMode requestedMode) {
        if (activeContext == null || runtime == null) return null;
        if (activeMode == requestedMode && activeContext.matchesCurrentOperation(context)) {
            return null;
        }
        CameraOperationResult release = runtime.release(activeContext);
        if (release.outcome() != CameraOperationOutcome.PASS) return release;
        clear();
        return null;
    }

    private static CameraOperationResult bindBlockedByRelease(
            CameraOperationContext context, CameraOperationResult release) {
        return new CameraOperationResult(context,
                com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation.BIND_SESSION,
                release.outcome(), release.failureClass(), release.elapsedMillis(),
                "previous_binding_release_failed:" + release.detail());
    }

    private SharedCameraVerificationRuntime runtime() {
        if (runtime == null) throw new IllegalStateException("verification session not bound");
        return runtime;
    }

    private void clear() {
        clear(true);
    }

    private void clear(boolean notifyObserver) {
        pipeline = null;
        runtime = null;
        activeContext = null;
        activeMode = null;
        if (notifyObserver) pipelineObserver.accept(null);
    }
}
