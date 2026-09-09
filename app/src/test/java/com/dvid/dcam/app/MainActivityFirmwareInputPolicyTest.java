package com.dvid.dcam.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MainActivityFirmwareInputPolicyTest {
    @Test void acceptsInputWhenDcamIsResumed() {
        assertTrue(MainActivity.shouldAcceptFirmwareHardwareInput(true, true, false));
    }

    @Test void acceptsInputForScreenOffRecordingAndStopControls() {
        assertTrue(MainActivity.shouldAcceptFirmwareHardwareInput(false, false, true));
        assertTrue(MainActivity.shouldAcceptFirmwareHardwareInput(false, false, false));
    }

    @Test void ignoresInputFromAnotherInteractiveForegroundAppWhenIdle() {
        assertFalse(MainActivity.shouldAcceptFirmwareHardwareInput(false, true, false));
    }
}
