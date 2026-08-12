package com.dvid.dcam.feature.device.domain.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

final class PipelineComparisonTest {
    private static final CameraId CAMERA = new CameraId("0");

    @Test void strictSupersetIsOnlyBroaderClassification() {
        CaptureModeTuple low = tuple(
                StandardResolutionLabel.HD, 1280, 720, 30,
                StandardResolutionLabel.SD, 720, 480);
        CaptureModeTuple high = tuple(
                StandardResolutionLabel.FHD, 1920, 1080, 30,
                StandardResolutionLabel.HD, 1280, 720);

        PipelineComparison report = compare(List.of(low, high),
                Map.of(low, VerificationOutcome.VERIFIED_PASS,
                        high, VerificationOutcome.VERIFIED_PASS),
                Map.of(low, VerificationOutcome.VERIFIED_PASS,
                        high, VerificationOutcome.DEFINITIVE_UNSUPPORTED));

        assertEquals(PipelineComparison.Status.COMPLETE, report.status());
        assertEquals(PipelineComparison.Coverage.A_BROADER, report.coverage());
        assertEquals(PipelineComparison.Recommendation.PIPELINE_A,
                report.recommendation());
        assertEquals(PipelineComparison.RecommendationReason.STRICT_SUPERSET,
                report.recommendationReason());
        assertEquals(java.util.Set.of(low), report.intersection());
        assertEquals(java.util.Set.of(high), report.aOnly());
        assertTrue(report.bOnly().isEmpty());
    }

