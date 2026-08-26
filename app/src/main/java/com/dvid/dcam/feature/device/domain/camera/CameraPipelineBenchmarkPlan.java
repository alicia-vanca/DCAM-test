package com.dvid.dcam.feature.device.domain.camera;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record CameraPipelineBenchmarkPlan(
        FrozenEnvironment environment,
        List<CameraScope> cameras,
        BenchmarkProtocol protocol) {
    public CameraPipelineBenchmarkPlan {
        environment = Objects.requireNonNull(environment, "environment");
        cameras = immutableScopes(cameras);
        protocol = Objects.requireNonNull(protocol, "protocol");
        if (cameras.isEmpty()) {
            throw new IllegalArgumentException("benchmark camera scope is empty");
        }
        Set<CameraId> expected = environment.cameraHardwareSignatures().keySet();
        Set<CameraId> actual = cameras.stream().map(CameraScope::cameraId).collect(java.util.stream.Collectors.toSet());
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("camera scope must match frozen inventory");
        }
        for (CameraScope scope : cameras) {
            if (!scope.hardwareSignature().equals(
                    environment.cameraHardwareSignatures().get(scope.cameraId()))) {
                throw new IllegalArgumentException(
                        "camera scope hardware signature differs from frozen inventory");
            }
        }
    }

    public record FrozenEnvironment(
            String hardwareSignature,
            Map<CameraId, String> cameraHardwareSignatures,
            VideoCodec codec,
            String h264Configuration,
            String cropRotationPolicy,
            long candidateTimeoutMillis,
            String storagePath,
            String thermalGate) {
        public FrozenEnvironment {
            hardwareSignature = required(hardwareSignature, "hardwareSignature");
            cameraHardwareSignatures = immutableSignatures(cameraHardwareSignatures);
            if (codec != VideoCodec.H264) {
                throw new IllegalArgumentException("CAM-IMP-12 supports H.264 only");
            }
            h264Configuration = required(h264Configuration, "h264Configuration");
            cropRotationPolicy = required(cropRotationPolicy, "cropRotationPolicy");
            if (candidateTimeoutMillis != CameraOperationDeadline.CANDIDATE_TIMEOUT_MILLIS) {
                throw new IllegalArgumentException("candidateTimeoutMillis must be 5000");
            }
            storagePath = required(storagePath, "storagePath");
            thermalGate = required(thermalGate, "thermalGate");
        }
    }

    public record CameraScope(
            CameraId cameraId,
            String hardwareSignature,
            Set<ImageMode> imageUniverse,
            Set<CaptureModeTuple> candidateUniverse) {
        public CameraScope(CameraId cameraId, String hardwareSignature,
                Set<CaptureModeTuple> candidateUniverse) {
            this(cameraId, hardwareSignature, imageModes(candidateUniverse), candidateUniverse);
        }

        public CameraScope {
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            hardwareSignature = required(hardwareSignature, "cameraHardwareSignature");
            TreeSet<ImageMode> images = new TreeSet<>();
            for (ImageMode image : Objects.requireNonNull(imageUniverse, "imageUniverse")) {
                images.add(Objects.requireNonNull(image, "image mode"));
            }
            TreeSet<CaptureModeTuple> ordered = new TreeSet<>(CameraModeOrder.tuples());
            for (CaptureModeTuple tuple : Objects.requireNonNull(
                    candidateUniverse, "candidateUniverse")) {
                ordered.add(Objects.requireNonNull(tuple, "candidate tuple"));
            }
            if (ordered.isEmpty()) {
                throw new IllegalArgumentException("candidate universe is empty");
            }
            if (images.isEmpty()) {
                throw new IllegalArgumentException("image universe is empty");
            }
            imageUniverse = Collections.unmodifiableSet(images);
            candidateUniverse = Collections.unmodifiableSet(ordered);
        }

        private static Set<ImageMode> imageModes(Set<CaptureModeTuple> tuples) {
            TreeSet<ImageMode> images = new TreeSet<>();
            for (CaptureModeTuple tuple : Objects.requireNonNull(tuples, "candidateUniverse")) {
                images.add(Objects.requireNonNull(tuple, "candidate tuple").imageMode());
            }
            return images;
        }
    }

    public record BenchmarkProtocol(
            int measuredBlocks,
            InitialOrder initialOrder,
            long cooldownMillis) {
        public BenchmarkProtocol {
            if (measuredBlocks <= 0) {
                throw new IllegalArgumentException("measuredBlocks must be positive");
            }
            initialOrder = Objects.requireNonNull(initialOrder, "initialOrder");
            if (cooldownMillis < 0) {
                throw new IllegalArgumentException("cooldownMillis must not be negative");
            }
        }

        public static BenchmarkProtocol defaults() {
            return new BenchmarkProtocol(1, InitialOrder.PIPELINE_A, 0);
        }
    }

    public enum InitialOrder { PIPELINE_A, PIPELINE_B }

    private static List<CameraScope> immutableScopes(Collection<CameraScope> values) {
        TreeMap<CameraId, CameraScope> ordered = new TreeMap<>();
        for (CameraScope value : Objects.requireNonNull(values, "cameras")) {
            CameraScope checked = Objects.requireNonNull(value, "camera scope");
            if (ordered.put(checked.cameraId(), checked) != null) {
                throw new IllegalArgumentException("duplicate benchmark camera");
            }
        }
        return List.copyOf(ordered.values());
    }

    private static Map<CameraId, String> immutableSignatures(Map<CameraId, String> values) {
        TreeMap<CameraId, String> ordered = new TreeMap<>();
        for (var entry : Objects.requireNonNull(values, "cameraHardwareSignatures").entrySet()) {
            ordered.put(Objects.requireNonNull(entry.getKey(), "cameraId"),
                    required(entry.getValue(), "cameraHardwareSignature"));
        }
        return Map.copyOf(ordered);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
