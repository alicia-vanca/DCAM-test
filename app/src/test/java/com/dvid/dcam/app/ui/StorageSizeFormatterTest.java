package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class StorageSizeFormatterTest {
    @Test void preservesExistingBinaryGbPresentation() {
        assertEquals("0.0 GB", StorageSizeFormatter.gibibytes(0L));
        assertEquals("1.0 GB", StorageSizeFormatter.gibibytes(1024L * 1024L * 1024L));
        assertEquals("1.5 GB", StorageSizeFormatter.gibibytes(1536L * 1024L * 1024L));
    }
}
