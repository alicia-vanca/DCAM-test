package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraHealthGenerationProvider;
import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.camera.CameraFailureClass;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import java.util.Objects;
import java.util.Optional;

public final class VerifyStandaloneImageUseCase {
    private final CameraRuntimeOperations runtime;
    private final Logger logger;
    private final CameraVerificationClock clock;
    private final CameraHealthGenerationProvider healthGenerationProvider;

    public VerifyStandaloneImageUseCase(CameraRuntimeOperations runtime, Logger logger,
            CameraVerificationClock clock,
            CameraHealthGenerationProvider healthGenerationProvider) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.healthGenerationProvider = Objects.requireNonNull(
                healthGenerationProvider, "healthGenerationProvider");
    }

    public synchronized Result execute(Request request) {
        Objects.requireNonNull(request, "request");
        VerificationOutcome existing = CameraCapabilitySnapshotUpdates.pipeline(
                request.snapshot(), request.imageCandidate().cameraId(),
                request.imageCandidate().codec(),
                request.imageCandidate().verificationPipelineId())
                .outcome(request.imageCandidate());
        if (existing == VerificationOutcome.VERIFIED_PASS
                || existing == VerificationOutcome.DEFINITIVE_UNSUPPORTED) {
            return new Result(existing, request.snapshot(), true,
                    "standalone_image_evidence_reused");
        }

        CameraOperationDeadline deadline = CameraOperationDeadline.forCandidate(
                clock.elapsedRealtimeMillis());
        Attempt first = perform(request, deadline, 1);
        if (first.outcome() == VerificationOutcome.VERIFIED_PASS) {
            return terminal(request, first, VerificationOutcome.VERIFIED_PASS,
                    "standalone_image_pass");
        }
        if (!first.cleanupComplete() || first.failureSignature().isEmpty()) {
            return nonTerminal(request, first);
        }
        if (!healthMatches(request) || deadline.isExpiredAt(clock.elapsedRealtimeMillis())) {
            return new Result(VerificationOutcome.CANCELLED_UNKNOWN, request.snapshot(), true,
                    "standalone_image_confirmation_cancelled");
        }

        Attempt confirmation = perform(request, deadline, 2);
        if (confirmation.outcome() == VerificationOutcome.VERIFIED_PASS) {
            return terminal(request, confirmation, VerificationOutcome.VERIFIED_PASS,
                    "standalone_image_confirmation_pass");
        }
        if (confirmation.cleanupComplete()
                && confirmation.failureSignature().isPresent()
                && first.failureSignature().equals(confirmation.failureSignature())) {
            return terminal(request, confirmation,
                    VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                    "standalone_image_confirmed_unsupported");
        }
        return nonTerminal(request, confirmation);
    }

    private Result terminal(Request request, Attempt attempt,
            VerificationOutcome outcome, String detail) {
        Snapshot updated = CameraCapabilitySnapshotUpdates.withStandaloneImageEvidence(
                request.snapshot(), request.imageCandidate(), outcome);
        logger.info(LogCategory.CAPABILITY, "unspecified", logPrefix(request, outcome, detail));
        return new Result(outcome, updated, attempt.cleanupComplete(), detail);
    }

    private Result nonTerminal(Request request, Attempt attempt) {
        logger.info(LogCategory.CAPABILITY, "unspecified", logPrefix(request, attempt.outcome(), attempt.detail()));
        return new Result(attempt.outcome(), request.snapshot(),
                attempt.cleanupComplete(), attempt.detail());
    }

    private Attempt perform(Request request, CameraOperationDeadline deadline,
            int attemptNumber) {
        CameraOperationContext context = new CameraOperationContext(
                request.imageCandidate().cameraId(),
                request.imageCandidate().verificationPipelineId(),
                request.imageCandidate().codec(), request.bindingTuple(),
                request.firstSessionGeneration() + attemptNumber - 1,
                request.cameraHealthGeneration(), deadline);
        if (!healthMatches(request)) {
            return Attempt.nonTerminal(VerificationOutcome.CANCELLED_UNKNOWN, true,
                    "camera_health_generation_changed");
        }

        Step bind = call(request, context, CameraPipelineOperation.BIND_SESSION,
                runtime::bindStandaloneImageSession, deadline);
        if (!bind.pass()) return finish(context, bind);

        Step bound = diagnostics(request, context, deadline);
        if (!bound.pass()) return finish(context, bound);
        CameraPipelineDiagnostics boundValue = bound.diagnostics().orElseThrow();
        if (!boundValue.sessionBound() || boundValue.encoderActive()
                || boundValue.cameraOutputCount() != 2
                || boundValue.downstreamSurfaceCount() != 1) {
            return finish(context, Step.candidate(
                    new FailureSignature(CameraPipelineOperation.BIND_SESSION,
                            CameraFailureClass.TOPOLOGY),
                    "invalid_standalone_image_topology"));
        }

        Step capture = call(request, context, CameraPipelineOperation.CAPTURE_JPEG,
                runtime::captureJpeg, deadline);
        if (!capture.pass()) return finish(context, capture);

        Step checked = diagnostics(request, context, deadline);
        if (!checked.pass()) return finish(context, checked);
        CameraPipelineDiagnostics value = checked.diagnostics().orElseThrow();
        var expected = request.imageCandidate().imageMode().orElseThrow()
                .resolution().actual();
        int sensorOrientationDegrees =
                CameraCapabilitySnapshotUpdates.sensorOrientationDegrees(
                        request.snapshot(), request.imageCandidate().cameraId());
        boolean valid = value.sessionBound() && !value.encoderActive()
                && !value.jpegCapturedWhileEncoderActive()
                && value.capturedJpegResolution()
                .filter(actual -> expected.matchesConsideringRotation(
                        actual, sensorOrientationDegrees))
                .isPresent()
                && value.capturedJpegArtifact().isPresent();
        if (!valid) {
            return finish(context, Step.candidate(
                    new FailureSignature(CameraPipelineOperation.CAPTURE_JPEG,
                            CameraFailureClass.JPEG_OUTPUT),
                    "standalone_image_output_validation_failed"));
        }
        return finish(context, Step.pass("standalone_image_capture_pass"));
    }

    private Attempt finish(CameraOperationContext context, Step step) {
        CameraOperationResult release;
        try {
            release = Objects.requireNonNull(runtime.release(context), "release result");
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, "camera_standalone_image release_exception", error);
            return Attempt.nonTerminal(VerificationOutcome.TRANSIENT_RETRYABLE, false,
                    "standalone_image_release_exception");
        }
        CameraOperationOutcome releaseOutcome = normalize(
                release, context, CameraPipelineOperation.RELEASE,
                context.deadline());
        if (releaseOutcome != CameraOperationOutcome.PASS) {
            return Attempt.nonTerminal(map(releaseOutcome), false,
                    "standalone_image_release=" + release.detail());
        }
        if (step.pass()) {
            return new Attempt(VerificationOutcome.VERIFIED_PASS,
                    Optional.empty(), true, step.detail());
        }
        if (step.failureSignature().isPresent()) {
            return new Attempt(VerificationOutcome.UNKNOWN,
                    step.failureSignature(), true, step.detail());
        }
        return Attempt.nonTerminal(step.outcome(), true, step.detail());
    }

    private Step call(Request request, CameraOperationContext context,
            CameraPipelineOperation operation, OperationCall operationCall,
            CameraOperationDeadline deadline) {
        if (!healthMatches(request)) {
            return Step.nonTerminal(VerificationOutcome.CANCELLED_UNKNOWN,
                    "camera_health_generation_changed");
        }
        if (deadline.isExpiredAt(clock.elapsedRealtimeMillis())) {
            return Step.nonTerminal(VerificationOutcome.TIMEOUT_UNKNOWN,
                    "deadline_before_" + operation.name().toLowerCase());
        }
        CameraOperationResult result;
        try {
            result = Objects.requireNonNull(operationCall.invoke(context), "runtime result");
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, "camera_standalone_image runtime_exception operation=" + operation,
                    error);
            return Step.nonTerminal(VerificationOutcome.GLOBAL_FAILURE,
                    "runtime_exception=" + error.getClass().getSimpleName());
        }
        CameraOperationOutcome outcome = normalize(result, context, operation, deadline);
        if (outcome == CameraOperationOutcome.PASS) return Step.pass(result.detail());
        if (outcome.isCandidateFailure()) {
            return Step.candidate(new FailureSignature(operation, result.failureClass()),
                    result.detail());
        }
        return Step.nonTerminal(map(outcome), result.detail());
    }

    private Step diagnostics(Request request, CameraOperationContext context,
            CameraOperationDeadline deadline) {
        CameraPipelineDiagnostics value;
        try {
            value = Objects.requireNonNull(runtime.diagnostics(context), "diagnostics");
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, "camera_standalone_image diagnostics_exception", error);
            return Step.nonTerminal(VerificationOutcome.GLOBAL_FAILURE,
                    "diagnostics_exception=" + error.getClass().getSimpleName());
        }
        if (!context.matchesCurrentOperation(value.context()) || !healthMatches(request)) {
            return Step.nonTerminal(VerificationOutcome.CANCELLED_UNKNOWN,
                    "standalone_image_diagnostics_stale");
        }
        if (deadline.isExpiredAt(clock.elapsedRealtimeMillis())) {
            return Step.nonTerminal(VerificationOutcome.TIMEOUT_UNKNOWN,
                    "standalone_image_diagnostics_timeout");
        }
        return Step.pass(value);
    }

    private CameraOperationOutcome normalize(CameraOperationResult result,
            CameraOperationContext context, CameraPipelineOperation operation,
            CameraOperationDeadline deadline) {
        if (result.operation() != operation) return CameraOperationOutcome.GLOBAL_FAILURE;
        if (deadline.isExpiredAt(clock.elapsedRealtimeMillis())) {
            return CameraOperationOutcome.TIMEOUT_UNKNOWN;
        }
        return result.effectiveOutcomeAgainst(context, clock.elapsedRealtimeMillis());
    }

    private boolean healthMatches(Request request) {
        return healthGenerationProvider.currentGeneration(
                request.imageCandidate().cameraId()) == request.cameraHealthGeneration();
    }

    private static VerificationOutcome map(CameraOperationOutcome outcome) {
        return switch (outcome) {
            case PASS -> VerificationOutcome.VERIFIED_PASS;
            case TIMEOUT_UNKNOWN -> VerificationOutcome.TIMEOUT_UNKNOWN;
            case TRANSIENT_RETRYABLE -> VerificationOutcome.TRANSIENT_RETRYABLE;
            case GLOBAL_FAILURE -> VerificationOutcome.GLOBAL_FAILURE;
            case BLOCKED_EXTERNAL -> VerificationOutcome.BLOCKED_EXTERNAL;
            case CANCELLED_UNKNOWN, STALE -> VerificationOutcome.CANCELLED_UNKNOWN;
            case CANDIDATE_SUSPECT, DEFINITIVE_CANDIDATE_FAILURE ->
                    VerificationOutcome.UNKNOWN;
        };
    }

    private static String logPrefix(Request request, VerificationOutcome outcome,
            String detail) {
        return "camera_standalone_image camera=" + request.imageCandidate().cameraId()
                + " pipeline=" + request.imageCandidate().verificationPipelineId()
                + " image=" + request.imageCandidate().imageMode().orElseThrow()
                + " outcome=" + outcome + " detail=" + detail;
    }

    public record Request(Snapshot snapshot, CandidateKey imageCandidate,
            CaptureModeTuple bindingTuple, long firstSessionGeneration,
            long cameraHealthGeneration) {
        public Request {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            imageCandidate = Objects.requireNonNull(imageCandidate, "imageCandidate");
            bindingTuple = Objects.requireNonNull(bindingTuple, "bindingTuple");
            if (imageCandidate.kind() != CandidateKey.Kind.IMAGE) {
                throw new IllegalArgumentException("standalone candidate must be an image");
            }
            if (!imageCandidate.imageMode().orElseThrow().equals(bindingTuple.imageMode())) {
                throw new IllegalArgumentException("binding tuple image differs");
            }
            if (firstSessionGeneration <= 0 || cameraHealthGeneration < 0) {
                throw new IllegalArgumentException("invalid verification generation");
            }
            if (!CameraCapabilitySnapshotUpdates.pipeline(snapshot,
                    imageCandidate.cameraId(), imageCandidate.codec(),
                    imageCandidate.verificationPipelineId()).hasRawScope(imageCandidate)) {
                throw new IllegalArgumentException("image candidate outside snapshot scope");
            }
        }
    }

    public record Result(VerificationOutcome outcome, Snapshot snapshot,
            boolean cleanupComplete, String detail) {
        public Result {
            outcome = Objects.requireNonNull(outcome, "outcome");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }
    }

    private interface OperationCall {
        CameraOperationResult invoke(CameraOperationContext context);
    }

    private record FailureSignature(CameraPipelineOperation operation,
            CameraFailureClass failureClass) {}

    private record Attempt(VerificationOutcome outcome,
            Optional<FailureSignature> failureSignature,
            boolean cleanupComplete, String detail) {
        private Attempt {
            outcome = Objects.requireNonNull(outcome, "outcome");
            failureSignature = Objects.requireNonNull(
                    failureSignature, "failureSignature");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }

        private static Attempt nonTerminal(VerificationOutcome outcome,
                boolean cleanupComplete, String detail) {
            return new Attempt(outcome, Optional.empty(), cleanupComplete, detail);
        }
    }

    private record Step(boolean pass, VerificationOutcome outcome,
            Optional<FailureSignature> failureSignature,
            Optional<CameraPipelineDiagnostics> diagnostics, String detail) {
        private Step {
            outcome = Objects.requireNonNull(outcome, "outcome");
            failureSignature = Objects.requireNonNull(
                    failureSignature, "failureSignature");
            diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }

        private static Step pass(String detail) {
            return new Step(true, VerificationOutcome.VERIFIED_PASS,
                    Optional.empty(), Optional.empty(), detail);
        }

        private static Step pass(CameraPipelineDiagnostics diagnostics) {
            return new Step(true, VerificationOutcome.VERIFIED_PASS,
                    Optional.empty(), Optional.of(diagnostics), diagnostics.detail());
        }

        private static Step candidate(FailureSignature signature, String detail) {
            return new Step(false, VerificationOutcome.UNKNOWN,
                    Optional.of(signature), Optional.empty(), detail);
        }

        private static Step nonTerminal(VerificationOutcome outcome, String detail) {
            return new Step(false, outcome, Optional.empty(), Optional.empty(), detail);
        }
    }
}