package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.CameraScope;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkPlan.FrozenEnvironment;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PerformanceSample;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineCoverage;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.PipelineRunStatus;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.ResourceSample;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineBenchmarkReport.TupleStage;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparison;
import com.dvid.dcam.feature.device.domain.camera.PipelineBenchmarkSchedule;
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
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class CompareCameraPipelinesUseCaseTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE_A =
            new VerificationPipelineId("pipeline-a");
    private static final VerificationPipelineId PIPELINE_B =
            new VerificationPipelineId("pipeline-b");

    @Test
    void publishesOneCompleteComparisonAfterBalancedBenchmark() {
        CaptureModeTuple tuple = tuple();
        Snapshot baseline = baseline(tuple);
        CameraPipelineBenchmarkPlan plan = plan(tuple);
        RecordingStore store = new RecordingStore();
        FakeRunner runnerA = new FakeRunner(PIPELINE_A, tuple, 10, PipelineRunStatus.COMPLETE);
        FakeRunner runnerB = new FakeRunner(PIPELINE_B, tuple, 20, PipelineRunStatus.COMPLETE);

        CompareCameraPipelinesUseCase.Result result = useCase(store, runnerA, runnerB)
                .execute(new CompareCameraPipelinesUseCase.Request(baseline, plan));

        assertEquals(CameraPipelineBenchmarkReport.Status.COMPLETE, result.report().status());
        assertTrue(result.report().publishedDurably());
        assertEquals(1, store.writeCount.get());
        assertEquals(3, runnerA.measurements.size());
        assertEquals(3, runnerB.measurements.size());
        var comparison = result.report().cameras().get(0).comparison();
        assertEquals(PipelineComparison.Coverage.EQUAL, comparison.coverage());
        assertEquals(PipelineComparison.Recommendation.PIPELINE_A, comparison.recommendation());
        assertEquals(PipelineComparison.RecommendationReason.PERFORMANCE,
                comparison.recommendationReason());
        var selected = store.published.cameras().get(0)
                .selectedPipeline().orElseThrow();
        assertEquals(CameraCapabilityStore.PipelineSelectionMode.AUTO, selected.mode());
        assertEquals(PIPELINE_A, selected.pipelineId());
        assertEquals(CameraCapabilityStore.PipelineDecisionStatus.VERIFIED_COMPLETE,
                selected.decisionStatus());
        assertEquals(CameraCapabilityStore.PipelineDecisionReason.PERFORMANCE,
                selected.decisionReason());
        assertEquals(1, result.report().cameras().get(0).performance().size());
        assertEquals(1, result.report().cameras().get(0).performance()
                .get(0).fastScanA().sampleCount());
        assertTrue(result.report().cameras().get(0).performance()
                .get(0).fastScanA().p95().isEmpty());
    }

    @Test
    void completeComparisonCreatesAutoSelectionWhenBaselineHasNone() {
        CaptureModeTuple tuple = tuple();
        Snapshot initial = baseline(tuple);
        CameraSnapshot camera = initial.cameras().get(0);
        Snapshot baseline = Snapshot.current(initial.hardwareSignature(),
                initial.cameraOrderOverride(), List.of(new CameraSnapshot(
                camera.cameraId(), camera.hardwareSignature(), camera.codecs(),
                Optional.empty(), camera.selectedRecordingProfile())));
        RecordingStore store = new RecordingStore();

        useCase(store,
                new FakeRunner(PIPELINE_A, tuple, 10, PipelineRunStatus.COMPLETE),
                new FakeRunner(PIPELINE_B, tuple, 20, PipelineRunStatus.COMPLETE))
                .execute(new CompareCameraPipelinesUseCase.Request(baseline, plan(tuple)));

        var selected = store.published.cameras().get(0)
                .selectedPipeline().orElseThrow();
        assertEquals(CameraCapabilityStore.PipelineSelectionMode.AUTO, selected.mode());
        assertEquals(PIPELINE_A, selected.pipelineId());
        assertEquals(CameraCapabilityStore.PipelineDecisionStatus.VERIFIED_COMPLETE,
                selected.decisionStatus());
    }

    @Test
    void recommendationChangeClearsStaleProfileAndReusableState() {
        CaptureModeTuple tuple = tuple();
        PipelineEvidence evidenceA = verifiedEvidence(PIPELINE_A, tuple);
        PipelineEvidence evidenceB = verifiedEvidence(PIPELINE_B, tuple);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264,
                CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                List.of(evidenceA, evidenceB), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-hardware", List.of(codec),
                Optional.of(CameraCapabilityStore.SelectedPipeline.autoFast(PIPELINE_A,
                        CameraCapabilityStore.PipelineDecisionReason.FINAL_TIE_A)),
                Optional.of(new CameraCapabilityStore.SelectedRecordingProfile(
                        VideoCodec.H264, PIPELINE_A, tuple)));
        Snapshot baseline = Snapshot.current("hardware", List.of(), List.of(camera))
                .withInitializationState(
                        CameraCapabilityStore.InitializationState.READY_REUSABLE);
        RecordingStore store = new RecordingStore();

        useCase(store,
                new FakeRunner(PIPELINE_A, tuple, 20, PipelineRunStatus.COMPLETE),
                new FakeRunner(PIPELINE_B, tuple, 10, PipelineRunStatus.COMPLETE))
                .execute(new CompareCameraPipelinesUseCase.Request(baseline, plan(tuple)));

        CameraSnapshot published = store.published.cameras().get(0);
        assertEquals(CameraCapabilityStore.InitializationState.INCOMPLETE,
                store.published.initializationState());
        assertEquals(PIPELINE_B, published.selectedPipeline().orElseThrow().pipelineId());
        assertTrue(published.selectedRecordingProfile().isEmpty());
    }

    @Test
    void incompleteRunLeavesDurableSnapshotUntouched() {
        CaptureModeTuple tuple = tuple();
        RecordingStore store = new RecordingStore();
        FakeRunner runnerA = new FakeRunner(PIPELINE_A, tuple, 10, PipelineRunStatus.INCOMPLETE);
        FakeRunner runnerB = new FakeRunner(PIPELINE_B, tuple, 20, PipelineRunStatus.COMPLETE);

        CompareCameraPipelinesUseCase.Result result = useCase(store, runnerA, runnerB)
                .execute(new CompareCameraPipelinesUseCase.Request(baseline(tuple), plan(tuple)));

        assertEquals(CameraPipelineBenchmarkReport.Status.INCOMPLETE, result.report().status());
        assertFalse(result.report().publishedDurably());
        assertEquals(0, store.writeCount.get());
        assertEquals(1, runnerB.runCount.get());
    }

    @Test
    void cleanupFailureStopsNextPipelineAndLeavesSnapshotUntouched() {
        CaptureModeTuple tuple = tuple();
        RecordingStore store = new RecordingStore();
        FakeRunner runnerA = new FakeRunner(PIPELINE_A, tuple, 10, PipelineRunStatus.INCOMPLETE, false);
        FakeRunner runnerB = new FakeRunner(PIPELINE_B, tuple, 20, PipelineRunStatus.COMPLETE);

        CompareCameraPipelinesUseCase.Result result = useCase(store, runnerA, runnerB)
                .execute(new CompareCameraPipelinesUseCase.Request(baseline(tuple), plan(tuple)));

        assertEquals(CameraPipelineBenchmarkReport.Status.INCOMPLETE, result.report().status());
        assertEquals(0, runnerB.runCount.get());
        assertEquals(2, runnerA.releaseCount.get());
        assertEquals(0, store.writeCount.get());
    }

    @Test
    void storagePublishFailureIsIncomplete() {
        CaptureModeTuple tuple = tuple();
        RecordingStore store = new RecordingStore(true);
        FakeRunner runnerA = new FakeRunner(PIPELINE_A, tuple, 10, PipelineRunStatus.COMPLETE);
        FakeRunner runnerB = new FakeRunner(PIPELINE_B, tuple, 20, PipelineRunStatus.COMPLETE);

        CompareCameraPipelinesUseCase.Result result = useCase(store, runnerA, runnerB)
                .execute(new CompareCameraPipelinesUseCase.Request(baseline(tuple), plan(tuple)));

        assertEquals(CameraPipelineBenchmarkReport.Status.INCOMPLETE, result.report().status());
        assertFalse(result.report().publishedDurably());
        assertTrue(result.freshSnapshot().isEmpty());
    }

    @Test
    void cooldownRunsBetweenEveryBenchmarkInvocation() {
        CaptureModeTuple tuple = tuple();
        RecordingStore store = new RecordingStore();
        FakeRunner runnerA = new FakeRunner(PIPELINE_A, tuple, 10, PipelineRunStatus.COMPLETE);
        FakeRunner runnerB = new FakeRunner(PIPELINE_B, tuple, 20, PipelineRunStatus.COMPLETE);
        List<Long> cooldowns = new java.util.ArrayList<>();
        CameraPipelineBenchmarkPlan benchmarkPlan = plan(tuple,
                new CameraPipelineBenchmarkPlan.BenchmarkProtocol(
                        2, CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A, 7));

        CompareCameraPipelinesUseCase.Result result = new CompareCameraPipelinesUseCase(
                store, runnerA, runnerB, new NoOpLogger(), cooldowns::add)
                .execute(new CompareCameraPipelinesUseCase.Request(baseline(tuple), benchmarkPlan));

        assertEquals(CameraPipelineBenchmarkReport.Status.COMPLETE, result.report().status());
        assertEquals(List.of(7L, 7L, 7L, 7L, 7L, 7L, 7L, 7L, 7L), cooldowns);
    }

    @Test
    void frozenInventoryMustMatchCameraScopes() {
        CaptureModeTuple tuple = tuple();
        FrozenEnvironment environment = new FrozenEnvironment("hardware",
                Map.of(CAMERA, "camera-hardware", new CameraId("1"), "other-camera"),
                VideoCodec.H264, "avc-profile-level-bitrate", "sensor-crop-rotation",
                5_000, "cache/benchmark", "thermal-not-throttled");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                new CameraPipelineBenchmarkPlan(environment,
                        List.of(new CameraScope(CAMERA, "camera-hardware", Set.of(tuple))),
                        CameraPipelineBenchmarkPlan.BenchmarkProtocol.defaults()));
    }

    @Test
    void baselineCameraInventoryMustExactlyMatchFrozenInventory() {
        CaptureModeTuple tuple = tuple();
        RecordingStore store = new RecordingStore();
        FakeRunner runnerA = new FakeRunner(PIPELINE_A, tuple, 10, PipelineRunStatus.COMPLETE);
        FakeRunner runnerB = new FakeRunner(PIPELINE_B, tuple, 20, PipelineRunStatus.COMPLETE);
        Snapshot baseline = baseline(tuple);
        CameraSnapshot extra = new CameraSnapshot(new CameraId("1"), "other-camera",
                List.of(), Optional.empty(), Optional.empty());
        Snapshot withExtraCamera = Snapshot.current(baseline.hardwareSignature(), List.of(),
                List.of(baseline.cameras().get(0), extra));

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> useCase(store, runnerA, runnerB)
                        .execute(new CompareCameraPipelinesUseCase.Request(
                                withExtraCamera, plan(tuple))));

        assertEquals("benchmark camera inventory changed", error.getMessage());
        assertEquals(0, runnerA.runCount.get());
        assertEquals(0, runnerB.runCount.get());
        assertEquals(0, store.writeCount.get());
    }

    @Test
    void cancellationAfterLastMeasurementPreventsDurablePublish() {
        CaptureModeTuple tuple = tuple();
        RecordingStore store = new RecordingStore();
        java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();
        AtomicInteger pipelineAMeasurements = new AtomicInteger();
        FakeRunner runnerA = new FakeRunner(PIPELINE_A, tuple, 10,
                PipelineRunStatus.COMPLETE, true, () -> {
                    if (pipelineAMeasurements.incrementAndGet() == 3) cancelled.set(true);
                });
        FakeRunner runnerB = new FakeRunner(PIPELINE_B, tuple, 20, PipelineRunStatus.COMPLETE);

        CompareCameraPipelinesUseCase.Result result = useCase(store, runnerA, runnerB)
                .execute(new CompareCameraPipelinesUseCase.Request(baseline(tuple), plan(tuple)),
                        cancelled::get, progress -> {});

        assertEquals(CameraPipelineBenchmarkReport.Status.CANCELLED, result.report().status());
        assertEquals(0, store.writeCount.get());
    }

    @Test
    void progressCallbackFailureDoesNotChangeCompletedOutcome() {
        CaptureModeTuple tuple = tuple();
        RecordingStore store = new RecordingStore();
        FakeRunner runnerA = new FakeRunner(PIPELINE_A, tuple, 10, PipelineRunStatus.COMPLETE);
        FakeRunner runnerB = new FakeRunner(PIPELINE_B, tuple, 20, PipelineRunStatus.COMPLETE);

        CompareCameraPipelinesUseCase.Result result = useCase(store, runnerA, runnerB)
                .execute(new CompareCameraPipelinesUseCase.Request(baseline(tuple), plan(tuple)),
                        () -> false, progress -> {
                            throw new IllegalStateException("observer failed");
                        });

        assertEquals(CameraPipelineBenchmarkReport.Status.COMPLETE, result.report().status());
        assertEquals(1, store.writeCount.get());
    }

    @Test
    void abbaScheduleKeepsWarmupOutsideMeasuredBlocks() {
        var protocol = new CameraPipelineBenchmarkPlan.BenchmarkProtocol(
                2, CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_B, 0);
        var invocations = PipelineBenchmarkSchedule.abba(protocol);

        assertEquals(List.of(
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_B, true, 0, 0),
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A, true, 0, 1),
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_B, false, 1, 0),
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A, false, 1, 1),
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A, false, 1, 2),
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_B, false, 1, 3),
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_B, false, 2, 0),
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A, false, 2, 1),
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_A, false, 2, 2),
                new PipelineBenchmarkSchedule.Invocation(
                        CameraPipelineBenchmarkPlan.InitialOrder.PIPELINE_B, false, 2, 3)), invocations);
    }

    private static CompareCameraPipelinesUseCase useCase(RecordingStore store,
            FakeRunner runnerA, FakeRunner runnerB) {
        return new CompareCameraPipelinesUseCase(store, runnerA, runnerB,
                new NoOpLogger(), ignored -> {});
    }

    private static CameraPipelineBenchmarkPlan plan(CaptureModeTuple tuple) {
        return plan(tuple, CameraPipelineBenchmarkPlan.BenchmarkProtocol.defaults());
    }

    private static CameraPipelineBenchmarkPlan plan(CaptureModeTuple tuple,
            CameraPipelineBenchmarkPlan.BenchmarkProtocol protocol) {
        return new CameraPipelineBenchmarkPlan(
                new FrozenEnvironment("hardware", Map.of(CAMERA, "camera-hardware"),
                        VideoCodec.H264, "avc-profile-level-bitrate", "sensor-crop-rotation",
                        5_000, "cache/benchmark", "thermal-not-throttled"),
                List.of(new CameraScope(CAMERA, "camera-hardware", Set.of(tuple))), protocol);
    }

    private static Snapshot baseline(CaptureModeTuple tuple) {
        PipelineEvidence a = evidence(PIPELINE_A, tuple);
        PipelineEvidence b = evidence(PIPELINE_B, tuple);
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264,
                CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(), List.of(a, b), Optional.empty());
        CameraSnapshot camera = new CameraSnapshot(CAMERA, "camera-hardware", List.of(codec),
                Optional.of(CameraCapabilityStore.SelectedPipeline.autoFast(PIPELINE_A,
                        CameraCapabilityStore.PipelineDecisionReason.FINAL_TIE_A)),
                Optional.empty());
        return Snapshot.current("hardware", List.of(), List.of(camera));
    }

    private static PipelineEvidence evidence(VerificationPipelineId id, CaptureModeTuple tuple) {
        var key = com.dvid.dcam.feature.device.domain.camera.CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, id, tuple);
        return new PipelineEvidence(CAMERA, VideoCodec.H264, id, PipelineAvailability.AVAILABLE,
                List.of(key), List.of());
    }

    private static PipelineEvidence verifiedEvidence(
            VerificationPipelineId id, CaptureModeTuple tuple) {
        var key = com.dvid.dcam.feature.device.domain.camera.CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, id, tuple);
        return new PipelineEvidence(CAMERA, VideoCodec.H264, id,
                PipelineAvailability.AVAILABLE, List.of(key), List.of(
                        new com.dvid.dcam.feature.device.domain.camera.CandidateEvidence(
                                key, VerificationOutcome.VERIFIED_PASS)));
    }

    private static CaptureModeTuple tuple() {
        return new CaptureModeTuple(
                new VideoMode(new StandardResolution(StandardResolutionLabel.HD,
                        new com.dvid.dcam.feature.device.domain.camera.CameraResolution(1280, 720)), 30),
                new ImageMode(new StandardResolution(StandardResolutionLabel.HD,
                        new com.dvid.dcam.feature.device.domain.camera.CameraResolution(1280, 720))));
    }

    private static final class FakeRunner extends CompareCameraPipelinesUseCase.PipelineRunner {
        private final VerificationPipelineId id;
        private final CaptureModeTuple tuple;
        private final long totalMillis;
        private final PipelineRunStatus status;
        private final boolean releaseSucceeds;
        private final Runnable measurementHook;
        private final List<PerformanceSample> measurements = new java.util.ArrayList<>();
        private final AtomicInteger runCount = new AtomicInteger();
        private final AtomicInteger releaseCount = new AtomicInteger();

        private FakeRunner(VerificationPipelineId id, CaptureModeTuple tuple,
                long totalMillis, PipelineRunStatus status) {
            this(id, tuple, totalMillis, status, true);
        }

        private FakeRunner(VerificationPipelineId id, CaptureModeTuple tuple,
                long totalMillis, PipelineRunStatus status, boolean releaseSucceeds) {
            this(id, tuple, totalMillis, status, releaseSucceeds, () -> {});
        }

        private FakeRunner(VerificationPipelineId id, CaptureModeTuple tuple,
                long totalMillis, PipelineRunStatus status, boolean releaseSucceeds,
                Runnable measurementHook) {
            this.id = id;
            this.tuple = tuple;
            this.totalMillis = totalMillis;
            this.status = status;
            this.releaseSucceeds = releaseSucceeds;
            this.measurementHook = measurementHook;
        }

        @Override public VerificationPipelineId pipelineId() { return id; }

        @Override public CompareCameraPipelinesUseCase.PipelineRun runCoverage(
                CameraPipelineBenchmarkPlan plan,
                BooleanSupplier cancellation,
                Consumer<CompareCameraPipelinesUseCase.Progress> progress) {
            runCount.incrementAndGet();
            var key = com.dvid.dcam.feature.device.domain.camera.CandidateKey.forTuple(
                    CAMERA, VideoCodec.H264, id, tuple);
            var evidence = new PipelineEvidence(CAMERA, VideoCodec.H264, id,
                    PipelineAvailability.AVAILABLE, List.of(key), List.of(
                            new com.dvid.dcam.feature.device.domain.camera.CandidateEvidence(key,
                                    VerificationOutcome.VERIFIED_PASS)));
            var coverage = new PipelineCoverage(CAMERA, id, status, evidence,
                    List.of(new TupleOutcome(tuple, VerificationOutcome.VERIFIED_PASS,
                            TupleStage.COMBO, "verified")), 4, totalMillis, true, "coverage");
            return new CompareCameraPipelinesUseCase.PipelineRun(id, status,
                    List.of(coverage), 4, totalMillis, true, "coverage");
        }

        @Override public CompareCameraPipelinesUseCase.Measurement measure(
                CameraPipelineBenchmarkPlan plan, CameraId cameraId, CaptureModeTuple tuple,
                CompareCameraPipelinesUseCase.Invocation invocation,
                BooleanSupplier cancellation) {
            PerformanceSample sample = new PerformanceSample(id, cameraId, tuple,
                    invocation.warmup(), invocation.block(), invocation.position(), 4,
                    OptionalLong.of(2), OptionalLong.of(3), OptionalLong.of(4),
                    OptionalLong.of(5), OptionalLong.of(totalMillis), OptionalDouble.of(30),
                    OptionalLong.of(1), ResourceSample.unavailable());
            measurements.add(sample);
            measurementHook.run();
            return new CompareCameraPipelinesUseCase.Measurement(true,
                    Optional.of(sample), "measured");
        }

        @Override public boolean release() {
            releaseCount.incrementAndGet();
            return releaseSucceeds;
        }
    }

    private static final class RecordingStore
            extends CompareCameraPipelinesUseCase.DurableComparisonPublisher {
        private final AtomicInteger writeCount = new AtomicInteger();
        private final boolean failWrites;
        private Snapshot published;

        private RecordingStore() { this(false); }

        private RecordingStore(boolean failWrites) { this.failWrites = failWrites; }

        @Override public boolean publishDurably(Snapshot snapshot,
                CameraPipelineBenchmarkReport report) {
            if (failWrites) throw new IllegalStateException("storage blocked");
            published = snapshot;
            writeCount.incrementAndGet();
            return true;
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