package com.dvid.dcam.platform.storage;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class DcamMediaReservation {
    static final Object FILESYSTEM_LOCK = new Object();
    private static final Set<String> ACTIVE_STAGING_PATHS = ConcurrentHashMap.newKeySet();
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Clock clock;
    private final Sleeper sleeper;
    private final EnumMap<DcamFileType, Long> reservedSeconds =
            new EnumMap<>(DcamFileType.class);

    DcamMediaReservation() {
        this(Clock.systemDefaultZone(), Thread::sleep);
    }

    DcamMediaReservation(Clock clock, Sleeper sleeper) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    long delayMillis(DcamFileType type) {
        Objects.requireNonNull(type, "type");
        long nowMillis = clock.millis();
        long nowSecond = Math.floorDiv(nowMillis, 1000L);
        synchronized (this) {
            long lastReservedSecond = reservedSeconds.getOrDefault(type, Long.MIN_VALUE);
            if (nowSecond > lastReservedSecond) return 0L;
            return Math.max(1L, (lastReservedSecond + 1L) * 1000L - nowMillis);
        }
    }

    boolean trackActiveStaging(File file) {
        return ACTIVE_STAGING_PATHS.add(
                Objects.requireNonNull(file, "file").getAbsolutePath());
    }

    void releaseActiveStaging(File file) {
        if (file != null) ACTIVE_STAGING_PATHS.remove(file.getAbsolutePath());
    }

    Set<String> activeStagingPaths() {
        return Set.copyOf(ACTIVE_STAGING_PATHS);
    }

    LocalDateTime reserve(DcamFileType type) {
        Objects.requireNonNull(type, "type");
        while (true) {
            Instant now = clock.instant();
            long nowMillis = now.toEpochMilli();
            long nowSecond = Math.floorDiv(nowMillis, 1000L);
            long lastReservedSecond;
            synchronized (this) {
                lastReservedSecond = reservedSeconds.getOrDefault(type, Long.MIN_VALUE);
                if (nowSecond > lastReservedSecond) {
                    reservedSeconds.put(type, nowSecond);
                    return LocalDateTime.ofInstant(now, clock.getZone());
                }
            }
            long delayMillis = Math.max(1L,
                    (lastReservedSecond + 1L) * 1000L - nowMillis);
            try {
                sleeper.sleep(delayMillis);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Media filename reservation interrupted for " + type, failure);
            }
        }
    }
}