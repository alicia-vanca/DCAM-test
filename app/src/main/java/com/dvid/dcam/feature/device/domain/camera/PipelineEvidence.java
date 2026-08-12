package com.dvid.dcam.feature.device.domain.camera;

import java.util.Collection;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

public final class PipelineEvidence {
    private final CameraId cameraId;
    private final VideoCodec codec;
    private final VerificationPipelineId verificationPipelineId;
    private final PipelineAvailability availability;
    private final NavigableSet<CandidateKey> rawFastCandidates;
    private final NavigableMap<CandidateKey, VerificationOutcome> candidateEvidence;

    public PipelineEvidence(CameraId cameraId, VideoCodec codec,
            VerificationPipelineId verificationPipelineId,
            PipelineAvailability availability,
            Collection<CandidateKey> rawFastCandidates,
            Collection<CandidateEvidence> candidateEvidence) {
        this.cameraId = Objects.requireNonNull(cameraId, "cameraId");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.verificationPipelineId = Objects.requireNonNull(
                verificationPipelineId, "verificationPipelineId");
        this.availability = Objects.requireNonNull(availability, "availability");

        TreeSet<CandidateKey> raw = new TreeSet<>();
        for (CandidateKey candidate : Objects.requireNonNull(
                rawFastCandidates, "rawFastCandidates")) {
            requireIdentity(Objects.requireNonNull(candidate, "raw candidate"));
            raw.add(candidate);
        }
        this.rawFastCandidates = Collections.unmodifiableNavigableSet(raw);

        TreeMap<CandidateKey, VerificationOutcome> facts = new TreeMap<>();
        for (CandidateEvidence fact : Objects.requireNonNull(
                candidateEvidence, "candidateEvidence")) {
            CandidateEvidence checked = Objects.requireNonNull(fact, "candidate evidence");
            requireIdentity(checked.candidate());
            if (facts.put(checked.candidate(), checked.outcome()) != null) {
                throw new IllegalArgumentException(
                        "duplicate candidate evidence: " + checked.candidate());
            }
        }
        this.candidateEvidence = Collections.unmodifiableNavigableMap(facts);
    }

    public CameraId cameraId() { return cameraId; }
    public VideoCodec codec() { return codec; }
    public VerificationPipelineId verificationPipelineId() {
        return verificationPipelineId;
    }
    public PipelineAvailability availability() { return availability; }
    public NavigableSet<CandidateKey> rawFastCandidates() { return rawFastCandidates; }
    public NavigableMap<CandidateKey, VerificationOutcome> candidateEvidence() {
        return candidateEvidence;
    }

    public VerificationOutcome outcome(CandidateKey candidate) {
        requireIdentity(Objects.requireNonNull(candidate, "candidate"));
        return candidateEvidence.getOrDefault(candidate, VerificationOutcome.UNKNOWN);
    }

    public VerificationOutcome outcome(CaptureModeTuple tuple) {
        Objects.requireNonNull(tuple, "tuple");
        return outcome(CandidateKey.forTuple(
                cameraId, codec, verificationPipelineId, tuple));
    }

    public boolean hasRawScope(CandidateKey candidate) {
        requireIdentity(Objects.requireNonNull(candidate, "candidate"));
        if (rawFastCandidates.contains(candidate)) return true;
        return rawFastCandidates.stream().anyMatch(raw -> switch (candidate.kind()) {
            case VIDEO -> raw.videoMode().equals(candidate.videoMode());
            case IMAGE -> raw.imageMode().equals(candidate.imageMode());
            case TUPLE -> false;
        });
    }

    public NavigableSet<CandidateKey> effectiveCandidates() {
        if (availability == PipelineAvailability.UNAVAILABLE) {
            return Collections.unmodifiableNavigableSet(new TreeSet<>());
        }
        TreeSet<CandidateKey> effective = new TreeSet<>(rawFastCandidates);
        for (var fact : candidateEvidence.entrySet()) {
            if (!fact.getValue().prunesCandidate()) continue;
            CandidateKey failed = fact.getKey();
            effective.removeIf(candidate -> isPrunedBy(candidate, failed));
        }
        TreeMap<StandardResolutionLabel, StandardResolution> verifiedVideo =
                verifiedTupleResolutions(CandidateKey.Kind.VIDEO);
        TreeMap<StandardResolutionLabel, StandardResolution> verifiedImage =
                verifiedTupleResolutions(CandidateKey.Kind.IMAGE);
        effective.removeIf(candidate -> candidate.videoMode().map(mode -> {
            StandardResolution pinned = verifiedVideo.get(mode.resolution().label());
            return pinned != null && !pinned.actual().equals(
                    mode.resolution().actual());
        }).orElse(false));
        effective.removeIf(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE
                && candidate.imageMode().map(mode -> {
                    StandardResolution pinned = verifiedImage.get(mode.resolution().label());
                    return pinned != null && !pinned.actual().equals(
                            mode.resolution().actual());
                }).orElse(false));
        return Collections.unmodifiableNavigableSet(effective);
    }

