package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

public class DcamFileNameTest {
    @Test public void buildsSosEncryptedName() {
        assertEquals("DCAM_CAM001_000000_20260619_100324_IMP_enc.mp4",
                DcamFileName.build(DcamFileType.IMP, "CAM001", "000000",
                        LocalDateTime.of(2026, 6, 19, 10, 3, 24), true));
    }

    @Test public void ignoresSubsecondPrecisionForEveryType() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 4, 10, 3, 24, 100_000_000);
        LocalDateTime second = LocalDateTime.of(2026, 8, 4, 10, 3, 24, 900_000_000);

        for (DcamFileType type : DcamFileType.values()) {
            assertEquals(
                    DcamFileName.build(type, "CAM001", "000000", first, false),
                    DcamFileName.build(type, "CAM001", "000000", second, false));
        }
    }

    @Test public void keepsDifferentTypesDistinctWithinSameSecond() {
        LocalDateTime at = LocalDateTime.of(2026, 8, 4, 10, 3, 24, 500_000_000);

        assertNotEquals(
                DcamFileName.build(DcamFileType.VIDEO, "CAM001", "000000", at, false),
                DcamFileName.build(DcamFileType.IMAGE, "CAM001", "000000", at, false));
    }
}