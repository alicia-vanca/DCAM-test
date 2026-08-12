package com.dvid.dcam.platform.logging.loggly;

import android.util.Log;
import com.dvid.dcam.BuildConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** :loggly-only HTTP transport. No Room access or retry policy. */
final class LogglyHttpClient {
    private static final String TAG = "LogglyUpload";

    private LogglyHttpClient() { }

    static String send(String payload) {
        return send("inputs", payload, "application/json; charset=utf-8", "Loggly send failed");
    }

    static String sendBatch(List<String> payloads) {
        if (payloads.isEmpty()) return null;
        Log.i(TAG, "bulk send events=" + payloads.size());
        return send("bulk", bulkPayload(payloads), "text/plain; charset=utf-8",
                "Loggly bulk send failed");
    }

    static String bulkPayload(List<String> payloads) {
        return String.join("\n", payloads);
    }

    private static String send(
            String endpoint, String payload, String contentType, String failureMessage) {
        if (BuildConfig.LOGGLY_TOKEN.isBlank()) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    "https://logs-01.loggly.com/" + endpoint + "/"
                            + BuildConfig.LOGGLY_TOKEN + "/tag/dcam/")
                    .openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", contentType);
            connection.setDoOutput(true);
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int statusCode = connection.getResponseCode();
            String responseBody = readResponseBody(connection, statusCode);
            if (isAcknowledgedResponse(statusCode, responseBody)) return null;
            String errorMessage = failureMessage + " HTTP " + statusCode
                    + " response=" + responseSummary(responseBody);
            Log.w(TAG, errorMessage);
            return errorMessage;
        } catch (Exception error) {
            String errorMessage = failureMessage + ": " + error.getMessage();
            Log.w(TAG, errorMessage, error);
            return errorMessage;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static boolean isAcknowledgedResponse(int statusCode, String responseBody) {
        if (statusCode != HttpURLConnection.HTTP_OK || responseBody == null) return false;
        return "{\"response\":\"ok\"}".equals(responseBody.replaceAll("\\s+", ""));
    }

    private static String readResponseBody(HttpURLConnection connection, int statusCode)
            throws IOException {
        InputStream response = statusCode >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();
        if (response == null) return "";
        try (InputStream input = response;
             ByteArrayOutputStream body = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[512];
            int remaining = 4_096;
            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) break;
                body.write(buffer, 0, read);
                remaining -= read;
            }
            return new String(body.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String responseSummary(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "<empty>";
        String summary = responseBody.replaceAll("\\s+", " ").trim();
        return summary.length() <= 256 ? summary : summary.substring(0, 256);
    }
}