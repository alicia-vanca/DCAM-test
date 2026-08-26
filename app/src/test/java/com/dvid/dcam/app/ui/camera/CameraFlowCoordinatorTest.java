package com.dvid.dcam.app.ui.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.app.ui.settings.camera.CameraResolutionOption;
import com.dvid.dcam.app.ui.settings.camera.CameraSelection;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsCamera;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsStatus;
import com.dvid.dcam.app.ui.settings.camera.CameraVideoOption;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class CameraFlowCoordinatorTest {
    @Test void recheckUpdateRejectsNullStatus() {
        assertThrows(NullPointerException.class,
                () -> new CameraFlowCoordinator.RecheckUpdate(null, "recheck_started"));
    }

    @Test void startupVerifiesSecondaryCamerasThenRetainsMain() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main"), candidate("aux-1"),
                candidate("aux-2")));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(List.of("aux-1", "aux-2", "main"), backend.transitions.stream()
                .map(value -> value.cameraId().value()).toList());
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertEquals("main", backend.active.orElseThrow().cameraId().value());
        assertEquals(List.of(false, true), backend.startupPreviewReady);
        assertEquals(List.of(true), backend.capabilityLoadDeepVerify);
    }

    @Test void startupLoadOnlyStillVerifiesNonReusableFastSnapshot() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        backend.deepVerifyOnStartup = false;
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertEquals(List.of("main"), backend.transitions.stream()
                .map(value -> value.cameraId().value()).toList());
        assertEquals(List.of(false, true), backend.startupPreviewReady);
        assertTrue(backend.startupUnavailableReasons.isEmpty());
        assertEquals(1, backend.markReusableCount);
        assertEquals(List.of(false), backend.capabilityLoadDeepVerify);
    }

    @Test void switchCyclesOrderedCamerasAndWrapsToMain() {
        CandidateKey main = candidate("main");
        CandidateKey rear = candidate("rear");
        FakeBackend backend = new FakeBackend(List.of(main, rear));
        backend.switchCandidates = List.of(main, rear);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();


        backend.transitions.clear();
        backend.transitionTypes.clear();

        assertTrue(coordinator.switchToNextCamera());
        assertEquals("rear", backend.active.orElseThrow().cameraId().value());
        assertEquals(List.of(CameraFlowCoordinator.Transition.SWITCH_CAMERA),
                backend.transitionTypes);

        assertTrue(coordinator.switchToNextCamera());
        assertEquals("main", backend.active.orElseThrow().cameraId().value());
        assertEquals(List.of(CameraFlowCoordinator.Transition.SWITCH_CAMERA,
                CameraFlowCoordinator.Transition.SWITCH_CAMERA), backend.transitionTypes);
    }


    @Test void stateObserverTracksDeferredCameraSwitch() {
        CandidateKey main = candidate("main");
        CandidateKey rear = candidate("rear");
        FakeBackend backend = new FakeBackend(List.of(main, rear));
        backend.switchCandidates = List.of(main, rear);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.deferSubmit = true;
        List<Boolean> transitions = new ArrayList<>();
        CameraFlowCoordinator.StateSubscription subscription = coordinator.observeStateChanges(
                () -> transitions.add(coordinator.transitionInFlight()));

        assertTrue(coordinator.switchToNextCamera());
        assertEquals(List.of(true), transitions);

        backend.completeDeferred();
        assertEquals(List.of(true, false), transitions);

        subscription.close();
        assertTrue(coordinator.switchToNextCamera());
        assertEquals(List.of(true, false), transitions);
    }

    @Test void unverifiedSwitchVerifiesBeforeCommittedRepeatsRestoreExact() {
        CandidateKey main = candidate("main");
        CandidateKey rear = candidate("rear");
        FakeBackend backend = new FakeBackend(List.of(main, rear));
        backend.switchCandidates = List.of(main, rear);
        backend.committedCandidates = List.of(main);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();
        backend.transitions.clear();
        backend.transitionTypes.clear();

        assertTrue(coordinator.switchToNextCamera());
        backend.committedCandidates = List.of(main, rear);
        assertTrue(coordinator.switchToNextCamera());
        assertTrue(coordinator.switchToNextCamera());

        assertEquals(List.of(CameraFlowCoordinator.Transition.SWITCH_CAMERA,
                        CameraFlowCoordinator.Transition.RESTORE_EXACT,
                        CameraFlowCoordinator.Transition.RESTORE_EXACT),
                backend.transitionTypes);
        assertEquals("rear", backend.active.orElseThrow().cameraId().value());
    }

    @Test void committedCameraSwitchUsesDirectRestore() {
        CandidateKey main = candidate("main");
        CandidateKey rear = candidate("rear");
        FakeBackend backend = new FakeBackend(List.of(main, rear));
        backend.switchCandidates = List.of(main, rear);
        backend.committedCandidates = List.of(main, rear);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();
        backend.transitions.clear();
        backend.transitionTypes.clear();

        assertTrue(coordinator.switchToNextCamera());

        assertEquals(List.of(CameraFlowCoordinator.Transition.RESTORE_EXACT),
                backend.transitionTypes);
        assertEquals("rear", backend.active.orElseThrow().cameraId().value());
    }

    @Test void switchAvailabilityTracksReadyAndInFlightState() {
        CandidateKey main = candidate("main");
        CandidateKey rear = candidate("rear");
        FakeBackend backend = new FakeBackend(List.of(main, rear));
        backend.switchCandidates = List.of(main, rear);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        assertFalse(coordinator.canSwitchCamera());

        coordinator.start();
        assertTrue(coordinator.canSwitchCamera());
        assertEquals(0, backend.switchCandidatesCalls);

        backend.deferSubmit = true;
        assertTrue(coordinator.switchToNextCamera());
        assertEquals(1, backend.switchCandidatesCalls);
        assertFalse(coordinator.canSwitchCamera());

        backend.completeDeferred();
        assertTrue(coordinator.canSwitchCamera());
        assertEquals(1, backend.switchCandidatesCalls);

        backend.recording = true;
        assertFalse(coordinator.canSwitchCamera());
        assertFalse(coordinator.switchToNextCamera());
        assertEquals(1, backend.switchCandidatesCalls);
    }

    @Test void switchAvailabilityReadsRuntimeOutsideCoordinatorLock() {
        CandidateKey main = candidate("main");
        CandidateKey rear = candidate("rear");
        FakeBackend backend = new FakeBackend(List.of(main, rear));
        backend.switchCandidates = List.of(main, rear);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.forbiddenLock = coordinator;

        assertTrue(coordinator.canSwitchCamera());
        assertFalse(backend.recordingCalledWhileHoldingForbiddenLock);
    }

    @Test void startupSkipsBWhenAWorksForEachCamera() {
        FakeBackend backend = new FakeBackend(List.of(
                candidate("main", "pipeline-a"), candidate("main", "pipeline-b"),
                candidate("aux", "pipeline-a"), candidate("aux", "pipeline-b")));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(List.of("aux:pipeline-a", "main:pipeline-a"), backend.transitions.stream()
                .map(value -> value.cameraId().value() + ":"
                        + value.verificationPipelineId().value()).toList());
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void startupStopsAfterBestResolutionSiblingPasses() {
        CandidateKey best = candidate("main", "pipeline-a", 30);
        CandidateKey sibling = candidate("main", "pipeline-a", 24);
        FakeBackend backend = new FakeBackend(List.of(best, sibling));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(List.of(best), backend.transitions);
        assertEquals(Optional.of(best), backend.active);
    }

    @Test void reusableStartupDirectBindsMainWithoutScanningAuxiliary() {
        CandidateKey main = candidate("main");
        CandidateKey auxiliary = candidate("aux");
        FakeBackend backend = new FakeBackend(List.of(main, auxiliary));
        backend.reusableStartup = true;
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(List.of(main), backend.transitions);
        assertEquals(List.of(CameraFlowCoordinator.Transition.BIND_COMMITTED),
                backend.transitionTypes);
        assertEquals(0, backend.markReusableCount);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void loadOnlyReusableBindFailureDoesNotStartCapabilityScan() {
        CandidateKey main = candidate("main");
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.reusableStartup = true;
        backend.deepVerifyOnStartup = false;
        backend.failCameraId = "main";
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(CameraFlowCoordinator.State.UNAVAILABLE, coordinator.state());
        assertEquals(0, backend.releaseCount);
        assertEquals(0, backend.invalidateCount);
        assertEquals(List.of(false), backend.capabilityLoadDeepVerify);
    }
    @Test void startupTriesBAfterAExhaustion() {
        FakeBackend backend = new FakeBackend(List.of(
                candidate("main", "pipeline-a"), candidate("main", "pipeline-b")));
        backend.failPipelineId = "pipeline-a";
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(List.of("pipeline-a", "pipeline-b"), backend.transitions.stream()
                .map(value -> value.verificationPipelineId().value()).toList());
        assertEquals("pipeline-b", backend.active.orElseThrow()
                .verificationPipelineId().value());
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void startupReinitializesAfterFailureClosesOwner() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main"), candidate("aux-1"),
                candidate("aux-2")));
        backend.failCameraId = "aux-1";
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(List.of(
                CameraFlowCoordinator.Transition.INITIALIZE,
                CameraFlowCoordinator.Transition.INITIALIZE,
                CameraFlowCoordinator.Transition.SWITCH_CAMERA), backend.transitionTypes);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertEquals("main", backend.active.orElseThrow().cameraId().value());
    }

    @Test void inactiveSettingPersistsWithoutCameraTransition() {
        CandidateKey main = candidate("main");
        CandidateKey auxiliary = candidate("aux");
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.active = Optional.of(main);
        backend.setting = Optional.of(auxiliary);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.transitions.clear();
        backend.transitionTypes.clear();

        assertTrue(coordinator.select("camera:3:aux:image-resolution", 0));

        assertTrue(backend.transitions.isEmpty());
        assertTrue(backend.transitionTypes.isEmpty());
        assertEquals("main", backend.active.orElseThrow().cameraId().value());
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void activeImageResolutionPersistsWithoutPreVerification() {
        CandidateKey active = candidate("main");
        CandidateKey requested = candidate(
                "main", "a-camera2-native-surface-sharing-v1", 24);
        FakeBackend backend = new FakeBackend(List.of(active));
        backend.active = Optional.of(active);
        backend.setting = Optional.of(requested);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.transitions.clear();
        backend.transitionTypes.clear();

        assertTrue(coordinator.select("camera:4:main:image-resolution", 0));

        assertEquals(1, backend.resolveCount);
        assertTrue(backend.transitions.isEmpty());
        assertTrue(backend.transitionTypes.isEmpty());
        assertEquals(Optional.of(active), backend.active);
        assertFalse(coordinator.transitionInFlight());
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void inactiveSettingSkipsDuplicateRestoreAfterVerifierRollback() {
        CandidateKey main = candidate("main");
        CandidateKey auxiliary = candidate("aux");
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.active = Optional.of(main);
        backend.setting = Optional.of(auxiliary);
        backend.retainActiveOnSetting = true;
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.transitions.clear();

        assertTrue(coordinator.select("camera:3:aux:image-resolution", 0));

        assertTrue(backend.transitions.isEmpty());
        assertEquals("main", backend.active.orElseThrow().cameraId().value());
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void failedInactiveRestoreLeavesControlledUnavailableState() {
        CandidateKey main = candidate("main");
        CandidateKey auxiliary = candidate("aux");
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.active = Optional.of(main);
        backend.setting = Optional.of(auxiliary);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.transitions.clear();
        backend.failCameraId = "main";

        assertTrue(coordinator.select("camera:3:aux:image-resolution", 0));

        assertTrue(backend.transitions.isEmpty());
        assertEquals(Optional.of(main), backend.active);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void failedMainVerificationRollbackStaysUnavailable() {
        CandidateKey main = candidate("main");
        CandidateKey auxiliary = candidate("aux");
        FakeBackend backend = new FakeBackend(List.of(main, auxiliary));
        backend.failCameraId = "main";
        backend.rollbackCandidate = Optional.of(auxiliary);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(Optional.empty(), backend.active);
        assertEquals(1, backend.releaseCount);
        assertEquals(CameraFlowCoordinator.State.UNAVAILABLE, coordinator.state());
    }

    @Test void startupAcceptsSamePipelineLowerTupleFallback() {
        String pipelineId = "a-camera2-native-surface-sharing-v1";
        CandidateKey main = candidate("main", pipelineId, 30);
        CandidateKey lowerFallback = candidate("main", pipelineId, 24);
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.readyCandidateOverride = lowerFallback;
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(Optional.of(lowerFallback), backend.active);
        assertEquals(0, backend.releaseCount);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void rapidRuntimeSettingChangesSubmitImmediatelyAndProjectLatestTarget() {
        CandidateKey main = candidate("main");
        CandidateKey first = candidate("main", "a-camera2-native-surface-sharing-v1", 24);
        CandidateKey middle = candidate("main", "a-camera2-native-surface-sharing-v1", 60);
        CandidateKey latest = candidate("main", "a-camera2-native-surface-sharing-v1", 15);
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.setting = Optional.of(first);
        backend.settingsCamera = settingsCamera(1080);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.transitions.clear();
        backend.startupPreviewReady.clear();
        backend.deferSubmit = true;

        assertTrue(coordinator.select("camera:4:main:video-frame-rate", 0));
        backend.setting = Optional.of(middle);
        assertTrue(coordinator.select("camera:4:main:video-frame-rate", 0));
        backend.setting = Optional.of(latest);
        assertTrue(coordinator.select("camera:4:main:video-frame-rate", 0));

        assertEquals(List.of(first, middle, latest), backend.transitions);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertFalse(coordinator.settings(false).cameras().get(0).videoOptions().isEmpty());
        assertEquals(Optional.of(latest), backend.settingsTarget);
        assertTrue(backend.startupPreviewReady.isEmpty());

        backend.completeDeferred();
        backend.completeDeferred();
        assertEquals(Optional.of(latest), backend.settingsTarget);

        backend.completeDeferred();
        assertEquals(Optional.of(latest), backend.active);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        coordinator.settings(false);
        assertEquals(Optional.empty(), backend.settingsTarget);
        assertTrue(backend.startupPreviewReady.isEmpty());
    }

    @Test void capabilityRecheckDoesNotRunWhileRecording() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.recording = true;

        assertFalse(coordinator.recheckCapabilities());
        assertEquals(0, backend.releaseCount);
        assertEquals(0, backend.invalidateCount);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void capabilityRecheckReleasesCameraBeforeInvalidatingAndReloading() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        List<CameraFlowCoordinator.RecheckStatus> updates = new ArrayList<>();

        assertTrue(coordinator.recheckCapabilities(update -> updates.add(update.status())));

        assertEquals(1, backend.releaseCount);
        assertEquals(1, backend.invalidateCount);
        assertEquals(List.of(true, true), backend.capabilityLoadDeepVerify);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertEquals(List.of(CameraFlowCoordinator.RecheckStatus.RUNNING,
                CameraFlowCoordinator.RecheckStatus.SUCCEEDED), updates);
        assertFalse(coordinator.recheckInFlight());
    }

    @Test void idleReleaseRebindsCommittedCandidateWithoutInvalidatingCapabilities() {
        CandidateKey main = candidate("main");
        FakeBackend backend = new FakeBackend(List.of(main));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.transitions.clear();
        backend.transitionTypes.clear();
        List<Boolean> releases = new ArrayList<>();

        assertTrue(coordinator.releaseIdleCamera(releases::add));

        assertEquals(List.of(true), releases);
        assertEquals(CameraFlowCoordinator.State.IDLE, coordinator.state());
        assertEquals(Optional.empty(), backend.active);
        assertEquals(0, backend.invalidateCount);

        coordinator.bindIfNeeded();

        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertEquals(Optional.of(main), backend.active);
        assertEquals(List.of(CameraFlowCoordinator.Transition.BIND_COMMITTED),
                backend.transitionTypes);
        assertEquals(0, backend.invalidateCount);
    }

    @Test void photoRequestedDuringIdleReleaseRunsAfterCommittedRebind() {
        CandidateKey main = candidate("main");
        FakeBackend backend = new FakeBackend(List.of(main));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.transitions.clear();
        backend.transitionTypes.clear();
        backend.deferRelease = true;
        List<String> captures = new ArrayList<>();

        assertTrue(coordinator.releaseIdleCamera(released -> {}));
        assertTrue(coordinator.runWhenReleasedCameraReady(() -> captures.add("photo")));
        assertTrue(captures.isEmpty());

        backend.completeDeferredRelease(true);

        assertEquals(List.of("photo"), captures);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertEquals(List.of(CameraFlowCoordinator.Transition.BIND_COMMITTED),
                backend.transitionTypes);
    }

    @Test void idleReleaseWakeWaitsForTerminalReleaseDespiteTimeoutScheduler() {
        CandidateKey main = candidate("main");
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.deferRelease = true;
        ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend, scheduler);
        coordinator.start();
        backend.transitions.clear();
        backend.transitionTypes.clear();

        assertTrue(coordinator.releaseIdleCamera(released -> {}));
        coordinator.bindIfNeeded();
        scheduler.fire();

        assertTrue(backend.transitionTypes.isEmpty());
        backend.completeDeferredRelease(true);

        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertEquals(List.of(CameraFlowCoordinator.Transition.BIND_COMMITTED),
                backend.transitionTypes);
    }

    @Test void failedCommittedRebindFallsBackAndRetainsDeferredPhoto() {
        CandidateKey main = candidate("main");
        FakeBackend backend = new FakeBackend(List.of(main));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.transitions.clear();
        backend.transitionTypes.clear();
        backend.failNextBindCommitted = true;
        List<String> captures = new ArrayList<>();

        assertTrue(coordinator.releaseIdleCamera(released -> {}));
        assertTrue(coordinator.runWhenReleasedCameraReady(() -> captures.add("photo")));

        assertEquals(List.of("photo"), captures);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertFalse(coordinator.releasedCameraRecoveryPending());
        assertEquals(List.of(CameraFlowCoordinator.Transition.BIND_COMMITTED,
                        CameraFlowCoordinator.Transition.INITIALIZE),
                backend.transitionTypes);
        assertEquals(List.of(true, true), backend.capabilityLoadDeepVerify);
    }
    @Test void idleReleaseRejectsActiveRecording() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.recording = true;

        assertFalse(coordinator.releaseIdleCamera(released -> {}));

        assertEquals(0, backend.releaseCount);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void successfulRecheckRebindsReusableSnapshotWithoutDeepStartupVerify() {
        CandidateKey main = candidate("main");
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.reusableStartup = true;
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();

        assertTrue(coordinator.recheckCapabilities());

        assertEquals(List.of(CameraFlowCoordinator.Transition.BIND_COMMITTED,
                        CameraFlowCoordinator.Transition.BIND_COMMITTED),
                backend.transitionTypes);
        assertEquals(0, backend.markReusableCount);
        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
    }

    @Test void releaseDuringPostRecheckBindPreservesVerifiedSnapshot() {
        CandidateKey main = candidate("main");
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.reusableStartup = true;
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.deferSubmit = true;
        List<CameraFlowCoordinator.RecheckStatus> updates = new ArrayList<>();

        assertTrue(coordinator.recheckCapabilities(update -> updates.add(update.status())));

        assertTrue(coordinator.recheckInFlight());
        assertEquals(List.of(CameraFlowCoordinator.RecheckStatus.RUNNING), updates);
        assertEquals(1, backend.invalidateCount);
        assertEquals(1, backend.releaseCount);

        backend.failDeferred();

        assertFalse(coordinator.recheckInFlight());
        assertEquals(List.of(CameraFlowCoordinator.RecheckStatus.RUNNING,
                CameraFlowCoordinator.RecheckStatus.FAILED), updates);
        assertEquals(CameraFlowCoordinator.State.UNAVAILABLE, coordinator.state());
        assertEquals(1, backend.invalidateCount);
        assertEquals(1, backend.releaseCount);

        backend.deferSubmit = false;
        coordinator.bindIfNeeded();

        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        assertEquals(1, backend.invalidateCount);
    }

    @Test void failedCapabilityReleaseDoesNotStartScan() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.releaseSuccessful = false;
        List<CameraFlowCoordinator.RecheckStatus> updates = new ArrayList<>();

        assertTrue(coordinator.recheckCapabilities(update -> updates.add(update.status())));

        assertEquals(1, backend.releaseCount);
        assertEquals(0, backend.invalidateCount);
        assertEquals(CameraFlowCoordinator.State.UNAVAILABLE, coordinator.state());
        assertEquals(List.of(CameraFlowCoordinator.RecheckStatus.RUNNING,
                CameraFlowCoordinator.RecheckStatus.FAILED), updates);
        assertFalse(coordinator.recheckInFlight());
    }

    @Test void failedCapabilityInvalidationDoesNotStartScan() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.invalidationSuccessful = false;
        List<CameraFlowCoordinator.RecheckStatus> updates = new ArrayList<>();

        assertTrue(coordinator.recheckCapabilities(update -> updates.add(update.status())));

        assertEquals(1, backend.releaseCount);
        assertEquals(1, backend.invalidateCount);
        assertEquals(List.of(true), backend.capabilityLoadDeepVerify);
        assertEquals(CameraFlowCoordinator.State.UNAVAILABLE, coordinator.state());
        assertEquals(List.of(CameraFlowCoordinator.RecheckStatus.RUNNING,
                CameraFlowCoordinator.RecheckStatus.FAILED), updates);
        assertFalse(coordinator.recheckInFlight());
    }

    @Test void releaseTimeoutFailsRecheckAndIgnoresLateCallback() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend, scheduler);
        coordinator.start();
        backend.deferRelease = true;
        List<CameraFlowCoordinator.RecheckStatus> updates = new ArrayList<>();

        assertTrue(coordinator.recheckCapabilities(update -> updates.add(update.status())));
        assertTrue(coordinator.recheckInFlight());

        scheduler.fire();

        assertFalse(coordinator.recheckInFlight());
        assertEquals(CameraFlowCoordinator.State.UNAVAILABLE, coordinator.state());
        assertEquals(0, backend.invalidateCount);
        assertEquals(List.of(CameraFlowCoordinator.RecheckStatus.RUNNING,
                CameraFlowCoordinator.RecheckStatus.FAILED), updates);

        backend.completeDeferredRelease(true);

        assertEquals(CameraFlowCoordinator.State.UNAVAILABLE, coordinator.state());
        assertEquals(0, backend.invalidateCount);
        assertEquals(List.of(CameraFlowCoordinator.RecheckStatus.RUNNING,
                CameraFlowCoordinator.RecheckStatus.FAILED), updates);
    }

    @Test void capabilityRecheckRemainsRunningUntilDeferredVerificationCompletes() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.deferSubmit = true;
        List<CameraFlowCoordinator.RecheckStatus> updates = new ArrayList<>();

        assertTrue(coordinator.recheckCapabilities(update -> updates.add(update.status())));

        assertTrue(coordinator.recheckInFlight());
        assertEquals(List.of(CameraFlowCoordinator.RecheckStatus.RUNNING), updates);

        backend.completeDeferred();

        assertFalse(coordinator.recheckInFlight());
        assertEquals(List.of(CameraFlowCoordinator.RecheckStatus.RUNNING,
                CameraFlowCoordinator.RecheckStatus.SUCCEEDED), updates);
    }

    @Test void startupHidesUnverifiedOptionsUntilFirstReadyProjection() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        backend.settingsCamera = settingsCamera(1080);
        backend.deferSubmit = true;
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);

        coordinator.start();

        assertEquals(CameraFlowCoordinator.State.VERIFYING, coordinator.state());
        assertTrue(coordinator.settings(false).cameras().get(0).videoOptions().isEmpty());
        assertTrue(coordinator.settings(false).cameras().get(0).imageOptions().isEmpty());

        backend.settingsCamera = settingsCamera(1088);
        backend.completeDeferred();

        assertEquals(CameraFlowCoordinator.State.READY, coordinator.state());
        var ready = coordinator.settings(false).cameras().get(0);
        assertEquals(1088, ready.videoOptions().get(0).resolution().height());
        assertEquals(1088, ready.imageOptions().get(0).height());
    }

    @Test void pipelineModeTransitionBlocksRecheckAndCameraSetting() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.setting = Optional.of(candidate("main"));
        backend.pipelineModeTransitionInFlight = true;

        assertFalse(coordinator.recheckCapabilities());
        assertFalse(coordinator.select("camera:4:main:image-resolution", 0));
        assertEquals(0, backend.releaseCount);
        assertTrue(backend.transitions.size() == 1);
    }
    @Test void recordingRejectsAllActiveVfiSettingsBeforeResolvingThem() {
        FakeBackend backend = new FakeBackend(List.of(candidate("main")));
        backend.setting = Optional.of(candidate("main", "a-camera2-native-surface-sharing-v1", 24));
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.recording = true;

        assertFalse(coordinator.canOpenSetting("camera:4:main:video-resolution"));
        assertFalse(coordinator.canOpenSetting("camera:4:main:video-frame-rate"));
        assertFalse(coordinator.canOpenSetting("camera:4:main:image-resolution"));
        assertFalse(coordinator.select("camera:4:main:video-resolution", 0));
        assertFalse(coordinator.select("camera:4:main:video-frame-rate", 0));
        assertFalse(coordinator.select("camera:4:main:image-resolution", 0));
        assertEquals(0, backend.resolveCount);
    }

    @Test void recordingAllowsAllInactiveVfiSettingsWithoutCameraTransition() {
        CandidateKey main = candidate("main");
        CandidateKey auxiliary = candidate("aux", "a-camera2-native-surface-sharing-v1", 24);
        FakeBackend backend = new FakeBackend(List.of(main));
        backend.setting = Optional.of(auxiliary);
        CameraFlowCoordinator coordinator = new CameraFlowCoordinator(backend);
        coordinator.start();
        backend.transitions.clear();
        backend.transitionTypes.clear();
        backend.recording = true;

        assertTrue(coordinator.canOpenSetting("camera:3:aux:video-resolution"));
        assertTrue(coordinator.canOpenSetting("camera:3:aux:video-frame-rate"));
        assertTrue(coordinator.canOpenSetting("camera:3:aux:image-resolution"));
        assertTrue(coordinator.select("camera:3:aux:video-resolution", 0));
        assertTrue(coordinator.select("camera:3:aux:video-frame-rate", 0));
        assertTrue(coordinator.select("camera:3:aux:image-resolution", 0));
        assertEquals(3, backend.resolveCount);
        assertTrue(backend.transitions.isEmpty());
        assertTrue(backend.transitionTypes.isEmpty());
        assertEquals(Optional.of(main), backend.active);
    }

    private static CameraSettingsCamera settingsCamera(int height) {
        CameraResolutionOption resolution = new CameraResolutionOption(
                "FHD", 1920, height);
        return new CameraSettingsCamera("main",
                List.of(new CameraVideoOption(resolution, List.of(30))),
                List.of(resolution),
                new CameraSelection("FHD", 30, "FHD"), null,
                CameraSettingsStatus.READY);
    }

    private static CandidateKey candidate(String cameraId) {
        return candidate(cameraId, "a-camera2-native-surface-sharing-v1");
    }

    private static CandidateKey candidate(String cameraId, String pipelineId) {
        return candidate(cameraId, pipelineId, 30);
    }

    private static CandidateKey candidate(String cameraId, String pipelineId, int fps) {
        StandardResolution resolution = new StandardResolution(StandardResolutionLabel.FHD,
                new CameraResolution(1920, 1080));
        return CandidateKey.forTuple(new CameraId(cameraId), VideoCodec.H264,
                new VerificationPipelineId(pipelineId),
                new CaptureModeTuple(new VideoMode(resolution, fps), new ImageMode(resolution)));
    }

    private static final class ManualTimeoutScheduler
            implements CameraFlowCoordinator.ReleaseTimeoutScheduler {
        private Runnable action;
        private boolean cancelled;

        @Override public CameraFlowCoordinator.TimeoutHandle schedule(
                Runnable action, long delayMillis) {
            this.action = action;
            cancelled = false;
            return () -> cancelled = true;
        }

        private void fire() {
            if (!cancelled && action != null) action.run();
        }
    }

    private static final class FakeBackend implements CameraFlowCoordinator.Backend {
        private final List<CandidateKey> startup;
        private final List<CandidateKey> transitions = new ArrayList<>();
        private final List<CameraFlowCoordinator.Transition> transitionTypes = new ArrayList<>();
        private final List<Boolean> startupPreviewReady = new ArrayList<>();
        private final List<String> startupUnavailableReasons = new ArrayList<>();
        private Optional<CandidateKey> active = Optional.empty();
        private Optional<CandidateKey> setting = Optional.empty();
        private String failCameraId;
        private String failPipelineId;
        private Optional<CandidateKey> rollbackCandidate = Optional.empty();
        private CandidateKey readyCandidateOverride;
        private boolean retainActiveOnSetting;
        private boolean recording;
        private Object forbiddenLock;
        private boolean recordingCalledWhileHoldingForbiddenLock;
        private boolean pipelineModeTransitionInFlight;
        private boolean reusableStartup;
        private boolean deepVerifyOnStartup = true;
        private final List<Boolean> capabilityLoadDeepVerify = new ArrayList<>();
        private List<CandidateKey> switchCandidates = List.of();
        private int switchCandidatesCalls;
        private List<CandidateKey> committedCandidates = List.of();
        private boolean releaseSuccessful = true;
        private boolean invalidationSuccessful = true;
        private boolean deferRelease;
        private boolean failNextBindCommitted;
        private Consumer<Boolean> deferredRelease;
        private int markReusableCount;
        private boolean deferSubmit;
        private CameraSettingsCamera settingsCamera;
        private Optional<CandidateKey> settingsTarget = Optional.empty();
        private final List<DeferredTransition> deferredTransitions = new ArrayList<>();
        private int releaseCount;
        private int invalidateCount;
        private int resolveCount;

        private FakeBackend(List<CandidateKey> startup) { this.startup = startup; }

        @Override public void loadCapabilities(Consumer<List<CandidateKey>> ready,
                Consumer<String> unavailable) { loadCapabilities(true, ready, unavailable); }

        @Override public void loadCapabilities(boolean deepVerify,
                Consumer<List<CandidateKey>> ready, Consumer<String> unavailable) {
            capabilityLoadDeepVerify.add(deepVerify);
            ready.accept(startup);
        }

        @Override public void releaseForCapabilityScan(Consumer<Boolean> completion) {
            releaseCount++;
            active = Optional.empty();
            if (deferRelease) {
                deferredRelease = completion;
                return;
            }
            completion.accept(releaseSuccessful);
        }

        private void completeDeferredRelease(boolean released) {
            Consumer<Boolean> completion = deferredRelease;
            deferredRelease = null;
            completion.accept(released);
        }

        @Override public boolean invalidateCapabilities() {
            invalidateCount++;
            return invalidationSuccessful;
        }

        @Override public void submit(CameraFlowCoordinator.Transition transition,
                CandidateKey candidate,
                Consumer<CameraFlowCoordinator.TransitionResult> completion) {
            transitions.add(candidate);
            transitionTypes.add(transition);
            if (failNextBindCommitted
                    && transition == CameraFlowCoordinator.Transition.BIND_COMMITTED) {
                failNextBindCommitted = false;
                active = Optional.empty();
                completion.accept(new CameraFlowCoordinator.TransitionResult(
                        false, active, "bind_failed"));
                return;
            }
            if (deferSubmit) {
                deferredTransitions.add(new DeferredTransition(candidate, completion));
                return;
            }
            if (candidate.cameraId().value().equals(failCameraId)
                    || candidate.verificationPipelineId().value().equals(failPipelineId)) {
                active = rollbackCandidate;
                completion.accept(new CameraFlowCoordinator.TransitionResult(
                        rollbackCandidate.isPresent(), active, "failed"));
                return;
            }
            if (!retainActiveOnSetting || setting.filter(candidate::equals).isEmpty()) {
                active = Optional.ofNullable(
                        readyCandidateOverride == null ? candidate : readyCandidateOverride);
            }
            completion.accept(new CameraFlowCoordinator.TransitionResult(
                    true, active, "ready"));
        }

        @Override public Optional<CandidateKey> resolveSetting(String stableId, int selectedIndex) {
            resolveCount++;
            return setting;
        }

        @Override public com.dvid.dcam.app.ui.settings.camera.CameraSettingsSource settingsSource(
                Optional<CandidateKey> target, CameraFlowCoordinator.State state) {
            settingsTarget = target;
            return () -> settingsCamera == null
                    ? List.<CameraSettingsCamera>of() : List.of(settingsCamera);
        }

        private void completeDeferred() {
            DeferredTransition deferred = deferredTransitions.remove(0);
            active = Optional.of(deferred.candidate());
            deferred.completion().accept(new CameraFlowCoordinator.TransitionResult(
                    true, active, "ready"));
        }

        private void failDeferred() {
            DeferredTransition deferred = deferredTransitions.remove(0);
            active = Optional.empty();
            deferred.completion().accept(new CameraFlowCoordinator.TransitionResult(
                    false, active, "activity_released"));
        }

        @Override public Optional<CandidateKey> activeCandidate() { return active; }
        @Override public boolean canRestoreExact(CandidateKey candidate) {
            return committedCandidates.contains(candidate);
        }
        @Override public List<CandidateKey> switchCandidates() {
            switchCandidatesCalls++;
            return switchCandidates;
        }
        @Override public int cameraCount() { return switchCandidates.size(); }

        @Override public boolean recording() {
            if (forbiddenLock != null && Thread.holdsLock(forbiddenLock)) {
                recordingCalledWhileHoldingForbiddenLock = true;
            }
            return recording;
        }
        @Override public boolean deepVerifyOnStartup() { return deepVerifyOnStartup; }
        @Override public boolean pipelineModeTransitionInFlight() {
            return pipelineModeTransitionInFlight;
        }
        @Override public boolean reusableStartup() { return reusableStartup; }
        @Override public void markCapabilitiesReusable() { markReusableCount++; }
        @Override public void setStartupPreviewReady(boolean ready) {
            startupPreviewReady.add(ready);
        }
        @Override public void setStartupUnavailable(String reason) {
            startupUnavailableReasons.add(reason);
        }

        private record DeferredTransition(CandidateKey candidate,
                Consumer<CameraFlowCoordinator.TransitionResult> completion) {}
    }
}
