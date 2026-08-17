package com.dvid.dcam.app.resourcemonitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResourceMonitorDisplayTest {
    private static final long MEBIBYTE = 1024L * 1024L;

    @Test void displayMatchesRequestedLayout() {
        ResourceMonitorController.MonitorSnapshot snapshot =
                new ResourceMonitorController.MonitorSnapshot(
                        25.0,
                        new ResourceMonitorController.ProcessCpuResult(6.25),
                        8,
                        new ResourceMonitorController.DeviceMemory(
                                3L * 1024L * MEBIBYTE, 1536L * MEBIBYTE),
                        Map.of(
                                101, new ResourceMonitorController.ProcessSample(
                                        101, "com.dvid.dcam", 1_000L,
                                        64L * MEBIBYTE, 256L * MEBIBYTE),
                                202, new ResourceMonitorController.ProcessSample(
                                        202, "com.dvid.dcam:loggly", 2_000L,
                                        32L * MEBIBYTE, 128L * MEBIBYTE)),
                        Map.of(
                                101, new ResourceMonitorController.RunningProcess(
                                        "com.dvid.dcam", 101),
                                202, new ResourceMonitorController.RunningProcess(
                                        "com.dvid.dcam:loggly", 202)),
                        Map.of(101, 96L * MEBIBYTE, 202, 48L * MEBIBYTE),
                        new ResourceMonitorController.BatterySnapshot(
                                88, -185_000, "-0.2%/min"));

        String display = snapshot.display("com.dvid.dcam");

        assertEquals("---- CPU ----\n"
                + "Device: Used 2.0 cores (25.0%)\n"
                + "DCAM: Used 0.5 cores (6.3%)\n"
                + "---- RAM ----\n"
                + "Device: Total 3.0 GB | Used 1.5 GB (50.0%)\n"
                + "DCAM: Used 144 MB (4.7%)\n"
                + "• main: Used 96 MB (3.1%)\n"
                + "• loggly: Used 48 MB (1.6%)\n"
                + "---- JAVA HEAP ----\n"
                + "• main: Allowed 256 MB | Used 64 MB (25.0%)\n"
                + "• loggly: Allowed 128 MB | Used 32 MB (25.0%)\n"
                + "---- BATTERY ----\n"
                + "88% | -185 mA | -0.2%/min", display);
        String normalized = display.toLowerCase(Locale.ROOT);
        for (String forbidden : List.of(
                "resource monitor", "n/a", "partial", "pss", "metric", "collecting")) {
            assertFalse(normalized.contains(forbidden));
        }
    }

    @Test void appCpuUsesProcessesWithValidHistoryWhenChildAppears() {
        Map<Integer, ResourceMonitorController.ProcessSample> previous = Map.of(
                101, new ResourceMonitorController.ProcessSample(
                        101, "com.dvid.dcam", 1_000L, 0L, 1L));
        Map<Integer, ResourceMonitorController.ProcessSample> current = Map.of(
                101, new ResourceMonitorController.ProcessSample(
                        101, "com.dvid.dcam", 1_120L, 0L, 1L),
                202, new ResourceMonitorController.ProcessSample(
                        202, "com.dvid.dcam:loggly", 2_000L, 0L, 1L));

        ResourceMonitorController.ProcessCpuResult result =
                ResourceMonitorController.calculateAppCpu(1_000L, previous, current, 8);

        assertEquals(1.5, result.percent(), 0.001);
    }

    @Test void reporterSampleAddsLogglyMissingFromProcessDiscovery() {
        Map<Integer, ResourceMonitorController.RunningProcess> merged =
                ResourceMonitorController.mergeRunningProcesses(
                        Map.of(101, new ResourceMonitorController.RunningProcess(
                                "com.dvid.dcam", 101)),
                        Map.of(
                                101, new ResourceMonitorController.ProcessSample(
                                        101, "com.dvid.dcam", 1_000L, 0L, 1L),
                                202, new ResourceMonitorController.ProcessSample(
                                        202, "com.dvid.dcam:loggly", 2_000L, 0L, 1L)));

        assertEquals(new ResourceMonitorController.RunningProcess(
                "com.dvid.dcam:loggly", 202), merged.get(202));
    }

    @Test void avoidsJavaApisMissingFromSupportedAndroidRuntime() throws IOException {
        String controller = Files.readString(source("ResourceMonitorController.java"));
        String reporter = Files.readString(source("ResourceMonitorProcessReporter.java"));

        assertFalse(controller.contains("text.isEmpty()"));
        assertFalse(controller.contains(".isBlank()"));
        assertFalse(controller.contains("Map.of("));
        assertFalse(controller.contains("Map.copyOf("));
        assertFalse(reporter.contains(".isBlank()"));
    }


    @Test void processTransportUsesSystemMediatedPendingIntents() throws IOException {
        String controller = Files.readString(source("ResourceMonitorController.java"));
        String reporter = Files.readString(source("ResourceMonitorProcessReporter.java"));

        assertTrue(controller.contains("PendingIntent.getBroadcast"));
        assertTrue(reporter.contains("PendingIntent.getBroadcast"));
        assertFalse(controller.contains("sendBroadcast("));
        assertFalse(controller.contains("sendOrderedBroadcast("));
        assertFalse(reporter.contains("sendBroadcast("));
        assertFalse(reporter.contains("sendOrderedBroadcast("));
    }

    @Test void appRamEqualsDisplayedProcessPssAfterRounding() {
        ResourceMonitorController.MonitorSnapshot snapshot =
                new ResourceMonitorController.MonitorSnapshot(
                        -1.0,
                        new ResourceMonitorController.ProcessCpuResult(-1.0),
                        8,
                        new ResourceMonitorController.DeviceMemory(
                                1024L * MEBIBYTE, 512L * MEBIBYTE),
                        Map.of(
                                101, new ResourceMonitorController.ProcessSample(
                                        101, "com.dvid.dcam", 0L,
                                        64L * MEBIBYTE, 256L * MEBIBYTE),
                                202, new ResourceMonitorController.ProcessSample(
                                        202, "com.dvid.dcam:loggly", 0L,
                                        32L * MEBIBYTE, 256L * MEBIBYTE)),
                        Map.of(
                                101, new ResourceMonitorController.RunningProcess(
                                        "com.dvid.dcam", 101),
                                202, new ResourceMonitorController.RunningProcess(
                                        "com.dvid.dcam:loggly", 202)),
                        Map.of(
                                101, MEBIBYTE + MEBIBYTE / 2L,
                                202, MEBIBYTE + MEBIBYTE / 2L),
                        new ResourceMonitorController.BatterySnapshot(
                                -1, Integer.MIN_VALUE, null));

        String display = snapshot.display("com.dvid.dcam");

        assertTrue(display.contains("DCAM: Used 4 MB (0.4%)"));
        assertTrue(display.contains("• main: Used 2 MB (0.2%)"));
        assertTrue(display.contains("• loggly: Used 2 MB (0.2%)"));
        assertTrue(display.contains("---- JAVA HEAP ----\n"
                + "• main: Allowed 256 MB | Used 64 MB (25.0%)\n"
                + "• loggly: Allowed 256 MB | Used 32 MB (12.5%)"));
    }

    @Test void eachProcessReportsFreshPssAndKeepsHeapSeparate() throws IOException {
        String controller = Files.readString(source("ResourceMonitorController.java"));
        String reporter = Files.readString(source("ResourceMonitorProcessReporter.java"));

        assertTrue(controller.contains("Debug.getPss()"));
        assertTrue(reporter.contains("Debug.getPss()"));
        assertTrue(controller.contains("EXTRA_INCLUDE_PSS"));
        assertTrue(reporter.contains("EXTRA_PSS_BYTES"));
        assertTrue(controller.contains("PSS_INTERVAL_MS"));
        assertTrue(controller.contains("---- JAVA HEAP ----"));
        assertFalse(controller.contains("getProcessMemoryInfo("));
        assertFalse(controller.contains("getTotalPss("));
    }

    @Test void pssCacheUsesOnlyFreshProcessSamples() {
        Map<Integer, ResourceMonitorController.ProcessSample> samples = Map.of(
                101, new ResourceMonitorController.ProcessSample(
                        101, "com.dvid.dcam", 0L, 0L, 1L, 96L * MEBIBYTE),
                202, new ResourceMonitorController.ProcessSample(
                        202, "com.dvid.dcam:loggly", 0L, 0L, 1L, 48L * MEBIBYTE),
                303, new ResourceMonitorController.ProcessSample(
                        303, "com.dvid.dcam:other", 0L, 0L, 1L, -1L));

        assertEquals(Map.of(101, 96L * MEBIBYTE, 202, 48L * MEBIBYTE),
                ResourceMonitorController.pssFromSamples(samples));
    }

    @Test void unavailableValuesAreOmitted() {
        ResourceMonitorController.MonitorSnapshot snapshot =
                new ResourceMonitorController.MonitorSnapshot(
                        -1.0,
                        new ResourceMonitorController.ProcessCpuResult(-1.0),
                        8,
                        new ResourceMonitorController.DeviceMemory(-1L, -1L),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        new ResourceMonitorController.BatterySnapshot(
                                -1, Integer.MIN_VALUE, null));

        assertEquals("", snapshot.display("com.dvid.dcam"));
    }

    private static Path source(String fileName) {
        Path appSource = Path.of("app/src/main/java/com/dvid/dcam/app/resourcemonitor")
                .resolve(fileName);
        return Files.exists(appSource) ? appSource
                : Path.of("src/main/java/com/dvid/dcam/app/resourcemonitor").resolve(fileName);
    }
}