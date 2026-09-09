package com.dvid.dcam.platform.device.capability.probe.nativesharing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderSizeCapabilities;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.FrameRateRange;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.RawCatalog;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionMapper;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.platform.device.capability.catalog.AndroidCameraCatalogSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class NativeSurfaceSharingFastProbeTest {
    @Test public void reportsExactSharedPrivateAndJpegTopology() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        grantCameraPermission(instrumentation, context);
        PrintingLogger logger = new PrintingLogger();
        RawCatalog catalog = new AndroidCameraCatalogSource(context, logger).load();
        Fixture fixture = fixture(catalog);

        NativeSurfaceSharingFastProbe.Result result = new NativeSurfaceSharingFastProbe(
                context, logger).probe(fixture.camera().cameraId(),
                List.of(fixture.video()), List.of(fixture.image()));

        print("completion=" + result.completion()
                + " availability=" + result.evidence().availability()
                + " cameraId=" + fixture.camera().cameraId()
                + " tuple=" + new com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple(
                        fixture.video(), fixture.image())
                + " maxSharedSurfaceCount=" + result.maxSharedSurfaceCount()
                + " candidateCount=" + result.evidence().rawFastCandidates().size()
                + " attemptCount=" + result.attempts().size()
                + " elapsedMs=" + result.elapsedMillis()
                + " detail=" + result.detail());

        assertEquals(NativeSurfaceSharingFastProbe.PIPELINE_ID,
                result.evidence().verificationPipelineId());
        assertTrue(result.evidence().candidateEvidence().isEmpty());
        if (result.completion()
                == NativeSurfaceSharingFastProbe.Completion.PIPELINE_UNAVAILABLE) {
            assertEquals(PipelineAvailability.UNAVAILABLE,
                    result.evidence().availability());
            return;
        }

        assertEquals("Device probe incomplete: " + result.detail(),
                NativeSurfaceSharingFastProbe.Completion.COMPLETE, result.completion());
        assertTrue("No exact topology attempt recorded", !result.attempts().isEmpty());
        assertTrue(result.attempts().stream().allMatch(attempt ->
                attempt.cameraOutputCount() == 2
                        && attempt.sharedSurfaceCount() == 2
                        && attempt.surfaceSourceClasses().equals(
                                "SurfaceTexture+MediaCodec|ImageReader")));
    }

    private static Fixture fixture(RawCatalog catalog) {
        for (CameraFacts camera : catalog.cameras()) {
            List<StandardResolution> videos = StandardResolutionMapper.map(
                    commonVideoResolutions(camera));
            List<StandardResolution> images = StandardResolutionMapper.map(
                    camera.jpegOutputs().stream().map(value -> value.resolution())
                            .collect(java.util.stream.Collectors.toList()));
            for (StandardResolution video : videos) {
                Integer framesPerSecond = framesPerSecond(catalog, camera, video.actual());
                if (framesPerSecond == null) continue;
                for (StandardResolution image : images) {
                    return new Fixture(camera,
                            new VideoMode(video, framesPerSecond), new ImageMode(image));
                }
            }
        }
        throw new AssertionError("No catalog-backed standard V-F-I fixture available");
    }

    private static List<CameraResolution> commonVideoResolutions(CameraFacts camera) {
        Set<CameraResolution> mediaCodec = new HashSet<>();
        camera.mediaCodecOutputs().forEach(value -> mediaCodec.add(value.resolution()));
        List<CameraResolution> common = new ArrayList<>();
        camera.privateOutputs().forEach(value -> {
            if (mediaCodec.contains(value.resolution())) common.add(value.resolution());
        });
        return List.copyOf(common);
    }

    private static Integer framesPerSecond(RawCatalog catalog, CameraFacts camera,
            CameraResolution resolution) {
        for (int framesPerSecond = 60; framesPerSecond >= 20; framesPerSecond--) {
            BigDecimal value = BigDecimal.valueOf(framesPerSecond);
            if (contains(camera.aeTargetFpsRanges(), value)
                    && encoderSupports(catalog.h264Encoders(), resolution, value)) {
                return framesPerSecond;
            }
        }
        return null;
    }

    private static boolean encoderSupports(List<EncoderFacts> encoders,
            CameraResolution resolution, BigDecimal framesPerSecond) {
        for (EncoderFacts encoder : encoders) {
            if (encoder.videoCapabilities() == null) continue;
            for (EncoderSizeCapabilities size
                    : encoder.videoCapabilities().cameraSizeCapabilities()) {
                if (size.resolution().equals(resolution)
                        && size.supportedFrameRates().contains(
                                framesPerSecond.intValueExact())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(List<FrameRateRange> ranges, BigDecimal value) {
        return ranges.stream().anyMatch(range -> contains(range, value));
    }

    private static boolean contains(FrameRateRange range, BigDecimal value) {
        return range.lower().compareTo(value) <= 0 && range.upper().compareTo(value) >= 0;
    }

    private static void grantCameraPermission(Instrumentation instrumentation, Context context)
            throws Exception {
        if (context.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            instrumentation.getUiAutomation().grantRuntimePermission(
                    context.getPackageName(), Manifest.permission.CAMERA);
        } else {
            instrumentation.getUiAutomation().executeShellCommand(
                    "pm grant " + context.getPackageName() + " " + Manifest.permission.CAMERA)
                    .close();
        }
    }

    private static void print(String message) {
        System.out.println("NATIVE_SURFACE_SHARING_DIAGNOSTIC " + message);
    }

    private record Fixture(CameraFacts camera, VideoMode video, ImageMode image) { }

    private static final class PrintingLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) { print(message); }
        @Override public void info(LogCategory category, String eventName, String message) { print(message); }
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {
            print(message + " error=" + error);
        }
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {
            print(message + " error=" + error);
        }
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {
            print(message + " error=" + error);
        }
    }
}
