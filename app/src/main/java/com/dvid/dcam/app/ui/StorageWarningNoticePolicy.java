package com.dvid.dcam.app.ui;

import com.dvid.dcam.feature.storage.domain.StorageWarningStatus;
import java.util.Objects;
import java.util.Optional;

/** Chooses the user-visible notice for the current storage warning status. */
public final class StorageWarningNoticePolicy {
    public enum Severity { WARNING, ERROR }

    public record Notice(String message, Severity severity) {
        public Notice {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(severity, "severity");
        }
    }

    private StorageWarningNoticePolicy() {}

    public static Optional<Notice> notice(StorageWarningStatus warning, String message) {
        Objects.requireNonNull(warning, "warning");
        Objects.requireNonNull(message, "message");
        if (!warning.isVisible()) return Optional.empty();
        Severity severity = warning.isRecordingBlocked() ? Severity.ERROR : Severity.WARNING;
        return Optional.of(new Notice(message, severity));
    }
}
