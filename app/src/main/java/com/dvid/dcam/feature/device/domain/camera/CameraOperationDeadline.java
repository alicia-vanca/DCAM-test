package com.dvid.dcam.feature.device.domain.camera;

public record CameraOperationDeadline(long startedAtMillis, long deadlineAtMillis) {
    public static final long CANDIDATE_TIMEOUT_MILLIS = 5_000L;

    public CameraOperationDeadline {
        if (startedAtMillis < 0) {
            throw new IllegalArgumentException("startedAtMillis must not be negative");
        }
        if (deadlineAtMillis <= startedAtMillis) {
            throw new IllegalArgumentException("deadline must be after start");
        }
    }

    public static CameraOperationDeadline forCandidate(long startedAtMillis) {
        return after(startedAtMillis, CANDIDATE_TIMEOUT_MILLIS);
    }

    public static CameraOperationDeadline after(long startedAtMillis, long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        try {
            return new CameraOperationDeadline(
                    startedAtMillis, Math.addExact(startedAtMillis, timeoutMillis));
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("deadline overflow", error);
        }
    }

    public long timeoutMillis() { return deadlineAtMillis - startedAtMillis; }

    public long remainingMillis(long nowMillis) {
        if (nowMillis <= startedAtMillis) return timeoutMillis();
        if (nowMillis >= deadlineAtMillis) return 0;
        return deadlineAtMillis - nowMillis;
    }

    public boolean isExpiredAt(long nowMillis) { return nowMillis >= deadlineAtMillis; }
}