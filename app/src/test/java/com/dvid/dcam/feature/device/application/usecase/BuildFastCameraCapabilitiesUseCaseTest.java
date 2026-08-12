package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CodecProfileLevel;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.ConcurrentCameraCombination;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.ConcurrentCameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.DeviceIdentity;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderSizeCapabilities;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderVideoCapabilities;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.FrameRateRange;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.IntegerRange;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.RawCatalog;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.StreamSize;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.BatchResult;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.CameraResult;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.Completion;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.Request;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.CameraFastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.RunCompletion;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

final class BuildFastCameraCapabilitiesUseCaseTest {
    private static final VerificationPipelineId PIPELINE_A =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");
    private static final VerificationPipelineId PIPELINE_B =
            new VerificationPipelineId("b-camera2-egl-fanout-v1");
    private static final CameraResolution SD = new CameraResolution(640, 480);
    private static final CameraResolution HD = new CameraResolution(1280, 720);
    private static final CameraResolution FHD = new CameraResolution(1920, 1080);
    private static final CameraResolution FHD_ALIGNED = new CameraResolution(1920, 1088);
    private static final CameraResolution FHD_WIDTH_ALIGNED =
            new CameraResolution(1904, 1080);
    private static final CameraResolution FHD_HEIGHT_ALIGNED =
            new CameraResolution(1920, 1064);
    private static final CameraResolution UHD = new CameraResolution(3840, 2160);

    @Test void mapsCandidatesPrunesFpsAndBuildsFullCartesianForOneCamera() {
        CameraFacts camera = camera("0", List.of(FHD_ALIGNED), List.of(SD, FHD),
                List.of(20, 25, 30, 60));
        RawCatalog catalog = catalog(List.of(camera), false, List.of());
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);
        BuildFastCameraCapabilitiesUseCase useCase = useCase(catalog);

        var result = useCase.execute(probe);

        assertTrue(result.complete());
        assertTrue(result.authoritativeSnapshot().isPresent());
        Request request = probe.requests().get(0).get(0);
        assertEquals(List.of(30, 60), request.videoModes().stream()
                .map(VideoMode::framesPerSecond).toList());
        assertEquals(StandardResolutionLabel.FHD,
                request.videoModes().get(0).resolution().label());
        assertEquals(FHD_ALIGNED, request.videoModes().get(0).resolution().actual());
        assertEquals(List.of(StandardResolutionLabel.SD, StandardResolutionLabel.FHD),
                request.imageModes().stream()
                        .map(mode -> mode.resolution().label()).toList());

