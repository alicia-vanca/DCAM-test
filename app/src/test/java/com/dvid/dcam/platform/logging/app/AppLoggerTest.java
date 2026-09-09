package com.dvid.dcam.platform.logging.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.dvid.dcam.core.logging.domain.LogCategory;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
public final class AppLoggerTest {
        @Test
        public void localLogIsCompactJsonLines() throws Exception {
                String line = AppLogger.json("INFO", "message", null, "main", "Test");
                JSONObject parsed = new JSONObject(line);

                assertFalse(line.contains("\n"));
                assertFalse(line.contains("\r"));
                assertEquals("INFO", parsed.getString("level"));
                assertEquals("message", parsed.getString("message"));
                assertTrue(parsed.has("timestamp"));
        }

        @Test
        public void localLogNormalizesMissingFieldsAndOmitsStack() throws Exception {
                AppLogger.setDeviceSerial(null);
                JSONObject parsed = new JSONObject(
                                AppLogger.json("INFO", null, null, null, null));

                assertEquals("unknown", parsed.getString("thread"));
                assertEquals("unknown", parsed.getString("source"));
                assertEquals("unknown", parsed.getString("model"));
                assertEquals("unknown", parsed.getString("deviceSerial"));
                assertEquals("", parsed.getString("message"));
                assertFalse(parsed.has("stack"));
        }

        @Test
        public void payloadNormalizesBlankRequiredFieldsAndOmitsBlankInfoReasonCode() throws Exception {
                AppLogger.setDeviceSerial("  ");
                try {
                        JSONObject parsed = new JSONObject(AppLogger.json(
                                        LogCategory.APP, "  ", "\t", "INFO", " \t ", null, " ", "\n"));

                        assertEquals("unspecified", parsed.getString("eventName"));
                        assertFalse(parsed.has("reasonCode"));
                        assertEquals("INFO", parsed.getString("level"));
                        assertEquals("unknown", parsed.getString("thread"));
                        assertEquals("unknown", parsed.getString("source"));
                        assertEquals("unknown", parsed.getString("hardwareId"));
                        assertEquals("unknown", parsed.getString("model"));
                        assertEquals("", parsed.getString("message"));
                        assertEquals("unknown", parsed.getString("deviceSerial"));
                        assertFalse(parsed.has("stack"));
                } finally {
                        AppLogger.setDeviceSerial(null);
                }
        }

        @Test
        public void localLogEscapesNewlineWithoutOverEscaping() throws Exception {
                String line = AppLogger.json(
                                "INFO", "line 1\nline 2", null, "main", "Test");
                JSONObject parsed = new JSONObject(line);

                assertEquals("line 1\nline 2", parsed.getString("message"));
                assertTrue(line.contains("\"message\":\"line 1\\nline 2\""));
                assertFalse(line.contains("\"message\":\"line 1\\\\nline 2\""));
                assertFalse(line.contains("\n"));
                assertFalse(line.contains("\r"));
        }

        @Test
        public void localLogRoundTripsJsonControlCharacters() throws Exception {
                String message = "tab\tbackspace\bformfeed\fcontrol\u0001";
                String line = AppLogger.json("INFO", message, null, "main", "Test");
                JSONObject parsed = new JSONObject(line);

                assertEquals(message, parsed.getString("message"));
                assertFalse(line.contains("\t"));
                assertFalse(line.contains("\b"));
                assertFalse(line.contains("\f"));
                assertFalse(line.contains("\u0001"));
        }

        @Test
        public void explicitMaskingFollowsLengthPolicy() {
                assertNull(AppLogger.maskExplicit(null));
                assertEquals("", AppLogger.maskExplicit(""));
                assertEquals("*", AppLogger.maskExplicit("A"));
                assertEquals("****", AppLogger.maskExplicit("ABCD"));
                assertEquals("***DE", AppLogger.maskExplicit("ABCDE"));
                assertEquals("******GH", AppLogger.maskExplicit("ABCDEFGH"));
                assertEquals("AB***FGHI", AppLogger.maskExplicit("ABCDEFGHI"));
        }

        @Test
        public void localLogMasksSerialInMessageAndStack() throws Exception {
                RuntimeException error = new RuntimeException("deviceSerial=ABCDEFGHIJ");
                String line = AppLogger.json(
                                "ERROR", "serial=ABCDEFGH", error, "main", "Test");
                JSONObject parsed = new JSONObject(line);

                assertEquals("serial=******GH", parsed.getString("message"));
                assertTrue(parsed.getString("stack").contains("deviceSerial=AB****GHIJ"));
                assertFalse(line.contains("ABCDEFGH"));
                assertFalse(line.contains("ABCDEFGHIJ"));
        }

