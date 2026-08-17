package com.dvid.dcam.platform.camera.shared;

import android.hardware.camera2.CameraCharacteristics;
import android.media.ExifInterface;
import java.io.File;
import java.io.IOException;

public final class CameraOrientation {
    private CameraOrientation() {}

    public static int normalize(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized % 90 != 0) {
            throw new IllegalArgumentException("rotation must be a multiple of 90");
        }
        return normalized;
    }

    public static int relativeRotation(
            int sensorOrientationDegrees, int displayRotationDegrees, boolean frontFacing) {
        int sensor = normalize(sensorOrientationDegrees);
        int display = normalize(displayRotationDegrees);
        return normalize(sensor + (frontFacing ? display : -display));
    }

    public static int nearestQuarterTurn(int orientationDegrees) {
        if (orientationDegrees < 0 || orientationDegrees >= 360) {
            throw new IllegalArgumentException("orientation must be between 0 and 359");
        }
        return normalize(((orientationDegrees + 45) / 90) * 90);
    }

    public static int photoRotation(
            int sensorOrientationDegrees, int deviceOrientationDegrees, boolean frontFacing) {
        int sensor = normalize(sensorOrientationDegrees);
        int device = normalize(deviceOrientationDegrees);
        return normalize(sensor + (frontFacing ? -device : device));
    }

    public static int previewBufferRotation(int displayRotationDegrees) {
        return normalize(-displayRotationDegrees);
    }

    public static String lensFacingLabel(Integer value) {
        if (value == null) return "unknown";
        if (value == CameraCharacteristics.LENS_FACING_BACK) return "back";
        if (value == CameraCharacteristics.LENS_FACING_FRONT) return "front";
        if (value == CameraCharacteristics.LENS_FACING_EXTERNAL) return "external";
        return "unknown:" + value;
    }

    static void applyJpegOrientationMetadata(File file, int rotationDegrees)
            throws IOException {
        ExifInterface exif = new ExifInterface(file.getAbsolutePath());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                Integer.toString(exifOrientation(rotationDegrees)));
        exif.saveAttributes();
    }

    static int exifOrientation(int rotationDegrees) {
        return switch (normalize(rotationDegrees)) {
            case 0 -> ExifInterface.ORIENTATION_NORMAL;
            case 90 -> ExifInterface.ORIENTATION_ROTATE_90;
            case 180 -> ExifInterface.ORIENTATION_ROTATE_180;
            case 270 -> ExifInterface.ORIENTATION_ROTATE_270;
            default -> throw new IllegalStateException("unreachable rotation");
        };
    }

    public static String jpegOrientationMetadata(File file) {
        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());
            return "exifOrientationTag=" + exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        } catch (IOException | RuntimeException error) {
            return "unavailable:" + error.getClass().getSimpleName();
        }
    }
}
