package com.dvid.dcam.feature.device.domain.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CameraOperationContractTest {
    private static final CameraId CAMERA = new CameraId("0");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");
    private static final CaptureModeTuple TUPLE = new CaptureModeTuple(
            new VideoMode(new StandardResolution(
                    StandardResolutionLabel.FHD,
                    new CameraResolution(1920, 1080)), 30),
            new ImageMode(new StandardResolution(
                    StandardResolutionLabel.UHD,
                    new CameraResolution(3840, 2160))));

    @Test void candidateDeadlineUsesOneFiveSecondBudget() {
        CameraOperationDeadline deadline = CameraOperationDeadline.forCandidate(1_000L);

        assertEquals(5_000L, deadline.timeoutMillis());
        assertEquals(6_000L, deadline.deadlineAtMillis());
        assertEquals(5_000L, deadline.remainingMillis(500L));
        assertEquals(1L, deadline.remainingMillis(5_999L));
        assertEquals(0L, deadline.remainingMillis(6_000L));
        assertTrue(deadline.isExpiredAt(6_000L));
        assertThrows(IllegalArgumentException.class,
                () -> CameraOperationDeadline.after(10L, 0L));
    }

    @Test void sessionAndHealthGenerationsRejectLateCallbacks() {
        CameraOperationContext oldSession = context(1L, 2L);
        CameraOperationResult callback = result(
                oldSession, CameraOperationOutcome.PASS, 100L);

        assertEquals(CameraOperationOutcome.STALE,
                callback.effectiveOutcomeAgainst(context(2L, 2L), 2_000L));
        assertEquals(CameraOperationOutcome.STALE,
                callback.effectiveOutcomeAgainst(context(1L, 3L), 2_000L));
        assertEquals(CameraOperationOutcome.PASS,
                callback.effectiveOutcomeAgainst(oldSession, 2_000L));
    }

    @Test void candidateConclusionAfterDeadlineRemainsUnknown() {
        CameraOperationContext context = context(1L, 0L);

        assertEquals(CameraOperationOutcome.PASS,
                result(context, CameraOperationOutcome.PASS, 5_000L)
                        .effectiveOutcomeAgainst(context, 5_999L));
        assertEquals(CameraOperationOutcome.TIMEOUT_UNKNOWN,
                result(context, CameraOperationOutcome.DEFINITIVE_CANDIDATE_FAILURE, 5_001L)
                        .effectiveOutcomeAgainst(context, 6_000L));
        assertEquals(CameraOperationOutcome.GLOBAL_FAILURE,
                result(context, CameraOperationOutcome.GLOBAL_FAILURE, 5_001L)
                        .effectiveOutcomeAgainst(context, 6_000L));
    }

    @Test void outcomeClassificationSeparatesCandidateAndGlobalFailures() {
        assertTrue(CameraOperationOutcome.CANDIDATE_SUSPECT.isUnknown());
        assertFalse(CameraOperationOutcome.CANDIDATE_SUSPECT.isCandidateConclusion());
        assertTrue(CameraOperationOutcome.DEFINITIVE_CANDIDATE_FAILURE
                .isCandidateConclusion());
        assertFalse(CameraOperationOutcome.DEFINITIVE_CANDIDATE_FAILURE
                .requiresRuntimeRecovery());
        assertTrue(CameraOperationOutcome.GLOBAL_FAILURE.isGlobalFailure());
        assertTrue(CameraOperationOutcome.GLOBAL_FAILURE.requiresRuntimeRecovery());
        assertTrue(CameraOperationOutcome.TRANSIENT_RETRYABLE.requiresRuntimeRecovery());
        assertTrue(CameraOperationOutcome.BLOCKED_EXTERNAL.isUnknown());
        assertTrue(CameraOperationOutcome.CANCELLED_UNKNOWN.isUnknown());
        assertTrue(CameraOperationOutcome.STALE.isUnknown());
    }

    @Test void operationContextRequiresCompleteRuntimeIdentity() {
        CameraOperationContext context = context(7L, 4L);

        assertEquals(CAMERA, context.cameraId());
        assertEquals(PIPELINE, context.verificationPipelineId());
        assertEquals(VideoCodec.H264, context.codec());
        assertEquals(TUPLE, context.tuple());
        assertEquals(7L, context.sessionGeneration());
        assertEquals(4L, context.cameraHealthGeneration());
        assertThrows(IllegalArgumentException.class,
                () -> context(0L, 0L));
    }

    private static CameraOperationContext context(
            long sessionGeneration, long healthGeneration) {
        return new CameraOperationContext(
                CAMERA,
                PIPELINE,
                VideoCodec.H264,
                TUPLE,
                sessionGeneration,
                healthGeneration,
                CameraOperationDeadline.forCandidate(1_000L));
    }

    private static CameraOperationResult result(
            CameraOperationContext context,
            CameraOperationOutcome outcome,
            long elapsedMillis) {
        return new CameraOperationResult(
                context,
                CameraPipelineOperation.BIND_SESSION,
                outcome,
                elapsedMillis,
                "test");
    }
}