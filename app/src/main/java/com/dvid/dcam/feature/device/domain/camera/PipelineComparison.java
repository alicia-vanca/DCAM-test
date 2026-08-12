package com.dvid.dcam.feature.device.domain.camera;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

public record PipelineComparison(
        Status status,
        Coverage coverage,
        Recommendation recommendation,
        RecommendationReason recommendationReason,
        VerificationPipelineId pipelineA,
        VerificationPipelineId pipelineB,
        Set<CaptureModeTuple> intersection,
        Set<CaptureModeTuple> aOnly,
        Set<CaptureModeTuple> bOnly,
        Set<CaptureModeTuple> bothFail,
        Set<CaptureModeTuple> unknown) {
    public enum Status { COMPLETE, INCOMPLETE }
    public enum Coverage { A_BROADER, B_BROADER, EQUAL, INCOMPARABLE, INCOMPLETE }
    public enum Recommendation { PIPELINE_A, PIPELINE_B, NONE }
    public enum RecommendationReason {
        STRICT_SUPERSET,
        BEST_TUPLE,
        PASS_COUNT,
        PERFORMANCE,
        FINAL_TIE_A,
        INCOMPLETE
    }

    public PipelineComparison {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(recommendation, "recommendation");
        Objects.requireNonNull(recommendationReason, "recommendationReason");
        Objects.requireNonNull(pipelineA, "pipelineA");
        Objects.requireNonNull(pipelineB, "pipelineB");
        intersection = immutableOrdered(intersection);
        aOnly = immutableOrdered(aOnly);
        bOnly = immutableOrdered(bOnly);
        bothFail = immutableOrdered(bothFail);
        unknown = immutableOrdered(unknown);
    }

    public static PipelineComparison compare(Collection<CaptureModeTuple> commonUniverse,
            PipelineComparisonInput pipelineA,
            PipelineComparisonInput pipelineB) {
        Objects.requireNonNull(commonUniverse, "commonUniverse");
        Objects.requireNonNull(pipelineA, "pipelineA");
        Objects.requireNonNull(pipelineB, "pipelineB");
        requireComparableInputs(pipelineA, pipelineB);

        TreeSet<CaptureModeTuple> universe = orderedSet(commonUniverse);
        TreeSet<CaptureModeTuple> intersection = orderedSet();
        TreeSet<CaptureModeTuple> aOnly = orderedSet();
        TreeSet<CaptureModeTuple> bOnly = orderedSet();
        TreeSet<CaptureModeTuple> bothFail = orderedSet();
        TreeSet<CaptureModeTuple> unknown = orderedSet();
        TreeSet<CaptureModeTuple> aPass = orderedSet();
        TreeSet<CaptureModeTuple> bPass = orderedSet();

        for (CaptureModeTuple tuple : universe) {
            VerificationOutcome aOutcome = pipelineA.evidence().outcome(tuple);
            VerificationOutcome bOutcome = pipelineB.evidence().outcome(tuple);
            boolean aPassed = aOutcome == VerificationOutcome.VERIFIED_PASS;
            boolean bPassed = bOutcome == VerificationOutcome.VERIFIED_PASS;
            boolean aFailed = aOutcome == VerificationOutcome.DEFINITIVE_UNSUPPORTED;
            boolean bFailed = bOutcome == VerificationOutcome.DEFINITIVE_UNSUPPORTED;
            if (aPassed) aPass.add(tuple);
            if (bPassed) bPass.add(tuple);

            if (aPassed && bPassed) intersection.add(tuple);
            else if (aPassed && bFailed) aOnly.add(tuple);
            else if (aFailed && bPassed) bOnly.add(tuple);
            else if (aFailed && bFailed) bothFail.add(tuple);
            else unknown.add(tuple);
        }

        boolean complete = pipelineA.complete()
                && pipelineB.complete()
                && pipelineA.evidence().availability() == PipelineAvailability.AVAILABLE
                && pipelineB.evidence().availability() == PipelineAvailability.AVAILABLE
                && unknown.isEmpty();
        if (!complete) {
            return new PipelineComparison(Status.INCOMPLETE, Coverage.INCOMPLETE,
                    Recommendation.NONE, RecommendationReason.INCOMPLETE,
                    pipelineA.evidence().verificationPipelineId(),
                    pipelineB.evidence().verificationPipelineId(),
                    intersection, aOnly, bOnly, bothFail, unknown);
        }

        Coverage coverage = coverage(aPass, bPass);
        Decision decision = recommendation(
                coverage, aPass, bPass,
                pipelineA.medianTotalVerifyMillis(),
                pipelineB.medianTotalVerifyMillis());
        return new PipelineComparison(Status.COMPLETE, coverage,
                decision.recommendation(), decision.reason(),
                pipelineA.evidence().verificationPipelineId(),
                pipelineB.evidence().verificationPipelineId(),
                intersection, aOnly, bOnly, bothFail, unknown);
    }

    private static Coverage coverage(Set<CaptureModeTuple> aPass,
            Set<CaptureModeTuple> bPass) {
        if (aPass.equals(bPass)) return Coverage.EQUAL;
        if (aPass.containsAll(bPass)) return Coverage.A_BROADER;
        if (bPass.containsAll(aPass)) return Coverage.B_BROADER;
        return Coverage.INCOMPARABLE;
    }

    private static Decision recommendation(Coverage coverage,
            Set<CaptureModeTuple> aPass,
            Set<CaptureModeTuple> bPass,
            OptionalLong aMedian,
            OptionalLong bMedian) {
        if (coverage == Coverage.A_BROADER) {
            return new Decision(Recommendation.PIPELINE_A,
                    RecommendationReason.STRICT_SUPERSET);
        }
        if (coverage == Coverage.B_BROADER) {
            return new Decision(Recommendation.PIPELINE_B,
                    RecommendationReason.STRICT_SUPERSET);
        }

        int bestOrder = compareBest(aPass, bPass);
        if (bestOrder > 0) {
            return new Decision(Recommendation.PIPELINE_A,
                    RecommendationReason.BEST_TUPLE);
        }
        if (bestOrder < 0) {
            return new Decision(Recommendation.PIPELINE_B,
                    RecommendationReason.BEST_TUPLE);
        }
        if (aPass.size() > bPass.size()) {
            return new Decision(Recommendation.PIPELINE_A,
                    RecommendationReason.PASS_COUNT);
        }
        if (bPass.size() > aPass.size()) {
            return new Decision(Recommendation.PIPELINE_B,
                    RecommendationReason.PASS_COUNT);
        }
        if (aMedian.isPresent() && bMedian.isPresent()) {
            int performanceOrder = Long.compare(
                    bMedian.orElseThrow(), aMedian.orElseThrow());
            if (performanceOrder > 0) {
                return new Decision(Recommendation.PIPELINE_A,
                        RecommendationReason.PERFORMANCE);
            }
            if (performanceOrder < 0) {
                return new Decision(Recommendation.PIPELINE_B,
                        RecommendationReason.PERFORMANCE);
            }
        }
        return new Decision(Recommendation.PIPELINE_A,
                RecommendationReason.FINAL_TIE_A);
    }

    private static int compareBest(Set<CaptureModeTuple> aPass,
            Set<CaptureModeTuple> bPass) {
        Comparator<CaptureModeTuple> order = CameraModeOrder.tuples();
        Optional<CaptureModeTuple> bestA = aPass.stream().max(order);
        Optional<CaptureModeTuple> bestB = bPass.stream().max(order);
        if (bestA.isEmpty()) return bestB.isEmpty() ? 0 : -1;
        if (bestB.isEmpty()) return 1;
        return order.compare(bestA.orElseThrow(), bestB.orElseThrow());
    }

    private static void requireComparableInputs(PipelineComparisonInput pipelineA,
            PipelineComparisonInput pipelineB) {
        PipelineEvidence a = pipelineA.evidence();
        PipelineEvidence b = pipelineB.evidence();
        if (!a.cameraId().equals(b.cameraId()) || a.codec() != b.codec()) {
            throw new IllegalArgumentException(
                    "pipeline comparison requires same camera and codec");
        }
        if (a.verificationPipelineId().equals(b.verificationPipelineId())) {
            throw new IllegalArgumentException(
                    "pipeline comparison requires different pipeline IDs");
        }
    }

    private static Set<CaptureModeTuple> immutableOrdered(
            Collection<CaptureModeTuple> values) {
        return Collections.unmodifiableSet(orderedSet(values));
    }

    private static TreeSet<CaptureModeTuple> orderedSet() {
        return new TreeSet<>(CameraModeOrder.tuples());
    }

    private static TreeSet<CaptureModeTuple> orderedSet(
            Collection<CaptureModeTuple> values) {
        TreeSet<CaptureModeTuple> result = orderedSet();
        for (CaptureModeTuple value : Objects.requireNonNull(values, "values")) {
            result.add(Objects.requireNonNull(value, "tuple"));
        }
        return result;
    }

    private record Decision(
            Recommendation recommendation, RecommendationReason reason) {}
}