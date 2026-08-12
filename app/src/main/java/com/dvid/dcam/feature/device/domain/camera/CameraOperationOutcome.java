package com.dvid.dcam.feature.device.domain.camera;

public enum CameraOperationOutcome {
    PASS(false, false, true, false),
    CANDIDATE_SUSPECT(false, false, false, true),
    DEFINITIVE_CANDIDATE_FAILURE(false, false, true, false),
    TIMEOUT_UNKNOWN(false, false, false, true),
    TRANSIENT_RETRYABLE(true, false, false, true),
    GLOBAL_FAILURE(true, true, false, true),
    BLOCKED_EXTERNAL(false, false, false, true),
    CANCELLED_UNKNOWN(false, false, false, true),
    STALE(false, false, false, true);

    private final boolean requiresRuntimeRecovery;
    private final boolean globalFailure;
    private final boolean candidateConclusion;
    private final boolean unknown;

    CameraOperationOutcome(boolean requiresRuntimeRecovery, boolean globalFailure,
            boolean candidateConclusion, boolean unknown) {
        this.requiresRuntimeRecovery = requiresRuntimeRecovery;
        this.globalFailure = globalFailure;
        this.candidateConclusion = candidateConclusion;
        this.unknown = unknown;
    }

    public boolean requiresRuntimeRecovery() { return requiresRuntimeRecovery; }

    public boolean isGlobalFailure() { return globalFailure; }

    public boolean isCandidateConclusion() { return candidateConclusion; }

    public boolean isCandidateFailure() {
        return this == CANDIDATE_SUSPECT || this == DEFINITIVE_CANDIDATE_FAILURE;
    }

    public boolean isUnknown() { return unknown; }
}