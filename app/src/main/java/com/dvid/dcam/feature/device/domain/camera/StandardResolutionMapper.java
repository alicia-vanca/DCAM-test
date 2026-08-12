package com.dvid.dcam.feature.device.domain.camera;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class StandardResolutionMapper {
    private static final Comparator<Match> MATCH_ORDER = Comparator
            .comparingInt(Match::deltaSum)
            .thenComparingInt(Match::maximumDelta)
            .thenComparing(Comparator.comparingLong(
                    (Match match) -> match.actual().pixelCount()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (Match match) -> match.actual().width()).reversed())
            .thenComparing(Comparator.comparingInt(
                    (Match match) -> match.actual().height()).reversed());

    private StandardResolutionMapper() {}

    public static List<StandardResolution> map(Collection<CameraResolution> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        TreeSet<CameraResolution> uniqueCandidates = new TreeSet<>();
        for (CameraResolution candidate : candidates) {
            uniqueCandidates.add(Objects.requireNonNull(candidate, "candidate"));
        }

        List<StandardResolution> mapped = new ArrayList<>();
        for (StandardResolutionLabel label : StandardResolutionLabel.values()) {
            if (label == StandardResolutionLabel.MAX) continue;
            List<StandardResolution> ranked = rankedMatches(label, uniqueCandidates);
            if (!ranked.isEmpty()) mapped.add(ranked.get(0));
        }

        CameraResolution rawMaximum = uniqueCandidates.isEmpty()
                ? null : uniqueCandidates.last();
        if (rawMaximum != null && qualifiesAsMaximum(rawMaximum,
                mapped.stream().map(StandardResolution::actual)
                        .collect(java.util.stream.Collectors.toList()))) {
            mapped.add(new StandardResolution(StandardResolutionLabel.MAX, rawMaximum));
        }
        return List.copyOf(mapped);
    }

    public static List<StandardResolution> rankedMatches(StandardResolutionLabel label,
            Collection<CameraResolution> candidates) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(candidates, "candidates");
        if (label == StandardResolutionLabel.MAX) return List.of();
        TreeSet<CameraResolution> uniqueCandidates = new TreeSet<>();
        for (CameraResolution candidate : candidates) {
            uniqueCandidates.add(Objects.requireNonNull(candidate, "candidate"));
        }
        List<Match> matches = new ArrayList<>();
        for (CameraResolution actual : uniqueCandidates) {
            Match best = match(label, actual);
            if (best != null) matches.add(best);
        }
        matches.sort(MATCH_ORDER);
        List<StandardResolution> result = new ArrayList<>();
        for (Match match : matches) {
            result.add(new StandardResolution(label, match.actual()));
        }
        return List.copyOf(result);
    }

    public static boolean qualifiesAsMaximum(CameraResolution candidate,
            Collection<CameraResolution> representedStandards) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(representedStandards, "representedStandards");
        CameraResolution highest = null;
        for (CameraResolution represented : representedStandards) {
            CameraResolution checked = Objects.requireNonNull(represented, "represented");
            if (candidate.equals(checked)) return false;
            if (highest == null || checked.compareTo(highest) > 0) highest = checked;
        }
        return highest == null || candidate.compareTo(highest) > 0;
    }

    public static int compareMatch(StandardResolution left, StandardResolution right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.label() != right.label() || left.label() == StandardResolutionLabel.MAX) {
            throw new IllegalArgumentException("standard match comparison needs same non-MAX label");
        }
        return MATCH_ORDER.compare(
                Objects.requireNonNull(match(left.label(), left.actual())),
                Objects.requireNonNull(match(right.label(), right.actual())));
    }

    private static Match match(StandardResolutionLabel label, CameraResolution actual) {
        Match best = null;
        for (CameraResolution target : label.targets()) {
            int deltaWidth = Math.abs(actual.width() - target.width());
            int deltaHeight = Math.abs(actual.height() - target.height());
            if (deltaWidth > StandardResolutionLabel.AXIS_TOLERANCE
                    || deltaHeight > StandardResolutionLabel.AXIS_TOLERANCE) {
                continue;
            }
            Match candidate = new Match(actual, deltaWidth + deltaHeight,
                    Math.max(deltaWidth, deltaHeight));
            if (best == null || MATCH_ORDER.compare(candidate, best) < 0) best = candidate;
        }
        return best;
    }

    private record Match(CameraResolution actual, int deltaSum, int maximumDelta) {}
}