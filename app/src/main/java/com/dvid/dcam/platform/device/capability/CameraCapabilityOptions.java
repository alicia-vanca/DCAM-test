package com.dvid.dcam.platform.device.capability;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.CaptureQuality;
import com.dvid.dcam.feature.device.domain.camera.CameraModeOrder;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

final class CameraCapabilityOptions {
    private static final Comparator<CaptureQuality> AREA_DESC = (left, right) -> {
        int area = Long.compare(area(right), area(left));
        return area != 0 ? area : left.getId().compareTo(right.getId());
    };

    private CameraCapabilityOptions() {}

    static List<String> cameraIds(Snapshot snapshot) {
        if (snapshot == null) return List.of();
        List<String> result = new ArrayList<>();
        if (!snapshot.cameraOrderOverride().isEmpty()) {
            for (var value : snapshot.cameraOrderOverride()) result.add(value.value());
        } else {
            for (var value : snapshot.cameras()) result.add(value.cameraId().value());
        }
        return List.copyOf(result);
    }

    static PipelineEvidence pipeline(Snapshot snapshot, String cameraId) {
        CameraSnapshot camera = camera(snapshot, cameraId).orElse(null);
        if (camera == null || camera.selectedPipeline().isEmpty()) return null;
        VerificationPipelineId selected = camera.selectedPipeline().orElseThrow().pipelineId();
        for (CodecSnapshot codec : camera.codecs()) {
            if (codec.codec() != com.dvid.dcam.feature.device.domain.camera.VideoCodec.H264) {
                continue;
            }
            for (PipelineEvidence pipeline : codec.pipelines()) {
                if (pipeline.verificationPipelineId().equals(selected)) return pipeline;
            }
        }
        return null;
    }

    static List<CaptureQuality> recordQualities(
            Snapshot snapshot, String cameraId, Set<String> runtimeRejections) {
        PipelineEvidence pipeline = pipeline(snapshot, cameraId);
        if (pipeline == null) return List.of();
        Map<String, ResolutionFacts> facts = new LinkedHashMap<>();
        for (CandidateKey candidate : effective(pipeline, runtimeRejections)) {
            if (candidate.kind() != CandidateKey.Kind.VIDEO) continue;
            VideoMode mode = candidate.videoMode().orElseThrow();
            String id = mode.resolution().label().name();
            ResolutionFacts existing = facts.get(id);
            int frameRate = mode.framesPerSecond();
            int resolutionOrder = existing == null ? 1
                    : CameraModeOrder.resolutions().compare(
                            mode.resolution(), existing.mode().resolution());
            if (existing == null || resolutionOrder > 0
                    || resolutionOrder == 0 && frameRate > existing.maxFrameRate()) {
                facts.put(id, new ResolutionFacts(mode, frameRate));
            }
        }
        List<CaptureQuality> result = new ArrayList<>();
        for (ResolutionFacts value : facts.values()) {
            if (!frameRates(snapshot, cameraId, value.mode().resolution().label().name(),
                    runtimeRejections).isEmpty()) {
                result.add(new CaptureQuality(value.mode().resolution().label().name(),
                        value.mode().resolution().actual().width(),
                        value.mode().resolution().actual().height(),
                        value.maxFrameRate()));
            }
        }
        result.sort(Comparator.comparingInt(value -> labelOrder(value.getId())));
        return List.copyOf(result);
    }

