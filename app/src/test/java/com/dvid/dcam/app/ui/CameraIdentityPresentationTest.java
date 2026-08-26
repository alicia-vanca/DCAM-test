package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CameraIdentityPresentationTest {
    @Test void configuredSerialIsDisplayedAsCameraIdentity() {
        assertEquals("CAM ABC123", CameraIdentityPresentation.cameraLabel("ABC123", true));
    }

    @Test void unconfiguredSerialUsesPlaceholderIdentity() {
        assertEquals("CAM —", CameraIdentityPresentation.cameraLabel("", false));
    }
}
