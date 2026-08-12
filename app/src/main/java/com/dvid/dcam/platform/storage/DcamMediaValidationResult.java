package com.dvid.dcam.platform.storage;

import java.util.Objects;

record DcamMediaValidationResult(boolean playable, Failure failure, String detail) {
    enum Failure { NONE, NON_POSITIVE_DURATION, OTHER }

    DcamMediaValidationResult {
        failure = Objects.requireNonNull(failure, "failure");
        detail = Objects.requireNonNull(detail, "detail").trim();
        if (detail.isEmpty()) throw new IllegalArgumentException("detail is required");
        if (playable != (failure == Failure.NONE)) {
            throw new IllegalArgumentException("playable result must not have a failure");
        }
    }

    static DcamMediaValidationResult accepted(String detail) {
        return new DcamMediaValidationResult(true, Failure.NONE, detail);
    }

    static DcamMediaValidationResult rejected(String detail) {
        return new DcamMediaValidationResult(false, Failure.OTHER, detail);
    }

    static DcamMediaValidationResult rejectedNonPositiveDuration(
            String mediaKind, long durationMillis) {
        return new DcamMediaValidationResult(false, Failure.NON_POSITIVE_DURATION,
                "Android metadata returned non-positive duration "
                        + durationMillis + " ms for the " + mediaKind + " track.");
    }
}