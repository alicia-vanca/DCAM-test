package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import android.hardware.camera2.CameraCharacteristics;
import org.junit.jupiter.api.Test;

final class CameraOrientationTest {
    private static final int[] QUARTER_TURNS = {0, 90, 180, 270};
    private static final int[][] BACK_OUTPUT = {
            {0, 270, 180, 90},
            {90, 0, 270, 180},
            {180, 90, 0, 270},
            {270, 180, 90, 0}
    };
    private static final int[][] FRONT_OUTPUT = {
            {0, 90, 180, 270},
            {90, 180, 270, 0},
            {180, 270, 0, 90},
            {270, 0, 90, 180}
    };

    @Test void everySensorAndDisplayQuarterTurnUsesLensFacingEquation() {
        for (int sensorIndex = 0; sensorIndex < QUARTER_TURNS.length; sensorIndex++) {
            for (int displayIndex = 0; displayIndex < QUARTER_TURNS.length; displayIndex++) {
                int sensor = QUARTER_TURNS[sensorIndex];
                int display = QUARTER_TURNS[displayIndex];
                assertEquals(BACK_OUTPUT[sensorIndex][displayIndex],
                        CameraOrientation.relativeRotation(sensor, display, false));
                assertEquals(FRONT_OUTPUT[sensorIndex][displayIndex],
                        CameraOrientation.relativeRotation(sensor, display, true));
            }
        }
    }

    @Test void lensFacingLabelsStayStable() {
        assertEquals("back", CameraOrientation.lensFacingLabel(
                CameraCharacteristics.LENS_FACING_BACK));
        assertEquals("front", CameraOrientation.lensFacingLabel(
                CameraCharacteristics.LENS_FACING_FRONT));
        assertEquals("external", CameraOrientation.lensFacingLabel(
                CameraCharacteristics.LENS_FACING_EXTERNAL));
        assertEquals("unknown", CameraOrientation.lensFacingLabel(null));
        assertEquals("unknown:99", CameraOrientation.lensFacingLabel(99));
    }

    @Test void previewBufferRotationCounterRotatesEveryDisplayQuarterTurn() {
        for (int displayIndex = 0; displayIndex < QUARTER_TURNS.length; displayIndex++) {
            assertEquals(QUARTER_TURNS[(4 - displayIndex) % 4],
                    CameraOrientation.previewBufferRotation(QUARTER_TURNS[displayIndex]));
        }
    }
}