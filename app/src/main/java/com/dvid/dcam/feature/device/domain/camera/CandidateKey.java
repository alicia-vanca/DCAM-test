package com.dvid.dcam.feature.device.domain.camera;

import java.util.Objects;
import java.util.Optional;

public record CandidateKey(
        CameraId cameraId,
        VideoCodec codec,
        VerificationPipelineId verificationPipelineId,
        Kind kind,
        Optional<VideoMode> videoMode,
        Optional<ImageMode> imageMode) implements Comparable<CandidateKey> {
    public enum Kind { VIDEO, IMAGE, TUPLE }

    public CandidateKey {
        Objects.requireNonNull(cameraId, "cameraId");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(verificationPipelineId, "verificationPipelineId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(videoMode, "videoMode");
        Objects.requireNonNull(imageMode, "imageMode");
        boolean valid = switch (kind) {
            case VIDEO -> videoMode.isPresent() && imageMode.isEmpty();
            case IMAGE -> videoMode.isEmpty() && imageMode.isPresent();
            case TUPLE -> videoMode.isPresent() && imageMode.isPresent();
        };
        if (!valid) throw new IllegalArgumentException("candidate kind does not match modes");
    }

    public static CandidateKey forVideo(CameraId cameraId, VideoCodec codec,
            VerificationPipelineId verificationPipelineId, VideoMode videoMode) {
        return new CandidateKey(cameraId, codec, verificationPipelineId, Kind.VIDEO,
                Optional.of(videoMode), Optional.empty());
    }

    public static CandidateKey forImage(CameraId cameraId, VideoCodec codec,
            VerificationPipelineId verificationPipelineId, ImageMode imageMode) {
        return new CandidateKey(cameraId, codec, verificationPipelineId, Kind.IMAGE,
                Optional.empty(), Optional.of(imageMode));
    }

    public static CandidateKey forTuple(CameraId cameraId, VideoCodec codec,
            VerificationPipelineId verificationPipelineId, CaptureModeTuple tuple) {
        Objects.requireNonNull(tuple, "tuple");
        return new CandidateKey(cameraId, codec, verificationPipelineId, Kind.TUPLE,
                Optional.of(tuple.videoMode()), Optional.of(tuple.imageMode()));
    }

    public Optional<CaptureModeTuple> tuple() {
        if (kind != Kind.TUPLE) return Optional.empty();
        return Optional.of(new CaptureModeTuple(videoMode.orElseThrow(), imageMode.orElseThrow()));
    }

    @Override public int compareTo(CandidateKey other) {
        int cameraOrder = cameraId.compareTo(other.cameraId);
        if (cameraOrder != 0) return cameraOrder;
        int codecOrder = codec.id().compareTo(other.codec.id());
        if (codecOrder != 0) return codecOrder;
        int pipelineOrder = verificationPipelineId.compareTo(other.verificationPipelineId);
        if (pipelineOrder != 0) return pipelineOrder;
        int kindOrder = kind.compareTo(other.kind);
        if (kindOrder != 0) return kindOrder;
        int videoOrder = compareOptional(videoMode, other.videoMode);
        if (videoOrder != 0) return videoOrder;
        return compareOptional(imageMode, other.imageMode);
    }

    private static <T extends Comparable<? super T>> int compareOptional(
            Optional<T> left, Optional<T> right) {
        if (left.isEmpty()) return right.isEmpty() ? 0 : -1;
        if (right.isEmpty()) return 1;
        return left.orElseThrow().compareTo(right.orElseThrow());
    }
}