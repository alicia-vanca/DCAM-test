package com.dvid.dcam.platform.camera.shared.egl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

final class EglTextureTransformTest {
    private static final float DELTA = 0.0001f;
    private static final float[] CROP_ONLY_FULL_FRAME = {
            1f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 1f, 0f, 1f
    };

    @Test void keepsMeasuredBackCameraCropOnlyTransform() {
        float[] actual = new float[16];

        EglTextureTransform.cropOnlyEncoderMatrix(CROP_ONLY_FULL_FRAME, actual);

        assertArrayEquals(CROP_ONLY_FULL_FRAME, actual, DELTA);
    }

    @Test void removesMeasuredFrontPreviewRotationAndMirrorFromEncoder() {
        float[] frontPreviewTransform = {
                0f, -1f, 0f, 0f,
                1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 1f, 0f, 1f
        };
        float[] actual = new float[16];

        EglTextureTransform.cropOnlyEncoderMatrix(frontPreviewTransform, actual);

        assertArrayEquals(CROP_ONLY_FULL_FRAME, actual, DELTA);
    }

    @Test void preservesMeasuredCropBoundsAcrossQuarterTurn() {
        float[] croppedQuarterTurn = {
                0f, -0.8f, 0f, 0f,
                0.6f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.2f, 0.9f, 0f, 1f
        };
        float[] expected = {
                0.6f, 0f, 0f, 0f,
                0f, -0.8f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0.2f, 0.9f, 0f, 1f
        };
        float[] actual = new float[16];

        EglTextureTransform.cropOnlyEncoderMatrix(croppedQuarterTurn, actual);

        assertArrayEquals(expected, actual, DELTA);
    }
}