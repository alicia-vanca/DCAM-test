package com.dvid.dcam.platform.camera.shared.egl;

import java.util.Arrays;

final class EglTextureTransform {
    private static final int MATRIX_SIZE = 16;
    private static final float AXIS_EPSILON = 0.0001f;

    private EglTextureTransform() {}

    static void cropOnlyEncoderMatrix(float[] source, float[] destination) {
        if (source == null || source.length < MATRIX_SIZE
                || destination == null || destination.length < MATRIX_SIZE) {
            throw new IllegalArgumentException("texture matrices must contain 16 values");
        }
        boolean straight = nearZero(source[1]) && nearZero(source[4]);
        boolean quarterTurn = nearZero(source[0]) && nearZero(source[5]);
        if (!straight && !quarterTurn) {
            throw new IllegalArgumentException("texture transform must use right-angle axes");
        }

        float x00 = sampleX(source, 0f, 0f);
        float x10 = sampleX(source, 1f, 0f);
        float x01 = sampleX(source, 0f, 1f);
        float x11 = sampleX(source, 1f, 1f);
        float y00 = sampleY(source, 0f, 0f);
        float y10 = sampleY(source, 1f, 0f);
        float y01 = sampleY(source, 0f, 1f);
        float y11 = sampleY(source, 1f, 1f);
        float minX = Math.min(Math.min(x00, x10), Math.min(x01, x11));
        float maxX = Math.max(Math.max(x00, x10), Math.max(x01, x11));
        float minY = Math.min(Math.min(y00, y10), Math.min(y01, y11));
        float maxY = Math.max(Math.max(y00, y10), Math.max(y01, y11));
        if (!Float.isFinite(minX) || !Float.isFinite(maxX)
                || !Float.isFinite(minY) || !Float.isFinite(maxY)
                || maxX <= minX || maxY <= minY) {
            throw new IllegalArgumentException("texture transform has invalid crop bounds");
        }

        Arrays.fill(destination, 0f);
        destination[0] = maxX - minX;
        destination[5] = minY - maxY;
        destination[10] = 1f;
        destination[12] = minX;
        destination[13] = maxY;
        destination[15] = 1f;
    }

    private static float sampleX(float[] matrix, float x, float y) {
        return matrix[0] * x + matrix[4] * y + matrix[12];
    }

    private static float sampleY(float[] matrix, float x, float y) {
        return matrix[1] * x + matrix[5] * y + matrix[13];
    }

    private static boolean nearZero(float value) {
        return Math.abs(value) <= AXIS_EPSILON;
    }
}