package com.dvid.dcam.app.resourcemonitor;

import java.util.Locale;

public final class ResourceMonitorMath {
    private ResourceMonitorMath() {
    }

    public static double cpuPercent(long cpuDeltaMs, long elapsedMs, int logicalCoreCount) {
        if (cpuDeltaMs < 0L || elapsedMs <= 0L || logicalCoreCount <= 0) return -1.0;
        double percent = 100.0 * cpuDeltaMs / (elapsedMs * (double) logicalCoreCount);
        return Math.max(0.0, Math.min(100.0, percent));
    }


    public static String formatBytes(long bytes) {
        if (bytes < 0L) return "";
        double megabytes = bytes / (1024.0 * 1024.0);
        if (megabytes >= 1024.0) return String.format(Locale.ROOT, "%.1f GB", megabytes / 1024.0);
        return String.format(Locale.ROOT, "%.0f MB", megabytes);
    }

    public static String formatPercent(double percent) {
        return percent < 0.0 ? "" : String.format(Locale.ROOT, "%.1f%%", percent);
    }

    public static String formatCpuUsed(double percent, int logicalCoreCount) {
        if (percent < 0.0 || logicalCoreCount <= 0) return "";
        return String.format(Locale.ROOT, "%.1f cores",
                percent * logicalCoreCount / 100.0);
    }

    public static String formatRatioPercent(long usedBytes, long totalBytes) {
        if (usedBytes < 0L || totalBytes <= 0L) return "";
        return formatPercent(100.0 * usedBytes / totalBytes);
    }

    public static String formatIntegerPercent(int percent) {
        return percent < 0 ? "" : percent + "%";
    }
}