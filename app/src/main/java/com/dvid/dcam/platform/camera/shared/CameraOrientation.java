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