    static List<CaptureQuality> imageQualities(
            Snapshot snapshot, String cameraId, Set<String> runtimeRejections) {
        PipelineEvidence pipeline = pipeline(snapshot, cameraId);
        if (pipeline == null) return List.of();
        Map<String, StandardResolution> resolutions = new LinkedHashMap<>();
        for (CandidateKey candidate : effective(pipeline, runtimeRejections)) {
            if (candidate.kind() != CandidateKey.Kind.IMAGE) continue;
            StandardResolution resolution = candidate.imageMode().orElseThrow().resolution();
            String id = resolution.label().name();
            resolutions.merge(id, resolution, (left, right) ->
                    CameraModeOrder.resolutions().compare(left, right) >= 0 ? left : right);
        }
        List<CaptureQuality> values = resolutions.entrySet().stream()
                .map(entry -> new CaptureQuality(entry.getKey(),
                        entry.getValue().actual().width(),
                        entry.getValue().actual().height()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        values.sort(Comparator.comparingInt(value -> labelOrder(value.getId())));
        return List.copyOf(values);
    }

    static List<Integer> frameRates(Snapshot snapshot, String cameraId,
            String videoId, Set<String> runtimeRejections) {
        PipelineEvidence pipeline = pipeline(snapshot, cameraId);
        if (pipeline == null) return List.of();
        StandardResolution selected = bestVideoResolution(
                pipeline, videoId, runtimeRejections).orElse(null);
        if (selected == null) return List.of();
        TreeSet<Integer> all = new TreeSet<>();
        for (CandidateKey candidate : effective(pipeline, runtimeRejections)) {
            if (candidate.kind() != CandidateKey.Kind.VIDEO) continue;
            VideoMode mode = candidate.videoMode().orElseThrow();
            if (mode.resolution().equals(selected)) all.add(mode.framesPerSecond());
        }
        List<Integer> supported = new ArrayList<>();
        for (int frameRate : all) {
            if (!imageIds(pipeline, videoId, frameRate, runtimeRejections).isEmpty()) {
                supported.add(frameRate);
            }
        }
        return List.copyOf(supported);
    }

    static List<CaptureQuality> videoFallbacks(Snapshot snapshot, String cameraId,
            String requestedVideoId, int requestedFrameRate, Set<String> runtimeRejections) {
        List<CaptureQuality> videos = recordQualities(snapshot, cameraId, runtimeRejections);
        CaptureQuality requested = find(videos, requestedVideoId);
        if (requested == null) return List.of();
        List<CaptureQuality> result = new ArrayList<>();
        for (CaptureQuality video : atOrBelow(videos, requested)) {
            List<Integer> rates = frameRates(snapshot, cameraId, video.getId(), runtimeRejections);
            for (int index = rates.size() - 1; index >= 0; index--) {
                int rate = rates.get(index);
                if (rate <= requestedFrameRate) result.add(video.withFrameRate(rate));
            }
        }
        return List.copyOf(result);
    }

    static List<CaptureQuality> imageFallbacks(Snapshot snapshot, String cameraId,
            String videoId, int frameRate, String requestedImageId,
            Set<String> runtimeRejections) {
        List<CaptureQuality> images = imageQualities(snapshot, cameraId, runtimeRejections);
        CaptureQuality requested = find(images, requestedImageId);
        if (requested == null) return List.of();
        List<String> compatible = imageIds(pipeline(snapshot, cameraId), videoId,
                frameRate, runtimeRejections);
        if (compatible == null) return atOrBelow(images, requested);
        List<CaptureQuality> result = new ArrayList<>();
        for (CaptureQuality image : atOrBelow(images, requested)) {
            if (compatible.contains(image.getId())) result.add(image);
        }
        return List.copyOf(result);
    }

    static Optional<CandidateKey> nextLowerStandaloneImageCandidate(
            Snapshot snapshot, CandidateKey requested, Set<String> runtimeRejections) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(runtimeRejections, "runtimeRejections");
        PipelineEvidence pipeline = pipeline(snapshot, requested);
        if (pipeline == null || requested.tuple().isEmpty()) return Optional.empty();
        ImageMode requestedImage = requested.imageMode().orElseThrow();
        return effective(pipeline, runtimeRejections).stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE)
                .map(candidate -> candidate.imageMode().orElseThrow())
                .filter(image -> CameraModeOrder.resolutions().compare(
                        image.resolution(), requestedImage.resolution()) < 0)
                .max((left, right) -> CameraModeOrder.resolutions().compare(
                        left.resolution(), right.resolution()))
                .map(image -> CandidateKey.forTuple(requested.cameraId(), requested.codec(),
                        requested.verificationPipelineId(), new CaptureModeTuple(
                                requested.videoMode().orElseThrow(), image)));
    }

    static CaptureQuality selectedVideo(Snapshot snapshot, String cameraId,
            String requestedId, int requestedFrameRate, Set<String> rejections) {
        List<CaptureQuality> videos = recordQualities(snapshot, cameraId, rejections);
        CaptureQuality selected = find(videos, requestedId);
        if (selected == null) selected = defaultQuality(videos);
        if (selected == null) return null;
        List<Integer> rates = frameRates(snapshot, cameraId, selected.getId(), rejections);
        int rate;
        if (rates.contains(requestedFrameRate)) {
            rate = requestedFrameRate;
        } else if (rates.isEmpty()) {
            rate = 0;
        } else {
            rate = rates.get(rates.size() - 1);
        }
        return selected.withFrameRate(rate);
    }

    static CaptureQuality selectedImage(Snapshot snapshot, String cameraId,
            String requestedId, Set<String> rejections) {
        List<CaptureQuality> images = imageQualities(snapshot, cameraId, rejections);
        CaptureQuality selected = find(images, requestedId);
        return selected == null ? defaultQuality(images) : selected;
    }

    static boolean containsQuality(List<CaptureQuality> values, String id) {
        return find(values, id) != null;
    }

