package com.dvid.dcam.platform.camera.shared.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import com.dvid.dcam.feature.device.application.usecase.CameraCapabilitySnapshotUpdates;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase;
import com.dvid.dcam.feature.device.application.usecase.CompareCameraPipelinesUseCase.Invocation;
import com.dvid.dcam.feature.device.application.usecase.VerifyCameraSelectionUseCase;
import com.dvid.dcam.feature.device.application.usecase.VerifyStandaloneImageUseCase;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.CameraScope;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.FrozenEnvironment;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineRunStatus;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleStage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SharedCameraPipelineBenchmarkRunnerTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("benchmark-test-pipeline");

    @Test
    void selectedCandidateMismatchReleasesReturnedBinding() {
        CaptureModeTuple tuple = tuple(1280, 720, 1280, 720);
        CaptureModeTuple wrongTuple = tuple(640, 480, 640, 480);
        CandidateKey requested = candidate(tuple);
        CandidateKey wrong = candidate(wrongTuple);
        FakeRuntime runtime = new FakeRuntime(true);
        SharedCameraPipelineBenchmarkRunner runner = runner(
                Map.of(CAMERA, frozenCoverage(requested)),
                (ignoredRuntime, ignoredStore, request) -> {
                    assertEquals(90, request.snapshot().cameras().get(0)
                            .sensorOrientationDegrees());
                    return result(request, wrong, request.snapshot(), context(tuple),
                            VerificationOutcome.VERIFIED_PASS);
                },
                environment -> new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                        true, "ready"), runtime);

        CompareCameraPipelinesUseCase.Measurement measurement = runner.measure(
                plan(tuple), CAMERA, tuple, new Invocation(
                        CompareCameraPipelinesUseCase.PipelineSide.A, false, 0, 0), () -> false);

        assertFalse(measurement.complete());
        assertTrue(measurement.detail().contains("measurement_verify=UNKNOWN"));
        assertEquals(1, runtime.releaseCalls.get());
        assertTrue(runner.release());
    }

    @Test
    void previewProgressFailureReleasesBindingAndLeavesNoActiveOwner() {
        CaptureModeTuple tuple = tuple(1280, 720, 1280, 720);
        CandidateKey requested = candidate(tuple);
        FakeRuntime runtime = new FakeRuntime(false);
        SharedCameraPipelineBenchmarkRunner runner = runner(
                Map.of(CAMERA, frozenCoverage(requested)),
                (ignoredRuntime, ignoredStore, request) -> result(request, requested,
                        verifiedSnapshot(request.snapshot(), requested), context(tuple),
                        VerificationOutcome.VERIFIED_PASS),
                environment -> new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                        true, "ready"), runtime);

        CompareCameraPipelinesUseCase.Measurement measurement = runner.measure(
                plan(tuple), CAMERA, tuple, new Invocation(
                        CompareCameraPipelinesUseCase.PipelineSide.A, false, 0, 0), () -> false);

        assertFalse(measurement.complete());
        assertEquals(1, runtime.previewCalls.get());
        assertEquals(1, runtime.releaseCalls.get());
        assertTrue(runner.release());
    }

    @Test
    void environmentRejectionStopsRemainingTuplesWithoutClaimingCompletion() {
        CaptureModeTuple first = tuple(1280, 720, 1280, 720);
        CaptureModeTuple second = tuple(1920, 1080, 1920, 1080);
        CandidateKey firstCandidate = candidate(first);
        CandidateKey secondCandidate = candidate(second);
        FakeRuntime runtime = new FakeRuntime(true);
        AtomicInteger validations = new AtomicInteger();
        AtomicInteger verifications = new AtomicInteger();
        SharedCameraPipelineBenchmarkRunner runner = runner(
                Map.of(CAMERA, frozenCoverage(firstCandidate, secondCandidate)),
                (ignoredRuntime, ignoredStore, request) -> {
                    verifications.incrementAndGet();
                    CandidateKey selected = request.requestedCandidate();
                    return result(request, selected,
                            verifiedSnapshot(request.snapshot(), selected), Optional.empty(),
                            VerificationOutcome.VERIFIED_PASS);
                },
                environment -> validations.incrementAndGet() <= 4
                        ? new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                        true, "ready")
                        : new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                        false, "thermal_gate_changed"), runtime);

        CompareCameraPipelinesUseCase.PipelineRun run = runner.runCoverage(
                plan(first, second), () -> false, ignored -> {});

        assertEquals(PipelineRunStatus.INCOMPLETE, run.status());
        assertEquals(1, verifications.get());
        assertEquals(5, validations.get());
        assertEquals(2, run.coverage(CAMERA).outcomes().size());
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                run.coverage(CAMERA).outcomes().get(0).outcome());
        assertEquals(VerificationOutcome.UNKNOWN,
                run.coverage(CAMERA).outcomes().get(1).outcome());
        assertTrue(run.coverage(CAMERA).outcomes().get(1).reason()
                .contains("environment=thermal_gate_changed"));
    }

    @Test void standaloneImageStageRunsBeforeDependentTuple() {
        CaptureModeTuple tuple = tuple(1280, 720, 1920, 1080);
        CandidateKey video = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, tuple.videoMode());
        CandidateKey image = CandidateKey.forImage(
                CAMERA, VideoCodec.H264, PIPELINE, tuple.imageMode());
        CandidateKey combo = candidate(tuple);
        FakeRuntime runtime = new FakeRuntime(true);
        List<String> order = new java.util.ArrayList<>();
        SharedCameraPipelineBenchmarkRunner runner = runner(
                Map.of(CAMERA, frozenCoverage(video, image, combo)),
                (ignoredRuntime, ignoredStore, request) -> {
                    order.add("tuple");
                    return result(request, combo,
                            verifiedSnapshot(request.snapshot(), combo), Optional.empty(),
                            VerificationOutcome.VERIFIED_PASS);
                }, (ignoredRuntime, request) -> {
                    order.add("image");
                    return standaloneResult(request, VerificationOutcome.VERIFIED_PASS);
                }, environment -> new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                        true, "ready"), runtime);

        CompareCameraPipelinesUseCase.PipelineRun run = runner.runCoverage(
                plan(tuple), () -> false, ignored -> {});

        assertEquals(PipelineRunStatus.COMPLETE, run.status());
        assertEquals(List.of("image", "tuple"), order);
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                run.coverage(CAMERA).outcomes().get(0).outcome());
    }

    @Test void standaloneImageFailureDoesNotSkipTupleVerification() {
        CaptureModeTuple tuple = tuple(1280, 720, 1920, 1080);
        CandidateKey video = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, tuple.videoMode());
        CandidateKey image = CandidateKey.forImage(
                CAMERA, VideoCodec.H264, PIPELINE, tuple.imageMode());
        CandidateKey combo = candidate(tuple);
        FakeRuntime runtime = new FakeRuntime(true);
        AtomicInteger tupleVerifications = new AtomicInteger();
        SharedCameraPipelineBenchmarkRunner runner = runner(
                Map.of(CAMERA, frozenCoverage(video, image, combo)),
                (ignoredRuntime, ignoredStore, request) -> {
                    tupleVerifications.incrementAndGet();
                    return result(request, combo,
                            verifiedSnapshot(request.snapshot(), combo), Optional.empty(),
                            VerificationOutcome.VERIFIED_PASS);
                }, (ignoredRuntime, request) -> standaloneResult(
                        request, VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                environment -> new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                        true, "ready"), runtime);

        CompareCameraPipelinesUseCase.PipelineRun run = runner.runCoverage(
                plan(tuple), () -> false, ignored -> {});

        assertEquals(PipelineRunStatus.COMPLETE, run.status());
        assertEquals(1, tupleVerifications.get());
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                run.coverage(CAMERA).outcomes().get(0).outcome());
        assertEquals(TupleStage.UNKNOWN,
                run.coverage(CAMERA).outcomes().get(0).stage());
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                run.coverage(CAMERA).evidence().outcome(image));
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                run.coverage(CAMERA).evidence().outcome(combo));
    }

    @Test void sameLabelDifferentActualTuplesAreAllDeepVerified() {
        CaptureModeTuple first = tuple(640, 480, 1920, 1080);
        CaptureModeTuple second = tuple(640, 480, 1920, 1088);
        FakeRuntime runtime = new FakeRuntime(true);
        List<CaptureModeTuple> verified = new java.util.ArrayList<>();
        SharedCameraPipelineBenchmarkRunner runner = runner(
                Map.of(CAMERA, frozenCoverage(candidate(first), candidate(second))),
                (ignoredRuntime, ignoredStore, request) -> {
                    CandidateKey requested = request.requestedCandidate();
                    verified.add(requested.tuple().orElseThrow());
                    return result(request, requested,
                            verifiedSnapshot(request.snapshot(), requested), Optional.empty(),
                            VerificationOutcome.VERIFIED_PASS);
                }, environment -> new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                true, "ready"), runtime);

        CompareCameraPipelinesUseCase.PipelineRun run = runner.runCoverage(
                plan(first, second), () -> false, ignored -> {});

        assertEquals(PipelineRunStatus.COMPLETE, run.status());
        assertEquals(2, verified.size());
        assertEquals(Set.of(first, second), Set.copyOf(verified));
        assertTrue(run.coverage(CAMERA).outcomes().stream()
                .allMatch(outcome -> outcome.outcome() == VerificationOutcome.VERIFIED_PASS));
    }

    @Test void unionTupleMissingFromProfileFastScanStillDeepVerifies() {
        CaptureModeTuple tuple = tuple(1280, 720, 1920, 1080);
        CandidateKey combined = candidate(tuple);
        FakeRuntime runtime = new FakeRuntime(true);
        AtomicInteger tupleVerifications = new AtomicInteger();
        SharedCameraPipelineBenchmarkRunner runner = runner(
                Map.of(CAMERA, frozenCoverage()),
                (ignoredRuntime, ignoredStore, request) -> {
                    tupleVerifications.incrementAndGet();
                    return result(request, combined,
                            verifiedSnapshot(request.snapshot(), combined), Optional.empty(),
                            VerificationOutcome.VERIFIED_PASS);
                }, environment -> new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                        true, "ready"), runtime);

        CompareCameraPipelinesUseCase.PipelineRun run = runner.runCoverage(
                plan(tuple), () -> false, ignored -> {});

        assertEquals(PipelineRunStatus.COMPLETE, run.status());
        assertEquals(1, tupleVerifications.get());
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                run.coverage(CAMERA).outcomes().get(0).outcome());
    }

    @Test void unionStandaloneImageMissingFromProfileFastScanUsesUnionVideoBinding() {
        CaptureModeTuple tuple = tuple(1280, 720, 1280, 720);
        ImageMode standalone = tuple(1280, 720, 1920, 1080).imageMode();
        CandidateKey combined = candidate(tuple);
        FakeRuntime runtime = new FakeRuntime(true);
        java.util.Set<ImageMode> verifiedImages = new java.util.HashSet<>();
        SharedCameraPipelineBenchmarkRunner runner = runner(
                Map.of(CAMERA, frozenCoverage()),
                (ignoredRuntime, ignoredStore, request) -> result(request, combined,
                        verifiedSnapshot(request.snapshot(), combined), Optional.empty(),
                        VerificationOutcome.VERIFIED_PASS),
                (ignoredRuntime, request) -> {
                    verifiedImages.add(request.imageCandidate().imageMode().orElseThrow());
                    assertEquals(tuple.videoMode(), request.bindingTuple().videoMode());
                    return standaloneResult(request, VerificationOutcome.VERIFIED_PASS);
                }, environment -> new SharedCameraPipelineBenchmarkRunner.EnvironmentValidation(
                        true, "ready"), runtime);

        CompareCameraPipelinesUseCase.PipelineRun run = runner.runCoverage(
                plan(Set.of(tuple.imageMode(), standalone), tuple),
                () -> false, ignored -> {});

        assertEquals(PipelineRunStatus.COMPLETE, run.status());
        assertEquals(Set.of(tuple.imageMode(), standalone), verifiedImages);
    }

    private static SharedCameraPipelineBenchmarkRunner runner(
            Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> fastCoverage,
            SharedCameraPipelineBenchmarkRunner.CandidateVerifier verifier,
            SharedCameraPipelineBenchmarkRunner.EnvironmentValidator environmentValidator,
            FakeRuntime runtime) {
        return runner(fastCoverage, verifier,
                (ignoredRuntime, request) -> standaloneResult(
                        request, VerificationOutcome.VERIFIED_PASS),
                environmentValidator, runtime);
    }

    private static SharedCameraPipelineBenchmarkRunner runner(
            Map<CameraId, SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage> fastCoverage,
            SharedCameraPipelineBenchmarkRunner.CandidateVerifier verifier,
            SharedCameraPipelineBenchmarkRunner.StandaloneImageVerifier imageVerifier,
            SharedCameraPipelineBenchmarkRunner.EnvironmentValidator environmentValidator,
            FakeRuntime runtime) {
        return new SharedCameraPipelineBenchmarkRunner(fastCoverage, PIPELINE, runtime, verifier,
                imageVerifier, new NoOpLogger(), camera -> 0L,
                environment(), environmentValidator, CameraBenchmarkTelemetry.UNAVAILABLE);
    }

    private static SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage frozenCoverage(
            CandidateKey... candidates) {
        return new SharedCameraPipelineBenchmarkRunner.FrozenFastCoverage(
                new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                        PipelineAvailability.AVAILABLE, List.of(candidates), List.of()), 1, 90);
    }

    private static VerifyStandaloneImageUseCase.Result standaloneResult(
            VerifyStandaloneImageUseCase.Request request,
            VerificationOutcome outcome) {
        CameraCapabilityStore.Snapshot snapshot =
                CameraCapabilitySnapshotUpdates.withStandaloneImageEvidence(
                        request.snapshot(), request.imageCandidate(), outcome);
        return new VerifyStandaloneImageUseCase.Result(
                outcome, snapshot, true, "standalone test result");
    }

    private static VerifyCameraSelectionUseCase.Result result(
            VerifyCameraSelectionUseCase.Request request,
            CandidateKey selected,
            CameraCapabilityStore.Snapshot snapshot,
            CameraOperationContext active,
            VerificationOutcome outcome) {
        return result(request, selected, snapshot, Optional.of(active), outcome);
    }

    private static VerifyCameraSelectionUseCase.Result result(
            VerifyCameraSelectionUseCase.Request request,
            CandidateKey selected,
            CameraCapabilityStore.Snapshot snapshot,
            Optional<CameraOperationContext> active,
            VerificationOutcome outcome) {
        return new VerifyCameraSelectionUseCase.Result(
                VerifyCameraSelectionUseCase.Completion.REQUESTED_VERIFIED,
                outcome, snapshot, Optional.of(selected), Optional.empty(), active,
                List.of(), false, false, "test result");
    }

    private static CameraPipelineBenchmarkPlan plan(CaptureModeTuple... tuples) {
        return plan(java.util.Arrays.stream(tuples)
                .map(CaptureModeTuple::imageMode)
                .collect(java.util.stream.Collectors.toSet()), tuples);
    }

    private static CameraPipelineBenchmarkPlan plan(
            Set<ImageMode> images, CaptureModeTuple... tuples) {
        return new CameraPipelineBenchmarkPlan(environment(),
                List.of(new CameraScope(CAMERA, "camera-hardware", images, Set.of(tuples))),
                CameraPipelineBenchmarkPlan.BenchmarkProtocol.defaults());
    }

    private static FrozenEnvironment environment() {
        return new FrozenEnvironment("hardware", Map.of(CAMERA, "camera-hardware"),
                VideoCodec.H264, "avc-profile-level-bitrate", "sensor-crop-rotation",
                5_000, "cache/benchmark", "thermal-not-throttled");
    }

    private static CandidateKey candidate(CaptureModeTuple tuple) {
        return CandidateKey.forTuple(CAMERA, VideoCodec.H264, PIPELINE, tuple);
    }

    private static CameraOperationContext context(CaptureModeTuple tuple) {
        return new CameraOperationContext(CAMERA, PIPELINE, VideoCodec.H264, tuple,
                1, 0, CameraOperationDeadline.forCandidate(0));
    }

    private static CameraCapabilityStore.Snapshot verifiedSnapshot(
            CameraCapabilityStore.Snapshot source, CandidateKey candidate) {
        CameraCapabilityStore.CameraSnapshot camera = source.cameras().get(0);
        CameraCapabilityStore.CodecSnapshot codec = camera.codecs().get(0);
        PipelineEvidence evidence = codec.pipelines().get(0);
        PipelineEvidence verified = new PipelineEvidence(CAMERA, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, evidence.rawFastCandidates(),
                List.of(new CandidateEvidence(candidate, VerificationOutcome.VERIFIED_PASS)));
        CameraCapabilityStore.CodecSnapshot updatedCodec = new CameraCapabilityStore.CodecSnapshot(
                VideoCodec.H264, CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                List.of(verified), Optional.empty());
        CameraCapabilityStore.CameraSnapshot updatedCamera =
                new CameraCapabilityStore.CameraSnapshot(CAMERA, "camera-hardware",
                        List.of(updatedCodec), Optional.empty(), Optional.empty(),
                        source.cameras().get(0).sensorOrientationDegrees());
        return CameraCapabilityStore.Snapshot.current(source.hardwareSignature(), List.of(),
                List.of(updatedCamera));
    }

    private static CaptureModeTuple tuple(int videoWidth, int videoHeight,
            int imageWidth, int imageHeight) {
        return new CaptureModeTuple(
                new VideoMode(new StandardResolution(labelFor(videoWidth, videoHeight),
                        new com.dvid.dcam.feature.device.domain.camera.CameraResolution(
                                videoWidth, videoHeight)), 30),
                new ImageMode(new StandardResolution(labelFor(imageWidth, imageHeight),
                        new com.dvid.dcam.feature.device.domain.camera.CameraResolution(
                                imageWidth, imageHeight))));
    }

    private static StandardResolutionLabel labelFor(int width, int height) {
        if (width == 640 && height == 480) return StandardResolutionLabel.SD;
        if (width == 1280 && height == 720) return StandardResolutionLabel.HD;
        if (width == 1920 && (height == 1080 || height == 1088)) return StandardResolutionLabel.FHD;
        return StandardResolutionLabel.MAX;
    }

    private static final class FakeRuntime implements CameraRuntimeOperations {
        private final boolean previewPass;
        private final AtomicInteger previewCalls = new AtomicInteger();
        private final AtomicInteger releaseCalls = new AtomicInteger();

        private FakeRuntime(boolean previewPass) {
            this.previewPass = previewPass;
        }

        @Override public CameraOperationResult bindSession(CameraOperationContext context) {
            return pass(context, CameraPipelineOperation.BIND_SESSION);
        }

        @Override public CameraOperationResult previewProgress(CameraOperationContext context) {
            previewCalls.incrementAndGet();
            return previewPass
                    ? pass(context, CameraPipelineOperation.PREVIEW_PROGRESS)
                    : fail(context, CameraPipelineOperation.PREVIEW_PROGRESS);
        }

        @Override public CameraOperationResult startEncoder(CameraOperationContext context) {
            return pass(context, CameraPipelineOperation.START_ENCODER);
        }

        @Override public CameraOperationResult stopEncoder(CameraOperationContext context) {
            return pass(context, CameraPipelineOperation.STOP_ENCODER);
        }

        @Override public CameraOperationResult finalizeEncoder(CameraOperationContext context) {
            return pass(context, CameraPipelineOperation.FINALIZE_ENCODER);
        }

        @Override public CameraOperationResult captureJpeg(CameraOperationContext context) {
            return pass(context, CameraPipelineOperation.CAPTURE_JPEG);
        }

        @Override public CameraOperationResult release(CameraOperationContext context) {
            releaseCalls.incrementAndGet();
            return pass(context, CameraPipelineOperation.RELEASE);
        }

        @Override public CameraPipelineDiagnostics diagnostics(CameraOperationContext context) {
            return new CameraPipelineDiagnostics(context, true, true, false, true,
                    1, 1, 1, 2, 2, Optional.empty(), Optional.empty(), false,
                    Optional.empty(), Optional.empty(), "test diagnostics");
        }

        private static CameraOperationResult pass(CameraOperationContext context,
                CameraPipelineOperation operation) {
            return new CameraOperationResult(context, operation, CameraOperationOutcome.PASS,
                    1, "pass");
        }

        private static CameraOperationResult fail(CameraOperationContext context,
                CameraPipelineOperation operation) {
            return new CameraOperationResult(context, operation,
                    CameraOperationOutcome.TIMEOUT_UNKNOWN, 1, "preview timeout");
        }
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    }
}
