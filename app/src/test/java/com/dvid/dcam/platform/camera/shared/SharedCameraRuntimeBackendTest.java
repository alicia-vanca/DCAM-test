package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.application.usecase.CaptureEvents;
import com.dvid.dcam.feature.capture.domain.CaptureEvent;
import com.dvid.dcam.feature.capture.domain.RecordingMode;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationResult;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineOperation;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.platform.camera.shared.runtime.CameraRuntimeSelection;
import com.dvid.dcam.platform.camera.shared.verification.SharedCameraVerificationSession;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeBackend;
import com.dvid.dcam.platform.storage.DcamFileType;
import com.dvid.dcam.platform.storage.DcamMediaFile;
import com.dvid.dcam.platform.storage.DcamMediaOutputImpl;
import com.dvid.dcam.platform.storage.DcamRecordingOutput;
import com.dvid.dcam.platform.storage.DcamStorage;
import com.dvid.dcam.platform.storage.SegmentedAesGcmJpegOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SharedCameraRuntimeBackendTest {
    @Test void cancelledTransitionStopsBeforePipelineCreation() {
        FakeProvider provider = new FakeProvider(new FakePipeline());
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), new FakeEvents(),
                new NoOpLogger());
        ProcessCameraRuntimeBackend.Command command = command(
                ProcessCameraRuntimeBackend.Operation.VERIFY_SETTING,
                selection(), 1L, Optional.empty());

        backend.cancel(command);
        ProcessCameraRuntimeBackend.Result result = execute(backend, command);

        assertEquals(ProcessCameraRuntimeBackend.Outcome.CANCELLED, result.outcome());
        assertEquals(0, provider.createCount);
    }

    @Test void displayRotationRefreshDelegatesToCurrentSelectionAndPipeline() {
        FakePipeline pipeline = new FakePipeline();
        FakeProvider provider = new FakeProvider(pipeline);
        FakePreviewOutput preview = new FakePreviewOutput();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, preview, new FakeMedia(), new FakeEvents(), new NoOpLogger());
        CameraRuntimeSelection selection = selection();
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                selection, 1L, Optional.empty()));

        backend.refreshDisplayRotation();

        assertEquals(1, provider.refreshCount);
        assertSame(selection, provider.refreshedSelection);
        assertSame(preview, provider.refreshedPreview);
        assertSame(pipeline, provider.refreshedPipeline);
    }

    @Test void previewExpectationUpdatesBindingAndActivePipeline() {
        FakePipeline pipeline = new FakePipeline();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), new FakeMedia(),
                new FakeEvents(), new NoOpLogger());
        backend.setPreviewExpected(true);
        pipeline.beforeBind = () -> backend.setPreviewExpected(false);

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                selection(), 1L, Optional.empty()));

        assertFalse(pipeline.previewExpectedAtBind);
        backend.setPreviewExpected(true);
        assertTrue(pipeline.previewExpected);
    }

    @Test void previewExpectationCanReenableWhileBinding() {
        FakePipeline pipeline = new FakePipeline();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), new FakeMedia(),
                new FakeEvents(), new NoOpLogger());
        pipeline.beforeBind = () -> {
            backend.setPreviewExpected(false);
            backend.setPreviewExpected(true);
        };

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                selection(), 1L, Optional.empty()));

        assertTrue(pipeline.previewExpectedAtBind);
        assertTrue(pipeline.previewExpected);
    }

    @Test void verifiedTransitionStartsOrientationTrackingBeforeCameraBind() {
        CameraRuntimeSelection selection = selection();
        FakePipeline pipeline = new FakePipeline();
        FakeProvider provider = new FakeProvider(pipeline);
        pipeline.beforeBind = () -> assertTrue(provider.orientationTracking);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(),
                new FakeCapabilityAccess(capabilitySnapshot(
                        selection, VerificationOutcome.VERIFIED_PASS)),
                new FakeEvents(), new NoOpLogger());

        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                selection, 1L, Optional.empty()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.READY, ready.outcome());
        assertEquals(1, provider.startOrientationTrackingCount);
        assertEquals(0, provider.stopOrientationTrackingCount);
    }

    @Test void verifiedTransitionRetainsSinglePipelineForRuntimeCapture() {
        CameraRuntimeSelection selection = selection();
        FakePipeline pipeline = new FakePipeline();
        FakeProvider provider = new FakeProvider(pipeline);
        FakeMedia media = new FakeMedia();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), media,
                new FakeCapabilityAccess(capabilitySnapshot(
                        selection, VerificationOutcome.VERIFIED_PASS)),
                new FakeEvents(), new NoOpLogger());

        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                selection, 1L, Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.READY, ready.outcome());
        assertEquals(1, provider.createCount);
        assertFalse(pipeline.operations.contains(CameraPipelineOperation.RELEASE));
        assertSame(media.recording.outputFile(), pipeline.recordingFile);
        assertEquals(pipeline.recordingBitrateBitsPerSecond(),
                media.preparedRecordingBitrateBitsPerSecond);
    }

    @Test void runtimePassesPreparedRecordingOutputHandle() throws Exception {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        File staged = media.recording.outputFile();
        Files.deleteIfExists(staged.toPath());
        try (DcamRecordingOutput recordingOutput = DcamRecordingOutput.openPlain(staged)) {
            media.recordingOutput = recordingOutput;
            FakeProvider provider = new FakeProvider(pipeline);
            provider.mediaRotationDegrees = 270;
            SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                    provider, new FakePreviewOutput(), media,
                    new FakeEvents(), new NoOpLogger());
            ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                    ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                    selection(), 1L, Optional.empty()));
            backend.requestRecording(RecordingMode.VIDEO);

            execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                    null, 2L, ready.activeBinding()));

            assertSame(recordingOutput, pipeline.recordingOutput);
            assertSame(staged, pipeline.recordingFile);
            assertEquals(270, pipeline.encoderRotationDegrees);
        } finally {
            Files.deleteIfExists(staged.toPath());
        }
    }

    @Test void unverifiedTransitionDeepVerifiesAndRetainsSinglePipeline() {
        CameraRuntimeSelection selection = selection();
        FakePipeline pipeline = new FakePipeline();
        FakeProvider provider = new FakeProvider(pipeline);
        FakeMedia media = new FakeMedia();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), media,
                new FakeCapabilityAccess(capabilitySnapshot(
                        selection, VerificationOutcome.UNKNOWN)),
                new FakeEvents(), new NoOpLogger());
        backend.setPreviewExpected(false);
        pipeline.beforeBind = () -> backend.setPreviewExpected(true);

        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                selection, 1L, Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.READY, ready.outcome());
        assertEquals(1, provider.createCount);
        assertEquals(1, pipeline.operations.stream()
                .filter(CameraPipelineOperation.BIND_SESSION::equals).count());
        assertTrue(pipeline.previewExpectedAtBind);
        assertFalse(pipeline.operations.contains(CameraPipelineOperation.RELEASE));
        assertSame(media.recording.outputFile(), pipeline.recordingFile);
    }

    @Test void failedTargetRetainsSingleRollbackPipelineForRuntimeCapture() {
        CameraRuntimeSelection target = selection();
        CameraRuntimeSelection previous = selection("0",
                image(StandardResolutionLabel.SD, 640, 480));
        FakePipeline targetPipeline = new FakePipeline();
        targetPipeline.bindOutcome = CameraOperationOutcome.GLOBAL_FAILURE;
        FakePipeline rollbackPipeline = new FakePipeline();
        FakeProvider provider = new FakeProvider(targetPipeline, rollbackPipeline);
        FakeMedia media = new FakeMedia();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), media,
                new FakeCapabilityAccess(capabilitySnapshot(target,
                        VerificationOutcome.UNKNOWN, previous)),
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Command initialize = new ProcessCameraRuntimeBackend.Command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, 1L, 1L, 0L,
                Optional.of(target), Optional.of(previous), Optional.empty());

        ProcessCameraRuntimeBackend.Result ready = execute(backend, initialize);
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.ROLLED_BACK_READY, ready.outcome());
        assertEquals(2, provider.createCount);
        assertFalse(rollbackPipeline.operations.contains(CameraPipelineOperation.RELEASE));
        assertSame(media.recording.outputFile(), rollbackPipeline.recordingFile);
    }
    @Test void healthProbeUsesQuietPipelineSnapshot() {
        FakePipeline pipeline = new FakePipeline();
        pipeline.health = new SharedCameraCapturePipeline.HealthSnapshot(
                true, false, true, 90L, 88L, "healthy");
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), new FakeMedia(),
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                selection(), 1L, Optional.empty()));

        ProcessCameraRuntimeBackend.HealthSnapshot health = backend.healthSnapshot(
                ready.activeBinding().orElseThrow()).orElseThrow();

        assertEquals(90L, health.sourceFrameCount());
        assertEquals(88L, health.previewFrameCount());
        assertEquals("healthy", health.detail());
        assertFalse(pipeline.operations.contains(CameraPipelineOperation.PREVIEW_PROGRESS));
    }

    @Test void displayRotationRefreshSerializesWithRelease() throws Exception {
        FakePipeline pipeline = new FakePipeline();
        FakeProvider provider = new FakeProvider(pipeline);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), new FakeEvents(),
                new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        CountDownLatch refreshEntered = new CountDownLatch(1);
        CountDownLatch allowRefresh = new CountDownLatch(1);
        CountDownLatch releaseEntered = new CountDownLatch(1);
        provider.refreshAction = () -> {
            refreshEntered.countDown();
            try {
                allowRefresh.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
        };
        pipeline.beforeRelease = releaseEntered::countDown;
        Throwable[] failures = new Throwable[2];
        Thread refresh = new Thread(() -> {
            try {
                backend.refreshDisplayRotation();
            } catch (Throwable error) {
                failures[0] = error;
            }
        });
        Thread release = new Thread(() -> {
            try {
                execute(backend, command(ProcessCameraRuntimeBackend.Operation.RELEASE,
                        null, 2L, ready.activeBinding()));
            } catch (Throwable error) {
                failures[1] = error;
            }
        });

        refresh.start();
        assertTrue(refreshEntered.await(2, TimeUnit.SECONDS));
        release.start();
        assertFalse(releaseEntered.await(150, TimeUnit.MILLISECONDS));
        allowRefresh.countDown();
        refresh.join(2_000L);
        release.join(2_000L);

        assertFalse(refresh.isAlive());
        assertFalse(release.isAlive());
        assertEquals(null, failures[0]);
        assertEquals(null, failures[1]);
        assertTrue(releaseEntered.await(1, TimeUnit.SECONDS));
    }

    @Test void verificationSessionDetachesBoundPipelineWithoutRelease() {
        FakePipeline pipeline = new FakePipeline();
        CameraRuntimeSelection selection = selection();
        CameraOperationContext context = new CameraOperationContext(selection.cameraId(),
                selection.verificationPipelineId(), selection.codec(), selection.tuple(),
                1L, 0L, CameraOperationDeadline.forCandidate(0L));
        SharedCameraVerificationSession session = new SharedCameraVerificationSession(
                new FakeProvider(pipeline), new NoOpLogger(), () -> 0L);

        assertEquals(CameraOperationOutcome.PASS, session.bindSession(context).outcome());
        SharedCameraCapturePipeline retained = session.detachActiveBinding(context);

        assertSame(pipeline, retained);
        assertTrue(session.activePipeline().isEmpty());
        assertFalse(pipeline.operations.contains(CameraPipelineOperation.RELEASE));
    }

    @Test void verificationReleaseExceptionBecomesRecoveryResult() {
        FakePipeline pipeline = new FakePipeline();
        pipeline.releaseException = new IllegalStateException("release failed");
        CameraRuntimeSelection selection = selection();
        CameraOperationContext context = new CameraOperationContext(selection.cameraId(),
                selection.verificationPipelineId(), selection.codec(), selection.tuple(),
                1L, 0L, CameraOperationDeadline.forCandidate(0L));
        SharedCameraVerificationSession session = new SharedCameraVerificationSession(
                new FakeProvider(pipeline), new NoOpLogger(), () -> 0L);

        assertEquals(com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome.PASS,
                session.bindSession(context).outcome());

        ProcessCameraRuntimeBackend.Result result =
                SharedCameraRuntimeBackend.releaseVerification(session, context);

        assertEquals(ProcessCameraRuntimeBackend.Outcome.RECOVERY_REQUIRED, result.outcome());
        assertEquals("verification_release_exception:IllegalStateException", result.detail());
    }

    @Test void verificationSessionModeSwitchReleasesSameContextBinding() {
        FakePipeline standalone = new FakePipeline();
        FakePipeline combo = new FakePipeline();
        FakeProvider provider = new FakeProvider(standalone, combo);
        CameraRuntimeSelection selection = selection();
        CameraOperationContext context = new CameraOperationContext(selection.cameraId(),
                selection.verificationPipelineId(), selection.codec(), selection.tuple(),
                1L, 0L, CameraOperationDeadline.forCandidate(0L));
        SharedCameraVerificationSession session = new SharedCameraVerificationSession(
                provider, new NoOpLogger(), () -> 0L);

        assertEquals(CameraOperationOutcome.PASS,
                session.bindStandaloneImageSession(context).outcome());
        assertEquals(CameraOperationOutcome.PASS, session.bindSession(context).outcome());

        assertTrue(standalone.operations.contains(CameraPipelineOperation.RELEASE));
        assertTrue(combo.operations.contains(CameraPipelineOperation.BIND_SESSION));
        assertEquals(2, provider.createCount);
    }

    @Test void verificationSessionReleaseFailureBlocksSameContextModeSwitch() {
        FakePipeline standalone = new FakePipeline();
        standalone.releaseOutcome = CameraOperationOutcome.GLOBAL_FAILURE;
        FakePipeline combo = new FakePipeline();
        FakeProvider provider = new FakeProvider(standalone, combo);
        CameraRuntimeSelection selection = selection();
        CameraOperationContext context = new CameraOperationContext(selection.cameraId(),
                selection.verificationPipelineId(), selection.codec(), selection.tuple(),
                1L, 0L, CameraOperationDeadline.forCandidate(0L));
        SharedCameraVerificationSession session = new SharedCameraVerificationSession(
                provider, new NoOpLogger(), () -> 0L);

        assertEquals(CameraOperationOutcome.PASS,
                session.bindStandaloneImageSession(context).outcome());
        CameraOperationResult result = session.bindSession(context);

        assertEquals(CameraOperationOutcome.GLOBAL_FAILURE, result.outcome());
        assertTrue(result.detail().contains("previous_binding_release_failed"));
        assertEquals(1, provider.createCount);
        assertEquals(Optional.of(standalone), session.activePipeline());
    }

    @Test void recordPhotoAndFinalizeUseSameVerifiedBindingAndRequestedFiles() {
        FakePipeline pipeline = new FakePipeline();
        FakeProvider provider = new FakeProvider(pipeline);
        FakeMedia media = new FakeMedia();
        FakeEvents events = new FakeEvents();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), media, events, new NoOpLogger());
        CameraRuntimeSelection selection = selection();

        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection, 1L, Optional.empty()));
        CameraOperationContext binding = ready.activeBinding().orElseThrow();
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, Optional.of(binding)));
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                null, 3L, Optional.of(binding)));
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                null, 4L, Optional.of(binding)));

        assertEquals(List.of(CameraPipelineOperation.BIND_SESSION,
                CameraPipelineOperation.START_ENCODER,
                CameraPipelineOperation.CAPTURE_JPEG,
                CameraPipelineOperation.STOP_ENCODER,
                CameraPipelineOperation.FINALIZE_ENCODER), pipeline.operations);
        assertSame(media.recording.mediaFile().getFile(), pipeline.recordingFile);
        assertSame(media.photo.mediaFile().getFile(), pipeline.photoFile);
        assertEquals(CameraOperationContext.class, pipeline.lastContext.getClass());
        assertTrue(events.started.contains("VIDEO:" + media.recording.mediaFile().getFileName()));
        assertTrue(events.photos.contains(media.photo.mediaFile().getFileName()));
        assertTrue(events.completed.contains(media.recording.mediaFile().getFileName()));
        assertEquals(pipeline.finalizedDurationUs, media.recordingDurationUs);
        assertTrue(pipeline.cleanFinalizationRequested);
        assertEquals(1, provider.createCount);
    }

    @Test void recordingPhotoUsesLiveRotationWithoutChangingRecordingRotation() {
        FakePipeline pipeline = new FakePipeline();
        pipeline.setRotation(90);
        FakeProvider provider = new FakeProvider(pipeline);
        provider.mediaRotationDegrees = 270;
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), new FakeEvents(),
                new NoOpLogger());
        CameraRuntimeSelection selection = selection();
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection, 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                null, 3L, ready.activeBinding()));
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                null, 4L, ready.activeBinding()));

        assertEquals(1, provider.startOrientationTrackingCount);
        assertEquals(0, provider.stopOrientationTrackingCount);
        assertSame(selection, provider.mediaRotationSelection);
        assertEquals(90, provider.mediaRotationFallbackDegrees);
        assertEquals(270, pipeline.jpegRotationDegrees);
        assertEquals(90, pipeline.outputRotationDegrees());
    }

    @Test void releaseStopsMediaOrientationTracking() {
        FakePipeline pipeline = new FakePipeline();
        FakeProvider provider = new FakeProvider(pipeline);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), new FakeEvents(),
                new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.RELEASE,
                null, 3L, ready.activeBinding()));

        assertEquals(1, provider.startOrientationTrackingCount);
        assertEquals(1, provider.stopOrientationTrackingCount);
        assertEquals(provider.estimatedRecordingBitrateBitsPerSecond,
                backend.recordingBitrateBitsPerSecond(selection()));
    }

    @Test void failedPipelineCreationStopsMediaOrientationTracking() {
        FakeProvider provider = new FakeProvider(new FakePipeline());
        provider.createException = new IllegalStateException("create failed");
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), new FakeEvents(),
                new NoOpLogger());

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.TARGET_FAILED, result.outcome());
        assertEquals(1, provider.startOrientationTrackingCount);
        assertEquals(1, provider.stopOrientationTrackingCount);
        assertFalse(provider.orientationTracking);
    }

    @Test void failedPipelineBindStopsMediaOrientationTracking() {
        FakePipeline pipeline = new FakePipeline();
        pipeline.bindOutcome = CameraOperationOutcome.CANDIDATE_SUSPECT;
        FakeProvider provider = new FakeProvider(pipeline);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), new FakeEvents(),
                new NoOpLogger());

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.TARGET_FAILED, result.outcome());
        assertEquals(1, provider.startOrientationTrackingCount);
        assertEquals(1, provider.stopOrientationTrackingCount);
        assertFalse(provider.orientationTracking);
    }

    @Test void standalonePhotoUsesPhysicalRotationWithoutChangingPreviewRotation() {
        FakePipeline pipeline = new FakePipeline();
        pipeline.setRotation(180);
        FakeProvider provider = new FakeProvider(pipeline);
        provider.mediaRotationDegrees = 270;
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), new FakeEvents(),
                new NoOpLogger());
        CameraRuntimeSelection selection = selection();
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection, 1L,
                Optional.empty()));

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                null, 2L, ready.activeBinding()));

        assertEquals(1, provider.startOrientationTrackingCount);
        assertEquals(1, provider.mediaRotationRequestCount);
        assertSame(selection, provider.mediaRotationSelection);
        assertEquals(180, provider.mediaRotationFallbackDegrees);
        assertEquals(270, pipeline.jpegRotationDegrees);
        assertEquals(180, pipeline.outputRotationDegrees());
    }

    @Test void recordingStartUsesPhysicalRotationWithoutChangingPreviewRotation() {
        FakePipeline pipeline = new FakePipeline();
        pipeline.setRotation(90);
        FakeProvider provider = new FakeProvider(pipeline);
        provider.mediaRotationDegrees = 270;
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), new FakeEvents(),
                new NoOpLogger());
        CameraRuntimeSelection selection = selection();
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection, 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));

        assertEquals(1, provider.startOrientationTrackingCount);
        assertEquals(1, provider.mediaRotationRequestCount);
        assertSame(selection, provider.mediaRotationSelection);
        assertEquals(90, provider.mediaRotationFallbackDegrees);
        assertEquals(270, pipeline.encoderRotationDegrees);
        assertEquals(90, pipeline.outputRotationDegrees());
    }

    @Test void encryptedRecordingUsesCleanContainerFinalization() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia(true);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE,
                selection(), 1L, Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                null, 3L, ready.activeBinding()));

        assertTrue(pipeline.cleanFinalizationRequested);
    }
    @Test void recordingRefreshesDeadlineInPipelineWallClockDomain() {
        FakePipeline pipeline = new FakePipeline();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), new FakeMedia(),
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        pipeline.rejectExpiredDeadlines = true;
        backend.requestRecording(RecordingMode.VIDEO);

        ProcessCameraRuntimeBackend.Result started = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.START_RECORDING, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.PASS, started.outcome());
        assertFalse(pipeline.lastContext.deadline().isExpiredAt(System.currentTimeMillis()));
    }

    @Test void verifiedTransitionReleasesCurrentPipelineBeforeBindingNext() {
        FakePipeline first = new FakePipeline();
        FakePipeline second = new FakePipeline();
        second.beforeBind = () -> assertTrue(first.operations.contains(
                CameraPipelineOperation.RELEASE));
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(first, second), new FakePreviewOutput(), new FakeMedia(),
                new FakeEvents(), new NoOpLogger());
        CameraRuntimeSelection initial = selection("0");
        CameraRuntimeSelection next = selection("1");

        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, initial, 1L,
                Optional.empty()));
        ProcessCameraRuntimeBackend.Result switched = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.SWITCH_CAMERA, next, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.READY, switched.outcome());
        assertEquals(List.of(CameraPipelineOperation.BIND_SESSION,
                CameraPipelineOperation.RELEASE), first.operations);
        assertEquals(List.of(CameraPipelineOperation.BIND_SESSION), second.operations);
    }

    @Test void preparingStorageBlocksRecordingUntilFreshPress() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        media.retryablePreparationFailures = 1;
        FakeEvents events = new FakeEvents();
        FakePreparationListener preparation = new FakePreparationListener();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        backend.setRecordingPreparationListener(preparation);
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);

        ProcessCameraRuntimeBackend.Result blocked = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.START_RECORDING, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, blocked.outcome());
        assertEquals(List.of("preparing:SD card is being prepared. Please wait."),
                preparation.events);
        assertEquals(1, events.blockedStarts);
        assertTrue(events.started.isEmpty());
        assertFalse(pipeline.operations.contains(CameraPipelineOperation.START_ENCODER));

        backend.requestRecording(RecordingMode.VIDEO);
        ProcessCameraRuntimeBackend.Result started = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.START_RECORDING, null, 3L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.PASS, started.outcome());
        assertEquals(List.of("preparing:SD card is being prepared. Please wait.", "cleared"),
                preparation.events);
        assertTrue(events.failures.isEmpty());
        assertTrue(events.started.contains("VIDEO:" + media.recording.mediaFile().getFileName()));
        assertTrue(pipeline.operations.contains(CameraPipelineOperation.START_ENCODER));
    }

    @Test void preparingStorageBlocksPhotoUntilFreshPress() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        media.retryablePreparationFailures = 1;
        FakeEvents events = new FakeEvents();
        FakePreparationListener preparation = new FakePreparationListener();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        backend.setRecordingPreparationListener(preparation);
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));

        ProcessCameraRuntimeBackend.Result blocked = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, blocked.outcome());
        assertEquals(List.of("preparing:SD card is being prepared. Please wait."),
                preparation.events);
        assertTrue(events.photos.isEmpty());
        assertFalse(pipeline.operations.contains(CameraPipelineOperation.CAPTURE_JPEG));

        ProcessCameraRuntimeBackend.Result captured = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 3L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.PASS, captured.outcome());
        assertEquals(List.of("preparing:SD card is being prepared. Please wait.", "cleared"),
                preparation.events);
        assertTrue(events.failures.isEmpty());
        assertTrue(events.photos.contains(media.photo.mediaFile().getFileName()));
        assertTrue(pipeline.operations.contains(CameraPipelineOperation.CAPTURE_JPEG));
    }

    @Test void recordingPreparationGateAppliesToVideoAndImp() {
        for (RecordingMode mode : List.of(RecordingMode.VIDEO, RecordingMode.IMP)) {
            FakePipeline pipeline = new FakePipeline();
            FakeMedia media = new FakeMedia();
            media.alwaysRetryPreparation = true;
            FakeEvents events = new FakeEvents();
            FakePreparationListener preparation = new FakePreparationListener();
            SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                    new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                    new NoOpLogger());
            backend.setRecordingPreparationListener(preparation);
            ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                    ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                    Optional.empty()));
            backend.requestRecording(mode);

            ProcessCameraRuntimeBackend.Result blocked = execute(backend, command(
                    ProcessCameraRuntimeBackend.Operation.START_RECORDING, null, 2L,
                    ready.activeBinding()));

            assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, blocked.outcome());
            assertEquals(mode, media.preparedRecordingMode);
            assertEquals(pipeline.recordingBitrateBitsPerSecond(),
                    media.preparedRecordingBitrateBitsPerSecond);
            assertEquals(List.of("preparing:SD card is being prepared. Please wait."),
                    preparation.events);
            assertEquals(1, events.blockedStarts);
            assertTrue(events.failures.isEmpty());
            assertFalse(pipeline.operations.contains(CameraPipelineOperation.START_ENCODER));
        }
    }

    @Test void terminalSdCardStateFailsImmediatelyWithoutCameraRecovery() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        media.storageUnavailable = true;
        FakeEvents events = new FakeEvents();
        FakePreparationListener preparation = new FakePreparationListener();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        backend.setRecordingPreparationListener(preparation);
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);

        ProcessCameraRuntimeBackend.Result unavailable = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.START_RECORDING, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, unavailable.outcome());
        assertEquals(List.of("unavailable:SD card unavailable."), preparation.events);
        assertEquals(1, events.blockedStarts);
        assertTrue(events.failures.isEmpty());
        assertFalse(pipeline.operations.contains(CameraPipelineOperation.START_ENCODER));
    }

    @Test void repeatedPreparingPressesNeverRetryOrBecomeUnavailableAutomatically() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        media.alwaysRetryPreparation = true;
        FakeEvents events = new FakeEvents();
        FakePreparationListener preparation = new FakePreparationListener();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        backend.setRecordingPreparationListener(preparation);
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));

        backend.requestRecording(RecordingMode.VIDEO);
        ProcessCameraRuntimeBackend.Result first = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.START_RECORDING, null, 2L,
                ready.activeBinding()));
        backend.requestRecording(RecordingMode.VIDEO);
        ProcessCameraRuntimeBackend.Result second = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.START_RECORDING, null, 3L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, first.outcome());
        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, second.outcome());
        assertEquals(List.of("preparing:SD card is being prepared. Please wait.",
                "preparing:SD card is being prepared. Please wait."), preparation.events);
        assertEquals(2, events.blockedStarts);
        assertTrue(events.failures.isEmpty());
        assertFalse(pipeline.operations.contains(CameraPipelineOperation.START_ENCODER));
    }

    @Test void cancelledInFlightStartNeverStartsEncoder() {
        FakePipeline pipeline = new FakePipeline();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), new FakeMedia(),
                new FakeEvents(), new NoOpLogger());
        CameraRuntimeSelection selection = selection();
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection, 1L, Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        backend.cancelPendingRecording();

        ProcessCameraRuntimeBackend.Result cancelled = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.START_RECORDING, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.CANCELLED, cancelled.outcome());
        assertFalse(pipeline.operations.contains(CameraPipelineOperation.START_ENCODER));
    }
    @Test void cancellationAfterEncoderStartStopsAndAbortsRecording() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        FakeEvents events = new FakeEvents();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        pipeline.afterStartEncoder = backend::cancelPendingRecording;
        backend.requestRecording(RecordingMode.VIDEO);

        ProcessCameraRuntimeBackend.Result cancelled = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.START_RECORDING, null, 2L,
                ready.activeBinding()));
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                null, 3L, ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.CANCELLED, cancelled.outcome());
        assertEquals(List.of(CameraPipelineOperation.BIND_SESSION,
                CameraPipelineOperation.START_ENCODER, CameraPipelineOperation.STOP_ENCODER,
                CameraPipelineOperation.FINALIZE_ENCODER), pipeline.operations);
        assertEquals(1, media.abortedRecordingCount);
        assertTrue(events.started.isEmpty());
    }

    @Test void readyPhotoUsesJpegStreamWithoutEncoder() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        FakeEvents events = new FakeEvents();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                null, 2L, ready.activeBinding()));

        assertTrue(events.photos.contains(media.photo.mediaFile().getFileName()));
        assertEquals(List.of("saving", "saved:" + media.photo.mediaFile().getFileName()),
                events.photoEvents);
        assertEquals(0, media.photoStoragePreflightCount);
        assertEquals(1, media.photoPreparationCount);
        assertSame(media.photo.outputFile(), pipeline.photoFile);
        assertNull(pipeline.photoOutput);
        assertEquals(List.of(CameraPipelineOperation.BIND_SESSION,
                CameraPipelineOperation.CAPTURE_JPEG), pipeline.operations);
    }

    @Test void encryptedPhotoUsesSegmentedOutputInsteadOfPlainFile(@TempDir Path root)
            throws Exception {
        String password = "123456";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, () -> password, new NoOpLogger());
        DcamMediaFile mediaFile = storage.mediaFile(
                DcamFileType.IMAGE, "0", "operator",
                LocalDateTime.of(2026, 8, 12, 10, 30), true);
        SegmentedAesGcmJpegOutput encryptedOutput =
                output.openSegmentedAesGcmJpegOutput(mediaFile);
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia(
                new SharedCameraMediaLifecycle.PhotoCapture(
                        mediaFile, true, encryptedOutput));
        FakeEvents events = new FakeEvents();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                null, 2L, ready.activeBinding()));

        assertSame(encryptedOutput, pipeline.photoOutput);
        assertNull(pipeline.photoFile);
        assertTrue(events.photos.contains(media.photo.mediaFile().getFileName()));
        assertEquals(List.of(CameraPipelineOperation.BIND_SESSION,
                CameraPipelineOperation.CAPTURE_JPEG), pipeline.operations);
    }

    @Test void recordingLimitRequestsStopAndEmitsStorageCompletion() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        FakeEvents events = new FakeEvents();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        boolean[] stopRequested = { false };
        backend.setRecordingStorageLimit(() -> stopRequested[0] = true);
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));

        assertEquals(media.recording.fileSizeLimitBytes(), pipeline.recordingFileSizeLimitBytes);
        pipeline.recordingLimitListener.onLimitReached();
        assertTrue(stopRequested[0]);
        assertTrue(backend.recordingStorageLimitRequested());

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                null, 3L, ready.activeBinding()));

        assertEquals(List.of(media.recording.mediaFile().getFileName()), events.storageStopped);
        assertTrue(events.completed.isEmpty());
        assertFalse(backend.recordingStorageLimitRequested());
    }

    @Test void storageLimitDuringAsyncFinalizationUsesStorageCompletion() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        media.deferRecordingFinalization = true;
        FakeEvents events = new FakeEvents();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));
        ProcessCameraRuntimeBackend.Result[] stopped = new ProcessCameraRuntimeBackend.Result[1];

        backend.execute(command(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                null, 3L, ready.activeBinding()), value -> stopped[0] = value);
        assertTrue(stopped[0] == null);
        pipeline.reachRecordingLimit();
        media.completeRecordingFinalization();

        assertEquals(ProcessCameraRuntimeBackend.Outcome.PASS, stopped[0].outcome());
        assertEquals(List.of(media.recording.mediaFile().getFileName()), events.storageStopped);
        assertTrue(events.completed.isEmpty());
        assertFalse(backend.recordingStorageLimitRequested());
    }

    @Test void failedAsyncFinalizationClearsStorageLimitRequest() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        media.deferRecordingFinalization = true;
        FakeEvents events = new FakeEvents();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));

        backend.execute(command(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                null, 3L, ready.activeBinding()), value -> {});
        pipeline.reachRecordingLimit();
        media.failRecordingFinalization();

        assertFalse(backend.recordingStorageLimitRequested());
        assertTrue(events.storageStopped.isEmpty());
        assertTrue(events.completed.isEmpty());
        assertTrue(events.failures.stream().anyMatch(value -> value.contains(
                "staged media left Temp; recovery will reconcile final publication")));
    }

    @Test void failedEncoderFinalizeDoesNotEmitCompletedEvent() {
        FakePipeline pipeline = new FakePipeline();
        pipeline.finalizeOutcome = com.dvid.dcam.feature.device.domain.camera
                .CameraOperationOutcome.CANDIDATE_SUSPECT;
        FakeMedia media = new FakeMedia();
        FakeEvents events = new FakeEvents();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection(), 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                null, 3L, ready.activeBinding()));

        assertTrue(events.completed.isEmpty());
        assertTrue(events.failures.stream().anyMatch(value -> value.startsWith("Recording:")));
        assertEquals(1, media.failedRecordingCount);
    }

    @Test void impHandoffStopsCurrentSegmentBeforeStartingSos() {
        FakePipeline pipeline = new FakePipeline();
        FakeMedia media = new FakeMedia();
        FakeEvents events = new FakeEvents();
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(pipeline), new FakePreviewOutput(), media, events,
                new NoOpLogger());
        CameraRuntimeSelection selection = selection();
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.INITIALIZE, selection, 1L, Optional.empty()));
        CameraOperationContext binding = ready.activeBinding().orElseThrow();
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, Optional.of(binding)));
        backend.requestImpHandoff();
        boolean[] handoff = { false };
        backend.setImpHandoff(() -> handoff[0] = true);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.STOP_RECORDING,
                null, 3L, Optional.of(binding)));
        assertEquals(RecordingMode.VIDEO, media.recording.mode());
        assertEquals(List.of(CameraPipelineOperation.BIND_SESSION,
                CameraPipelineOperation.START_ENCODER,
                CameraPipelineOperation.STOP_ENCODER,
                CameraPipelineOperation.FINALIZE_ENCODER), pipeline.operations);
        assertTrue(handoff[0]);
    }

    @Test void recordingPhotoKeepsEffectiveRuntimeImageBinding() {
        CameraRuntimeSelection effective = selection();
        CandidateKey requested = candidate(effective,
                image(StandardResolutionLabel.FHD, 1920, 1080));
        FakePipeline pipeline = new FakePipeline();
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                requested, VerificationOutcome.UNKNOWN);
        FakeMedia media = new FakeMedia();
        FakeProvider provider = new FakeProvider(pipeline);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), media, capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));
        backend.requestRecording(RecordingMode.VIDEO);
        execute(backend, command(ProcessCameraRuntimeBackend.Operation.START_RECORDING,
                null, 2L, ready.activeBinding()));

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 3L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.PASS, result.outcome(), result.detail());
        assertEquals(1, provider.createCount);
        assertTrue(capabilities.recordedOutcomes.isEmpty());
        assertTrue(pipeline.operations.contains(CameraPipelineOperation.CAPTURE_JPEG));
    }
    @Test void standalonePhotoBindsRequestedImageAndKeepsMismatchFile() {
        CameraRuntimeSelection effective = selection();
        CandidateKey requested = candidate(effective,
                image(StandardResolutionLabel.FHD, 1920, 1080));
        FakePipeline initial = new FakePipeline();
        FakePipeline photo = new FakePipeline();
        photo.jpegWidth = 1280;
        photo.jpegHeight = 720;
        FakePipeline restored = new FakePipeline();
        FakeProvider provider = new FakeProvider(initial, photo, restored);
        FakeMedia media = new FakeMedia();
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                requested, VerificationOutcome.UNKNOWN);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), media, capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.PASS, result.outcome(), result.detail());
        assertEquals(List.of(VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                capabilities.recordedOutcomes);
        assertEquals(StandardResolutionLabel.FHD, photo.lastContext.tuple()
                .imageMode().resolution().label());
        assertEquals(3, provider.createCount);
        assertEquals(1, media.photoStoragePreflightCount);
        assertEquals(1, media.photoPreparationCount);
        assertTrue(media.photo.outputFile().isFile());
    }

    @Test void repeatedStandaloneBindRejectionPersistsUnsupported() {
        CameraRuntimeSelection effective = selection();
        CandidateKey requested = candidate(effective,
                image(StandardResolutionLabel.FHD, 1920, 1080));
        FakePipeline firstRejection = new FakePipeline();
        firstRejection.bindOutcome = CameraOperationOutcome.CANDIDATE_SUSPECT;
        FakePipeline confirmation = new FakePipeline();
        confirmation.bindOutcome = CameraOperationOutcome.CANDIDATE_SUSPECT;
        FakeProvider provider = new FakeProvider(
                new FakePipeline(), firstRejection, confirmation, new FakePipeline());
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                requested, VerificationOutcome.UNKNOWN);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, result.outcome());
        assertEquals(List.of(VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                capabilities.recordedOutcomes);
        assertEquals(4, provider.createCount);
    }

    @Test void transientStandaloneBindConfirmationDoesNotPruneImage() {
        CameraRuntimeSelection effective = selection();
        CandidateKey requested = candidate(effective,
                image(StandardResolutionLabel.FHD, 1920, 1080));
        FakePipeline firstRejection = new FakePipeline();
        firstRejection.bindOutcome = CameraOperationOutcome.CANDIDATE_SUSPECT;
        FakePipeline confirmation = new FakePipeline();
        confirmation.bindOutcome = CameraOperationOutcome.TIMEOUT_UNKNOWN;
        FakeProvider provider = new FakeProvider(
                new FakePipeline(), firstRejection, confirmation, new FakePipeline());
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                requested, VerificationOutcome.UNKNOWN);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, result.outcome());
        assertTrue(capabilities.recordedOutcomes.isEmpty());
        assertEquals(4, provider.createCount);
    }

    @Test void repeatedStandaloneCaptureRejectionPersistsUnsupported() {
        CameraRuntimeSelection effective = selection();
        CandidateKey requested = candidate(effective,
                image(StandardResolutionLabel.FHD, 1920, 1080));
        FakePipeline photo = new FakePipeline();
        photo.captureOutcome = CameraOperationOutcome.CANDIDATE_SUSPECT;
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                requested, VerificationOutcome.UNKNOWN);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(new FakePipeline(), photo, new FakePipeline()),
                new FakePreviewOutput(), new FakeMedia(), capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, result.outcome());
        assertEquals(List.of(VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                capabilities.recordedOutcomes);
        assertEquals(2L, photo.operations.stream()
                .filter(operation -> operation == CameraPipelineOperation.CAPTURE_JPEG)
                .count());
    }

    @Test void matchingStandalonePhotoPersistsPassOnlyWhenUnverified() {
        CameraRuntimeSelection effective = selection();
        CandidateKey requested = candidate(effective,
                image(StandardResolutionLabel.FHD, 1920, 1080));
        FakePipeline initial = new FakePipeline();
        FakePipeline photo = new FakePipeline();
        photo.jpegWidth = 1920;
        photo.jpegHeight = 1080;
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                requested, VerificationOutcome.UNKNOWN);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(initial, photo, new FakePipeline()),
                new FakePreviewOutput(), new FakeMedia(), capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.PASS, result.outcome(), result.detail());
        assertEquals(List.of(VerificationOutcome.VERIFIED_PASS),
                capabilities.recordedOutcomes);
    }

    @Test void transposedStandalonePhotoPassesForQuarterTurnOutputRotations() {
        for (int sensorOrientationDegrees : List.of(0, 180)) {
            for (int outputRotationDegrees : List.of(90, 270)) {
                CameraRuntimeSelection effective = selection();
                CandidateKey requested = candidate(effective,
                        image(StandardResolutionLabel.FHD, 1920, 1080));
                FakePipeline photo = new FakePipeline();
                photo.jpegWidth = 1080;
                photo.jpegHeight = 1920;
                photo.setRotation(outputRotationDegrees);
                FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                        requested, VerificationOutcome.UNKNOWN);
                capabilities.sensorOrientationDegrees = sensorOrientationDegrees;
                SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                        new FakeProvider(new FakePipeline(), photo, new FakePipeline()),
                        new FakePreviewOutput(), new FakeMedia(), capabilities,
                        new FakeEvents(), new NoOpLogger());
                ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                        ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                        Optional.empty()));

                ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                        ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                        ready.activeBinding()));

                assertEquals(ProcessCameraRuntimeBackend.Outcome.PASS, result.outcome(),
                        result.detail());
                assertEquals(List.of(VerificationOutcome.VERIFIED_PASS),
                        capabilities.recordedOutcomes);
            }
        }
    }

    @Test void transposedStandalonePhotoFailsForStraightOutputRotations() {
        for (int sensorOrientationDegrees : List.of(90, 270)) {
            for (int outputRotationDegrees : List.of(0, 180)) {
                CameraRuntimeSelection effective = selection();
                CandidateKey requested = candidate(effective,
                        image(StandardResolutionLabel.FHD, 1920, 1080));
                FakePipeline photo = new FakePipeline();
                photo.jpegWidth = 1080;
                photo.jpegHeight = 1920;
                photo.setRotation(outputRotationDegrees);
                FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                        requested, VerificationOutcome.UNKNOWN);
                capabilities.sensorOrientationDegrees = sensorOrientationDegrees;
                SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                        new FakeProvider(new FakePipeline(), photo, new FakePipeline()),
                        new FakePreviewOutput(), new FakeMedia(), capabilities,
                        new FakeEvents(), new NoOpLogger());
                ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                        ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                        Optional.empty()));

                ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                        ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                        ready.activeBinding()));

                assertEquals(ProcessCameraRuntimeBackend.Outcome.PASS, result.outcome(),
                        result.detail());
                assertEquals(List.of(VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                        capabilities.recordedOutcomes);
            }
        }
    }

    @Test void knownStandaloneImageSkipsDimensionEvidenceRewrite() {
        CameraRuntimeSelection effective = selection();
        CandidateKey requested = candidate(effective,
                image(StandardResolutionLabel.FHD, 1920, 1080));
        FakePipeline photo = new FakePipeline();
        photo.jpegWidth = 1280;
        photo.jpegHeight = 720;
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                requested, VerificationOutcome.VERIFIED_PASS);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(new FakePipeline(), photo, new FakePipeline()),
                new FakePreviewOutput(), new FakeMedia(), capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));

        execute(backend, command(ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO,
                null, 2L, ready.activeBinding()));

        assertTrue(capabilities.recordedOutcomes.isEmpty());
    }

    @Test void standalonePhotoBlocksWhenRequestedImageHasNoAvailableCandidate() {
        CameraRuntimeSelection effective = selection();
        FakePipeline initial = new FakePipeline();
        FakeProvider provider = new FakeProvider(initial);
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                null, VerificationOutcome.DEFINITIVE_UNSUPPORTED);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                provider, new FakePreviewOutput(), new FakeMedia(), capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, result.outcome());
        assertEquals("standalone_photo_selection_unavailable", result.detail());
        assertEquals(1, provider.createCount);
        assertFalse(initial.operations.contains(CameraPipelineOperation.CAPTURE_JPEG));
    }

    @Test void transientStandaloneCaptureFailureDoesNotPruneImage() {
        CameraRuntimeSelection effective = selection();
        CandidateKey requested = candidate(effective,
                image(StandardResolutionLabel.FHD, 1920, 1080));
        FakePipeline photo = new FakePipeline();
        photo.captureOutcome = CameraOperationOutcome.TIMEOUT_UNKNOWN;
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                requested, VerificationOutcome.UNKNOWN);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(new FakePipeline(), photo, new FakePipeline()),
                new FakePreviewOutput(), new FakeMedia(), capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, result.outcome());
        assertTrue(capabilities.recordedOutcomes.isEmpty());
    }

    @Test void definitiveStandaloneCaptureFailurePersistsUnsupported() {
        CameraRuntimeSelection effective = selection();
        CandidateKey requested = candidate(effective,
                image(StandardResolutionLabel.FHD, 1920, 1080));
        FakePipeline photo = new FakePipeline();
        photo.captureOutcome = CameraOperationOutcome.DEFINITIVE_CANDIDATE_FAILURE;
        FakeCapabilityAccess capabilities = new FakeCapabilityAccess(
                requested, VerificationOutcome.UNKNOWN);
        SharedCameraRuntimeBackend backend = new SharedCameraRuntimeBackend(
                new FakeProvider(new FakePipeline(), photo, new FakePipeline()),
                new FakePreviewOutput(), new FakeMedia(), capabilities,
                new FakeEvents(), new NoOpLogger());
        ProcessCameraRuntimeBackend.Result ready = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.BIND_COMMITTED, effective, 1L,
                Optional.empty()));

        ProcessCameraRuntimeBackend.Result result = execute(backend, command(
                ProcessCameraRuntimeBackend.Operation.CAPTURE_PHOTO, null, 2L,
                ready.activeBinding()));

        assertEquals(ProcessCameraRuntimeBackend.Outcome.BLOCKED, result.outcome());
        assertEquals(List.of(VerificationOutcome.DEFINITIVE_UNSUPPORTED),
                capabilities.recordedOutcomes);
    }
    private static ProcessCameraRuntimeBackend.Result execute(
            SharedCameraRuntimeBackend backend, ProcessCameraRuntimeBackend.Command command) {
        final ProcessCameraRuntimeBackend.Result[] result = new ProcessCameraRuntimeBackend.Result[1];
        backend.execute(command, value -> result[0] = value);
        if (result[0] == null) throw new AssertionError("backend completion was asynchronous");
        return result[0];
    }

    private static ProcessCameraRuntimeBackend.Command command(
            ProcessCameraRuntimeBackend.Operation operation,
            CameraRuntimeSelection target,
            long sequence,
            Optional<CameraOperationContext> activeBinding) {
        return new ProcessCameraRuntimeBackend.Command(operation, sequence, sequence, 0L,
                Optional.ofNullable(target), Optional.empty(), activeBinding);
    }

    private static CameraRuntimeSelection selection() {
        return selection("0");
    }

    private static CameraRuntimeSelection selection(String cameraId) {
        return selection(cameraId, image(StandardResolutionLabel.HD, 1280, 720));
    }

    private static CameraRuntimeSelection selection(String cameraId, ImageMode image) {
        StandardResolution fhd = new StandardResolution(StandardResolutionLabel.FHD,
                new CameraResolution(1920, 1080));
        return new CameraRuntimeSelection(new CameraId(cameraId),
                new VerificationPipelineId("a-camera2-native-surface-sharing-v1"),
                VideoCodec.H264, new CaptureModeTuple(new VideoMode(fhd, 30), image));
    }

    private static ImageMode image(
            StandardResolutionLabel label, int width, int height) {
        return new ImageMode(new StandardResolution(label, new CameraResolution(width, height)));
    }

    private static CandidateKey candidate(
            CameraRuntimeSelection selection, ImageMode image) {
        return CandidateKey.forTuple(selection.cameraId(), selection.codec(),
                selection.verificationPipelineId(), new CaptureModeTuple(
                        selection.tuple().videoMode(), image));
    }

    private static Snapshot capabilitySnapshot(
            CameraRuntimeSelection target, VerificationOutcome targetOutcome,
            CameraRuntimeSelection previous) {
        CandidateKey targetCandidate = CandidateKey.forTuple(target.cameraId(), target.codec(),
                target.verificationPipelineId(), target.tuple());
        CandidateKey previousCandidate = CandidateKey.forTuple(previous.cameraId(), previous.codec(),
                previous.verificationPipelineId(), previous.tuple());
        List<CandidateEvidence> facts = targetOutcome == VerificationOutcome.UNKNOWN
                ? List.of(new CandidateEvidence(previousCandidate,
                        VerificationOutcome.VERIFIED_PASS))
                : List.of(new CandidateEvidence(targetCandidate, targetOutcome),
                        new CandidateEvidence(previousCandidate,
                                VerificationOutcome.VERIFIED_PASS));
        PipelineEvidence evidence = new PipelineEvidence(target.cameraId(), target.codec(),
                target.verificationPipelineId(), PipelineAvailability.AVAILABLE,
                List.of(targetCandidate, previousCandidate), facts);
        CameraCapabilityStore.CodecSnapshot codec = new CameraCapabilityStore.CodecSnapshot(
                target.codec(), CameraCapabilityStore.CodecState.ACTIVE,
                Optional.empty(), List.of(evidence), Optional.empty());
        CameraCapabilityStore.SelectedRecordingProfile selected =
                new CameraCapabilityStore.SelectedRecordingProfile(previous.codec(),
                        previous.verificationPipelineId(), previous.tuple());
        CameraCapabilityStore.CameraSnapshot camera = new CameraCapabilityStore.CameraSnapshot(
                target.cameraId(), "camera-hardware", List.of(codec),
                Optional.empty(), Optional.of(selected));
        return Snapshot.current("hardware", List.of(target.cameraId()), List.of(camera));
    }
    private static Snapshot capabilitySnapshot(
            CameraRuntimeSelection selection, VerificationOutcome outcome) {
        CandidateKey candidate = CandidateKey.forTuple(selection.cameraId(), selection.codec(),
                selection.verificationPipelineId(), selection.tuple());
        List<CandidateEvidence> facts = outcome == VerificationOutcome.UNKNOWN
                ? List.of() : List.of(new CandidateEvidence(candidate, outcome));
        PipelineEvidence evidence = new PipelineEvidence(selection.cameraId(), selection.codec(),
                selection.verificationPipelineId(), PipelineAvailability.AVAILABLE,
                List.of(candidate), facts);
        CameraCapabilityStore.CodecSnapshot codec = new CameraCapabilityStore.CodecSnapshot(
                selection.codec(), CameraCapabilityStore.CodecState.ACTIVE,
                Optional.empty(), List.of(evidence), Optional.empty());
        CameraCapabilityStore.CameraSnapshot camera = new CameraCapabilityStore.CameraSnapshot(
                selection.cameraId(), "camera-hardware", List.of(codec),
                Optional.empty(), Optional.empty());
        return Snapshot.current("hardware", List.of(selection.cameraId()), List.of(camera));
    }

    private static final class FakeCapabilityAccess
            implements SharedCameraRuntimeBackend.CapabilityAccess {
        private final CandidateKey requested;
        private Snapshot snapshot;
        private VerificationOutcome standaloneOutcome;
        private int sensorOrientationDegrees;
        private final List<VerificationOutcome> recordedOutcomes = new ArrayList<>();

        private FakeCapabilityAccess(
                CandidateKey requested, VerificationOutcome standaloneOutcome) {
            this.requested = requested;
            this.standaloneOutcome = standaloneOutcome;
        }

        private FakeCapabilityAccess(Snapshot snapshot) {
            this.requested = null;
            this.snapshot = snapshot;
            this.standaloneOutcome = VerificationOutcome.UNKNOWN;
        }

        @Override public CameraCapabilityStore capabilityStore() {
            return new CameraCapabilityStore() {
                @Override public LoadResult load(Freshness freshness) {
                    return snapshot == null
                            ? LoadResult.rebuildNeeded(RebuildReason.MISSING)
                            : LoadResult.loaded(snapshot);
                }

                @Override public void requestWrite(Snapshot value) {
                    snapshot = value;
                }
            };
        }

        @Override public Optional<Snapshot> currentSnapshot() {
            return Optional.ofNullable(snapshot);
        }

        @Override public Optional<CandidateKey> requestedCandidate(String cameraId) {
            return Optional.ofNullable(requested);
        }

        @Override public void applyRecordingFallback(
                CandidateKey requested, CandidateKey effective) {}

        @Override public void restoreSelectedRecordingProfile(
                CameraId cameraId, Optional<CameraCapabilityStore.SelectedRecordingProfile> selection) {}

        @Override public VerificationOutcome standaloneImageOutcome(CandidateKey requested) {
            return standaloneOutcome;
        }

        @Override public java.util.OptionalInt sensorOrientationDegrees(CameraId cameraId) {
            return java.util.OptionalInt.of(sensorOrientationDegrees);
        }

        @Override public void recordStandaloneImageOutcome(
                CandidateKey requested, VerificationOutcome outcome) {
            recordedOutcomes.add(outcome);
            standaloneOutcome = outcome;
        }
    }
    private static final class FakeProvider implements SharedCameraPipelineProvider {
        private final FakePipeline[] pipelines;
        private int createCount;
        private int refreshCount;
        private CameraRuntimeSelection refreshedSelection;
        private SharedCameraPreviewOutput refreshedPreview;
        private SharedCameraCapturePipeline refreshedPipeline;
        private Runnable refreshAction = () -> {};
        private RuntimeException createException;
        private int startOrientationTrackingCount;
        private int stopOrientationTrackingCount;
        private boolean orientationTracking;
        private int mediaRotationRequestCount;
        private Integer mediaRotationDegrees;
        private int mediaRotationFallbackDegrees;
        private CameraRuntimeSelection mediaRotationSelection;
        private long estimatedRecordingBitrateBitsPerSecond = 8_000_000L;
        private CameraRuntimeSelection bitrateSelection;

        private FakeProvider(FakePipeline... pipelines) {
            if (pipelines.length == 0) throw new IllegalArgumentException("pipelines required");
            this.pipelines = pipelines;
        }

        @Override public SharedCameraCapturePipeline create(
                CameraRuntimeSelection selection, SharedCameraPreviewOutput previewOutput) {
            int index = Math.min(createCount, pipelines.length - 1);
            createCount++;
            if (createException != null) throw createException;
            return pipelines[index];
        }

        @Override public long recordingBitrateBitsPerSecond(
                CameraRuntimeSelection selection) {
            bitrateSelection = selection;
            return estimatedRecordingBitrateBitsPerSecond;
        }

        @Override public void refreshRotation(
                CameraRuntimeSelection selection,
                SharedCameraPreviewOutput previewSurface,
                SharedCameraCapturePipeline pipeline) {
            refreshCount++;
            refreshedSelection = selection;
            refreshedPreview = previewSurface;
            refreshedPipeline = pipeline;
            refreshAction.run();
        }

        @Override public void startMediaOrientationTracking() {
            orientationTracking = true;
            startOrientationTrackingCount++;
        }

        @Override public void stopMediaOrientationTracking() {
            if (!orientationTracking) return;
            orientationTracking = false;
            stopOrientationTrackingCount++;
        }

        @Override public int mediaRotationDegrees(
                CameraRuntimeSelection selection, int fallbackRotationDegrees) {
            mediaRotationRequestCount++;
            mediaRotationSelection = selection;
            mediaRotationFallbackDegrees = fallbackRotationDegrees;
            return mediaRotationDegrees == null
                    ? fallbackRotationDegrees : mediaRotationDegrees;
        }

        @Override public SharedCameraCapturePipeline createVerification(
                CameraRuntimeSelection selection) {
            int index = Math.min(createCount, pipelines.length - 1);
            createCount++;
            return pipelines[index];
        }
    }

    private static final class FakePipeline implements SharedCameraCapturePipeline {
        private final List<CameraPipelineOperation> operations = new ArrayList<>();
        private CameraOperationContext lastContext;
        private File recordingFile;
        private DcamRecordingOutput recordingOutput;
        private long recordingFileSizeLimitBytes;
        private SharedCameraCapturePipeline.RecordingLimitListener recordingLimitListener;
        private Runnable beforeBind = () -> {};
        private Runnable afterStartEncoder = () -> {};
        private Runnable beforeRelease = () -> {};
        private boolean rejectExpiredDeadlines;
        private RuntimeException releaseException;
        private CameraOperationOutcome releaseOutcome = CameraOperationOutcome.PASS;
        private File photoFile;
        private SegmentedAesGcmJpegOutput photoOutput;
        private CameraOperationOutcome bindOutcome = CameraOperationOutcome.PASS;
        private SharedCameraCapturePipeline.HealthSnapshot health =
                new SharedCameraCapturePipeline.HealthSnapshot(
                        true, false, true, 1L, 1L, "healthy");
        private CameraOperationOutcome captureOutcome = CameraOperationOutcome.PASS;
        private int jpegWidth;
        private int jpegHeight;
        private int outputRotationDegrees;
        private int jpegRotationDegrees = -1;
        private int encoderRotationDegrees = -1;
        private boolean previewExpected = true;
        private boolean previewExpectedAtBind = true;
        private com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome stopOutcome =
                com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome.PASS;
        private com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome finalizeOutcome =
                com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome.PASS;
        private long finalizedDurationUs = 12_345_678L;
        private boolean cleanFinalizationRequested;
        private long recordingBitrateBitsPerSecond = 12_000_000L;

        @Override public long recordingBitrateBitsPerSecond() {
            return recordingBitrateBitsPerSecond;
        }

        @Override public VerificationPipelineId pipelineId() {
            return new VerificationPipelineId("a-camera2-native-surface-sharing-v1");
        }

        @Override public void setPreviewExpected(boolean expected) {
            previewExpected = expected;
        }

        @Override public void setRotation(int rotationDegrees) {
            outputRotationDegrees = CameraOrientation.normalize(rotationDegrees);
        }

        @Override public int outputRotationDegrees() {
            return outputRotationDegrees;
        }

        @Override public CameraOperationResult bindSession(CameraOperationContext context) {
            beforeBind.run();
            previewExpectedAtBind = previewExpected;
            if (bindOutcome == CameraOperationOutcome.PASS) {
                return pass(context, CameraPipelineOperation.BIND_SESSION);
            }
            operations.add(CameraPipelineOperation.BIND_SESSION);
            lastContext = context;
            return new CameraOperationResult(context, CameraPipelineOperation.BIND_SESSION,
                    bindOutcome, 1L, "bind");
        }

        @Override public CameraOperationResult updateSession(CameraOperationContext context) {
            return pass(context, CameraPipelineOperation.UPDATE_SESSION);
        }

        @Override public CameraOperationResult previewProgress(CameraOperationContext context) {
            return pass(context, CameraPipelineOperation.PREVIEW_PROGRESS);
        }

        @Override public SharedCameraCapturePipeline.HealthSnapshot healthSnapshot(
                CameraOperationContext context) {
            lastContext = context;
            return health;
        }

        @Override public CameraOperationResult startEncoder(CameraOperationContext context) {
            return pass(context, CameraPipelineOperation.START_ENCODER);
        }

        @Override public CameraOperationResult startEncoder(
                CameraOperationContext context, File outputFile) {
            recordingFile = outputFile;
            return pass(context, CameraPipelineOperation.START_ENCODER);
        }

        @Override public CameraOperationResult startEncoder(
                CameraOperationContext context, File outputFile, long fileSizeLimitBytes,
                RecordingLimitListener listener) {
            recordingFile = outputFile;
            recordingFileSizeLimitBytes = fileSizeLimitBytes;
            recordingLimitListener = listener;
            CameraOperationResult result = pass(context, CameraPipelineOperation.START_ENCODER);
            afterStartEncoder.run();
            return result;
        }
        @Override public CameraOperationResult startEncoder(
                CameraOperationContext context, File outputFile, long fileSizeLimitBytes,
                RecordingLimitListener listener, int rotationDegrees) {
            encoderRotationDegrees = rotationDegrees;
            return startEncoder(context, outputFile, fileSizeLimitBytes, listener);
        }

        @Override public CameraOperationResult startEncoder(
                CameraOperationContext context, DcamRecordingOutput output,
                long fileSizeLimitBytes, RecordingLimitListener listener) {
            recordingOutput = output;
            recordingFile = output.file();
            recordingFileSizeLimitBytes = fileSizeLimitBytes;
            recordingLimitListener = listener;
            CameraOperationResult result = pass(context, CameraPipelineOperation.START_ENCODER);
            afterStartEncoder.run();
            return result;
        }
        @Override public CameraOperationResult startEncoder(
                CameraOperationContext context, DcamRecordingOutput output,
                long fileSizeLimitBytes, RecordingLimitListener listener,
                int rotationDegrees) {
            encoderRotationDegrees = rotationDegrees;
            return startEncoder(context, output, fileSizeLimitBytes, listener);
        }

        @Override public CameraOperationResult stopEncoder(CameraOperationContext context) {
            operations.add(CameraPipelineOperation.STOP_ENCODER);
            lastContext = context;
            return new CameraOperationResult(context, CameraPipelineOperation.STOP_ENCODER,
                    stopOutcome, 1L, "stop");
        }

        @Override public CameraOperationResult finalizeEncoder(CameraOperationContext context) {
            operations.add(CameraPipelineOperation.FINALIZE_ENCODER);
            lastContext = context;
            return new CameraOperationResult(context, CameraPipelineOperation.FINALIZE_ENCODER,
                    finalizeOutcome, 1L, "finalize");
        }

        @Override public CameraOperationResult finalizeEncoder(
                CameraOperationContext context, boolean cleanStop) {
            cleanFinalizationRequested = cleanStop;
            return finalizeEncoder(context);
        }

        @Override public long finalizedDurationUs() { return finalizedDurationUs; }

        @Override public CameraOperationResult captureJpeg(CameraOperationContext context) {
            return pass(context, CameraPipelineOperation.CAPTURE_JPEG);
        }

        @Override public CameraOperationResult captureJpeg(
                CameraOperationContext context, File outputFile) {
            photoFile = outputFile;
            photoOutput = null;
            if (captureOutcome != CameraOperationOutcome.PASS) {
                operations.add(CameraPipelineOperation.CAPTURE_JPEG);
                lastContext = context;
                return new CameraOperationResult(context, CameraPipelineOperation.CAPTURE_JPEG,
                        captureOutcome, 1L, "capture");
            }
            if (jpegWidth > 0 && jpegHeight > 0) {
                File parent = outputFile.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) {
                    throw new IllegalStateException("failed to create photo directory");
                }
                try {
                    Files.write(outputFile.toPath(), JpegDimensionsTest.jpeg(jpegWidth, jpegHeight));
                } catch (IOException error) {
                    throw new IllegalStateException(error);
                }
            }
            return pass(context, CameraPipelineOperation.CAPTURE_JPEG);
        }

        @Override public CameraOperationResult captureJpeg(
                CameraOperationContext context, File outputFile, int rotationDegrees) {
            jpegRotationDegrees = rotationDegrees;
            return captureJpeg(context, outputFile);
        }

        @Override public CameraOperationResult captureJpeg(
                CameraOperationContext context, SegmentedAesGcmJpegOutput output) {
            return captureJpeg(context, output, outputRotationDegrees);
        }

        @Override public CameraOperationResult captureJpeg(
                CameraOperationContext context, SegmentedAesGcmJpegOutput output,
                int rotationDegrees) {
            photoFile = null;
            photoOutput = output;
            jpegRotationDegrees = rotationDegrees;
            if (captureOutcome != CameraOperationOutcome.PASS) {
                operations.add(CameraPipelineOperation.CAPTURE_JPEG);
                lastContext = context;
                return new CameraOperationResult(context, CameraPipelineOperation.CAPTURE_JPEG,
                        captureOutcome, 1L, "capture");
            }
            return pass(context, CameraPipelineOperation.CAPTURE_JPEG);
        }

        @Override public CameraOperationResult release(CameraOperationContext context) {
            beforeRelease.run();
            if (releaseException != null) throw releaseException;
            if (releaseOutcome == CameraOperationOutcome.PASS) {
                return pass(context, CameraPipelineOperation.RELEASE);
            }
            operations.add(CameraPipelineOperation.RELEASE);
            lastContext = context;
            return new CameraOperationResult(context, CameraPipelineOperation.RELEASE,
                    releaseOutcome, 1L, "release");
        }

        @Override public CameraPipelineDiagnostics diagnostics(CameraOperationContext context) {
            return new CameraPipelineDiagnostics(context, true, true, false, true,
                    30L, 30L, 11L, 2, 2,
                    Optional.of(context.tuple().videoMode().resolution().actual()),
                    Optional.of(context.tuple().imageMode().resolution().actual()), true,
                    Optional.of(new File("build/tmp/verification.mp4").getAbsolutePath()),
                    Optional.of(new File("build/tmp/verification.jpg").getAbsolutePath()),
                    "valid");
        }

        private void reachRecordingLimit() {
            if (recordingLimitListener == null) throw new AssertionError("listener missing");
            recordingLimitListener.onLimitReached();
        }

        private CameraOperationResult pass(
                CameraOperationContext context, CameraPipelineOperation operation) {
            operations.add(operation);
            lastContext = context;
            if (rejectExpiredDeadlines
                    && context.deadline().isExpiredAt(System.currentTimeMillis())) {
                return new CameraOperationResult(context, operation,
                        com.dvid.dcam.feature.device.domain.camera
                                .CameraOperationOutcome.TIMEOUT_UNKNOWN,
                        1L, "operation_deadline_expired");
            }
            return new CameraOperationResult(context, operation,
                    com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome.PASS,
                    1L, operation.name().toLowerCase());
        }
    }

    private static final class FakeMedia implements SharedCameraMediaLifecycle {
        private final RecordingCapture recording;
        private final PhotoCapture photo;
        private int abortedRecordingCount;
        private int failedRecordingCount;
        private int photoStoragePreflightCount;
        private int photoPreparationCount;
        private long recordingDurationUs;
        private boolean deferRecordingFinalization;
        private int retryablePreparationFailures;
        private boolean alwaysRetryPreparation;
        private boolean storageUnavailable;
        private DcamRecordingOutput recordingOutput;
        private RecordingMode preparedRecordingMode;
        private long preparedRecordingBitrateBitsPerSecond;
        private Completion pendingRecordingCompletion;

        private FakeMedia() {
            this(false);
        }

        private FakeMedia(boolean encrypted) {
            this(encrypted, new PhotoCapture(file(DcamFileType.IMAGE, "photo.jpg"), false));
        }

        private FakeMedia(PhotoCapture photo) {
            this(false, photo);
        }

        private FakeMedia(boolean encrypted, PhotoCapture photo) {
            recording = new RecordingCapture(RecordingMode.VIDEO,
                    file(DcamFileType.VIDEO, "video.mp4"), encrypted, 1024L);
            this.photo = photo;
            photo.discard();
        }

        @Override public RecordingCapture prepareRecording(
                RecordingMode mode, long bitrateBitsPerSecond)
                throws PreparationException {
            preparedRecordingMode = mode;
            preparedRecordingBitrateBitsPerSecond = bitrateBitsPerSecond;
            requireStorage();
            if (mode == RecordingMode.VIDEO && recordingOutput == null) return recording;
            return new RecordingCapture(mode, recording.mediaFile(),
                    mode == RecordingMode.VIDEO && recording.encrypted(),
                    recording.fileSizeLimitBytes(), recordingOutput);
        }

        @Override public void requirePhotoStorage() throws PreparationException {
            photoStoragePreflightCount++;
            requireStorage();
        }

        @Override public PhotoCapture preparePhoto() throws PreparationException {
            photoPreparationCount++;
            requireStorage();
            return photo;
        }

        private void requireStorage() throws PreparationException {
            if (storageUnavailable) {
                throw PreparationException.unavailable("Storage", "SD card unavailable.");
            }
            if (alwaysRetryPreparation || retryablePreparationFailures > 0) {
                if (retryablePreparationFailures > 0) retryablePreparationFailures--;
                throw PreparationException.retryable("Storage",
                        "SD card is being prepared. Please wait.", "SD card unavailable.",
                        new IllegalStateException("Operation not permitted"));
            }
        }
        @Override public void abortRecordingStart(RecordingCapture capture) { abortedRecordingCount++; }
        @Override public void failRecording(RecordingCapture capture) { failedRecordingCount++; }
        @Override public void finalizeRecording(
                RecordingCapture capture, long durationUs, Completion completion) {
            recordingDurationUs = durationUs;
            if (deferRecordingFinalization) {
                pendingRecordingCompletion = completion;
                return;
            }
            completion.onSuccess(capture.outputFile());
        }

        private void completeRecordingFinalization() {
            Completion completion = takePendingRecordingCompletion();
            completion.onSuccess(recording.outputFile());
        }

        private void failRecordingFinalization() {
            Completion completion = takePendingRecordingCompletion();
            completion.onFailure(new IllegalStateException("finalize failed"));
        }

        private Completion takePendingRecordingCompletion() {
            Completion completion = pendingRecordingCompletion;
            if (completion == null) throw new AssertionError("recording completion missing");
            pendingRecordingCompletion = null;
            return completion;
        }

        @Override public void finalizePhoto(PhotoCapture capture, Completion completion) {
            completion.onSuccess(capture.outputFile());
        }

        private static DcamMediaFile file(DcamFileType type, String name) {
            return new DcamMediaFile(type, name, new File("build/tmp/" + name),
                    java.time.LocalDateTime.now());
        }
    }

    private static final class FakeEvents implements CaptureEvents {
        private final List<String> started = new ArrayList<>();
        private final List<String> completed = new ArrayList<>();
        private final List<String> photos = new ArrayList<>();
        private final List<String> photoEvents = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private final List<String> storageStopped = new ArrayList<>();
        private int blockedStarts;
        @Override public void setListener(java.util.function.Consumer<CaptureEvent> listener) {}
        @Override public void clearListener() {}
        @Override public RecordingMode currentMode() { return RecordingMode.IDLE; }
        @Override public void recordingStartBlocked() { blockedStarts++; }
        @Override public void recordingStarted(RecordingMode mode, String fileName) {
            started.add(mode + ":" + fileName);
        }
        @Override public void recordingInterrupted(String message) {}
        @Override public void recordingResumed() {}
        @Override public void recordingCompleted(String fileName) { completed.add(fileName); }
        @Override public void recordingStoppedForStorage(String fileName) {
            storageStopped.add(fileName);
        }
        @Override public void audioRecordingStarted(String fileName, long startedAtMillis) {}
        @Override public void audioRecordingStopped(String fileName) {}
        @Override public void photoSaving() { photoEvents.add("saving"); }
        @Override public void photoSaved(String fileName) {
            photos.add(fileName);
            photoEvents.add("saved:" + fileName);
        }
        @Override public void photoFailed(String operation, String message) {}
        @Override public void captureFailed(String operation, String message) {
            failures.add(operation + ":" + message);
        }
    }

    private static final class FakePreparationListener
            implements SharedCameraGatewayBackend.RecordingPreparationListener {
        private final List<String> events = new ArrayList<>();

        @Override public void onPreparing(String message) {
            events.add("preparing:" + message);
        }

        @Override public void onCleared() {
            events.add("cleared");
        }

        @Override public void onUnavailable(String message) {
            events.add("unavailable:" + message);
        }
    }

    private static final class FakePreviewOutput implements SharedCameraPreviewOutput {
        @Override public android.view.Surface surface() { return null; }
        @Override public void resize(CameraResolution resolution) {}
        @Override public void setSensorOrientation(int sensorOrientationDegrees) {}
        @Override public void setDisplayRotation(int displayRotationDegrees) {}
        @Override public void setRotation(int rotationDegrees) {}
        @Override public void setMirrored(boolean mirrored) {}
        @Override public void setDisplayResolution(CameraResolution resolution) {}
        @Override public void start(android.os.Handler handler, Runnable onFrame) {}
        @Override public void stop() {}
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    }
}