    @Test void incomparableCoverageUsesBestTupleWithoutClaimingBroader() {
        CaptureModeTuple low = tuple(
                StandardResolutionLabel.HD, 1280, 720, 30,
                StandardResolutionLabel.SD, 720, 480);
        CaptureModeTuple high = tuple(
                StandardResolutionLabel.FHD, 1920, 1080, 30,
                StandardResolutionLabel.HD, 1280, 720);

        PipelineComparison report = compare(List.of(low, high),
                Map.of(low, VerificationOutcome.VERIFIED_PASS,
                        high, VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                Map.of(low, VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                        high, VerificationOutcome.VERIFIED_PASS));

        assertEquals(PipelineComparison.Coverage.INCOMPARABLE, report.coverage());
        assertEquals(PipelineComparison.Recommendation.PIPELINE_B,
                report.recommendation());
        assertEquals(PipelineComparison.RecommendationReason.BEST_TUPLE,
                report.recommendationReason());
        assertEquals(java.util.Set.of(low), report.aOnly());
        assertEquals(java.util.Set.of(high), report.bOnly());
    }

    @Test void passCountPerformanceAndFinalTieFollowDeterministicOrder() {
        CaptureModeTuple best = tuple(
                StandardResolutionLabel.FHD, 1920, 1080, 30,
                StandardResolutionLabel.HD, 1280, 720);
        CaptureModeTuple aLow = tuple(
                StandardResolutionLabel.HD, 1280, 720, 30,
                StandardResolutionLabel.SD, 720, 480);
        CaptureModeTuple aExtra = tuple(
                StandardResolutionLabel.SD, 720, 480, 30,
                StandardResolutionLabel.SD, 720, 480);
        CaptureModeTuple bLow = tuple(
                StandardResolutionLabel.HD, 1280, 720, 20,
                StandardResolutionLabel.SD, 720, 480);
        List<CaptureModeTuple> universe = List.of(best, aLow, aExtra, bLow);
        Map<CaptureModeTuple, VerificationOutcome> aOutcomes = Map.of(
                best, VerificationOutcome.VERIFIED_PASS,
                aLow, VerificationOutcome.VERIFIED_PASS,
                aExtra, VerificationOutcome.VERIFIED_PASS,
                bLow, VerificationOutcome.DEFINITIVE_UNSUPPORTED);
        Map<CaptureModeTuple, VerificationOutcome> bOutcomes = Map.of(
                best, VerificationOutcome.VERIFIED_PASS,
                aLow, VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                aExtra, VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                bLow, VerificationOutcome.VERIFIED_PASS);

        PipelineComparison passCount = compare(universe, aOutcomes, bOutcomes);
        assertEquals(PipelineComparison.Coverage.INCOMPARABLE, passCount.coverage());
        assertEquals(PipelineComparison.Recommendation.PIPELINE_A,
                passCount.recommendation());
        assertEquals(PipelineComparison.RecommendationReason.PASS_COUNT,
                passCount.recommendationReason());

        Map<CaptureModeTuple, VerificationOutcome> equalA = Map.of(
                best, VerificationOutcome.VERIFIED_PASS,
                aLow, VerificationOutcome.VERIFIED_PASS,
                bLow, VerificationOutcome.DEFINITIVE_UNSUPPORTED);
        Map<CaptureModeTuple, VerificationOutcome> equalB = Map.of(
                best, VerificationOutcome.VERIFIED_PASS,
                aLow, VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                bLow, VerificationOutcome.VERIFIED_PASS);
        PipelineComparison performance = compare(
                List.of(best, aLow, bLow), equalA, equalB, 120, 100);
        assertEquals(PipelineComparison.Recommendation.PIPELINE_B,
                performance.recommendation());
        assertEquals(PipelineComparison.RecommendationReason.PERFORMANCE,
                performance.recommendationReason());

        PipelineComparison finalTie = compare(
                List.of(best, aLow, bLow), equalA, equalB);
        assertEquals(PipelineComparison.Recommendation.PIPELINE_A,
                finalTie.recommendation());
        assertEquals(PipelineComparison.RecommendationReason.FINAL_TIE_A,
                finalTie.recommendationReason());
    }

    @Test void bothFailAndUnknownRemainVisibleAndComparisonStaysIncomplete() {
        CaptureModeTuple failed = tuple(
                StandardResolutionLabel.HD, 1280, 720, 30,
                StandardResolutionLabel.SD, 720, 480);
        CaptureModeTuple unknown = tuple(
                StandardResolutionLabel.FHD, 1920, 1080, 30,
                StandardResolutionLabel.HD, 1280, 720);

        PipelineComparison report = compare(List.of(failed, unknown),
                Map.of(failed, VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                        unknown, VerificationOutcome.TIMEOUT_UNKNOWN),
                Map.of(failed, VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                        unknown, VerificationOutcome.VERIFIED_PASS));

        assertEquals(PipelineComparison.Status.INCOMPLETE, report.status());
        assertEquals(PipelineComparison.Coverage.INCOMPLETE, report.coverage());
        assertEquals(PipelineComparison.Recommendation.NONE,
                report.recommendation());
        assertEquals(java.util.Set.of(failed), report.bothFail());
        assertEquals(java.util.Set.of(unknown), report.unknown());
    }

    @Test void soloFailuresCompleteSkippedDependentTupleComparison() {
        CaptureModeTuple tuple = tuple(
                StandardResolutionLabel.FHD, 1920, 1080, 30,
                StandardResolutionLabel.HD, 1280, 720);
        PipelineEvidence pipelineA = evidenceWithSoloFailure(
                "pipeline-a", tuple, CandidateKey.Kind.VIDEO);
        PipelineEvidence pipelineB = evidenceWithSoloFailure(
                "pipeline-b", tuple, CandidateKey.Kind.IMAGE);

        PipelineComparison report = PipelineComparison.compare(List.of(tuple),
                PipelineComparisonInput.withoutPerformance(pipelineA, true),
                PipelineComparisonInput.withoutPerformance(pipelineB, true));

        assertEquals(PipelineComparison.Status.INCOMPLETE, report.status());
        assertTrue(report.bothFail().isEmpty());
        assertEquals(java.util.Set.of(tuple), report.unknown());
    }
    @Test void cancelledRunCannotPublishRecommendation() {
        CaptureModeTuple tuple = tuple(
                StandardResolutionLabel.HD, 1280, 720, 30,
                StandardResolutionLabel.SD, 720, 480);
        PipelineComparisonInput a = new PipelineComparisonInput(
                evidence("pipeline-a", Map.of(
                        tuple, VerificationOutcome.VERIFIED_PASS)),
                false, OptionalLong.empty());
        PipelineComparisonInput b = PipelineComparisonInput.withoutPerformance(
                evidence("pipeline-b", Map.of(
                        tuple, VerificationOutcome.VERIFIED_PASS)), true);

        PipelineComparison report = PipelineComparison.compare(List.of(tuple), a, b);

        assertEquals(PipelineComparison.Status.INCOMPLETE, report.status());
        assertEquals(PipelineComparison.Recommendation.NONE,
                report.recommendation());
    }

    private static PipelineComparison compare(
            Collection<CaptureModeTuple> universe,
            Map<CaptureModeTuple, VerificationOutcome> aOutcomes,
            Map<CaptureModeTuple, VerificationOutcome> bOutcomes) {
        return PipelineComparison.compare(universe,
                PipelineComparisonInput.withoutPerformance(
                        evidence("pipeline-a", aOutcomes), true),
                PipelineComparisonInput.withoutPerformance(
                        evidence("pipeline-b", bOutcomes), true));
    }

    private static PipelineComparison compare(
            Collection<CaptureModeTuple> universe,
            Map<CaptureModeTuple, VerificationOutcome> aOutcomes,
            Map<CaptureModeTuple, VerificationOutcome> bOutcomes,
            long aMedian,
            long bMedian) {
        return PipelineComparison.compare(universe,
                new PipelineComparisonInput(
                        evidence("pipeline-a", aOutcomes), true,
                        OptionalLong.of(aMedian)),
                new PipelineComparisonInput(
                        evidence("pipeline-b", bOutcomes), true,
                        OptionalLong.of(bMedian)));
    }

    private static PipelineEvidence evidence(String pipelineId,
            Map<CaptureModeTuple, VerificationOutcome> outcomes) {
        VerificationPipelineId pipeline = new VerificationPipelineId(pipelineId);
        List<CandidateKey> raw = new ArrayList<>();
        List<CandidateEvidence> facts = new ArrayList<>();
        for (var entry : outcomes.entrySet()) {
            CandidateKey key = CandidateKey.forTuple(
                    CAMERA, VideoCodec.H264, pipeline, entry.getKey());
            raw.add(key);
            facts.add(new CandidateEvidence(key, entry.getValue()));
        }
        return new PipelineEvidence(
                CAMERA, VideoCodec.H264, pipeline,
                PipelineAvailability.AVAILABLE, raw, facts);
    }

    private static PipelineEvidence evidenceWithSoloFailure(String pipelineId,
            CaptureModeTuple tuple, CandidateKey.Kind kind) {
        VerificationPipelineId pipeline = new VerificationPipelineId(pipelineId);
        CandidateKey solo = kind == CandidateKey.Kind.VIDEO
                ? CandidateKey.forVideo(CAMERA, VideoCodec.H264,
                pipeline, tuple.videoMode())
                : CandidateKey.forImage(CAMERA, VideoCodec.H264,
                pipeline, tuple.imageMode());
        CandidateKey combined = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, pipeline, tuple);
        return new PipelineEvidence(CAMERA, VideoCodec.H264, pipeline,
                PipelineAvailability.AVAILABLE, List.of(solo, combined),
                List.of(new CandidateEvidence(
                        solo, VerificationOutcome.DEFINITIVE_UNSUPPORTED)));
    }
    private static CaptureModeTuple tuple(
            StandardResolutionLabel videoLabel,
            int videoWidth,
            int videoHeight,
            int framesPerSecond,
            StandardResolutionLabel imageLabel,
            int imageWidth,
            int imageHeight) {
        VideoMode video = new VideoMode(new StandardResolution(
                videoLabel, new CameraResolution(videoWidth, videoHeight)),
                framesPerSecond);
        ImageMode image = new ImageMode(new StandardResolution(
                imageLabel, new CameraResolution(imageWidth, imageHeight)));
        return new CaptureModeTuple(video, image);
    }
}