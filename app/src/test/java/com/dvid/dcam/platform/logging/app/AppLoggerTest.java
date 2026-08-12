package com.dvid.dcam.platform.logging.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppLoggerTest {
        @Test
        void logglyPayloadContainsLevel() {
                assertTrue(AppLogger.json("INFO", "message", null, "main", "Test")
                                .contains("\"level\":\"INFO\""));
                assertTrue(AppLogger.json("DEBUG", "message", null, "main", "Test")
                                .contains("\"level\":\"DEBUG\""));
                assertTrue(AppLogger.json("WARN", "message", null, "main", "Test")
                                .contains("\"level\":\"WARN\""));
                assertTrue(AppLogger.json("ERROR", "message", null, "main", "Test")
                                .contains("\"level\":\"ERROR\""));
        }

        @Test
        void logglyPayloadContainsNullDeviceSerialWhenUnset() {
                AppLogger.setDeviceSerial(null);

                assertTrue(AppLogger.json("message", null, "main", "Test")
                                .contains("\"deviceSerial\":null"));
        }

        @Test
        void logglyPayloadContainsDeviceSerial() {
                try {
                        AppLogger.setDeviceSerial(" ABC123 ");

                        assertTrue(AppLogger.json("message", null, "main", "Test")
                                        .contains("\"deviceSerial\":\"ABC123\""));
                } finally {
                        AppLogger.setDeviceSerial(null);
                }
        }

        @Test
        void logglyPayloadOmitsCamId() {
                assertFalse(AppLogger.json("message", null, "main", "Test")
                                .contains("camId"));
        }

        @Test
        void logglyMessageContainsOnlyCallerSuppliedMessage() {
                String payload = AppLogger.json(
                                "Recording started\nCamera \"front\"",
                                null,
                                "capture-thread",
                                "CaptureController");

                assertTrue(payload.contains("\"message\":\"Recording started\\nCamera \\\"front\\\"\""));
                assertTrue(payload.contains("\"timestamp\":\""));
                assertTrue(payload.contains("+07:00\""));
                assertFalse(payload.contains("\"message\":\"20"));
                assertFalse(payload.contains(" thread=\\\"capture-thread\\\""));
                assertFalse(payload.contains(" source=CaptureController"));
        }

        @Test
        void logglyPayloadOmitsStackWithoutThrowable() {
                assertFalse(AppLogger.json("ERROR", "Camera error", null, "main", "Camera")
                                .contains("\"stack\""));
        }

        @Test
        void logglyPayloadContainsDiagnosticStackTrace() {
                RuntimeException error = new RuntimeException("KIOSK_TRACE blocked");
                error.setStackTrace(new StackTraceElement[] {
                                new StackTraceElement(
                                                "com.dvid.dcam.platform.device.DcamKioskController",
                                                "grantRuntimePermissions",
                                                "DcamKioskController.java",
                                                115)
                });

                String payload = AppLogger.json(
                                "KIOSK_TRACE slow policy call",
                                error,
                                "main",
                                "DcamKioskController");

                assertTrue(payload.contains("\"stack\":\"java.lang.RuntimeException: KIOSK_TRACE blocked"));
                assertTrue(payload.contains("DcamKioskController.grantRuntimePermissions"));
        }

        @Test
        void callerSourceSkipsAppLoggerFrames() {
                String source = AppLogger.callerClass(new StackTraceElement[] {
                                new StackTraceElement("dalvik.system.VMStack", "getThreadStackTrace", "VMStack.java",
                                                0),
                                new StackTraceElement("java.lang.Thread", "getStackTrace", "Thread.java", 0),
                                new StackTraceElement(AppLogger.class.getName(), "callerClass", "AppLogger.java", 0),
                                new StackTraceElement(AppLogger.class.getName(), "info", "AppLogger.java", 92),
                                new StackTraceElement(
                                                "com.dvid.dcam.platform.device.DcamKioskController",
                                                "applyActiveKioskPolicy",
                                                "DcamKioskController.java",
                                                47)
                });

                assertEquals("DcamKioskController", source);
        }

        @Test
        void crashSourceUsesThrowingClass() {
                RuntimeException error = new RuntimeException("boom");
                error.setStackTrace(new StackTraceElement[] {
                                new StackTraceElement(
                                                "com.dvid.dcam.app.devmode.CameraDevModeActivity",
                                                "requestMissingPermissions",
                                                "CameraDevModeActivity.java",
                                                229)
                });

                assertEquals("CameraDevModeActivity", AppLogger.crashSource(error));
                assertEquals("UncaughtException", AppLogger.crashSource(null));
        }

        @Test
        void crashPersistenceWaitsForRoomWrite() {
                assertTrue(AppLogger.isCrashPersistence("ERROR", "Crash on dcam-media-finalization"));
                assertFalse(AppLogger.isCrashPersistence("ERROR", "Audio stop failed"));
                assertFalse(AppLogger.isCrashPersistence("INFO", "Crash on dcam-media-finalization"));
                assertFalse(AppLogger.isCrashPersistence("ERROR", null));
        }

        @Test
        void fatalCrashSpoolsOnlyWhenRoomWriteFails() {
                assertFalse(AppLogger.shouldSpoolCrash(
                                "ERROR", "Crash on dcam-media-finalization", true, true));
                assertTrue(AppLogger.shouldSpoolCrash(
                                "ERROR", "Crash on dcam-media-finalization", true, false));
                assertFalse(AppLogger.shouldSpoolCrash(
                                "ERROR", "Crash on dcam-media-finalization", false, false));
                assertFalse(AppLogger.shouldSpoolCrash(
                                "ERROR", "Audio stop failed", true, false));
        }
}
