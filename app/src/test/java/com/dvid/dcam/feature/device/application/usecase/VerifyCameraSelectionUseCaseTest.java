package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.domain.camera.CameraFailureClass;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class VerifyCameraSelectionUseCaseTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("pipeline-a");
    private static final VerificationPipelineId PIPELINE_B =
            new VerificationPipelineId("pipeline-b");
    private static final VideoMode VIDEO = new VideoMode(
            new StandardResolution(StandardResolutionLabel.FHD,
                    new CameraResolution(1920, 1080)), 30);
    private static final VideoMode VIDEO_ALIGNED = new VideoMode(
            new StandardResolution(StandardResolutionLabel.FHD,
                    new CameraResolution(1920, 1088)), 30);
    private static final ImageMode IMAGE_HD = new ImageMode(
            new StandardResolution(StandardResolutionLabel.HD,
                    new CameraResolution(1280, 720)));
    private static final ImageMode IMAGE_SD = new ImageMode(
            new StandardResolution(StandardResolutionLabel.SD,
                    new CameraResolution(720, 480)));
    private static final ImageMode IMAGE_FHD = new ImageMode(
            new StandardResolution(StandardResolutionLabel.FHD,
                    new CameraResolution(1920, 1080)));
    private static final ImageMode IMAGE_FHD_ALIGNED = new ImageMode(
            new StandardResolution(StandardResolutionLabel.FHD,
                    new CameraResolution(1920, 1088)));

    @Test void passRetainsOneComboBindingAndPersistsSelection() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        FakeRuntime runtime = new FakeRuntime();
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), imageKey(IMAGE_SD),
                        requested, tupleKey(IMAGE_SD)),
                Map.of(), Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 1, 0));

        assertTrue(result.verified());
        assertEquals(VerifyCameraSelectionUseCase.Completion.REQUESTED_VERIFIED,
                result.completion());
        assertTrue(result.verifiedBinding().isPresent());
        assertEquals(1, runtime.bindContexts.size());
        assertTrue(runtime.releaseGenerations().isEmpty());
        assertEquals(1, store.writes.size());
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                pipeline(result.snapshot()).outcome(requested));
        assertTrue(camera(result.snapshot()).selectedRecordingProfile().isPresent());
        assertEquals(PIPELINE,
                camera(result.snapshot()).selectedPipeline().orElseThrow().pipelineId());
        assertEquals(CameraCapabilityStore.PipelineDecisionStatus.PROFILE_VERIFIED,
                camera(result.snapshot()).selectedPipeline().orElseThrow().decisionStatus());
        assertEquals(CameraCapabilityStore.PipelineDecisionReason.VERIFIED_RECORDING_PROFILE,
                camera(result.snapshot()).selectedPipeline().orElseThrow().decisionReason());
    }

    @Test void fixedPipelineSwitchPersistsVerifiedTargetWithoutMismatch() {
        CandidateKey previous = tupleKey(IMAGE_HD);
        CandidateKey videoB = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE_B, VIDEO);
        CandidateKey imageB = CandidateKey.forImage(
                CAMERA, VideoCodec.H264, PIPELINE_B, IMAGE_HD);
        CandidateKey requestedB = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE_B, tuple(IMAGE_HD));
        PipelineEvidence evidenceA = new PipelineEvidence(CAMERA, VideoCodec.H264,
                PIPELINE, PipelineAvailability.AVAILABLE, List.of(previous),
                List.of(new CandidateEvidence(
                        previous, VerificationOutcome.VERIFIED_PASS)));
        PipelineEvidence evidenceB = new PipelineEvidence(CAMERA, VideoCodec.H264,
                PIPELINE_B, PipelineAvailability.AVAILABLE,
                List.of(videoB, imageB, requestedB), List.of());
        CameraCapabilityStore.CodecSnapshot codec =
                new CameraCapabilityStore.CodecSnapshot(VideoCodec.H264,
                        CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                        List.of(evidenceA, evidenceB), Optional.empty());
        CameraCapabilityStore.SelectedRecordingProfile previousSelection =
                new CameraCapabilityStore.SelectedRecordingProfile(
                        VideoCodec.H264, PIPELINE, tuple(IMAGE_HD));
        CameraCapabilityStore.Snapshot snapshot = CameraCapabilityStore.Snapshot.current(
                "hardware-signature", List.of(), List.of(
                new CameraCapabilityStore.CameraSnapshot(CAMERA, "camera-signature",
                        List.of(codec), Optional.of(
                        CameraCapabilityStore.SelectedPipeline.fixed(PIPELINE)),
                        Optional.of(previousSelection))));
        FakeRuntime runtime = new FakeRuntime();
        FakeStore store = new FakeStore(snapshot);

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requestedB, 10, 0));

        assertTrue(result.verified());
        assertEquals(VerifyCameraSelectionUseCase.Completion.REQUESTED_VERIFIED,
                result.completion());
        assertEquals(CameraCapabilityStore.PipelineSelectionMode.FIXED,
                camera(result.snapshot()).selectedPipeline().orElseThrow().mode());
        assertEquals(PIPELINE_B,
                camera(result.snapshot()).selectedPipeline().orElseThrow().pipelineId());
        assertEquals(PIPELINE_B, camera(result.snapshot()).selectedRecordingProfile()
                .orElseThrow().verificationPipelineId());
    }
    @Test void unverifiedSoloInventoryDoesNotBlockComboVerification() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        FakeRuntime startupRuntime = new FakeRuntime();
        FakeStore startupStore = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), imageKey(IMAGE_SD),
                        requested, tupleKey(IMAGE_SD)), Map.of(), Optional.empty()));

        VerifyCameraSelectionUseCase.Result startup = usecase(startupRuntime, startupStore)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        startupStore.snapshot, requested, 2, 0));
        assertTrue(startup.verified());

        CandidateKey changed = tupleKey(IMAGE_SD);
        FakeRuntime lazyRuntime = new FakeRuntime();
        FakeStore lazyStore = new FakeStore(startup.snapshot());
        VerifyCameraSelectionUseCase.Result lazy = usecase(lazyRuntime, lazyStore)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        lazyStore.snapshot, changed, 20, 0));

        assertTrue(lazy.verified());
        assertEquals(1, lazyRuntime.bindContexts.size());
        assertEquals(1, lazyRuntime.startCalls);
        assertEquals(1, lazyRuntime.captureCalls);
        assertTrue(lazyRuntime.releaseGenerations().isEmpty());
    }

    @Test void startupFamilyFallbackPinsAlignedActualBeforeCombo() {
        CandidateKey requested = tupleKey(VIDEO, IMAGE_HD);
        CandidateKey aligned = tupleKey(VIDEO_ALIGNED, IMAGE_HD);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.candidate(CameraFailureClass.SESSION_CONFIGURATION,
                        "exact_bind_failed", 1),
                Script.candidate(CameraFailureClass.SESSION_CONFIGURATION,
                        "exact_bind_failed", 1),
                Script.pass(), Script.pass(), Script.pass());
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(VIDEO), videoKey(VIDEO_ALIGNED), imageKey(IMAGE_HD),
                        requested, aligned), Map.of(), Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 30, 0));

        assertTrue(result.verified());
        assertEquals(VerifyCameraSelectionUseCase.Completion.FALLBACK_VERIFIED,
                result.completion());
        assertEquals(aligned, result.selectedCandidate().orElseThrow());
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                pipeline(result.snapshot()).outcome(requested));
        assertEquals(VerificationOutcome.UNKNOWN,
                pipeline(result.snapshot()).outcome(videoKey(VIDEO)));
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                pipeline(result.snapshot()).outcome(aligned));
        assertFalse(pipeline(result.snapshot()).effectiveCandidates().contains(requested));
        assertEquals(3, runtime.bindContexts.size());
    }

    @Test void startupImageFamilyFallbackPinsAlignedActualBeforeCombo() {
        CandidateKey requested = tupleKey(VIDEO, IMAGE_FHD);
        CandidateKey aligned = tupleKey(VIDEO, IMAGE_FHD_ALIGNED);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.candidate(CameraFailureClass.SESSION_CONFIGURATION,
                        "exact_image_bind_failed", 1),
                Script.candidate(CameraFailureClass.SESSION_CONFIGURATION,
                        "exact_image_bind_failed", 1),
                Script.pass());
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_FHD), imageKey(IMAGE_FHD_ALIGNED),
                        requested, aligned), Map.of(), Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 40, 0));

        assertTrue(result.verified());
        assertEquals(VerifyCameraSelectionUseCase.Completion.FALLBACK_VERIFIED,
                result.completion());
        assertEquals(aligned, result.selectedCandidate().orElseThrow());
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                pipeline(result.snapshot()).outcome(requested));
        assertEquals(VerificationOutcome.UNKNOWN,
                pipeline(result.snapshot()).outcome(imageKey(IMAGE_FHD)));
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                pipeline(result.snapshot()).outcome(aligned));
        assertFalse(pipeline(result.snapshot()).effectiveCandidates().contains(requested));
        assertEquals(3, runtime.bindContexts.size());
    }

    @Test void previouslyVerifiedTupleDirectBindsWithoutDeepMediaVerification() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        Map<CandidateKey, VerificationOutcome> facts = Map.of(
                videoKey(), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS,
                requested, VerificationOutcome.VERIFIED_PASS);
        FakeRuntime runtime = new FakeRuntime();
        FakeStore store = new FakeStore(snapshot(
                List.of(requested), facts, Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 5, 0));

        assertTrue(result.verified());
        assertTrue(result.verifiedBinding().isEmpty());
        assertTrue(result.activeBinding().isPresent());
        assertEquals(1, runtime.bindContexts.size());
        assertEquals(0, runtime.startCalls);
        assertEquals(0, runtime.captureCalls);
        assertTrue(runtime.releaseGenerations().isEmpty());
    }

    @Test void healthGenerationChangeCancelsBeforeConfirmationWithoutPruning() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        Map<CandidateKey, VerificationOutcome> facts = Map.of(
                videoKey(), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.candidate(CameraFailureClass.SESSION_CONFIGURATION,
                        "session_config_failed", 1));
        runtime.afterNextOperation = () -> runtime.healthGeneration++;
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), requested), facts,
                Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 6, 0));

        assertEquals(VerificationOutcome.CANCELLED_UNKNOWN, result.outcome());
        assertEquals(VerificationOutcome.UNKNOWN,
                pipeline(result.snapshot()).outcome(requested));
        assertEquals(1, runtime.bindContexts.size());
        assertEquals(List.of(6L), runtime.releaseGenerations());
        assertTrue(store.writes.isEmpty());
    }
    @Test void candidateSuspectConfirmsExactFailureThenFallbackGetsFreshBudget() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        CandidateKey fallback = tupleKey(IMAGE_SD);
        Map<CandidateKey, VerificationOutcome> facts = Map.of(
                videoKey(), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_SD), VerificationOutcome.VERIFIED_PASS);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.of(CameraOperationOutcome.CANDIDATE_SUSPECT,
                        "session_config_failed", 10),
                Script.of(CameraOperationOutcome.CANDIDATE_SUSPECT,
                        "session_config_failed", 10),
                Script.pass());
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), imageKey(IMAGE_SD),
                        requested, fallback), facts, Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 10, 0));

        assertTrue(result.verified());
        assertEquals(fallback, result.selectedCandidate().orElseThrow());
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                pipeline(result.snapshot()).outcome(requested));
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                pipeline(result.snapshot()).outcome(fallback));
        assertEquals(List.of(10L, 11L, 12L), runtime.bindGenerations());
        assertTrue(runtime.deadlines.get(2) > runtime.deadlines.get(0));
        assertEquals(VerifyCameraSelectionUseCase.Completion.FALLBACK_VERIFIED,
                result.completion());
    }

    @Test void timeoutKeepsCandidateUnknownAndFallbackGetsNewFiveSecondBudget() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        CandidateKey fallback = tupleKey(IMAGE_SD);
        Map<CandidateKey, VerificationOutcome> facts = Map.of(
                videoKey(), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_SD), VerificationOutcome.VERIFIED_PASS);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.of(CameraOperationOutcome.PASS, "bind_pass", 5_000),
                Script.pass());
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), imageKey(IMAGE_SD),
                        requested, fallback), facts, Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 20, 0));

        assertTrue(result.verified());
        assertEquals(fallback, result.selectedCandidate().orElseThrow());
        assertEquals(VerificationOutcome.UNKNOWN,
                pipeline(result.snapshot()).outcome(requested));
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                pipeline(result.snapshot()).outcome(fallback));
        assertEquals(5_000L, runtime.deadlines.get(0) - runtime.clock.start);
        assertEquals(5_000L, runtime.deadlines.get(1) - runtime.clock.now);
    }

    @Test void globalFailureRollsBackWithoutTryingFallbackOrPruning() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        CandidateKey fallback = tupleKey(IMAGE_SD);
        SelectedRecordingProfileHolder previous = new SelectedRecordingProfileHolder(
                new CameraCapabilityStore.SelectedRecordingProfile(
                        VideoCodec.H264, PIPELINE, tuple(IMAGE_SD)));
        Map<CandidateKey, VerificationOutcome> facts = Map.of(
                videoKey(), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_SD), VerificationOutcome.VERIFIED_PASS,
                fallback, VerificationOutcome.VERIFIED_PASS);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.of(CameraOperationOutcome.GLOBAL_FAILURE,
                        "camera_service_error", 1), Script.pass());
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), imageKey(IMAGE_SD),
                        requested, fallback), facts, Optional.of(previous.value)));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 30, 0));

        assertEquals(VerifyCameraSelectionUseCase.Completion.ROLLED_BACK,
                result.completion());
        assertEquals(VerificationOutcome.GLOBAL_FAILURE, result.outcome());
        assertEquals(2, runtime.bindContexts.size());
        assertEquals(VerificationOutcome.UNKNOWN,
                pipeline(result.snapshot()).outcome(requested));
        assertTrue(result.activeBinding().isPresent());
        assertEquals(tuple(IMAGE_SD), camera(result.snapshot())
                .selectedRecordingProfile().orElseThrow().tuple());
    }

    @Test void persistenceFailureDoesNotReleaseVerifiedComboBinding() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        FakeRuntime runtime = new FakeRuntime();
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), requested),
                Map.of(), Optional.empty()));
        store.throwOnWrite = true;

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 40, 0));

        assertTrue(result.verified());
        assertTrue(result.persistenceWriteFailed());
        assertTrue(result.activeBinding().isPresent());
        assertTrue(runtime.releaseGenerations().isEmpty());
        assertEquals(1, runtime.bindContexts.size());
    }

    @Test void differentConfirmationFailureClassStaysUnknownAndRollsBack() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        CandidateKey fallback = tupleKey(IMAGE_SD);
        SelectedRecordingProfileHolder previous = new SelectedRecordingProfileHolder(
                new CameraCapabilityStore.SelectedRecordingProfile(
                        VideoCodec.H264, PIPELINE, tuple(IMAGE_SD)));
        Map<CandidateKey, VerificationOutcome> facts = Map.of(
                videoKey(), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_SD), VerificationOutcome.VERIFIED_PASS,
                fallback, VerificationOutcome.VERIFIED_PASS);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.candidate(CameraFailureClass.SESSION_CONFIGURATION,
                        "session_config_failed", 1),
                Script.candidate(CameraFailureClass.VIDEO_ENCODER,
                        "encoder_failed", 1), Script.pass());
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), imageKey(IMAGE_SD),
                        requested, fallback), facts, Optional.of(previous.value)));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 50, 0));

        assertEquals(VerifyCameraSelectionUseCase.Completion.ROLLED_BACK,
                result.completion());
        assertEquals(VerificationOutcome.UNKNOWN, result.outcome());
        assertEquals(3, runtime.bindContexts.size());
        assertEquals(VerificationOutcome.UNKNOWN,
                pipeline(result.snapshot()).outcome(requested));
        assertEquals(fallback, pipeline(result.snapshot()).outcome(fallback)
                == VerificationOutcome.VERIFIED_PASS ? fallback : null);
    }
    @Test void wrongVideoOutputNeedsConfirmationBeforeDefinitiveFail() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        FakeRuntime runtime = new FakeRuntime();
        runtime.invalidVideoFinalizations = 2;
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), requested), Map.of(),
                Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 60, 0));

        assertEquals(VerifyCameraSelectionUseCase.Completion.UNAVAILABLE,
                result.completion());
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED, result.outcome());
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                pipeline(result.snapshot()).outcome(requested));
        assertEquals(VerificationOutcome.UNKNOWN,
                pipeline(result.snapshot()).outcome(videoKey()));
        assertEquals(2, runtime.finalizeCalls);
        assertEquals(2, runtime.bindContexts.size());
    }


    @Test void fpsMismatchInDiagnosticDetailDoesNotFailValidOutputs() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        FakeRuntime runtime = new FakeRuntime();
        runtime.fpsDetail = "measuredFps=20;selectedFps=30";
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), requested), Map.of(),
                Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 70, 0));

        assertTrue(result.verified());
        assertTrue(result.attempts().stream().anyMatch(value ->
                value.detail().contains("measuredFps=20")));
    }

    @Test void transposedJpegDimensionsVerifyQuarterTurnCameraOutput() {
        for (int sensorOrientationDegrees : List.of(90, 270)) {
            CandidateKey requested = tupleKey(IMAGE_HD);
            FakeRuntime runtime = new FakeRuntime();
            runtime.transposeImageCapture = true;
            FakeStore store = new FakeStore(withSensorOrientation(snapshot(
                    List.of(videoKey(), imageKey(IMAGE_HD), requested), Map.of(),
                    Optional.empty()), sensorOrientationDegrees));

            VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                    .execute(new VerifyCameraSelectionUseCase.Request(
                            store.snapshot, requested, 75, 0));

            assertTrue(result.verified());
            assertEquals(VerifyCameraSelectionUseCase.Completion.REQUESTED_VERIFIED,
                    result.completion());
            assertEquals(VerificationOutcome.VERIFIED_PASS, result.outcome());
            assertEquals(sensorOrientationDegrees, result.verifiedBinding()
                    .orElseThrow().sensorOrientationDegrees());
            assertEquals(1, runtime.captureCalls);
        }
    }

    @Test void transposedJpegDimensionsRejectStraightCameraOutput() {
        for (int sensorOrientationDegrees : List.of(0, 180)) {
            CandidateKey requested = tupleKey(IMAGE_HD);
            FakeRuntime runtime = new FakeRuntime();
            runtime.transposeImageCapture = true;
            FakeStore store = new FakeStore(withSensorOrientation(snapshot(
                    List.of(videoKey(), imageKey(IMAGE_HD), requested), Map.of(),
                    Optional.empty()), sensorOrientationDegrees));

            VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                    .execute(new VerifyCameraSelectionUseCase.Request(
                            store.snapshot, requested, 75, 0));

            assertFalse(result.verified());
            assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED, result.outcome());
            assertEquals(2, runtime.captureCalls);
        }
    }

    @Test void transientBlockedCancelledAndStaleNeverPruneOrTryFallback() {
        for (CameraOperationOutcome operationOutcome : List.of(
                CameraOperationOutcome.TRANSIENT_RETRYABLE,
                CameraOperationOutcome.BLOCKED_EXTERNAL,
                CameraOperationOutcome.CANCELLED_UNKNOWN,
                CameraOperationOutcome.STALE)) {
            CandidateKey requested = tupleKey(IMAGE_HD);
            CandidateKey previousCandidate = tupleKey(IMAGE_SD);
            Map<CandidateKey, VerificationOutcome> facts = Map.of(
                    videoKey(), VerificationOutcome.VERIFIED_PASS,
                    imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS,
                    imageKey(IMAGE_SD), VerificationOutcome.VERIFIED_PASS,
                    previousCandidate, VerificationOutcome.VERIFIED_PASS);
            FakeRuntime runtime = new FakeRuntime();
            runtime.script(CameraPipelineOperation.BIND_SESSION,
                    Script.of(operationOutcome, "operation_failed", 1), Script.pass());
            FakeStore store = new FakeStore(snapshot(
                    List.of(videoKey(), imageKey(IMAGE_HD), imageKey(IMAGE_SD),
                            requested, previousCandidate), facts,
                    Optional.of(new CameraCapabilityStore.SelectedRecordingProfile(
                            VideoCodec.H264, PIPELINE, tuple(IMAGE_SD)))));

            VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                    .execute(new VerifyCameraSelectionUseCase.Request(
                            store.snapshot, requested, 80, 0));

            VerificationOutcome expected = switch (operationOutcome) {
                case TRANSIENT_RETRYABLE -> VerificationOutcome.TRANSIENT_RETRYABLE;
                case BLOCKED_EXTERNAL -> VerificationOutcome.BLOCKED_EXTERNAL;
                case CANCELLED_UNKNOWN, STALE -> VerificationOutcome.CANCELLED_UNKNOWN;
                default -> throw new IllegalStateException();
            };
            assertEquals(expected, result.outcome(), operationOutcome.name());
            assertEquals(VerifyCameraSelectionUseCase.Completion.ROLLED_BACK,
                    result.completion(), operationOutcome.name());
            assertEquals(VerificationOutcome.UNKNOWN,
                    pipeline(result.snapshot()).outcome(requested), operationOutcome.name());
            assertEquals(2, runtime.bindContexts.size(), operationOutcome.name());
        }
    }

    @Test void confirmationPassDoesNotPersistInitialCandidateFailure() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        Map<CandidateKey, VerificationOutcome> facts = Map.of(
                videoKey(), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.of(CameraOperationOutcome.CANDIDATE_SUSPECT,
                        "session_config_failed", 1), Script.pass());
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), requested), facts,
                Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 90, 0));

        assertTrue(result.verified());
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                pipeline(result.snapshot()).outcome(requested));
        assertEquals(2, runtime.bindContexts.size());
    }

    @Test void wrongJpegOutputNeedsConfirmationBeforeImagePrune() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        FakeRuntime runtime = new FakeRuntime();
        runtime.invalidImageCaptures = 2;
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), requested), Map.of(),
                Optional.empty()));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 100, 0));

        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED, result.outcome());
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                pipeline(result.snapshot()).outcome(requested));
        assertEquals(VerificationOutcome.UNKNOWN,
                pipeline(result.snapshot()).outcome(imageKey(IMAGE_HD)));
        assertEquals(2, runtime.captureCalls);
    }

    @Test void rollbackReleasesPartialPreviousBindingAfterRebindFailure() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        CandidateKey previousCandidate = tupleKey(IMAGE_SD);
        SelectedRecordingProfileHolder previous = new SelectedRecordingProfileHolder(
                new CameraCapabilityStore.SelectedRecordingProfile(
                        VideoCodec.H264, PIPELINE, tuple(IMAGE_SD)));
        Map<CandidateKey, VerificationOutcome> facts = Map.of(
                videoKey(), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_SD), VerificationOutcome.VERIFIED_PASS,
                previousCandidate, VerificationOutcome.VERIFIED_PASS);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.of(CameraOperationOutcome.GLOBAL_FAILURE,
                        "camera_service_error", 1),
                Script.of(CameraOperationOutcome.CANDIDATE_SUSPECT,
                        "session_config_failed", 1));
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), imageKey(IMAGE_SD),
                        requested, previousCandidate), facts, Optional.of(previous.value)));

        VerifyCameraSelectionUseCase.Result result = usecase(runtime, store)
                .execute(new VerifyCameraSelectionUseCase.Request(
                        store.snapshot, requested, 105, 0));

        assertEquals(VerifyCameraSelectionUseCase.Completion.ROLLED_BACK,
                result.completion());
        assertTrue(result.recoveryRequired());
        assertEquals(List.of(105L, 106L), runtime.releaseGenerations());
    }

    @Test void confirmedBindFailureNeverStartsEncoderOrCapturesJpeg() {
        CandidateKey requested = tupleKey(IMAGE_HD);
        Map<CandidateKey, VerificationOutcome> facts = Map.of(
                videoKey(), VerificationOutcome.VERIFIED_PASS,
                imageKey(IMAGE_HD), VerificationOutcome.VERIFIED_PASS);
        FakeRuntime runtime = new FakeRuntime();
        runtime.script(CameraPipelineOperation.BIND_SESSION,
                Script.of(CameraOperationOutcome.CANDIDATE_SUSPECT,
                        "session_config_failed", 1),
                Script.of(CameraOperationOutcome.CANDIDATE_SUSPECT,
                        "session_config_failed", 1));
        FakeStore store = new FakeStore(snapshot(
                List.of(videoKey(), imageKey(IMAGE_HD), requested), facts,
                Optional.empty()));

        usecase(runtime, store).execute(new VerifyCameraSelectionUseCase.Request(
                store.snapshot, requested, 110, 0));

        assertEquals(0, runtime.startCalls);
        assertEquals(0, runtime.captureCalls);
    }
    private static VerifyCameraSelectionUseCase usecase(
            FakeRuntime runtime, FakeStore store) {
        return new VerifyCameraSelectionUseCase(runtime, store,
                new NoOpLogger(), runtime.clock,
                cameraId -> runtime.healthGeneration);
    }

    private static CandidateKey videoKey() {
        return videoKey(VIDEO);
    }

    private static CandidateKey videoKey(VideoMode video) {
        return CandidateKey.forVideo(CAMERA, VideoCodec.H264, PIPELINE, video);
    }

    private static CandidateKey imageKey(ImageMode image) {
        return CandidateKey.forImage(CAMERA, VideoCodec.H264, PIPELINE, image);
    }

    private static CandidateKey tupleKey(ImageMode image) {
        return tupleKey(VIDEO, image);
    }

    private static CandidateKey tupleKey(VideoMode video, ImageMode image) {
        return CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(video, image));
    }

    private static CaptureModeTuple tuple(ImageMode image) {
        return new CaptureModeTuple(VIDEO, image);
    }

    private static CameraCapabilityStore.Snapshot snapshot(
            List<CandidateKey> raw,
            Map<CandidateKey, VerificationOutcome> facts,
            Optional<CameraCapabilityStore.SelectedRecordingProfile> selection) {
        List<CandidateEvidence> evidence = facts.entrySet().stream()
                .map(value -> new CandidateEvidence(value.getKey(), value.getValue()))
                .toList();
        PipelineEvidence pipeline = new PipelineEvidence(CAMERA, VideoCodec.H264,
                PIPELINE, PipelineAvailability.AVAILABLE, raw, evidence);
        CameraCapabilityStore.CodecSnapshot codec =
                new CameraCapabilityStore.CodecSnapshot(VideoCodec.H264,
                        CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                        List.of(pipeline), Optional.empty());
        CameraCapabilityStore.CameraSnapshot camera =
                new CameraCapabilityStore.CameraSnapshot(CAMERA, "camera-signature",
                        List.of(codec), Optional.empty(), selection);
        return CameraCapabilityStore.Snapshot.current("hardware-signature",
                List.of(), List.of(camera));
    }

    private static CameraCapabilityStore.Snapshot withSensorOrientation(
            CameraCapabilityStore.Snapshot snapshot, int sensorOrientationDegrees) {
        CameraCapabilityStore.CameraSnapshot camera = camera(snapshot);
        CameraCapabilityStore.CameraSnapshot oriented =
                new CameraCapabilityStore.CameraSnapshot(camera.cameraId(),
                        camera.hardwareSignature(), camera.codecs(),
                        camera.selectedPipeline(), camera.selectedRecordingProfile(),
                        sensorOrientationDegrees);
        return new CameraCapabilityStore.Snapshot(snapshot.format(),
                snapshot.initializationState(), snapshot.hardwareSignature(),
                snapshot.cameraOrderOverride(), List.of(oriented));
    }

    private static PipelineEvidence pipeline(
            CameraCapabilityStore.Snapshot snapshot) {
        return snapshot.cameras().get(0).codecs().get(0).pipelines().get(0);
    }

    private static CameraCapabilityStore.CameraSnapshot camera(
            CameraCapabilityStore.Snapshot snapshot) {
        return snapshot.cameras().get(0);
    }

    private record SelectedRecordingProfileHolder(
            CameraCapabilityStore.SelectedRecordingProfile value) {}

    private static final class FakeStore implements CameraCapabilityStore {
        private final Snapshot snapshot;
        private final List<Snapshot> writes = new ArrayList<>();
        private boolean throwOnWrite;

        private FakeStore(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override public LoadResult load(Freshness freshness) {
            return LoadResult.loaded(snapshot);
        }

        @Override public void requestWrite(Snapshot snapshot) {
            if (throwOnWrite) throw new IllegalStateException("write_failed");
            writes.add(snapshot);
        }
    }

    private static final class NoOpLogger implements com.dvid.dcam.core.logging.application.port.Logger {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    }

    private static final class FakeClock implements CameraVerificationClock {
        private final long start;
        private long now;

        private FakeClock(long start) {
            this.start = start;
            this.now = start;
        }

        @Override public long elapsedRealtimeMillis() {
            return now;
        }

        private void advance(long millis) {
            now += millis;
        }
    }

    private record Script(CameraOperationOutcome outcome,
            CameraFailureClass failureClass, String detail,
            long advanceMillis) {
        private static Script pass() {
            return new Script(CameraOperationOutcome.PASS,
                    CameraFailureClass.NONE, "pass", 0);
        }

        private static Script of(CameraOperationOutcome outcome,
                String detail, long advanceMillis) {
            return new Script(outcome,
                    outcome.isCandidateFailure()
                            ? CameraFailureClass.UNKNOWN : CameraFailureClass.NONE,
                    detail, advanceMillis);
        }

        private static Script candidate(CameraFailureClass failureClass,
                String detail, long advanceMillis) {
            return new Script(CameraOperationOutcome.CANDIDATE_SUSPECT,
                    failureClass, detail, advanceMillis);
        }
    }

    private static final class FakeRuntime implements CameraRuntimeOperations {
        private final FakeClock clock = new FakeClock(100);
        private final Map<CameraPipelineOperation, Deque<Script>> scripts = new HashMap<>();
        private final List<CameraOperationContext> bindContexts = new ArrayList<>();
        private final List<Long> deadlines = new ArrayList<>();
        private final List<Long> releaseGenerations = new ArrayList<>();
        private boolean sessionBound;
        private boolean encoderActive;
        private boolean encoderFinalized;
        private boolean jpegCaptured;
        private boolean jpegCapturedWhileEncoderActive;
        private boolean lastVideoWrong;
        private boolean lastImageWrong;
        private boolean transposeImageCapture;
        private int invalidVideoFinalizations;
        private int invalidImageCaptures;
        private int finalizeCalls;
        private int startCalls;
        private int captureCalls;
        private long healthGeneration;
        private Runnable afterNextOperation;
        private String fpsDetail = "measuredFps=30;selectedFps=30";

        private void script(CameraPipelineOperation operation, Script... values) {
            scripts.put(operation, new ArrayDeque<>(List.of(values)));
        }

        private List<Long> bindGenerations() {
            return bindContexts.stream().map(value -> value.sessionGeneration()).toList();
        }

        private List<Long> releaseGenerations() {
            return List.copyOf(releaseGenerations);
        }

        @Override public CameraOperationResult bindSession(CameraOperationContext context) {
            bindContexts.add(context);
            deadlines.add(context.deadline().deadlineAtMillis());
            sessionBound = true;
            encoderActive = false;
            encoderFinalized = false;
            jpegCaptured = false;
            jpegCapturedWhileEncoderActive = false;
            lastVideoWrong = false;
            lastImageWrong = false;
            return execute(context, CameraPipelineOperation.BIND_SESSION);
        }

        @Override public CameraOperationResult updateSession(CameraOperationContext context) {
            return execute(context, CameraPipelineOperation.UPDATE_SESSION);
        }

        @Override public CameraOperationResult previewProgress(CameraOperationContext context) {
            return execute(context, CameraPipelineOperation.PREVIEW_PROGRESS);
        }

        @Override public CameraOperationResult startEncoder(CameraOperationContext context) {
            startCalls++;
            CameraOperationResult result = execute(context, CameraPipelineOperation.START_ENCODER);
            if (result.outcome() == CameraOperationOutcome.PASS) encoderActive = true;
            return result;
        }

        @Override public CameraOperationResult stopEncoder(CameraOperationContext context) {
            CameraOperationResult result = execute(context, CameraPipelineOperation.STOP_ENCODER);
            if (result.outcome() == CameraOperationOutcome.PASS) encoderActive = false;
            return result;
        }

        @Override public CameraOperationResult finalizeEncoder(CameraOperationContext context) {
            finalizeCalls++;
            CameraOperationResult result = execute(context,
                    CameraPipelineOperation.FINALIZE_ENCODER);
            if (result.outcome() == CameraOperationOutcome.PASS) {
                encoderFinalized = true;
                lastVideoWrong = invalidVideoFinalizations > 0;
                if (invalidVideoFinalizations > 0) invalidVideoFinalizations--;
            }
            return result;
        }

        @Override public CameraOperationResult captureJpeg(CameraOperationContext context) {
            captureCalls++;
            CameraOperationResult result = execute(context, CameraPipelineOperation.CAPTURE_JPEG);
            if (result.outcome() == CameraOperationOutcome.PASS) {
                jpegCaptured = true;
                jpegCapturedWhileEncoderActive = encoderActive;
                lastImageWrong = invalidImageCaptures > 0;
                if (invalidImageCaptures > 0) invalidImageCaptures--;
            }
            return result;
        }

        @Override public CameraOperationResult release(CameraOperationContext context) {
            releaseGenerations.add(context.sessionGeneration());
            CameraOperationResult result = execute(context, CameraPipelineOperation.RELEASE);
            if (result.outcome() == CameraOperationOutcome.PASS) sessionBound = false;
            return result;
        }
        @Override public CameraPipelineDiagnostics diagnostics(CameraOperationContext context) {
            CameraResolution video = context.tuple().videoMode().resolution().actual();
            CameraResolution image = context.tuple().imageMode().resolution().actual();
            if (lastVideoWrong) {
                video = new CameraResolution(video.width() + 1, video.height());
            }
            if (lastImageWrong) {
                image = new CameraResolution(image.width() + 1, image.height());
            }
            if (transposeImageCapture) {
                image = new CameraResolution(image.height(), image.width());
            }
            return new CameraPipelineDiagnostics(context, sessionBound,
                    sessionBound, encoderActive, encoderFinalized,
                    1, 1, encoderFinalized ? 1 : 0,
                    2, 2,
                    encoderFinalized ? Optional.of(video) : Optional.empty(),
                    jpegCaptured ? Optional.of(image) : Optional.empty(),
                    jpegCapturedWhileEncoderActive,
                    encoderFinalized ? Optional.of("video.mp4") : Optional.empty(),
                    jpegCaptured ? Optional.of("image.jpg") : Optional.empty(),
                    fpsDetail);
        }

        private CameraOperationResult execute(CameraOperationContext context,
                CameraPipelineOperation operation) {
            Script script = scripts.getOrDefault(operation, new ArrayDeque<>())
                    .pollFirst();
            if (script == null) script = Script.pass();
            clock.advance(script.advanceMillis());
            Runnable callback = afterNextOperation;
            afterNextOperation = null;
            if (callback != null) callback.run();
            String detail = script.detail() + ";operation=" + operation;
            return new CameraOperationResult(context, operation, script.outcome(),
                    script.failureClass(), script.advanceMillis(), detail);
        }
    }
}