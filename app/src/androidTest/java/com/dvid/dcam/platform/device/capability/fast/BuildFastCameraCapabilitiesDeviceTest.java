package com.dvid.dcam.platform.device.capability.fast;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.platform.device.capability.catalog.AndroidCameraCatalogSource;
import com.dvid.dcam.platform.device.capability.probe.nativesharing.NativeSurfaceSharingFastProbe;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class BuildFastCameraCapabilitiesDeviceTest {
    @Test public void buildsAuthoritativeFastSnapshotOnAndroidRuntime() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantCameraPermission(context);
        NoOpLogger logger = new NoOpLogger();
        BuildFastCameraCapabilitiesUseCase useCase = new BuildFastCameraCapabilitiesUseCase(
                new AndroidCameraCatalogSource(context, logger), logger);
        AndroidFastCameraCapabilityProbe probe = new AndroidFastCameraCapabilityProbe(
                new NativeSurfaceSharingFastProbe(context, logger), logger);

        BuildFastCameraCapabilitiesUseCase.Result result = useCase.execute(probe);

        assertTrue(result.detail(), result.complete());
        assertTrue(result.authoritativeSnapshot().isPresent());
        var snapshot = result.authoritativeSnapshot().orElseThrow();
        assertFalse(snapshot.cameras().isEmpty());
        CameraResolution exactFhd = new CameraResolution(1920, 1080);
        CameraResolution alignedFhd = new CameraResolution(1920, 1088);
        var alignedCamera = snapshot.cameras().stream()
                .filter(camera -> camera.cameraFacts().privateOutputs().stream()
                        .anyMatch(output -> output.resolution().equals(exactFhd)))
                .filter(camera -> camera.cameraFacts().privateOutputs().stream()
                        .anyMatch(output -> output.resolution().equals(alignedFhd)))
                .findFirst();
        assertTrue("Target camera lacks exact and aligned FHD outputs",
                alignedCamera.isPresent());
        var fhdVideoCandidates = alignedCamera.orElseThrow().evidence()
                .rawFastCandidates().stream()
                .filter(candidate -> candidate.videoMode().isPresent())
                .filter(candidate -> candidate.videoMode().orElseThrow()
                        .resolution().label() == StandardResolutionLabel.FHD)
                .collect(java.util.stream.Collectors.toList());
        assertTrue("Aligned FHD candidate was not probed",
                fhdVideoCandidates.stream().anyMatch(candidate -> candidate
                        .videoMode().orElseThrow().resolution().actual().equals(alignedFhd)));
        assertFalse("Unsupported exact FHD candidate remained selected",
                fhdVideoCandidates.stream().anyMatch(candidate -> candidate
                        .videoMode().orElseThrow().resolution().actual().equals(exactFhd)));
        assertTrue(result.benchmark().rounds().stream()
                .allMatch(BuildFastCameraCapabilitiesUseCase.RoundTiming::cleanupComplete));
    }

    private static void grantCameraPermission(Context context) {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), Manifest.permission.CAMERA);
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    }
}
