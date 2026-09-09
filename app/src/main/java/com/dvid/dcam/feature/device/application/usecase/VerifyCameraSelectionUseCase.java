package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraHealthGenerationProvider;
import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.domain.camera.CameraFailureClass;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.RequestedCeilingFallback;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerifiedCameraBinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class VerifyCameraSelectionUseCase {
    private static final String HEALTH_GENERATION_CHANGED = "camera_health_generation_changed";
    private static final String GLOBAL_FAILURE = "GLOBAL_FAILURE";

    private final CameraRuntimeOperations runtime;
    private final CameraCapabilityStore capabilityStore;
    private final Logger logger;
    private final CameraVerificationClock clock;
    private final CameraHealthGenerationProvider healthGenerationProvider;

    public VerifyCameraSelectionUseCase(CameraRuntimeOperations runtime,
            CameraCapabilityStore capabilityStore, Logger logger,
            CameraVerificationClock clock,
            CameraHealthGenerationProvider healthGenerationProvider) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.capabilityStore = Objects.requireNonNull(capabilityStore, "capabilityStore");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.healthGenerationProvider = Objects.requireNonNull(
                healthGenerationProvider, "healthGenerationProvider");
    }

    public synchronized Result execute(Request request) {
        Objects.requireNonNull(request, "request");
        RunState run = new RunState(request);
        CandidateKey requested = request.requestedCandidate();
        PipelineEvidence initial = evidence(run.snapshot, requested);
        if (initial.availability() == PipelineAvailability.UNAVAILABLE) {
            return rollback(run, VerificationOutcome.BLOCKED_EXTERNAL,
                    "pipeline_unavailable");
        }
        List<CandidateKey> candidates = RequestedCeilingFallback.orderedCandidates(
                requested, initial.effectiveCandidates());
        if (candidates.isEmpty()) {
            return rollback(run, VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                    "recording_inventory_removed_all_requested_fallbacks");
        }

        VerificationOutcome lastOutcome = VerificationOutcome.UNKNOWN;
        boolean fallbackUsed = false;
        for (CandidateKey candidate : candidates) {
            CandidatePreparation preparation = prepareCandidate(run, candidate);
            if (preparation == CandidatePreparation.REMOVED_BY_RECORDING_EVIDENCE) {
                fallbackUsed = true;
            } else if (preparation == CandidatePreparation.READY) {
                CandidateResult result = verifyPreparedCandidate(run, candidate);
                if (result.outcome == VerificationOutcome.VERIFIED_PASS) {
                    return successfulCandidateResult(run, candidate, result, requested, fallbackUsed);
                }
                lastOutcome = result.outcome;
                if (result.halt) return rollback(run, lastOutcome, result.detail);
                fallbackUsed = true;
                logRequest(run.request, candidate, "fallback_transition",
                        "outcome=" + lastOutcome + ",nextBudgetMs="
                                + CameraOperationDeadline.CANDIDATE_TIMEOUT_MILLIS);
            }
        }
        return rollback(run, lastOutcome, "fallback_ladder_exhausted");
    }

    private CandidatePreparation prepareCandidate(RunState run, CandidateKey candidate) {
        if (!evidence(run.snapshot, candidate).isEffective(candidate)) {
            logRequest(run.request, candidate, "candidate_skipped",
                    "reason=removed_by_recording_evidence");
            return CandidatePreparation.REMOVED_BY_RECORDING_EVIDENCE;
        }
        return run.attemptedCandidates.add(candidate)
                ? CandidatePreparation.READY : CandidatePreparation.DUPLICATE;
    }

    private CandidateResult verifyPreparedCandidate(RunState run, CandidateKey candidate) {
        CameraOperationDeadline deadline = CameraOperationDeadline.forCandidate(
                clock.elapsedRealtimeMillis());
        logRequest(run.request, candidate, "candidate_start",
                "remainingMs=" + deadline.remainingMillis(clock.elapsedRealtimeMillis()));
        return verifyCandidate(run, candidate, deadline);
    }

    private Result successfulCandidateResult(RunState run, CandidateKey candidate,
            CandidateResult result, CandidateKey requested, boolean fallbackUsed) {
        boolean requestedPass = candidate.equals(requested) && !fallbackUsed;
        Completion completion = requestedPass
                ? Completion.REQUESTED_VERIFIED : Completion.FALLBACK_VERIFIED;
        return success(run, candidate, result, completion,
                requestedPass ? "requested_candidate_pass" : "fallback_candidate_pass");
    }

    public enum Completion {
        REQUESTED_VERIFIED,
        FALLBACK_VERIFIED,
        ROLLED_BACK,
        UNAVAILABLE
    }

    public enum VerificationStage {
        COMBO,
        ROLLBACK
    }

    public record PreviousSelection(CameraId cameraId, SelectedRecordingProfile selection) {
        public PreviousSelection {
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            selection = Objects.requireNonNull(selection, "selection");
        }
    }

    public record Request(Snapshot snapshot, CandidateKey requestedCandidate,
            Optional<PreviousSelection> previousSelection,
            long firstSessionGeneration, long cameraHealthGeneration) {
        public Request {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            requestedCandidate = Objects.requireNonNull(
                    requestedCandidate, "requestedCandidate");
            previousSelection = Objects.requireNonNull(previousSelection, "previousSelection");
            if (requestedCandidate.kind() != CandidateKey.Kind.TUPLE) {
                throw new IllegalArgumentException("requested candidate must be a tuple");
            }
            if (firstSessionGeneration <= 0) {
                throw new IllegalArgumentException("firstSessionGeneration must be positive");
            }
            if (cameraHealthGeneration < 0) {
                throw new IllegalArgumentException(
                        "cameraHealthGeneration must not be negative");
            }
            CameraCapabilitySnapshotUpdates.pipeline(snapshot,
                    requestedCandidate.cameraId(), requestedCandidate.codec(),
                    requestedCandidate.verificationPipelineId());
            Snapshot validatedSnapshot = snapshot;
            previousSelection.ifPresent(value -> {
                Optional<SelectedRecordingProfile> committed = CameraCapabilitySnapshotUpdates.selectedRecordingProfile(
                        validatedSnapshot, value.cameraId());
                if (committed.isEmpty() || !committed.orElseThrow().equals(value.selection())) {
                    throw new IllegalArgumentException(
                            "previous selection is not committed verified evidence");
                }
            });
        }

        public Request(Snapshot snapshot, CandidateKey requestedCandidate,
                long firstSessionGeneration, long cameraHealthGeneration) {
            this(snapshot, requestedCandidate,
                    CameraCapabilitySnapshotUpdates.selectedRecordingProfile(
                            snapshot, requestedCandidate.cameraId())
                            .map(value -> new PreviousSelection(
                                    requestedCandidate.cameraId(), value)),
                    firstSessionGeneration, cameraHealthGeneration);
        }
    }

    public record Attempt(VerificationStage stage, CandidateKey candidate,
            int attemptNumber, CameraPipelineOperation operation,
            CameraOperationOutcome operationOutcome, long elapsedMillis,
            long remainingMillis, String detail) {
        public Attempt {
            stage = Objects.requireNonNull(stage, "stage");
            candidate = Objects.requireNonNull(candidate, "candidate");
            operation = Objects.requireNonNull(operation, "operation");
            operationOutcome = Objects.requireNonNull(operationOutcome, "operationOutcome");
            if (attemptNumber <= 0) throw new IllegalArgumentException("attemptNumber");
            if (elapsedMillis < 0 || remainingMillis < 0) {
                throw new IllegalArgumentException("timing must not be negative");
            }
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }
    }

    public record Result(Completion completion, VerificationOutcome outcome,
            Snapshot snapshot, Optional<CandidateKey> selectedCandidate,
            Optional<VerifiedCameraBinding> verifiedBinding,
            Optional<CameraOperationContext> activeBinding,
            List<Attempt> attempts, boolean persistenceWriteFailed,
            boolean recoveryRequired, String detail) {
        public Result {
            completion = Objects.requireNonNull(completion, "completion");
            outcome = Objects.requireNonNull(outcome, "outcome");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            selectedCandidate = Objects.requireNonNull(selectedCandidate, "selectedCandidate");
            verifiedBinding = Objects.requireNonNull(verifiedBinding, "verifiedBinding");
            activeBinding = Objects.requireNonNull(activeBinding, "activeBinding");
            attempts = List.copyOf(Objects.requireNonNull(attempts, "attempts"));
            if (verifiedBinding.isPresent()
                    && !verifiedBinding.orElseThrow().context().equals(
                    activeBinding.orElseThrow())) {
                throw new IllegalArgumentException("verified binding is not active");
            }
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }

        public boolean verified() {
            return completion == Completion.REQUESTED_VERIFIED
                    || completion == Completion.FALLBACK_VERIFIED;
        }
    }

    private CandidateResult verifyCandidate(RunState run, CandidateKey candidate,
            CameraOperationDeadline deadline) {
        PipelineEvidence current = evidence(run.snapshot, candidate);
        CaptureModeTuple tuple = candidate.tuple().orElseThrow();
        VerificationOutcome exactOutcome = current.outcome(candidate);
        if (exactOutcome == VerificationOutcome.VERIFIED_PASS) {
            return bindVerifiedCandidate(run, candidate, tuple, deadline);
        }
        if (exactOutcome == VerificationOutcome.DEFINITIVE_UNSUPPORTED
                || !current.isEffective(candidate)) {
            return CandidateResult.next(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                    "recording_tuple_already_failed");
        }
        StageResult combo = verifyStage(run, candidate,
                VerificationStage.COMBO, tuple, deadline);
        if (combo.disposition == Disposition.PASS) {
            if (!healthMatches(run, candidate)) {
                return abortForHealthChange(run, combo, "combo");
            }
            commitComboPass(run, candidate);
            VerifiedCameraBinding binding = new VerifiedCameraBinding(
                    combo.context, combo.diagnostics.orElseThrow(),
                    CameraCapabilitySnapshotUpdates.sensorOrientationDegrees(
                            run.snapshot, candidate.cameraId()));
            return CandidateResult.pass(Optional.of(binding), "combo_pass");
        }
        if (combo.disposition == Disposition.DEFINITIVE) {
            if (!healthMatches(run, candidate)) {
                return CandidateResult.stop(VerificationOutcome.CANCELLED_UNKNOWN,
                        "combo_health_generation_changed");
            }
            commitFact(run, candidate, VerificationOutcome.DEFINITIVE_UNSUPPORTED);
            return CandidateResult.next(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                    "combo_confirmed_fail");
        }
        return combo.disposition == Disposition.TIMEOUT
                ? CandidateResult.next(combo.outcome(), "combo_timeout")
                : CandidateResult.stop(combo.outcome(), "combo=" + combo.detail);
    }

    private CandidateResult bindVerifiedCandidate(RunState run, CandidateKey candidate,
            CaptureModeTuple tuple, CameraOperationDeadline deadline) {
        CameraOperationContext context = newContext(run, candidate, tuple, deadline);
        OperationAttempt attempt = new OperationAttempt(run, VerificationStage.COMBO,
                candidate, 1, context);
        StageResult bind = call(attempt, CameraPipelineOperation.BIND_SESSION,
                runtime::bindSession, false);
        if (bind.disposition != Disposition.PASS) {
            return CandidateResult.stop(bind.outcome(),
                    "verified_tuple_bind=" + bind.detail);
        }
        StageResult checked = diagnostics(attempt, true);
        if (checked.disposition == Disposition.PASS
                && checked.diagnostics.orElseThrow().sessionBound()) {
            commitSelection(run, candidate);
            return CandidateResult.boundPass(context, "verified_tuple_reused");
        }
        cleanup(run, checked);
        return CandidateResult.stop(checked.outcome(),
                "verified_tuple_bind_diagnostics=" + checked.detail);
    }
    private CandidateResult abortForHealthChange(RunState run,
            StageResult stage, String name) {
        StageResult cleanup = cleanup(run, stage);
        if (cleanup.disposition == Disposition.PASS) {
            return CandidateResult.stop(VerificationOutcome.CANCELLED_UNKNOWN,
                    name + "_health_generation_changed");
        }
        return CandidateResult.stop(cleanup.outcome(),
                name + "_health_cleanup=" + cleanup.detail);
    }

    private StageResult verifyStage(RunState run, CandidateKey candidate,
            VerificationStage stage, CaptureModeTuple tuple,
            CameraOperationDeadline deadline) {
        StageResult first = perform(run, candidate, stage, tuple, deadline, 1);
        if (first.disposition == Disposition.PASS) return first;
        if (first.disposition != Disposition.CANDIDATE_SUSPECT) {
            return cleanupAndReturn(run, first);
        }
        StageResult firstCleanup = cleanup(run, first);
        if (firstCleanup.disposition != Disposition.PASS) return firstCleanup;
        if (!healthMatches(run, candidate)) {
            return StageResult.cancelled(stage, candidate, 1, first.context,
                    false, "camera_health_generation_changed_before_confirmation");
        }
        if (deadline.isExpiredAt(clock.elapsedRealtimeMillis())) {
            return StageResult.timeout(stage, candidate, 1, first.context,
                    false, "deadline_before_confirmation");
        }

        StageResult confirmation = perform(run, candidate, stage, tuple, deadline, 2);
        if (confirmation.disposition == Disposition.PASS) return confirmation;
        StageResult confirmationCleanup = cleanup(run, confirmation);
        if (confirmationCleanup.disposition != Disposition.PASS) {
            return confirmationCleanup;
        }
        if (confirmation.disposition == Disposition.CANDIDATE_SUSPECT) {
            if (first.signature.isPresent()
                    && first.signature.equals(confirmation.signature)) {
                return StageResult.definitive(stage, candidate, 2,
                        confirmation.context, confirmation.detail);
            }
            return StageResult.unknown(stage, candidate, 2,
                    confirmation.context,
                    "confirmation_failure_changed_stage_or_error_class");
        }
        return confirmation;
    }

    private StageResult perform(RunState run, CandidateKey candidate,
            VerificationStage stage, CaptureModeTuple tuple,
            CameraOperationDeadline deadline, int attemptNumber) {
        CameraOperationContext context = newContext(run, candidate, tuple, deadline);
        OperationAttempt attempt = new OperationAttempt(run, stage, candidate,
                attemptNumber, context);
        if (stage != VerificationStage.ROLLBACK && !healthMatches(run, candidate)) {
            return StageResult.cancelled(stage, candidate, attemptNumber, context,
                    false, HEALTH_GENERATION_CHANGED);
        }
        if (deadline.isExpiredAt(clock.elapsedRealtimeMillis())) {
            return StageResult.timeout(stage, candidate, attemptNumber, context,
                    false, "deadline_before_" + stage.name().toLowerCase(Locale.ROOT));
        }
        StageResult topology = bindAndValidateTopology(attempt);
        if (topology.disposition != Disposition.PASS) return topology;
        StageResult encoding = runEncoderOperations(attempt);
        if (encoding.disposition != Disposition.PASS) return encoding;
        return validateOutputs(attempt);
    }

    private StageResult bindAndValidateTopology(OperationAttempt attempt) {
        StageResult bind = call(attempt, CameraPipelineOperation.BIND_SESSION,
                runtime::bindSession, false);
        if (bind.disposition != Disposition.PASS) return bind;
        StageResult checked = diagnostics(attempt, true);
        if (checked.disposition != Disposition.PASS) return checked;
        CameraPipelineDiagnostics diagnostics = checked.diagnostics.orElseThrow();
        if (diagnostics.sessionBound() && diagnostics.cameraOutputCount() == 2
                && diagnostics.downstreamSurfaceCount() == 2) {
            return checked;
        }
        return StageResult.candidateSuspect(attempt.stage(), attempt.candidate(),
                attempt.attemptNumber(), attempt.context(), true,
                CameraPipelineOperation.BIND_SESSION, CameraFailureClass.TOPOLOGY,
                "invalid_shared_private_jpeg_topology:cameraOutputs="
                        + diagnostics.cameraOutputCount() + ",downstreamSurfaces="
                        + diagnostics.downstreamSurfaceCount());
    }

    private StageResult runEncoderOperations(OperationAttempt attempt) {
        StageResult start = call(attempt, CameraPipelineOperation.START_ENCODER,
                runtime::startEncoder, true);
        if (start.disposition != Disposition.PASS) return start;
        if (attempt.stage() == VerificationStage.COMBO) {
            StageResult capture = call(attempt, CameraPipelineOperation.CAPTURE_JPEG,
                    runtime::captureJpeg, true);
            if (capture.disposition != Disposition.PASS) return capture;
        }
        StageResult stop = call(attempt, CameraPipelineOperation.STOP_ENCODER,
                runtime::stopEncoder, true);
        if (stop.disposition != Disposition.PASS) return stop;
        return call(attempt, CameraPipelineOperation.FINALIZE_ENCODER,
                runtime::finalizeEncoder, true);
    }

    private StageResult validateOutputs(OperationAttempt attempt) {
        StageResult checked = diagnostics(attempt, true);
        if (checked.disposition != Disposition.PASS) return checked;
        CameraPipelineDiagnostics diagnostics = checked.diagnostics.orElseThrow();
        int sensorOrientationDegrees =
                CameraCapabilitySnapshotUpdates.sensorOrientationDegrees(
                        attempt.run().snapshot, attempt.candidate().cameraId());
        if (!validVideo(attempt.context(), diagnostics)) {
            return StageResult.candidateSuspect(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), true,
                    CameraPipelineOperation.FINALIZE_ENCODER,
                    CameraFailureClass.VIDEO_OUTPUT,
                    validationDetail(attempt.context(), diagnostics, sensorOrientationDegrees));
        }
        if (attempt.stage() == VerificationStage.COMBO
                && (!validImage(attempt.context(), diagnostics, sensorOrientationDegrees)
                || !diagnostics.jpegCapturedWhileEncoderActive())) {
            return StageResult.candidateSuspect(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), true,
                    CameraPipelineOperation.CAPTURE_JPEG,
                    CameraFailureClass.JPEG_OUTPUT,
                    validationDetail(attempt.context(), diagnostics, sensorOrientationDegrees));
        }
        return StageResult.pass(attempt.stage(), attempt.candidate(),
                attempt.attemptNumber(), attempt.context(), true, diagnostics,
                attempt.stage() == VerificationStage.COMBO ? "combo_pass" : "rollback_pass");
    }

    private StageResult call(OperationAttempt attempt, CameraPipelineOperation operation,
            OperationCall operationCall, boolean cleanupNeeded) {
        if (attempt.deadline().isExpiredAt(clock.elapsedRealtimeMillis())) {
            return StageResult.timeout(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(),
                    cleanupNeeded, "deadline_before_"
                            + operation.name().toLowerCase(Locale.ROOT));
        }
        if (attempt.stage() != VerificationStage.ROLLBACK
                && !healthMatches(attempt.run(), attempt.candidate())) {
            return StageResult.cancelled(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), cleanupNeeded,
                    HEALTH_GENERATION_CHANGED);
        }
        CameraOperationResult result;
        try {
            result = Objects.requireNonNull(operationCall.invoke(attempt.context()),
                    "runtime result");
        } catch (RuntimeException error) {
            addAttempt(attempt, operation, CameraOperationOutcome.GLOBAL_FAILURE,
                    "runtime_exception=" + error.getClass().getSimpleName());
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, logPrefix(attempt.context(), attempt.stage(), GLOBAL_FAILURE,
                    "runtime_exception"), error);
            return StageResult.globalFailure(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), cleanupNeeded,
                    "runtime_exception");
        }
        boolean nowNeedsCleanup = cleanupNeeded
                || operation == CameraPipelineOperation.BIND_SESSION;
        if (attempt.stage() != VerificationStage.ROLLBACK
                && !healthMatches(attempt.run(), attempt.candidate())) {
            addAttempt(attempt, operation, CameraOperationOutcome.CANCELLED_UNKNOWN,
                    HEALTH_GENERATION_CHANGED);
            return StageResult.cancelled(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), nowNeedsCleanup,
                    HEALTH_GENERATION_CHANGED);
        }
        CameraOperationOutcome outcome = normalize(result, attempt.context(), attempt.deadline());
        if (result.operation() != operation) {
            outcome = CameraOperationOutcome.GLOBAL_FAILURE;
        }
        addAttempt(attempt, operation, outcome, result.detail());
        log(attempt.context(), attempt.stage(), outcome, result.detail());
        return switch (outcome) {
            case PASS -> StageResult.pass(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), nowNeedsCleanup,
                    null, result.detail());
            case CANDIDATE_SUSPECT, DEFINITIVE_CANDIDATE_FAILURE ->
                    StageResult.candidateSuspect(attempt.stage(), attempt.candidate(),
                            attempt.attemptNumber(), attempt.context(), nowNeedsCleanup, operation,
                            result.failureClass(), result.detail());
            case TIMEOUT_UNKNOWN -> StageResult.timeout(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), nowNeedsCleanup, result.detail());
            case TRANSIENT_RETRYABLE -> StageResult.transientFailure(attempt.stage(),
                    attempt.candidate(), attempt.attemptNumber(), attempt.context(),
                    nowNeedsCleanup, result.detail());
            case GLOBAL_FAILURE -> StageResult.globalFailure(attempt.stage(),
                    attempt.candidate(), attempt.attemptNumber(), attempt.context(),
                    nowNeedsCleanup, result.detail());
            case BLOCKED_EXTERNAL -> StageResult.blocked(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), nowNeedsCleanup, result.detail());
            case CANCELLED_UNKNOWN, STALE -> StageResult.cancelled(attempt.stage(),
                    attempt.candidate(), attempt.attemptNumber(), attempt.context(),
                    nowNeedsCleanup, result.detail());
        };
    }

    private StageResult diagnostics(OperationAttempt attempt, boolean cleanupNeeded) {
        CameraPipelineDiagnostics value;
        try {
            value = Objects.requireNonNull(runtime.diagnostics(attempt.context()), "diagnostics");
        } catch (RuntimeException error) {
            addAttempt(attempt, CameraPipelineOperation.DIAGNOSTICS,
                    CameraOperationOutcome.GLOBAL_FAILURE,
                    "diagnostics_exception=" + error.getClass().getSimpleName());
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, logPrefix(attempt.context(), attempt.stage(), GLOBAL_FAILURE,
                    "diagnostics_exception"), error);
            return StageResult.globalFailure(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), cleanupNeeded,
                    "diagnostics_exception");
        }
        CameraOperationOutcome outcome;
        if (!attempt.context().matchesCurrentOperation(value.context())) {
            outcome = CameraOperationOutcome.STALE;
        } else if (attempt.stage() != VerificationStage.ROLLBACK
                && !healthMatches(attempt.run(), attempt.candidate())) {
            outcome = CameraOperationOutcome.CANCELLED_UNKNOWN;
        } else if (attempt.deadline().isExpiredAt(clock.elapsedRealtimeMillis())) {
            outcome = CameraOperationOutcome.TIMEOUT_UNKNOWN;
        } else {
            outcome = CameraOperationOutcome.PASS;
        }
        addAttempt(attempt, CameraPipelineOperation.DIAGNOSTICS, outcome, value.detail());
        if (outcome == CameraOperationOutcome.PASS) {
            return StageResult.pass(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), cleanupNeeded,
                    value, value.detail());
        }
        if (outcome == CameraOperationOutcome.TIMEOUT_UNKNOWN) {
            return StageResult.timeout(attempt.stage(), attempt.candidate(),
                    attempt.attemptNumber(), attempt.context(), cleanupNeeded, value.detail());
        }
        return StageResult.cancelled(attempt.stage(), attempt.candidate(),
                attempt.attemptNumber(), attempt.context(), cleanupNeeded, value.detail());
    }

    private StageResult cleanupAndReturn(RunState run, StageResult result) {
        if (!result.cleanupNeeded) return result;
        StageResult cleanup = cleanup(run, result);
        return cleanup.disposition == Disposition.PASS ? result : cleanup;
    }

    private StageResult cleanup(RunState run, StageResult result) {
        if (!result.cleanupNeeded) {
            return StageResult.pass(result.stage, result.candidate,
                    result.attemptNumber, result.context, false,
                    result.diagnostics.orElse(null), "cleanup_not_needed");
        }
        OperationAttempt attempt = new OperationAttempt(run, result.stage,
                result.candidate, result.attemptNumber, result.context);
        CameraOperationResult release;
        try {
            release = Objects.requireNonNull(runtime.release(result.context),
                    "release result");
        } catch (RuntimeException error) {
            addAttempt(attempt, CameraPipelineOperation.RELEASE,
                    CameraOperationOutcome.GLOBAL_FAILURE,
                    "release_exception=" + error.getClass().getSimpleName());
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, logPrefix(result.context, result.stage,
                    GLOBAL_FAILURE, "release_exception"), error);
            return StageResult.transientFailure(result.stage, result.candidate,
                    result.attemptNumber, result.context, false,
                    "release_exception");
        }
        CameraOperationOutcome outcome = release.context()
                .matchesCurrentOperation(result.context)
                ? release.outcome() : CameraOperationOutcome.STALE;
        addAttempt(attempt, CameraPipelineOperation.RELEASE, outcome, release.detail());
        log(result.context, result.stage, outcome, "cleanup=" + release.detail());
        return switch (outcome) {
            case PASS -> StageResult.pass(result.stage, result.candidate,
                    result.attemptNumber, result.context, false,
                    result.diagnostics.orElse(null), "cleanup_complete");
            case BLOCKED_EXTERNAL -> StageResult.blocked(result.stage,
                    result.candidate, result.attemptNumber, result.context,
                    false, release.detail());
            case CANCELLED_UNKNOWN, STALE -> StageResult.cancelled(result.stage,
                    result.candidate, result.attemptNumber, result.context,
                    false, release.detail());
            case TIMEOUT_UNKNOWN -> StageResult.timeout(result.stage,
                    result.candidate, result.attemptNumber, result.context,
                    false, release.detail());
            case GLOBAL_FAILURE -> StageResult.globalFailure(result.stage,
                    result.candidate, result.attemptNumber, result.context,
                    false, release.detail());
            default -> StageResult.transientFailure(result.stage,
                    result.candidate, result.attemptNumber, result.context,
                    false, release.detail());
        };
    }

    private void commitFact(RunState run, CandidateKey candidate,
            VerificationOutcome outcome) {
        run.snapshot = CameraCapabilitySnapshotUpdates.withEvidence(
                run.snapshot, candidate, outcome);
        persist(run, "evidence_" + outcome.name().toLowerCase(Locale.ROOT));
    }

    private void commitComboPass(RunState run, CandidateKey tuple) {
        Snapshot updated = CameraCapabilitySnapshotUpdates.withEvidence(
                run.snapshot, tuple, VerificationOutcome.VERIFIED_PASS);
        run.snapshot = CameraCapabilitySnapshotUpdates.withRuntimeVerifiedRecordingProfile(updated,
                tuple.cameraId(), Optional.of(selection(tuple)));
        persist(run, "combo_pass_and_selection");
    }

    private void commitSelection(RunState run, CandidateKey candidate) {
        run.snapshot = CameraCapabilitySnapshotUpdates.withRuntimeVerifiedRecordingProfile(
                run.snapshot, candidate.cameraId(),
                Optional.of(selection(candidate)));
        persist(run, "reused_verified_selection");
    }

    private static SelectedRecordingProfile selection(CandidateKey candidate) {
        return new SelectedRecordingProfile(candidate.codec(),
                candidate.verificationPipelineId(), candidate.tuple().orElseThrow());
    }

    private void persist(RunState run, String reason) {
        try {
            capabilityStore.requestWrite(run.snapshot);
            logRequest(run.request, run.request.requestedCandidate(),
                    "persist_request", "reason=" + reason);
        } catch (RuntimeException error) {
            run.persistenceWriteFailed = true;
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, "camera_verification persist_failed reason=" + reason, error);
        }
    }

    private Result success(RunState run, CandidateKey candidate,
            CandidateResult candidateResult, Completion completion,
            String detail) {
        return new Result(completion, VerificationOutcome.VERIFIED_PASS,
                run.snapshot, Optional.of(candidate), candidateResult.binding,
                candidateResult.activeBinding, run.attempts,
                run.persistenceWriteFailed, false, detail + ":" + candidateResult.detail);
    }

    private Result rollback(RunState run, VerificationOutcome cause,
            String detail) {
        Optional<PreviousSelection> previous = run.request.previousSelection();
        if (previous.isEmpty()) {
            boolean recovery = cause == VerificationOutcome.TRANSIENT_RETRYABLE
                    || cause == VerificationOutcome.GLOBAL_FAILURE;
            return new Result(Completion.UNAVAILABLE, cause, run.snapshot,
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    run.attempts, run.persistenceWriteFailed, recovery, detail);
        }
        PreviousSelection value = previous.orElseThrow();
        try {
            run.snapshot = CameraCapabilitySnapshotUpdates.withRuntimeVerifiedRecordingProfile(
                    run.snapshot, value.cameraId(), Optional.of(value.selection()));
            persist(run, "rollback_previous_selection");
        } catch (RuntimeException error) {
            run.persistenceWriteFailed = true;
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, "camera_verification rollback_snapshot_failed", error);
        }

        CandidateKey previousCandidate = CandidateKey.forTuple(value.cameraId(),
                value.selection().codec(), value.selection().verificationPipelineId(),
                value.selection().tuple());
        CameraOperationDeadline deadline = CameraOperationDeadline.forCandidate(
                clock.elapsedRealtimeMillis());
        CameraOperationContext context = newContext(run, previousCandidate,
                value.selection().tuple(), deadline);
        OperationAttempt attempt = new OperationAttempt(run, VerificationStage.ROLLBACK,
                previousCandidate, 1, context);
        StageResult bind = call(attempt, CameraPipelineOperation.BIND_SESSION,
                runtime::bindSession, false);
        StageResult rollbackFailure = bind;
        if (bind.disposition == Disposition.PASS) {
            StageResult checked = diagnostics(attempt, true);
            if (checked.disposition == Disposition.PASS
                    && checked.diagnostics.orElseThrow().sessionBound()) {
                return new Result(Completion.ROLLED_BACK, cause, run.snapshot,
                        Optional.empty(), Optional.empty(), Optional.of(context),
                        run.attempts, run.persistenceWriteFailed, false,
                        detail + ":rollback_ready");
            }
            rollbackFailure = checked;
        }
        cleanup(run, rollbackFailure);
        return new Result(Completion.ROLLED_BACK, cause, run.snapshot,
                Optional.empty(), Optional.empty(), Optional.empty(), run.attempts,
                run.persistenceWriteFailed, true,
                detail + ":rollback_recovery_required");
    }

    private boolean healthMatches(RunState run, CandidateKey candidate) {
        long current;
        try {
            current = healthGenerationProvider.currentGeneration(candidate.cameraId());
        } catch (RuntimeException error) {
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, "camera_verification health_generation_read_failed camera="
                    + candidate.cameraId(), error);
            return false;
        }
        boolean matches = current == run.request.cameraHealthGeneration();
        if (!matches) {
            logRequest(run.request, candidate, "health_generation_changed",
                    "expected=" + run.request.cameraHealthGeneration()
                            + ",current=" + current);
        }
        return matches;
    }

    private CameraOperationContext newContext(RunState run,
            CandidateKey candidate, CaptureModeTuple tuple,
            CameraOperationDeadline deadline) {
        return new CameraOperationContext(candidate.cameraId(),
                candidate.verificationPipelineId(), candidate.codec(), tuple,
                run.nextSessionGeneration++, run.request.cameraHealthGeneration(), deadline);
    }

    private CameraOperationOutcome normalize(CameraOperationResult result,
            CameraOperationContext expected, CameraOperationDeadline deadline) {
        if (!result.context().matchesCurrentOperation(expected)) {
            return CameraOperationOutcome.STALE;
        }
        CameraOperationOutcome outcome = result.outcome();
        if (outcome == CameraOperationOutcome.GLOBAL_FAILURE
                || outcome == CameraOperationOutcome.TRANSIENT_RETRYABLE
                || outcome == CameraOperationOutcome.BLOCKED_EXTERNAL
                || outcome == CameraOperationOutcome.CANCELLED_UNKNOWN
                || outcome == CameraOperationOutcome.STALE) {
            return outcome;
        }
        return deadline.isExpiredAt(clock.elapsedRealtimeMillis())
                ? CameraOperationOutcome.TIMEOUT_UNKNOWN : outcome;
    }

    private static boolean validVideo(CameraOperationContext context,
            CameraPipelineDiagnostics diagnostics) {
        CameraResolution expected = context.tuple().videoMode().resolution().actual();
        return diagnostics.sessionBound()
                && !diagnostics.encoderActive()
                && diagnostics.encoderFinalized()
                && diagnostics.encodedSampleCount() > 0
                && diagnostics.encodedVideoResolution()
                        .filter(value -> Objects.equals(expected, value)).isPresent()
                && diagnostics.finalizedVideoArtifact().isPresent();
    }

    private static boolean validImage(CameraOperationContext context,
            CameraPipelineDiagnostics diagnostics, int sensorOrientationDegrees) {
        CameraResolution expected = context.tuple().imageMode().resolution().actual();
        return diagnostics.capturedJpegResolution()
                .filter(actual -> expected.matchesConsideringRotation(
                        actual, sensorOrientationDegrees))
                .isPresent()
                && diagnostics.capturedJpegArtifact().isPresent();
    }

    private static String validationDetail(CameraOperationContext context,
            CameraPipelineDiagnostics diagnostics, int sensorOrientationDegrees) {
        return "output_validation_failed:expectedVideo="
                + context.tuple().videoMode().resolution().actual()
                + ",expectedImage=" + context.tuple().imageMode().resolution().actual()
                + ",sensorOrientationDegrees=" + sensorOrientationDegrees
                + ",samples=" + diagnostics.encodedSampleCount()
                + ",encodedVideo=" + diagnostics.encodedVideoResolution()
                + ",jpeg=" + diagnostics.capturedJpegResolution()
                + ",jpegWhileEncoder=" + diagnostics.jpegCapturedWhileEncoderActive()
                + ",videoArtifact=" + diagnostics.finalizedVideoArtifact()
                + ",jpegArtifact=" + diagnostics.capturedJpegArtifact();
    }

    private void addAttempt(OperationAttempt attempt, CameraPipelineOperation operation,
            CameraOperationOutcome outcome, String detail) {
        long now = clock.elapsedRealtimeMillis();
        attempt.run().attempts.add(new Attempt(attempt.stage(), attempt.candidate(),
                attempt.attemptNumber(), operation, outcome,
                Math.max(0, now - attempt.deadline().startedAtMillis()),
                attempt.deadline().remainingMillis(now),
                detail == null || detail.isBlank() ? "no_detail" : detail));
    }

    private void log(CameraOperationContext context, VerificationStage stage,
            CameraOperationOutcome outcome, String detail) {
        if (outcome == CameraOperationOutcome.PASS) return;
        logger.info(LogCategory.CAPABILITY, "unspecified", logPrefix(context, stage, outcome.name(), detail));
    }

    private String logPrefix(CameraOperationContext context,
            VerificationStage stage, String outcome, String detail) {
        long now = clock.elapsedRealtimeMillis();
        return "camera_verification camera=" + context.cameraId()
                + " codec=" + context.codec()
                + " pipeline=" + context.verificationPipelineId()
                + " tuple=" + context.tuple()
                + " generation=" + context.sessionGeneration()
                + " healthGeneration=" + context.cameraHealthGeneration()
                + " stage=" + stage + " outcome=" + outcome
                + " elapsedMs=" + Math.max(0,
                now - context.deadline().startedAtMillis())
                + " remainingMs=" + context.deadline().remainingMillis(now)
                + " detail=" + detail;
    }

    private void logRequest(Request request, CandidateKey candidate,
            String stage, String detail) {
        if ("candidate_start".equals(stage) || "persist_request".equals(stage)) return;
        logger.info(LogCategory.CAPABILITY, "unspecified", "camera_verification camera=" + candidate.cameraId()
                + " codec=" + candidate.codec()
                + " pipeline=" + candidate.verificationPipelineId()
                + " requestedTuple=" + request.requestedCandidate().tuple().orElse(null)
                + " effectiveTuple=" + candidate.tuple().orElse(null)
                + " generation=" + request.firstSessionGeneration()
                + " stage=" + stage + " detail=" + detail);
    }

    private static PipelineEvidence evidence(Snapshot snapshot,
            CandidateKey candidate) {
        return CameraCapabilitySnapshotUpdates.pipeline(snapshot,
                candidate.cameraId(), candidate.codec(),
                candidate.verificationPipelineId());
    }

    private record OperationAttempt(RunState run, VerificationStage stage,
            CandidateKey candidate, int attemptNumber, CameraOperationContext context) {
        private CameraOperationDeadline deadline() {
            return context.deadline();
        }
    }

    private interface OperationCall {
        CameraOperationResult invoke(CameraOperationContext context);
    }

    private enum CandidatePreparation {
        READY,
        REMOVED_BY_RECORDING_EVIDENCE,
        DUPLICATE
    }

    private enum Disposition {
        PASS,
        CANDIDATE_SUSPECT,
        DEFINITIVE,
        TIMEOUT,
        TRANSIENT,
        GLOBAL,
        BLOCKED,
        CANCELLED,
        UNKNOWN
    }

    private static final class RunState {
        private final Request request;
        private final List<Attempt> attempts = new ArrayList<>();
        private final Set<CandidateKey> attemptedCandidates = new HashSet<>();
        private Snapshot snapshot;
        private long nextSessionGeneration;
        private boolean persistenceWriteFailed;

        private RunState(Request request) {
            this.request = request;
            snapshot = request.snapshot();
            nextSessionGeneration = request.firstSessionGeneration();
        }
    }

    private record CandidateResult(VerificationOutcome outcome, boolean halt,
            Optional<VerifiedCameraBinding> binding,
            Optional<CameraOperationContext> activeBinding, String detail) {
        private CandidateResult {
            outcome = Objects.requireNonNull(outcome, "outcome");
            binding = Objects.requireNonNull(binding, "binding");
            activeBinding = Objects.requireNonNull(activeBinding, "activeBinding");
            if (binding.isPresent()
                    && !activeBinding.equals(binding.map(VerifiedCameraBinding::context))) {
                throw new IllegalArgumentException("verified binding is not active");
            }
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }

        private static CandidateResult pass(
                Optional<VerifiedCameraBinding> binding, String detail) {
            return new CandidateResult(VerificationOutcome.VERIFIED_PASS,
                    false, binding, binding.map(VerifiedCameraBinding::context), detail);
        }

        private static CandidateResult boundPass(CameraOperationContext context,
                String detail) {
            return new CandidateResult(VerificationOutcome.VERIFIED_PASS,
                    false, Optional.empty(), Optional.of(context), detail);
        }

        private static CandidateResult next(
                VerificationOutcome outcome, String detail) {
            return new CandidateResult(outcome, false, Optional.empty(),
                    Optional.empty(), detail);
        }

        private static CandidateResult stop(
                VerificationOutcome outcome, String detail) {
            return new CandidateResult(outcome, true, Optional.empty(),
                    Optional.empty(), detail);
        }
    }

    private record FailureSignature(CameraPipelineOperation operation,
            CameraFailureClass failureClass) {
        private FailureSignature {
            operation = Objects.requireNonNull(operation, "operation");
            failureClass = Objects.requireNonNull(failureClass, "failureClass");
            if (failureClass == CameraFailureClass.NONE) {
                throw new IllegalArgumentException("candidate failure class is required");
            }
        }
    }

    private record StageResult(Disposition disposition,
            VerificationStage stage, CandidateKey candidate, int attemptNumber,
            CameraOperationContext context, boolean cleanupNeeded,
            Optional<CameraPipelineDiagnostics> diagnostics,
            Optional<FailureSignature> signature, String detail) {
        private StageResult {
            disposition = Objects.requireNonNull(disposition, "disposition");
            stage = Objects.requireNonNull(stage, "stage");
            candidate = Objects.requireNonNull(candidate, "candidate");
            context = Objects.requireNonNull(context, "context");
            diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
            signature = Objects.requireNonNull(signature, "signature");
            if (attemptNumber <= 0) throw new IllegalArgumentException("attemptNumber");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }

        private VerificationOutcome outcome() {
            return switch (disposition) {
                case PASS -> VerificationOutcome.VERIFIED_PASS;
                case DEFINITIVE -> VerificationOutcome.DEFINITIVE_UNSUPPORTED;
                case TIMEOUT -> VerificationOutcome.TIMEOUT_UNKNOWN;
                case TRANSIENT -> VerificationOutcome.TRANSIENT_RETRYABLE;
                case GLOBAL -> VerificationOutcome.GLOBAL_FAILURE;
                case BLOCKED -> VerificationOutcome.BLOCKED_EXTERNAL;
                case CANCELLED -> VerificationOutcome.CANCELLED_UNKNOWN;
                case CANDIDATE_SUSPECT, UNKNOWN -> VerificationOutcome.UNKNOWN;
            };
        }

        private static StageResult pass(VerificationStage stage,
                CandidateKey candidate, int attemptNumber,
                CameraOperationContext context, boolean cleanupNeeded,
                CameraPipelineDiagnostics diagnostics, String detail) {
            return new StageResult(Disposition.PASS, stage, candidate, attemptNumber,
                    context, cleanupNeeded, Optional.ofNullable(diagnostics),
                    Optional.empty(), detail);
        }

        private static StageResult candidateSuspect(VerificationStage stage,
                CandidateKey candidate, int attemptNumber,
                CameraOperationContext context, boolean cleanupNeeded,
                CameraPipelineOperation operation, CameraFailureClass failureClass,
                String detail) {
            return new StageResult(Disposition.CANDIDATE_SUSPECT, stage, candidate,
                    attemptNumber, context, cleanupNeeded, Optional.empty(),
                    Optional.of(new FailureSignature(operation, failureClass)), detail);
        }

        private static StageResult definitive(VerificationStage stage,
                CandidateKey candidate, int attemptNumber,
                CameraOperationContext context, String detail) {
            return new StageResult(Disposition.DEFINITIVE, stage, candidate,
                    attemptNumber, context, false, Optional.empty(),
                    Optional.empty(), detail);
        }

        private static StageResult timeout(VerificationStage stage,
                CandidateKey candidate, int attemptNumber,
                CameraOperationContext context, boolean cleanupNeeded,
                String detail) {
            return new StageResult(Disposition.TIMEOUT, stage, candidate,
                    attemptNumber, context, cleanupNeeded, Optional.empty(),
                    Optional.empty(), detail);
        }

        private static StageResult transientFailure(VerificationStage stage,
                CandidateKey candidate, int attemptNumber,
                CameraOperationContext context, boolean cleanupNeeded,
                String detail) {
            return new StageResult(Disposition.TRANSIENT, stage, candidate,
                    attemptNumber, context, cleanupNeeded, Optional.empty(),
                    Optional.empty(), detail);
        }

        private static StageResult globalFailure(VerificationStage stage,
                CandidateKey candidate, int attemptNumber,
                CameraOperationContext context, boolean cleanupNeeded,
                String detail) {
            return new StageResult(Disposition.GLOBAL, stage, candidate,
                    attemptNumber, context, cleanupNeeded, Optional.empty(),
                    Optional.empty(), detail);
        }

        private static StageResult blocked(VerificationStage stage,
                CandidateKey candidate, int attemptNumber,
                CameraOperationContext context, boolean cleanupNeeded,
                String detail) {
            return new StageResult(Disposition.BLOCKED, stage, candidate,
                    attemptNumber, context, cleanupNeeded, Optional.empty(),
                    Optional.empty(), detail);
        }

        private static StageResult cancelled(VerificationStage stage,
                CandidateKey candidate, int attemptNumber,
                CameraOperationContext context, boolean cleanupNeeded,
                String detail) {
            return new StageResult(Disposition.CANCELLED, stage, candidate,
                    attemptNumber, context, cleanupNeeded, Optional.empty(),
                    Optional.empty(), detail);
        }

        private static StageResult unknown(VerificationStage stage,
                CandidateKey candidate, int attemptNumber,
                CameraOperationContext context, String detail) {
            return new StageResult(Disposition.UNKNOWN, stage, candidate,
                    attemptNumber, context, false, Optional.empty(),
                    Optional.empty(), detail);
        }
    }


}
