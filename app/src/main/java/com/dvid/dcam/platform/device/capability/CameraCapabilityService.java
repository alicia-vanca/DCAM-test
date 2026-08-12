package com.dvid.dcam.platform.device.capability;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.platform.camera.shared.runtime.ProcessCameraRuntimeOwner;
import com.dvid.dcam.platform.camera.shared.benchmark.ProductionCameraCapabilityRecheck;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.SelectedRecordingProfile;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.LoadResult;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.LoadStatus;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.ConcurrentCameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.RawCatalog;
import com.dvid.dcam.feature.device.application.usecase.CameraCapabilitySnapshotUpdates;
import com.dvid.dcam.feature.device.application.usecase.ResolveCameraRuntimeSelectionUseCase;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.FastSnapshot;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.Progress;
import com.dvid.dcam.feature.device.application.usecase.BuildFastCameraCapabilitiesUseCase.Result;
import com.dvid.dcam.feature.device.application.usecase.FinalizeVerifiedCameraCapabilitiesUseCase.Summary;
import com.dvid.dcam.feature.device.domain.CaptureQuality;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.platform.device.capability.catalog.AndroidCameraCatalogSource;
import com.dvid.dcam.platform.device.capability.fast.AndroidFastCameraCapabilityProbe;
import com.dvid.dcam.platform.device.capability.probe.egl.EglFanOutFastProbe;
import com.dvid.dcam.platform.device.capability.probe.nativesharing.NativeSurfaceSharingFastProbe;
import com.dvid.dcam.platform.device.capability.store.AtomicCameraCapabilityStore;
import com.dvid.dcam.platform.permission.DcamPermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CameraCapabilityService implements CameraCapabilityStore {
    public record PipelineSelection(String cameraId, String pipelineId, int tupleCount) {
        public PipelineSelection {
            if (cameraId == null || cameraId.isBlank()
                    || pipelineId == null || pipelineId.isBlank() || tupleCount < 0) {
                throw new IllegalArgumentException("invalid pipeline selection");
            }
        }
    }

    public record RecheckProgress(String profile, String cameraId, String stage,
            int completed, int total, String detail) {
        public RecheckProgress {
            profile = Objects.requireNonNull(profile, "profile");
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            stage = Objects.requireNonNull(stage, "stage");
            detail = Objects.requireNonNull(detail, "detail");
            if (profile.isBlank() || cameraId.isBlank() || stage.isBlank()
                    || detail.isBlank()) {
                throw new IllegalArgumentException("progress text is required");
            }
            if (completed < 0 || total <= 0 || completed > total) {
                throw new IllegalArgumentException("invalid progress range");
            }
        }
    }

    private final Context context;
    private final Logger logger;
    private final AtomicCameraCapabilityStore store;
    private final RequestedCameraSelectionStore selections;
    private final CameraCapabilityAuthority authority = new CameraCapabilityAuthority();
    private final ProcessCameraRuntimeOwner runtimeOwner;
    private final ResolveCameraRuntimeSelectionUseCase selectionResolver;

    private final Executor callbackExecutor;
    private final ExecutorService scanExecutor;
    private final List<Runnable> readyCallbacks = new ArrayList<>();
    private final List<Runnable> completionCallbacks = new ArrayList<>();
    private final List<Consumer<RecheckProgress>> progressCallbacks = new ArrayList<>();
    private boolean started;
    private boolean startupFastCollectionInFlight;
    private boolean scanInFlight;
    private boolean forceRescan;
    private boolean scanAwaitingRuntimeRelease;
    private ProcessCameraRuntimeOwner.Attachment scanReleaseAttachment;
    private List<VerificationPipelineId> pendingScanPipelines = List.of();
    private long generation;
    private volatile Optional<VerificationPipelineId> forcedPipeline = Optional.empty();
    private final Map<String, Optional<VerificationPipelineId>> forcedPipelines = new HashMap<>();
    private boolean deferredWrites;
    private Snapshot deferredSnapshot;
    private boolean lastScanSuccessful;
    private Optional<Summary> lastRecheckSummary = Optional.empty();

    public CameraCapabilityService(Context context, Logger logger,
            AtomicCameraCapabilityStore store, RequestedCameraSelectionStore selections,
            ProcessCameraRuntimeOwner runtimeOwner,
            ResolveCameraRuntimeSelectionUseCase selectionResolver,
            Executor callbackExecutor, ExecutorService scanExecutor) {
        Context applicationContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
        this.logger = Objects.requireNonNull(logger, "logger");
        this.store = Objects.requireNonNull(store, "store");
        this.selections = Objects.requireNonNull(selections, "selections");
        this.runtimeOwner = Objects.requireNonNull(runtimeOwner, "runtimeOwner");
        this.selectionResolver = Objects.requireNonNull(selectionResolver, "selectionResolver");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
        this.scanExecutor = Objects.requireNonNull(scanExecutor, "scanExecutor");
    }

    public ProcessCameraRuntimeOwner runtimeOwner() {
        return runtimeOwner;
    }

    public synchronized Optional<Snapshot> currentSnapshot() {
        return authority.snapshot();
    }

    public synchronized boolean loadPersistedSnapshot() {
        return loadPersistedSnapshot(cameraId -> forcedPipelines.getOrDefault(cameraId, forcedPipeline));
    }

    public synchronized boolean loadPersistedSnapshot(
            Function<String, Optional<VerificationPipelineId>> pipelineSelection) {
        Objects.requireNonNull(pipelineSelection, "pipelineSelection");
        if (startupFastCollectionInFlight || scanInFlight || scanAwaitingRuntimeRelease) {
            logger.info("camera_capability_owner stage=load_persisted"
                    + " outcome=defer reason=scan_in_progress");
            return false;
        }
        LoadResult loaded = store.loadPersisted();
        if (loaded.status() != LoadStatus.LOADED || loaded.snapshot().isEmpty()) {
            authority.unavailable("persisted_snapshot_unavailable:" + loaded.reason().name());
            lastScanSuccessful = false;
            return false;
        }
        try {
            Snapshot persisted = loaded.snapshot().orElseThrow();
            Map<String, Optional<VerificationPipelineId>> configured = new HashMap<>();
            for (var camera : persisted.cameras()) {
                String cameraId = camera.cameraId().value();
                configured.put(cameraId, Objects.requireNonNull(
                        pipelineSelection.apply(cameraId), "pipeline selection"));
            }
            Snapshot selected = selectionSnapshotForModes(
                    persisted, configured, forcedPipeline, selectionResolver);
            forcedPipelines.clear();
            forcedPipelines.putAll(configured);
            authority.current(selected);
            lastScanSuccessful = true;
            logger.info("Loaded saved camera capabilities for " + selected.cameras().size()
                    + " cameras. No capability scan was needed.");
            return true;
        } catch (RuntimeException error) {
            authority.unavailable("persisted_snapshot_invalid_selection");
            lastScanSuccessful = false;
            logger.warn("Saved camera capabilities contain an invalid camera selection."
                    + " A new capability scan is required.", error);
            return false;
        }
    }

    @Override
    public LoadResult load(CameraCapabilityStore.Freshness freshness) {
        return store.load(freshness);
    }

    @Override
    public synchronized void requestWrite(Snapshot snapshot) {
        Snapshot value = Objects.requireNonNull(snapshot, "snapshot");
        if (deferredWrites) {
            deferredSnapshot = value;
            logger.info("camera_capability_owner stage=deferred_write action=hold");
            return;
        }
        authority.current(value);
        store.requestWrite(value);
    }

    @Override
    public synchronized void writeNow(Snapshot snapshot) {
        Snapshot value = Objects.requireNonNull(snapshot, "snapshot");
        if (deferredWrites) {
            deferredSnapshot = value;
            logger.info("camera_capability_owner stage=deferred_write action=hold");
            return;
        }
        store.writeNow(value);
        authority.current(value);
    }

    public synchronized void restoreSelectedRecordingProfile(
            CameraId cameraId, Optional<SelectedRecordingProfile> selection) {
        Snapshot current = authority.snapshot()
                .orElseThrow(() -> new IllegalStateException("capability snapshot unavailable"));
        Snapshot restored;
        try {
            restored = CameraCapabilitySnapshotMapper.withSelectedRecordingProfile(
                    current, cameraId, selection);
        } catch (IllegalArgumentException invalidSelection) {
            if (selection.isEmpty())
                throw invalidSelection;
            restored = CameraCapabilitySnapshotMapper.withSelectedRecordingProfile(
                    current, cameraId, Optional.empty());
            logger.warn("camera_capability_owner stage=selection_restore cameraId=" + cameraId
                    + " action=clear_invalid_previous", invalidSelection);
        }
        requestWrite(restored);
        logger.info("camera_capability_owner stage=selection_restore cameraId=" + cameraId
                + " selectionPresent=" + restored.cameras().stream()
                        .filter(camera -> camera.cameraId().equals(cameraId))
                        .findFirst().orElseThrow().selectedRecordingProfile().isPresent());
    }

    public synchronized boolean beginDeferredWrites() {
        if (deferredWrites)
            return false;
        deferredWrites = true;
        deferredSnapshot = null;
        logger.info("camera_capability_owner stage=deferred_write action=begin");
        return true;
    }

    public void completeDeferredWrites(boolean commit) {
        Snapshot value;
        synchronized (this) {
            if (!deferredWrites)
                return;
            value = commit ? deferredSnapshot : null;
            deferredSnapshot = null;
            deferredWrites = false;
        }
        if (value != null) {
            authority.current(value);
            store.requestWrite(value);
            logger.info("camera_capability_owner stage=deferred_write action=commit");
        } else {
            logger.info("camera_capability_owner stage=deferred_write action=discard");
        }
    }

    public synchronized void configurePipelineSelection(
            Optional<VerificationPipelineId> pipeline) {
        forcedPipeline = Objects.requireNonNull(pipeline, "pipeline");
        forcedPipelines.clear();
        logger.info(pipeline.isEmpty()
                ? "Camera pipeline selection is automatic."
                : "Camera pipeline selection uses " + pipeline.orElseThrow().value() + ".");
    }

    public synchronized void configurePipelineSelection(String cameraId,
            Optional<VerificationPipelineId> pipeline) {
        String id = requiredCameraId(cameraId);
        Optional<VerificationPipelineId> value = Objects.requireNonNull(pipeline, "pipeline");
        forcedPipelines.put(id, value);
        logger.info(value.isEmpty()
                ? "Camera " + id + " uses automatic pipeline selection."
                : "Camera " + id + " uses pipeline " + value.orElseThrow().value() + ".");
    }

    public synchronized void persistPipelineSelection(
            Optional<VerificationPipelineId> pipeline) {
        Snapshot current = authority.snapshot()
                .orElseThrow(() -> new IllegalStateException("capability snapshot unavailable"));
        Snapshot updated = selectionSnapshotForMode(current,
                Objects.requireNonNull(pipeline, "pipeline"), selectionResolver);
        requireCompletePipelineSelection(updated);
        writeNow(updated);
    }

    public synchronized void persistPipelineSelection(String cameraId,
            Optional<VerificationPipelineId> pipeline) {
        Snapshot current = authority.snapshot()
                .orElseThrow(() -> new IllegalStateException("capability snapshot unavailable"));
        Snapshot updated = selectionSnapshotForCameraMode(current, requiredCameraId(cameraId),
                Objects.requireNonNull(pipeline, "pipeline"), selectionResolver);
        requireCompletePipelineSelection(updated);
        writeNow(updated);
    }

    public synchronized boolean hasPipelineEvidence(String cameraId,
            Optional<VerificationPipelineId> pipeline) {
        Snapshot current = snapshot();
        CameraId id = cameraId(requiredCameraId(cameraId));
        if (current == null || id == null)
            return false;
        return hasPipelineEvidence(current, id, scanPipelinesForSelection(
                Objects.requireNonNull(pipeline, "pipeline")));
    }

    private static boolean hasPipelineEvidence(Snapshot current, CameraId cameraId,
            List<VerificationPipelineId> required) {
        return current.cameras().stream()
                .filter(camera -> camera.cameraId().equals(cameraId))
                .findFirst()
                .map(camera -> required.stream().allMatch(requiredPipeline -> camera.codecs().stream()
                        .filter(codec -> codec.codec() == com.dvid.dcam.feature.device.domain.camera.VideoCodec.H264)
                        .flatMap(codec -> codec.pipelines().stream())
                        .anyMatch(value -> value.verificationPipelineId()
                                .equals(requiredPipeline))))
                .orElse(false);
    }

    public List<CandidateKey> startupCandidates() {
        Snapshot selectedSnapshot = selectionSnapshot();
        if (selectedSnapshot == null)
            return List.of();
        List<CameraId> order = selectionResolver.cameraOrder(selectedSnapshot);
        if (order.isEmpty())
            return List.of();
        CameraId main = order.get(0);
        if (isInitializationReusable(selectedSnapshot)) {
            Optional<CandidateKey> committed = selectedProfileCandidate(selectedSnapshot, main);
            committed.ifPresent(this::saveRequestedCandidate);
            return committed.map(List::of).orElseGet(List::of);
        }
        for (CameraId cameraId : order) {
            List<CandidateKey> requested = selectionResolver.requestedCandidates(selectedSnapshot,
                    cameraId, selectedRecordQuality(cameraId.value()),
                    selectedRecordFrameRate(cameraId.value()), selectedImageQuality(cameraId.value()));
            if (requested.isEmpty()) {
                requested = selectionResolver.defaultCandidates(selectedSnapshot, cameraId);
            }
            requested.stream().findFirst().ifPresent(this::saveRequestedCandidate);
        }
        List<CandidateKey> requested = selectionResolver.requestedCandidates(selectedSnapshot, main,
                selectedRecordQuality(main.value()), selectedRecordFrameRate(main.value()),
                selectedImageQuality(main.value()));
        if (requested.isEmpty()) {
            requested = selectionResolver.defaultCandidates(selectedSnapshot, main);
        }
        return List.copyOf(requested);
    }

    public synchronized boolean isInitializationReusable() {
        return isInitializationReusable(selectionSnapshot());
    }

    private boolean isInitializationReusable(Snapshot selected) {
        if (selected == null
                || selected.initializationState() != InitializationState.READY_REUSABLE
                || !CameraCapabilitySnapshotMapper.hasVerifiedSelectionForMainCamera(selected)) {
            return false;
        }
        CameraId main = selectionResolver.cameraOrder(selected).get(0);
        return selected.cameras().stream().filter(camera -> camera.cameraId().equals(main))
                .findFirst().flatMap(camera -> camera.selectedPipeline()
                        .map(CameraCapabilityStore.SelectedPipeline::pipelineId)
                        .flatMap(pipeline -> camera.selectedRecordingProfile()
                                .filter(selection -> selection.verificationPipelineId()
                                        .equals(pipeline))))
                .isPresent();
    }

    public synchronized void markInitializationReusable() {
        Snapshot current = authority.snapshot()
                .orElseThrow(() -> new IllegalStateException("capability snapshot unavailable"));
        Snapshot selected = selectionSnapshotForConfiguredModes(current);
        requireCompletePipelineSelection(selected);
        if (!CameraCapabilitySnapshotMapper.hasVerifiedSelectionForMainCamera(selected)) return;
        Snapshot reusable = selected.withInitializationState(InitializationState.READY_REUSABLE);
        writeNow(reusable);
        logger.info("camera_capability_owner stage=initialization_state"
                + " outcome=ready_reusable cameraCount=" + reusable.cameras().size());
    }

    public Optional<CandidateKey> requestedCandidate(String cameraId) {
        Snapshot snapshot = selectionSnapshot();
        CameraId id = cameraId(cameraId);
        if (snapshot == null || id == null) {
            String reason = snapshot == null
                    ? "camera capability data is unavailable"
                    : "camera is not present in camera capability data";
            logger.info("Could not select a capture profile for camera " + cameraId
                    + " because " + reason + ".");
            return Optional.empty();
        }
        String savedVideo = selections.recordQuality(id);
        int savedFrameRate = selections.recordFrameRate(id);
        String savedImage = selections.imageQuality(id);
        Set<String> runtimeRejections = selections.runtimeRejections();
        Optional<CandidateKey> requested = validatedSavedCandidate(snapshot, id,
                savedVideo, savedFrameRate, savedImage, runtimeRejections);
        if (requested.isEmpty()) {
            CaptureQuality video = CameraCapabilityOptions.selectedVideo(snapshot, id.value(),
                    savedVideo, savedFrameRate, runtimeRejections);
            CaptureQuality image = CameraCapabilityOptions.selectedImage(snapshot, id.value(),
                    savedImage, runtimeRejections);
            requested = video == null || image == null ? Optional.empty()
                    : selectionResolver.requestedCandidate(snapshot, id, video.getId(),
                            video.getFrameRate(), image.getId());
        }
        requested.ifPresent(this::saveRequestedCandidate);
        return requested;
    }

    public Optional<CandidateKey> requestedCandidate(String cameraId, String videoId,
            int frameRate, String imageId) {
        return requestedCandidate(cameraId, videoId, frameRate, imageId,
                pipelineSelection(cameraId));
    }

    public Optional<CandidateKey> requestedCandidate(String cameraId, String videoId,
            int frameRate, String imageId, Optional<VerificationPipelineId> pipeline) {
        Snapshot snapshot = selectionSnapshot(pipeline);
        CameraId id = cameraId(cameraId);
        if (snapshot == null || id == null)
            return Optional.empty();
        return selectionResolver.requestedCandidate(
                snapshot, id, videoId, frameRate, imageId);
    }

    public Optional<CandidateKey> requestedProfileCandidate(CandidateKey currentProfile,
            Optional<VerificationPipelineId> pipeline) {
        Objects.requireNonNull(currentProfile, "currentProfile");
        Snapshot selected = selectionSnapshot(Objects.requireNonNull(pipeline, "pipeline"));
        if (selected == null || currentProfile.tuple().isEmpty())
            return Optional.empty();
        return selectionResolver.requestedProfileCandidate(selected, currentProfile.cameraId(),
                currentProfile.tuple().orElseThrow());
    }

    public void saveRequestedCandidate(CandidateKey candidate) {
        Objects.requireNonNull(candidate, "candidate");
        var tuple = candidate.tuple().orElseThrow();
        selections.saveRecordQuality(candidate.cameraId(),
                tuple.videoMode().resolution().label().name());
        selections.saveRecordFrameRate(candidate.cameraId(),
                tuple.videoMode().framesPerSecond());
        selections.saveImageQuality(candidate.cameraId(),
                tuple.imageMode().resolution().label().name());
    }

    public void applyRecordingFallback(CandidateKey requested, CandidateKey effective) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(effective, "effective");
        Snapshot current = authority.snapshot().orElse(null);
        if (current == null)
            return;
        var requestedTuple = requested.tuple().orElseThrow();
        var effectiveTuple = effective.tuple().orElseThrow();
        boolean requestedQualityAvailable = recordingVideoSelectionAvailable(
                current, requested, false);
        boolean requestedFrameRateAvailable = recordingVideoSelectionAvailable(
                current, requested, true);
        if (!requestedQualityAvailable
                && !requestedTuple.videoMode().resolution().label().equals(
                        effectiveTuple.videoMode().resolution().label())) {
            selections.saveRecordQuality(effective.cameraId(),
                    effectiveTuple.videoMode().resolution().label().name());
        }
        if (!requestedFrameRateAvailable
                && requestedTuple.videoMode().framesPerSecond() != effectiveTuple.videoMode().framesPerSecond()) {
            selections.saveRecordFrameRate(effective.cameraId(),
                    effectiveTuple.videoMode().framesPerSecond());
        }
    }

    static boolean recordingVideoSelectionAvailable(Snapshot snapshot,
            CandidateKey requested, boolean requireRequestedFrameRate) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(requested, "requested");
        var requestedVideo = requested.videoMode().orElseThrow();
        for (var camera : snapshot.cameras()) {
            if (!camera.cameraId().equals(requested.cameraId()))
                continue;
            for (var codec : camera.codecs()) {
                if (codec.codec() != requested.codec())
                    continue;
                for (var pipeline : codec.pipelines()) {
                    if (!pipeline.verificationPipelineId().equals(
                            requested.verificationPipelineId()))
                        continue;
                    for (CandidateKey candidate : pipeline.effectiveCandidates()) {
                        if (candidate.kind() != CandidateKey.Kind.TUPLE)
                            continue;
                        var video = candidate.videoMode().orElseThrow();
                        if (!video.resolution().label().equals(
                                requestedVideo.resolution().label()))
                            continue;
                        if (!requireRequestedFrameRate
                                || video.framesPerSecond() == requestedVideo.framesPerSecond()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public VerificationOutcome standaloneImageOutcome(CandidateKey requested) {
        Objects.requireNonNull(requested, "requested");
        Snapshot current = authority.snapshot().orElse(null);
        if (current == null)
            return VerificationOutcome.UNKNOWN;
        CandidateKey image = CandidateKey.forImage(requested.cameraId(), requested.codec(),
                requested.verificationPipelineId(), requested.imageMode().orElseThrow());
        for (var camera : current.cameras()) {
            if (!camera.cameraId().equals(requested.cameraId()))
                continue;
            for (var codec : camera.codecs()) {
                if (codec.codec() != requested.codec())
                    continue;
                for (var pipeline : codec.pipelines()) {
                    if (pipeline.verificationPipelineId().equals(
                            requested.verificationPipelineId())) {
                        return pipeline.outcome(image);
                    }
                }
            }
        }
        return VerificationOutcome.UNKNOWN;
    }

    public synchronized Optional<String> recordStandaloneImageOutcome(
            CandidateKey requested, VerificationOutcome outcome) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(outcome, "outcome");
        if (outcome != VerificationOutcome.VERIFIED_PASS
                && outcome != VerificationOutcome.DEFINITIVE_UNSUPPORTED) {
            throw new IllegalArgumentException("standalone image outcome must be durable");
        }
        Snapshot current = authority.snapshot()
                .orElseThrow(() -> new IllegalStateException("capability snapshot unavailable"));
        Snapshot updated = CameraCapabilitySnapshotUpdates.withStandaloneImageEvidence(
                current, requested, outcome);
        requestWrite(updated);
        if (outcome == VerificationOutcome.VERIFIED_PASS)
            return Optional.empty();
        Snapshot selectedUpdated = selectionSnapshotForConfiguredModes(updated);
        CaptureQuality fallback = standaloneImageFallback(
                CameraCapabilityOptions.imageQualities(selectedUpdated,
                        requested.cameraId().value(), selections.runtimeRejections()),
                requested.imageMode().orElseThrow().resolution().actual().width(),
                requested.imageMode().orElseThrow().resolution().actual().height());
        if (fallback == null)
            return Optional.empty();
        selections.saveImageQuality(requested.cameraId(), fallback.getId());
        return Optional.of(fallback.getId());
    }

    static CaptureQuality standaloneImageFallback(List<CaptureQuality> qualities,
            int requestedWidth, int requestedHeight) {
        Objects.requireNonNull(qualities, "qualities");
        long requestedArea = (long) requestedWidth * requestedHeight;
        CaptureQuality lower = null;
        CaptureQuality higher = null;
        for (CaptureQuality quality : qualities) {
            long area = (long) quality.getWidth() * quality.getHeight();
            if (area <= requestedArea) {
                if (lower == null || area > (long) lower.getWidth() * lower.getHeight()) {
                    lower = quality;
                }
            } else if (higher == null
                    || area < (long) higher.getWidth() * higher.getHeight()) {
                higher = quality;
            }
        }
        return lower == null ? higher : lower;
    }

    public Optional<CandidateKey> committedCandidate(String cameraId) {
        Snapshot snapshot = snapshot();
        CameraId id = cameraId(cameraId);
        return snapshot == null || id == null
                ? Optional.empty()
                : selectedProfileCandidate(snapshot, id);
    }

    public synchronized void start() {
        if (started)
            return;
        started = true;
        long appMillis = Math.max(0L, SystemClock.elapsedRealtime()
                - Process.getStartElapsedRealtime());
        logger.info("camera_capability_owner stage=app_t0 appMs=" + appMillis
                + " owner=" + Integer.toHexString(System.identityHashCode(this)));
        if (!cameraPermissionGranted()) {
            authority.unavailable("camera_permission_required");
            logger.info("camera_capability_owner stage=waiting_for_readiness"
                    + " reason=camera_permission_required");
        }
    }

    public void requestScan(Runnable ready, Runnable complete) {
        requestScan(ready, complete, null);
    }

    public void requestScan(Runnable ready, Runnable complete,
            Consumer<RecheckProgress> progress) {
        requestScan(scanPipelinesForConfiguredSelections(), ready, complete, progress);
    }

    public void requestStartupFastCollection() {
        List<VerificationPipelineId> required = scanPipelinesForSelection(Optional.empty());
        synchronized (this) {
            if (startupFastCollectionInFlight || scanInFlight || scanAwaitingRuntimeRelease
                    || !cameraPermissionGranted()) return;
            startupFastCollectionInFlight = true;
            authority.loading();
        }
        try {
            scanExecutor.execute(() -> runStartupFastCollectionPreflight(required));
        } catch (RuntimeException error) {
            synchronized (this) {
                startupFastCollectionInFlight = false;
                lastScanSuccessful = false;
                authority.unavailable("startup_fast_collection_schedule_exception:"
                        + error.getClass().getSimpleName());
                logger.warn("camera_capability_owner stage=startup_fast_collection"
                        + " outcome=schedule_failed", error);
                finishCallbacksLocked();
            }
        }
    }

    private void runStartupFastCollectionPreflight(
            List<VerificationPipelineId> required) {
        try {
            RawCatalog catalog = new AndroidCameraCatalogSource(context, logger).load();
            LoadResult loaded = store.load(CameraCapabilitySnapshotMapper.freshness(catalog));
            if (hasCompleteStartupFastCollection(loaded, required)) {
                Snapshot selected = selectionSnapshotForConfiguredModes(
                        loaded.snapshot().orElseThrow());
                synchronized (this) {
                    startupFastCollectionInFlight = false;
                    pendingScanPipelines = List.of();
                    authority.current(selected);
                    lastScanSuccessful = true;
                    logger.info("camera_capability_owner stage=startup_fast_collection"
                            + " outcome=skip reason=xml_complete");
                    finishCallbacksLocked();
                }
                return;
            }
        } catch (Exception | LinkageError error) {
            logger.warn("camera_capability_owner stage=startup_fast_collection"
                    + " outcome=preflight_failed", error);
        }
        synchronized (this) {
            startupFastCollectionInFlight = false;
        }
        requestScan(required, null, null, null);
    }

    public void requestBootstrapScan(Runnable ready, Runnable complete) {
        requestScan(scanPipelinesForSelection(Optional.empty()), ready, complete, null);
    }

    public void ensurePipelineEvidence(Optional<VerificationPipelineId> pipeline,
            Consumer<Boolean> completion) {
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(completion, "completion");
        List<VerificationPipelineId> required = scanPipelinesForSelection(pipeline);
        AtomicBoolean delivered = new AtomicBoolean();
        Runnable deliver = () -> {
            if (!delivered.compareAndSet(false, true))
                return;
            completion.accept(hasPipelineEvidenceForEveryCamera(snapshot(), required));
        };
        requestScan(required, deliver, deliver, null);
    }
    public void ensurePipelineEvidence(String cameraId,
            Optional<VerificationPipelineId> pipeline, Consumer<Boolean> completion) {
        String id = requiredCameraId(cameraId);
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(completion, "completion");
        if (hasPipelineEvidence(id, pipeline)) {
            dispatch(() -> completion.accept(true));
            return;
        }
        try {
            scanExecutor.execute(() -> {
                boolean available;
                try {
                    available = quickScanPipelineEvidence(id, pipeline);
                } catch (Exception | LinkageError error) {
                    logger.warn("camera_capability_owner stage=targeted_fast_scan"
                            + " cameraId=" + id + " outcome=unavailable", error);
                    available = false;
                }
                boolean result = available;
                dispatch(() -> completion.accept(result));
            });
        } catch (RuntimeException error) {
            logger.warn("camera_capability_owner stage=targeted_fast_scan"
                    + " cameraId=" + id + " outcome=schedule_failed", error);
            dispatch(() -> completion.accept(false));
        }
    }

    private boolean quickScanPipelineEvidence(String cameraId,
            Optional<VerificationPipelineId> pipeline) throws CameraCatalogSource.CatalogException {
        Snapshot previous = snapshot();
        CameraId targetId = cameraId(cameraId);
        if (previous == null || targetId == null)
            return false;
        List<VerificationPipelineId> required = scanPipelinesForSelection(pipeline);
        List<VerificationPipelineId> missing = required.stream()
                .filter(value -> !hasPipelineEvidence(previous, targetId, List.of(value)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (missing.isEmpty())
            return true;

        RawCatalog catalog = new AndroidCameraCatalogSource(context, logger).load();
        var freshness = CameraCapabilitySnapshotMapper.freshness(catalog);
        if (!CameraCapabilitySnapshotMapper.isFresh(previous, freshness))
            return false;
        RawCatalog targetCatalog = targetedCatalog(catalog, targetId);
        BuildFastCameraCapabilitiesUseCase scanner = new BuildFastCameraCapabilitiesUseCase(
                () -> targetCatalog, logger);
        List<FastSnapshot> scans = new ArrayList<>();
        for (VerificationPipelineId requiredPipeline : missing) {
            Result scan = executeFastScan(scanner, requiredPipeline, ignored -> {});
            if (!scan.complete() || scan.authoritativeSnapshot().isEmpty())
                return false;
            scans.add(scan.authoritativeSnapshot().orElseThrow());
        }

        synchronized (this) {
            Snapshot current = snapshot();
            if (current == null || !CameraCapabilitySnapshotMapper.isFresh(current, freshness))
                return false;
            Snapshot updated = current;
            for (FastSnapshot scan : scans)
                updated = mergeTargetedScan(updated, targetId, scan);
            authority.current(updated);
            store.requestWrite(updated);
            return hasPipelineEvidence(updated, targetId, required);
        }
    }

    private void requestScan(List<VerificationPipelineId> required,
            Runnable ready, Runnable complete, Consumer<RecheckProgress> progress) {
        synchronized (this) {
            if (ready != null)
                readyCallbacks.add(ready);
            if (complete != null)
                completionCallbacks.add(complete);
            if (progress != null)
                progressCallbacks.add(progress);
            pendingScanPipelines = orderedUnion(pendingScanPipelines, required);
            if (!cameraPermissionGranted()) {
                pendingScanPipelines = List.of();
                lastScanSuccessful = false;
                authority.unavailable("camera_permission_required");
                finishCallbacksLocked();
                return;
            }
            if (startupFastCollectionInFlight || scanInFlight || scanAwaitingRuntimeRelease)
                return;
            Snapshot current = authority.snapshot().orElse(null);
            if (!forceRescan
                    && !requiresDeepVerification(current, pendingScanPipelines)) {
                pendingScanPipelines = List.of();
                lastScanSuccessful = true;
                finishCallbacksLocked();
                return;
            }
            if (runtimeOwner.snapshot()
                    .state() != com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState.CLOSED) {
                authority.loading();
                awaitRuntimeReleaseForScanLocked();
                return;
            }
            schedulePendingScanLocked();
        }
    }

    private void schedulePendingScanLocked() {
        List<VerificationPipelineId> requested = List.copyOf(pendingScanPipelines);
        pendingScanPipelines = List.of();
        List<VerificationPipelineId> pipelines = forceRescan
                ? scanPipelinesForSelection(Optional.empty())
                : missingPipelines(authority.snapshot().orElse(null), requested);
        if (pipelines.isEmpty()) {
            finishCallbacksLocked();
            return;
        }
        authority.loading();
        lastScanSuccessful = false;
        scanInFlight = true;
        generation++;
        long currentGeneration = generation;
        logger.info("camera_capability_owner stage=scan_schedule generation="
                + currentGeneration + " pipelines=" + pipelines);
        ScanScheduleResult schedule = scheduleCapabilityScan(
                runtimeOwner, scanExecutor, () -> runScan(currentGeneration, pipelines), () -> {
                    List<VerificationPipelineId> pending;
                    synchronized (CameraCapabilityService.this) {
                        scanInFlight = false;
                        pending = List.copyOf(pendingScanPipelines);
                        pendingScanPipelines = List.of();
                        if (pending.isEmpty())
                            finishCallbacksLocked();
                    }
                    if (!pending.isEmpty())
                        requestScan(pending, null, null, null);
                }, error -> {
                    synchronized (CameraCapabilityService.this) {
                        scanInFlight = false;
                        lastScanSuccessful = false;
                        authority.unavailable("scan_schedule_exception:"
                                + error.getClass().getSimpleName());
                        logger.warn("camera_capability_owner stage=scan_schedule"
                                + " outcome=failed", error);
                        finishCallbacksLocked();
                    }
                });
        if (schedule == ScanScheduleResult.RESERVATION_UNAVAILABLE) {
            scanInFlight = false;
            pendingScanPipelines = orderedUnion(pipelines, pendingScanPipelines);
            awaitRuntimeReleaseForScanLocked();
        }
    }

    enum ScanScheduleResult {
        SCHEDULED,
        RESERVATION_UNAVAILABLE,
        SCHEDULE_FAILED
    }

    static ScanScheduleResult scheduleCapabilityScan(
            ProcessCameraRuntimeOwner runtimeOwner,
            Executor executor,
            Runnable task,
            Runnable afterRelease,
            Consumer<RuntimeException> scheduleFailure) {
        Objects.requireNonNull(runtimeOwner, "runtimeOwner");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(afterRelease, "afterRelease");
        Objects.requireNonNull(scheduleFailure, "scheduleFailure");
        if (!runtimeOwner.beginCapabilityScan()) {
            return ScanScheduleResult.RESERVATION_UNAVAILABLE;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    runtimeOwner.endCapabilityScan();
                    afterRelease.run();
                }
            });
            return ScanScheduleResult.SCHEDULED;
        } catch (RuntimeException error) {
            runtimeOwner.endCapabilityScan();
            scheduleFailure.accept(error);
            return ScanScheduleResult.SCHEDULE_FAILED;
        }
    }

    private void awaitRuntimeReleaseForScanLocked() {
        scanAwaitingRuntimeRelease = true;
        logger.info("camera_capability_owner stage=scan_wait action=release_runtime");
        ProcessCameraRuntimeOwner.Attachment attachment = runtimeOwner.attach(snapshot -> {
            boolean released = snapshot.state() == com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState.CLOSED
                    && snapshot.inFlight().isEmpty();
            boolean failed = snapshot
                    .state() == com.dvid.dcam.feature.device.domain.camera.CameraRuntimeState.RECOVERING
                    && snapshot.inFlight().isEmpty();
            if (!released && !failed)
                return;
            finishRuntimeReleaseForScan(released,
                    failed ? "release_recovery_required" : "released");
        });
        scanReleaseAttachment = attachment;
        if (!scanAwaitingRuntimeRelease) {
            attachment.close();
            scanReleaseAttachment = null;
            return;
        }
        ProcessCameraRuntimeOwner.Submission submission = runtimeOwner.releaseCamera();
        logger.info("camera_capability_owner stage=scan_wait action=release_submitted"
                + " outcome=" + submission.name().toLowerCase());
        if (submission != ProcessCameraRuntimeOwner.Submission.ACCEPTED
                && submission != ProcessCameraRuntimeOwner.Submission.NO_OP) {
            finishRuntimeReleaseForScan(false,
                    "release_submission_" + submission.name().toLowerCase());
        }
    }

    private void finishRuntimeReleaseForScan(boolean released, String reason) {
        ProcessCameraRuntimeOwner.Attachment attachment;
        synchronized (this) {
            if (!scanAwaitingRuntimeRelease)
                return;
            scanAwaitingRuntimeRelease = false;
            attachment = scanReleaseAttachment;
            scanReleaseAttachment = null;
            if (!released) {
                pendingScanPipelines = List.of();
                lastScanSuccessful = false;
                authority.unavailable("runtime_release_failed:" + reason);
                logger.info("camera_capability_owner stage=scan_wait outcome=failed reason="
                        + reason);
                finishCallbacksLocked();
            }
        }
        if (attachment != null)
            attachment.close();
        if (released)
            resumePendingScan();
    }

    private void resumePendingScan() {
        List<VerificationPipelineId> pending;
        synchronized (this) {
            pending = List.copyOf(pendingScanPipelines);
        }
        if (!pending.isEmpty())
            requestScan(pending, null, null, null);
    }

    public synchronized boolean invalidate() {
        if (scanInFlight)
            return false;
        forceRescan = true;
        lastScanSuccessful = false;
        lastRecheckSummary = Optional.empty();
        authority.loading();
        logger.info("camera_capability_owner stage=invalidate action=rescan_requested"
                + " durableSnapshot=preserved");
        return true;
    }

    public String unavailableReason() {
        return authority.unavailableReason();
    }

    public synchronized boolean lastScanSuccessful() {
        return lastScanSuccessful;
    }

    public boolean hasCurrentSnapshot() {
        return authority.hasCurrent();
    }

    public boolean isLoading() {
        return authority.state() == CameraCapabilityAuthority.State.LOADING;
    }

    public List<String> cameraIds() {
        return CameraCapabilityOptions.cameraIds(snapshot());
    }

    public String primaryCameraId() {
        List<String> ids = cameraIds();
        return ids.isEmpty() ? "" : ids.get(0);
    }

    public int cameraOrientationDegrees(String cameraId) {
        Snapshot current = snapshot();
        if (current == null) {
            throw new IllegalStateException("camera capability snapshot unavailable");
        }
        String resolved = resolvedCameraId(cameraId);
        if (resolved.isBlank()) {
            throw new IllegalStateException("camera sensor orientation unavailable cameraId="
                    + cameraId);
        }
        for (var camera : current.cameras()) {
            if (camera.cameraId().value().equals(resolved)) {
                return camera.sensorOrientationDegrees();
            }
        }
        throw new IllegalStateException(
                "camera sensor orientation unavailable cameraId=" + resolved);
    }

    public synchronized Optional<Summary> lastRecheckSummary() {
        return lastRecheckSummary;
    }

    public synchronized boolean hasFullyVerifiedCapabilities() {
        Snapshot current = snapshot();
        return current != null
                && CameraCapabilitySnapshotMapper.hasExhaustiveVerifiedCapabilities(current);
    }

    public List<PipelineSelection> selectedPipelineSelections() {
        return pipelineSelections(selectionSnapshot());
    }

    public List<PipelineSelection> automaticPipelineSelections() {
        return pipelineSelections(selectionSnapshot(Optional.empty()));
    }

    private static List<PipelineSelection> pipelineSelections(Snapshot current) {
        if (current == null)
            return List.of();
        boolean fullyVerified = CameraCapabilitySnapshotMapper
                .hasExhaustiveVerifiedCapabilities(current);
        List<PipelineSelection> result = new ArrayList<>();
        for (var camera : current.cameras()) {
            Optional<CameraCapabilityStore.SelectedPipeline> selected = camera.selectedPipeline();
            if (selected.isEmpty())
                continue;
            String pipelineId = selected.orElseThrow().pipelineId().value();
            int tupleCount = 0;
            for (var codec : camera.codecs()) {
                if (codec.codec() != com.dvid.dcam.feature.device.domain.camera.VideoCodec.H264) {
                    continue;
                }
                for (var pipeline : codec.pipelines()) {
                    if (!pipeline.verificationPipelineId().value().equals(pipelineId))
                        continue;
                    tupleCount = displayedTupleCount(pipeline, fullyVerified);
                }
            }
            result.add(new PipelineSelection(camera.cameraId().value(), pipelineId, tupleCount));
        }
        return List.copyOf(result);
    }

    static int displayedTupleCount(PipelineEvidence pipeline, boolean fullyVerified) {
        Objects.requireNonNull(pipeline, "pipeline");
        return Math.toIntExact((fullyVerified
                ? pipeline.effectiveCandidates().stream()
                        .filter(candidate -> pipeline.outcome(candidate) == VerificationOutcome.VERIFIED_PASS)
                : pipeline.rawFastCandidates().stream())
                .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                .count());
    }

    public OptionalInt verifiedCaptureTupleCount(String cameraId,
            VerificationPipelineId pipelineId) {
        Objects.requireNonNull(pipelineId, "pipelineId");
        Snapshot current = snapshot();
        CameraId id = cameraId(cameraId);
        if (current == null || id == null)
            return OptionalInt.empty();
        for (var camera : current.cameras()) {
            if (!camera.cameraId().equals(id))
                continue;
            for (var codec : camera.codecs()) {
                if (codec.codec() != com.dvid.dcam.feature.device.domain.camera.VideoCodec.H264) {
                    continue;
                }
                for (var pipeline : codec.pipelines()) {
                    if (!pipeline.verificationPipelineId().equals(pipelineId))
                        continue;
                    int count = Math.toIntExact(pipeline.effectiveCandidates().stream()
                            .filter(candidate -> candidate
                                    .kind() == com.dvid.dcam.feature.device.domain.camera.CandidateKey.Kind.TUPLE)
                            .filter(candidate -> pipeline.outcome(candidate) == VerificationOutcome.VERIFIED_PASS)
                            .count());
                    return OptionalInt.of(count);
                }
            }
        }
        return OptionalInt.empty();
    }

    public OptionalInt fastCaptureTupleCount(String cameraId,
            VerificationPipelineId pipelineId) {
        Objects.requireNonNull(pipelineId, "pipelineId");
        Snapshot current = snapshot();
        CameraId id = cameraId(cameraId);
        if (current == null || id == null)
            return OptionalInt.empty();
        for (var camera : current.cameras()) {
            if (!camera.cameraId().equals(id))
                continue;
            for (var codec : camera.codecs()) {
                if (codec.codec() != com.dvid.dcam.feature.device.domain.camera.VideoCodec.H264) {
                    continue;
                }
                for (var pipeline : codec.pipelines()) {
                    if (!pipeline.verificationPipelineId().equals(pipelineId))
                        continue;
                    int count = 0;
                    for (var candidate : pipeline.rawFastCandidates()) {
                        if (candidate.kind() == com.dvid.dcam.feature.device.domain.camera.CandidateKey.Kind.TUPLE) {
                            count++;
                        }
                    }
                    return OptionalInt.of(count);
                }
            }
        }
        return OptionalInt.empty();
    }

    public List<CaptureQuality> supportedRecordQualities(String cameraId) {
        return CameraCapabilityOptions.recordQualities(selectionSnapshot(), resolvedCameraId(cameraId),
                selections.runtimeRejections());
    }

    public List<CaptureQuality> supportedImageQualities(String cameraId) {
        return CameraCapabilityOptions.imageQualities(selectionSnapshot(), resolvedCameraId(cameraId),
                selections.runtimeRejections());
    }

    public List<Integer> supportedRecordFrameRates(String cameraId, String qualityId) {
        return CameraCapabilityOptions.frameRates(selectionSnapshot(), resolvedCameraId(cameraId), qualityId,
                selections.runtimeRejections());
    }

    public String selectedRecordQuality(String cameraId) {
        CameraId id = cameraId(resolvedCameraId(cameraId));
        if (id == null)
            return "SD";
        CaptureQuality selected = CameraCapabilityOptions.selectedVideo(selectionSnapshot(), id.value(),
                selections.recordQuality(id), selections.recordFrameRate(id),
                selections.runtimeRejections());
        return selected == null ? "SD" : selected.getId();
    }

    public int selectedRecordFrameRate(String cameraId) {
        CameraId id = cameraId(resolvedCameraId(cameraId));
        if (id == null)
            return 0;
        CaptureQuality selected = CameraCapabilityOptions.selectedVideo(selectionSnapshot(), id.value(),
                selections.recordQuality(id), selections.recordFrameRate(id),
                selections.runtimeRejections());
        return selected == null ? 0 : selected.getFrameRate();
    }

    public String selectedImageQuality(String cameraId) {
        CameraId id = cameraId(resolvedCameraId(cameraId));
        if (id == null)
            return "SD";
        CaptureQuality selected = CameraCapabilityOptions.selectedImage(selectionSnapshot(), id.value(),
                selections.imageQuality(id), selections.runtimeRejections());
        return selected == null ? "SD" : selected.getId();
    }

    public CaptureQuality selectedRecordCaptureQuality() {
        CameraId id = cameraId(primaryCameraId());
        return id == null ? null
                : CameraCapabilityOptions.selectedVideo(selectionSnapshot(), id.value(),
                        selections.recordQuality(id), selections.recordFrameRate(id),
                        selections.runtimeRejections());
    }

    public CaptureQuality selectedImageCaptureQuality() {
        CameraId id = cameraId(primaryCameraId());
        return id == null ? null
                : CameraCapabilityOptions.selectedImage(selectionSnapshot(), id.value(),
                        selections.imageQuality(id), selections.runtimeRejections());
    }

    public void selectRecordQuality(String cameraId, String qualityId) {
        CameraId id = cameraId(resolvedCameraId(cameraId));
        if (id == null || !CameraCapabilityOptions.containsQuality(
                supportedRecordQualities(id.value()), qualityId))
            return;
        selections.saveRecordQuality(id, qualityId);
        List<Integer> rates = supportedRecordFrameRates(id.value(), qualityId);
        if (!rates.contains(selections.recordFrameRate(id)) && !rates.isEmpty()) {
            selections.saveRecordFrameRate(id, rates.get(rates.size() - 1));
        }
    }

    public void selectRecordFrameRate(String cameraId, int frameRate) {
        CameraId id = cameraId(resolvedCameraId(cameraId));
        if (id == null)
            return;
        if (supportedRecordFrameRates(id.value(), selectedRecordQuality(id.value()))
                .contains(frameRate))
            selections.saveRecordFrameRate(id, frameRate);
    }

    public void selectImageQuality(String cameraId, String qualityId) {
        CameraId id = cameraId(resolvedCameraId(cameraId));
        if (id != null && CameraCapabilityOptions.containsQuality(
                supportedImageQualities(id.value()), qualityId)) {
            selections.saveImageQuality(id, qualityId);
        }
    }

    public List<CaptureQuality> recordingVideoFallbackQualities() {
        CameraId id = cameraId(primaryCameraId());
        if (id == null)
            return List.of();
        return CameraCapabilityOptions.videoFallbacks(selectionSnapshot(), id.value(),
                selectedRecordQuality(id.value()), selectedRecordFrameRate(id.value()),
                selections.runtimeRejections());
    }

    public List<CaptureQuality> recordingImageFallbackQualities(
            String videoId, int frameRate) {
        CameraId id = cameraId(primaryCameraId());
        if (id == null)
            return List.of();
        return CameraCapabilityOptions.imageFallbacks(selectionSnapshot(), id.value(), videoId, frameRate,
                selectedImageQuality(id.value()), selections.runtimeRejections());
    }

    public void markCaptureAttempt(String cameraId, String videoId, String imageId) {
        if (cameraId == null || cameraId.isBlank() || videoId == null || videoId.isBlank())
            return;
        selections.markCaptureAttempt(Process.myPid() + "|" + cameraId + "|" + videoId
                + "|" + (imageId == null ? "" : imageId));
    }

    public void clearCaptureAttempt() {
        selections.clearCaptureAttempt();
    }

    public void recordCaptureFailure(String cameraId, String videoId, int frameRate,
            String imageId, String reason) {
        if (cameraId == null || cameraId.isBlank() || videoId == null || videoId.isBlank()
                || frameRate <= 0)
            return;
        String rejection = cameraId + "|" + videoId + "|" + frameRate + "|"
                + (imageId == null ? "" : imageId);
        selections.addRuntimeRejection(rejection);
        logger.warn("camera_capability_owner stage=runtime_rejection"
                + " cameraId=" + cameraId + " video=" + videoId + " fps=" + frameRate
                + " image=" + (imageId == null ? "" : imageId)
                + " reason=" + String.valueOf(reason), null);
    }

    private void runScan(long currentGeneration,
            List<VerificationPipelineId> requestedPipelines) {
        long startedNanos = System.nanoTime();
        logger.info("camera_capability_owner stage=scan_start generation=" + currentGeneration
                + " pipelines=" + requestedPipelines);
        Optional<Snapshot> previous = authority.snapshot();
        boolean rebuildForced;
        synchronized (this) {
            rebuildForced = forceRescan;
        }
        if (rebuildForced) {
            publishRecheckProgress(new ProductionCameraCapabilityRecheck.Progress(
                    "A/B", "all", "preparing", 0, 1,
                    "stage=preparing"));
        }
        try {
            AndroidCameraCatalogSource catalogSource = new AndroidCameraCatalogSource(context, logger);
            RawCatalog catalog = catalogSource.load();
            var freshness = CameraCapabilitySnapshotMapper.freshness(catalog);
            if (previous.isPresent() && !CameraCapabilitySnapshotMapper.isFresh(
                    previous.orElseThrow(), freshness)) {
                previous = Optional.empty();
            }
            LoadResult loaded = store.load(freshness);
            if (!rebuildForced && loaded.status() == LoadStatus.LOADED
                    && loaded.snapshot().isPresent()
                    && !requiresDeepVerification(
                            loaded.snapshot().orElseThrow(), requestedPipelines)) {
                Snapshot reusable = selectionSnapshotForConfiguredModes(
                        loaded.snapshot().orElseThrow());
                requireCompletePipelineSelection(reusable);
                store.requestWrite(reusable);
                authority.current(reusable);
                synchronized (this) {
                    forceRescan = false;
                    lastScanSuccessful = true;
                }
                logger.info("camera_capability_owner stage=snapshot_reuse"
                        + " outcome=ready_reusable generation=" + currentGeneration
                        + " cameraCount=" + reusable.cameras().size());
                return;
            }
            if (previous.isEmpty() && loaded.snapshot().isPresent()
                    && CameraCapabilitySnapshotMapper.hasUsableTuple(
                            loaded.snapshot().orElseThrow())) {
                previous = loaded.snapshot();
            }
            List<VerificationPipelineId> pipelines = rebuildForced
                    ? scanPipelinesForSelection(Optional.empty())
                    : missingPipelines(previous.orElse(null), requestedPipelines);
            if (pipelines.isEmpty() && previous.isPresent()) {
                authority.current(previous.orElseThrow());
                synchronized (this) {
                    forceRescan = false;
                    lastScanSuccessful = true;
                }
                return;
            }
            CameraCatalogSource fixedCatalog = () -> catalog;
            BuildFastCameraCapabilitiesUseCase scanner = new BuildFastCameraCapabilitiesUseCase(fixedCatalog, logger);
            Optional<Snapshot> updated = rebuildForced ? Optional.empty() : previous;
            Map<VerificationPipelineId, Result> fastResults = new HashMap<>();
            List<String> completions = new ArrayList<>();
            for (VerificationPipelineId pipeline : pipelines) {
                logger.info("camera_capability_owner stage=scan_pipeline generation="
                        + currentGeneration + " pipeline=" + pipeline.value());
                String profile = pipeline.equals(NativeSurfaceSharingFastProbe.PIPELINE_ID)
                        ? "A"
                        : "B";
                int fastBuildTotal = Math.max(1, catalog.cameras().size());
                publishRecheckProgress(new ProductionCameraCapabilityRecheck.Progress(
                        profile, "all", "fast_scan", 0, fastBuildTotal,
                        "pipeline=" + pipeline.value() + " camera=all"));
                Result result = executeFastScan(scanner, pipeline,
                        progress -> publishFastRecheckProgress(profile, pipeline, progress));
                completions.add(pipeline.value() + "="
                        + result.completion().name().toLowerCase());
                if (result.complete() && result.authoritativeSnapshot().isPresent()) {
                    updated = Optional.of(CameraCapabilitySnapshotMapper.merge(
                            result.authoritativeSnapshot().orElseThrow(), updated));
                    fastResults.put(pipeline, result);
                }
            }
            if (fastResults.size() != pipelines.size() || updated.isEmpty()
                    || !CameraCapabilitySnapshotMapper.hasUsableTupleForEveryCamera(
                            updated.orElseThrow())) {
                publishFailure(previous, "shared_pipeline_scan_incomplete:"
                        + String.join(",", completions));
                return;
            }
            if (rebuildForced) {
                Result fastA = fastResults.get(NativeSurfaceSharingFastProbe.PIPELINE_ID);
                Result fastB = fastResults.get(EglFanOutFastProbe.PIPELINE_ID);
                if (fastA == null || fastB == null) {
                    publishFailure(previous, "deep_recheck_missing_pipeline_fast_scan");
                    return;
                }
                ProductionCameraCapabilityRecheck.RunResult deep = new ProductionCameraCapabilityRecheck(context,
                        logger,
                        ignored -> runtimeOwner.snapshot().healthGeneration())
                        .execute(updated.orElseThrow(), fastA, fastB,
                                deepVerificationSelection(), this::publishRecheckProgress);
                if (!deep.complete()) {
                    publishFailure(previous, deep.detail());
                    return;
                }
                Snapshot selected = selectionSnapshotForConfiguredModes(
                        deep.snapshot().orElseThrow());
                Summary summary = deep.summary().orElseThrow();
                requireCompletePipelineSelection(selected);
                store.writeNow(selected);
                authority.current(selected);
                synchronized (this) {
                    forceRescan = false;
                    lastScanSuccessful = true;
                    lastRecheckSummary = Optional.of(summary);
                }
                logger.info("camera_capability_recheck stage=complete"
                        + " pipelineATupleCount=" + summary.pipelineATupleCount()
                        + " pipelineBTupleCount=" + summary.pipelineBTupleCount()
                        + " pipelineAElapsedMs=" + summary.pipelineAElapsedMillis()
                        + " pipelineBElapsedMs=" + summary.pipelineBElapsedMillis());
            } else {
                Snapshot selected = selectionSnapshotForConfiguredModes(
                        updated.orElseThrow());
                requireCompletePipelineSelection(selected);
                authority.current(selected);
                store.requestWrite(selected);
                synchronized (this) {
                    forceRescan = false;
                    lastScanSuccessful = true;
                }
            }
        } catch (Exception | LinkageError error) {
            logger.warn("camera_capability_owner stage=scan_end generation="
                    + currentGeneration + " outcome=unavailable", error);
            publishFailure(previous, "scan_exception:" + error.getClass().getSimpleName());
        } finally {
            long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
            synchronized (this) {
                logger.info("camera_capability_owner stage=scan_end generation="
                        + currentGeneration + " state=" + authority.state().name().toLowerCase()
                        + " elapsedMs=" + elapsedMillis);
            }
        }
    }

    private Result executeFastScan(BuildFastCameraCapabilitiesUseCase scanner,
            VerificationPipelineId pipeline, Consumer<Progress> progress) {
        if (pipeline.equals(NativeSurfaceSharingFastProbe.PIPELINE_ID)) {
            return scanner.execute(new AndroidFastCameraCapabilityProbe(
                    new NativeSurfaceSharingFastProbe(context, logger), logger), progress);
        }
        if (pipeline.equals(EglFanOutFastProbe.PIPELINE_ID)) {
            return scanner.execute(new AndroidFastCameraCapabilityProbe(
                    new EglFanOutFastProbe(context, logger), logger), progress);
        }
        throw new IllegalArgumentException(
                "unsupported capability pipeline " + pipeline.value());
    }

    static RawCatalog targetedCatalog(RawCatalog catalog, CameraId cameraId) {
        List<CameraFacts> cameras = catalog.cameras().stream()
                .filter(camera -> camera.cameraId().equals(cameraId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (cameras.size() != 1)
            throw new IllegalArgumentException("unknown cameraId: " + cameraId.value());
        return new RawCatalog(catalog.deviceIdentity(), cameras,
                new ConcurrentCameraFacts(false, List.of()), catalog.h264Encoders(),
                catalog.hardwareSignatureInput());
    }

    static List<VerificationPipelineId> scanPipelinesForSelection(
            Optional<VerificationPipelineId> pipeline) {
        Objects.requireNonNull(pipeline, "pipeline");
        return pipeline.map(List::of).orElseGet(() -> List.of(
                NativeSurfaceSharingFastProbe.PIPELINE_ID,
                EglFanOutFastProbe.PIPELINE_ID));
    }

    private static List<VerificationPipelineId> orderedUnion(
            List<VerificationPipelineId> left, List<VerificationPipelineId> right) {
        List<VerificationPipelineId> result = new ArrayList<>();
        if (left.contains(NativeSurfaceSharingFastProbe.PIPELINE_ID)
                || right.contains(NativeSurfaceSharingFastProbe.PIPELINE_ID)) {
            result.add(NativeSurfaceSharingFastProbe.PIPELINE_ID);
        }
        if (left.contains(EglFanOutFastProbe.PIPELINE_ID)
                || right.contains(EglFanOutFastProbe.PIPELINE_ID)) {
            result.add(EglFanOutFastProbe.PIPELINE_ID);
        }
        for (VerificationPipelineId pipeline : left) {
            if (!result.contains(pipeline))
                result.add(pipeline);
        }
        for (VerificationPipelineId pipeline : right) {
            if (!result.contains(pipeline))
                result.add(pipeline);
        }
        return List.copyOf(result);
    }

    static boolean requiresDeepVerification(
            Snapshot snapshot, List<VerificationPipelineId> requested) {
        return snapshot == null
                || snapshot.initializationState() != InitializationState.READY_REUSABLE
                || !CameraCapabilitySnapshotMapper.hasExhaustiveVerifiedCapabilities(snapshot)
                || !CameraCapabilitySnapshotMapper.hasVerifiedSelectionForMainCamera(snapshot)
                || !hasPipelineEvidenceForEveryCamera(snapshot, requested);
    }

    static boolean hasCompleteStartupFastCollection(
            LoadResult loaded, List<VerificationPipelineId> required) {
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(required, "required");
        return loaded.status() == LoadStatus.LOADED
                && missingPipelines(loaded.snapshot().orElseThrow(), required).isEmpty();
    }

    static List<VerificationPipelineId> missingPipelines(
            Snapshot snapshot, List<VerificationPipelineId> requested) {
        List<VerificationPipelineId> result = new ArrayList<>();
        for (VerificationPipelineId pipeline : requested) {
            if (!hasPipelineEvidenceForEveryCamera(snapshot, List.of(pipeline))) {
                result.add(pipeline);
            }
        }
        return List.copyOf(result);
    }

    private static boolean hasPipelineEvidenceForEveryCamera(
            Snapshot snapshot, List<VerificationPipelineId> pipelines) {
        if (snapshot == null || snapshot.cameras().isEmpty())
            return false;
        for (var camera : snapshot.cameras()) {
            for (VerificationPipelineId pipeline : pipelines) {
                boolean present = camera.codecs().stream()
                        .filter(codec -> codec.codec() == com.dvid.dcam.feature.device.domain.camera.VideoCodec.H264)
                        .flatMap(codec -> codec.pipelines().stream())
                        .anyMatch(value -> value.verificationPipelineId().equals(pipeline));
                if (!present)
                    return false;
            }
        }
        return true;
    }

    private void publishFastRecheckProgress(String profile,
            VerificationPipelineId pipeline, BuildFastCameraCapabilitiesUseCase.Progress progress) {
        publishRecheckProgress(new ProductionCameraCapabilityRecheck.Progress(
                profile, progress.cameraId().value(), "fast_scan", progress.completed(),
                progress.total(), progress.detail()));
    }

    private void publishRecheckProgress(
            ProductionCameraCapabilityRecheck.Progress progress) {
        RecheckProgress update = new RecheckProgress(progress.profile(),
                progress.cameraId(), progress.stage(), progress.completed(),
                progress.total(), progress.detail());
        List<Consumer<RecheckProgress>> callbacks;
        synchronized (this) {
            callbacks = List.copyOf(progressCallbacks);
        }
        for (Consumer<RecheckProgress> callback : callbacks) {
            callbackExecutor.execute(() -> {
                try {
                    callback.accept(update);
                } catch (RuntimeException error) {
                    logger.warn("camera_capability_owner progress callback failed", error);
                }
            });
        }
    }

    private void publishFailure(Optional<Snapshot> previous, String reason) {
        synchronized (this) {
            lastScanSuccessful = false;
        }
        if (previous.isPresent() && !previous.orElseThrow().cameras().isEmpty()) {
            authority.current(previous.orElseThrow());
            authority.unavailable(reason);
        } else {
            authority.unavailable(reason);
        }
    }

    private synchronized void finishCallbacksLocked() {
        boolean ready = authority.hasCurrent() && lastScanSuccessful;
        List<Runnable> readyValues = ready ? List.copyOf(readyCallbacks) : List.of();
        List<Runnable> completionValues = List.copyOf(completionCallbacks);
        readyCallbacks.clear();
        completionCallbacks.clear();
        progressCallbacks.clear();
        for (Runnable callback : readyValues)
            dispatch(callback);
        for (Runnable callback : completionValues)
            dispatch(callback);
    }

    private void dispatch(Runnable callback) {
        callbackExecutor.execute(() -> {
            try {
                callback.run();
            } catch (RuntimeException error) {
                logger.warn("camera_capability_owner callback failed", error);
            }
        });
    }

    private Snapshot selectionSnapshot() {
        Snapshot current = snapshot();
        return current == null ? null : selectionSnapshotForConfiguredModes(current);
    }

    private Snapshot selectionSnapshot(Optional<VerificationPipelineId> pipeline) {
        Snapshot current = snapshot();
        return current == null ? null
                : selectionSnapshotForMode(
                        current, pipeline, selectionResolver);
    }

    private Snapshot selectionSnapshotForConfiguredModes(Snapshot current) {
        Map<String, Optional<VerificationPipelineId>> configured;
        Optional<VerificationPipelineId> fallback;
        synchronized (this) {
            configured = Map.copyOf(forcedPipelines);
            fallback = forcedPipeline;
        }
        return selectionSnapshotForModes(current, configured, fallback, selectionResolver);
    }

    private synchronized Optional<VerificationPipelineId> pipelineSelection(String cameraId) {
        return forcedPipelines.getOrDefault(requiredCameraId(cameraId), forcedPipeline);
    }

    private synchronized List<VerificationPipelineId> scanPipelinesForConfiguredSelections() {
        List<VerificationPipelineId> required = forcedPipelines.isEmpty()
                ? scanPipelinesForSelection(forcedPipeline)
                : List.of();
        for (Optional<VerificationPipelineId> pipeline : forcedPipelines.values()) {
            required = orderedUnion(required, scanPipelinesForSelection(pipeline));
        }
        return required.isEmpty() ? scanPipelinesForSelection(forcedPipeline) : required;
    }

    private synchronized Optional<VerificationPipelineId> deepVerificationSelection() {
        return forcedPipelines.isEmpty() ? forcedPipeline : Optional.empty();
    }

    static Snapshot selectionSnapshotForMode(Snapshot current,
            Optional<VerificationPipelineId> pipeline,
            ResolveCameraRuntimeSelectionUseCase selectionResolver) {
        return selectionSnapshotForModes(current, Map.of(),
                Objects.requireNonNull(pipeline, "pipeline"), selectionResolver);
    }

    static Snapshot selectionSnapshotForCameraMode(Snapshot current, String cameraId,
            Optional<VerificationPipelineId> pipeline,
            ResolveCameraRuntimeSelectionUseCase selectionResolver) {
        Objects.requireNonNull(current, "current");
        String id = requiredCameraId(cameraId);
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(selectionResolver, "selectionResolver");
        List<CameraCapabilityStore.CameraSnapshot> cameras = new ArrayList<>();
        boolean found = false;
        for (var camera : current.cameras()) {
            if (camera.cameraId().value().equals(id)) {
                cameras.add(selectCameraPipeline(camera, pipeline, selectionResolver));
                found = true;
            } else {
                cameras.add(camera);
            }
        }
        if (!found)
            throw new IllegalArgumentException("unknown cameraId: " + id);
        return snapshotWithCameras(current, cameras);
    }

    static Snapshot selectionSnapshotForModes(Snapshot current,
            Map<String, Optional<VerificationPipelineId>> pipelines,
            Optional<VerificationPipelineId> fallback,
            ResolveCameraRuntimeSelectionUseCase selectionResolver) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(pipelines, "pipelines");
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(selectionResolver, "selectionResolver");
        List<CameraCapabilityStore.CameraSnapshot> cameras = new ArrayList<>();
        for (var camera : current.cameras()) {
            Optional<VerificationPipelineId> pipeline = pipelines.getOrDefault(
                    camera.cameraId().value(), fallback);
            cameras.add(selectCameraPipeline(camera, pipeline, selectionResolver));
        }
        return snapshotWithCameras(current, cameras);
    }

    private static CameraCapabilityStore.CameraSnapshot selectCameraPipeline(
            CameraCapabilityStore.CameraSnapshot camera,
            Optional<VerificationPipelineId> pipeline,
            ResolveCameraRuntimeSelectionUseCase selectionResolver) {
        if (pipeline.isPresent()) {
            VerificationPipelineId pipelineId = pipeline.orElseThrow();
            boolean present = camera.codecs().stream()
                    .flatMap(value -> value.pipelines().stream())
                    .anyMatch(value -> value.verificationPipelineId().equals(pipelineId));
            return present
                    ? withSelectedPipeline(camera, Optional.of(
                            CameraCapabilityStore.SelectedPipeline.fixed(pipelineId)))
                    : unavailableCamera(camera);
        }
        CameraCapabilityStore.CameraSnapshot autoCamera = withSelectedPipeline(camera, Optional.empty());
        var decision = selectionResolver.selectedPipelineDecision(autoCamera);
        if (decision.isEmpty())
            return autoCamera;
        var value = decision.orElseThrow();
        CameraCapabilityStore.SelectedPipeline selected = switch (value.status()) {
            case FAST_COMPLETE -> CameraCapabilityStore.SelectedPipeline.autoFast(
                    value.pipeline().verificationPipelineId(), value.reason());
            case VERIFIED_COMPLETE -> CameraCapabilityStore.SelectedPipeline.autoVerified(
                    value.pipeline().verificationPipelineId(), value.reason());
            case PROFILE_VERIFIED, FIXED -> throw new IllegalArgumentException(
                    "AUTO pipeline decision cannot have fixed status");
        };
        return withSelectedPipeline(camera, Optional.of(selected));
    }

    static Snapshot mergeTargetedScan(Snapshot current, CameraId cameraId, FastSnapshot scan) {
        if (scan.cameras().size() != 1
                || !scan.cameras().get(0).cameraFacts().cameraId().equals(cameraId)) {
            throw new IllegalArgumentException("targeted scan camera mismatch");
        }
        Snapshot scanned = CameraCapabilitySnapshotMapper.merge(scan, Optional.of(current));
        CameraCapabilityStore.CameraSnapshot replacement = scanned.cameras().get(0);
        List<CameraCapabilityStore.CameraSnapshot> cameras = new ArrayList<>();
        boolean replaced = false;
        for (CameraCapabilityStore.CameraSnapshot camera : current.cameras()) {
            if (camera.cameraId().equals(cameraId)) {
                cameras.add(replacement);
                replaced = true;
            } else {
                cameras.add(camera);
            }
        }
        if (!replaced)
            throw new IllegalArgumentException("unknown cameraId: " + cameraId.value());
        return snapshotWithCameras(current, cameras);
    }

    private static Snapshot snapshotWithCameras(Snapshot current,
            List<CameraCapabilityStore.CameraSnapshot> cameras) {
        if (cameras.isEmpty()) {
            return new Snapshot(current.format(), InitializationState.INCOMPLETE,
                    current.hardwareSignature(), current.cameraOrderOverride(), cameras);
        }
        CameraId mainCameraId = current.cameraOrderOverride().isEmpty()
                ? cameras.get(0).cameraId()
                : current.cameraOrderOverride().get(0);
        boolean mainProfileSelected = cameras.stream()
                .filter(camera -> camera.cameraId().equals(mainCameraId))
                .findFirst().flatMap(
                        CameraCapabilityStore.CameraSnapshot::selectedRecordingProfile)
                .isPresent();
        InitializationState state = mainProfileSelected
                ? current.initializationState()
                : InitializationState.INCOMPLETE;
        return new Snapshot(current.format(), state,
                current.hardwareSignature(), current.cameraOrderOverride(), cameras);
    }

    static Optional<CandidateKey> validatedSavedCandidate(Snapshot snapshot, CameraId cameraId,
            String videoId, int frameRate, String imageId, Set<String> runtimeRejections) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(cameraId, "cameraId");
        Objects.requireNonNull(runtimeRejections, "runtimeRejections");
        return selectedProfileCandidate(snapshot, cameraId)
                .filter(candidate -> savedSettingsMatch(
                        candidate, videoId, frameRate, imageId))
                .filter(candidate -> !CameraCapabilityOptions.isRejected(
                        candidate, runtimeRejections));
    }

    private static Optional<CandidateKey> selectedProfileCandidate(
            Snapshot snapshot, CameraId cameraId) {
        return snapshot.cameras().stream()
                .filter(camera -> camera.cameraId().equals(cameraId))
                .findFirst().flatMap(camera -> camera.selectedRecordingProfile()
                        .map(selection -> CandidateKey.forTuple(cameraId, selection.codec(),
                                selection.verificationPipelineId(), selection.tuple())));
    }

    private static boolean savedSettingsMatch(CandidateKey candidate,
            String videoId, int frameRate, String imageId) {
        var tuple = candidate.tuple().orElseThrow();
        return (videoId == null || videoId.isBlank()
                || tuple.videoMode().resolution().label().name().equals(videoId))
                && (frameRate <= 0 || tuple.videoMode().framesPerSecond() == frameRate)
                && (imageId == null || imageId.isBlank()
                        || tuple.imageMode().resolution().label().name().equals(imageId));
    }

    static String describeCandidate(CandidateKey candidate) {
        Objects.requireNonNull(candidate, "candidate");
        String video = candidate.videoMode().map(mode -> mode.resolution().label()
                + " (" + mode.resolution().actual() + ") at "
                + mode.framesPerSecond() + " FPS").orElse("none");
        String image = candidate.imageMode().map(mode -> mode.resolution().label()
                + " (" + mode.resolution().actual() + ")").orElse("none");
        return candidate.codec() + " video " + video + " with " + image + " photos";
    }

    private static String requiredCameraId(String cameraId) {
        if (cameraId == null || cameraId.isBlank()) {
            throw new IllegalArgumentException("cameraId is required");
        }
        return cameraId.trim();
    }

    private static void requireCompletePipelineSelection(Snapshot snapshot) {
        if (snapshot.cameras().stream().anyMatch(
                camera -> camera.selectedPipeline().isEmpty())) {
            throw new IllegalStateException("pipeline selection is incomplete");
        }
    }

    private static CameraCapabilityStore.CameraSnapshot withSelectedPipeline(
            CameraCapabilityStore.CameraSnapshot camera,
            Optional<CameraCapabilityStore.SelectedPipeline> selectedPipeline) {
        Optional<CameraCapabilityStore.SelectedRecordingProfile> recordingProfile = selectedPipeline
                .flatMap(selected -> camera.selectedRecordingProfile()
                        .filter(profile -> profile.verificationPipelineId().equals(
                                selected.pipelineId())));
        return new CameraCapabilityStore.CameraSnapshot(camera.cameraId(),
                camera.hardwareSignature(), camera.codecs(), selectedPipeline, recordingProfile,
                camera.sensorOrientationDegrees());
    }

    private static CameraCapabilityStore.CameraSnapshot unavailableCamera(
            CameraCapabilityStore.CameraSnapshot camera) {
        List<CameraCapabilityStore.CodecSnapshot> codecs = new ArrayList<>();
        for (var codec : camera.codecs()) {
            if (codec.codec() == com.dvid.dcam.feature.device.domain.camera.VideoCodec.H264
                    && codec.state() == CameraCapabilityStore.CodecState.ACTIVE) {
                codecs.add(new CameraCapabilityStore.CodecSnapshot(codec.codec(), codec.state(),
                        codec.unsupportedReason(), List.of(), Optional.empty()));
            } else {
                codecs.add(codec);
            }
        }
        return new CameraCapabilityStore.CameraSnapshot(camera.cameraId(),
                camera.hardwareSignature(), codecs, Optional.empty(), Optional.empty(),
                camera.sensorOrientationDegrees());
    }

    private Snapshot snapshot() {
        return authority.snapshot().orElse(null);
    }

    private String resolvedCameraId(String cameraId) {
        return cameraId == null || cameraId.isBlank() ? primaryCameraId() : cameraId;
    }

    private CameraId cameraId(String value) {
        if (value == null || value.isBlank())
            return null;
        for (String id : cameraIds())
            if (id.equals(value))
                return new CameraId(value);
        return null;
    }

    private boolean cameraPermissionGranted() {
        return DcamPermissions.cameraGranted(context);
    }
}
