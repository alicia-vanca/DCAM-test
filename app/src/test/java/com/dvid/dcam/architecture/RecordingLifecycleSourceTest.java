package com.dvid.dcam.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RecordingLifecycleSourceTest {
    @Test void startupFinalizesInterruptedCaptureWithoutStartingNewCapture() throws IOException {
        String application = source("app/DcamApplication.java");
        String composition = source("app/AppComposition.java");
        String service = source("platform/recording/RecordingForegroundService.java");

        int startupMarker = application.indexOf("markUnfinishedCaptureFinalizing(this)");
        int createComposition = application.indexOf("AppComposition.create(this)");
        assertTrue(startupMarker >= 0 && createComposition > startupMarker);
        int recoverTemp = composition.indexOf(
                "mediaOutputImpl.recoverStaged(this::handleStagedMediaRecovery)");
        int createCamera = composition.indexOf("SharedCameraGatewayFactory.create");
        assertTrue(recoverTemp >= 0 && createCamera > recoverTemp);
        assertFalse(application.contains("startVideo"));
        assertFalse(application.contains("startImp"));
        assertTrue(service.contains("return active ? START_STICKY : START_NOT_STICKY"));
        assertTrue(service.contains("dispatchStateChanged(context)"));
    }

    @Test void queuedForegroundIntentCannotResurrectClearedCapture() throws IOException {
        String service = source("platform/recording/RecordingForegroundService.java");
        int onStart = service.indexOf("@Override public int onStartCommand");
        int nextMethod = service.indexOf("static int restartMode", onStart);
        String body = service.substring(onStart, nextMethod);

        assertTrue(body.contains("stateStore.snapshot()"));
        assertFalse(body.contains("stateStore.startVideo("));
        assertFalse(body.contains("stateStore.startAudio("));
    }

    @Test void sharedMediaLifecyclePreservesForegroundAndFinalizationContracts()
            throws IOException {
        String media = source("platform/camera/shared/AndroidSharedCameraMediaLifecycle.java");

        assertTrue(media.contains("RecordingForegroundService.startVideo("));
        assertTrue(media.contains("mediaOutput.checkRecordingReady(bitrateBitsPerSecond)"));
        assertFalse(media.contains("storageWarningActive"));
        assertTrue(media.contains("RecordingForegroundService.markVideoFinalizing(context)"));
        assertTrue(media.contains("mediaOutput.openSegmentedAesGcmJpegOutput(mediaFile)"));
        assertTrue(media.contains("mediaOutput.finalizeSaved(context, capture.mediaFile(), null"));
        assertTrue(media.contains("RecordingForegroundService.completeVideoFinalization(context)"));
        assertFalse(media.contains("mediaOutput.encryptSaved("));
    }

    @Test void audioStoragePreparationUsesSamePreviewNoticeFlow() throws IOException {
        String composition = source("app/AppComposition.java");
        String audio = source("platform/audio/AndroidAudioRecorderImpl.java");
        String gateway = source("platform/camera/shared/SharedCameraGateway.java");
        String preview = source("platform/camera/shared/SharedCameraPreviewView.java");
        String notice = source("app/ui/FloatingNotice.java");

        assertTrue(audio.contains("externalStoragePreparationException("));
        assertTrue(audio.contains("catch (IOException error)"));
        assertTrue(audio.contains("AudioRecorder.PreparationException.retryable("));
        assertTrue(audio.contains("AudioRecorder.PreparationException.unavailable("));
        assertTrue(composition.contains("new CaptureStorageNoticeMonitor("));
        assertTrue(composition.contains("audioPreparationNotifier")
                && composition.contains(".bind(new AudioPreparationEvents()"));
        assertTrue(composition.contains("FloatingNotice.showPersistent(owner, message)"));
        assertTrue(composition.contains("cameraPreview.showStorageWarning(message)"));
        assertTrue(preview.contains("persistentNotice.accept(text)"));
        assertTrue(preview.contains("clearPersistentNotice.run()"));
        assertTrue(notice.contains("showPersistent(Context context, CharSequence message)"));
        assertTrue(gateway.contains("storagePreparationEvents.onPreparing(message)"));
    }
    @Test void standaloneAudioSupportsAacAndRecoverableM4a() throws IOException {
        String composition = source("app/AppComposition.java");
        String audio = source("platform/audio/AndroidAudioRecorderImpl.java");
        String video = source("platform/camera/shared/SharedAvcEncoder.java");
        String writer = source("platform/storage/DcamAudioM4aWriter.java");
        String output = source("platform/storage/DcamMediaOutputImpl.java");

        assertTrue(composition.contains("locationTracking::latestCoordinate"));
        assertTrue(audio.contains(
                "new DcamAudioM4aWriter(recordingOutput, captureLocation, log)"));
        assertTrue(audio.contains("MediaFormatUtil.createFormatFromMediaFormat(format)"));
        assertTrue(audio.contains("mediaOutput.finalizeOpenAudioM4aNow("));
        assertTrue(audio.contains("AudioFileFormat"));
        assertTrue(audio.contains("AudioCaptureSettings.BIT_RATE_BPS"));
        assertTrue(video.contains("AudioCaptureSettings.BIT_RATE_BPS"));
        assertTrue(audio.contains("outputFileType(recordingFormat)"));
        assertTrue(audio.contains("writeAdtsFrame"));
        assertTrue(audio.contains("startDurabilitySync"));
        assertTrue(audio.contains("recordingDurationUs = durationUs;"));


        assertTrue(writer.contains("new FragmentedMp4Muxer.Builder(outputChannel)"));
        assertTrue(writer.contains("outputChannel.queueGpsRoutePoint("));
        assertTrue(output.contains("DcamFileType.AUDIO_M4A"));
        assertTrue(output.contains("return claimMediaFile(type, cameraId, fileUserId, encrypted);"));
        int cleanPatch = output.indexOf(
                "mp4Finalizer.finalizeCleanTimed(recordingOutput.media(), durationUs)");
        int recoveryPatch = output.indexOf(
                "mp4Finalizer.finalizeInterrupted(recordingOutput.media())", cleanPatch);
        int seal = output.indexOf("recordingOutput.finish();", recoveryPatch);
        assertTrue(cleanPatch >= 0 && recoveryPatch > cleanPatch && seal > recoveryPatch);
    }
    @Test void previewSurfaceAvailabilityOwnsPreviewHealthPolicy() throws IOException {
        String activity = source("app/MainActivity.java");
        String preview = source("platform/camera/shared/SharedCameraPreviewView.java");
        String factory = source("platform/camera/shared/SharedCameraGatewayFactory.java");
        String gateway = source("platform/camera/shared/SharedCameraGateway.java");
        String pipeline = source("platform/camera/shared/AbstractSharedCameraPipeline.java");
        int attach = gateway.indexOf("public synchronized void attachPreview");
        int expectation = gateway.indexOf("synchronized void setPreviewExpected");
        String attachBody = gateway.substring(attach, expectation);

        assertFalse(activity.contains("setPreviewExpected("));
        assertTrue(preview.contains("previewSurfaceAvailable = true;"));
        assertTrue(preview.contains("previewSurfaceAvailable = false;"));
        assertTrue(preview.contains("publishPreviewExpected();"));
        assertTrue(preview.contains(
                "previewSurfaceAvailable && getWindowVisibility() == VISIBLE"));
        assertTrue(factory.contains("gateway.setPreviewExpected(view, expected)"));
        assertTrue(attachBody.contains(
                "runtimeOwner.setPreviewExpected(view.isPreviewExpected());"));
        assertTrue(gateway.contains("if (previewView != view) return;"));
        assertTrue(gateway.contains("runtimeOwner.setPreviewExpected(expected);"));
        assertTrue(pipeline.contains(
                "boolean previewReady = awaitPreviewReady(value);"));
        assertTrue(pipeline.contains(
                "while (previewExpected && hasPreviewEvidence()"));
        assertTrue(pipeline.contains(
                "boolean previewSignalAvailable = !previewExpected || hasPreviewEvidence()"));
    }

    @Test void satelliteCurrentLocationRequestKeepsContinuousRouteListener()
            throws IOException {
        String location = source("platform/location/AndroidLocationSourceImpl.java");
        int request = location.indexOf(
                "@Override public synchronized void requestCurrentLocation");
        int stop = location.indexOf("@Override public synchronized void stop()", request);
        String requestBody = location.substring(request, stop);

        assertTrue(requestBody.contains("activeMode == GpsMode.FUSED"));
        assertTrue(requestBody.contains("fusedSource.requestCurrentLocation(settings)"));
        assertFalse(requestBody.contains("requestSingleUpdate"));
        assertFalse(location.contains("private void requestSingleUpdate"));
    }

    @Test void finalActivityDestroyReleasesSharedOwnerButRecreationOnlyDetachesPreview()
            throws IOException {
        String activity = source("app/MainActivity.java");
        String composition = source("app/AppComposition.java");
        String service = source("platform/recording/RecordingForegroundService.java");
        int redirect = activity.indexOf("if (redirectToHomeTaskIfNeeded()) return;");
        int register = activity.indexOf("activityInstanceCount++;", redirect);
        int destroy = activity.indexOf("protected void onDestroy()");
        int redirectDestroy = activity.indexOf("if (redirectingToHomeTask)", destroy);
        int unregister = activity.indexOf("activityInstanceCount--;", redirectDestroy);
        int releaseRuntime = activity.indexOf("releaseCaptureRuntime();", destroy);
        int finalRelease = activity.indexOf("composition.releaseCameraForActivityFinish();", destroy);
        assertTrue(redirect >= 0 && register > redirect && register < destroy);
        assertTrue(destroy >= 0 && redirectDestroy > destroy && unregister > redirectDestroy
                && releaseRuntime > unregister && finalRelease > releaseRuntime);
        assertTrue(activity.substring(releaseRuntime, finalRelease).contains(
                "isFinishing() && activityInstanceCount == 0"));
        assertTrue(composition.contains(
                "RecordingForegroundService.keepCameraReady(context)"));
        assertTrue(composition.contains(
                "releaseCamera(released -> RecordingForegroundService.releaseCameraReady(context))"));
        assertTrue(service.contains("ACTION_CAMERA_READY"));
        assertTrue(service.contains("ACTION_CAMERA_RELEASED"));
        assertTrue(service.contains("cameraReady || snapshot.hasVideo()"));
        assertTrue(service.contains("FOREGROUND_SERVICE_TYPE_CAMERA"));
        int captureRelease = composition.indexOf("public void release() {");
        int captureEnd = composition.indexOf("}", captureRelease);
        String captureReleaseBody = composition.substring(captureRelease, captureEnd);
        assertTrue(captureReleaseBody.contains("audioPreparationBinding.close();"));
        assertTrue(captureReleaseBody.contains("camera.detachPreview(cameraPreview);"));
        assertFalse(captureReleaseBody.contains("releaseCamera"));
    }

    private static String source(String relative) throws IOException {
        Path root = existingPath(Path.of("app/src/main/java"), Path.of("src/main/java"));
        return Files.readString(root.resolve("com/dvid/dcam").resolve(relative),
                StandardCharsets.UTF_8);
    }

    private static Path existingPath(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate.normalize();
        }
        throw new IllegalStateException("Source root not found: " + List.of(candidates));
    }
}