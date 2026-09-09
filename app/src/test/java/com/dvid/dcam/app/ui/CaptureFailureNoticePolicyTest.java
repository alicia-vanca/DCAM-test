package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CaptureFailureNoticePolicyTest {
    private static final String SD_CARD_UNAVAILABLE = "SD card unavailable.";
    private static final String LOW_STORAGE_TEMPLATE = "Low storage: %1$s";

    @Test void sdCardFailureSelectsDedicatedNotice() {
        CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                "Storage failed: " + SD_CARD_UNAVAILABLE,
                SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).orElseThrow();

        assertEquals(CaptureFailureNoticePolicy.Kind.SD_CARD_UNAVAILABLE, notice.kind());
    }

    @Test void otherStorageFailureUsesGenericNotice() {
        CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                "Storage failed: media directory unavailable",
                SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).orElseThrow();

        assertEquals(CaptureFailureNoticePolicy.Kind.STORAGE_UNAVAILABLE, notice.kind());
    }

    @Test void storageFailureIsClassifiedWithoutPreviousUiState() {
        CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                "Storage failed: Capture storage unavailable",
                SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).orElseThrow();

        assertEquals(CaptureFailureNoticePolicy.Kind.STORAGE_UNAVAILABLE, notice.kind());
    }

    @Test void diagnosticTextDoesNotClassifyStorageFailure() {
        for (String message : List.of(
                "CAPTURE_PHOTO failed: photo_capture:jpeg_write:FileNotFoundException",
                "CAPTURE_PHOTO failed: jpeg_orientation_metadata:IOException")) {
            CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                    message, SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).orElseThrow();

            assertEquals(CaptureFailureNoticePolicy.Kind.CAPTURE_FAILED, notice.kind());
            assertEquals(message, notice.detail());
        }
    }

    @Test void otherCaptureFailurePreservesFailureDetail() {
        String message = "CAPTURE_PHOTO failed: capture_failed:reason=3";

        CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                message, SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).orElseThrow();

        assertEquals(CaptureFailureNoticePolicy.Kind.CAPTURE_FAILED, notice.kind());
        assertEquals(message, notice.detail());
    }

    @Test void lowStorageFailurePreservesUserMessageDetail() {
        CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                "Storage failed: Low storage: 128 MB",
                SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).orElseThrow();

        assertEquals(CaptureFailureNoticePolicy.Kind.LOW_STORAGE_RECORDING_BLOCKED, notice.kind());
        assertEquals("Low storage: 128 MB", notice.detail());
    }

    @Test void identicalFailuresAreClassifiedIndependently() {
        String message = "Storage failed: Capture storage unavailable";

        assertEquals(CaptureFailureNoticePolicy.Kind.STORAGE_UNAVAILABLE,
                CaptureFailureNoticePolicy.notice(
                        message, SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE)
                        .orElseThrow().kind());
        assertEquals(CaptureFailureNoticePolicy.Kind.STORAGE_UNAVAILABLE,
                CaptureFailureNoticePolicy.notice(
                        message, SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE)
                        .orElseThrow().kind());
    }

    @Test void missingFailureProducesNoNotice() {
        assertTrue(CaptureFailureNoticePolicy.notice(
                null, SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).isEmpty());
    }
}
