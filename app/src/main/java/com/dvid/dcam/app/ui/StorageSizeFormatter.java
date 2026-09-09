package com.dvid.dcam.app.ui;

import java.util.Locale;

/** Formats storage byte counts with the existing binary-GB presentation. */
public final class StorageSizeFormatter {
    private static final double BYTES_PER_GIB = 1024d * 1024d * 1024d;

    private StorageSizeFormatter() {}

    public static String gibibytes(long bytes) {
        return String.format(Locale.US, "%.1f GB", bytes / BYTES_PER_GIB);
    }
}
