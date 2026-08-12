package com.dvid.dcam.feature.device.domain.camera;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class FrameRatePolicy {
    private static final int MINIMUM_EXCLUSIVE = 19;
    private static final int PREFERRED_MINIMUM = 30;

    private FrameRatePolicy() {}

    public static int normalize(double framesPerSecond) {
        if (!Double.isFinite(framesPerSecond) || framesPerSecond <= 0) {
            throw new IllegalArgumentException("frames per second must be finite and positive");
        }
        long normalized = (long) Math.ceil(framesPerSecond);
        if (normalized > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("frames per second is too large");
        }
        return (int) normalized;
    }

    public static List<Integer> retainSupported(Collection<? extends Number> frameRates) {
        Objects.requireNonNull(frameRates, "frameRates");
        TreeSet<Integer> normalized = new TreeSet<>();
        for (Number frameRate : frameRates) {
            normalized.add(normalize(Objects.requireNonNull(frameRate, "frameRate").doubleValue()));
        }

        List<Integer> preferred = normalized.stream()
                .filter(frameRate -> frameRate >= PREFERRED_MINIMUM)
                .collect(java.util.stream.Collectors.toList());
        if (!preferred.isEmpty()) return preferred;
        return normalized.stream()
                .filter(frameRate -> frameRate > MINIMUM_EXCLUSIVE)
                .max(Integer::compareTo)
                .map(List::of)
                .orElseGet(List::of);
    }
}