        CameraFastSnapshot snapshot = result.authoritativeSnapshot().orElseThrow()
                .cameras().get(0);
        assertEquals(4, snapshot.evidence().rawFastCandidates().size());
        assertEquals(4, tupleCandidates(snapshot.evidence()).size());
        assertEquals(List.of(new CameraId("0")),
                result.authoritativeSnapshot().orElseThrow().autoCameraOrder());
        assertTrue(result.benchmark().allFastReadyMillis().isPresent());
        assertFalse(result.benchmark().rounds().get(0).concurrent());
    }

    @Test void reportsFastBuildProgressForEachCamera() {
        RawCatalog catalog = catalog(cameras("0", "1"), false, List.of());
        List<BuildFastCameraCapabilitiesUseCase.Progress> progress = new ArrayList<>();

        var result = useCase(catalog).execute(
                FakeProbe.supporting(PIPELINE_A, tuple -> true), progress::add);

        assertTrue(result.complete());
        assertEquals(List.of(new CameraId("0"), new CameraId("1")), progress.stream()
                .map(BuildFastCameraCapabilitiesUseCase.Progress::cameraId).toList());
        assertEquals(List.of(1, 2), progress.stream()
                .map(BuildFastCameraCapabilitiesUseCase.Progress::completed).toList());
        assertEquals(List.of(2, 2), progress.stream()
                .map(BuildFastCameraCapabilitiesUseCase.Progress::total).toList());
    }

    @Test void rankedChildOrderSurvivesRequestAndFastSnapshot() {
        CameraFacts camera = camera("0",
                List.of(FHD_HEIGHT_ALIGNED, FHD_WIDTH_ALIGNED),
                List.of(FHD_HEIGHT_ALIGNED, FHD_WIDTH_ALIGNED), List.of(30));
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog(List.of(camera), false, List.of())).execute(probe);

        assertTrue(result.complete());
        Request request = probe.requests().get(0).get(0);
        List<CameraResolution> expected = List.of(
                FHD_WIDTH_ALIGNED, FHD_HEIGHT_ALIGNED);
        assertEquals(expected, request.videoModes().stream()
                .map(mode -> mode.resolution().actual()).toList());
        assertEquals(expected, request.imageModes().stream()
                .map(mode -> mode.resolution().actual()).toList());
        CameraFastSnapshot snapshot = result.authoritativeSnapshot().orElseThrow()
                .cameras().get(0);
        assertEquals(expected, snapshot.videoModes().stream()
                .map(mode -> mode.resolution().actual()).toList());
        assertEquals(expected, snapshot.imageModes().stream()
                .map(mode -> mode.resolution().actual()).toList());
        assertEquals(4, tupleCandidates(snapshot.evidence()).size());
    }

    @Test void fallsBackToAlignedResolutionWhenExactEncoderSizeIsUnsupported() {
        CameraFacts camera = camera("0", List.of(FHD, FHD_ALIGNED), List.of(FHD),
                List.of(30));
        EncoderFacts encoder = encoderWithSizeSupport(Map.of(
                FHD, false,
                FHD_ALIGNED, true));
        RawCatalog catalog = new RawCatalog(device(), List.of(camera),
                new ConcurrentCameraFacts(false, List.of()), List.of(encoder),
                "hardware-signature-input");
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog).execute(probe);

        assertTrue(result.complete());
        Request request = probe.requests().get(0).get(0);
        assertEquals(1, request.videoModes().size());
        assertEquals(StandardResolutionLabel.FHD,
                request.videoModes().get(0).resolution().label());
        assertEquals(FHD_ALIGNED, request.videoModes().get(0).resolution().actual());
        assertEquals(30, request.videoModes().get(0).framesPerSecond());
    }

    @Test void retainsExactAndAlignedChildrenWhenEncoderSupportsBoth() {
        CameraFacts camera = camera("0", List.of(FHD_ALIGNED, FHD), List.of(FHD),
                List.of(30));
        EncoderFacts encoder = encoderWithSizeSupport(Map.of(
                FHD, true,
                FHD_ALIGNED, true));
        RawCatalog catalog = new RawCatalog(device(), List.of(camera),
                new ConcurrentCameraFacts(false, List.of()), List.of(encoder),
                "hardware-signature-input");
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog).execute(probe);

        assertTrue(result.complete());
        assertEquals(List.of(FHD, FHD_ALIGNED), probe.requests().get(0).get(0)
                .videoModes().stream()
                .map(mode -> mode.resolution().actual()).toList());
    }

    @Test void fullConcurrentInventoryUsesOneFastRound() {
        RawCatalog catalog = catalog(cameras("0", "1", "2"), true,
                List.of(combination("0", "1", "2")));
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog).execute(probe);

        assertTrue(result.complete());
        assertEquals(List.of(List.of(new CameraId("0"), new CameraId("1"),
                new CameraId("2"))), probe.cameraCalls());
        assertEquals(1, result.benchmark().rounds().size());
        assertTrue(result.benchmark().rounds().get(0).concurrent());
    }

    @Test void subsetSchedulingUsesStableCameraIdTieBreak() {
        RawCatalog catalog = catalog(cameras("0", "1", "2", "3"), true,
                List.of(combination("2", "3"), combination("0", "1")));
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog).execute(probe);

        assertTrue(result.complete());
        assertEquals(List.of(
                List.of(new CameraId("0"), new CameraId("1")),
                List.of(new CameraId("2"), new CameraId("3"))),
                probe.cameraCalls());
        assertTrue(result.benchmark().rounds().stream()
                .allMatch(BuildFastCameraCapabilitiesUseCase.RoundTiming::concurrent));
    }

    @Test void subsetSchedulingPrefersMostUnscannedCameras() {
        RawCatalog catalog = catalog(cameras("0", "1", "2", "3", "4"), true,
                List.of(combination("0", "4"), combination("1", "2", "3")));
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog).execute(probe);

        assertTrue(result.complete());
        assertEquals(List.of(
                List.of(new CameraId("1"), new CameraId("2"), new CameraId("3")),
                List.of(new CameraId("0"), new CameraId("4"))),
                probe.cameraCalls());
    }

    @Test void queryUnavailableScansEveryCameraAsSingleton() {
        RawCatalog catalog = catalog(cameras("0", "1", "2"), false,
                List.of(combination("0", "1", "2")));
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog).execute(probe);

        assertTrue(result.complete());
        assertEquals(List.of(
                List.of(new CameraId("0")),
                List.of(new CameraId("1")),
                List.of(new CameraId("2"))), probe.cameraCalls());
        assertTrue(result.benchmark().rounds().stream()
                .noneMatch(BuildFastCameraCapabilitiesUseCase.RoundTiming::concurrent));
    }

    @Test void failedBatchRetriesSingletonsAndDisablesCombinationForProcess() {
        RawCatalog catalog = catalog(cameras("0", "1"), true,
                List.of(combination("0", "1")));
        BuildFastCameraCapabilitiesUseCase useCase = useCase(catalog);
        FakeProbe firstProbe = new FakeProbe(PIPELINE_A, (requests, call) -> {
            if (requests.size() > 1) {
                return new BatchResult(Completion.INCOMPLETE_TRANSIENT, List.of(),
                        true, 3, "max_cameras_in_use");
            }
            return complete(PIPELINE_A, requests, tuple -> true);
        });

        var first = useCase.execute(firstProbe);

        assertTrue(first.complete());
        assertEquals(List.of(
                List.of(new CameraId("0"), new CameraId("1")),
                List.of(new CameraId("0")),
                List.of(new CameraId("1"))), firstProbe.cameraCalls());
        assertEquals(List.of(false, true, true), first.benchmark().rounds().stream()
                .map(round -> !round.concurrent() && round.singletonRetry()).toList());

        FakeProbe secondProbe = FakeProbe.supporting(PIPELINE_A, tuple -> true);
        var second = useCase.execute(secondProbe);

        assertTrue(second.complete());
        assertEquals(List.of(
                List.of(new CameraId("0")),
                List.of(new CameraId("1"))), secondProbe.cameraCalls());
    }
    @Test void globalAndBlockedConcurrentFailuresNeverRetryOrPublish() {
        RawCatalog catalog = catalog(cameras("0", "1"), true,
                List.of(combination("0", "1")));
        for (boolean cleanupComplete : List.of(true, false)) {
            FakeProbe global = new FakeProbe(PIPELINE_A, (requests, call) ->
                    new BatchResult(Completion.INCOMPLETE_GLOBAL, List.of(),
                            cleanupComplete, 2, "camera_service"));

            var globalResult = useCase(catalog).execute(global);

            assertEquals(RunCompletion.INCOMPLETE_GLOBAL, globalResult.completion());
            assertTrue(globalResult.authoritativeSnapshot().isEmpty());
            assertEquals(1, global.cameraCalls().size());
        }

        for (boolean cleanupComplete : List.of(true, false)) {
            FakeProbe blocked = new FakeProbe(PIPELINE_A, (requests, call) ->
                    new BatchResult(Completion.BLOCKED_EXTERNAL, List.of(),
                            cleanupComplete, 2, "permission"));

            var blockedResult = useCase(catalog).execute(blocked);

            assertEquals(RunCompletion.INCOMPLETE_BLOCKED_EXTERNAL,
                    blockedResult.completion());
            assertTrue(blockedResult.authoritativeSnapshot().isEmpty());
            assertEquals(1, blocked.cameraCalls().size());
        }
    }

    @Test void zeroSupportedTuplesDoNotInventSoloEvidence() {
        RawCatalog catalog = catalog(cameras("0"), false, List.of());
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> false);

        var result = useCase(catalog).execute(probe);

        CameraFastSnapshot snapshot = result.authoritativeSnapshot()
                .orElseThrow().cameras().get(0);
        assertFalse(snapshot.videoModes().isEmpty());
        assertFalse(snapshot.imageModes().isEmpty());
        assertTrue(snapshot.evidence().rawFastCandidates().isEmpty());
        assertTrue(snapshot.bestEffectiveTuple().isEmpty());
    }

    @Test void disabledBatchIsIsolatedByPipelineAndHardwareSignature() {
        RawCatalog firstCatalog = catalog(cameras("0", "1"), true,
                List.of(combination("0", "1")));
        MutableCatalogSource source = new MutableCatalogSource(firstCatalog);
        BuildFastCameraCapabilitiesUseCase useCase =
                new BuildFastCameraCapabilitiesUseCase(source, new TestLogger());
        FakeProbe pipelineA = new FakeProbe(PIPELINE_A, (requests, call) -> {
            if (requests.size() > 1) {
                return new BatchResult(Completion.INCOMPLETE_TRANSIENT, List.of(),
                        true, 2, "max_cameras_in_use");
            }
            return complete(PIPELINE_A, requests, tuple -> true);
        });
        assertTrue(useCase.execute(pipelineA).complete());

        FakeProbe pipelineB = FakeProbe.supporting(PIPELINE_B, tuple -> true);
        assertTrue(useCase.execute(pipelineB).complete());
        assertEquals(List.of(List.of(new CameraId("0"), new CameraId("1"))),
                pipelineB.cameraCalls());

        source.catalog = withSignature(firstCatalog, "changed-hardware-signature");
        FakeProbe refreshedA = FakeProbe.supporting(PIPELINE_A, tuple -> true);
        assertTrue(useCase.execute(refreshedA).complete());
        assertEquals(List.of(List.of(new CameraId("0"), new CameraId("1"))),
                refreshedA.cameraCalls());
    }

    @Test void rankingUsesVideoAreaThenFpsThenImageAreaAndCameraId() {
        CameraFacts camera0 = camera("0", List.of(FHD), List.of(HD), List.of(30));
        CameraFacts camera1 = camera("1", List.of(HD), List.of(UHD), List.of(60));
        CameraFacts camera2 = camera("2", List.of(FHD), List.of(FHD), List.of(30));
        CameraFacts camera3 = camera("3", List.of(FHD), List.of(HD), List.of(30));
        RawCatalog catalog = catalog(
                List.of(camera0, camera1, camera2, camera3), false, List.of());
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog).execute(probe);

        assertEquals(List.of(new CameraId("2"), new CameraId("0"),
                new CameraId("3"), new CameraId("1")),
                result.authoritativeSnapshot().orElseThrow().autoCameraOrder());
    }

    @Test void mainCameraPrefersHighestCapabilityBackFacingCamera() {
        CameraFacts strongerFront = cameraWithFacing(
                "0", List.of(UHD), List.of(UHD), List.of(60), 0);
        CameraFacts weakerBack = cameraWithFacing(
                "1", List.of(HD), List.of(HD), List.of(30), 1);

        var result = useCase(catalog(List.of(strongerFront, weakerBack), false, List.of()))
                .execute(FakeProbe.supporting(PIPELINE_A, tuple -> true));

        assertEquals(List.of(new CameraId("1"), new CameraId("0")),
                result.authoritativeSnapshot().orElseThrow().autoCameraOrder());
    }

    @Test void mainCameraUsesHighestCapabilityAmongMultipleBackFacingCameras() {
        CameraFacts strongestFront = cameraWithFacing(
                "0", List.of(UHD), List.of(UHD), List.of(60), 0);
        CameraFacts weakerBack = cameraWithFacing(
                "1", List.of(HD), List.of(HD), List.of(30), 1);
        CameraFacts strongerBack = cameraWithFacing(
                "2", List.of(FHD), List.of(FHD), List.of(30), 1);

        var result = useCase(catalog(
                List.of(strongestFront, weakerBack, strongerBack), false, List.of()))
                .execute(FakeProbe.supporting(PIPELINE_A, tuple -> true));

        assertEquals(new CameraId("2"),
                result.authoritativeSnapshot().orElseThrow().autoCameraOrder().get(0));
    }

    @Test void mainCameraFallsBackToHighestCapabilityWhenNoBackFacingCameraExists() {
        CameraFacts external = cameraWithFacing(
                "0", List.of(UHD), List.of(UHD), List.of(60), 2);
        CameraFacts front = cameraWithFacing(
                "1", List.of(FHD), List.of(FHD), List.of(30), 0);

        var result = useCase(catalog(List.of(external, front), false, List.of()))
                .execute(FakeProbe.supporting(PIPELINE_A, tuple -> true));

        assertEquals(new CameraId("0"),
                result.authoritativeSnapshot().orElseThrow().autoCameraOrder().get(0));
    }

    @Test void unknownFacingUsesOverallCapabilityFallbackWhenNoBackFacingCameraExists() {
        CameraFacts unknown = cameraWithFacing(
                "0", List.of(UHD), List.of(UHD), List.of(60), null);
        CameraFacts front = cameraWithFacing(
                "1", List.of(FHD), List.of(FHD), List.of(30), 0);

        var result = useCase(catalog(List.of(unknown, front), false, List.of()))
                .execute(FakeProbe.supporting(PIPELINE_A, tuple -> true));

        assertEquals(new CameraId("0"),
                result.authoritativeSnapshot().orElseThrow().autoCameraOrder().get(0));
    }

    @Test void noCandidatesRemainAuthoritativeInventoryButRankLast() {
        CameraFacts supported = camera("0", List.of(HD), List.of(HD), List.of(30));
        CameraFacts empty = camera("1", List.of(), List.of(), List.of());
        RawCatalog catalog = catalog(List.of(empty, supported), false, List.of());
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog).execute(probe);

        assertTrue(result.complete());
        assertEquals(2, result.authoritativeSnapshot().orElseThrow().cameras().size());
        assertEquals(List.of(new CameraId("0"), new CameraId("1")),
                result.authoritativeSnapshot().orElseThrow().autoCameraOrder());
        CameraFastSnapshot emptySnapshot = cameraSnapshot(result, "1");
        assertTrue(emptySnapshot.videoModes().isEmpty());
        assertTrue(emptySnapshot.imageModes().isEmpty());
        assertTrue(emptySnapshot.evidence().rawFastCandidates().isEmpty());
        assertTrue(emptySnapshot.bestEffectiveTuple().isEmpty());
    }

    @Test void cancellationReturnsIncompleteWithoutAuthoritativeSnapshot() {
        RawCatalog catalog = catalog(cameras("0", "1"), true,
                List.of(combination("0", "1")));
        FakeProbe probe = new FakeProbe(PIPELINE_A, (requests, call) ->
                new BatchResult(Completion.CANCELLED, List.of(), true,
                        1, "cancelled"));

        var result = useCase(catalog).execute(probe);

        assertEquals(RunCompletion.INCOMPLETE_CANCELLED, result.completion());
        assertTrue(result.authoritativeSnapshot().isEmpty());
        assertTrue(result.benchmark().allFastReadyMillis().isEmpty());
    }

    @Test void requestedCancellationStopsBeforeProbeAndPublishesNothing() {
        RawCatalog catalog = catalog(cameras("0"), false, List.of());
        FakeProbe probe = FakeProbe.supporting(PIPELINE_A, tuple -> true);

        var result = useCase(catalog).execute(probe, () -> true);

        assertEquals(RunCompletion.INCOMPLETE_CANCELLED, result.completion());
        assertTrue(result.authoritativeSnapshot().isEmpty());
        assertTrue(probe.cameraCalls().isEmpty());
    }

    @Test void singletonGlobalFailureKeepsPartialDataNonAuthoritative() {
        RawCatalog catalog = catalog(cameras("0", "1"), false, List.of());
        FakeProbe probe = new FakeProbe(PIPELINE_A, (requests, call) -> {
            if (call == 1) return complete(PIPELINE_A, requests, tuple -> true);
            return new BatchResult(Completion.INCOMPLETE_GLOBAL, List.of(),
                    true, 2, "camera_service");
        });

        var result = useCase(catalog).execute(probe);

        assertEquals(RunCompletion.INCOMPLETE_GLOBAL, result.completion());
        assertTrue(result.authoritativeSnapshot().isEmpty());
        assertEquals(List.of(new CameraId("0")), result.partialCameras().stream()
                .map(camera -> camera.cameraFacts().cameraId()).toList());
    }

    @Test void catalogFailureReturnsIncompleteWithoutEmptyAuthority() {
        CameraCatalogSource source = () -> {
            throw new CameraCatalogSource.CatalogException(
                    "catalog failure", new IllegalStateException("boom"));
        };
        BuildFastCameraCapabilitiesUseCase useCase =
                new BuildFastCameraCapabilitiesUseCase(source, new TestLogger());

        var result = useCase.execute(FakeProbe.supporting(PIPELINE_A, tuple -> true));

        assertEquals(RunCompletion.INCOMPLETE_GLOBAL, result.completion());
        assertTrue(result.authoritativeSnapshot().isEmpty());
        assertTrue(result.partialCameras().isEmpty());
    }

    @Test void pipelineUnavailableAndPipelineBFactsStaySeparate() {
        RawCatalog catalog = catalog(cameras("0"), false, List.of());
        FakeProbe pipelineA = new FakeProbe(PIPELINE_A, (requests, call) -> {
            Request request = requests.get(0);
            PipelineEvidence evidence = new PipelineEvidence(
                    request.cameraId(), VideoCodec.H264, PIPELINE_A,
                    PipelineAvailability.UNAVAILABLE, List.of(), List.of());
            return new BatchResult(Completion.COMPLETE,
                    List.of(new CameraResult(evidence,
                            Completion.PIPELINE_UNAVAILABLE, 2, "unavailable")),
                    true, 2, "complete");
        });
        FakeProbe pipelineB = FakeProbe.supporting(PIPELINE_B, tuple -> true);
        BuildFastCameraCapabilitiesUseCase useCase = useCase(catalog);

        var resultA = useCase.execute(pipelineA);
        var resultB = useCase.execute(pipelineB);

        CameraFastSnapshot snapshotA = resultA.authoritativeSnapshot()
                .orElseThrow().cameras().get(0);
        CameraFastSnapshot snapshotB = resultB.authoritativeSnapshot()
                .orElseThrow().cameras().get(0);
        assertEquals(PIPELINE_A, snapshotA.evidence().verificationPipelineId());
        assertEquals(PipelineAvailability.UNAVAILABLE,
                snapshotA.evidence().availability());
        assertTrue(snapshotA.evidence().rawFastCandidates().isEmpty());
        assertEquals(PIPELINE_B, snapshotB.evidence().verificationPipelineId());
        assertEquals(PipelineAvailability.AVAILABLE,
                snapshotB.evidence().availability());
        assertFalse(snapshotB.evidence().rawFastCandidates().isEmpty());
    }

    private static BuildFastCameraCapabilitiesUseCase useCase(RawCatalog catalog) {
        return new BuildFastCameraCapabilitiesUseCase(() -> catalog, new TestLogger());
    }

    private static CameraFastSnapshot cameraSnapshot(
            BuildFastCameraCapabilitiesUseCase.Result result, String cameraId) {
        return result.authoritativeSnapshot().orElseThrow().cameras().stream()
                .filter(camera -> camera.cameraFacts().cameraId().value().equals(cameraId))
                .findFirst().orElseThrow();
    }

    private static Set<CaptureModeTuple> tupleCandidates(PipelineEvidence evidence) {
        java.util.TreeSet<CaptureModeTuple> result = new java.util.TreeSet<>();
        for (CandidateKey candidate : evidence.rawFastCandidates()) {
            candidate.tuple().ifPresent(result::add);
        }
        return Set.copyOf(result);
    }

    private static List<CameraFacts> cameras(String... cameraIds) {
        List<CameraFacts> result = new ArrayList<>();
        for (String cameraId : cameraIds) {
            result.add(camera(cameraId, List.of(HD), List.of(HD), List.of(30)));
        }
        return List.copyOf(result);
    }

    private static CameraFacts camera(
            String cameraId,
            List<CameraResolution> videoResolutions,
            List<CameraResolution> imageResolutions,
            List<Integer> frameRates) {
        return cameraWithFacing(cameraId, videoResolutions, imageResolutions, frameRates, 1);
    }

    private static CameraFacts cameraWithFacing(
            String cameraId,
            List<CameraResolution> videoResolutions,
            List<CameraResolution> imageResolutions,
            List<Integer> frameRates,
            Integer lensFacing) {
        List<StreamSize> privateOutputs = videoResolutions.stream()
                .map(resolution -> new StreamSize(resolution, 16_666_667L))
                .toList();
        List<StreamSize> jpegOutputs = imageResolutions.stream()
                .map(resolution -> new StreamSize(resolution, null))
                .toList();
        List<FrameRateRange> ranges = frameRates.stream()
                .map(value -> range(value, value))
                .toList();
        return new CameraFacts(new CameraId(cameraId), 1, lensFacing, 90,
                List.of(), List.of(), true, privateOutputs, List.of(), List.of(),
                jpegOutputs, ranges);
    }

    private static RawCatalog catalog(
            List<CameraFacts> cameras,
            boolean concurrencyAvailable,
            List<ConcurrentCameraCombination> combinations) {
        List<CameraResolution> videoResolutions = cameras.stream()
                .flatMap(camera -> camera.privateOutputs().stream())
                .map(StreamSize::resolution)
                .distinct()
                .toList();
        return new RawCatalog(device(), cameras,
                new ConcurrentCameraFacts(concurrencyAvailable, combinations),
                List.of(encoder(videoResolutions)), "hardware-signature-input");
    }

    private static RawCatalog withSignature(RawCatalog catalog, String signature) {
        return new RawCatalog(catalog.deviceIdentity(), catalog.cameras(),
                catalog.concurrency(), catalog.h264Encoders(), signature);
    }

    private static EncoderFacts encoder(List<CameraResolution> resolutions) {
        List<EncoderSizeCapabilities> sizes = resolutions.stream()
                .map(resolution -> new EncoderSizeCapabilities(
                        resolution, List.of(30, 60, 120)))
                .toList();
        EncoderVideoCapabilities video = new EncoderVideoCapabilities(
                2, 2, new IntegerRange(1, 8192), new IntegerRange(1, 8192),
                new IntegerRange(1, 100_000_000), range(1, 120), sizes);
        return new EncoderFacts("encoder", "encoder", false, true, false, false,
                4, List.<CodecProfileLevel>of(), List.of(), video);
    }

    private static EncoderFacts encoderWithSizeSupport(
            Map<CameraResolution, Boolean> sizeSupport) {
        List<EncoderSizeCapabilities> sizes = sizeSupport.entrySet().stream()
                .map(entry -> new EncoderSizeCapabilities(entry.getKey(),
                        Boolean.TRUE.equals(entry.getValue())
                                ? List.of(30, 60, 120) : List.of()))
                .toList();
        EncoderVideoCapabilities video = new EncoderVideoCapabilities(
                2, 2, new IntegerRange(1, 8192), new IntegerRange(1, 8192),
                new IntegerRange(1, 100_000_000), range(1, 120), sizes);
        return new EncoderFacts("encoder", "encoder", false, true, false, false,
                4, List.<CodecProfileLevel>of(), List.of(), video);
    }

    private static DeviceIdentity device() {
        return new DeviceIdentity(36, "manufacturer", "brand", "device",
                "product", "model", "hardware", "board", "bootloader",
                "build", "incremental", "patch", "fingerprint");
    }

    private static FrameRateRange range(int lower, int upper) {
        return new FrameRateRange(
                BigDecimal.valueOf(lower), BigDecimal.valueOf(upper));
    }

    private static ConcurrentCameraCombination combination(String... cameraIds) {
        return new ConcurrentCameraCombination(
                java.util.Arrays.stream(cameraIds).map(CameraId::new).toList());
    }
    private static BatchResult complete(
            VerificationPipelineId pipelineId,
            List<Request> requests,
            Predicate<CaptureModeTuple> supported) {
        List<CameraResult> results = new ArrayList<>();
        for (Request request : requests) {
            List<CandidateKey> candidates = new ArrayList<>();
            for (VideoMode videoMode : request.videoModes()) {
                for (ImageMode imageMode : request.imageModes()) {
                    CaptureModeTuple tuple = new CaptureModeTuple(videoMode, imageMode);
                    if (supported.test(tuple)) {
                        candidates.add(CandidateKey.forTuple(
                                request.cameraId(), VideoCodec.H264, pipelineId, tuple));
                    }
                }
            }
            PipelineEvidence evidence = new PipelineEvidence(
                    request.cameraId(), VideoCodec.H264, pipelineId,
                    PipelineAvailability.AVAILABLE, candidates, List.of());
            results.add(new CameraResult(
                    evidence, Completion.COMPLETE, 2, "matrix_complete"));
        }
        return new BatchResult(
                Completion.COMPLETE, results, true, 3, "batch_complete");
    }

    private static final class MutableCatalogSource implements CameraCatalogSource {
        private RawCatalog catalog;

        private MutableCatalogSource(RawCatalog catalog) { this.catalog = catalog; }

        @Override public RawCatalog load() { return catalog; }
    }

    private static final class FakeProbe implements FastCameraCapabilityProbe {
        private final VerificationPipelineId pipelineId;
        private final BiFunction<List<Request>, Integer, BatchResult> behavior;
        private final List<List<Request>> requests = new ArrayList<>();

        private FakeProbe(
                VerificationPipelineId pipelineId,
                BiFunction<List<Request>, Integer, BatchResult> behavior) {
            this.pipelineId = pipelineId;
            this.behavior = behavior;
        }

        private static FakeProbe supporting(
                VerificationPipelineId pipelineId,
                Predicate<CaptureModeTuple> supported) {
            return new FakeProbe(pipelineId,
                    (requests, call) -> complete(pipelineId, requests, supported));
        }

        @Override public VerificationPipelineId pipelineId() { return pipelineId; }

        @Override public BatchResult probeBatch(
                List<Request> batch, CancellationSignal cancellationSignal) {
            List<Request> copied = List.copyOf(batch);
            requests.add(copied);
            return behavior.apply(copied, requests.size());
        }

        private List<List<Request>> requests() { return List.copyOf(requests); }

        private List<List<CameraId>> cameraCalls() {
            return requests.stream()
                    .map(batch -> batch.stream().map(Request::cameraId).toList())
                    .toList();
        }
    }

    private static final class TestLogger implements Logger {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    }
}