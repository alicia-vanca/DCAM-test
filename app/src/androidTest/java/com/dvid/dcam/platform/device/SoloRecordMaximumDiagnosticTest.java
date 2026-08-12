package com.dvid.dcam.platform.device;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SoloRecordMaximumDiagnosticTest {
    private static final String TAG = "SOLO_RECORD_POC";
    private static final Size PREVIEW_HD = new Size(1280, 720);
    private static final long WAIT_SECONDS = 10;

    @Test public void findMaximumSoloRecordOutput() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CameraManager manager = context.getSystemService(CameraManager.class);
        String cameraId = rearCameraId(manager);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        List<Size> candidates = outputCandidates(map);
        print("camera=" + cameraId + " candidates=" + labels(candidates));

        HandlerThread thread = new HandlerThread("solo-record-poc");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        CameraDevice camera = null;
        try {
            camera = openCamera(manager, cameraId, handler);
            List<ProbeResult> successful = new ArrayList<>();
            for (Size candidate : candidates) {
                ProbeResult result = probeRecorder(context, camera, map, candidate, handler);
                print("candidate=" + label(candidate) + " success=" + result.success
                        + " output=" + label(result.outputSize) + " detail=" + result.detail);
                if (result.success) successful.add(result);
            }
            successful.sort(Comparator.comparingLong((ProbeResult result) -> area(result.outputSize)).reversed());
            if (successful.isEmpty())
                throw new AssertionError("No solo recording candidate produced a valid file");
            print("MAX_NATIVE_SOLO_RECORD=" + label(successful.get(0).outputSize));
        } finally {
            if (camera != null) camera.close();
            thread.quitSafely();
            thread.join(WAIT_SECONDS * 1000);
        }
    }

    @Test public void findMaximumRecordWithHdPreviewOutput() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CameraManager manager = context.getSystemService(CameraManager.class);
        String cameraId = rearCameraId(manager);
        StreamConfigurationMap map = manager.getCameraCharacteristics(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        List<Size> candidates = outputCandidates(map);
        HandlerThread thread = new HandlerThread("record-hd-preview-poc");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        ImageReader preview = ImageReader.newInstance(PREVIEW_HD.getWidth(),
                PREVIEW_HD.getHeight(), ImageFormat.PRIVATE, 4);
        preview.setOnImageAvailableListener(reader -> {
            android.media.Image image = reader.acquireLatestImage();
            if (image != null) image.close();
        }, handler);
        CameraDevice camera = null;
        try {
            camera = openCamera(manager, cameraId, handler);
            List<ProbeResult> successful = new ArrayList<>();
            for (Size candidate : candidates) {
                ProbeResult result = probeRecorder(context, camera, map, candidate, handler,
                        preview.getSurface());
                print("preview=1280x720 candidate=" + label(candidate)
                        + " success=" + result.success + " output=" + label(result.outputSize)
                        + " detail=" + result.detail);
                if (result.success) successful.add(result);
            }
            successful.sort(Comparator.comparingLong(
                    (ProbeResult result) -> area(result.outputSize)).reversed());
            if (successful.isEmpty())
                throw new AssertionError("No recording candidate worked with HD preview");
            print("MAX_NATIVE_RECORD_WITH_HD_PREVIEW="
                    + label(successful.get(0).outputSize));
        } finally {
            if (camera != null) camera.close();
            preview.close();
            thread.quitSafely();
            thread.join(WAIT_SECONDS * 1000);
        }
    }

    private static ProbeResult probeRecorder(Context context, CameraDevice camera,
            StreamConfigurationMap map, Size size, Handler handler) {
        return probeRecorder(context, camera, map, size, handler, null);
    }

    private static ProbeResult probeRecorder(Context context, CameraDevice camera,
            StreamConfigurationMap map, Size size, Handler handler, Surface previewSurface) {
        File output = new File(context.getCacheDir(), "solo-record-" + label(size) + ".mp4");
        if (output.exists()) output.delete();
        MediaRecorder recorder = new MediaRecorder(context);
        CameraCaptureSession session = null;
        Surface recorderSurface = null;
        try {
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(size.getWidth(), size.getHeight());
            recorder.setVideoFrameRate(frameRate(map, size));
            recorder.setVideoEncodingBitRate((int) Math.min(40000000L,
                    Math.max(4000000L, area(size) * 5L)));
            recorder.setOutputFile(output.getAbsolutePath());
            recorder.prepare();
            recorderSurface = recorder.getSurface();

            CountDownLatch configured = new CountDownLatch(1);
            AtomicReference<CameraCaptureSession> sessionRef = new AtomicReference<>();
            AtomicReference<String> failure = new AtomicReference<>("none");
            List<Surface> surfaces = new ArrayList<>();
            if (previewSurface != null) surfaces.add(previewSurface);
            surfaces.add(recorderSurface);
            camera.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession value) {
                            sessionRef.set(value);
                            configured.countDown();
                        }

                        @Override public void onConfigureFailed(CameraCaptureSession value) {
                            failure.set("camera_session_failed");
                            value.close();
                            configured.countDown();
                        }
                    }, handler);
            if (!configured.await(WAIT_SECONDS, TimeUnit.SECONDS))
                return ProbeResult.fail("camera_session_timeout");
            session = sessionRef.get();
            if (session == null) return ProbeResult.fail(failure.get());

            CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            if (previewSurface != null) request.addTarget(previewSurface);
            request.addTarget(recorderSurface);
            session.setRepeatingRequest(request.build(), null, handler);
            SystemClock.sleep(400);
            recorder.start();
            SystemClock.sleep(1500);
            recorder.stop();

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            Size outputSize;
            try {
                retriever.setDataSource(output.getAbsolutePath());
                outputSize = new Size(
                        parseMetadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                        parseMetadata(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            } finally {
                retriever.release();
            }
            return size.equals(outputSize) ? ProbeResult.success(outputSize)
                    : ProbeResult.fail("file=" + label(outputSize));
        } catch (Throwable failure) {
            return ProbeResult.fail(failure.getClass().getSimpleName() + ":" + failure.getMessage());
        } finally {
            if (session != null) session.close();
            try { recorder.reset(); } catch (Throwable ignored) { }
            recorder.release();
            if (recorderSurface != null) recorderSurface.release();
            if (output.exists()) output.delete();
        }
    }

    private static int frameRate(StreamConfigurationMap map, Size size) {
        long duration = 0;
        try {
            duration = map.getOutputMinFrameDuration(MediaRecorder.class, size);
        } catch (IllegalArgumentException ignored) { }
        if (duration <= 0) {
            try {
                duration = map.getOutputMinFrameDuration(SurfaceTexture.class, size);
            } catch (IllegalArgumentException ignored) { }
        }
        if (duration <= 0) return 30;
        return Math.max(1, Math.min(30, (int) (1000000000L / duration)));
    }

    private static List<Size> outputCandidates(StreamConfigurationMap map) {
        Map<String, Size> unique = new LinkedHashMap<>();
        addSizes(unique, map.getOutputSizes(SurfaceTexture.class));
        addSizes(unique, map.getOutputSizes(MediaRecorder.class));
        try {
            addSizes(unique, map.getOutputSizes(android.media.MediaCodec.class));
        } catch (IllegalArgumentException ignored) { }
        List<Size> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparingLong(SoloRecordMaximumDiagnosticTest::area)
                .thenComparingInt(Size::getWidth)
                .thenComparingInt(Size::getHeight).reversed());
        return result;
    }

    private static void addSizes(Map<String, Size> unique, Size[] sizes) {
        if (sizes == null) return;
        for (Size size : sizes) unique.put(label(size), size);
    }

    private static CameraDevice openCamera(CameraManager manager, String cameraId, Handler handler)
            throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        AtomicReference<CameraDevice> result = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>("none");
        manager.openCamera(cameraId, new CameraDevice.StateCallback() {
            @Override public void onOpened(CameraDevice camera) {
                result.set(camera);
                opened.countDown();
            }

            @Override public void onDisconnected(CameraDevice camera) {
                failure.set("disconnected");
                camera.close();
                opened.countDown();
            }

            @Override public void onError(CameraDevice camera, int error) {
                failure.set("error=" + error);
                camera.close();
                opened.countDown();
            }
        }, handler);
        if (!opened.await(WAIT_SECONDS, TimeUnit.SECONDS))
            throw new AssertionError("Camera open timeout");
        if (result.get() == null)
            throw new AssertionError("Camera open failed: " + failure.get());
        return result.get();
    }

    private static String rearCameraId(CameraManager manager) throws Exception {
        for (String cameraId : manager.getCameraIdList()) {
            Integer facing = manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK)
                return cameraId;
        }
        throw new IllegalStateException("No rear camera");
    }

    private static int parseMetadata(MediaMetadataRetriever retriever, int key) {
        String value = retriever.extractMetadata(key);
        if (value == null) throw new IllegalStateException("Missing metadata " + key);
        return Integer.parseInt(value);
    }

    private static long area(Size size) {
        return (long) size.getWidth() * size.getHeight();
    }

    private static String label(Size size) {
        return size == null ? "none" : size.getWidth() + "x" + size.getHeight();
    }

    private static String labels(List<Size> sizes) {
        List<String> labels = new ArrayList<>();
        for (Size size : sizes) labels.add(label(size));
        return labels.toString();
    }

    private static void print(String message) {
        Log.i(TAG, message);
        System.out.println(TAG + " " + message);
    }

    private static final class ProbeResult {
        private final boolean success;
        private final Size outputSize;
        private final String detail;

        private ProbeResult(boolean success, Size outputSize, String detail) {
            this.success = success;
            this.outputSize = outputSize;
            this.detail = detail;
        }

        private static ProbeResult success(Size outputSize) {
            return new ProbeResult(true, outputSize, "exact");
        }

        private static ProbeResult fail(String detail) {
            return new ProbeResult(false, null, detail);
        }
    }
}