        @Test
        public void localLogSanitizesSensitiveMessageData() throws Exception {
                String line = AppLogger.json(
                                "WARN", "token=secret123 password=hunter2", null, "main", "Test");
                JSONObject parsed = new JSONObject(line);

                assertEquals("token=[REDACTED] password=[REDACTED]",
                                parsed.getString("message"));
                assertFalse(line.contains("secret123"));
                assertFalse(line.contains("hunter2"));
        }

        @Test
        public void localLogSerializesStackAsValidSingleLineJson() throws Exception {
                RuntimeException error = new RuntimeException("failure");
                String line = AppLogger.json("ERROR", "failed", error, "main", "Test");
                JSONObject parsed = new JSONObject(line);

                assertTrue(parsed.getString("stack").contains("java.lang.RuntimeException: failure"));
                assertTrue(parsed.getString("stack").contains("\tat "));
                assertFalse(line.contains("\n"));
                assertFalse(line.contains("\r"));
                assertFalse(line.contains("\t"));
        }

        @Test
        public void payloadContainsOnlyMaskedDeviceIdentifierFields() throws Exception {
                AppLogger.setDeviceSerial("ABC123");
                try {
                        JSONObject parsed = new JSONObject(
                                        AppLogger.json("INFO", "message", null, "main", "Test"));

                        assertEquals("unknown", parsed.getString("hardwareId"));
                        assertEquals("****23", parsed.getString("deviceSerial"));
                        assertFalse(parsed.toString().contains("ABC123"));
                } finally {
                        AppLogger.setDeviceSerial(null);
                }
        }

        @Test
        public void payloadContainsLevel() throws Exception {
                assertEquals("INFO", parsePayload("INFO", "message").getString("level"));
                assertEquals("DEBUG", parsePayload("DEBUG", "message").getString("level"));
                assertEquals("WARN", parsePayload("WARN", "message").getString("level"));
                assertEquals("ERROR", parsePayload("ERROR", "message").getString("level"));
        }

        @Test
        public void payloadContainsCategory() throws Exception {
                JSONObject parsed = new JSONObject(AppLogger.json(
                                LogCategory.CAMERA, "INFO", "message", null, "main", "Test"));

                assertEquals("CAMERA", parsed.getString("category"));
        }

        @Test
        public void payloadContainsEventNameAndReasonCode() throws Exception {
                JSONObject parsed = new JSONObject(AppLogger.json(
                                LogCategory.STORAGE, "capture_start_rejected", "STORAGE_UNAVAILABLE",
                                "WARN", "message", null, "main", "Test"));

                assertEquals("capture_start_rejected", parsed.getString("eventName"));
                assertEquals("STORAGE_UNAVAILABLE", parsed.getString("reasonCode"));
        }

        @Test
        public void payloadNormalizesMissingReasonCodeForWarningsAndErrors() throws Exception {
                for (String level : new String[] {"WARN", "ERROR"}) {
                        for (String reasonCode : new String[] {null, " \t "}) {
                                JSONObject parsed = new JSONObject(AppLogger.json(
                                                LogCategory.APP, "event", reasonCode, level,
                                                "message", null, "main", "Test"));

                                assertEquals("unspecified", parsed.getString("reasonCode"));
                        }
                }
        }

        @Test
        public void payloadOmitsGenericInfoReasonCodeButPreservesExplicitValue() throws Exception {
                JSONObject missing = new JSONObject(AppLogger.json(
                                LogCategory.APP, "event", null, "INFO", "message", null,
                                "main", "Test"));
                JSONObject generic = new JSONObject(AppLogger.json(
                                LogCategory.APP, "event", "unspecified", "INFO", "message", null,
                                "main", "Test"));
                JSONObject explicit = new JSONObject(AppLogger.json(
                                LogCategory.APP, "event", "USER_ACTION", "INFO", "message", null,
                                "main", "Test"));

                assertFalse(missing.has("reasonCode"));
                assertFalse(generic.has("reasonCode"));
                assertEquals("USER_ACTION", explicit.getString("reasonCode"));
        }

        @Test
        public void payloadUsesUnspecifiedCategoryWhenMissing() throws Exception {
                JSONObject parsed = new JSONObject(AppLogger.json(
                                null, "INFO", "message", null, "main", "Test"));

                assertEquals("UNSPECIFIED", parsed.getString("category"));
        }

        @Test
        public void payloadUsesCanonicalSchemaFields() throws Exception {
                JSONObject payload = new JSONObject(AppLogger.json(
                                LogCategory.APP, "INFO", "message", null, "main", "Test"));

                assertTrue(payload.getString("timestamp").endsWith("+07:00"));
                assertEquals(1, payload.getInt("schemaVersion"));
                assertFalse(payload.has("schema_version"));
                assertEquals("DCAM", payload.getString("app"));
                assertTrue(payload.has("eventName"));
                assertFalse(payload.has("reasonCode"));
        }

