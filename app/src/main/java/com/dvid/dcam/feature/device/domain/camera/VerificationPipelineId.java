package com.dvid.dcam.feature.device.domain.camera;

public record VerificationPipelineId(String value)
        implements Comparable<VerificationPipelineId> {
    public VerificationPipelineId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("verification pipeline ID is required");
        }
    }

    @Override public int compareTo(VerificationPipelineId other) {
        return value.compareTo(other.value);
    }

    @Override public String toString() { return value; }
}