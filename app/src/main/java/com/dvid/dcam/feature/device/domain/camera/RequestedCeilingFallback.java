package com.dvid.dcam.feature.device.domain.camera;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

public final class RequestedCeilingFallback {
    private RequestedCeilingFallback() {}

    public static List<CandidateKey> orderedCandidates(CandidateKey requested,
            Collection<CandidateKey> effectiveCandidates) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(effectiveCandidates, "effectiveCandidates");
        if (requested.kind() != CandidateKey.Kind.TUPLE) {
            throw new IllegalArgumentException("requested candidate must be a tuple");
        }
        CaptureModeTuple requestedTuple = requested.tuple().orElseThrow();
        TreeSet<CandidateKey> ordered = new TreeSet<>(fallbackOrder());
        for (CandidateKey candidate : effectiveCandidates) {
            CandidateKey checked = Objects.requireNonNull(candidate, "effective candidate");
            if (checked.kind() != CandidateKey.Kind.TUPLE
                    || !samePipelineIdentity(requested, checked)) {
                continue;
            }
            CaptureModeTuple tuple = checked.tuple().orElseThrow();
            if (withinCeilings(tuple, requestedTuple)) ordered.add(checked);
        }
        return List.copyOf(new ArrayList<>(ordered));
    }

    private static boolean withinCeilings(CaptureModeTuple candidate,
            CaptureModeTuple requested) {
        return CameraModeOrder.resolutions().compare(
                        candidate.videoMode().resolution(),
                        requested.videoMode().resolution()) <= 0
                && candidate.videoMode().framesPerSecond()
                        <= requested.videoMode().framesPerSecond()
                && CameraModeOrder.resolutions().compare(
                        candidate.imageMode().resolution(),
                        requested.imageMode().resolution()) <= 0;
    }

    private static Comparator<CandidateKey> fallbackOrder() {
        return (left, right) -> {
            int qualityOrder = CameraModeOrder.tuples().compare(
                    right.tuple().orElseThrow(), left.tuple().orElseThrow());
            if (qualityOrder != 0) return qualityOrder;
            return left.compareTo(right);
        };
    }

    private static boolean samePipelineIdentity(CandidateKey left, CandidateKey right) {
        return left.cameraId().equals(right.cameraId())
                && left.codec() == right.codec()
                && left.verificationPipelineId().equals(right.verificationPipelineId());
    }
}