        @Test
        public void payloadNormalizesDeviceSerialWhenUnsetOrBlank() throws Exception {
                AppLogger.setDeviceSerial("  ");

                assertEquals("unknown",
                                new JSONObject(AppLogger.json("message", null, "main", "Test"))
                                                .getString("deviceSerial"));
        }

        @Test
        public void payloadMasksDeviceSerial() throws Exception {
                try {
                        AppLogger.setDeviceSerial(" ABC123 ");

                        assertEquals("****23",
                                        new JSONObject(AppLogger.json("message", null, "main", "Test"))
                                                        .getString("deviceSerial"));
                } finally {
                        AppLogger.setDeviceSerial(null);
                }
        }

        @Test
        public void payloadMasksHardwareId() throws Exception {
                java.lang.reflect.Field field = AppLogger.class.getDeclaredField("hardwareId");
                field.setAccessible(true);
                String previousHardwareId = (String) field.get(null);
                try {
                        field.set(null, "HARDWARE123");

                        JSONObject parsed = new JSONObject(
                                        AppLogger.json("message", null, "main", "Test"));

                        assertEquals("HA*****E123", parsed.getString("hardwareId"));
                } finally {
                        field.set(null, previousHardwareId);
                }
        }

        @Test
        public void payloadOmitsCamId() throws Exception {
                assertFalse(new JSONObject(AppLogger.json("message", null, "main", "Test"))
                                .has("camId"));
        }

        @Test
        public void payloadContainsOnlyCallerSuppliedMessage() throws Exception {
                JSONObject parsed = new JSONObject(AppLogger.json(
                                "Recording started\nCamera \"front\"",
                                null,
                                "capture-thread",
                                "CaptureController"));

                assertEquals("Recording started\nCamera \"front\"", parsed.getString("message"));
                assertTrue(parsed.getString("timestamp").endsWith("+07:00"));
                assertEquals("capture-thread", parsed.getString("thread"));
                assertEquals("CaptureController", parsed.getString("source"));
        }

        @Test
        public void payloadPreservesSourceValue() throws Exception {
                String source = "  Camera   Controller  ";

                JSONObject parsed = new JSONObject(
                                AppLogger.json("message", null, "main", source));

                assertEquals(source, parsed.getString("source"));
        }

        @Test
        public void payloadOmitsStackWithoutThrowable() throws Exception {
                assertFalse(parsePayload("ERROR", "Camera error").has("stack"));
        }

        @Test
        public void payloadContainsDiagnosticStackTrace() throws Exception {
                RuntimeException error = new RuntimeException("KIOSK_TRACE blocked");
                error.setStackTrace(new StackTraceElement[] {
                                new StackTraceElement(
                                                "com.dvid.dcam.platform.device.DcamKioskController",
                                                "grantRuntimePermissions",
                                                "DcamKioskController.java",
                                                115)
                });

                JSONObject parsed = new JSONObject(AppLogger.json(
                                "KIOSK_TRACE slow policy call",
                                error,
                                "main",
                                "DcamKioskController"));

                assertTrue(parsed.getString("stack")
                                .contains("java.lang.RuntimeException: KIOSK_TRACE blocked"));
                assertTrue(parsed.getString("stack")
                                .contains("DcamKioskController.grantRuntimePermissions"));
        }

        @Test
        public void callerSourceSkipsAppLoggerFrames() {
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
        public void crashSourceUsesThrowingClass() {
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
        public void crashPersistenceWaitsForRoomWrite() {
                assertTrue(AppLogger.isCrashPersistence("ERROR", "Crash on dcam-media-finalization"));
                assertFalse(AppLogger.isCrashPersistence("ERROR", "Audio stop failed"));
                assertFalse(AppLogger.isCrashPersistence("INFO", "Crash on dcam-media-finalization"));
                assertFalse(AppLogger.isCrashPersistence("ERROR", null));
        }

        @Test
        public void fatalCrashSpoolsOnlyWhenRoomWriteFails() {
                assertFalse(AppLogger.shouldSpoolCrash(
                                "ERROR", "Crash on dcam-media-finalization", true, true));
                assertTrue(AppLogger.shouldSpoolCrash(
                                "ERROR", "Crash on dcam-media-finalization", true, false));
                assertFalse(AppLogger.shouldSpoolCrash(
                                "ERROR", "Crash on dcam-media-finalization", false, false));
                assertFalse(AppLogger.shouldSpoolCrash(
                                "ERROR", "Audio stop failed", true, false));
        }

        private static JSONObject parsePayload(String level, String message) throws Exception {
                return new JSONObject(AppLogger.json(level, message, null, "main", "Test"));
        }
}
