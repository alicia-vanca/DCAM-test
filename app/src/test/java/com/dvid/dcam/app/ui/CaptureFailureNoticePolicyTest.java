package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CaptureFailureNoticePolicyTest {
    private static final String SD_CARD_UNAVAILABLE = "SD card unavailable.";
    private static final String LOW_STORAGE_TEMPLATE = "Low storage: %1$s";

    @Test void sdCardFailureSelectsDedicatedNotice() {
        CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                "previous", "Storage failed: " + SD_CARD_UNAVAILABLE,
                SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).orElseThrow();

        assertEquals(CaptureFailureNoticePolicy.Kind.SD_CARD_UNAVAILABLE, notice.kind());
    }

    @Test void otherStorageFailureUsesGenericNotice() {
        CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                "previous", "Storage failed: media directory unavailable",
                SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).orElseThrow();

        assertEquals(CaptureFailureNoticePolicy.Kind.STORAGE_UNAVAILABLE, notice.kind());
    }

    @Test void lowStorageFailurePreservesUserMessageDetail() {
        CaptureFailureNoticePolicy.Notice notice = CaptureFailureNoticePolicy.notice(
                "previous", "Storage failed: Low storage: 128 MB",
                SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).orElseThrow();

        assertEquals(CaptureFailureNoticePolicy.Kind.LOW_STORAGE_RECORDING_BLOCKED, notice.kind());
        assertEquals("Low storage: 128 MB", notice.detail());
    }

    @Test void repeatedOrMissingFailureProducesNoNotice() {
        assertTrue(CaptureFailureNoticePolicy.notice(
                "same", "same", SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).isEmpty());
        assertTrue(CaptureFailureNoticePolicy.notice(
                null, "Storage failed: " + SD_CARD_UNAVAILABLE,
                SD_CARD_UNAVAILABLE, LOW_STORAGE_TEMPLATE).isEmpty());
    }
}
