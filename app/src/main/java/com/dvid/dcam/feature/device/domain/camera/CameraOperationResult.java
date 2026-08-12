package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;

public record CameraOperationResult(
        CameraOperationContext context,
        CameraPipelineOperation operation,
        CameraOperationOutcome outcome,
        CameraFailureClass failureClass,
        long elapsedMillis,
        String detail) {
    public CameraOperationResult {
        context = Objects.requireNonNull(context, "context");
        operation = Objects.requireNonNull(operation, "operation");
        outcome = Objects.requireNonNull(outcome, "outcome");
        failureClass = Objects.requireNonNull(failureClass, "failureClass");
        if (outcome.isCandidateFailure() && failureClass == CameraFailureClass.NONE) {
            throw new IllegalArgumentException(
                    "candidate conclusion requires failure class");
        }
        if (!outcome.isCandidateFailure() && failureClass != CameraFailureClass.NONE) {
            throw new IllegalArgumentException(
                    "non-candidate outcome cannot have failure class");
        }
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("elapsedMillis must not be negative");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail is required");
        }
    }

    public CameraOperationResult(CameraOperationContext context,
            CameraPipelineOperation operation, CameraOperationOutcome outcome,
            long elapsedMillis, String detail) {
        this(context, operation, outcome,
                outcome.isCandidateFailure()
                        ? CameraFailureClass.UNKNOWN : CameraFailureClass.NONE,
                elapsedMillis, detail);
    }

    public boolean completedWithinDeadline(long completedAtMillis) {
        return !context.deadline().isExpiredAt(completedAtMillis);
    }

    public CameraOperationOutcome effectiveOutcomeAgainst(
            CameraOperationContext currentContext, long completedAtMillis) {
        if (!context.matchesCurrentOperation(currentContext)) {
            return CameraOperationOutcome.STALE;
        }
        if (outcome.isCandidateConclusion() && !completedWithinDeadline(completedAtMillis)) {
            return CameraOperationOutcome.TIMEOUT_UNKNOWN;
        }
        return outcome;
    }
}