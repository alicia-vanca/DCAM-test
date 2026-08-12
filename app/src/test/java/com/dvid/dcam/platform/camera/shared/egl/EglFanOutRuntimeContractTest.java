package com.dvid.dcam.platform.camera.shared.egl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.platform.camera.shared.CameraPipelineFailureClassifier;
import com.dvid.dcam.platform.camera.shared.CameraPipelineIds;
import com.dvid.dcam.platform.camera.shared.CameraSurfaceTopology;
import org.junit.jupiter.api.Test;

final class EglFanOutRuntimeContractTest {
    @Test void topologyKeepsPreviewAndEncoderDownstreamFromCamera() {
        CameraSurfaceTopology topology = new CameraSurfaceTopology(2, 2, 1);

        assertTrue(topology.isExactPrivateSourcePlusJpeg());
        assertEquals(2, topology.cameraOutputCount());
        assertEquals(2, topology.privateSurfaceCount());
        assertEquals(1, topology.jpegSurfaceCount());
        assertEquals("b-camera2-egl-fanout-v1",
                CameraPipelineIds.EGL_FAN_OUT.value());
    }

    @Test void topologyRejectsExtraCameraOrDownstreamOutputs() {
        assertFalse(new CameraSurfaceTopology(3, 2, 1).isExactPrivateSourcePlusJpeg());
        assertFalse(new CameraSurfaceTopology(2, 3, 1).isExactPrivateSourcePlusJpeg());
        assertFalse(new CameraSurfaceTopology(2, 2, 2).isExactPrivateSourcePlusJpeg());
    }

    @Test void encoderBranchWinsWhenSourceBacklogAppears() {
        EglFrameDispatchPolicy.Decision clear =
                EglFrameDispatchPolicy.decide(true, true, 1);
        EglFrameDispatchPolicy.Decision backlog =
                EglFrameDispatchPolicy.decide(true, true, 2);

        assertTrue(clear.renderEncoder());
        assertTrue(clear.renderPreview());
        assertTrue(backlog.renderEncoder());
        assertFalse(backlog.renderPreview());
        assertTrue(backlog.countPreviewDrop());
    }

    @Test void detachFailurePolicyClearsEncoderStateAndPreservesTaxonomy() {
        EglEncoderDetachPolicy.Decision success = EglEncoderDetachPolicy.decide(true, false);
        EglEncoderDetachPolicy.Decision interrupted = EglEncoderDetachPolicy.decide(false, true);
        EglEncoderDetachPolicy.Decision failed = EglEncoderDetachPolicy.decide(false, false);

        assertEquals(CameraOperationOutcome.PASS, success.outcome());
        assertEquals(CameraOperationOutcome.CANCELLED_UNKNOWN, interrupted.outcome());
        assertEquals(CameraOperationOutcome.TRANSIENT_RETRYABLE, failed.outcome());
        assertFalse(success.encoderActive());
        assertFalse(interrupted.encoderActive());
        assertFalse(failed.encoderActive());
    }

    @Test void eglGlobalErrorsNeverBecomeTupleUnsupported() {
        assertEquals(CameraOperationOutcome.BLOCKED_EXTERNAL,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.EGL_UNAVAILABLE));
        assertEquals(CameraOperationOutcome.GLOBAL_FAILURE,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.EGL_CONTEXT_LOST));
        assertEquals(CameraOperationOutcome.TRANSIENT_RETRYABLE,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.CAMERA_IN_USE));
        assertEquals(CameraOperationOutcome.CANDIDATE_SUSPECT,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.SESSION_REJECTED));
        assertEquals(CameraOperationOutcome.GLOBAL_FAILURE,
                CameraPipelineFailureClassifier.classify(
                        CameraPipelineFailureClassifier.Signal.UNKNOWN_GLOBAL));
    }

    @Test void camera2ErrorsUseSharedTaxonomy() {
        assertEquals(CameraOperationOutcome.TRANSIENT_RETRYABLE,
                CameraPipelineFailureClassifier.classifyCameraAccessReason(
                        CameraAccessException.CAMERA_IN_USE));
        assertEquals(CameraOperationOutcome.GLOBAL_FAILURE,
                CameraPipelineFailureClassifier.classifyCameraStateError(
                        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE));
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