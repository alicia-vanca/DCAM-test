package com.dvid.dcam.feature.device.application.port;

import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraModeOrder;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparison;
import com.dvid.dcam.feature.device.domain.camera.PipelineComparisonInput;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationOutcome;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public interface CameraCapabilityStore {
    int FORMAT_VERSION = 1;

    LoadResult load(Freshness freshness);

    void requestWrite(Snapshot snapshot);

    default void writeNow(Snapshot snapshot) { requestWrite(snapshot); }

    enum LoadStatus {
        LOADED,
        LOADED_WITH_INVALIDATED_EVIDENCE,
        REBUILD_NEEDED
    }

    enum RebuildReason {
        NONE,
        MISSING,
        CORRUPT,
        UNSUPPORTED_FORMAT,
        HARDWARE_MISMATCH,
        PIPELINE_IDENTITY_MISMATCH
    }

    enum InitializationState { INCOMPLETE, READY_REUSABLE }

    enum CodecState { ACTIVE, UNSUPPORTED }

    enum UnsupportedCodecReason { NOT_IMPLEMENTED }

    enum PipelineSelectionMode { AUTO, FIXED }

    enum PipelineDecisionStatus {
        FIXED, PROFILE_VERIFIED, FAST_COMPLETE, VERIFIED_COMPLETE
    }

    enum PipelineDecisionReason {
        FIXED,
        ONLY_AVAILABLE,
        VERIFIED_RECORDING_PROFILE,
        STRICT_SUPERSET,
        BEST_TUPLE,
        TUPLE_COUNT,
        PASS_COUNT,
        PERFORMANCE,
        FINAL_TIE_A
    }

    record PipelineIdentity(
            CameraId cameraId,
            VideoCodec codec,
            VerificationPipelineId verificationPipelineId)
            implements Comparable<PipelineIdentity> {
        public PipelineIdentity {
            Objects.requireNonNull(cameraId, "cameraId");
            Objects.requireNonNull(codec, "codec");
            Objects.requireNonNull(verificationPipelineId, "verificationPipelineId");
        }

        public static PipelineIdentity of(PipelineEvidence evidence) {
            Objects.requireNonNull(evidence, "evidence");
            return new PipelineIdentity(evidence.cameraId(), evidence.codec(),
                    evidence.verificationPipelineId());
        }

        @Override public int compareTo(PipelineIdentity other) {
            int cameraOrder = cameraId.compareTo(other.cameraId);
            if (cameraOrder != 0) return cameraOrder;
            int codecOrder = codec.id().compareTo(other.codec.id());
            if (codecOrder != 0) return codecOrder;
            return verificationPipelineId.compareTo(other.verificationPipelineId);
        }
    }

    record Freshness(
            String hardwareSignature,
            Map<CameraId, String> cameraHardwareSignatures,
            Set<PipelineIdentity> pipelineIdentities) {
        public Freshness {
            hardwareSignature = required(hardwareSignature, "hardwareSignature");
            cameraHardwareSignatures = immutableHardwareSignatures(
                    cameraHardwareSignatures);
            TreeSet<PipelineIdentity> identities = new TreeSet<>();
            for (PipelineIdentity identity : Objects.requireNonNull(
                    pipelineIdentities, "pipelineIdentities")) {
                PipelineIdentity checked = Objects.requireNonNull(
                        identity, "pipelineIdentity");
                if (!cameraHardwareSignatures.containsKey(checked.cameraId())) {
                    throw new IllegalArgumentException(
                            "pipeline identity has no camera hardware signature");
                }
                identities.add(checked);
            }
            pipelineIdentities = Set.copyOf(identities);
        }
    }

    record LoadResult(
            LoadStatus status,
            RebuildReason reason,
            Optional<Snapshot> snapshot,
            Set<CameraId> invalidatedCameras,
            Set<PipelineIdentity> invalidatedPipelines) {
        public LoadResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(snapshot, "snapshot");
            invalidatedCameras = Set.copyOf(Objects.requireNonNull(
                    invalidatedCameras, "invalidatedCameras"));
            invalidatedPipelines = Set.copyOf(Objects.requireNonNull(
                    invalidatedPipelines, "invalidatedPipelines"));
            boolean loaded = status != LoadStatus.REBUILD_NEEDED;
            if (loaded != snapshot.isPresent()) {
                throw new IllegalArgumentException("load status does not match snapshot");
            }
            if (status == LoadStatus.LOADED
                    && (reason != RebuildReason.NONE
                    || !invalidatedCameras.isEmpty()
                    || !invalidatedPipelines.isEmpty())) {
                throw new IllegalArgumentException("loaded result must not need rebuild");
            }
            if (status == LoadStatus.LOADED_WITH_INVALIDATED_EVIDENCE
                    && (reason != RebuildReason.HARDWARE_MISMATCH
                    && reason != RebuildReason.PIPELINE_IDENTITY_MISMATCH)) {
                throw new IllegalArgumentException("invalidated result needs identity reason");
            }
            if (status == LoadStatus.LOADED_WITH_INVALIDATED_EVIDENCE
                    && invalidatedCameras.isEmpty()
                    && invalidatedPipelines.isEmpty()) {
                throw new IllegalArgumentException("invalidated result needs affected scope");
            }
            if (status == LoadStatus.REBUILD_NEEDED
                    && (reason == RebuildReason.NONE
                    || reason == RebuildReason.HARDWARE_MISMATCH
                    || reason == RebuildReason.PIPELINE_IDENTITY_MISMATCH)) {
                throw new IllegalArgumentException("rebuild result needs terminal reason");
            }
        }

        public static LoadResult loaded(Snapshot snapshot) {
            return new LoadResult(LoadStatus.LOADED, RebuildReason.NONE,
                    Optional.of(snapshot), Set.of(), Set.of());
        }

        public static LoadResult loadedWithInvalidatedEvidence(
                Snapshot snapshot,
                RebuildReason reason,
                Collection<CameraId> invalidatedCameras,
                Collection<PipelineIdentity> invalidatedPipelines) {
            return new LoadResult(LoadStatus.LOADED_WITH_INVALIDATED_EVIDENCE,
                    reason, Optional.of(snapshot), Set.copyOf(invalidatedCameras),
                    Set.copyOf(invalidatedPipelines));
        }

        public static LoadResult rebuildNeeded(RebuildReason reason) {
            return new LoadResult(LoadStatus.REBUILD_NEEDED, reason,
                    Optional.empty(), Set.of(), Set.of());
        }

        public boolean rebuildNeeded() { return status != LoadStatus.LOADED; }
    }

    record Snapshot(
            int format,
            InitializationState initializationState,
            String hardwareSignature,
            List<CameraId> cameraOrderOverride,
            List<CameraSnapshot> cameras) {
        public Snapshot {
            if (format != FORMAT_VERSION) {
                throw new IllegalArgumentException("unsupported capability format");
            }
            Objects.requireNonNull(initializationState, "initializationState");
            hardwareSignature = required(hardwareSignature, "hardwareSignature");
            cameraOrderOverride = immutableUniqueCameraIds(cameraOrderOverride);
            cameras = immutableUniqueCameras(cameras);
            Set<CameraId> cameraIds = new HashSet<>();
            for (CameraSnapshot camera : cameras) cameraIds.add(camera.cameraId());
            if (!cameraIds.containsAll(cameraOrderOverride)) {
                throw new IllegalArgumentException(
                        "camera order override contains unknown camera ID");
            }
        }

        public Snapshot(int format, String hardwareSignature,
                List<CameraId> cameraOrderOverride, List<CameraSnapshot> cameras) {
            this(format, InitializationState.INCOMPLETE, hardwareSignature,
                    cameraOrderOverride, cameras);
        }

        public static Snapshot current(String hardwareSignature,
                Collection<CameraId> cameraOrderOverride,
                Collection<CameraSnapshot> cameras) {
            return new Snapshot(FORMAT_VERSION, InitializationState.INCOMPLETE,
                    hardwareSignature, List.copyOf(cameraOrderOverride),
                    List.copyOf(cameras));
        }

        public Snapshot withInitializationState(InitializationState state) {
            return new Snapshot(format, state, hardwareSignature,
                    cameraOrderOverride, cameras);
        }
    }

    record CameraSnapshot(
            CameraId cameraId,
            String hardwareSignature,
            List<CodecSnapshot> codecs,
            Optional<SelectedPipeline> selectedPipeline,
            Optional<SelectedRecordingProfile> selectedRecordingProfile,
            int sensorOrientationDegrees) {
        public CameraSnapshot {
            Objects.requireNonNull(cameraId, "cameraId");
            hardwareSignature = required(hardwareSignature, "cameraHardwareSignature");
            sensorOrientationDegrees = normalizeSensorOrientationDegrees(sensorOrientationDegrees);
            codecs = immutableUniqueCodecs(codecs);
            Objects.requireNonNull(selectedPipeline, "selectedPipeline");
            Objects.requireNonNull(selectedRecordingProfile, "selectedRecordingProfile");
            for (CodecSnapshot codec : codecs) codec.requireCamera(cameraId);
            if (selectedPipeline.isPresent()) {
                selectedPipeline.orElseThrow().requireAvailable(cameraId, codecs);
            }
            if (selectedRecordingProfile.isPresent()) {
                selectedRecordingProfile.orElseThrow().requireVerified(cameraId, codecs);
            }
            if (selectedPipeline.isPresent() && selectedRecordingProfile.isPresent()
                    && !selectedPipeline.orElseThrow().pipelineId().equals(
                    selectedRecordingProfile.orElseThrow().verificationPipelineId())) {
                throw new IllegalArgumentException(
                        "selected pipeline and recording profile differ");
            }
        }

        public CameraSnapshot(CameraId cameraId, String hardwareSignature,
                List<CodecSnapshot> codecs, Optional<SelectedPipeline> selectedPipeline,
                Optional<SelectedRecordingProfile> selectedRecordingProfile) {
            this(cameraId, hardwareSignature, codecs, selectedPipeline,
                    selectedRecordingProfile, 0);
        }
    }

    record CodecSnapshot(
            VideoCodec codec,
            CodecState state,
            Optional<UnsupportedCodecReason> unsupportedReason,
            List<PipelineEvidence> pipelines,
            Optional<PipelineComparisonSnapshot> comparison) {
        public CodecSnapshot {
            Objects.requireNonNull(codec, "codec");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(unsupportedReason, "unsupportedReason");
            pipelines = immutableUniquePipelines(pipelines);
            Objects.requireNonNull(comparison, "comparison");
            if (state == CodecState.ACTIVE && unsupportedReason.isPresent()) {
                throw new IllegalArgumentException("active codec cannot have unsupported reason");
            }
            if (state == CodecState.UNSUPPORTED
                    && (unsupportedReason.isEmpty()
                    || !pipelines.isEmpty()
                    || comparison.isPresent())) {
                throw new IllegalArgumentException(
                        "unsupported codec cannot contain pipeline evidence");
            }
            for (PipelineEvidence pipeline : pipelines) {
                if (pipeline.codec() != codec) {
                    throw new IllegalArgumentException("pipeline belongs to another codec");
                }
                for (var fact : pipeline.candidateEvidence().entrySet()) {
                    if (!fact.getValue().isTerminalEvidence()) {
                        throw new IllegalArgumentException(
                                "only durable verification facts may be persisted");
                    }
                    if (!pipeline.hasRawScope(fact.getKey())) {
                        throw new IllegalArgumentException(
                                "verification fact is outside raw fast candidate scope");
                    }
                }
            }
            if (comparison.isPresent()) {
                comparison.orElseThrow().requireConsistent(pipelines);
            }
        }

        private void requireCamera(CameraId cameraId) {
            for (PipelineEvidence pipeline : pipelines) {
                if (!pipeline.cameraId().equals(cameraId)) {
                    throw new IllegalArgumentException("pipeline belongs to another camera");
                }
            }
        }
    }

    record PipelineVerificationCoverage(
            int plannedImageCount,
            int verifiedImageCount,
            int plannedTupleCount,
            int verifiedTupleCount) {
        public PipelineVerificationCoverage {
            if (plannedImageCount < 0 || verifiedImageCount < 0
                    || plannedTupleCount <= 0 || verifiedTupleCount < 0
                    || verifiedImageCount > plannedImageCount
                    || verifiedTupleCount > plannedTupleCount) {
                throw new IllegalArgumentException("invalid pipeline verification coverage");
            }
        }
    }

    record PipelineComparisonSnapshot(
            PipelineComparison comparison,
            OptionalLong pipelineAMedianTotalVerifyMillis,
            OptionalLong pipelineBMedianTotalVerifyMillis,
            Optional<PipelineVerificationCoverage> pipelineACoverage,
            Optional<PipelineVerificationCoverage> pipelineBCoverage) {
        public PipelineComparisonSnapshot(PipelineComparison comparison,
                OptionalLong pipelineAMedianTotalVerifyMillis,
                OptionalLong pipelineBMedianTotalVerifyMillis) {
            this(comparison, pipelineAMedianTotalVerifyMillis,
                    pipelineBMedianTotalVerifyMillis, Optional.empty(), Optional.empty());
        }

        public PipelineComparisonSnapshot {
            Objects.requireNonNull(comparison, "comparison");
            Objects.requireNonNull(
                    pipelineAMedianTotalVerifyMillis, "pipelineAMedianTotalVerifyMillis");
            Objects.requireNonNull(
                    pipelineBMedianTotalVerifyMillis, "pipelineBMedianTotalVerifyMillis");
            pipelineACoverage = Objects.requireNonNull(pipelineACoverage, "pipelineACoverage");
            pipelineBCoverage = Objects.requireNonNull(pipelineBCoverage, "pipelineBCoverage");
            if (comparison.status() != PipelineComparison.Status.COMPLETE) {
                throw new IllegalArgumentException(
                        "only complete pipeline comparison may be persisted");
            }
            if (pipelineAMedianTotalVerifyMillis.isPresent()
                    != pipelineBMedianTotalVerifyMillis.isPresent()) {
                throw new IllegalArgumentException(
                        "pipeline comparison performance must be complete");
            }
            if (pipelineAMedianTotalVerifyMillis.orElse(0) < 0
                    || pipelineBMedianTotalVerifyMillis.orElse(0) < 0) {
                throw new IllegalArgumentException(
                        "pipeline comparison performance must not be negative");
            }
            if (pipelineACoverage.isPresent() != pipelineBCoverage.isPresent()) {
                throw new IllegalArgumentException(
                        "pipeline verification coverage must be complete");
            }
        }

        public static PipelineComparisonSnapshot withoutPerformance(
                PipelineComparison comparison) {
            return new PipelineComparisonSnapshot(
                    comparison, OptionalLong.empty(), OptionalLong.empty());
        }

        public static PipelineComparisonSnapshot withCoverage(
                PipelineComparison comparison,
                PipelineVerificationCoverage pipelineACoverage,
                PipelineVerificationCoverage pipelineBCoverage) {
            return new PipelineComparisonSnapshot(comparison,
                    OptionalLong.empty(), OptionalLong.empty(),
                    Optional.of(pipelineACoverage), Optional.of(pipelineBCoverage));
        }

        private void requireConsistent(List<PipelineEvidence> pipelines) {
            PipelineEvidence pipelineA = null;
            PipelineEvidence pipelineB = null;
            for (PipelineEvidence pipeline : pipelines) {
                if (pipeline.verificationPipelineId().equals(comparison.pipelineA())) {
                    pipelineA = pipeline;
                }
                if (pipeline.verificationPipelineId().equals(comparison.pipelineB())) {
                    pipelineB = pipeline;
                }
            }
            if (pipelineA == null || pipelineB == null) {
                throw new IllegalArgumentException(
                        "pipeline comparison references absent pipeline");
            }

            TreeSet<CaptureModeTuple> universe = new TreeSet<>(CameraModeOrder.tuples());
            addTuples(universe, pipelineA);
            addTuples(universe, pipelineB);
            universe.addAll(comparison.intersection());
            universe.addAll(comparison.aOnly());
            universe.addAll(comparison.bOnly());
            universe.addAll(comparison.bothFail());
            universe.addAll(comparison.unknown());
            PipelineComparison expected = PipelineComparison.compare(universe,
                    new PipelineComparisonInput(pipelineA, true,
                            pipelineAMedianTotalVerifyMillis),
                    new PipelineComparisonInput(pipelineB, true,
                            pipelineBMedianTotalVerifyMillis));
            if (!expected.equals(comparison)) {
                throw new IllegalArgumentException(
                        "pipeline comparison conflicts with persisted evidence");
            }
            if (pipelineACoverage.isPresent()) {
                requireCoverageMatches(pipelineA, pipelineACoverage.orElseThrow());
                requireCoverageMatches(pipelineB, pipelineBCoverage.orElseThrow());
            }
        }

        private static void requireCoverageMatches(PipelineEvidence pipeline,
                PipelineVerificationCoverage coverage) {
            int verifiedImages = Math.toIntExact(pipeline.rawFastCandidates().stream()
                    .filter(candidate -> candidate.kind() == CandidateKey.Kind.IMAGE)
                    .count());
            int verifiedTuples = Math.toIntExact(pipeline.rawFastCandidates().stream()
                    .filter(candidate -> candidate.kind() == CandidateKey.Kind.TUPLE)
                    .count());
            if (coverage.verifiedImageCount() != verifiedImages
                    || coverage.verifiedTupleCount() != verifiedTuples) {
                throw new IllegalArgumentException(
                        "pipeline verification coverage conflicts with persisted evidence");
            }
        }

        private static void addTuples(
                Set<CaptureModeTuple> universe, PipelineEvidence pipeline) {
            for (CandidateKey candidate : pipeline.rawFastCandidates()) {
                candidate.tuple().ifPresent(universe::add);
            }
            for (CandidateKey candidate : pipeline.candidateEvidence().keySet()) {
                candidate.tuple().ifPresent(universe::add);
            }
        }
    }

    record SelectedPipeline(
            PipelineSelectionMode mode,
            VerificationPipelineId pipelineId,
            PipelineDecisionStatus decisionStatus,
            PipelineDecisionReason decisionReason) {
        public SelectedPipeline {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(pipelineId, "pipelineId");
            Objects.requireNonNull(decisionStatus, "decisionStatus");
            Objects.requireNonNull(decisionReason, "decisionReason");
            if (mode == PipelineSelectionMode.FIXED) {
                if (decisionStatus != PipelineDecisionStatus.FIXED
                        || decisionReason != PipelineDecisionReason.FIXED) {
                    throw new IllegalArgumentException("invalid fixed pipeline decision");
                }
            } else if (decisionStatus == PipelineDecisionStatus.FIXED
                    || decisionReason == PipelineDecisionReason.FIXED) {
                throw new IllegalArgumentException("invalid auto pipeline decision");
            }

            if (decisionStatus == PipelineDecisionStatus.PROFILE_VERIFIED
                    && decisionReason != PipelineDecisionReason.VERIFIED_RECORDING_PROFILE) {
                throw new IllegalArgumentException("invalid profile-verified pipeline reason");
            }
            if (decisionStatus != PipelineDecisionStatus.PROFILE_VERIFIED
                    && decisionReason == PipelineDecisionReason.VERIFIED_RECORDING_PROFILE) {
                throw new IllegalArgumentException(
                        "profile-verified reason requires profile-verified status");
            }
            if (decisionStatus == PipelineDecisionStatus.FAST_COMPLETE
                    && (decisionReason == PipelineDecisionReason.PASS_COUNT
                    || decisionReason == PipelineDecisionReason.PERFORMANCE)) {
                throw new IllegalArgumentException("invalid fast pipeline decision reason");
            }
            if (decisionStatus == PipelineDecisionStatus.VERIFIED_COMPLETE
                    && (decisionReason == PipelineDecisionReason.ONLY_AVAILABLE
                    || decisionReason == PipelineDecisionReason.TUPLE_COUNT)) {
                throw new IllegalArgumentException("invalid verified pipeline decision reason");
            }
        }

        public static SelectedPipeline fixed(VerificationPipelineId pipelineId) {
            return new SelectedPipeline(PipelineSelectionMode.FIXED,
                    Objects.requireNonNull(pipelineId, "pipelineId"),
                    PipelineDecisionStatus.FIXED, PipelineDecisionReason.FIXED);
        }


        public static SelectedPipeline autoProfileVerified(
                VerificationPipelineId pipelineId) {
            return new SelectedPipeline(PipelineSelectionMode.AUTO,
                    Objects.requireNonNull(pipelineId, "pipelineId"),
                    PipelineDecisionStatus.PROFILE_VERIFIED,
                    PipelineDecisionReason.VERIFIED_RECORDING_PROFILE);
        }

        public static SelectedPipeline autoFast(VerificationPipelineId pipelineId,
                PipelineDecisionReason reason) {
            return new SelectedPipeline(PipelineSelectionMode.AUTO,
                    Objects.requireNonNull(pipelineId, "pipelineId"),
                    PipelineDecisionStatus.FAST_COMPLETE, reason);
        }

        public static SelectedPipeline autoVerified(VerificationPipelineId pipelineId,
                PipelineDecisionReason reason) {
            return new SelectedPipeline(PipelineSelectionMode.AUTO,
                    Objects.requireNonNull(pipelineId, "pipelineId"),
                    PipelineDecisionStatus.VERIFIED_COMPLETE, reason);
        }

        private void requireAvailable(CameraId cameraId, List<CodecSnapshot> codecs) {
            for (CodecSnapshot codec : codecs) {
                for (PipelineEvidence pipeline : codec.pipelines()) {
                    if (pipeline.cameraId().equals(cameraId)
                            && pipeline.verificationPipelineId().equals(pipelineId)) {
                        return;
                    }
                }
            }
            throw new IllegalArgumentException("selected pipeline references absent pipeline");
        }
    }
    record SelectedRecordingProfile(
            VideoCodec codec,
            VerificationPipelineId verificationPipelineId,
            CaptureModeTuple tuple) {
        public SelectedRecordingProfile {
            Objects.requireNonNull(codec, "codec");
            Objects.requireNonNull(verificationPipelineId, "verificationPipelineId");
            Objects.requireNonNull(tuple, "tuple");
        }

        private void requireVerified(CameraId cameraId, List<CodecSnapshot> codecs) {
            for (CodecSnapshot codecSnapshot : codecs) {
                if (codecSnapshot.codec() != codec) continue;
                for (PipelineEvidence pipeline : codecSnapshot.pipelines()) {
                    if (pipeline.cameraId().equals(cameraId)
                            && pipeline.verificationPipelineId().equals(
                            verificationPipelineId)
                            && pipeline.outcome(tuple)
                            == VerificationOutcome.VERIFIED_PASS) {
                        return;
                    }
                }
            }
            throw new IllegalArgumentException("selection is not verified pass evidence");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static int normalizeSensorOrientationDegrees(int value) {
        int normalized = ((value % 360) + 360) % 360;
        if (normalized % 90 != 0) {
            throw new IllegalArgumentException(
                    "sensorOrientationDegrees must be a multiple of 90");
        }
        return normalized;
    }

    private static Map<CameraId, String> immutableHardwareSignatures(
            Map<CameraId, String> values) {
        TreeMap<CameraId, String> result = new TreeMap<>();
        for (var entry : Objects.requireNonNull(
                values, "cameraHardwareSignatures").entrySet()) {
            CameraId cameraId = Objects.requireNonNull(entry.getKey(), "cameraId");
            result.put(cameraId, required(
                    entry.getValue(), "cameraHardwareSignature"));
        }
        return Map.copyOf(result);
    }
    private static List<CameraId> immutableUniqueCameraIds(
            Collection<CameraId> values) {
        List<CameraId> result = new ArrayList<>();
        Set<CameraId> seen = new HashSet<>();
        for (CameraId value : Objects.requireNonNull(values, "cameraOrderOverride")) {
            CameraId cameraId = Objects.requireNonNull(value, "cameraId");
            if (!seen.add(cameraId)) {
                throw new IllegalArgumentException("duplicate camera order ID");
            }
            result.add(cameraId);
        }
        return List.copyOf(result);
    }

    private static List<CameraSnapshot> immutableUniqueCameras(
            Collection<CameraSnapshot> values) {
        List<CameraSnapshot> result = new ArrayList<>();
        Set<CameraId> seen = new HashSet<>();
        for (CameraSnapshot value : Objects.requireNonNull(values, "cameras")) {
            CameraSnapshot camera = Objects.requireNonNull(value, "camera");
            if (!seen.add(camera.cameraId())) {
                throw new IllegalArgumentException("duplicate camera ID");
            }
            result.add(camera);
        }
        result.sort(Comparator.comparing(CameraSnapshot::cameraId));
        return List.copyOf(result);
    }

    private static List<CodecSnapshot> immutableUniqueCodecs(
            Collection<CodecSnapshot> values) {
        List<CodecSnapshot> result = new ArrayList<>();
        Set<VideoCodec> seen = new HashSet<>();
        for (CodecSnapshot value : Objects.requireNonNull(values, "codecs")) {
            CodecSnapshot codec = Objects.requireNonNull(value, "codec");
            if (!seen.add(codec.codec())) {
                throw new IllegalArgumentException("duplicate codec ID");
            }
            result.add(codec);
        }
        result.sort(Comparator.comparing(value -> value.codec().id()));
        return List.copyOf(result);
    }

    private static List<PipelineEvidence> immutableUniquePipelines(
            Collection<PipelineEvidence> values) {
        List<PipelineEvidence> result = new ArrayList<>();
        Set<VerificationPipelineId> seen = new HashSet<>();
        for (PipelineEvidence value : Objects.requireNonNull(values, "pipelines")) {
            PipelineEvidence pipeline = Objects.requireNonNull(value, "pipeline");
            if (!seen.add(pipeline.verificationPipelineId())) {
                throw new IllegalArgumentException("duplicate pipeline ID");
            }
            result.add(pipeline);
        }
        result.sort(Comparator.comparing(PipelineEvidence::verificationPipelineId));
        return List.copyOf(result);
    }
}