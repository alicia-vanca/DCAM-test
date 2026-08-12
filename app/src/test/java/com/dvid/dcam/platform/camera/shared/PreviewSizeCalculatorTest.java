package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import org.junit.jupiter.api.Test;

final class PreviewSizeCalculatorTest {
    @Test void portraitBufferFitsWithoutHorizontalStretch() {
        assertEquals(new PreviewSizeCalculator.Size(101, 180),
                PreviewSizeCalculator.fit(400, 180, new CameraResolution(720, 1280)));
    }

    @Test void landscapeBufferRotatedToPortraitFitsWithoutDistortion() {
        assertEquals(new PreviewSizeCalculator.Size(180, 101),
                PreviewSizeCalculator.fit(400, 180,
                        new CameraResolution(1920, 1080), 270));
    }

    @Test void frontCameraLandscapeBufferScalesToScreenWidthAfterRotation() {
        assertEquals(new PreviewSizeCalculator.Size(720, 480),
                PreviewSizeCalculator.fitWidth(480,
                        new CameraResolution(720, 480), 270));
    }

    @Test void eitherQuarterTurnUsesSameAxisSwap() {
        CameraResolution source = new CameraResolution(1920, 1080);
        assertEquals(
                PreviewSizeCalculator.fit(480, 692, source, 90),
                PreviewSizeCalculator.fit(480, 692, source, 270));
    }

    @Test void halfTurnKeepsUnrotatedDisplayDimensions() {
        CameraResolution source = new CameraResolution(1920, 1080);
        assertEquals(
                PreviewSizeCalculator.fit(480, 692, source, 0),
                PreviewSizeCalculator.fit(480, 692, source, 180));
    }

    @Test void frontVideoAspectFitsInsidePreviewWithoutCropping() {
        assertEquals(new PreviewSizeCalculator.Size(692, 461),
                PreviewSizeCalculator.fit(480, 692,
                        new CameraResolution(720, 480), 270));
    }

    @Test void fittedFrontVideoViewportKeepsFullSourceFrame() {
        assertEquals(new PreviewSizeCalculator.Size(692, 461),
                PreviewSizeCalculator.centerCrop(461, 692,
                        new CameraResolution(720, 480), 270));
    }


    @Test void containRoundingNeverExceedsQuarterTurnViewport() {
        assertEquals(new PreviewSizeCalculator.Size(746, 420),
                PreviewSizeCalculator.fit(420, 746,
                        new CameraResolution(1280, 720), 270));
    }

    @Test void landscapeBufferScalesToScreenWidth() {
        assertEquals(new PreviewSizeCalculator.Size(480, 272),
                PreviewSizeCalculator.fitWidth(480,
                        new CameraResolution(1920, 1088), 0));
    }

    @Test void portraitBackLayoutRemainsUnchangedAtZeroDisplayRotation() {
        CameraResolution source = new CameraResolution(1920, 1088);

        assertEquals(new PreviewSizeCalculator.Layout(
                        480, 272, 480, 272, 480, 272, 0),
                PreviewSizeCalculator.containLayout(
                        480, 746, source, source, 0, 0));
    }

    @Test void portraitFrontLayoutUsesDisplayBasedPreviewBufferRotation() {
        CameraResolution source = new CameraResolution(1280, 720);

        assertEquals(new PreviewSizeCalculator.Layout(
                        420, 746, 420, 746, 420, 746, 0),
                PreviewSizeCalculator.containLayout(
                        480, 746, source, source, 0, 270));
    }

    @Test void imageAspectViewportContainsDifferentVideoBufferWithoutCrop() {
        assertEquals(new PreviewSizeCalculator.Layout(
                        480, 270, 476, 270, 476, 270, 0),
                PreviewSizeCalculator.containLayout(
                        480, 746,
                        new CameraResolution(3072, 1728),
                        new CameraResolution(1920, 1088),
                        0, 0));
    }

    @Test void landscapeBackLayoutRotatesRawTextureWithoutOverflow() {
        CameraResolution source = new CameraResolution(1920, 1088);

        assertEquals(new PreviewSizeCalculator.Layout(
                        272, 480, 480, 272, 272, 480, 270),
                PreviewSizeCalculator.containLayout(
                        854, 480, source, source, 90, 270));
    }

    @Test void frontLandscapePreviewCounterRotatesDisplay() {
        CameraResolution source = new CameraResolution(1280, 720);

        assertEquals(new PreviewSizeCalculator.Layout(
                        1280, 720, 720, 1280, 1280, 720, 270),
                PreviewSizeCalculator.containLayout(
                        1280, 720, source, source, 90, 0));
    }

    @Test void cameraOneLandscapeLeftCounterRotatesDisplay() {
        CameraResolution source = new CameraResolution(1280, 720);
        int outputRotation = CameraOrientation.relativeRotation(270, 270, true);

        assertEquals(new PreviewSizeCalculator.Layout(
                        782, 440, 440, 782, 782, 440, 90),
                PreviewSizeCalculator.containLayout(
                        782, 444, source, source, 270, outputRotation));
    }
}