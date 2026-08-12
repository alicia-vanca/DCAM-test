package com.dvid.dcam.app;

import com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class CameraPipelineModeController {
    public enum Result { APPLIED, REJECTED_BUSY, REJECTED_UNAVAILABLE, FAILED }

    public record TransitionResult(boolean ready, Optional<CandidateKey> activeCandidate,
            String detail) {
        public TransitionResult {
            activeCandidate = Objects.requireNonNull(activeCandidate, "activeCandidate");
            if (detail == null || detail.isBlank()) throw new IllegalArgumentException(
                    "detail is required");
        }
    }

    public interface Backend {
        boolean canSwitch(String cameraId);
        Optional<CandidateKey> activeCandidate();
        void prepare(String cameraId, DeveloperSettingsStore.Mode mode, boolean activeCamera,
                Consumer<Boolean> completion);
        Optional<CandidateKey> resolve(String cameraId, DeveloperSettingsStore.Mode mode,
                CandidateKey currentProfile);
        void verify(CandidateKey target, Consumer<TransitionResult> completion);
        void restore(CandidateKey target, Consumer<TransitionResult> completion);
        void commit(String cameraId, DeveloperSettingsStore.Mode mode);
        DeveloperSettingsStore.Mode mode(String cameraId);
    }

    private final Backend backend;
    private boolean transitionInFlight;

    public CameraPipelineModeController(Backend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public synchronized boolean transitionInFlight() {
        return transitionInFlight;
    }

    public boolean canSelect() {
        return backend.activeCandidate().map(value -> canSelect(value.cameraId().value()))
                .orElse(false);
    }

    public synchronized boolean canSelect(String cameraId) {
        return !transitionInFlight && backend.canSwitch(requireCameraId(cameraId));
    }

    public boolean select(DeveloperSettingsStore.Mode mode, Consumer<Result> completion) {
        Objects.requireNonNull(completion, "completion");
        Optional<CandidateKey> active = backend.activeCandidate();
        if (active.isEmpty()) {
            completion.accept(Result.REJECTED_UNAVAILABLE);
            return false;
        }
        return select(active.orElseThrow().cameraId().value(), mode, completion);
    }

    public boolean select(String cameraId, DeveloperSettingsStore.Mode mode,
            Consumer<Result> completion) {
        String selectedCameraId = requireCameraId(cameraId);
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(completion, "completion");
        Optional<CandidateKey> previousActive;
        boolean activeCamera;
        synchronized (this) {
            if (transitionInFlight || !backend.canSwitch(selectedCameraId)) {
                completion.accept(Result.REJECTED_BUSY);
                return false;
            }
            previousActive = backend.activeCandidate();
            activeCamera = previousActive.filter(value ->
                    value.cameraId().value().equals(selectedCameraId)).isPresent();
            transitionInFlight = true;
        }
        try {
            backend.prepare(selectedCameraId, mode, activeCamera, available -> {
                if (!available) {
                    restore(previousActive, activeCamera, Result.REJECTED_UNAVAILABLE, completion);
                } else if (activeCamera) {
                    continueActiveSelection(selectedCameraId, mode,
                            previousActive.orElseThrow(), completion);
                } else {
                    commitInactiveSelection(selectedCameraId, mode, previousActive, completion);
                }
            });
        } catch (RuntimeException error) {
            restore(previousActive, activeCamera, Result.FAILED, completion);
        }
        return true;
    }

    private void continueActiveSelection(String cameraId, DeveloperSettingsStore.Mode mode,
            CandidateKey currentProfile, Consumer<Result> completion) {
        Optional<CandidateKey> target;
        try {
            target = backend.resolve(cameraId, mode, currentProfile);
        } catch (RuntimeException error) {
            restore(Optional.of(currentProfile), true, Result.FAILED, completion);
            return;
        }
        if (target.isEmpty()) {
            restore(Optional.of(currentProfile), true, Result.REJECTED_UNAVAILABLE, completion);
            return;
        }
        Optional<CandidateKey> active = backend.activeCandidate();
        if (active.isPresent() && samePipeline(active.orElseThrow(), target.orElseThrow())) {
            commitActiveSelection(cameraId, mode, currentProfile, target.orElseThrow(), completion);
            return;
        }
        try {
            backend.verify(target.orElseThrow(), result -> {
                boolean success = result.ready() && result.activeCandidate().filter(
                        value -> samePipeline(value, target.orElseThrow())).isPresent();
                if (success) {
                    commitActiveSelection(cameraId, mode, currentProfile,
                            target.orElseThrow(), completion);
                } else {
                    restore(Optional.of(currentProfile), true, Result.FAILED, completion);
                }
            });
        } catch (RuntimeException error) {
            restore(Optional.of(currentProfile), true, Result.FAILED, completion);
        }
    }

    private void commitInactiveSelection(String cameraId, DeveloperSettingsStore.Mode mode,
            Optional<CandidateKey> previousActive, Consumer<Result> completion) {
        Result result;
        try {
            backend.commit(cameraId, mode);
            result = Result.APPLIED;
        } catch (RuntimeException error) {
            result = Result.FAILED;
        }
        restore(previousActive, false, result, completion);
    }

    private void commitActiveSelection(String cameraId, DeveloperSettingsStore.Mode mode,
            CandidateKey previous, CandidateKey target, Consumer<Result> completion) {
        try {
            backend.commit(cameraId, mode);
            finish(Result.APPLIED, completion);
        } catch (RuntimeException error) {
            if (backend.mode(cameraId) == mode || samePipeline(previous, target)) {
                finish(Result.FAILED, completion);
                return;
            }
            try {
                backend.restore(previous, result -> {
                    if (result.activeCandidate().filter(
                            value -> samePipeline(value, previous)).isPresent()) {
                        finish(Result.FAILED, completion);
                        return;
                    }
                    if (result.activeCandidate().filter(
                            value -> samePipeline(value, target)).isPresent()) {
                        try {
                            backend.commit(cameraId, mode);
                        } catch (RuntimeException ignored) {
                        }
                    }
                    finish(Result.FAILED, completion);
                });
            } catch (RuntimeException rollbackError) {
                finish(Result.FAILED, completion);
            }
        }
    }

    private void restore(Optional<CandidateKey> previous, boolean samePipelineEnough,
            Result restoredResult, Consumer<Result> completion) {
        if (previous.isEmpty()) {
            finish(restoredResult, completion);
            return;
        }
        CandidateKey target = previous.orElseThrow();
        Optional<CandidateKey> active = backend.activeCandidate();
        if (active.filter(value -> restored(value, target, samePipelineEnough)).isPresent()) {
            finish(restoredResult, completion);
            return;
        }
        try {
            backend.restore(target, result -> {
                boolean restored = result.activeCandidate().filter(
                        value -> restored(value, target, samePipelineEnough)).isPresent();
                finish(restored ? restoredResult : Result.FAILED, completion);
            });
        } catch (RuntimeException error) {
            finish(Result.FAILED, completion);
        }
    }

    private void finish(Result result, Consumer<Result> completion) {
        synchronized (this) {
            if (!transitionInFlight) return;
            transitionInFlight = false;
        }
        completion.accept(result);
    }

    private static boolean restored(CandidateKey active, CandidateKey target,
            boolean samePipelineEnough) {
        return samePipelineEnough ? samePipeline(active, target) : active.equals(target);
    }

    private static boolean samePipeline(CandidateKey left, CandidateKey right) {
        return left.cameraId().equals(right.cameraId())
                && left.codec() == right.codec()
                && left.verificationPipelineId().equals(right.verificationPipelineId());
    }

    private static String requireCameraId(String cameraId) {
        if (cameraId == null || cameraId.isBlank()) {
            throw new IllegalArgumentException("cameraId is required");
        }
        return cameraId.trim();
    }
}