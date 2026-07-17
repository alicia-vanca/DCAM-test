package com.dvid.dcam.platform.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AudioForegroundOrderingTest {
    @Test void foregroundFailurePreventsRecorderStart() {
        List<String> calls = new ArrayList<>();

        boolean started = AndroidAudioRecorderImpl.startProtectedRecording(
                () -> {
                    calls.add("foreground");
                    return false;
                },
                () -> calls.add("recorder"));

        assertFalse(started);
        assertEquals(List.of("foreground"), calls);
    }

    @Test void foregroundProtectionStartsBeforeRecorder() {
        List<String> calls = new ArrayList<>();

        boolean started = AndroidAudioRecorderImpl.startProtectedRecording(
                () -> {
                    calls.add("foreground");
                    return true;
                },
                () -> calls.add("recorder"));

        assertTrue(started);
        assertEquals(List.of("foreground", "recorder"), calls);
    }
}
