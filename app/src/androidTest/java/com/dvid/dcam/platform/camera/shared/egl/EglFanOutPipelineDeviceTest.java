package com.dvid.dcam.platform.camera.shared.egl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static com.dvid.dcam.platform.camera.shared.SharedCameraDeviceAssertions.audioSampleCount;

import android.Manifest;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Range;
import android.util.Size;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.platform.camera.shared.JpegDimensions;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativePreviewFrameSignal;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationDeadline;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationOutcome;
import com.dvid.dcam.feature.device.domain.camera.CameraPipelineDiagnostics;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class EglFanOutPipelineDeviceTest {
    @Test public void recordsAndCapturesExactTupleWithoutCameraRebind() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantPermissions(context);
        Selection selection = select(context);
        Assume.assumeTrue("No H.264 Camera2 tuple available", selection != null);
        File outputDirectory = new File(context.getCacheDir(), "egl-fanout-runtime-test");
        EglFanOutPipeline pipeline = new EglFanOutPipelineFactory(
                context, new NoOpLogger(), outputDirectory, () -> null).createHeadless(0);
        CameraOperationContext operation = operation(selection);
        String videoPath = null;
        String jpegPath = null;
        try {
            CameraOperationOutcome bind = pipeline.bindSession(operation).outcome();
            Assume.assumeTrue("EGL fan-out unavailable: " + bind,
                    bind != CameraOperationOutcome.BLOCKED_EXTERNAL);
            assertEquals(CameraOperationOutcome.PASS, bind);
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.previewProgress(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.startEncoder(operation).outcome());
            SystemClock.sleep(500);
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.captureJpeg(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.stopEncoder(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.finalizeEncoder(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.startEncoder(operation).outcome());
            SystemClock.sleep(300);
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.captureJpeg(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.stopEncoder(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.finalizeEncoder(operation).outcome());
            CameraPipelineDiagnostics diagnostics = pipeline.diagnostics(operation);
            assertEquals(2, diagnostics.cameraOutputCount());
            assertEquals(2, diagnostics.downstreamSurfaceCount());
            assertTrue(diagnostics.sourceFrameCount() > 0);
            assertTrue(diagnostics.previewFrameCount() > 0);
            assertTrue(diagnostics.encodedSampleCount() > 0);
            assertTrue(diagnostics.jpegCapturedWhileEncoderActive());
            assertEquals(1, pipeline.cameraOpenCount());
            assertEquals(1, pipeline.sessionCreateCount());
            System.out.println("CAM_IMP_10_EVIDENCE cameraId=" + selection.cameraId
                    + " video=" + selection.videoSize + " fps=" + selection.fps
                    + " jpeg=" + selection.imageSize
                    + " cameraOutputs=" + diagnostics.cameraOutputCount()
                    + " downstreamSurfaces=" + diagnostics.downstreamSurfaceCount()
                    + " sourceFrames=" + diagnostics.sourceFrameCount()
                    + " previewFrames=" + diagnostics.previewFrameCount()
                    + " encodedSamples=" + diagnostics.encodedSampleCount()
                    + " cameraOpenCount=" + pipeline.cameraOpenCount()
                    + " sessionCreateCount=" + pipeline.sessionCreateCount());
            videoPath = diagnostics.finalizedVideoArtifact().orElseThrow();
            jpegPath = diagnostics.capturedJpegArtifact().orElseThrow();
            assertEquals(selection.videoSize, videoSize(new File(videoPath)));
            assertTrue(audioSampleCount(new File(videoPath)) > 0L);
            assertEquals(selection.imageSize, jpegSize(new File(jpegPath)));
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.release(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.release(operation).outcome());
            assertFalse(new File(videoPath).exists());
            assertFalse(new File(jpegPath).exists());
        } finally {
            pipeline.release(operation);
            delete(videoPath);
            delete(jpegPath);
        }
    }

    @Test public void failedStartAndCancelledJpegRemainRetryableAndClean() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantPermissions(context);
        Selection selection = select(context);
        Assume.assumeTrue("No H.264 Camera2 tuple available", selection != null);
        File outputDirectory = new File(context.getCacheDir(),
                "egl-fanout-retry-cleanup-test");
        EglFanOutPipeline pipeline = new EglFanOutPipelineFactory(
                context, new NoOpLogger(), outputDirectory, () -> null).createHeadless(0);
        CameraOperationContext operation = operation(selection);
        try {
            CameraOperationOutcome bind = pipeline.bindSession(operation).outcome();
            Assume.assumeTrue("EGL fan-out unavailable: " + bind,
                    bind != CameraOperationOutcome.BLOCKED_EXTERNAL);
            assertEquals(CameraOperationOutcome.PASS, bind);

            Thread.currentThread().interrupt();
            CameraOperationOutcome interruptedStart;
            try {
                interruptedStart = pipeline.startEncoder(operation).outcome();
            } finally {
                Thread.interrupted();
            }
            assertEquals(CameraOperationOutcome.CANCELLED_UNKNOWN, interruptedStart);
            SystemClock.sleep(200);
            assertFalse(pipeline.encoderTargetEnabled());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.startEncoder(operation).outcome());

            CameraOperationOutcome timedOutJpeg =
                    pipeline.captureJpeg(expired(operation)).outcome();
            assertEquals(CameraOperationOutcome.TIMEOUT_UNKNOWN, timedOutJpeg);
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.stopEncoder(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.finalizeEncoder(operation).outcome());
            assertEquals(1, pipeline.cameraOpenCount());
            assertEquals(1, pipeline.sessionCreateCount());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.release(operation).outcome());
            SystemClock.sleep(500);
            File[] artifacts = outputDirectory.listFiles((directory, name) ->
                    name.endsWith(".mp4") || name.endsWith(".jpg"));
            assertTrue(artifacts == null || artifacts.length == 0);
        } finally {
            Thread.interrupted();
            pipeline.release(operation);
        }
    }

    @Test public void externalPreviewStallDoesNotBlockEncoder() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantPermissions(context);
        Selection selection = select(context);
        Assume.assumeTrue("No H.264 Camera2 tuple available", selection != null);
        File outputDirectory = new File(context.getCacheDir(), "egl-fanout-external-preview");
        int previewMaxImages = 1;
        ImageReader previewReader = ImageReader.newInstance(
                selection.videoSize.getWidth(), selection.videoSize.getHeight(),
                PixelFormat.RGBA_8888, previewMaxImages);
        HandlerThread previewConsumerThread = new HandlerThread("egl-preview-test-consumer");
        previewConsumerThread.start();
        AtomicBoolean consumePreview = new AtomicBoolean(true);
        AtomicLong consumedPreviewFrames = new AtomicLong();
        AtomicReference<Runnable> previewFrameCallback = new AtomicReference<>(() -> {});
        List<Image> heldPreviewImages = new CopyOnWriteArrayList<>();
        previewReader.setOnImageAvailableListener(reader -> {
            previewFrameCallback.get().run();
            if (!consumePreview.get()) {
                if (heldPreviewImages.size() < previewMaxImages) {
                    Image image = reader.acquireNextImage();
                    if (image != null) heldPreviewImages.add(image);
                }
                return;
            }
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) consumedPreviewFrames.incrementAndGet();
            }
        }, new Handler(previewConsumerThread.getLooper()));
        NativePreviewFrameSignal frameSignal = new NativePreviewFrameSignal() {
            @Override public void start(Handler handler, Runnable onFrame) {
                previewFrameCallback.set(onFrame);
            }

            @Override public void stop() {
                previewFrameCallback.set(() -> {});
            }
        };
        EglFanOutPipeline pipeline = new EglFanOutPipelineFactory(
                context, new NoOpLogger(), outputDirectory, () -> null).create(
                        previewReader.getSurface(), frameSignal, 0, 0L);
        CameraOperationContext operation = operation(selection);
        try {
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.bindSession(operation).outcome());
            long previewBefore = pipeline.diagnostics(operation).previewFrameCount();
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.startEncoder(operation).outcome());
            SystemClock.sleep(300);
            consumePreview.set(false);
            long stallDeadline = SystemClock.elapsedRealtime() + 1_000;
            while (heldPreviewImages.size() < previewMaxImages
                    && SystemClock.elapsedRealtime() < stallDeadline) {
                SystemClock.sleep(20);
            }
            assertEquals(previewMaxImages, heldPreviewImages.size());
            long encodedBeforeStall = pipeline.diagnostics(operation).encodedSampleCount();
            SystemClock.sleep(700);
            CameraPipelineDiagnostics active = pipeline.diagnostics(operation);
            assertTrue(active.encodedSampleCount() > encodedBeforeStall);
            assertTrue(active.previewFrameCount() >= previewBefore);
            consumePreview.set(true);
            heldPreviewImages.forEach(Image::close);
            heldPreviewImages.clear();
            SystemClock.sleep(200);
            assertTrue(consumedPreviewFrames.get() > 0);
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.stopEncoder(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.finalizeEncoder(operation).outcome());
            assertEquals(1, pipeline.cameraOpenCount());
            assertEquals(1, pipeline.sessionCreateCount());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.release(operation).outcome());
            assertEquals(CameraOperationOutcome.PASS,
                    pipeline.release(operation).outcome());
        } finally {
            pipeline.release(operation);
            heldPreviewImages.forEach(Image::close);
            heldPreviewImages.clear();
            previewReader.close();
            previewConsumerThread.quitSafely();
            previewConsumerThread.join(2_000);
        }
    }
    @Test public void malformedJpegArtifactIsRejected() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File malformed = new File(context.getCacheDir(), "egl-malformed.jpg");
        try (FileOutputStream output = new FileOutputStream(malformed)) {
            output.write(new byte[] {0x01, 0x02, 0x03, 0x04});
        }
        try {
            assertTrue(JpegDimensions.read(malformed).isEmpty());
        } finally {
            malformed.delete();
        }
    }

    private static CameraOperationContext expired(CameraOperationContext current) {
        long now = System.currentTimeMillis();
        return new CameraOperationContext(current.cameraId(), current.verificationPipelineId(),
                current.codec(), current.tuple(), current.sessionGeneration(),
                current.cameraHealthGeneration(), new CameraOperationDeadline(now - 2, now - 1));
    }

    private static CameraOperationContext operation(Selection selection) {

        CameraResolution video = new CameraResolution(
                selection.videoSize.getWidth(), selection.videoSize.getHeight());
        CameraResolution image = new CameraResolution(
                selection.imageSize.getWidth(), selection.imageSize.getHeight());
        return new CameraOperationContext(new CameraId(selection.cameraId),
                EglFanOutPipelineFactory.PIPELINE_ID, VideoCodec.H264,
                new CaptureModeTuple(
                        new VideoMode(new StandardResolution(
                                StandardResolutionLabel.MAX, video), selection.fps),
                        new ImageMode(new StandardResolution(
                                StandardResolutionLabel.MAX, image))),
                1, 0, CameraOperationDeadline.after(
                        System.currentTimeMillis(), 60_000));
    }

    private static Selection select(Context context) throws Exception {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Range<Integer>[] ranges = characteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (map == null || ranges == null) continue;
            int fps = chooseFps(ranges);
            Size[] videos = map.getOutputSizes(android.graphics.SurfaceTexture.class);
            Size[] images = map.getOutputSizes(ImageFormat.JPEG);
            if (videos == null || images == null || fps <= 0) continue;
            Arrays.sort(videos, (left, right) -> Long.compare(area(left), area(right)));
            Arrays.sort(images, (left, right) -> Long.compare(area(left), area(right)));
            for (Size video : videos) {
                if (supportsAvc(video, fps)) return new Selection(
                        cameraId, video, images[0], fps);
            }
        }
        return null;
    }

    private static int chooseFps(Range<Integer>[] ranges) {
        boolean hasThirty = false;
        int highest = 0;
        for (Range<Integer> range : ranges) {
            if (range.contains(30)) hasThirty = true;
            highest = Math.max(highest, range.getUpper());
        }
        return hasThirty ? 30 : highest;
    }
    private static boolean supportsAvc(Size size, int fps) {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (!info.isEncoder()) continue;
            try {
                MediaCodecInfo.VideoCapabilities video = info
                        .getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                        .getVideoCapabilities();
                if (video.areSizeAndRateSupported(
                        size.getWidth(), size.getHeight(), fps)) return true;
            } catch (IllegalArgumentException ignored) {}
        }
        return false;
    }

    private static Size videoSize(File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            return new Size(Integer.parseInt(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)),
                    Integer.parseInt(retriever.extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));
        } finally {
            try { retriever.release(); } catch (java.io.IOException ignored) {}
        }
    }

    private static Size jpegSize(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return new Size(options.outWidth, options.outHeight);
    }

    private static long area(Size size) {
        return (long) size.getWidth() * size.getHeight();
    }

    private static void grantPermissions(Context context) {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
        automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.RECORD_AUDIO);
    }

    private static void delete(String path) {
        if (path != null) new File(path).delete();
    }

    private record Selection(String cameraId, Size videoSize, Size imageSize, int fps) {}

    private static final class NoOpLogger implements Logger {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    }
}
