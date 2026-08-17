package com.dvid.dcam.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.capture.application.usecase.CaptureStorageNoticeMonitor;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeBackend;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeOwner;
import java.util.Optional;

import org.junit.jupiter.api.Test;

final class AppCompositionResetTest {
    @Test void cameraTransitionsWaitThroughRecoveryForTerminalRuntimeState() {
        assertFalse(AppComposition.cameraTransitionTerminal(
                snapshot(CameraRuntimeState.RECOVERING, Optional.empty())));
        assertFalse(AppComposition.cameraTransitionTerminal(
                snapshot(CameraRuntimeState.BINDING, Optional.of(
                        ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED))));
        assertTrue(AppComposition.cameraTransitionTerminal(
                snapshot(CameraRuntimeState.READY, Optional.empty())));
    }

    @Test void recordingMinimumRefreshesOncePerReadyCameraGeneration() {
        assertFalse(AppComposition.recordingMinimumRefreshRequired(
                snapshot(CameraRuntimeState.CLOSED, Optional.empty(), 0L), -1L));
        assertTrue(AppComposition.recordingMinimumRefreshRequired(
                snapshot(CameraRuntimeState.READY, Optional.empty(), 1L), -1L));
        assertFalse(AppComposition.recordingMinimumRefreshRequired(
                snapshot(CameraRuntimeState.READY, Optional.empty(), 1L), 1L));
        assertFalse(AppComposition.recordingMinimumRefreshRequired(
                snapshot(CameraRuntimeState.RECOVERING, Optional.empty(), 2L), 1L));
        assertTrue(AppComposition.recordingMinimumRefreshRequired(
                snapshot(CameraRuntimeState.READY, Optional.empty(), 2L), 1L));
    }

    @Test void storageNoticeKeepsLowCapacityInLowStorageFlow() {
        var lowCapacity = CaptureStorageCheck.lowCapacity(1L, 2L, "Not enough free storage");
        var notWritable = CaptureStorageCheck.rejected(1L, 2L, "Storage is not writable");
        var preparing = CaptureStorageCheck.preparing(0L, 2L, "preparing");
        var unavailable = CaptureStorageCheck.unavailable(0L, 2L, "unavailable");

        assertEquals(CaptureStorageNoticeMonitor.State.READY,
                AppComposition.captureStorageNoticeStatus(
                        lowCapacity, "preparing", "unavailable").state());
        assertEquals(CaptureStorageNoticeMonitor.State.UNAVAILABLE,
                AppComposition.captureStorageNoticeStatus(
                        notWritable, "preparing", "unavailable").state());
        assertEquals(CaptureStorageNoticeMonitor.State.PREPARING,
                AppComposition.captureStorageNoticeStatus(
                        preparing, "preparing", "unavailable").state());
        assertEquals(CaptureStorageNoticeMonitor.State.UNAVAILABLE,
                AppComposition.captureStorageNoticeStatus(
                        unavailable, "preparing", "unavailable").state());
    }

    private static ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot(
            CameraRuntimeState state,
            Optional<ProcessCameraRuntimeBackend.Operation> inFlight) {
        return snapshot(state, inFlight, 0L);
    }

    private static ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot(
            CameraRuntimeState state,
            Optional<ProcessCameraRuntimeBackend.Operation> inFlight,
            long transitionGeneration) {
        return new ProcessCameraRuntimeOwner.RuntimeSnapshot(state, Optional.empty(),
                Optional.empty(), transitionGeneration, 0L, inFlight,
                false, false, false, false, false, false);
    }

    @Test void finalOnlyMediaCompletesInterruptedFinalizationState() {
        assertFalse(AppComposition.isInterruptedFinalizationResolved(false, false));
        assertTrue(AppComposition.isInterruptedFinalizationResolved(false, true));
    }

    @Test void resetIsRejectedWhileAudioRecordingIsActive() {
        assertThrows(IllegalStateException.class,
                () -> AppComposition.requireCaptureIdle(RecordingMode.IDLE, true));
    }

    @Test void resetIsRejectedWhileVideoOrSosRecordingIsActive() {
        assertThrows(IllegalStateException.class,
                () -> AppComposition.requireCaptureIdle(RecordingMode.VIDEO, false));
        assertThrows(IllegalStateException.class,
                () -> AppComposition.requireCaptureIdle(RecordingMode.IMP, false));
    }

    @Test void resetIsRejectedWhilePhotoWorkIsActive() {
        assertThrows(IllegalStateException.class, () -> AppComposition.requireCaptureIdle(
                RecordingMode.IDLE, false, true));
    }

    @Test void resetIsAllowedOnlyWhenAllCaptureIsIdle() {
        assertDoesNotThrow(() -> AppComposition.requireCaptureIdle(RecordingMode.IDLE, false));
    }

}
