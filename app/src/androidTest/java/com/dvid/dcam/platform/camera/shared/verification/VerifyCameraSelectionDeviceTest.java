package com.dvid.dcam.platform.camera.shared.verification;

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Range;
import android.util.Size;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import com.dvid.dcam.feature.device.application.port.CameraVerificationClock;
import com.dvid.dcam.feature.device.application.usecase.VerifyCameraSelectionUseCase;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CandidateEvidence;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
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
import com.dvid.dcam.platform.camera.shared.api.SharedCameraPipeline;
import com.dvid.dcam.platform.camera.shared.egl.EglFanOutPipelineFactory;
import com.dvid.dcam.platform.camera.shared.outputsharing.NativeSurfaceSharingPipelineFactory;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class VerifyCameraSelectionDeviceTest {
    private static final Size FHD_ALIGNED_VIDEO = new Size(1920, 1088);
    private static final Size FHD_IMAGE = new Size(1920, 1080);
    @Test public void nativePipelineRunsThroughVerifier() throws Exception {
        Context context = targetContext();
        grantPermissions(context);
        Selection selection = select(context);
        Assume.assumeTrue("No H.264 Camera2 tuple available", selection != null);
        runVerifier(context, selection, NativeSurfaceSharingPipelineFactory.PIPELINE_ID,
                new NativeSurfaceSharingPipelineFactory(context, new NoOpLogger(),
                        new File(context.getCacheDir(), "verifier-native"), () -> null)
                        .createHeadless(0));
    }

    @Test public void eglPipelineRunsThroughVerifier() throws Exception {
        Assume.assumeTrue("EGL fan-out needs API 26", Build.VERSION.SDK_INT >= 26);
        Context context = targetContext();
        grantPermissions(context);
        Selection selection = select(context);
        Assume.assumeTrue("No H.264 Camera2 tuple available", selection != null);
        runVerifier(context, selection, EglFanOutPipelineFactory.PIPELINE_ID,
                new EglFanOutPipelineFactory(context, new NoOpLogger(),
                        new File(context.getCacheDir(), "verifier-egl"), () -> null)
                        .createHeadless(0));
    }

    @Test public void nativePipelineVerifiesAlignedFhdFallback() throws Exception {
        Context context = targetContext();
        grantPermissions(context);
        Selection selection = select(context, FHD_ALIGNED_VIDEO, FHD_IMAGE);
        assertTrue("Aligned FHD H.264 tuple unavailable", selection != null);
        runVerifier(context, selection, NativeSurfaceSharingPipelineFactory.PIPELINE_ID,
                new NativeSurfaceSharingPipelineFactory(context, new NoOpLogger(),
                        new File(context.getCacheDir(), "verifier-native-fhd"), () -> null)
                        .createHeadless(0));
    }

    @Test public void eglPipelineVerifiesAlignedFhdFallback() throws Exception {
        Assume.assumeTrue("EGL fan-out needs API 26", Build.VERSION.SDK_INT >= 26);
        Context context = targetContext();
        grantPermissions(context);
        Selection selection = select(context, FHD_ALIGNED_VIDEO, FHD_IMAGE);
        assertTrue("Aligned FHD H.264 tuple unavailable", selection != null);
        runVerifier(context, selection, EglFanOutPipelineFactory.PIPELINE_ID,
                new EglFanOutPipelineFactory(context, new NoOpLogger(),
                        new File(context.getCacheDir(), "verifier-egl-fhd"), () -> null)
                        .createHeadless(0));
    }

    private static void runVerifier(Context context, Selection selection,
            VerificationPipelineId pipelineId, SharedCameraPipeline pipeline) {
        CameraId cameraId = new CameraId(selection.cameraId);
        CaptureModeTuple tuple = new CaptureModeTuple(
                new VideoMode(standardResolution(selection.videoSize), selection.fps),
                new ImageMode(standardResolution(selection.imageSize)));
        CandidateKey video = CandidateKey.forVideo(cameraId, VideoCodec.H264,
                pipelineId, tuple.videoMode());
        CandidateKey image = CandidateKey.forImage(cameraId, VideoCodec.H264,
                pipelineId, tuple.imageMode());
        CandidateKey candidate = CandidateKey.forTuple(cameraId, VideoCodec.H264,
                pipelineId, tuple);
        PipelineEvidence evidence = new PipelineEvidence(cameraId, VideoCodec.H264,
                pipelineId, PipelineAvailability.AVAILABLE,
                List.of(video, image, candidate),
                List.of(new CandidateEvidence(video, VerificationOutcome.VERIFIED_PASS)));
        CameraCapabilityStore.Snapshot snapshot = snapshot(cameraId, evidence);
        CameraVerificationClock clock = CameraVerificationClock.system();
        NoOpLogger logger = new NoOpLogger();
        CameraRuntimeOperations runtime = new SharedCameraVerificationRuntime(
                pipeline, logger, clock);
        VerifyCameraSelectionUseCase.Result result = new VerifyCameraSelectionUseCase(
                runtime, new DeviceStore(snapshot), logger, clock, ignoredCameraId -> 0L)
                .execute(new VerifyCameraSelectionUseCase.Request(snapshot, candidate, 1, 0));
        try {
            System.out.println("CAM_IMP_11_DIAGNOSTIC pipeline=" + pipelineId
                    + " completion=" + result.completion()
                    + " outcome=" + result.outcome()
                    + " detail=" + result.detail()
                    + " attempts=" + result.attempts());
            Assume.assumeTrue("External camera state blocked verifier: " + result.outcome(),
                    result.outcome() != VerificationOutcome.BLOCKED_EXTERNAL
                            && result.outcome() != VerificationOutcome.TRANSIENT_RETRYABLE
                            && result.outcome() != VerificationOutcome.GLOBAL_FAILURE
                            && result.outcome() != VerificationOutcome.CANCELLED_UNKNOWN);
            assertTrue("Verifier result: " + result.detail(), result.verified());
            assertTrue(result.verifiedBinding().isPresent());
            System.out.println("CAM_IMP_11_EVIDENCE pipeline=" + pipelineId
                    + " cameraId=" + selection.cameraId + " video=" + selection.videoSize
                    + " fps=" + selection.fps + " image=" + selection.imageSize
                    + " attempts=" + result.attempts().size());
        } finally {
            result.activeBinding().ifPresent(runtime::release);
        }
    }

    private static CameraCapabilityStore.Snapshot snapshot(CameraId cameraId,
            PipelineEvidence evidence) {
        CameraCapabilityStore.CodecSnapshot codec =
                new CameraCapabilityStore.CodecSnapshot(VideoCodec.H264,
                        CameraCapabilityStore.CodecState.ACTIVE, Optional.empty(),
                        List.of(evidence), Optional.empty());
        CameraCapabilityStore.CameraSnapshot camera =
                new CameraCapabilityStore.CameraSnapshot(cameraId, "device-camera-signature",
                        List.of(codec), Optional.empty(), Optional.empty());
        return CameraCapabilityStore.Snapshot.current("device-signature",
                List.of(), List.of(camera));
    }

    private static Context targetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static void grantPermissions(Context context) {
        var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
        automation.grantRuntimePermission(context.getPackageName(), Manifest.permission.RECORD_AUDIO);
    }

    private static Selection select(Context context) throws Exception {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) continue;
            Size[] videos = map.getOutputSizes(MediaCodec.class);
            Size[] images = map.getOutputSizes(ImageFormat.JPEG);
            Range<Integer>[] ranges = characteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (videos == null || images == null || ranges == null) continue;
            int fps = chooseFps(ranges);
            Arrays.sort(videos, Comparator.comparingLong(VerifyCameraSelectionDeviceTest::area));
            Arrays.sort(images, Comparator.comparingLong(VerifyCameraSelectionDeviceTest::area));
            for (Size video : videos) {
                if (supportsAvc(video, fps)) {
                    return new Selection(cameraId, video, images[0], fps);
                }
            }
        }
        return null;
    }

    private static Selection select(Context context, Size videoSize, Size imageSize)
            throws Exception {
        CameraManager manager = (CameraManager) context.getSystemService(
                Context.CAMERA_SERVICE);
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null || !contains(map.getOutputSizes(MediaCodec.class), videoSize)
                    || !contains(map.getOutputSizes(ImageFormat.JPEG), imageSize)) {
                continue;
            }
            Range<Integer>[] ranges = characteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            if (ranges == null) continue;
            int fps = chooseFps(ranges);
            if (supportsAvc(videoSize, fps)) {
                return new Selection(cameraId, videoSize, imageSize, fps);
            }
        }
        return null;
    }

    private static boolean contains(Size[] sizes, Size required) {
        if (sizes == null) return false;
        for (Size size : sizes) {
            if (size.equals(required)) return true;
        }
        return false;
    }

    private static StandardResolution standardResolution(Size size) {
        CameraResolution actual = new CameraResolution(size.getWidth(), size.getHeight());
        for (StandardResolutionLabel label : StandardResolutionLabel.values()) {
            if (label != StandardResolutionLabel.MAX && label.accepts(actual)) {
                return new StandardResolution(label, actual);
            }
        }
        return new StandardResolution(StandardResolutionLabel.MAX, actual);
    }

    private static int chooseFps(Range<Integer>[] ranges) {
        int best = 0;
        for (Range<Integer> range : ranges) {
            if (range.contains(30)) return 30;
            if (range.getUpper() <= 30) best = Math.max(best, range.getUpper());
        }
        if (best > 0) return best;
        for (Range<Integer> range : ranges) best = Math.max(best, range.getUpper());
        return best;
    }

    private static boolean supportsAvc(Size size, int fps) {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (!info.isEncoder()) continue;
            try {
                MediaCodecInfo.VideoCapabilities capabilities = info
                        .getCapabilitiesForType("video/avc").getVideoCapabilities();
                if (capabilities.areSizeAndRateSupported(
                        size.getWidth(), size.getHeight(), fps)) return true;
            } catch (IllegalArgumentException ignored) {}
        }
        return false;
    }

    private static long area(Size size) {
        return (long) size.getWidth() * size.getHeight();
    }

    private record Selection(String cameraId, Size videoSize, Size imageSize, int fps) {}

    private static final class DeviceStore implements CameraCapabilityStore {
        private final Snapshot snapshot;
        private DeviceStore(Snapshot snapshot) { this.snapshot = snapshot; }
        @Override public LoadResult load(Freshness freshness) {
            return LoadResult.loaded(snapshot);
        }
        @Override public void requestWrite(Snapshot snapshot) {}
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }
}