    static List<String> imageIds(PipelineEvidence pipeline, String videoId, int frameRate,
            Set<String> runtimeRejections) {
        if (pipeline == null) return List.of();
        Optional<StandardResolution> selected = bestVideoResolution(
                pipeline, videoId, runtimeRejections);
        boolean labelRowExists = pipeline.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .map(CandidateKey::tuple).flatMap(Optional::stream)
                .anyMatch(tuple -> tuple.videoMode().resolution().label().name().equals(videoId)
                        && tuple.videoMode().framesPerSecond() == frameRate);
        if (selected.isEmpty()) return labelRowExists ? List.of() : null;
        StandardResolution actual = selected.orElseThrow();
        boolean rowExists = pipeline.rawFastCandidates().stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .map(CandidateKey::tuple).flatMap(Optional::stream)
                .anyMatch(tuple -> tuple.videoMode().resolution().equals(actual)
                        && tuple.videoMode().framesPerSecond() == frameRate);
        TreeSet<String> result = new TreeSet<>();
        for (CandidateKey candidate : pipeline.effectiveCandidates()) {
            if (candidate.kind() != CandidateKey.Kind.TUPLE) continue;
            var tuple = candidate.tuple().orElseThrow();
            if (tuple.videoMode().resolution().equals(actual)
                    && tuple.videoMode().framesPerSecond() == frameRate
                    && !isRejected(candidate, runtimeRejections)) {
                result.add(tuple.imageMode().resolution().label().name());
            }
        }
        return rowExists ? List.copyOf(result) : null;
    }

    private static Optional<StandardResolution> bestVideoResolution(
            PipelineEvidence pipeline, String videoId, Set<String> runtimeRejections) {
        return effective(pipeline, runtimeRejections).stream()
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.VIDEO)
                .map(candidate -> candidate.videoMode().orElseThrow().resolution())
                .filter(resolution -> resolution.label().name().equals(videoId))
                .max(CameraModeOrder.resolutions());
    }
    private static Set<CandidateKey> effective(PipelineEvidence pipeline,
            Set<String> runtimeRejections) {
        TreeSet<CandidateKey> result = new TreeSet<>();
        for (CandidateKey candidate : pipeline.effectiveCandidates()) {
            if (!isRejected(candidate, runtimeRejections)) result.add(candidate);
        }
        return result;
    }

    static boolean isRejected(CandidateKey candidate, Set<String> rejections) {
        if (candidate.kind() != CandidateKey.Kind.TUPLE) return false;
        var tuple = candidate.tuple().orElseThrow();
        String prefix = candidate.cameraId().value() + "|"
                + tuple.videoMode().resolution().label().name() + "|"
                + tuple.videoMode().framesPerSecond() + "|";
        String image = tuple.imageMode().resolution().label().name();
        return rejections.contains(prefix) || rejections.contains(prefix + image);
    }

    private static Optional<CameraSnapshot> camera(Snapshot snapshot, String id) {
        if (snapshot == null || id == null) return Optional.empty();
        for (CameraSnapshot value : snapshot.cameras()) {
            if (value.cameraId().value().equals(id)) return Optional.of(value);
        }
        return Optional.empty();
    }

    private static PipelineEvidence pipeline(Snapshot snapshot, CandidateKey requested) {
        CameraSnapshot camera = camera(snapshot, requested.cameraId().value()).orElse(null);
        if (camera == null) return null;
        for (CodecSnapshot codec : camera.codecs()) {
            if (codec.codec() != requested.codec()) continue;
            for (PipelineEvidence pipeline : codec.pipelines()) {
                if (pipeline.verificationPipelineId().equals(
                        requested.verificationPipelineId())) return pipeline;
            }
        }
        return null;
    }

    private static List<CaptureQuality> atOrBelow(
            List<CaptureQuality> values, CaptureQuality maximum) {
        List<CaptureQuality> result = new ArrayList<>();
        long maxArea = area(maximum);
        for (CaptureQuality value : values) if (area(value) <= maxArea) result.add(value);
        result.sort(AREA_DESC);
        return List.copyOf(result);
    }

    private static CaptureQuality defaultQuality(List<CaptureQuality> values) {
        if (values.isEmpty()) return null;
        for (String id : List.of("FHD", "HD", "SD")) {
            CaptureQuality found = find(values, id);
            if (found != null) return found;
        }
        List<CaptureQuality> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparingLong(CameraCapabilityOptions::area));
        return sorted.get(0);
    }

    private static CaptureQuality find(List<CaptureQuality> values, String id) {
        if (id == null) return null;
        for (CaptureQuality value : values) if (value.getId().equals(id)) return value;
        return null;
    }

    private static long area(CaptureQuality value) {
        return (long) value.getWidth() * value.getHeight();
    }

    private static int labelOrder(String id) {
        try {
            return switch (StandardResolutionLabel.valueOf(id)) {
                case SD -> 0;
                case HD -> 1;
                case FHD -> 2;
                case QHD -> 3;
                case UHD -> 4;
                case MAX -> 5;
            };
        } catch (IllegalArgumentException error) {
            return Integer.MAX_VALUE;
        }
    }

    private record ResolutionFacts(VideoMode mode, int maxFrameRate) {}
}
