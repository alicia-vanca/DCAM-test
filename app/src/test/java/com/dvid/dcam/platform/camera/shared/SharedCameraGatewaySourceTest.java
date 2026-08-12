package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SharedCameraGatewaySourceTest {
    @Test void gatewayUsesInjectedProcessOwnerAndHasNoLegacyFallback() throws IOException {
        String gateway = source("SharedCameraGateway.java");
        String factory = source("SharedCameraGatewayFactory.java");

        assertTrue(gateway.contains("private final ProcessCameraRuntimeOwner runtimeOwner;"));
        assertTrue(gateway.contains("runtimeOwner.installBackend(backend);"));
        assertFalse(gateway.contains("new ProcessCameraRuntimeOwner"));
        assertFalse(gateway.contains("CameraXCameraGatewayImpl"));
        assertFalse(factory.contains("CameraXCameraGatewayImpl"));
        assertTrue(factory.contains("ProcessCameraRuntimeOwner runtimeOwner"));
    }

    @Test void previewDetachDoesNotReleaseCameraOrStopRecording() throws IOException {
        String gateway = source("SharedCameraGateway.java");
        int detach = gateway.indexOf("void detachPreview");
        int nextMethod = gateway.indexOf("@Override public void takePhoto", detach);
        String body = gateway.substring(detach, nextMethod);

        assertTrue(body.contains("previewView = null;"));
        assertFalse(body.contains("releaseCamera"));
        assertFalse(body.contains("stopRecording"));
        assertFalse(body.contains("close("));
    }

    @Test void startupPreviewUsesStyledBlackMaskUntilFreshFrame() throws IOException {
        String preview = source("SharedCameraPreviewView.java");
        String gateway = source("SharedCameraGateway.java");
        int surfaceAvailable = preview.indexOf("onSurfaceTextureAvailable");
        int surfaceSizeChanged = preview.indexOf("onSurfaceTextureSizeChanged", surfaceAvailable);
        String availableCallback = preview.substring(surfaceAvailable, surfaceSizeChanged);

        assertFalse(availableCallback.contains("clearMessage()"));
        assertTrue(preview.contains("startupOverlay.setBackgroundColor(Color.BLACK)"));
        assertTrue(preview.contains(
                "startupPanel.setBackgroundResource(R.drawable.bg_setting_card)"));
        assertTrue(preview.contains("startupProgress = new ProgressBar("));
        assertTrue(preview.contains("showPreviewWhenFrameArrives()"));
        assertTrue(preview.contains("startupOverlay.setVisibility(GONE)"));
        assertTrue(gateway.contains(
                "if (startupPreviewReady) view.showPreviewWhenFrameArrives();"));
        assertTrue(gateway.contains(
                "else if (capabilityCheckInProgress && capabilityCheckProgress != null) {"));
        assertTrue(gateway.contains(
                "else if (capabilityCheckInProgress) view.showCheckingCapabilities();"));
        assertFalse(gateway.contains("startupUnavailable"));
        assertTrue(gateway.contains(
                "else if (capabilityCheckFailed) view.showCapabilityCheckFailed();"));
        assertTrue(gateway.contains("else view.showStarting();"));
    }

    @Test void previewGeometryWaitsForFrameFromActivePipeline() throws IOException {
        String preview = source("SharedCameraPreviewView.java");
        String provider = source("SharedCameraPipelineProvider.java");
        String eglPipeline = source("egl/EglFanOutPipeline.java");

        assertTrue(preview.contains(
                "callback = frameCallback == null ? null : layoutChangedCallback;"));
        int activeFrameGate = preview.indexOf("if (!surfaceHandle.onFrame()) return;");
        assertTrue(activeFrameGate >= 0);
        assertTrue(preview.indexOf(
                "updateTextureLayout(getWidth(), getHeight());", activeFrameGate)
                > activeFrameGate);
        assertTrue(provider.contains(
                "eglFactory.create(previewSurface.surface(), previewSurface,"));
        assertTrue(eglPipeline.contains(
                "externalPreviewFrameSignal.start(cameraHandler(),"));
        assertTrue(eglPipeline.contains("if (externalPreviewFrameSignal != null) return;"));
        assertTrue(eglPipeline.contains("externalPreviewFrameSignal.stop()"));
    }

    @Test void previewRestoresCameraBufferAfterTextureViewResize() throws IOException {
        String preview = source("SharedCameraPreviewView.java");
        int changed = preview.indexOf("onSurfaceTextureSizeChanged");
        int destroyed = preview.indexOf("onSurfaceTextureDestroyed", changed);
        String callback = preview.substring(changed, destroyed);

        assertTrue(callback.contains("surfaceHandle.restoreBufferSize()"));
        assertTrue(preview.contains(
                "surfaceTexture.setDefaultBufferSize(resolution.width(), resolution.height())"));
    }

    @Test void previewDefersResizeTransformUntilParentLayoutCompletes() throws IOException {
        String preview = source("SharedCameraPreviewView.java");
        int sizeChanged = preview.indexOf("@Override protected void onSizeChanged");
        int updateMethod = preview.indexOf("private void updateTextureLayout", sizeChanged);
        String callback = preview.substring(sizeChanged, updateMethod);

        assertTrue(callback.contains(
                "post(() -> updateTextureLayout(getWidth(), getHeight()))"));
        assertFalse(callback.contains("updateTextureLayout(width, height);"));
    }
    @Test void previewLayoutRequestDoesNotLoopWhileMeasurementIsPending() throws IOException {
        String preview = source("SharedCameraPreviewView.java");
        int gateStart = preview.indexOf("if (textureLayoutWidth == appliedPreviewWidth");
        int gateEnd = preview.indexOf(") return;", gateStart);
        String gate = preview.substring(gateStart, gateEnd);

        assertFalse(gate.contains("textureViewport.getWidth()"));
        assertFalse(gate.contains("textureViewport.getHeight()"));
        assertFalse(gate.contains("textureView.getWidth()"));
        assertFalse(gate.contains("textureView.getHeight()"));
    }

    @Test void capabilityRecheckUsesPreviewProgressBarWithTupleDetails() throws IOException {
        String preview = source("SharedCameraPreviewView.java");
        String gateway = source("SharedCameraGateway.java");

        assertTrue(preview.contains("progressBarStyleHorizontal"));
        assertTrue(preview.contains("startupProgress.setIndeterminate(false);"));
        assertTrue(preview.contains("camera_capabilities_recheck_progress_detail"));
        assertTrue(gateway.contains("beginCapabilityCheckPreview()"));
        assertTrue(gateway.contains("updateCapabilityCheckProgress("));
        assertTrue(gateway.contains("completeCapabilityCheckPreview(boolean successful)"));
        assertFalse(gateway.contains("showStartupUnavailable"));
        assertFalse(preview.contains("camera_startup_unavailable"));
        assertTrue(gateway.contains("surface=preview_overlay"));
        assertTrue(gateway.contains(
                "if (previewView != null) previewView.showCheckingCapabilities();"));
        assertFalse(gateway.contains("preview_overlay=suppressed"));
        assertFalse(gateway.contains("view.hideStartupOverlay();"));
    }

    @Test void captureProfileValidationUsesOneHumanResultLog() throws IOException {
        String pipeline = source("AbstractSharedCameraPipeline.java");
        String nativePipeline = source(
                "outputsharing/NativeSurfaceSharingPipeline.java");

        assertTrue(pipeline.contains("Capture profile validation "));
        assertTrue(pipeline.contains("? \"succeeded\" : \"failed\""));
        assertFalse(pipeline.contains("log(value, \"eligibility\""));
        assertFalse(nativePipeline.contains("cameraSurfaceClasses="));
        assertFalse(nativePipeline.contains("Camera supports this setup:"));
    }

    @Test void encoderReleaseBreaksAsyncCallbackReferences() throws IOException {
        String encoder = source("SharedAvcEncoder.java");

        String nativeFactory = source(
                "outputsharing/NativeSurfaceSharingPipelineFactory.java");
        String eglFactory = source("egl/EglFanOutPipelineFactory.java");
        assertTrue(encoder.contains("currentCodec.setCallback(null)"));
        assertTrue(encoder.contains("removeCallbacksAndMessages(null)"));
        assertTrue(encoder.contains("REUSABLE_CODECS"));
        assertTrue(encoder.contains("codec.reset()"));
        assertTrue(encoder.contains("codec = null;"));
        assertTrue(encoder.contains("inputSurface = null;"));
        assertTrue(nativeFactory.contains("createBenchmark(int rotationDegrees)"));
        assertTrue(eglFactory.contains("createBenchmark(int rotationDegrees)"));
    }

    @Test void videoRecordingAddsPermissionGatedAacTrack() throws IOException {
        String encoder = source("SharedAvcEncoder.java");
        String pipeline = source("AbstractSharedCameraPipeline.java");

        assertTrue(encoder.contains("MediaFormat.MIMETYPE_AUDIO_AAC"));
        String microphone = source("../../audio/SharedMicrophoneCapture.java");
        String standaloneAudio = source("../../audio/AndroidAudioRecorderImpl.java");
        assertFalse(encoder.contains("new AudioRecord("));
        assertTrue(microphone.contains("new AudioRecord("));
        assertFalse(standaloneAudio.contains("new MediaRecorder("));
        int captureRelease = microphone.indexOf("finished.countDown();");
        assertTrue(captureRelease >= 0
                && captureRelease == microphone.lastIndexOf("finished.countDown();"));
        assertTrue(encoder.contains("microphoneCapture.subscribe()"));
        assertTrue(standaloneAudio.contains("microphoneCapture.subscribe()"));
        int signalEnd = microphone.indexOf("private void signalEnd()");
        int close = microphone.indexOf("@Override public void close()", signalEnd);
        assertTrue(signalEnd >= 0 && close > signalEnd);
        String signalEndBlock = microphone.substring(signalEnd, close);
        assertFalse(signalEndBlock.contains("queue.clear()"));
        assertTrue(signalEndBlock.contains("queue.offer(END)"));
        assertTrue(microphone.contains("queue.poll(100L, TimeUnit.MILLISECONDS)"));
        assertTrue(microphone.contains("closed && queue.isEmpty()"));
        assertTrue(microphone.contains("queue.offer(chunk, SUBSCRIPTION_BACKPRESSURE_WAIT_MILLIS"));
        assertFalse(microphone.contains("shared_microphone_consumer_backpressure"));
        assertTrue(standaloneAudio.contains("int bytes = runningMicrophone.read(input);"));
        assertFalse(standaloneAudio.contains("stopRequested ? -1 : runningMicrophone.read(input)"));
        int pauseStart = encoder.indexOf("public void pause()");
        int discardStart = encoder.indexOf("public void discard()", pauseStart);
        assertTrue(pauseStart >= 0 && discardStart > pauseStart);
        String pause = encoder.substring(pauseStart, discardStart);
        assertTrue(pause.contains("requestAudioStop();"));
        assertFalse(pause.contains("stopAudioCapture();"));
        assertTrue(encoder.contains("audioTrackIndex = muxer.addTrack"));
        assertTrue(pipeline.contains("Manifest.permission.RECORD_AUDIO"));
        assertTrue(pipeline.contains("limitListener::onLimitReached, includeAudio"));
    }
    @Test void failedStandaloneAudioReleasesActiveStagingReservation() throws IOException {
        String audio = source("../../audio/AndroidAudioRecorderImpl.java");
        int startFailure = audio.indexOf("deleteEmptyStagedAudio(failed, empty);");
        int startRelease = audio.indexOf("releaseFailedMediaReservation(failed);", startFailure);
        int stopFailure = audio.indexOf("deleteEmptyStagedAudio(completed, empty);");
        int stopRelease = audio.indexOf(
                "releaseFailedMediaReservation(completed);", stopFailure);
        int finalizeFailure = audio.indexOf(
                "deleteEmptyStagedAudio(completed, empty);", stopFailure + 1);
        int finalizeRelease = audio.indexOf(
                "releaseFailedMediaReservation(completed);", stopRelease + 1);
        int releaseStart = audio.indexOf("@Override public void release()");
        int releaseEnd = audio.indexOf("@Override public long recordingStartedAtMillis()", releaseStart);
        String release = audio.substring(releaseStart, releaseEnd);

        assertTrue(startFailure >= 0 && startRelease > startFailure);
        assertTrue(stopFailure >= 0 && stopRelease > stopFailure);
        assertTrue(finalizeFailure > stopFailure && finalizeRelease > finalizeFailure);
        assertTrue(release.contains("releaseFailedMediaReservation(failed);"));
        assertTrue(audio.contains("mediaOutput.releaseMediaReservation(mediaFile);"));
    }

    @Test void recordingStartOverlapsOnlyFinalPathOpen() throws IOException {
        String encoder = source("SharedAvcEncoder.java");

        int begin = encoder.indexOf("public void begin(File outputFile");
        int audio = encoder.indexOf("if (includeAudio) startAudioAsync();", begin);
        int resume = encoder.indexOf("resumeInput();", audio);
        int open = encoder.indexOf("DcamRecordingOutput.openPlain(outputFile);", resume);
        int attach = encoder.indexOf("muxer = nextMuxer;", open);
        assertTrue(begin >= 0 && audio > begin && resume > audio && open > resume && attach > open);
        assertTrue(encoder.contains("segmentFile = outputFile;"));
        assertTrue(encoder.contains("awaitSegmentOutputReady();"));
        assertFalse(encoder.contains("renameTo("));
        assertFalse(encoder.contains("prewarmVideoFile"));
    }
    @Test void recordingStartUsesPrimedVideoAsyncAudioAndDurableSampleGate()
            throws IOException {
        String composition = source("../../../app/AppComposition.java");
        String pipeline = source("AbstractSharedCameraPipeline.java");
        String encoder = source("SharedAvcEncoder.java");
        String gateway = source("SharedCameraGateway.java");
        String factory = source("SharedCameraGatewayFactory.java");
        String eglPipeline = source("egl/EglFanOutPipeline.java");
        String nativePipeline = source("outputsharing/NativeSurfaceSharingPipeline.java");

        assertTrue(composition.contains(
                "private static final long PRE_RECORD_GOP_DURATION_MILLIS = 0L;"));
        assertTrue(composition.contains("PRE_RECORD_GOP_DURATION_MILLIS, logger"));
        assertTrue(pipeline.contains("preRecordGopDurationMillis);"));
        int primeCall = pipeline.indexOf("if (!standaloneImage) primeVideoEncoder(value);");
        int bound = pipeline.indexOf("sessionBound = true;", primeCall);
        assertTrue(primeCall >= 0 && bound > primeCall);
        assertTrue(pipeline.contains("encoder.requestKeyFrame();"));
        assertTrue(pipeline.contains("encoder.awaitOutputFormat("));
        assertTrue(pipeline.contains("encoder.suspendInput();"));
        assertTrue(encoder.contains("MediaCodec.PARAMETER_KEY_SUSPEND"));
        assertTrue(encoder.contains("requestKeyFrame();")
                && encoder.contains("resumeInput();"));
        int primeMethod = pipeline.indexOf("private void primeVideoEncoder(");
        int updateMethod = pipeline.indexOf("@Override public final CameraOperationResult updateSession", primeMethod);
        assertFalse(pipeline.substring(primeMethod, updateMethod).contains("rollbackTopologyRecording"));
        assertTrue(pipeline.contains("encoder_stopped_input_retained"));
        int begin = pipeline.indexOf("encoder.begin(videoArtifact");
        int videoInput = pipeline.indexOf("startTopologyRecording();", begin);
        int durableVideo = pipeline.indexOf("encoder.awaitFirstSample(", videoInput);
        assertTrue(begin >= 0 && videoInput > begin && durableVideo > videoInput);
        assertTrue(pipeline.contains("encoder.awaitAudioCaptureReady("));
        assertTrue(pipeline.contains("current.observeVideoFrameTimestamp("));
        assertTrue(eglPipeline.contains("signalSourceFrame(timestampNanos);"));
        assertTrue(nativePipeline.contains("else signalSourceFrame(timestamp);"));
        assertTrue(encoder.contains("encoder.primeAudioCodec();"));
        assertTrue(encoder.contains("openPrimedAudioCodec()"));
        assertTrue(encoder.contains("primedAudioOutputFormat"));
        assertTrue(encoder.contains("Microphone remains closed until recording."));
        assertFalse(encoder.contains("segmentStartTimeUs"));
        assertFalse(encoder.contains("segmentRelativePresentationTimeUs("));
        assertTrue(encoder.contains("videoTimelineOriginUs = presentationTimeUs;"));
        assertTrue(encoder.contains("segmentVideoCutoffUs("));
        assertTrue(encoder.contains("sampleAfterCutoff("));
        assertTrue(encoder.contains("videoFrameSystemTimeUs("));
        assertTrue(encoder.contains(
                "SystemClock.elapsedRealtimeNanos() / 1_000L"));
        assertFalse(encoder.contains(
                "recordingSystemTimeOriginUs = System.nanoTime() / 1_000L"));
        assertTrue(encoder.contains(
                "long captureStartedSystemTimeUs ="));
        assertTrue(encoder.contains(
                "captureStartedSystemTimeUs - recordingOriginSystemTimeUs"));
        assertTrue(encoder.contains("audioAlignmentFrames("));
        assertTrue(encoder.contains(
                "sourceInfo.size, sourceInfo.presentationTimeUs"));
        assertTrue(encoder.contains("new Thread(this::prepareAndRunAudio"));
        assertTrue(encoder.contains("if (includeAudio) startAudioAsync();"));
        assertTrue(encoder.contains("bufferPendingVideoSampleLocked("));
        assertTrue(encoder.contains("return flushPendingVideoGopLocked();"));
        int videoWrite = encoder.indexOf("muxer.writeSampleData(trackIndex");
        int durableSignal = encoder.indexOf("firstSample.countDown();", videoWrite);
        assertTrue(videoWrite >= 0 && durableSignal > videoWrite);
        assertTrue(pipeline.contains("encoderFailure != null"));
        assertTrue(pipeline.contains("encoder_finalize_failed:"));
        assertTrue(pipeline.contains(
                "Pre-record requires a disk-backed GOP store; only 0 ms is supported"));
        assertTrue(gateway.contains("preview=detached cameraLifetime=retained"));
    }

    @Test void sensorAndDisplayOrientationOwnPreviewAndMediaMetadata() throws IOException {
        String factory = source("SharedCameraGatewayFactory.java");
        String provider = source("SharedCameraPipelineProvider.java");
        String preview = source("SharedCameraPreviewView.java");
        String encoder = source("SharedAvcEncoder.java");
        String pipeline = source("AbstractSharedCameraPipeline.java");
        String fanOut = source("egl/EglFrameFanOut.java");
        String activity = source("../../../app/MainActivity.java");
        String capabilityService = source(
                "../../device/capability/CameraCapabilityService.java");

        assertTrue(factory.contains("capabilities::cameraOrientationDegrees"));
        assertTrue(factory.contains("() -> displayRotationDegrees(context)"));
        assertTrue(factory.contains("CameraCharacteristics.LENS_FACING_FRONT"));
        assertTrue(provider.contains("cameraOrientationDegrees.applyAsInt(cameraId)"));
        assertTrue(provider.contains(
                "CameraOrientation.relativeRotation(sensor, display, front)"));
        assertTrue(provider.contains("previewSurface.setSensorOrientation("));
        assertTrue(provider.contains("previewSurface.setDisplayRotation("));
        assertTrue(provider.contains("pipeline.setRotation(orientation.outputRotationDegrees())"));
        assertTrue(provider.contains("boolean previewMirrorCompensation = front;"));
        assertTrue(provider.contains(
                "previewSurface.setMirrored(orientation.previewMirrorCompensation())"));
        assertFalse(provider.contains("previewSurface.setMirrored(false)"));
        assertTrue(provider.contains("selection.tuple().videoMode().resolution().actual()"));
        assertFalse(provider.contains("selection.tuple().imageMode().resolution().actual()"));
        assertFalse(provider.contains("CameraManager"));
        assertFalse(provider.contains("capability_xml"));
        assertTrue(preview.contains("PreviewSizeCalculator.containLayout("));
        assertTrue(preview.contains("textureViewport.setClipChildren(true)"));
        assertFalse(preview.contains("PreviewSizeCalculator.centerCrop("));
        assertFalse(preview.contains("target=video_viewport"));
        assertTrue(preview.contains("-1f, 1f"));
        assertTrue(preview.contains("1f, -1f"));
        assertTrue(preview.contains("textureView.setRotation(previewRotation)"));
        assertTrue(preview.contains("stage=transform_validation outcome=failed"));
        assertTrue(preview.contains("copySurfaceTextureTransform"));
        assertTrue(pipeline.contains("private volatile int rotationDegrees"));
        int eglTimestampRead = fanOut.indexOf("long timestamp = sourceTexture.getTimestamp();");
        int eglTimestampCallback = fanOut.indexOf("listener.onSourceFrame(timestamp);", eglTimestampRead);
        assertTrue(eglTimestampRead >= 0 && eglTimestampCallback > eglTimestampRead);
        assertFalse(fanOut.contains("listener.onSourceFrame(SystemClock.elapsedRealtimeNanos())"));
        assertTrue(pipeline.contains("CaptureRequest.JPEG_ORIENTATION, jpegRotation"));
        assertTrue(fanOut.contains("EGL14.eglQuerySurface("));
        assertTrue(fanOut.contains("GLES20.glViewport(0, 0, previewWidth, previewHeight)"));
        assertFalse(fanOut.contains("EXTERNAL_PREVIEW_TEX_COORDS"));
        assertTrue(fanOut.contains("externalPreviewTextureCoordinates =")
                && fanOut.contains("floatBuffer(TEX_COORDS);"));
        assertTrue(encoder.contains("MediaFormat.KEY_ROTATION"));
        assertTrue(encoder.contains("Mp4OrientationData"));
        assertTrue(encoder.contains("muxer.addMetadataEntry"));
        assertTrue(encoder.contains("public boolean setRotation(int rotationDegrees)"));
        assertTrue(activity.contains("onConfigurationChanged(Configuration newConfig)"));
        assertTrue(activity.contains("DisplayManager.DisplayListener"));
        assertTrue(activity.contains("registerDisplayRotationListener()"));
        assertTrue(activity.contains("runtime.refreshDisplayRotation()"));
        assertTrue(preview.contains("clearLayoutChangedCallback"));
        int orientationStart = capabilityService.indexOf(
                "public int cameraOrientationDegrees(String cameraId)");
        int orientationEnd = capabilityService.indexOf(
                "public synchronized Optional<Summary>", orientationStart);
        String orientationBody = capabilityService.substring(orientationStart, orientationEnd);
        assertFalse(orientationBody.contains("return 0;"));
        assertTrue(orientationBody.contains("camera sensor orientation unavailable"));
    }
    @Test void healthyRotationDeduplicatesRefreshAndSuppressesIntermediateLogs()
            throws IOException {
        String activity = source("../../../app/MainActivity.java");
        String provider = source("SharedCameraPipelineProvider.java");
        String pipeline = source("AbstractSharedCameraPipeline.java");
        String backend = source("SharedCameraRuntimeBackend.java");
        String preview = source("SharedCameraPreviewView.java");

        assertTrue(activity.contains("refreshDisplayRotationIfNeeded(display);"));
        assertTrue(activity.contains("refreshDisplayRotationIfNeeded(getDisplay());"));
        int refreshGuard = activity.indexOf(
                "if (runtime == null || display == null");
        int autoRotateGuard = activity.indexOf(
                "|| !androidRuntime.isAutoRotateEnabled()", refreshGuard);
        int displayRead = activity.indexOf(
                "int rotation = display.getRotation();", autoRotateGuard);
        assertTrue(refreshGuard >= 0 && autoRotateGuard > refreshGuard
                && displayRead > autoRotateGuard);
        assertFalse(activity.contains("OrientationEventListener"));
        assertFalse(activity.contains("lockedPreviewCompensation"));
        String refreshCall = "runtime.refreshDisplayRotation();";
        assertTrue(activity.indexOf(refreshCall) == activity.lastIndexOf(refreshCall));
        assertFalse(activity.contains("? \"mismatch\" : \"aligned\""));
        assertFalse(activity.contains("AUDIO_UI_TRACE mismatch"));
        assertFalse(provider.contains("stage=lens_facing"));
        assertFalse(provider.contains("outcome=resolved"));
        assertTrue(provider.contains("stage=orientation outcome=applied"));
        int rotationStart = pipeline.indexOf("@Override public final void setRotation");
        int rotationEnd = pipeline.indexOf(
                "@Override public final int outputRotationDegrees", rotationStart);
        String rotationBody = pipeline.substring(rotationStart, rotationEnd);
        assertTrue(rotationBody.contains("if (previous == normalized) return;"));
        assertFalse(rotationBody.contains("logger.info"));
        assertFalse(backend.contains("stage=orientation_refresh"));
        assertFalse(preview.contains("stage=buffer_size"));
        assertFalse(preview.contains("stage=layout outcome=capture_framing"));
        assertFalse(preview.contains("stage=transform_diagnostics"));
        assertTrue(preview.contains("stage=transform_validation outcome=failed"));
    }
    @Test void cameraNoticesUseInjectedFloatingNoticeSinks() throws IOException {
        String preview = source("SharedCameraPreviewView.java");
        String factory = source("SharedCameraGatewayFactory.java");

        assertTrue(preview.contains("Consumer<CharSequence> transientNotice"));
        assertTrue(preview.contains("Consumer<CharSequence> persistentNotice"));
        assertTrue(preview.contains("transientNotice.accept(message)"));
        assertTrue(preview.contains("persistentNotice.accept(text)"));
        assertTrue(preview.contains("clearPersistentNotice.run()"));
        assertFalse(preview.contains("Color.RED"));
        assertTrue(factory.contains("Consumer<CharSequence> transientNotice"));
        assertTrue(factory.contains("Consumer<CharSequence> persistentNotice"));
    }

    @Test void productionPipelinesAcceptDcamStagingFilesExplicitly() throws IOException {
        String contract = source("SharedCameraCapturePipeline.java");
        String lifecycle = source("AbstractSharedCameraPipeline.java");
        String nativePipeline = source("outputsharing/NativeSurfaceSharingPipeline.java");
        String eglPipeline = source("egl/EglFanOutPipeline.java");

        assertTrue(contract.contains("startEncoder(CameraOperationContext context, File outputFile)"));
        assertTrue(contract.contains("long fileSizeLimitBytes"));
        assertTrue(contract.contains("RecordingLimitListener listener"));
        assertTrue(contract.contains("captureJpeg(CameraOperationContext context, File outputFile)"));
        assertTrue(lifecycle.contains("CameraDevice.TEMPLATE_STILL_CAPTURE"));
        assertFalse(lifecycle.contains("jpeg_requires_active_encoder"));
        assertTrue(lifecycle.contains("Objects.requireNonNull(outputFile, \"outputFile\")"));
        assertTrue(nativePipeline.contains("extends AbstractSharedCameraPipeline"));
        assertTrue(eglPipeline.contains("extends AbstractSharedCameraPipeline"));
    }
    @Test void normalStopUsesEncoderStateWithoutFullMp4SampleTraversal() throws IOException {
        String support = source("SharedCameraPipelineSupport.java");
        int cleanInspection = support.indexOf(
                "public static VideoInspection inspectCleanVideo(");
        int cleanInspectionEnd = support.indexOf(
                "private static VideoInspection inspectVideo(", cleanInspection);

        assertTrue(cleanInspection >= 0 && cleanInspectionEnd > cleanInspection);
        assertFalse(support.substring(cleanInspection, cleanInspectionEnd)
                .contains("MediaExtractor"));
        assertFalse(support.substring(cleanInspection, cleanInspectionEnd)
                .contains("extractor.advance()"));
        assertTrue(support.substring(cleanInspectionEnd).contains("extractor.advance()"));
        String lifecycle = source("AbstractSharedCameraPipeline.java");
        assertTrue(lifecycle.contains("inspectCleanVideo("));
        assertTrue(lifecycle.contains("segment.file(), segment.sampleCount(),"));
        assertTrue(lifecycle.contains("encoder.encodedVideoResolution()"));
        assertTrue(lifecycle.contains(
                "if (cleanStop) retainVideoArtifactOnRelease = segment.file() != null;"));
        assertTrue(lifecycle.contains("else deleteQuietly(segment.file());"));
        assertTrue(lifecycle.contains("finalizedDurationUs = segment.durationUs();"));
        assertFalse(lifecycle.contains("duration_unavailable"));
    }
    @Test void failedEncoderFinalizationPreservesStagingDuringRecoveryRelease()
            throws IOException {
        String pipeline = source("AbstractSharedCameraPipeline.java");
        assertTrue(pipeline.contains(
                "retainVideoArtifactOnRelease = segment.file() != null;"));
        assertTrue(pipeline.contains(
                "boolean videoRetained = retainVideoArtifactOnRelease && videoArtifact != null;"));
        assertTrue(pipeline.contains(
                "boolean videoDeleted = videoRetained || deleteArtifact(videoArtifact);"));
        assertTrue(pipeline.contains(
                "Preserve failed recording staging artifact for recovery:"));
        assertTrue(pipeline.contains("retainVideoArtifactOnRelease = false;"));
    }

    @Test void terminalStopDoesNotDrainAudioTwiceAndKeepsEos() throws IOException {
        String encoder = source("SharedAvcEncoder.java");
        assertTrue(encoder.contains("boolean audioStopped = stopAudioCapture();"));
        assertTrue(encoder.contains("audioCaptureStopped = false;"));
        assertTrue(encoder.contains("MediaCodec.BUFFER_FLAG_END_OF_STREAM"));
        assertTrue(encoder.contains("audioStopTargetFrames = audioFrameCountForDurationUs"));
        assertTrue(encoder.contains("if (audioCodec == runningCodec)"));
        assertTrue(encoder.contains("audioMuxerWritesFinished.countDown();"));
        assertTrue(encoder.contains("if (await(muxerWritesFinished, timeoutMillis)) return true;"));
        assertTrue(encoder.contains("stopAudioCaptureAndAwaitCleanup(timeoutMillis)"));
        assertTrue(encoder.indexOf("audioMuxerWritesFinished.countDown();")
                < encoder.indexOf("releaseAudio(finishedCodec, null);"));
        assertTrue(encoder.contains("releaseAudio(finishedCodec, null);"));
        assertFalse(encoder.contains(
                "if (!audioStopRequested) audioFailure(\"audio_encoder\", error);"));
        int releaseAudio = encoder.indexOf("private static void releaseAudio(");
        int releaseAudioEnd = encoder.indexOf("    static long segmentVideoCutoffUs", releaseAudio);
        assertTrue(releaseAudio >= 0 && releaseAudioEnd > releaseAudio);
        String releaseAudioBlock = encoder.substring(releaseAudio, releaseAudioEnd);
        assertFalse(releaseAudioBlock.contains("codec.stop()"));
        assertTrue(releaseAudioBlock.contains("codec.release()"));
    }

    @Test void recordingBuffersAacPreparationBurstAndKeepsFinalSyncInStorage() throws IOException {
        String encoder = source("SharedAvcEncoder.java");
        String finalizer = source("../../storage/DcamInterruptedMp4Finalizer.java");
        String layout = source("../../storage/DcamFragmentedMp4Layout.java");
        String mediaOutput = source("../../storage/DcamMediaOutputImpl.java");
        assertTrue(encoder.contains("MIN_PENDING_VIDEO_GOP_BYTES = 8 * 1024 * 1024"));
        assertTrue(encoder.contains("DcamFragmentedMp4Layout.SEEK_INDEX_RESERVE_BYTES"));
        assertFalse(encoder.contains(
                "DcamFragmentedMp4Layout.reserveSeekIndex(output.getChannel())"));
        assertTrue(encoder.contains(
                "DcamFragmentedMp4Layout.offsetAwareChannel(output)"));
        assertTrue(encoder.contains("output.reserveSeekIndex();"));
        assertTrue(encoder.contains("reserveSeekIndexLocked();"));
        assertTrue(encoder.contains("fileBytesForNextSampleLocked()"));
        assertFalse(encoder.contains("durabilityCheckpoint"));
        assertFalse(encoder.contains("output.getChannel().force(false)"));
        int videoEndOfStream = encoder.indexOf("writeVideoEndOfStream(");
        int muxerClose = encoder.indexOf("current.close()", videoEndOfStream);
        assertTrue(videoEndOfStream >= 0 && muxerClose > videoEndOfStream);
        assertTrue(encoder.contains("MediaCodec.BUFFER_FLAG_END_OF_STREAM"));

        assertTrue(layout.contains("SEEK_INDEX_RESERVE_BYTES = 1024 * 1024"));
        assertTrue(layout.contains("SEEK_INDEX_MAGIC = 0x4443414d53494458L"));
        assertTrue(finalizer.contains("private static final int BOX_SIDX"));
        assertTrue(finalizer.contains("patchSeekIndex(media, media.length(), false);"));
        assertTrue(finalizer.contains("patchSeekIndex(media, retainedBytes, true);"));
        assertTrue(finalizer.contains("if (repairFragmentOffsets)"));
        assertTrue(layout.contains("private static List<RawTrackTiming> patchFragmentBaseOffsets"));
        assertTrue(layout.contains("writeRecordedSeekIndex()"));
        assertTrue(finalizer.contains("readRecordedSeekIndex("));
        assertTrue(mediaOutput.contains("Seek index source: "));
        assertTrue(finalizer.contains("media.force(false);"));
    }

    @Test void mediaLifecyclePreservesStorageIdentityEncryptionAndForegroundContracts()
            throws IOException {
        String media = source("AndroidSharedCameraMediaLifecycle.java");
        assertTrue(media.contains("mediaOutput.checkCaptureReady()"));
        assertTrue(media.contains("mediaOutput.prepareVideoFile(mediaFile)"));
        assertTrue(media.contains("mediaOutput.openVideoOutput(mediaFile)"));
        assertTrue(media.contains("mediaOutput.isExternalStorageRequested()"));
        assertFalse(media.contains("mediaOutput.isExternalCaptureStorage()"));
        assertTrue(media.contains("mediaOutput.recordingFileSizeLimit()"));
        assertTrue(media.contains("mediaOutput.prepareImageFile(mediaFile)"));
        assertTrue(media.contains("mediaEncryptionPassword"));
        assertFalse(media.contains("mediaOutput.finalizeSaved(context, capture.mediaFile(),"));
        assertTrue(media.contains("mediaOutput.finalizeCleanVideo(context, capture.mediaFile(),"));
        assertTrue(media.contains("capture.recordingOutput(), durationUs, callback"));
        assertTrue(media.contains("RecordingForegroundService.updateVideoMode(context, capture.mode())"));
        assertTrue(media.contains("RecordingForegroundService.markVideoFinalizing(context)"));
        assertTrue(media.contains("RecordingForegroundService.completeVideoFinalization(context)"));
    }

    @Test void recordingPreparationWarningOutranksLowStorageWarning() throws IOException {
        String preview = source("SharedCameraPreviewView.java");
        String composition = source("../../../app/AppComposition.java");

        assertTrue(preview.contains("private String storageWarning;"));
        assertTrue(preview.contains("private String lowStorageWarning;"));
        assertTrue(preview.contains("public void showLowStorageWarning(String text)"));
        assertTrue(preview.contains("if (storageWarning != null)"));
        assertTrue(composition.contains("cameraPreview.showLowStorageWarning(message)"));
        assertTrue(composition.contains("cameraPreview.clearLowStorageWarning()"));
    }
    private static String source(String relative) throws IOException {
        Path root = existingPath(Path.of("app/src/main/java"), Path.of("src/main/java"));
        return Files.readString(root.resolve("com/dvid/dcam/platform/camera/shared")
                .resolve(relative), StandardCharsets.UTF_8);
    }

    private static Path existingPath(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate.normalize();
        }
        throw new IllegalStateException("Source root not found: " + List.of(candidates));
    }
}