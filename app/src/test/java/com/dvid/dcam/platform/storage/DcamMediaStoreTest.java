package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

public class DcamMediaStoreTest {
    @Test public void buildsRelativePathAndMimeType() {
        LocalDateTime at = LocalDateTime.of(2026, 6, 19, 10, 3, 24);
        assertEquals("DCIM/Media/IMP/2026-06-19", DcamMediaStore.relativePath(DcamFileType.SOS, at));
        assertEquals("video/mp4", DcamFileType.SOS.getMimeType());
    }
}
