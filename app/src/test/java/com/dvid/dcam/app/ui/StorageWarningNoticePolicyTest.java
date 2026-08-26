package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.dvid.dcam.feature.storage.domain.StorageWarningStatus;
import org.junit.jupiter.api.Test;

final class StorageWarningNoticePolicyTest {
    @Test void visibleWarningProducesWarningNotice() {
        StorageWarningNoticePolicy.Notice notice = StorageWarningNoticePolicy.notice(
                new StorageWarningStatus(true, false, 512L), "512 B free").orElseThrow();

        assertEquals("512 B free", notice.message());
        assertEquals(StorageWarningNoticePolicy.Severity.WARNING, notice.severity());
    }

    @Test void blockedRecordingProducesErrorNotice() {
        StorageWarningNoticePolicy.Notice notice = StorageWarningNoticePolicy.notice(
                new StorageWarningStatus(true, true, 128L), "128 B free").orElseThrow();

        assertEquals("128 B free", notice.message());
        assertEquals(StorageWarningNoticePolicy.Severity.ERROR, notice.severity());
    }

    @Test void hiddenWarningProducesNoNotice() {
        assertTrue(StorageWarningNoticePolicy.notice(
                new StorageWarningStatus(false, false, 10_000L), "10 KB free").isEmpty());
    }
}
