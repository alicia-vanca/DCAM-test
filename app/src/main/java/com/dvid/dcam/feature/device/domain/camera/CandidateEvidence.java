package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;

public record CandidateEvidence(CandidateKey candidate, VerificationOutcome outcome) {
    public CandidateEvidence {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(outcome, "outcome");
    }
}