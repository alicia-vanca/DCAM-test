package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.domain.camera.CameraFailureClass;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
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
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class VerifyStandaloneImageUseCaseTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("pipeline-a");
    private static final VideoMode VIDEO = new VideoMode(new StandardResolution(
            StandardResolutionLabel.HD, new CameraResolution(1280, 720)), 30);
    private static final ImageMode IMAGE = new ImageMode(new StandardResolution(
            StandardResolutionLabel.FHD, new CameraResolution(1920, 1080)));
    private static final CaptureModeTuple TUPLE = new CaptureModeTuple(VIDEO, IMAGE);
    private static final CandidateKey IMAGE_KEY = CandidateKey.forImage(
            CAMERA, VideoCodec.H264, PIPELINE, IMAGE);

    @Test void passReturnsSnapshotWithVerifiedImageEvidence() {
        FakeRuntime runtime = new FakeRuntime();

        VerifyStandaloneImageUseCase.Result result = execute(runtime);

        assertEquals(VerificationOutcome.VERIFIED_PASS, result.outcome());
        assertEquals(VerificationOutcome.VERIFIED_PASS,
                pipeline(result.snapshot()).outcome(IMAGE_KEY));
        assertEquals(1, runtime.bindCalls);
        assertEquals(1, runtime.releaseCalls);
        assertTrue(result.cleanupComplete());
    }

    @Test void transposedDimensionsPassForQuarterTurnOrientations() {
        for (int sensorOrientationDegrees : List.of(90, 270)) {
            FakeRuntime runtime = new FakeRuntime();
            runtime.transposeImageCapture = true;

            VerifyStandaloneImageUseCase.Result result =
                    execute(runtime, sensorOrientationDegrees);

            assertEquals(VerificationOutcome.VERIFIED_PASS, result.outcome());
            assertEquals(1, runtime.captureCalls);
        }
    }

    @Test void transposedDimensionsFailForStraightOrientations() {
        for (int sensorOrientationDegrees : List.of(0, 180)) {
            FakeRuntime runtime = new FakeRuntime();
            runtime.transposeImageCapture = true;

            VerifyStandaloneImageUseCase.Result result =
                    execute(runtime, sensorOrientationDegrees);

            assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED, result.outcome());
            assertEquals(2, runtime.captureCalls);
        }
    }

    @Test void repeatedCandidateFailureBecomesUnsupported() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.bindOutcomes.add(CameraOperationOutcome.CANDIDATE_SUSPECT);
        runtime.bindOutcomes.add(CameraOperationOutcome.CANDIDATE_SUSPECT);

        VerifyStandaloneImageUseCase.Result result = execute(runtime);

        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED, result.outcome());
        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED,
                pipeline(result.snapshot()).outcome(IMAGE_KEY));
        assertEquals(2, runtime.bindCalls);
        assertEquals(2, runtime.releaseCalls);
    }

    @Test void transientFailureStaysNonterminal() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.bindOutcomes.add(CameraOperationOutcome.TIMEOUT_UNKNOWN);

        VerifyStandaloneImageUseCase.Result result = execute(runtime);

        assertEquals(VerificationOutcome.TIMEOUT_UNKNOWN, result.outcome());
        assertEquals(VerificationOutcome.UNKNOWN,
                pipeline(result.snapshot()).outcome(IMAGE_KEY));
        assertEquals(1, runtime.bindCalls);
        assertEquals(1, runtime.releaseCalls);
    }

    @Test void invalidDimensionsNeedMatchingConfirmation() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.invalidCaptures = 2;

        VerifyStandaloneImageUseCase.Result result = execute(runtime);

        assertEquals(VerificationOutcome.DEFINITIVE_UNSUPPORTED, result.outcome());
        assertEquals(2, runtime.captureCalls);
        assertEquals(2, runtime.releaseCalls);
        assertFalse(pipeline(result.snapshot()).effectiveCandidates().contains(IMAGE_KEY));
    }

    private static VerifyStandaloneImageUseCase.Result execute(FakeRuntime runtime) {
        CameraCapabilityStore.Snapshot snapshot = snapshot();
        return execute(runtime, snapshot);
    }

    private static VerifyStandaloneImageUseCase.Result execute(
            FakeRuntime runtime, int sensorOrientationDegrees) {
        return execute(runtime, snapshot(sensorOrientationDegrees));
    }

    private static VerifyStandaloneImageUseCase.Result execute(FakeRuntime runtime,
            CameraCapabilityStore.Snapshot snapshot) {
        VerifyStandaloneImageUseCase useCase = new VerifyStandaloneImageUseCase(
                runtime, new NoOpLogger(), runtime.clock, camera -> 0L);
        return useCase.execute(new VerifyStandaloneImageUseCase.Request(
                snapshot, IMAGE_KEY, TUPLE, 1, 0));
    }

    private static CameraCapabilityStore.Snapshot snapshot() {
        CandidateKey video = CandidateKey.forVideo(
                CAMERA, VideoCodec.H264, PIPELINE, VIDEO);
        CandidateKey tuple = CandidateKey.forTuple(
                CAMERA, VideoCodec.H264, PIPELINE, TUPLE);
        PipelineEvidence evidence = new PipelineEvidence(CAMERA, VideoCodec.H264,
                PIPELINE, PipelineAvailability.AVAILABLE,
                List.of(video, IMAGE_KEY, tuple), List.of());
        CameraCapabilityStore.CodecSnapshot codec = new CameraCapabilityStore.CodecSnapshot(
                VideoCodec.H264, CameraCapabilityStore.CodecState.ACTIVE,
                Optional.empty(), List.of(evidence), Optional.empty());
        CameraCapabilityStore.CameraSnapshot camera =
                new CameraCapabilityStore.CameraSnapshot(CAMERA, "camera-hardware",
                        List.of(codec), Optional.empty(), Optional.empty());
        return CameraCapabilityStore.Snapshot.current(
                "hardware", List.of(CAMERA), List.of(camera));
    }

    private static CameraCapabilityStore.Snapshot snapshot(
            int sensorOrientationDegrees) {
        CameraCapabilityStore.Snapshot snapshot = snapshot();
        CameraCapabilityStore.CameraSnapshot camera = snapshot.cameras().get(0);
        CameraCapabilityStore.CameraSnapshot oriented =
                new CameraCapabilityStore.CameraSnapshot(camera.cameraId(),
                        camera.hardwareSignature(), camera.codecs(),
                        camera.selectedPipeline(), camera.selectedRecordingProfile(),
                        sensorOrientationDegrees);
        return CameraCapabilityStore.Snapshot.current(
                snapshot.hardwareSignature(), snapshot.cameraOrderOverride(),
                List.of(oriented));
    }

    private static PipelineEvidence pipeline(CameraCapabilityStore.Snapshot snapshot) {
        return snapshot.cameras().get(0).codecs().get(0).pipelines().get(0);
    }

    private static final class FakeClock implements CameraVerificationClock {
        @Override public long elapsedRealtimeMillis() { return 100; }
    }

    private static final class FakeRuntime implements CameraRuntimeOperations {
        private final FakeClock clock = new FakeClock();
        private final Deque<CameraOperationOutcome> bindOutcomes = new ArrayDeque<>();
        private boolean bound;
        private boolean captured;
        private boolean wrongCapture;
        private boolean transposeImageCapture;
        private int invalidCaptures;
        private int bindCalls;
        private int captureCalls;
        private int releaseCalls;

        @Override public CameraOperationResult bindSession(CameraOperationContext context) {
            throw new AssertionError("standalone image must not use combo bind");
        }

        @Override public CameraOperationResult bindStandaloneImageSession(
                CameraOperationContext context) {
            bindCalls++;
            CameraOperationOutcome outcome = bindOutcomes.isEmpty()
                    ? CameraOperationOutcome.PASS : bindOutcomes.removeFirst();
            bound = outcome == CameraOperationOutcome.PASS;
            captured = false;
            wrongCapture = false;
            return result(context, CameraPipelineOperation.BIND_SESSION, outcome);
        }

        @Override public CameraOperationResult previewProgress(CameraOperationContext context) {
            return result(context, CameraPipelineOperation.PREVIEW_PROGRESS,
                    CameraOperationOutcome.PASS);
        }

        @Override public CameraOperationResult startEncoder(CameraOperationContext context) {
            return result(context, CameraPipelineOperation.START_ENCODER,
                    CameraOperationOutcome.PASS);
        }

        @Override public CameraOperationResult stopEncoder(CameraOperationContext context) {
            return result(context, CameraPipelineOperation.STOP_ENCODER,
                    CameraOperationOutcome.PASS);
        }

        @Override public CameraOperationResult finalizeEncoder(CameraOperationContext context) {
            return result(context, CameraPipelineOperation.FINALIZE_ENCODER,
                    CameraOperationOutcome.PASS);
        }

        @Override public CameraOperationResult captureJpeg(CameraOperationContext context) {
            captureCalls++;
            captured = true;
            wrongCapture = invalidCaptures > 0;
            if (invalidCaptures > 0) invalidCaptures--;
            return result(context, CameraPipelineOperation.CAPTURE_JPEG,
                    CameraOperationOutcome.PASS);
        }

        @Override public CameraOperationResult release(CameraOperationContext context) {
            releaseCalls++;
            bound = false;
            return result(context, CameraPipelineOperation.RELEASE,
                    CameraOperationOutcome.PASS);
        }

        @Override public CameraPipelineDiagnostics diagnostics(
                CameraOperationContext context) {
            CameraResolution resolution = IMAGE.resolution().actual();
            if (wrongCapture) {
                resolution = new CameraResolution(
                        resolution.width() + 1, resolution.height());
            }
            if (transposeImageCapture) {
                resolution = new CameraResolution(
                        resolution.height(), resolution.width());
            }
            return new CameraPipelineDiagnostics(context, bound, bound, false, false,
                    1, 1, 0, 2, 1, Optional.empty(),
                    captured ? Optional.of(resolution) : Optional.empty(), false,
                    Optional.empty(), captured ? Optional.of("image.jpg") : Optional.empty(),
                    "test diagnostics");
        }

        private static CameraOperationResult result(CameraOperationContext context,
                CameraPipelineOperation operation, CameraOperationOutcome outcome) {
            CameraFailureClass failureClass = outcome.isCandidateFailure()
                    ? CameraFailureClass.SESSION_CONFIGURATION : CameraFailureClass.NONE;
            return new CameraOperationResult(context, operation, outcome,
                    failureClass, 0, outcome.name());
        }
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }
}
