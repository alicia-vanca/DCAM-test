package com.dvid.dcam.app.ui.camera;

import com.dvid.dcam.app.ui.settings.camera.CameraSettingControlId;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsPresentationState;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingsSource;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class CameraFlowCoordinator {
    public enum State { IDLE, LOADING, VERIFYING, READY, UNAVAILABLE }
    public enum Transition { INITIALIZE, SWITCH_CAMERA, VERIFY_SETTING, RESTORE_EXACT, BIND_COMMITTED }
    public enum RecheckStatus { RUNNING, SUCCEEDED, FAILED }

    public record RecheckUpdate(RecheckStatus status, String detail) {
        public RecheckUpdate {
            Objects.requireNonNull(status, "status");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }
    }

    public record TransitionResult(boolean ready, Optional<CandidateKey> activeCandidate,
            String detail) {
        public TransitionResult {
            activeCandidate = Objects.requireNonNull(activeCandidate, "activeCandidate");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException("detail is required");
            }
        }
    }

    public interface ReleaseTimeoutScheduler {
        TimeoutHandle schedule(Runnable action, long delayMillis);
    }

    public interface TimeoutHandle {
        void cancel();
    }

    public interface StateSubscription extends AutoCloseable {
        @Override
        void close();
    }

    public interface Backend {
        void loadCapabilities(Consumer<List<CandidateKey>> ready, Consumer<String> unavailable);
        default void loadCapabilities(boolean deepVerify, Consumer<List<CandidateKey>> ready,
                Consumer<String> unavailable) {
            loadCapabilities(ready, unavailable);
        }
        void releaseForCapabilityScan(Consumer<Boolean> completion);
        boolean invalidateCapabilities();
        void submit(Transition transition, CandidateKey candidate,
                Consumer<TransitionResult> completion);
        Optional<CandidateKey> resolveSetting(String stableId, int selectedIndex);
        CameraSettingsSource settingsSource(Optional<CandidateKey> target, State state);
        Optional<CandidateKey> activeCandidate();
        default boolean canRestoreExact(CandidateKey candidate) { return false; }
        boolean recording();
        default boolean pipelineModeTransitionInFlight() { return false; }
        default boolean deepVerifyOnStartup() { return true; }
        default List<CandidateKey> switchCandidates() { return List.of(); }
        default int cameraCount() { return switchCandidates().size(); }
        boolean reusableStartup();
        void markCapabilitiesReusable();
        void setStartupPreviewReady(boolean ready);
        default void setStartupUnavailable(String reason) {}
    }

    private static final long RELEASE_TIMEOUT_MILLIS = 15_000L;
    private static final ReleaseTimeoutScheduler NO_TIMEOUT = (action, delayMillis) -> () -> {};

    private final Backend backend;
    private final ReleaseTimeoutScheduler releaseTimeoutScheduler;
    private final List<Runnable> stateObservers = new ArrayList<>();
    private ReleaseAttempt releaseAttempt;
    private State state = State.IDLE;
    private boolean optionsReady;
    private boolean transitionInFlight;
    private CandidateKey target;
    private long generation;
    private Consumer<RecheckUpdate> recheckObserver;
    private boolean idleRecoveryInProgress;
    private CandidateKey idleReleaseCandidate;
    private boolean idleReleaseInFlight;
    private boolean idleRebindRequested;
    private final List<Runnable> idleReadyActions = new ArrayList<>();

    public CameraFlowCoordinator(Backend backend) {
        this(backend, NO_TIMEOUT);
    }

    public CameraFlowCoordinator(Backend backend, ReleaseTimeoutScheduler releaseTimeoutScheduler) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.releaseTimeoutScheduler = Objects.requireNonNull(
                releaseTimeoutScheduler, "releaseTimeoutScheduler");
    }

    public synchronized State state() { return state; }
    public synchronized boolean transitionInFlight() { return transitionInFlight; }
    public synchronized boolean recheckInFlight() { return recheckObserver != null; }

    public StateSubscription observeStateChanges(Runnable observer) {
        Runnable checked = Objects.requireNonNull(observer, "observer");
        synchronized (this) {
            stateObservers.add(checked);
        }
        return () -> {
            synchronized (CameraFlowCoordinator.this) {
                stateObservers.remove(checked);
            }
        };
    }

    public synchronized void start() {
        start(backend.deepVerifyOnStartup());
    }

    private synchronized void start(boolean deepVerify) {
        if (state == State.LOADING || state == State.VERIFYING || state == State.READY
                || transitionInFlight || idleReleaseCandidate != null) return;
        backend.setStartupPreviewReady(false);
        state = State.LOADING;
        optionsReady = false;
        long run = ++generation;
        publishState();
        backend.loadCapabilities(deepVerify,
                candidates -> startLoaded(run, candidates, deepVerify),
                reason -> unavailable(run, reason));
    }

    public void retry() {
        if (prepareReleasedCamera()) return;
        synchronized (this) {
            if (state == State.LOADING || state == State.VERIFYING || transitionInFlight
                    || backend.recording()) return;
            state = State.IDLE;
        }
        start();
    }

    public void bindIfNeeded() {
        if (prepareReleasedCamera()) return;
        synchronized (this) {
            if (state != State.IDLE && state != State.UNAVAILABLE) return;
        }
        start();
    }

    public boolean releaseIdleCamera(Consumer<Boolean> completion) {
        Consumer<Boolean> checkedCompletion = Objects.requireNonNull(completion, "completion");
        long run;
        synchronized (this) {
            if (state != State.READY || transitionInFlight || backend.recording()
                    || backend.pipelineModeTransitionInFlight() || releaseAttempt != null
                    || idleRecoveryInProgress) return false;
            Optional<CandidateKey> active = backend.activeCandidate();
            if (active.isEmpty()) return false;
            idleRecoveryInProgress = true;
            idleReleaseCandidate = active.orElseThrow();
            idleReleaseInFlight = true;
            idleRebindRequested = false;
            target = null;
            state = State.IDLE;
            run = ++generation;
        }
        backend.setStartupPreviewReady(false);
        publishState();
        releaseWithoutTimeout(run,
                released -> finishIdleRelease(run, released, checkedCompletion));
        return true;
    }

    public boolean prepareReleasedCamera() {
        return prepareReleasedCamera(null);
    }

    public boolean runWhenReleasedCameraReady(Runnable action) {
        return prepareReleasedCamera(Objects.requireNonNull(action, "action"));
    }

    public synchronized boolean releasedCameraRecoveryPending() {
        return idleRecoveryInProgress;
    }

    private boolean prepareReleasedCamera(Runnable readyAction) {
        boolean startRebind;
        boolean startFallback;
        synchronized (this) {
            if (!idleRecoveryInProgress) return false;
            if (readyAction != null) idleReadyActions.add(readyAction);
            idleRebindRequested = true;
            startRebind = idleReleaseCandidate != null
                    && !idleReleaseInFlight && !transitionInFlight;
            startFallback = idleReleaseCandidate == null
                    && !idleReleaseInFlight && !transitionInFlight
                    && (state == State.IDLE || state == State.UNAVAILABLE);
        }
        if (startRebind) startIdleRebind();
        else if (startFallback) start(true);
        return true;
    }

    public synchronized int cameraCount() { return backend.cameraCount(); }

    public boolean canSwitchCamera() {
        synchronized (this) {
            if (state != State.READY || transitionInFlight) return false;
        }
        return !backend.recording() && !backend.pipelineModeTransitionInFlight()
                && backend.activeCandidate().isPresent() && backend.cameraCount() > 1;
    }

    public synchronized boolean switchToNextCamera() {
        Optional<CandidateKey> next = nextCameraCandidate();
        if (next.isEmpty()) return false;
        startCameraSwitch(next.orElseThrow());
        return true;
    }

    private Optional<CandidateKey> nextCameraCandidate() {
        if (state != State.READY || transitionInFlight || backend.recording()
                || backend.pipelineModeTransitionInFlight()) return Optional.empty();
        Optional<CandidateKey> active = backend.activeCandidate();
        if (active.isEmpty()) return Optional.empty();
        List<CandidateKey> candidates = uniqueCameraCandidates(backend.switchCandidates());
        if (candidates.size() <= 1) return Optional.empty();
        int activeIndex = -1;
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).cameraId().equals(active.orElseThrow().cameraId())) {
                activeIndex = index;
                break;
            }
        }
        if (activeIndex < 0) return Optional.empty();
        return Optional.of(candidates.get((activeIndex + 1) % candidates.size()));
    }

    public boolean recheckCapabilities() {
        return recheckCapabilities(update -> {});
    }

    public boolean recheckCapabilities(Consumer<RecheckUpdate> observer) {
        Objects.requireNonNull(observer, "observer");
        long run;
        synchronized (this) {
            if (state == State.LOADING || state == State.VERIFYING || transitionInFlight
                    || backend.recording() || backend.pipelineModeTransitionInFlight()
                    || idleRecoveryInProgress) {
                return false;
            }
            state = State.VERIFYING;
            optionsReady = false;
            target = null;
            recheckObserver = observer;
            run = ++generation;
        }
        publishState();
        observer.accept(new RecheckUpdate(RecheckStatus.RUNNING, "recheck_started"));
        releaseForCapabilityScan(run, released -> finishCapabilityRelease(run, released));
        return true;
    }

    public synchronized boolean select(String stableId, int selectedIndex) {
        CameraSettingControlId control;
        try {
            control = CameraSettingControlId.parse(stableId);
        } catch (IllegalArgumentException error) {
            return false;
        }
        if (!canOpenSetting(stableId)) return false;
        Optional<CandidateKey> resolved = backend.resolveSetting(stableId, selectedIndex);
        if (resolved.isEmpty()) return false;
        CandidateKey requested = resolved.orElseThrow();
        if (control.kind() == CameraSettingControlId.Kind.IMAGE_RESOLUTION) {
            target = null;
            return true;
        }
        Optional<CandidateKey> active = backend.activeCandidate();
        if (active.isPresent()
                && !active.orElseThrow().cameraId().equals(requested.cameraId())) {
            target = null;
            return true;
        }
        startSetting(requested);
        return true;
    }

    private void startCameraSwitch(CandidateKey requested) {
        transitionInFlight = true;
        target = requested;
        long run = ++generation;
        Transition transition = backend.canRestoreExact(requested)
                ? Transition.RESTORE_EXACT : Transition.SWITCH_CAMERA;
        publishState();
        backend.submit(transition, requested,
                result -> finishCameraSwitch(run, requested, result));
    }

    private synchronized void finishCameraSwitch(long run, CandidateKey requested,
            TransitionResult result) {
        if (run != generation) return;
        transitionInFlight = false;
        target = null;
        boolean activeTarget = result.ready() && result.activeCandidate()
                .filter(active -> active.cameraId().equals(requested.cameraId())).isPresent();
        state = activeTarget || result.activeCandidate().isPresent()
                ? State.READY : State.UNAVAILABLE;
        publishState();
    }

    private void startSetting(CandidateKey requested) {
        CandidateKey previous = backend.activeCandidate().orElse(null);
        transitionInFlight = true;
        target = requested;
        long run = ++generation;
        Transition transition = previous != null
                && previous.cameraId().equals(requested.cameraId())
                ? Transition.VERIFY_SETTING : Transition.SWITCH_CAMERA;
        publishState();
        backend.submit(transition, requested,
                result -> finishSetting(run, previous, requested, result));
    }

    public synchronized boolean canOpenSetting(String stableId) {
        if (state != State.READY || backend.pipelineModeTransitionInFlight()) return false;
        if (!backend.recording()) return true;
        CameraSettingControlId control;
        try {
            control = CameraSettingControlId.parse(stableId);
        } catch (IllegalArgumentException error) {
            return false;
        }
        if (control.kind() == CameraSettingControlId.Kind.STATUS) return false;
        Optional<CandidateKey> active = backend.activeCandidate();
        return active.isPresent()
                && !active.orElseThrow().cameraId().value().equals(control.cameraId());
    }

    public synchronized CameraSettingsPresentationState settings(boolean recording) {
        CameraSettingsSource source = backend.settingsSource(Optional.ofNullable(target), state);
        Optional<String> activeCameraId = backend.activeCandidate()
                .map(candidate -> candidate.cameraId().value());
        return CameraSettingsPresentationState.from(source, recording,
                state == State.VERIFYING, optionsReady, activeCameraId);
    }

    private void startLoaded(long run, List<CandidateKey> candidates, boolean deepVerify) {
        List<CandidateKey> supplied = List.copyOf(
                Objects.requireNonNull(candidates, "candidates"));
        synchronized (this) {
            if (run != generation) return;
            if (supplied.isEmpty()) {
                state = State.UNAVAILABLE;
                publishState();
                backend.setStartupUnavailable("no_startup_candidates");
                completeRecheck(false, "no_startup_candidates");
                return;
            }
        }
        if (backend.reusableStartup()) {
            boolean verifiedRecheck;
            synchronized (this) {
                verifiedRecheck = run == generation && recheckObserver != null;
            }
            startReusable(run, supplied.get(0), verifiedRecheck || !deepVerify);
            return;
        }
        List<CandidateKey> ordered = startupOrder(supplied);
        synchronized (this) {
            if (run != generation) return;
            state = State.VERIFYING;
            target = ordered.get(0);
        }
        publishState();
        verifyStartup(run, ordered, 0);
    }

    private void startReusable(long run, CandidateKey candidate,
            boolean preserveVerifiedCapabilities) {
        synchronized (this) {
            if (run != generation) return;
            state = State.VERIFYING;
            target = candidate;
        }
        publishState();
        backend.submit(Transition.BIND_COMMITTED, candidate,
                result -> finishReusableStartup(
                        run, candidate, preserveVerifiedCapabilities, result));
    }

    private void finishReusableStartup(long run, CandidateKey candidate,
            boolean preserveVerifiedCapabilities, TransitionResult result) {
        synchronized (this) {
            if (run != generation) return;
            if (result.ready() && result.activeCandidate()
                    .filter(value -> Objects.equals(candidate, value)).isPresent()) {
                target = null;
                optionsReady = true;
                state = State.READY;
                backend.setStartupPreviewReady(true);
                publishState();
                completeRecheck(true, "ready");
                return;
            }
            target = null;
            optionsReady = false;
            state = preserveVerifiedCapabilities ? State.UNAVAILABLE : State.VERIFYING;
        }
        publishState();
        if (preserveVerifiedCapabilities) {
            String detail = "verified_snapshot_bind_failed:" + result.detail();
            backend.setStartupUnavailable(detail);
            completeRecheck(false, detail);
            return;
        }
        releaseForCapabilityScan(run, released -> finishCapabilityRelease(run, released));
    }

    private void verifyStartup(long run, List<CandidateKey> ordered, int index) {
        CandidateKey candidate = ordered.get(index);
        Transition transition = startupTransition(backend.activeCandidate(), candidate);
        backend.submit(transition, candidate, result -> {
            synchronized (CameraFlowCoordinator.this) {
                if (run != generation) return;
                boolean pipelineReady = result.ready() && result.activeCandidate()
                        .filter(activeCandidate -> sameRuntimePipeline(candidate, activeCandidate)).isPresent();
                int next = index + 1;
                if (pipelineReady) {
                    while (next < ordered.size()
                            && ordered.get(next).cameraId().equals(candidate.cameraId())) next++;
                }
                if (next < ordered.size()) {
                    target = ordered.get(next);
                    verifyStartup(run, ordered, next);
                    return;
                }
                if (!pipelineReady) {
                    target = null;
                    state = State.VERIFYING;
                    releaseForCapabilityScan(run, released -> finishStartupFailure(run, released));
                    return;
                }
                target = null;
                backend.markCapabilitiesReusable();
                optionsReady = true;
                state = State.READY;
                backend.setStartupPreviewReady(true);
                publishState();
                completeRecheck(true, "ready");
            }
        });
    }

    private static List<CandidateKey> uniqueCameraCandidates(List<CandidateKey> supplied) {
        Map<CameraId, CandidateKey> unique = new LinkedHashMap<>();
        for (CandidateKey candidate : Objects.requireNonNull(supplied, "supplied candidates")) {
            unique.putIfAbsent(candidate.cameraId(), candidate);
        }
        return List.copyOf(unique.values());
    }

    private static Transition startupTransition(Optional<CandidateKey> active,
            CandidateKey candidate) {
        if (active.isEmpty()) return Transition.INITIALIZE;
        return active.orElseThrow().cameraId().equals(candidate.cameraId())
                ? Transition.VERIFY_SETTING : Transition.SWITCH_CAMERA;
    }

    private static boolean sameRuntimePipeline(CandidateKey requested, CandidateKey active) {
        return active.cameraId().equals(requested.cameraId())
                && active.codec().equals(requested.codec())
                && active.verificationPipelineId().equals(requested.verificationPipelineId());
    }

    private void releaseWithoutTimeout(long run, Consumer<Boolean> completion) {
        ReleaseAttempt attempt = new ReleaseAttempt(run, completion);
        try {
            backend.releaseForCapabilityScan(attempt::complete);
        } catch (RuntimeException error) {
            attempt.complete(false);
        }
    }

    private void releaseForCapabilityScan(long run, Consumer<Boolean> completion) {
        ReleaseAttempt attempt = new ReleaseAttempt(run, completion);
        synchronized (this) {
            if (run != generation) return;
            releaseAttempt = attempt;
        }
        try {
            attempt.schedule();
            backend.releaseForCapabilityScan(attempt::complete);
        } catch (RuntimeException error) {
            attempt.complete(false);
        }
    }

    private void finishIdleRelease(long run, boolean released, Consumer<Boolean> completion) {
        boolean rebind;
        synchronized (this) {
            if (run != generation) return;
            idleReleaseInFlight = false;
            state = released ? State.IDLE : State.UNAVAILABLE;
            rebind = idleRebindRequested;
        }
        publishState();
        try {
            completion.accept(released);
        } finally {
            if (rebind) startIdleRebind();
        }
    }

    private void startIdleRebind() {
        CandidateKey candidate;
        long run;
        synchronized (this) {
            if (idleReleaseCandidate == null || idleReleaseInFlight || transitionInFlight) return;
            candidate = idleReleaseCandidate;
            idleRebindRequested = false;
            transitionInFlight = true;
            state = State.VERIFYING;
            target = candidate;
            run = ++generation;
        }
        publishState();
        backend.submit(Transition.BIND_COMMITTED, candidate,
                result -> finishIdleRebind(run, candidate, result));
    }

    private void finishIdleRebind(long run, CandidateKey candidate, TransitionResult result) {
        boolean ready;
        synchronized (this) {
            if (run != generation) return;
            transitionInFlight = false;
            target = null;
            ready = result.ready() && result.activeCandidate()
                    .filter(value -> Objects.equals(candidate, value)).isPresent();
            state = ready ? State.READY : State.IDLE;
            if (!ready) {
                idleReleaseCandidate = null;
                idleRebindRequested = false;
            }
        }
        if (ready) backend.setStartupPreviewReady(true);
        publishState();
        if (!ready) start(true);
    }

    private void finishStartupFailure(long run, boolean released) {
        synchronized (this) {
            if (run != generation) return;
            optionsReady = false;
            state = State.UNAVAILABLE;
        }
        publishState();
        completeRecheck(false, released
                ? "startup_verification_failed" : "capability_release_failed");
    }

    private void finishCapabilityRelease(long run, boolean released) {
        boolean restart;
        synchronized (this) {
            if (run != generation) return;
            restart = released && backend.invalidateCapabilities();
            state = restart ? State.IDLE : State.UNAVAILABLE;
        }
        if (!restart) {
            publishState();
            completeRecheck(false, released
                    ? "capability_invalidation_failed" : "capability_release_failed");
            return;
        }
        start(true);
    }

    private void finishSetting(long run, CandidateKey previous, CandidateKey requested,
            TransitionResult result) {
        synchronized (this) {
            if (run != generation) return;
            if (!result.ready()) {
                transitionInFlight = false;
                target = null;
                state = result.activeCandidate().isPresent() ? State.READY : State.UNAVAILABLE;
                publishState();
                return;
            }
            boolean inactiveCamera = previous != null
                    && !previous.cameraId().equals(requested.cameraId());
            if (!inactiveCamera || result.activeCandidate()
                    .filter(value -> Objects.equals(previous, value)).isPresent()) {
                transitionInFlight = false;
                target = null;
                state = State.READY;
                publishState();
                return;
            }
            backend.submit(Transition.RESTORE_EXACT, previous,
                    restored -> finishRestore(run, restored));
        }
    }

    private synchronized void finishRestore(long run, TransitionResult result) {
        if (run != generation) return;
        transitionInFlight = false;
        target = null;
        state = result.ready() ? State.READY : State.UNAVAILABLE;
        publishState();
    }

    private void unavailable(long run, String reason) {
        synchronized (this) {
            if (run != generation) return;
            transitionInFlight = false;
            target = null;
            optionsReady = false;
            state = State.UNAVAILABLE;
        }
        publishState();
        String detail = reason == null || reason.isBlank()
                ? "capability_scan_unavailable" : reason;
        backend.setStartupUnavailable(detail);
        completeRecheck(false, detail);
    }

    private final class ReleaseAttempt {
        private final long run;
        private final Consumer<Boolean> completion;
        private boolean completed;
        private TimeoutHandle timeout;

        private ReleaseAttempt(long run, Consumer<Boolean> completion) {
            this.run = run;
            this.completion = Objects.requireNonNull(completion, "completion");
        }

        private void schedule() {
            TimeoutHandle scheduled = releaseTimeoutScheduler.schedule(
                    () -> complete(false), RELEASE_TIMEOUT_MILLIS);
            synchronized (this) {
                if (completed) scheduled.cancel();
                else timeout = scheduled;
            }
        }

        private void complete(boolean released) {
            synchronized (this) {
                if (completed) return;
                completed = true;
                if (timeout != null) timeout.cancel();
            }
            synchronized (CameraFlowCoordinator.this) {
                if (releaseAttempt == this) releaseAttempt = null;
                if (run != generation) return;
            }
            completion.accept(released);
        }
    }

    private void publishState() {
        List<Runnable> observers;
        List<Runnable> readyActions = List.of();
        synchronized (this) {
            if (state == State.READY && idleRecoveryInProgress) {
                idleRecoveryInProgress = false;
                idleReleaseCandidate = null;
                idleRebindRequested = false;
                readyActions = List.copyOf(idleReadyActions);
                idleReadyActions.clear();
            }
            observers = List.copyOf(stateObservers);
        }
        observers.forEach(Runnable::run);
        readyActions.forEach(Runnable::run);
    }

    private void completeRecheck(boolean successful, String detail) {
        Consumer<RecheckUpdate> observer;
        synchronized (this) {
            observer = recheckObserver;
            recheckObserver = null;
        }
        if (observer == null) return;
        observer.accept(new RecheckUpdate(
                successful ? RecheckStatus.SUCCEEDED : RecheckStatus.FAILED, detail));
    }

    private static List<CandidateKey> startupOrder(List<CandidateKey> candidates) {
        List<CandidateKey> supplied = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (supplied.size() <= 1) return supplied;
        CameraId primary = supplied.get(0).cameraId();
        int firstOtherCamera = 1;
        while (firstOtherCamera < supplied.size()
                && supplied.get(firstOtherCamera).cameraId().equals(primary)) firstOtherCamera++;
        if (firstOtherCamera == supplied.size()) return supplied;
        List<CandidateKey> result = new ArrayList<>(supplied.size());
        result.addAll(supplied.subList(firstOtherCamera, supplied.size()));
        result.addAll(supplied.subList(0, firstOtherCamera));
        return List.copyOf(result);
    }

}
