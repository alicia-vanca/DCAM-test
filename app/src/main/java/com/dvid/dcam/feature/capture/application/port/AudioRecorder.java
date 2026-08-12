package com.dvid.dcam.feature.capture.application.port;

/** Audio capability required by application workflows. */
public interface AudioRecorder {
    final class PreparationException extends RuntimeException {
        private final boolean retryable;
        private final String terminalMessage;

        private PreparationException(String message, boolean retryable, String terminalMessage,
                Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
            this.terminalMessage = terminalMessage;
        }

        public static PreparationException retryable(
                String message, String terminalMessage, Throwable cause) {
            return new PreparationException(message, true, terminalMessage, cause);
        }

        public static PreparationException unavailable(String message) {
            return new PreparationException(message, false, message, null);
        }

        public boolean retryable() { return retryable; }
        public String terminalMessage() { return terminalMessage; }
    }
    String toggle();
    boolean isRecording();
    default boolean hasPendingWork() { return isRecording(); }
    long recordingStartedAtMillis();
    void release();
}
