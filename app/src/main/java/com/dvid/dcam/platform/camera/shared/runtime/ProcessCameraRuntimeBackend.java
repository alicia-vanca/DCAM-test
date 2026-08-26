package com.dvid.dcam.platform.camera.shared.runtime;

import com.dvid.dcam.feature.device.domain.camera.CameraOperationContext;
import java.util.Objects;
import java.util.Optional;

public interface ProcessCameraRuntimeBackend {

    enum Operation {
        INITIALIZE,
        SWITCH_CAMERA,
        VERIFY_SETTING,
        BIND_COMMITTED,
        RESTORE_EXACT,
        RELEASE,
        START_RECORDING,
        STOP_RECORDING,
        CAPTURE_PHOTO,
        RECOVER
    }

    enum Outcome {
        READY,
        ROLLED_BACK_READY,
        PASS,
        TARGET_FAILED,
        RECOVERY_REQUIRED,
        BLOCKED,
        CANCELLED
    }

    record Command(
            Operation operation,
            long operationSequence,
            long transitionGeneration,
            long healthGeneration,
            Optional<CameraRuntimeSelection> target,
            Optional<CameraRuntimeSelection> previous,
            Optional<CameraOperationContext> activeBinding) {
        public Command {
            operation = Objects.requireNonNull(operation, "operation");
            if (operationSequence <= 0) {
                throw new IllegalArgumentException("operationSequence must be positive");
            }
            if (transitionGeneration < 0 || healthGeneration < 0) {
                throw new IllegalArgumentException("generations must not be negative");
            }
            target = Objects.requireNonNull(target, "target");
            previous = Objects.requireNonNull(previous, "previous");
            activeBinding = Objects.requireNonNull(activeBinding, "activeBinding");
        }
    }

    record Result(
            Outcome outcome,
            Optional<CameraRuntimeSelection> selection,
            Optional<CameraOperationContext> activeBinding,
            String detail) {
        public Result {
            outcome = Objects.requireNonNull(outcome, "outcome");
            selection = Objects.requireNonNull(selection, "selection");
            activeBinding = Objects.requireNonNull(activeBinding, "activeBinding");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
            if ((outcome == Outcome.READY || outcome == Outcome.ROLLED_BACK_READY)
                    && (selection.isEmpty() || activeBinding.isEmpty())) {
                throw new IllegalArgumentException(
                        "ready outcome requires selection and active binding");
            }
            if (selection.isPresent() != activeBinding.isPresent()) {
                throw new IllegalArgumentException(
                        "selection and active binding must be present together");
            }
            if (selection.isPresent()
                    && !selection.orElseThrow().matches(activeBinding.orElseThrow())) {
                throw new IllegalArgumentException(
                        "active binding does not match selection");
            }
        }

        public static Result ready(CameraRuntimeSelection selection,
                CameraOperationContext activeBinding, String detail) {
            return new Result(Outcome.READY, Optional.of(selection),
                    Optional.of(activeBinding), detail);
        }

        public static Result rolledBackReady(CameraRuntimeSelection selection,
                CameraOperationContext activeBinding, String detail) {
            return new Result(Outcome.ROLLED_BACK_READY, Optional.of(selection),
                    Optional.of(activeBinding), detail);
        }

        public static Result pass(String detail) {
            return new Result(Outcome.PASS, Optional.empty(), Optional.empty(), detail);
        }

        public static Result pass(CameraRuntimeSelection selection,
                CameraOperationContext activeBinding, String detail) {
            return retaining(Outcome.PASS, selection, activeBinding, detail);
        }

        public static Result targetFailed(String detail) {
            return new Result(Outcome.TARGET_FAILED,
                    Optional.empty(), Optional.empty(), detail);
        }

        public static Result recoveryRequired(String detail) {
            return new Result(Outcome.RECOVERY_REQUIRED,
                    Optional.empty(), Optional.empty(), detail);
        }


        public static Result blocked(String detail) {
            return new Result(Outcome.BLOCKED,
                    Optional.empty(), Optional.empty(), detail);
        }

        public static Result blocked(CameraRuntimeSelection selection,
                CameraOperationContext activeBinding, String detail) {
            return retaining(Outcome.BLOCKED, selection, activeBinding, detail);
        }

        public static Result cancelled(String detail) {
            return new Result(Outcome.CANCELLED,
                    Optional.empty(), Optional.empty(), detail);
        }

        public static Result cancelled(CameraRuntimeSelection selection,
                CameraOperationContext activeBinding, String detail) {
            return retaining(Outcome.CANCELLED, selection, activeBinding, detail);
        }

        private static Result retaining(Outcome outcome, CameraRuntimeSelection selection,
                CameraOperationContext activeBinding, String detail) {
            return new Result(outcome, Optional.of(selection), Optional.of(activeBinding), detail);
        }
    }

    record HealthSnapshot(
            boolean sessionBound,
            boolean recoveryRequired,
            boolean previewSignalAvailable,
            long sourceFrameCount,
            long previewFrameCount,
            String detail) {
        public HealthSnapshot {
            if (sourceFrameCount < 0L || previewFrameCount < 0L) {
                throw new IllegalArgumentException("frame counts must not be negative");
            }
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }
    }

    @FunctionalInterface
    interface Completion {
        void complete(Result result);
    }

    void execute(Command command, Completion completion);

    default void cancel(Command command) {
        Objects.requireNonNull(command, "command");
    }

    default void setPreviewExpected(boolean expected) {}

    default Optional<HealthSnapshot> healthSnapshot(CameraOperationContext activeBinding) {
        Objects.requireNonNull(activeBinding, "activeBinding");
        return Optional.empty();
    }
}
