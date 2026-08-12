package com.dvid.dcam.platform.camera.shared.outputsharing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class NativeFrameTargetPolicyTest {
    @Test void externalPreviewContinuesWhileEncoderOwnsRepeatingRequest() {
        NativeFrameTargetPolicy.Decision decision =
                NativeFrameTargetPolicy.repeating(true);

        assertTrue(decision.includePreview());
        assertTrue(decision.includeEncoder());
        assertFalse(decision.previewSuppressed());
    }

    @Test void externalPreviewYieldsSingleJpegFrameWhileEncoderActive() {
        NativeFrameTargetPolicy.Decision decision =
                NativeFrameTargetPolicy.jpeg(true, true);

        assertFalse(decision.includePreview());
        assertTrue(decision.includeEncoder());
        assertTrue(decision.previewSuppressed());
    }

    @Test void headlessJpegContinuesWithEncoder() {
        NativeFrameTargetPolicy.Decision decision =
                NativeFrameTargetPolicy.jpeg(false, true);

        assertTrue(decision.includePreview());
        assertTrue(decision.includeEncoder());
        assertFalse(decision.previewSuppressed());
    }
}