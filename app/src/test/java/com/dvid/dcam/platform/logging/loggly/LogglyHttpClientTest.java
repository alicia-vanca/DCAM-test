package com.dvid.dcam.platform.logging.loggly;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogglyHttpClientTest {
    @Test
    void acceptsDocumentedLogglyAcknowledgment() {
        assertTrue(LogglyHttpClient.isAcknowledgedResponse(
                200, "{\"response\" : \"ok\"}"));
        assertTrue(LogglyHttpClient.isAcknowledgedResponse(
                200, "  { \n \"response\" : \"ok\" \n }  "));
    }

    @Test
    void rejectsUnconfirmedSuccessfulResponses() {
        assertFalse(LogglyHttpClient.isAcknowledgedResponse(200, ""));
        assertFalse(LogglyHttpClient.isAcknowledgedResponse(200, "<html>login</html>"));
        assertFalse(LogglyHttpClient.isAcknowledgedResponse(
                200, "{\"response\":\"error\"}"));
        assertFalse(LogglyHttpClient.isAcknowledgedResponse(
                202, "{\"response\":\"ok\"}"));
        assertFalse(LogglyHttpClient.isAcknowledgedResponse(
                204, "{\"response\":\"ok\"}"));
    }

    @Test
    void rejectsAcknowledgmentOnHttpFailure() {
        assertFalse(LogglyHttpClient.isAcknowledgedResponse(
                500, "{\"response\":\"ok\"}"));
    }
}
