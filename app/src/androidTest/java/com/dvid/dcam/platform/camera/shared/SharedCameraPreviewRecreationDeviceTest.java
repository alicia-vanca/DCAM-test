package com.dvid.dcam.platform.camera.shared;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEvents;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.platform.camera.shared.runtime.CameraRuntimeSelection;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeBackend;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeOwner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SharedCameraPreviewRecreationDeviceTest {
    private final SharedCameraPreviewView.SurfaceHandle surfaceHandle =
            new SharedCameraPreviewView.SurfaceHandle();

    @After public void closeSurface() {
        surfaceHandle.close();
    }

    @Test public void gatewayKeepsRecordingAcrossPreviewRecreation() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        FakeBackend backend = new FakeBackend(surfaceHandle);
        ProcessCameraRuntimeOwner owner = new ProcessCameraRuntimeOwner(new NoOpLogger());
        SharedCameraGateway gateway = new SharedCameraGateway(
                owner, backend, new NoOpEvents(), new NoOpLogger());
        CountDownLatch recording = new CountDownLatch(1);
        CountDownLatch photoCaptured = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(1);
        owner.attach(snapshot -> {
            if (snapshot.state() == CameraRuntimeState.RECORDING) recording.countDown();
            if (snapshot.state() == CameraRuntimeState.RECORDING
                    && snapshot.inFlight().isEmpty()
                    && backend.operations.contains(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO)) {
                photoCaptured.countDown();
            }
            if (snapshot.state() == CameraRuntimeState.READY
                    && backend.operations.contains(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING)) {
                ready.countDown();
            }
        });

        final SharedCameraPreviewView[] views = new SharedCameraPreviewView[2];
        getInstrumentation().runOnMainSync(() -> {
            views[0] = new SharedCameraPreviewView(context, new NoOpLogger(), surfaceHandle, ignored -> {});
            gateway.attachPreview(views[0]);
        });
        gateway.initialize(selection());
        gateway.startVideo();
        assertTrue(recording.await(5, TimeUnit.SECONDS));

        getInstrumentation().runOnMainSync(() -> {
            gateway.detachPreview(views[0]);
            views[1] = new SharedCameraPreviewView(context, new NoOpLogger(), surfaceHandle, ignored -> {});
            gateway.attachPreview(views[1]);
        });
        gateway.takePhoto();
        assertTrue(photoCaptured.await(5, TimeUnit.SECONDS));
        gateway.stopRecording();
        assertTrue(ready.await(5, TimeUnit.SECONDS));

        assertNotNull(views[1]);
        assertSame(surfaceHandle.surface(), views[0].surfaceHandle().surface());
        assertSame(surfaceHandle.surface(), views[1].surfaceHandle().surface());
        assertEquals(List.of(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
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
        private final SharedCameraPreviewOutput output;
        private final List<Operation> operations = new ArrayList<>();
        private SosHandoff handoff = () -> {};

        private FakeBackend(SharedCameraPreviewOutput output) { this.output = output; }
        @Override public SharedCameraPreviewOutput previewOutput() { return output; }
        @Override public void refreshDisplayRotation() {}
        @Override public void setSosHandoff(SosHandoff value) { handoff = value; }
        @Override public void requestRecording(RecordingMode mode) {}
        @Override public void requestSosHandoff() {}
        @Override public void setRecordingStorageLimit(Runnable listener) {}
        @Override public boolean recordingStorageLimitRequested() { return false; }
        @Override public void cancelPendingRecording() {}

        @Override public void execute(Command command, Completion completion) {
            operations.add(command.operation());
            if (command.operation() == Operation.INITIALIZE) {
                CameraRuntimeSelection target = command.target().orElseThrow();
                CameraOperationContext context = new CameraOperationContext(target.cameraId(),
                        target.verificationPipelineId(), target.codec(), target.tuple(),
                        command.operationSequence(), command.healthGeneration(),
                        CameraOperationDeadline.forCandidate(
                                Math.max(0L, System.nanoTime() / 1_000_000L)));
                completion.complete(Result.ready(target, context, "ready"));
            } else {
                completion.complete(Result.pass(command.operation().name().toLowerCase()));
            }
        }
    }

    private static final class NoOpEvents implements CaptureEvents {
        @Override public void setListener(java.util.function.Consumer<CaptureEvent> listener) {}
        @Override public void clearListener() {}
        @Override public RecordingMode currentMode() { return RecordingMode.IDLE; }
        @Override public void recordingStarted(RecordingMode mode, String fileName) {}
        @Override public void recordingInterrupted(String message) {}
        @Override public void recordingResumed() {}
        @Override public void recordingCompleted(String fileName) {}
        @Override public void recordingStoppedForStorage(String fileName) {}
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
