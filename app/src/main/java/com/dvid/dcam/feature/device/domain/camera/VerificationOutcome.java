package com.dvid.dcam.feature.device.domain.camera;

public enum VerificationOutcome {
    UNKNOWN(false, false),
    VERIFIED_PASS(true, false),
    DEFINITIVE_UNSUPPORTED(true, true),
    TIMEOUT_UNKNOWN(false, false),
    TRANSIENT_RETRYABLE(false, false),
    GLOBAL_FAILURE(false, false),
    BLOCKED_EXTERNAL(false, false),
    STORAGE_BLOCKED(false, false),
    CANCELLED_UNKNOWN(false, false);

    private final boolean terminalEvidence;
    private final boolean prunesCandidate;

    VerificationOutcome(boolean terminalEvidence, boolean prunesCandidate) {
        this.terminalEvidence = terminalEvidence;
        this.prunesCandidate = prunesCandidate;
    }

    public boolean isTerminalEvidence() { return terminalEvidence; }
    public boolean prunesCandidate() { return prunesCandidate; }
}