package com.dvid.dcam.platform.camera.shared.outputsharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.platform.camera.shared.CameraPipelineFailureClassifier;
import com.dvid.dcam.platform.camera.shared.CameraPipelineIds;
import com.dvid.dcam.platform.camera.shared.CameraSurfaceTopology;
import org.junit.jupiter.api.Test;

final class NativeSurfaceSharingRuntimeContractTest {
    @Test void topologyIsOneSharedPrivateOutputPlusOneJpegOutput() {
        CameraSurfaceTopology topology = new CameraSurfaceTopology(2, 2, 1);

        assertTrue(topology.isExactPrivateSourcePlusJpeg());
        assertEquals(2, topology.cameraOutputCount());
        assertEquals(2, topology.privateSurfaceCount());
        assertEquals(1, topology.jpegSurfaceCount());
        assertEquals("a-camera2-native-surface-sharing-v1",
                CameraPipelineIds.NATIVE_SURFACE_SHARING.value());
    }

    @Test void topologyRejectsExtraCameraOrSharedOutputs() {
        assertFalse(new CameraSurfaceTopology(3, 2, 1)
                .isExactPrivateSourcePlusJpeg());
        assertFalse(new CameraSurfaceTopology(2, 3, 1)
                .isExactPrivateSourcePlusJpeg());
        assertFalse(new CameraSurfaceTopology(2, 2, 2)
                .isExactPrivateSourcePlusJpeg());
    }

    @Test void globalCameraFailuresNeverBecomeTupleUnsupported() {
        assertEquals(CameraOperationOutcome.TRANSIENT_RETRYABLE,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.CAMERA_IN_USE));
        assertEquals(CameraOperationOutcome.TRANSIENT_RETRYABLE,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.CAMERA_DEVICE_ERROR));
        assertEquals(CameraOperationOutcome.GLOBAL_FAILURE,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.CAMERA_SERVICE_ERROR));
        assertEquals(CameraOperationOutcome.BLOCKED_EXTERNAL,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.PERMISSION_BLOCKED));
    }

    @Test void onlyExactSessionAndOutputFailuresAreCandidateSuspect() {
        assertEquals(CameraOperationOutcome.CANDIDATE_SUSPECT,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.SESSION_REJECTED));
        assertEquals(CameraOperationOutcome.CANDIDATE_SUSPECT,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.OUTPUT_INVALID));
        assertEquals(CameraOperationOutcome.GLOBAL_FAILURE,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.UNKNOWN_GLOBAL));
        assertEquals(CameraOperationOutcome.TIMEOUT_UNKNOWN,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.TIMEOUT));
    }

    @Test void cleanupTimeoutOverridesCandidateButNotCancellation() {
        assertEquals(CameraOperationOutcome.TRANSIENT_RETRYABLE,
                CameraPipelineFailureClassifier.withCleanupResult(
                        CameraOperationOutcome.CANDIDATE_SUSPECT, false));
        assertEquals(CameraOperationOutcome.TRANSIENT_RETRYABLE,
                CameraPipelineFailureClassifier.withCleanupResult(
                        CameraOperationOutcome.CANCELLED_UNKNOWN, false));
        assertEquals(CameraOperationOutcome.CANDIDATE_SUSPECT,
                CameraPipelineFailureClassifier.withCleanupResult(
                        CameraOperationOutcome.CANDIDATE_SUSPECT, true));
    }
}