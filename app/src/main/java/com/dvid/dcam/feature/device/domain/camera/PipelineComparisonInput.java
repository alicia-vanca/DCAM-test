package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;
import java.util.OptionalLong;

public record PipelineComparisonInput(
        PipelineEvidence evidence,
        boolean complete,
        OptionalLong medianTotalVerifyMillis) {
    public PipelineComparisonInput {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(medianTotalVerifyMillis, "medianTotalVerifyMillis");
        if (medianTotalVerifyMillis.isPresent()
                && medianTotalVerifyMillis.orElseThrow() < 0) {
            throw new IllegalArgumentException(
                    "median total verify duration must not be negative");
        }
    }

    public static PipelineComparisonInput withoutPerformance(
            PipelineEvidence evidence, boolean complete) {
        return new PipelineComparisonInput(evidence, complete, OptionalLong.empty());
    }
}