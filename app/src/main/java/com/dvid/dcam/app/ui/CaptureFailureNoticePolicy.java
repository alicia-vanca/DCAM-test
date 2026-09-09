package com.dvid.dcam.app.ui;

import java.util.Objects;
import java.util.Optional;

/** Classifies capture failures into user-visible notice types. */
public final class CaptureFailureNoticePolicy {
    private static final String FINALIZATION_FAILURE_PREFIX = "Finalization failed:";
    private static final String STORAGE_FAILURE_PREFIX = "Storage failed: ";
    private static final String LOW_STORAGE_MARKER = "%1$s";

    public enum Kind {
        CAPTURE_FAILED,
        FINALIZATION_FAILED,
        LOW_STORAGE_RECORDING_BLOCKED,
        SD_CARD_UNAVAILABLE,
        STORAGE_UNAVAILABLE
    }

    public record Notice(Kind kind, String detail) {
        public Notice {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(detail, "detail");
        }
    }

    private CaptureFailureNoticePolicy() {}

    public static Optional<Notice> notice(
            String message,
            String sdCardUnavailableMessage,
            String lowStorageTemplate) {
        Objects.requireNonNull(sdCardUnavailableMessage, "sdCardUnavailableMessage");
        Objects.requireNonNull(lowStorageTemplate, "lowStorageTemplate");
        if (message == null) return Optional.empty();
        if (message.startsWith(FINALIZATION_FAILURE_PREFIX))
            return Optional.of(new Notice(Kind.FINALIZATION_FAILED, ""));

        Optional<String> lowStorageDetail = lowStorageDetail(message, lowStorageTemplate);
        if (lowStorageDetail.isPresent())
            return Optional.of(new Notice(Kind.LOW_STORAGE_RECORDING_BLOCKED,
                    lowStorageDetail.orElseThrow()));
        if (message.equals(STORAGE_FAILURE_PREFIX + sdCardUnavailableMessage))
            return Optional.of(new Notice(Kind.SD_CARD_UNAVAILABLE, ""));
        if (message.startsWith(STORAGE_FAILURE_PREFIX))
            return Optional.of(new Notice(Kind.STORAGE_UNAVAILABLE, ""));
        return Optional.of(new Notice(Kind.CAPTURE_FAILED, message));
    }

    private static Optional<String> lowStorageDetail(String message, String template) {
        int markerIndex = template.indexOf(LOW_STORAGE_MARKER);
        if (markerIndex < 0 || !message.startsWith(STORAGE_FAILURE_PREFIX))
            return Optional.empty();
        String payload = message.substring(STORAGE_FAILURE_PREFIX.length());
        boolean matches = payload.startsWith(template.substring(0, markerIndex))
                && payload.endsWith(template.substring(markerIndex + LOW_STORAGE_MARKER.length()));
        return matches ? Optional.of(payload) : Optional.empty();
    }
}