    public boolean isEffective(CandidateKey candidate) {
        CandidateKey checked = Objects.requireNonNull(candidate, "candidate");
        requireIdentity(checked);
        if (availability == PipelineAvailability.UNAVAILABLE
                || !rawFastCandidates.contains(checked)) {
            return false;
        }
        for (var fact : candidateEvidence.entrySet()) {
            if (fact.getValue().prunesCandidate() && isPrunedBy(checked, fact.getKey())) {
                return false;
            }
        }
        if (checked.videoMode().map(mode -> {
            StandardResolution pinned = verifiedTupleResolution(
                    CandidateKey.Kind.VIDEO, mode.resolution().label());
            return pinned != null && !pinned.actual().equals(mode.resolution().actual());
        }).orElse(false)) {
            return false;
        }
        return checked.kind() != CandidateKey.Kind.TUPLE
                || !checked.imageMode().map(mode -> {
                    StandardResolution pinned = verifiedTupleResolution(
                            CandidateKey.Kind.IMAGE, mode.resolution().label());
                    return pinned != null && !pinned.actual().equals(
                            mode.resolution().actual());
                }).orElse(false);
    }

    private StandardResolution verifiedTupleResolution(
            CandidateKey.Kind kind, StandardResolutionLabel label) {
        StandardResolution verified = null;
        for (var fact : candidateEvidence.entrySet()) {
            CandidateKey candidate = fact.getKey();
            if (candidate.kind() != CandidateKey.Kind.TUPLE
                    || fact.getValue() != VerificationOutcome.VERIFIED_PASS) {
                continue;
            }
            StandardResolution resolution = kind == CandidateKey.Kind.VIDEO
                    ? candidate.videoMode().orElseThrow().resolution()
                    : candidate.imageMode().orElseThrow().resolution();
            if (resolution.label() == label && (verified == null
                    || CameraModeOrder.resolutions().compare(resolution, verified) > 0)) {
                verified = resolution;
            }
        }
        return verified;
    }

    private TreeMap<StandardResolutionLabel, StandardResolution> verifiedTupleResolutions(
            CandidateKey.Kind kind) {
        TreeMap<StandardResolutionLabel, StandardResolution> verified = new TreeMap<>();
        for (var fact : candidateEvidence.entrySet()) {
            CandidateKey candidate = fact.getKey();
            if (candidate.kind() != CandidateKey.Kind.TUPLE
                    || fact.getValue() != VerificationOutcome.VERIFIED_PASS) {
                continue;
            }
            StandardResolution resolution = kind == CandidateKey.Kind.VIDEO
                    ? candidate.videoMode().orElseThrow().resolution()
                    : candidate.imageMode().orElseThrow().resolution();
            verified.merge(resolution.label(), resolution, (left, right) ->
                    CameraModeOrder.resolutions().compare(left, right) >= 0 ? left : right);
        }
        return verified;
    }
    private boolean isPrunedBy(CandidateKey candidate, CandidateKey failed) {
        return switch (failed.kind()) {
            case VIDEO -> candidate.videoMode().equals(failed.videoMode());
            case IMAGE -> candidate.kind() == CandidateKey.Kind.IMAGE
                    && candidate.imageMode().equals(failed.imageMode());
            case TUPLE -> candidate.equals(failed);
        };
    }

    private void requireIdentity(CandidateKey candidate) {
        if (!cameraId.equals(candidate.cameraId())
                || codec != candidate.codec()
                || !verificationPipelineId.equals(candidate.verificationPipelineId())) {
            throw new IllegalArgumentException("candidate belongs to another pipeline identity");
        }
    }
}