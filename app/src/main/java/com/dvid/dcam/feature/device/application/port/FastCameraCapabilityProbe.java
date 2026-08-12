package com.dvid.dcam.feature.device.application.port;

import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


public interface FastCameraCapabilityProbe {
    VerificationPipelineId pipelineId();

    BatchResult probeBatch(List<Request> requests, CancellationSignal cancellationSignal);

    enum Completion {
        COMPLETE,
        PIPELINE_UNAVAILABLE,
        INCOMPLETE_TRANSIENT,
        INCOMPLETE_GLOBAL,
        BLOCKED_EXTERNAL,
        CANCELLED
    }

    @FunctionalInterface
    interface CancellationSignal {
        CancellationSignal NEVER = () -> false;

        boolean isCancellationRequested();
    }

    record Request(
            CameraId cameraId,
            List<VideoMode> videoModes,
            List<ImageMode> imageModes) {
        public Request {
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            videoModes = immutableUniqueInOrder(videoModes, "videoModes");
            imageModes = immutableUniqueInOrder(imageModes, "imageModes");
        }
    }

    record CameraResult(
            PipelineEvidence evidence,
            Completion completion,
            long elapsedMillis,
            String detail) {
        public CameraResult {
            evidence = Objects.requireNonNull(evidence, "evidence");
            completion = Objects.requireNonNull(completion, "completion");
            if (elapsedMillis < 0) {
                throw new IllegalArgumentException("elapsedMillis must not be negative");
            }
            detail = required(detail, "detail");
        }

        public boolean terminal() {
            return completion == Completion.COMPLETE
                    || completion == Completion.PIPELINE_UNAVAILABLE;
        }
    }

    record BatchResult(
            Completion completion,
            List<CameraResult> cameraResults,
            boolean cleanupComplete,
            long elapsedMillis,
            String detail) {
        public BatchResult {
            completion = Objects.requireNonNull(completion, "completion");
            if (completion == Completion.PIPELINE_UNAVAILABLE) {
                throw new IllegalArgumentException(
                        "pipeline unavailable is a per-camera completion");
            }
            cameraResults = immutableUniqueResults(cameraResults);
            if (elapsedMillis < 0) {
                throw new IllegalArgumentException("elapsedMillis must not be negative");
            }
            detail = required(detail, "detail");
        }

        public boolean complete() {
            return completion == Completion.COMPLETE && cleanupComplete;
        }
    }

    private static <T> List<T> immutableUniqueInOrder(
            Collection<T> values, String name) {
        ArrayList<T> result = new ArrayList<>();
        Set<T> seen = new HashSet<>();
        for (T value : Objects.requireNonNull(values, name)) {
            T checked = Objects.requireNonNull(value, name + " element");
            if (seen.add(checked)) result.add(checked);
        }
        return List.copyOf(result);
    }

    private static List<CameraResult> immutableUniqueResults(
            Collection<CameraResult> values) {
        ArrayList<CameraResult> result = new ArrayList<>();
        Set<CameraId> cameraIds = new HashSet<>();
        for (CameraResult value : Objects.requireNonNull(values, "cameraResults")) {
            CameraResult checked = Objects.requireNonNull(value, "cameraResult");
            if (!cameraIds.add(checked.evidence().cameraId())) {
                throw new IllegalArgumentException("duplicate camera result");
            }
            result.add(checked);
        }
        result.sort(Comparator.comparing(value -> value.evidence().cameraId()));
        return List.copyOf(result);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}