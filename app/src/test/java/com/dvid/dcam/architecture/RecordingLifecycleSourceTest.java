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
        assertTrue(media.contains("RecordingForegroundService.markVideoFinalizing(context)"));
        assertTrue(media.contains("mediaOutput.finalizeSaved(context, mediaFile, password"));
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
        assertTrue(composition.contains("audioPreparationNotifier.bind("));
        assertTrue(composition.contains("FloatingNotice.showPersistent(owner, message)"));
        assertTrue(composition.contains("cameraPreview.showStorageWarning(message)"));
        assertTrue(preview.contains("persistentNotice.accept(text)"));
        assertTrue(preview.contains("clearPersistentNotice.run()"));
        assertTrue(notice.contains("showPersistent(Context context, CharSequence message)"));
        assertTrue(gateway.contains("storagePreparationEvents.onPreparing(message)"));
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