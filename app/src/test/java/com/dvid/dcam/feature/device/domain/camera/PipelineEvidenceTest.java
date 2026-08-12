package com.dvid.dcam.feature.device.domain.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PipelineEvidenceTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("pipeline-a");
    private static final VideoMode UHD_30 = video(
            StandardResolutionLabel.UHD, 3840, 2160, 30);
    private static final VideoMode UHD_20 = video(
            StandardResolutionLabel.UHD, 3840, 2160, 20);
    private static final ImageMode HD = image(
            StandardResolutionLabel.HD, 1280, 720);
    private static final ImageMode SD = image(
            StandardResolutionLabel.SD, 720, 480);

    @Test void videoSoloFailureRemovesFpsAndDependentTuplesOnly() {
        CandidateKey video30 = videoKey(UHD_30);
        CandidateKey video20 = videoKey(UHD_20);
        CandidateKey imageHd = imageKey(HD);
        CandidateKey imageSd = imageKey(SD);
        CandidateKey tuple30Hd = tupleKey(UHD_30, HD);
        CandidateKey tuple30Sd = tupleKey(UHD_30, SD);
        CandidateKey tuple20Hd = tupleKey(UHD_20, HD);
        CandidateKey tuple20Sd = tupleKey(UHD_20, SD);
        List<CandidateKey> raw = List.of(
                video30, video20, imageHd, imageSd,
                tuple30Hd, tuple30Sd, tuple20Hd, tuple20Sd);

        PipelineEvidence evidence = evidence(raw, new CandidateEvidence(
                video30, VerificationOutcome.DEFINITIVE_UNSUPPORTED));
        Set<CandidateKey> effective = evidence.effectiveCandidates();

        assertFalse(effective.contains(video30));
        assertFalse(evidence.isEffective(video30));
        assertFalse(effective.contains(tuple30Hd));
        assertFalse(evidence.isEffective(tuple30Hd));
        assertFalse(effective.contains(tuple30Sd));
        assertTrue(evidence.isEffective(tuple20Hd));
        assertTrue(effective.containsAll(List.of(
                video20, imageHd, imageSd, tuple20Hd, tuple20Sd)));
        assertEquals(Set.copyOf(raw), evidence.rawFastCandidates());
    }

    @Test void verifiedVideoTuplePinsActualAndRemovesSiblingActuals() {
        VideoMode exact30 = video(StandardResolutionLabel.FHD, 1920, 1080, 30);
        VideoMode exact20 = video(StandardResolutionLabel.FHD, 1920, 1080, 20);
        VideoMode aligned30 = video(StandardResolutionLabel.FHD, 1920, 1088, 30);
        CandidateKey exact30Key = videoKey(exact30);
        CandidateKey exact20Key = videoKey(exact20);
        CandidateKey aligned30Key = videoKey(aligned30);
        CandidateKey exactTuple = tupleKey(exact30, HD);
        CandidateKey exact20Tuple = tupleKey(exact20, HD);
        CandidateKey alignedTuple = tupleKey(aligned30, HD);

        PipelineEvidence evidence = evidence(List.of(
                exact30Key, exact20Key, aligned30Key, imageKey(HD),
                exactTuple, exact20Tuple, alignedTuple),
                new CandidateEvidence(alignedTuple, VerificationOutcome.VERIFIED_PASS));

        assertFalse(evidence.effectiveCandidates().contains(exact30Key));
        assertFalse(evidence.effectiveCandidates().contains(exact20Key));
        assertFalse(evidence.effectiveCandidates().contains(exactTuple));
        assertFalse(evidence.isEffective(exactTuple));
        assertFalse(evidence.effectiveCandidates().contains(exact20Tuple));
        assertTrue(evidence.effectiveCandidates().contains(aligned30Key));
        assertTrue(evidence.effectiveCandidates().contains(alignedTuple));
        assertTrue(evidence.isEffective(alignedTuple));
    }

    @Test void staticSoloPassRetainsRankedFamilyChildren() {
        VideoMode exact = video(StandardResolutionLabel.FHD, 1920, 1080, 30);
        VideoMode aligned = video(StandardResolutionLabel.FHD, 1920, 1088, 30);
        CandidateKey exactKey = videoKey(exact);
        CandidateKey alignedKey = videoKey(aligned);
        PipelineEvidence evidence = evidence(List.of(
                exactKey, alignedKey, imageKey(HD),
                tupleKey(exact, HD), tupleKey(aligned, HD)),
                new CandidateEvidence(exactKey, VerificationOutcome.VERIFIED_PASS),
                new CandidateEvidence(alignedKey, VerificationOutcome.VERIFIED_PASS));

        assertTrue(evidence.effectiveCandidates().containsAll(List.of(
                exactKey, alignedKey, tupleKey(exact, HD), tupleKey(aligned, HD))));
    }

    @Test void allSiblingFailuresRemoveResolutionLabel() {
        VideoMode exact = video(StandardResolutionLabel.FHD, 1920, 1080, 30);
        VideoMode aligned = video(StandardResolutionLabel.FHD, 1920, 1088, 30);
        CandidateKey exactKey = videoKey(exact);
        CandidateKey alignedKey = videoKey(aligned);
        PipelineEvidence evidence = evidence(List.of(
                exactKey, alignedKey, imageKey(HD),
                tupleKey(exact, HD), tupleKey(aligned, HD)),
                new CandidateEvidence(exactKey,
                        VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                new CandidateEvidence(alignedKey,
                        VerificationOutcome.DEFINITIVE_UNSUPPORTED));

        assertTrue(evidence.effectiveCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.VIDEO)
                .noneMatch(candidate -> candidate.videoMode()
                        .map(mode -> mode.resolution().label()
                                == StandardResolutionLabel.FHD)
                        .orElse(false)));
    }

    @Test void verifiedImageTuplePinsActualAndRemovesSiblingActuals() {
        ImageMode exact = image(StandardResolutionLabel.FHD, 1920, 1080);
        ImageMode aligned = image(StandardResolutionLabel.FHD, 1920, 1088);
        CandidateKey exactKey = imageKey(exact);
        CandidateKey alignedKey = imageKey(aligned);
        CandidateKey exactTuple = tupleKey(UHD_30, exact);
        CandidateKey alignedTuple = tupleKey(UHD_30, aligned);

        PipelineEvidence evidence = evidence(List.of(
                videoKey(UHD_30), exactKey, alignedKey, exactTuple, alignedTuple),
                new CandidateEvidence(alignedTuple, VerificationOutcome.VERIFIED_PASS));

        assertTrue(evidence.effectiveCandidates().contains(exactKey));
        assertFalse(evidence.effectiveCandidates().contains(exactTuple));
        assertFalse(evidence.isEffective(exactTuple));
        assertTrue(evidence.effectiveCandidates().contains(alignedKey));
        assertTrue(evidence.effectiveCandidates().contains(alignedTuple));
        assertTrue(evidence.isEffective(alignedTuple));
    }

    @Test void allImageSiblingFailuresRemoveResolutionLabel() {
        ImageMode exact = image(StandardResolutionLabel.FHD, 1920, 1080);
        ImageMode aligned = image(StandardResolutionLabel.FHD, 1920, 1088);
        CandidateKey exactKey = imageKey(exact);
        CandidateKey alignedKey = imageKey(aligned);
        PipelineEvidence evidence = evidence(List.of(
                videoKey(UHD_30), exactKey, alignedKey,
                tupleKey(UHD_30, exact), tupleKey(UHD_30, aligned)),
                new CandidateEvidence(exactKey,
                        VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                new CandidateEvidence(alignedKey,
                        VerificationOutcome.DEFINITIVE_UNSUPPORTED));

        assertTrue(evidence.effectiveCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE)
                .noneMatch(candidate -> candidate.imageMode()
                        .map(mode -> mode.resolution().label()
                                == StandardResolutionLabel.FHD)
                        .orElse(false)));
    }

    @Test void imageSoloFailureRemovesOnlyStandaloneImageCandidate() {
        CandidateKey video30 = videoKey(UHD_30);
        CandidateKey video20 = videoKey(UHD_20);
        CandidateKey imageHd = imageKey(HD);
        CandidateKey imageSd = imageKey(SD);
        CandidateKey tuple30Hd = tupleKey(UHD_30, HD);
        CandidateKey tuple30Sd = tupleKey(UHD_30, SD);
        CandidateKey tuple20Hd = tupleKey(UHD_20, HD);
        CandidateKey tuple20Sd = tupleKey(UHD_20, SD);

        PipelineEvidence evidence = evidence(List.of(
                video30, video20, imageHd, imageSd,
                tuple30Hd, tuple30Sd, tuple20Hd, tuple20Sd),
                new CandidateEvidence(
                        imageHd, VerificationOutcome.DEFINITIVE_UNSUPPORTED));
        Set<CandidateKey> effective = evidence.effectiveCandidates();

        assertFalse(effective.contains(imageHd));
        assertTrue(effective.containsAll(List.of(
                video30, video20, imageSd,
                tuple30Hd, tuple30Sd, tuple20Hd, tuple20Sd)));
    }

    @Test void comboFailureRemovesExactCellOnly() {
        CandidateKey failed = tupleKey(UHD_30, HD);
        CandidateKey sameVideo = tupleKey(UHD_30, SD);
        CandidateKey sameImage = tupleKey(UHD_20, HD);

        PipelineEvidence evidence = evidence(
                List.of(failed, sameVideo, sameImage),
                new CandidateEvidence(
                        failed, VerificationOutcome.DEFINITIVE_UNSUPPORTED));

        assertEquals(Set.of(sameVideo, sameImage), evidence.effectiveCandidates());
    }

    @Test void nonDefinitiveOutcomesNeverPruneRawCandidates() {
        CandidateKey candidate = tupleKey(UHD_30, HD);
        for (VerificationOutcome outcome : List.of(
                VerificationOutcome.UNKNOWN,
                VerificationOutcome.VERIFIED_PASS,
                VerificationOutcome.TIMEOUT_UNKNOWN,
                VerificationOutcome.TRANSIENT_RETRYABLE,
                VerificationOutcome.GLOBAL_FAILURE,
                VerificationOutcome.BLOCKED_EXTERNAL,
                VerificationOutcome.STORAGE_BLOCKED,
                VerificationOutcome.CANCELLED_UNKNOWN)) {
            PipelineEvidence evidence = evidence(
                    List.of(candidate), new CandidateEvidence(candidate, outcome));
            assertEquals(Set.of(candidate), evidence.effectiveCandidates(), outcome.name());
        }
    }

    @Test void soloFailureDoesNotDefineTupleOutcome() {
        CandidateKey video = videoKey(UHD_30);
        CandidateKey tuple = tupleKey(UHD_30, HD);
        PipelineEvidence evidence = evidence(List.of(video, tuple),
                new CandidateEvidence(
                        video, VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                new CandidateEvidence(tuple, VerificationOutcome.VERIFIED_PASS));

        assertEquals(VerificationOutcome.VERIFIED_PASS,
                evidence.outcome(new CaptureModeTuple(UHD_30, HD)));
    }
    @Test void tupleOnlyRawCandidatesDefineSoloEvidenceScope() {
        CandidateKey tuple = tupleKey(UHD_30, HD);
        PipelineEvidence evidence = evidence(List.of(tuple));

        assertTrue(evidence.hasRawScope(videoKey(UHD_30)));
        assertTrue(evidence.hasRawScope(imageKey(HD)));
    }

    @Test void pipelineUnavailableIsSeparateFromTupleFailure() {
        CandidateKey candidate = tupleKey(UHD_30, HD);
        PipelineEvidence evidence = new PipelineEvidence(
                CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.UNAVAILABLE,
                List.of(candidate), List.of());

        assertEquals(Set.of(candidate), evidence.rawFastCandidates());
        assertTrue(evidence.candidateEvidence().isEmpty());
        assertTrue(evidence.effectiveCandidates().isEmpty());
    }

    private static PipelineEvidence evidence(List<CandidateKey> raw,
            CandidateEvidence... facts) {
        return new PipelineEvidence(
                CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, raw, List.of(facts));
    }

    private static CandidateKey videoKey(VideoMode video) {
        return CandidateKey.forVideo(CAMERA, VideoCodec.H264, PIPELINE, video);
    }

    private static CandidateKey imageKey(ImageMode image) {
        return CandidateKey.forImage(CAMERA, VideoCodec.H264, PIPELINE, image);
    }

    private static CandidateKey tupleKey(VideoMode video, ImageMode image) {
        return CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(video, image));
    }

    private static VideoMode video(StandardResolutionLabel label,
            int width, int height, int framesPerSecond) {
        return new VideoMode(new StandardResolution(
                label, new CameraResolution(width, height)), framesPerSecond);
    }

    private static ImageMode image(StandardResolutionLabel label, int width, int height) {
        return new ImageMode(new StandardResolution(
                label, new CameraResolution(width, height)));
    }
}