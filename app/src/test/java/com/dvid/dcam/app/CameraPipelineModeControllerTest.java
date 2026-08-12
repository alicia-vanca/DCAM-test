package com.dvid.dcam.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.R;
import com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class CameraPipelineModeControllerTest {
    private static final CandidateKey A = candidate("0", "a-camera2-native-surface-sharing-v1");
    private static final CandidateKey B = candidate("0", "b-camera2-egl-fanout-v1");
    private static final CandidateKey B_INACTIVE = candidate("1", "b-camera2-egl-fanout-v1");

    @Test void uiMapsEveryPipelineResultConsistently() {
        assertEquals(R.string.camera_pipeline_applied, MainActivity.cameraPipelineNotice(
                CameraPipelineModeController.Result.APPLIED));
        assertEquals(R.string.camera_pipeline_busy, MainActivity.cameraPipelineNotice(
                CameraPipelineModeController.Result.REJECTED_BUSY));
        assertEquals(R.string.camera_pipeline_unknown, MainActivity.cameraPipelineNotice(
                CameraPipelineModeController.Result.REJECTED_UNAVAILABLE));
        assertEquals(R.string.camera_pipeline_failed, MainActivity.cameraPipelineNotice(
                CameraPipelineModeController.Result.FAILED));
    }

    @Test void forcedSwitchRequiresIdleRuntime() {
        FakeBackend backend = new FakeBackend(A, false, B);
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertFalse(controller.select(DeveloperSettingsStore.Mode.B, result::set));
        assertEquals(CameraPipelineModeController.Result.REJECTED_BUSY, result.get());
        assertEquals(0, backend.verifyCount);
        assertEquals(DeveloperSettingsStore.Mode.AUTO, backend.mode("0"));
        assertFalse(controller.canSelect());
    }

    @Test void selectionUnavailableWhileAnyCameraChangeRuns() {
        FakeBackend backend = new FakeBackend(A, true, B);
        backend.deferPreparation = true;
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> first = new AtomicReference<>();
        AtomicReference<CameraPipelineModeController.Result> second = new AtomicReference<>();

        assertTrue(controller.select("1", DeveloperSettingsStore.Mode.B, first::set));
        assertFalse(controller.canSelect("0"));
        assertFalse(controller.select("0", DeveloperSettingsStore.Mode.B, second::set));
        assertEquals(CameraPipelineModeController.Result.REJECTED_BUSY, second.get());

        backend.completePreparation();

        assertEquals(CameraPipelineModeController.Result.APPLIED, first.get());
        assertTrue(controller.canSelect("0"));
    }

    @Test void failedSwitchDoesNotPersistAndRestoresPreviousWorkingPipeline() {
        FakeBackend backend = new FakeBackend(A, true, B);
        backend.verifyResult = new CameraPipelineModeController.TransitionResult(
                false, Optional.of(B), "failed");
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertTrue(controller.select(DeveloperSettingsStore.Mode.B, result::set));

        assertEquals(CameraPipelineModeController.Result.FAILED, result.get());
        assertEquals(DeveloperSettingsStore.Mode.AUTO, backend.mode("0"));
        assertEquals(A, backend.active);
        assertEquals(1, backend.verifyCount);
        assertEquals(1, backend.restoreCount);
    }

    @Test void successfulSwitchPersistsOnlyAfterTargetPipelineIsActive() {
        FakeBackend backend = new FakeBackend(A, true, B);
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertTrue(controller.select(DeveloperSettingsStore.Mode.B, result::set));

        assertEquals(CameraPipelineModeController.Result.APPLIED, result.get());
        assertEquals(DeveloperSettingsStore.Mode.B, backend.mode("0"));
        assertEquals(B, backend.active);
    }

    @Test void missingPipelineEvidenceBuildsBeforeResolvingCurrentProfile() {
        FakeBackend backend = new FakeBackend(A, true, B);
        backend.releaseDuringPrepare = true;
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertTrue(controller.select(DeveloperSettingsStore.Mode.B, result::set));

        assertEquals(CameraPipelineModeController.Result.APPLIED, result.get());
        assertEquals(1, backend.prepareCount);
        assertTrue(backend.preparedActiveCamera);
        assertEquals(A, backend.resolvedProfile);
        assertEquals(B, backend.active);
    }

    @Test void unavailablePipelineRestoresPreviousProfileAfterPreparationRelease() {
        FakeBackend backend = new FakeBackend(A, true, B);
        backend.releaseDuringPrepare = true;
        backend.evidenceAvailable = false;
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertTrue(controller.select(DeveloperSettingsStore.Mode.B, result::set));

        assertEquals(CameraPipelineModeController.Result.REJECTED_UNAVAILABLE, result.get());
        assertEquals(A, backend.active);
        assertEquals(1, backend.restoreCount);
        assertEquals(DeveloperSettingsStore.Mode.AUTO, backend.mode("0"));
        assertFalse(controller.transitionInFlight());
    }

    @Test void persistenceFailureRestoresPreviousPipelineBeforeReportingFailure() {
        FakeBackend backend = new FakeBackend(A, true, B);
        backend.commitFailures = 1;
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertTrue(controller.select(DeveloperSettingsStore.Mode.B, result::set));

        assertEquals(CameraPipelineModeController.Result.FAILED, result.get());
        assertEquals(DeveloperSettingsStore.Mode.AUTO, backend.mode("0"));
        assertEquals(A, backend.active);
        assertEquals(1, backend.restoreCount);
        assertFalse(controller.transitionInFlight());
    }

    @Test void sessionModeFallbackKeepsActiveTargetConsistentAfterWriteFailure() {
        FakeBackend backend = new FakeBackend(A, true, B);
        backend.commitFailures = 1;
        backend.applyModeBeforeFailure = true;
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertTrue(controller.select(DeveloperSettingsStore.Mode.B, result::set));

        assertEquals(CameraPipelineModeController.Result.FAILED, result.get());
        assertEquals(DeveloperSettingsStore.Mode.B, backend.mode("0"));
        assertEquals(B, backend.active);
        assertEquals(0, backend.restoreCount);
        assertFalse(controller.transitionInFlight());
    }

    @Test void failedRestoreRecommitsModeForStillActiveTargetPipeline() {
        FakeBackend backend = new FakeBackend(A, true, B);
        backend.commitFailures = 1;
        backend.failRestore = true;
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertTrue(controller.select(DeveloperSettingsStore.Mode.B, result::set));

        assertEquals(CameraPipelineModeController.Result.FAILED, result.get());
        assertEquals(DeveloperSettingsStore.Mode.B, backend.mode("0"));
        assertEquals(B, backend.active);
        assertEquals(1, backend.restoreCount);
        assertFalse(controller.transitionInFlight());
    }

    @Test void inactiveCameraSelectionCommitsWithoutRuntimeVerificationAndRestoresActiveCamera() {
        FakeBackend backend = new FakeBackend(A, true, B_INACTIVE);
        backend.releaseDuringPrepare = true;
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertTrue(controller.select("1", DeveloperSettingsStore.Mode.B, result::set));

        assertEquals(CameraPipelineModeController.Result.APPLIED, result.get());
        assertEquals(DeveloperSettingsStore.Mode.B, backend.mode("1"));
        assertEquals(A, backend.active);
        assertFalse(backend.preparedActiveCamera);
        assertEquals(0, backend.verifyCount);
        assertEquals(1, backend.restoreCount);
    }

    @Test void inactivePersistenceFailureRestoresRuntimeAndKeepsPreviousMode() {
        FakeBackend backend = new FakeBackend(A, true, B_INACTIVE);
        backend.releaseDuringPrepare = true;
        backend.commitFailures = 1;
        CameraPipelineModeController controller = new CameraPipelineModeController(backend);
        AtomicReference<CameraPipelineModeController.Result> result = new AtomicReference<>();

        assertTrue(controller.select("1", DeveloperSettingsStore.Mode.B, result::set));

        assertEquals(CameraPipelineModeController.Result.FAILED, result.get());
        assertEquals(DeveloperSettingsStore.Mode.AUTO, backend.mode("1"));
        assertEquals(A, backend.active);
        assertEquals(1, backend.restoreCount);
    }

    private static CandidateKey candidate(String cameraId, String pipeline) {
        StandardResolution resolution = new StandardResolution(
                StandardResolutionLabel.FHD,
                new com.dvid.dcam.feature.device.domain.camera.CameraResolution(1920, 1080));
        return CandidateKey.forTuple(new CameraId(cameraId), VideoCodec.H264,
                new VerificationPipelineId(pipeline), new CaptureModeTuple(
                        new VideoMode(resolution, 30), new ImageMode(resolution)));
    }

    private static final class FakeBackend implements CameraPipelineModeController.Backend {
        private CandidateKey active;
        private final boolean switchAllowed;
        private final CandidateKey target;
        private final Map<String, DeveloperSettingsStore.Mode> committed = new HashMap<>();
        private CameraPipelineModeController.TransitionResult verifyResult;
        private int prepareCount;
        private int verifyCount;
        private int restoreCount;
        private int commitFailures;
        private CandidateKey resolvedProfile;
        private boolean releaseDuringPrepare;
        private boolean evidenceAvailable = true;
        private boolean failRestore;
        private boolean applyModeBeforeFailure;
        private boolean deferPreparation;
        private boolean preparedActiveCamera;
        private java.util.function.Consumer<Boolean> pendingPreparation;

        private FakeBackend(CandidateKey active, boolean switchAllowed, CandidateKey target) {
            this.active = active;
            this.switchAllowed = switchAllowed;
            this.target = target;
            verifyResult = new CameraPipelineModeController.TransitionResult(
                    true, Optional.of(target), "ready");
        }

        @Override public boolean canSwitch(String cameraId) { return switchAllowed; }
        @Override public Optional<CandidateKey> activeCandidate() {
            return Optional.ofNullable(active);
        }
        @Override public void prepare(String cameraId, DeveloperSettingsStore.Mode mode,
                boolean activeCamera, java.util.function.Consumer<Boolean> completion) {
            prepareCount++;
            preparedActiveCamera = activeCamera;
            if (releaseDuringPrepare) active = null;
            if (deferPreparation) {
                pendingPreparation = completion;
                return;
            }
            completion.accept(evidenceAvailable);
        }
        private void completePreparation() {
            java.util.function.Consumer<Boolean> completion = pendingPreparation;
            pendingPreparation = null;
            completion.accept(evidenceAvailable);
        }
        @Override public Optional<CandidateKey> resolve(String cameraId,
                DeveloperSettingsStore.Mode mode, CandidateKey currentProfile) {
            resolvedProfile = currentProfile;
            return Optional.of(target);
        }
        @Override public void verify(CandidateKey target,
                java.util.function.Consumer<CameraPipelineModeController.TransitionResult> completion) {
            verifyCount++;
            active = verifyResult.activeCandidate().orElse(active);
            completion.accept(verifyResult);
        }
        @Override public void restore(CandidateKey target,
                java.util.function.Consumer<CameraPipelineModeController.TransitionResult> completion) {
            restoreCount++;
            if (failRestore) {
                completion.accept(new CameraPipelineModeController.TransitionResult(
                        false, Optional.ofNullable(active), "restore_failed"));
                return;
            }
            active = target;
            completion.accept(new CameraPipelineModeController.TransitionResult(
                    true, Optional.of(target), "restored"));
        }
        @Override public void commit(String cameraId, DeveloperSettingsStore.Mode mode) {
            if (applyModeBeforeFailure) committed.put(cameraId, mode);
            if (commitFailures > 0) {
                commitFailures--;
                throw new IllegalStateException("write failed");
            }
            committed.put(cameraId, mode);
        }
        @Override public DeveloperSettingsStore.Mode mode(String cameraId) {
            return committed.getOrDefault(cameraId, DeveloperSettingsStore.Mode.AUTO);
        }
    }
}