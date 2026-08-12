package com.dvid.dcam.platform.camera.shared.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEvents;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.platform.camera.shared.SharedCameraGateway;
import com.dvid.dcam.platform.camera.shared.SharedCameraGatewayBackend;
import com.dvid.dcam.platform.camera.shared.SharedCameraPreviewOutput;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

final class SharedCameraGatewayOwnerContractTest {
    @Test void featureCommandsDelegateThroughOneProcessOwnerBackend() {
        FakeBackend backend = new FakeBackend(false);
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), Runnable::run);
        SharedCameraGateway gateway = new SharedCameraGateway(
                owner, backend, new FakeEvents(), new NoOpLogger());

        gateway.initialize(selection());
        gateway.startVideo();
        gateway.takePhoto();
        gateway.stopRecording();

        assertEquals(List.of(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                ProcessCameraRuntimeBackend.Operation.STOP_RECORDING), backend.operations);
        assertEquals(List.of(RecordingMode.VIDEO), backend.requestedModes);
        assertEquals(com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState.READY,
                gateway.snapshot().state());
    }

    @Test void stopBeforeReadyConsumesHeldRecordingWithoutLateStart() {
        FakeBackend backend = new FakeBackend(true);
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), Runnable::run);
        SharedCameraGateway gateway = new SharedCameraGateway(
                owner, backend, new FakeEvents(), new NoOpLogger());

        gateway.initialize(selection());
        gateway.startVideo();
        gateway.stopRecording();
        backend.completeDelayedInitialize();

        assertFalse(backend.operations.contains(
                ProcessCameraRuntimeBackend.Operation.START_RECORDING));
        assertEquals(1, backend.cancelPendingCount);
    }

    @Test void impHandoffFinalizesCurrentSegmentBeforeStartingSos() {
        FakeBackend backend = new FakeBackend(false);
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), Runnable::run);
        SharedCameraGateway gateway = new SharedCameraGateway(
                owner, backend, new FakeEvents(), new NoOpLogger());

        gateway.initialize(selection());
        gateway.startVideo();
        gateway.startImp();

        assertEquals(List.of(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                ProcessCameraRuntimeBackend.Operation.START_RECORDING), backend.operations);
        assertEquals(List.of(RecordingMode.VIDEO, RecordingMode.IMP), backend.requestedModes);
    }

    @Test void asynchronousImpHandoffWaitsForReadyBeforeStartingNextSegment() {
        FakeBackend backend = new FakeBackend(false);
        ManualExecutor executor = new ManualExecutor();
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), executor);
        SharedCameraGateway gateway = new SharedCameraGateway(
                owner, backend, new FakeEvents(), new NoOpLogger());

        gateway.initialize(selection());
        executor.runAll();
        gateway.startVideo();
        executor.runAll();
        gateway.startImp();
        executor.runAll();

        assertEquals(List.of(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                ProcessCameraRuntimeBackend.Operation.START_RECORDING), backend.operations);
        assertEquals(List.of(RecordingMode.VIDEO, RecordingMode.IMP), backend.requestedModes);
    }

    @Test void storageLimitCallbackStopsActiveRecording() {
        FakeBackend backend = new FakeBackend(false);
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(
                new NoOpLogger(), Runnable::run);
        SharedCameraGateway gateway = new SharedCameraGateway(
                owner, backend, new FakeEvents(), new NoOpLogger());

        gateway.initialize(selection());
        gateway.startVideo();
        backend.triggerStorageLimit();

        assertEquals(List.of(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                ProcessCameraRuntimeBackend.Operation.STOP_RECORDING), backend.operations);
    }

    private static CameraRuntimeSelection selection() {
        StandardResolution fhd = new StandardResolution(StandardResolutionLabel.FHD,
                new CameraResolution(1920, 1080));
        StandardResolution hd = new StandardResolution(StandardResolutionLabel.HD,
                new CameraResolution(1280, 720));
        return new CameraRuntimeSelection(new CameraId("0"),
                new VerificationPipelineId("a-camera2-native-surface-sharing-v1"),
                VideoCodec.H264, new CaptureModeTuple(
                        new VideoMode(fhd, 30), new ImageMode(hd)));
    }

    private static final class FakeBackend implements SharedCameraGatewayBackend {
        private final boolean delayInitialize;
        private final List<Operation> operations = new ArrayList<>();
        private final List<RecordingMode> requestedModes = new ArrayList<>();
        private SharedCameraGatewayBackend.ImpHandoff handoff = () -> {};
        private Runnable storageLimitListener = () -> {};
        private Completion delayedCompletion;
        private Command delayedCommand;
        private boolean handoffRequested;
        private boolean storageLimitRequested;
        private int cancelPendingCount;

        private FakeBackend(boolean delayInitialize) {
            this.delayInitialize = delayInitialize;
        }

        @Override public SharedCameraPreviewOutput previewOutput() { return null; }
        @Override public void refreshDisplayRotation() {}
        @Override public void setImpHandoff(SharedCameraGatewayBackend.ImpHandoff value) {
            handoff = value;
        }
        @Override public void requestRecording(RecordingMode mode) {
            requestedModes.add(mode);
        }
        @Override public void requestImpHandoff() { handoffRequested = true; }
        @Override public void setRecordingStorageLimit(Runnable listener) {
            storageLimitListener = listener;
        }
        @Override public boolean recordingStorageLimitRequested() {
            return storageLimitRequested;
        }
        @Override public void cancelPendingRecording() { cancelPendingCount++; }

        private void triggerStorageLimit() {
            storageLimitRequested = true;
            storageLimitListener.run();
        }

        @Override public void execute(Command command, Completion completion) {
            operations.add(command.operation());
            if (command.operation() == Operation.INITIALIZE && delayInitialize) {
                delayedCommand = command;
                delayedCompletion = completion;
                return;
            }
            complete(command, completion);
        }

        private void completeDelayedInitialize() {
            complete(delayedCommand, delayedCompletion);
        }

        private void complete(Command command, Completion completion) {
            if (command.operation() == Operation.INITIALIZE) {
                CameraRuntimeSelection target = command.target().orElseThrow();
                CameraOperationContext context = new CameraOperationContext(target.cameraId(),
                        target.verificationPipelineId(), target.codec(), target.tuple(),
                        command.operationSequence(), command.healthGeneration(),
                        CameraOperationDeadline.forCandidate(0L));
                completion.complete(Result.ready(target, context, "ready"));
                return;
            }
            completion.complete(Result.pass(command.operation().name().toLowerCase()));
            if (command.operation() == Operation.STOP_RECORDING && handoffRequested) {
                handoffRequested = false;
                handoff.startImpRecording();
            }
        }
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override public void execute(Runnable command) {
            tasks.addLast(command);
        }

        private void runAll() {
            int remaining = 100;
            while (!tasks.isEmpty() && remaining-- > 0) tasks.removeFirst().run();
            if (!tasks.isEmpty()) throw new AssertionError("executor did not drain");
        }
    }

    private static final class FakeEvents implements CaptureEvents {
        @Override public void setListener(java.util.function.Consumer<CaptureEvent> listener) {}
        @Override public void clearListener() {}
        @Override public RecordingMode currentMode() { return RecordingMode.IDLE; }
        @Override public void recordingStarted(RecordingMode mode, String fileName) {}
        @Override public void recordingInterrupted(String message) {}
        @Override public void recordingResumed() {}
        @Override public void recordingCompleted(String fileName) {}
        @Override public void recordingStoppedForStorage(String fileName) {}
        @Override public void audioRecordingStarted(String fileName, long startedAtMillis) {}
        @Override public void audioRecordingStopped(String fileName) {}
        @Override public void photoSaved(String fileName) {}
        @Override public void photoFailed(String operation, String message) {}
        @Override public void captureFailed(String operation, String message) {}
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    }
}