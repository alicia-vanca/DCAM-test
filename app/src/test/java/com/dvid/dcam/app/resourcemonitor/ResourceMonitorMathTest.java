package com.dvid.dcam.app.resourcemonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ResourceMonitorMathTest {
    @Test void appCpuUsesAllLogicalCoresAsHundredPercent() {
        assertEquals(25.0, ResourceMonitorMath.cpuPercent(500L, 1_000L, 2), 0.001);
    }

    @Test void formatsCpuUsageAsCoresAndPercent() {
        assertEquals("2.0 cores", ResourceMonitorMath.formatCpuUsed(25.0, 8));
        assertEquals("25.0%", ResourceMonitorMath.formatPercent(25.0));
    }

    @Test void formatsMemoryAndRatio() {
        long mebibyte = 1024L * 1024L;
        assertEquals("144 MB", ResourceMonitorMath.formatBytes(144L * mebibyte));
        assertEquals("4.7%", ResourceMonitorMath.formatRatioPercent(
                144L * mebibyte, 3L * 1024L * mebibyte));
    }
}