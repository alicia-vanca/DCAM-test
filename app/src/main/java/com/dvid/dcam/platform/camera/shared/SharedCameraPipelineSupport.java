package com.dvid.dcam.platform.camera.shared;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Range;
import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import java.io.File;
import java.io.IOException;

public final class SharedCameraPipelineSupport {
    private SharedCameraPipelineSupport() {}

    public static Range<Integer> selectFpsRange(
            Context context, CameraOperationContext operation) throws CameraAccessException {
        int selected = operation.tuple().videoMode().framesPerSecond();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        Range<Integer>[] ranges = manager.getCameraCharacteristics(operation.cameraId().value())
                .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        Range<Integer> best = null;
        if (ranges != null) for (Range<Integer> range : ranges) {
            if (!range.contains(selected)) continue;
            if (best == null || range.getUpper() < best.getUpper()
                    || range.getLower() > best.getLower()) best = range;
        }
        return best;
    }

    public static void applyFps(CaptureRequest.Builder builder, Range<Integer> range) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range);
        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
    }

    public static long remainingReleaseMillis(long deadlineMillis) {
        return Math.max(0, deadlineMillis - SystemClock.elapsedRealtime());
    }

    public static long elapsed(long startedMillis) {
        return Math.max(0, SystemClock.elapsedRealtime() - startedMillis);
    }

    public static boolean deadlineExpired(CameraOperationContext operation) {
        return operation.deadline().isExpiredAt(System.currentTimeMillis());
    }

    public static long waitMillis(CameraOperationContext operation, long maximum) {
        return Math.max(1, Math.min(maximum,
                operation.deadline().remainingMillis(System.currentTimeMillis())));
    }

    public static boolean deleteArtifact(File file) {
        if (file == null || !file.exists()) return true;
        try { return file.delete() || !file.exists(); }
        catch (SecurityException ignored) { return false; }
    }

    public static void deleteQuietly(File file) {
        deleteArtifact(file);
    }

    public static boolean joinThread(HandlerThread thread, long timeoutMillis) {
        if (!thread.isAlive()) return true;
        if (timeoutMillis <= 0) return false;
        try {
            thread.join(timeoutMillis);
            return !thread.isAlive();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static VideoInspection inspectVideo(File file) {
        return inspectVideo(file, 0L, true);
    }

    public static VideoInspection inspectCleanVideo(
            File file, long writtenSamples, CameraResolution encodedResolution) {
        if (file == null || !file.isFile() || file.length() <= 0) {
            return VideoInspection.invalid("file_missing");
        }
        if (writtenSamples <= 0L) return VideoInspection.invalid("samples_missing");
        if (encodedResolution == null) {
            return VideoInspection.invalid("encoder_resolution_missing");
        }
        return new VideoInspection(true, encodedResolution, writtenSamples,
                "valid,source=encoder");
    }

    private static VideoInspection inspectVideo(
            File file, long writtenSamples, boolean scanSamples) {
        if (file == null || !file.isFile() || file.length() <= 0) {
            return VideoInspection.invalid("file_missing");
        }
        if (!scanSamples && writtenSamples <= 0L) {
            return VideoInspection.invalid("samples_missing");
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int index = 0; index < extractor.getTrackCount(); index++) {
                MediaFormat format = extractor.getTrackFormat(index);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("video/")) continue;
                int width = format.getInteger(MediaFormat.KEY_WIDTH);
                int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                int containerRotation = format.containsKey(MediaFormat.KEY_ROTATION)
                        ? format.getInteger(MediaFormat.KEY_ROTATION) : 0;
                long samples = writtenSamples;
                if (scanSamples) {
                    extractor.selectTrack(index);
                    samples = 0L;
                    while (extractor.getSampleTrackIndex() >= 0) {
                        samples++;
                        if (!extractor.advance()) break;
                    }
                }
                if (!MediaFormat.MIMETYPE_VIDEO_AVC.equalsIgnoreCase(mime) || samples <= 0L) {
                    return VideoInspection.invalid(
                            "invalid_track:mime=" + mime + ",samples=" + samples);
                }
                return new VideoInspection(true,
                        new CameraResolution(width, height), samples,
                        "valid,containerRotationDegrees=" + containerRotation);
            }
            return VideoInspection.invalid("video_track_missing");
        } catch (IOException | RuntimeException error) {
            return VideoInspection.invalid("parse:" + error.getClass().getSimpleName());
        } finally {
            extractor.release();
        }
    }

    public record VideoInspection(boolean valid, CameraResolution resolution,
            long sampleCount, String detail) {
        public static VideoInspection invalid(String detail) {
            return new VideoInspection(false, null, 0, detail);
        }
    }
}
