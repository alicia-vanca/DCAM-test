package com.dvid.dcam.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dvid.dcam.feature.capture.domain.RecordingMode;
import org.junit.jupiter.api.Test;

final class AppCompositionResetTest {
    @Test void resetIsRejectedWhileAudioRecordingIsActive() {
        assertThrows(IllegalStateException.class,
                () -> AppComposition.requireCaptureIdle(RecordingMode.IDLE, true));
    }

    @Test void resetIsRejectedWhileVideoOrSosRecordingIsActive() {
        assertThrows(IllegalStateException.class,
                () -> AppComposition.requireCaptureIdle(RecordingMode.VIDEO, false));
        assertThrows(IllegalStateException.class,
                () -> AppComposition.requireCaptureIdle(RecordingMode.SOS, false));
    }

    @Test void resetIsAllowedOnlyWhenAllCaptureIsIdle() {
        assertDoesNotThrow(() -> AppComposition.requireCaptureIdle(RecordingMode.IDLE, false));
    }
}
