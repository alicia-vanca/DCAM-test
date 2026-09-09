package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeBackend;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeOwner;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SharedCameraGatewayStartupRecoveryTest {
    @Test void suppressesOnlyTheFirstRecoveryEpisodeBeforeTheFirstFrame() {
        assertTrue(SharedCameraGateway.isInitialStartupRecovery(
                snapshot(CameraRuntimeState.RECOVERING, 1), 0));
        assertFalse(SharedCameraGateway.isInitialStartupRecovery(
                snapshot(CameraRuntimeState.RECOVERING, 2), 0));
        assertFalse(SharedCameraGateway.isInitialStartupRecovery(
                snapshot(CameraRuntimeState.READY, 1), 0));
        assertFalse(SharedCameraGateway.isInitialStartupRecovery(
                snapshot(CameraRuntimeState.RECOVERING, 1), -1));
    }

    private static ProcessCameraRuntimeOwner.RuntimeSnapshot snapshot(
            CameraRuntimeState state, long healthGeneration) {
        return new ProcessCameraRuntimeOwner.RuntimeSnapshot(
                state, Optional.empty(), Optional.empty(), 0, healthGeneration,
                Optional.<ProcessCameraRuntimeBackend.Operation>empty(),
                false, false, false, false, false, false);
    }
}
