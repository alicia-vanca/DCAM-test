package com.dvid.dcam.platform.camera.shared.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
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
import org.junit.jupiter.api.Test;

final class ProcessCameraRuntimeRecoveryTest {
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");

    @Test void startupUnavailableRetriesOriginalSelectionOnWatchdog() {
        Fixture fixture = new Fixture();
        CameraRuntimeSelection selection = selection("0");
        fixture.owner.initialize(selection);
        fixture.backend.completeNext(ProcessCameraRuntimeBackend.Result.recoveryRequired("busy"));

        assertEquals(CameraRuntimeState.RECOVERING, fixture.owner.snapshot().state());
        fixture.scheduler.runNext();
        assertEquals(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                fixture.backend.last().operation());
        assertEquals(selection, fixture.backend.last().target().orElseThrow());
        fixture.backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 2), "ready"));
        assertEquals(CameraRuntimeState.READY, fixture.owner.snapshot().state());
    }

    @Test void ownUnavailableCallbackDoesNotInterruptReadyAndAvailableWakesRecovery() {
        Fixture fixture = new Fixture();
        CameraRuntimeSelection selection = fixture.ready();
        fixture.monitor.current().onUnavailable(selection.cameraId());
        assertEquals(CameraRuntimeState.READY, fixture.owner.snapshot().state());

        fixture.owner.reportGlobalFailure("camera_disconnected");
        assertEquals(CameraRuntimeState.RECOVERING, fixture.owner.snapshot().state());
        fixture.monitor.current().onAvailable(selection.cameraId());
        assertEquals(ProcessCameraRuntimeBackend.Operation.RECOVER,
                fixture.backend.last().operation());
        assertEquals(selection, fixture.backend.last().target().orElseThrow());
    }

    @Test void unchangedSourceAndPreviewFramesEnterRecovery() {
        Fixture fixture = new Fixture();
        fixture.ready();
        fixture.owner.setPreviewExpected(true);
        fixture.backend.health = Optional.of(health(4, 4));
        fixture.scheduler.runNext();
        fixture.scheduler.runNext();
        assertEquals(CameraRuntimeState.RECOVERING, fixture.owner.snapshot().state());
    }

    @Test void staleAvailabilityCallbackCannotRecoverNewCamera() {
        Fixture fixture = new Fixture();
        CameraRuntimeSelection first = fixture.ready();
        CameraAvailabilityMonitor.Listener stale = fixture.monitor.current();
        CameraRuntimeSelection second = selection("1");
        fixture.owner.switchCamera(second);
        fixture.backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                second, context(second, 2), "ready"));

        stale.onUnavailable(first.cameraId());
        assertEquals(CameraRuntimeState.READY, fixture.owner.snapshot().state());
        assertEquals(second, fixture.owner.snapshot().committedSelection().orElseThrow());
    }

    @Test void exactRestoreDirectlyBindsRequestedSelection() {
        Fixture fixture = new Fixture();
        CameraRuntimeSelection main = fixture.ready();
        CameraRuntimeSelection auxiliary = selection("1");
        fixture.owner.switchCamera(auxiliary);
        fixture.backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                auxiliary, context(auxiliary, 2), "ready"));

        assertEquals(ProcessCameraRuntimeOwner.Submission.ACCEPTED,
                fixture.owner.restoreExact(main));
        assertEquals(ProcessCameraRuntimeBackend.Operation.RESTORE_EXACT,
                fixture.backend.last().operation());
        assertEquals(main, fixture.backend.last().target().orElseThrow());
        fixture.backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                main, context(main, 3), "restored"));

        assertEquals(CameraRuntimeState.READY, fixture.owner.snapshot().state());
        assertEquals(main, fixture.owner.snapshot().committedSelection().orElseThrow());
    }

    @Test void failedRecoveryRemainsRecoveringAndRetriesLater() {
        Fixture fixture = new Fixture();
        CameraRuntimeSelection selection = fixture.ready();
        fixture.owner.reportGlobalFailure("camera_disconnected");
        fixture.monitor.current().onAvailable(selection.cameraId());
        fixture.backend.completeNext(ProcessCameraRuntimeBackend.Result.recoveryRequired("busy"));
        assertEquals(CameraRuntimeState.RECOVERING, fixture.owner.snapshot().state());
        assertTrue(fixture.scheduler.hasPending());
    }

    @Test void hiddenPreviewIgnoresPreviewStallButSourceStallStillRecovers() {
        Fixture fixture = new Fixture();
        fixture.ready();
        fixture.owner.setPreviewExpected(true);
        fixture.owner.setPreviewExpected(false);
        fixture.backend.health = Optional.of(health(4, 4));
        fixture.scheduler.runNext();

        fixture.backend.health = Optional.of(health(5, 4));
        fixture.scheduler.runNext();
        assertEquals(CameraRuntimeState.READY, fixture.owner.snapshot().state());

        fixture.scheduler.runNext();
        assertEquals(CameraRuntimeState.RECOVERING, fixture.owner.snapshot().state());
    }

    @Test void recordingFailureStopsBeforeRecoveryAndNeverRestarts() {
        Fixture fixture = new Fixture();
        CameraRuntimeSelection selection = fixture.ready();
        fixture.owner.startRecording();
        fixture.backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("recording"));

        fixture.owner.reportGlobalFailure("camera_disconnected");
        assertEquals(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                fixture.backend.last().operation());
        fixture.backend.completeNext(ProcessCameraRuntimeBackend.Result.pass("finalized"));
        fixture.monitor.current().onAvailable(selection.cameraId());
        fixture.backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                selection, context(selection, 3), "recovered"));

        assertEquals(CameraRuntimeState.READY, fixture.owner.snapshot().state());
        assertEquals(1, fixture.backend.count(ProcessCameraRuntimeBackend.Operation.START_RECORDING));
        assertEquals(1, fixture.backend.count(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING));
    }

    @Test void lifecyclePauseSuppressesAndResumeImmediatelyRetries() {
        Fixture fixture = new Fixture();
        CameraRuntimeSelection selection = fixture.ready();
        fixture.owner.reportGlobalFailure("camera_disconnected");
        fixture.owner.setCameraUseAllowed(false);
        fixture.monitor.current().onAvailable(selection.cameraId());
        assertEquals(0, fixture.backend.count(ProcessCameraRuntimeBackend.Operation.RECOVER));

        fixture.owner.setCameraUseAllowed(true);
        assertEquals(ProcessCameraRuntimeBackend.Operation.RECOVER,
                fixture.backend.last().operation());
    }
    private static ProcessCameraRuntimeBackend.HealthSnapshot health(long source, long preview) {
        return new ProcessCameraRuntimeBackend.HealthSnapshot(
                true, false, true, source, preview, "pass");
    }

    private static CameraRuntimeSelection selection(String cameraId) {
        StandardResolution resolution = new StandardResolution(
                StandardResolutionLabel.FHD, new CameraResolution(1920, 1080));
        return new CameraRuntimeSelection(new CameraId(cameraId), PIPELINE, VideoCodec.H264,
                new CaptureModeTuple(new VideoMode(resolution, 30), new ImageMode(resolution)));
    }

    private static CameraOperationContext context(CameraRuntimeSelection selection, long generation) {
        return new CameraOperationContext(selection.cameraId(), selection.verificationPipelineId(),
                selection.codec(), selection.tuple(), generation, 0,
                CameraOperationDeadline.forCandidate(0));
    }

    private static final class Fixture {
        private final ManualBackend backend = new ManualBackend();
        private final ManualScheduler scheduler = new ManualScheduler();
        private final ManualMonitor monitor = new ManualMonitor();
        private final ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), Runnable::run, monitor, scheduler);

        private Fixture() {
            owner.installBackend(backend);
        }

        private CameraRuntimeSelection ready() {
            CameraRuntimeSelection selection = selection("0");
            owner.initialize(selection);
            backend.completeNext(ProcessCameraRuntimeBackend.Result.ready(
                    selection, context(selection, 1), "ready"));
            return selection;
        }
    }

    private static final class ManualBackend implements ProcessCameraRuntimeBackend {
        private final List<Command> history = new ArrayList<>();
        private final List<Invocation> pending = new ArrayList<>();
        private Optional<HealthSnapshot> health = Optional.empty();

        @Override public void execute(Command command, Completion completion) {
            history.add(command);
            pending.add(new Invocation(command, completion));
        }

        @Override public Optional<HealthSnapshot> healthSnapshot(CameraOperationContext binding) {
            return health;
        }

        private void completeNext(Result result) {
            Invocation invocation = pending.remove(0);
            invocation.completion.complete(result);
        }

        private Command last() { return history.get(history.size() - 1); }

        private int count(Operation operation) {
            return (int) history.stream().filter(value -> value.operation() == operation).count();
        }

        private record Invocation(Command command, Completion completion) {}
    }

    private static final class ManualScheduler implements CameraRuntimeRecoveryScheduler {
        private final List<TaskEntry> tasks = new ArrayList<>();

        @Override public Task schedule(Runnable action, long delayMillis) {
            assertEquals(3000L, delayMillis);
            TaskEntry entry = new TaskEntry(action);
            tasks.add(entry);
            return () -> entry.cancelled = true;
        }

        private void runNext() {
            for (TaskEntry task : tasks) {
                if (!task.cancelled && !task.ran) {
                    task.ran = true;
                    task.action.run();
                    return;
                }
            }
            throw new AssertionError("no pending scheduler task");
        }

        private boolean hasPending() {
            return tasks.stream().anyMatch(task -> !task.cancelled && !task.ran);
        }

        @Override public void close() {}
        private static final class TaskEntry {
            private final Runnable action;
            private boolean cancelled;
            private boolean ran;
            private TaskEntry(Runnable action) { this.action = action; }
        }
    }

    private static final class ManualMonitor implements CameraAvailabilityMonitor {
        private final List<Registration> registrations = new ArrayList<>();

        @Override public Attachment watch(CameraId cameraId, Listener listener) {
            Registration registration = new Registration(cameraId, listener);
            registrations.add(registration);
            return () -> registration.closed = true;
        }

        private Listener current() {
            for (int index = registrations.size() - 1; index >= 0; index--) {
                Registration registration = registrations.get(index);
                if (!registration.closed) return registration.listener;
            }
            throw new AssertionError("no active availability registration");
        }

        @Override public void close() {}
        private static final class Registration {
            private final CameraId cameraId;
            private final Listener listener;
            private boolean closed;
            private Registration(CameraId cameraId, Listener listener) {
                this.cameraId = cameraId;
                this.listener = listener;
            }
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
