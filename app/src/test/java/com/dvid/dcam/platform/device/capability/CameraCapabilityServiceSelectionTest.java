package com.dvid.dcam.platform.device.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineComparisonSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineVerificationCoverage;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.ConcurrentCameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.DeviceIdentity;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.RawCatalog;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.CameraFastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.FastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.ResolveCameraRuntimeSelectionUseCase;
import com.dvid.dcam.feature.device.application.usecase.SelectCameraPipelineUseCase;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparison;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparisonInput;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeOwner;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

final class CameraCapabilityServiceSelectionTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId A =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");
    private static final VerificationPipelineId B =
            new VerificationPipelineId("b-camera2-egl-fanout-v1");
    private static final VerificationPipelineId MISSING =
            new VerificationPipelineId("missing-pipeline");
    private static final CaptureModeTuple TUPLE = new CaptureModeTuple(
            new VideoMode(resolution(), 30), new ImageMode(resolution()));

    @Test void forcedSelectionScansOnePipelineWhileAutoRequestsBoth() {
        assertEquals(List.of(A),
                CameraCapabilityService.scanPipelinesForSelection(Optional.of(A)));
        assertEquals(List.of(B),
                CameraCapabilityService.scanPipelinesForSelection(Optional.of(B)));
        assertEquals(List.of(A, B),
                CameraCapabilityService.scanPipelinesForSelection(Optional.empty()));
    }

    @Test void capabilityScanScheduleReleasesReservationAfterExecutorRejection() {
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(new NoOpLogger());
        RuntimeException rejection = new IllegalStateException("rejected");
        RuntimeException[] observed = {null};

        CameraCapabilityService.ScanScheduleResult result =
                CameraCapabilityService.scheduleCapabilityScan(owner,
                        command -> { throw rejection; }, () -> {}, () -> {},
                        error -> observed[0] = error);

        assertEquals(CameraCapabilityService.ScanScheduleResult.SCHEDULE_FAILED, result);
        assertEquals(rejection, observed[0]);
        assertTrue(owner.beginCapabilityScan());
        owner.endCapabilityScan();
        owner.close();
    }

    @Test void capabilityScanScheduleHoldsReservationUntilTaskFinally() {
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(new NoOpLogger());
        QueuedExecutor executor = new QueuedExecutor();

        boolean[] releasedBeforeCallback = {false};
        CameraCapabilityService.ScanScheduleResult result =
                CameraCapabilityService.scheduleCapabilityScan(owner, executor,
                        () -> { throw new IllegalStateException("scan failed"); },
                        () -> {
                            releasedBeforeCallback[0] = owner.beginCapabilityScan();
                            owner.endCapabilityScan();
                        }, error -> { throw new AssertionError(error); });

        assertEquals(CameraCapabilityService.ScanScheduleResult.SCHEDULED, result);
        assertFalse(owner.beginCapabilityScan());
        assertThrows(IllegalStateException.class, executor::run);
        assertTrue(releasedBeforeCallback[0]);
        assertTrue(owner.beginCapabilityScan());
        owner.endCapabilityScan();
        owner.close();
    }

    @Test void autoIgnoresStaleFixedSelectionAndUsesStableEvidenceTie() {
        Snapshot view = CameraCapabilityService.selectionSnapshotForMode(snapshot(),
                Optional.empty(), resolver());

        CameraSnapshot camera = view.cameras().get(0);
        assertEquals(CameraCapabilityStore.PipelineSelectionMode.AUTO,
                camera.selectedPipeline().orElseThrow().mode());
        assertEquals(A, camera.selectedPipeline().orElseThrow().pipelineId());
        assertEquals(CameraCapabilityStore.PipelineDecisionStatus.FAST_COMPLETE,
                camera.selectedPipeline().orElseThrow().decisionStatus());
        assertEquals(CameraCapabilityStore.PipelineDecisionReason.FINAL_TIE_A,
                camera.selectedPipeline().orElseThrow().decisionReason());
        assertEquals(List.of("0"), CameraCapabilityOptions.cameraIds(view));
    }

    @Test void cameraPipelineModesSelectDifferentPipelinesIndependently() {
        Snapshot view = CameraCapabilityService.selectionSnapshotForModes(
                twoCameraSnapshot(),
                Map.of("0", Optional.of(A), "1", Optional.of(B)),
                Optional.empty(), resolver());

        assertEquals(A, view.cameras().get(0).selectedPipeline().orElseThrow().pipelineId());
        assertEquals(B, view.cameras().get(1).selectedPipeline().orElseThrow().pipelineId());
        assertEquals(CameraCapabilityStore.PipelineSelectionMode.FIXED,
                view.cameras().get(0).selectedPipeline().orElseThrow().mode());
        assertEquals(CameraCapabilityStore.PipelineSelectionMode.FIXED,
                view.cameras().get(1).selectedPipeline().orElseThrow().mode());
    }

    @Test void perCameraModesOverrideUnavailableGlobalFallbackWithoutLosingEvidence() {
        CameraId camera0 = new CameraId("0");
        CameraId camera1 = new CameraId("1");
        Snapshot current = Snapshot.current("hardware-signature", List.of(camera0, camera1),
                List.of(cameraWithPipeline(camera0, A), cameraWithPipeline(camera1, B)));

        Snapshot view = CameraCapabilityService.selectionSnapshotForModes(current,
                Map.of("0", Optional.of(A), "1", Optional.of(B)),
                Optional.of(A), resolver());

        assertEquals(A, view.cameras().get(0).selectedPipeline().orElseThrow().pipelineId());
        assertEquals(B, view.cameras().get(1).selectedPipeline().orElseThrow().pipelineId());
        assertEquals(1, view.cameras().get(0).codecs().get(0).pipelines().size());
        assertEquals(1, view.cameras().get(1).codecs().get(0).pipelines().size());
    }

    @Test void forcedUnavailablePipelinePreservesCameraWithEmptyOptions() {
        Snapshot view = CameraCapabilityService.selectionSnapshotForMode(snapshot(),
                Optional.of(MISSING), resolver());

        assertEquals(List.of("0"), CameraCapabilityOptions.cameraIds(view));
        assertEquals(List.of(), CameraCapabilityOptions.recordQualities(view, "0", Set.of()));
        CameraSnapshot camera = view.cameras().get(0);
        assertTrue(camera.selectedPipeline().isEmpty());
        assertTrue(camera.codecs().stream()
                .filter(codec -> codec.codec() == VideoCodec.H264)
                .findFirst().orElseThrow().pipelines().isEmpty());
    }

    @Test void startupFastCollectionBuildsOnlyMissingXmlEvidence() {
        Snapshot onlyA = snapshot(pipeline(A));

        assertEquals(List.of(A), CameraCapabilityService.missingPipelines(
                null, List.of(A)));
        assertEquals(List.of(B), CameraCapabilityService.missingPipelines(
                onlyA, List.of(A, B)));
        assertTrue(CameraCapabilityService.missingPipelines(
                snapshot(), List.of(A, B)).isEmpty());
    }

    @Test void startupFastCollectionSkipsOnlyCurrentCompleteXml() {
        Snapshot complete = snapshot();

        assertTrue(CameraCapabilityService.hasCompleteStartupFastCollection(
                CameraCapabilityStore.LoadResult.loaded(complete), List.of(A, B)));
        assertFalse(CameraCapabilityService.hasCompleteStartupFastCollection(
                CameraCapabilityStore.LoadResult.loadedWithInvalidatedEvidence(
                        complete, CameraCapabilityStore.RebuildReason.HARDWARE_MISMATCH,
                        List.of(CAMERA), List.of()), List.of(A, B)));
        assertFalse(CameraCapabilityService.hasCompleteStartupFastCollection(
                CameraCapabilityStore.LoadResult.rebuildNeeded(
                        CameraCapabilityStore.RebuildReason.MISSING), List.of(A, B)));
    }
    @Test void targetedQuickScanPreservesOtherCameraSnapshot() {
        CameraId activeCamera = new CameraId("0");
        CameraId targetCamera = new CameraId("1");
        Snapshot current = twoCameraSnapshot();
        CameraSnapshot activeBefore = current.cameras().get(0);
        RawCatalog catalog = catalog(activeCamera, targetCamera);
        RawCatalog targeted = CameraCapabilityService.targetedCatalog(catalog, targetCamera);
        PipelineEvidence evidence = verifiedPipeline(targetCamera, B);
        CameraFastSnapshot target = new CameraFastSnapshot(targeted.cameras().get(0),
                List.of(TUPLE.videoMode()), List.of(TUPLE.imageMode()), evidence,
                Optional.of(TUPLE), 1L, "complete");
        FastSnapshot scan = new FastSnapshot(targeted, B, List.of(target),
                List.of(targetCamera));

        Snapshot merged = CameraCapabilityService.mergeTargetedScan(
                current, targetCamera, scan);

        assertEquals(List.of(targetCamera), targeted.cameras().stream()
                .map(CameraFacts::cameraId)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)));
        assertEquals(2, merged.cameras().size());
        assertEquals(activeBefore, merged.cameras().get(0));
        assertEquals(targetCamera, merged.cameras().get(1).cameraId());
    }

    @Test void onlyFinalExhaustiveSnapshotSkipsDeepVerification() {
        Snapshot verified = verifiedSnapshot();

        assertFalse(CameraCapabilityService.requiresDeepVerification(
                verified, List.of(A, B)));
        assertTrue(CameraCapabilityService.requiresDeepVerification(
                verified.withInitializationState(InitializationState.INCOMPLETE),
                List.of(A, B)));
        assertTrue(CameraCapabilityService.requiresDeepVerification(
                verified, List.of(A, MISSING)));
        assertTrue(CameraCapabilityService.requiresDeepVerification(null, List.of(A, B)));
    }

    @Test void displayedTupleCountUsesFastInventoryUntilFullVerification() {
        CaptureModeTuple secondTuple = new CaptureModeTuple(
                new VideoMode(resolution(), 24), new ImageMode(resolution()));
        CandidateKey first = CandidateKey.forTuple(CAMERA, VideoCodec.H264, A, TUPLE);
        CandidateKey second = CandidateKey.forTuple(CAMERA, VideoCodec.H264, A, secondTuple);
        PipelineEvidence pipeline = new PipelineEvidence(CAMERA, VideoCodec.H264, A,
                PipelineAvailability.AVAILABLE, List.of(first, second), List.of(
                new CandidateEvidence(first, VerificationOutcome.VERIFIED_PASS)));

        assertEquals(2, CameraCapabilityService.displayedTupleCount(pipeline, false));
        assertEquals(1, CameraCapabilityService.displayedTupleCount(pipeline, true));
    }

    @Test void validatedSavedCandidateReusesVerifiedProfileWithoutInventoryResolution() {
        Snapshot snapshot = verifiedSnapshot();
        CandidateKey expected = CandidateKey.forTuple(CAMERA, VideoCodec.H264, A, TUPLE);

        assertEquals(expected, CameraCapabilityService.validatedSavedCandidate(
                snapshot, CAMERA, "HD", 30, "HD", Set.of()).orElseThrow());
        assertEquals(expected, CameraCapabilityService.validatedSavedCandidate(
                snapshot, CAMERA, "", 0, "", Set.of()).orElseThrow());
        assertTrue(CameraCapabilityService.validatedSavedCandidate(
                snapshot, CAMERA, "FHD", 30, "HD", Set.of()).isEmpty());
        assertTrue(CameraCapabilityService.validatedSavedCandidate(snapshot, CAMERA,
                "HD", 30, "HD", Set.of("0|HD|30|HD")).isEmpty());
    }

    @Test void candidateDescriptionUsesReadableModes() {
        StandardResolution maximum = new StandardResolution(StandardResolutionLabel.MAX,
                new CameraResolution(1600, 1200));
        CandidateKey candidate = CandidateKey.forTuple(CAMERA, VideoCodec.H264, A,
                new CaptureModeTuple(new VideoMode(maximum, 30), new ImageMode(resolution())));

        assertEquals("H264 video MAX (1600x1200) at 30 FPS with HD (1280x720) photos",
                CameraCapabilityService.describeCandidate(candidate));
    }

    @Test void recordingFallbackPersistsOnlyAfterRequestedFrameRateExhausted() {
        CandidateKey requested = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, A, TUPLE);
        CaptureModeTuple fallbackTuple = new CaptureModeTuple(
                new VideoMode(resolution(), 24), new ImageMode(resolution()));
        CandidateKey fallback = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, A, fallbackTuple);
        Snapshot timeout = snapshot(new PipelineEvidence(
                CAMERA, VideoCodec.H264, A, PipelineAvailability.AVAILABLE,
                List.of(requested, fallback), List.of(
                        new CandidateEvidence(fallback, VerificationOutcome.VERIFIED_PASS))));

        assertTrue(CameraCapabilityService.recordingVideoSelectionAvailable(
                timeout, requested, true));

        Snapshot exhausted = snapshot(new PipelineEvidence(
                CAMERA, VideoCodec.H264, A, PipelineAvailability.AVAILABLE,
                List.of(requested, fallback), List.of(
                        new CandidateEvidence(requested,
                                VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                        new CandidateEvidence(fallback, VerificationOutcome.VERIFIED_PASS))));
        assertFalse(CameraCapabilityService.recordingVideoSelectionAvailable(
                exhausted, requested, true));
        assertTrue(CameraCapabilityService.recordingVideoSelectionAvailable(
                exhausted, requested, false));
    }

    private static ResolveCameraRuntimeSelectionUseCase resolver() {
        return new ResolveCameraRuntimeSelectionUseCase(
                new SelectCameraPipelineUseCase(List.of(B, A), A));
    }

    private static RawCatalog catalog(CameraId... cameraIds) {
        List<CameraFacts> cameras = java.util.Arrays.stream(cameraIds)
                .map(CameraCapabilityServiceSelectionTest::cameraFacts)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        return new RawCatalog(new DeviceIdentity(35, "manufacturer", "brand", "device",
                "product", "model", "hardware", "board", "bootloader", "build",
                "incremental", "patch", "fingerprint"), cameras,
                new ConcurrentCameraFacts(true, List.of()), List.of(), "hardware-signature-input");
    }

    private static CameraFacts cameraFacts(CameraId cameraId) {
        return new CameraFacts(cameraId, 1, 1, 90, List.of(), List.of(), true,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static Snapshot snapshot() {
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline(A), pipeline(B)), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-signature",
                List.of(codec), Optional.of(CameraCapabilityStore.SelectedPipeline.fixed(A)),
                Optional.empty());
        return Snapshot.current("hardware-signature", List.of(CAMERA), List.of(camera));
    }

    private static Snapshot verifiedSnapshot() {
        PipelineEvidence pipelineA = verifiedPipeline(A);
        PipelineEvidence pipelineB = verifiedPipeline(B);
        PipelineComparison comparison = PipelineComparison.compare(Set.of(TUPLE),
                PipelineComparisonInput.withoutPerformance(pipelineA, true),
                PipelineComparisonInput.withoutPerformance(pipelineB, true));
        PipelineVerificationCoverage coverage =
                new PipelineVerificationCoverage(0, 0, 1, 1);
        PipelineComparisonSnapshot comparisonSnapshot =
                PipelineComparisonSnapshot.withCoverage(comparison, coverage, coverage);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipelineA, pipelineB),
                Optional.of(comparisonSnapshot));
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-signature",
                List.of(codec), Optional.of(CameraCapabilityStore.SelectedPipeline.fixed(A)),
                Optional.of(new SelectedRecordingProfile(VideoCodec.H264, A, TUPLE)));
        return Snapshot.current("hardware-signature", List.of(CAMERA), List.of(camera))
                .withInitializationState(InitializationState.READY_REUSABLE);
    }

    private static Snapshot twoCameraSnapshot() {
        CameraSnapshot camera0 = cameraWithPipelines(new CameraId("0"));
        CameraSnapshot camera1 = cameraWithPipelines(new CameraId("1"));
        return Snapshot.current("hardware-signature", List.of(new CameraId("0"), new CameraId("1")),
                List.of(camera0, camera1));
    }

    private static CameraSnapshot cameraWithPipeline(
            CameraId cameraId, VerificationPipelineId pipelineId) {
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(verifiedPipeline(cameraId, pipelineId)),
                Optional.empty());
        return new CameraSnapshot(cameraId, "camera-signature-" + cameraId.value(),
                List.of(codec), Optional.empty(), Optional.empty());
    }

    private static CameraSnapshot cameraWithPipelines(CameraId cameraId) {
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(verifiedPipeline(cameraId, A), verifiedPipeline(cameraId, B)),
                Optional.empty());
        return new CameraSnapshot(cameraId, "camera-signature-" + cameraId.value(),
                List.of(codec), Optional.empty(), Optional.empty());
    }

    private static Snapshot snapshot(PipelineEvidence pipeline) {
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-signature",
                List.of(codec), Optional.of(CameraCapabilityStore.SelectedPipeline.fixed(A)),
                Optional.empty());
        return Snapshot.current("hardware-signature", List.of(CAMERA), List.of(camera));
    }

    private static PipelineEvidence verifiedPipeline(VerificationPipelineId pipelineId) {
        return verifiedPipeline(CAMERA, pipelineId);
    }

    private static PipelineEvidence verifiedPipeline(CameraId cameraId,
            VerificationPipelineId pipelineId) {
        CandidateKey tuple = CandidateKey.forTuple(cameraId, VideoCodec.H264, pipelineId, TUPLE);
        return new PipelineEvidence(cameraId, VideoCodec.H264, pipelineId,
                PipelineAvailability.AVAILABLE, List.of(tuple), List.of(
                new CandidateEvidence(tuple, VerificationOutcome.VERIFIED_PASS)));
    }

    private static PipelineEvidence pipeline(VerificationPipelineId pipelineId) {
        CandidateKey tuple = CandidateKey.forTuple(CAMERA, VideoCodec.H264, pipelineId, TUPLE);
        return new PipelineEvidence(CAMERA, VideoCodec.H264, pipelineId,
                PipelineAvailability.AVAILABLE, List.of(tuple), List.of());
    }

    private static final class QueuedExecutor implements Executor {
        private Runnable task;

        @Override public void execute(Runnable command) {
            task = command;
        }

        private void run() {
            Runnable current = task;
            task = null;
            current.run();
        }
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }

    private static StandardResolution resolution() {
        return new StandardResolution(StandardResolutionLabel.HD,
                new CameraResolution(1280, 720));
    }
}
