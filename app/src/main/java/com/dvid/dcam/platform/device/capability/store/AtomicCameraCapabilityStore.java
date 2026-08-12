package com.dvid.dcam.platform.device.capability.store;

import android.util.AtomicFile;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AtomicCameraCapabilityStore implements CameraCapabilityStore {
    private static final ExecutorService PROCESS_WRITER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "dcam-capability-store");
                thread.setDaemon(true);
                return thread;
            });
    private static final Map<String, WriteCoordinator> PROCESS_WRITERS =
            new ConcurrentHashMap<>();

    private final SnapshotFile snapshotFile;
    private final SnapshotXmlCodec xmlCodec;
    private final WriteCoordinator writeCoordinator;
    private final Logger logger;

    public AtomicCameraCapabilityStore(File file, Logger logger) {
        this(production(Objects.requireNonNull(file, "file")), logger);
    }

    private AtomicCameraCapabilityStore(ProductionParts parts, Logger logger) {
        this(parts.snapshotFile(), parts.xmlCodec(), parts.writeCoordinator(), logger);
    }

    AtomicCameraCapabilityStore(SnapshotFile snapshotFile, SnapshotXmlCodec xmlCodec,
            Executor writer, Logger logger) {
        this(snapshotFile, xmlCodec,
                new WriteCoordinator(snapshotFile, xmlCodec, writer), logger);
    }

    AtomicCameraCapabilityStore(SnapshotFile snapshotFile, SnapshotXmlCodec xmlCodec,
            WriteCoordinator writeCoordinator, Logger logger) {
        this.snapshotFile = Objects.requireNonNull(snapshotFile, "snapshotFile");
        this.xmlCodec = Objects.requireNonNull(xmlCodec, "xmlCodec");
        this.writeCoordinator = Objects.requireNonNull(
                writeCoordinator, "writeCoordinator");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public LoadResult loadPersisted() {
        Snapshot snapshot;
        try {
            snapshot = xmlCodec.decode(snapshotFile.read());
        } catch (FileNotFoundException error) {
            return LoadResult.rebuildNeeded(RebuildReason.MISSING);
        } catch (UnsupportedSnapshotFormatException error) {
            logger.warn("Saved camera capabilities use an unsupported format. A new capability scan is required.", error);
            return LoadResult.rebuildNeeded(RebuildReason.UNSUPPORTED_FORMAT);
        } catch (IOException | InvalidSnapshotXmlException | RuntimeException error) {
            logger.warn("Saved camera capabilities could not be read. A new capability scan is required.", error);
            return LoadResult.rebuildNeeded(RebuildReason.CORRUPT);
        }
        return LoadResult.loaded(snapshot);
    }

    @Override public LoadResult load(Freshness freshness) {
        Objects.requireNonNull(freshness, "freshness");
        Snapshot snapshot;
        try {
            snapshot = xmlCodec.decode(snapshotFile.read());
        } catch (FileNotFoundException error) {
            return LoadResult.rebuildNeeded(RebuildReason.MISSING);
        } catch (UnsupportedSnapshotFormatException error) {
            logger.warn("Saved camera capabilities use an unsupported format. A new capability scan is required.", error);
            return LoadResult.rebuildNeeded(RebuildReason.UNSUPPORTED_FORMAT);
        } catch (IOException | InvalidSnapshotXmlException | RuntimeException error) {
            logger.warn("Saved camera capabilities could not be read. A new capability scan is required.", error);
            return LoadResult.rebuildNeeded(RebuildReason.CORRUPT);
        }

        FilteredSnapshot filtered = filter(snapshot, freshness);
        if (!filtered.invalidatedCameras().isEmpty()
                || !filtered.invalidatedPipelines().isEmpty()) {
            RebuildReason reason = filtered.invalidatedCameras().isEmpty()
                    ? RebuildReason.PIPELINE_IDENTITY_MISMATCH
                    : RebuildReason.HARDWARE_MISMATCH;
            logger.info("Saved camera capabilities need a partial rebuild."
                    + " Invalidated cameras=" + filtered.invalidatedCameras().size()
                    + ", pipelines=" + filtered.invalidatedPipelines().size() + ".");
            return LoadResult.loadedWithInvalidatedEvidence(filtered.snapshot(), reason,
                    filtered.invalidatedCameras(), filtered.invalidatedPipelines());
        }
        logger.info("Camera image qualities loaded. "
                + selectedImageQualities(filtered.snapshot()) + ".");
        return LoadResult.loaded(filtered.snapshot());
    }


    @Override public void requestWrite(Snapshot snapshot) {
        writeCoordinator.request(Objects.requireNonNull(snapshot, "snapshot"), logger);
    }

    @Override public void writeNow(Snapshot snapshot) {
        writeCoordinator.writeNow(Objects.requireNonNull(snapshot, "snapshot"), logger);
    }

    private static FilteredSnapshot filter(Snapshot snapshot, Freshness freshness) {
        boolean rootHardwareMismatch = !snapshot.hardwareSignature().equals(
                freshness.hardwareSignature());
        Map<CameraId, String> currentHardware = freshness.cameraHardwareSignatures();
        TreeSet<CameraId> invalidatedCameras = new TreeSet<>();
        Set<CameraId> storedCameraIds = new HashSet<>();
        List<CameraSnapshot> hardwareValidCameras = new ArrayList<>();
        for (CameraSnapshot camera : snapshot.cameras()) {
            storedCameraIds.add(camera.cameraId());
            String currentSignature = currentHardware.get(camera.cameraId());
            if (!camera.hardwareSignature().equals(currentSignature)) {
                invalidatedCameras.add(camera.cameraId());
                continue;
            }
            hardwareValidCameras.add(camera);
        }
        for (CameraId currentCamera : currentHardware.keySet()) {
            if (!storedCameraIds.contains(currentCamera)) {
                invalidatedCameras.add(currentCamera);
            }
        }
        if (rootHardwareMismatch && invalidatedCameras.isEmpty()) {
            invalidatedCameras.addAll(currentHardware.keySet());
            hardwareValidCameras.clear();
        }

        Set<PipelineIdentity> currentPipelines = freshness.pipelineIdentities();
        Set<PipelineIdentity> storedPipelines = new TreeSet<>();
        List<CameraSnapshot> cameras = new ArrayList<>();
        for (CameraSnapshot camera : hardwareValidCameras) {
            List<CodecSnapshot> codecs = new ArrayList<>();
            for (CodecSnapshot codec : camera.codecs()) {
                List<PipelineEvidence> pipelines = new ArrayList<>();
                for (PipelineEvidence pipeline : codec.pipelines()) {
                    PipelineIdentity identity = PipelineIdentity.of(pipeline);
                    storedPipelines.add(identity);
                    if (currentPipelines.contains(identity)) pipelines.add(pipeline);
                }
                Set<VerificationPipelineId> retainedIds = new HashSet<>();
                for (PipelineEvidence pipeline : pipelines) {
                    retainedIds.add(pipeline.verificationPipelineId());
                }
                Optional<PipelineComparisonSnapshot> comparison = codec.comparison()
                        .filter(value -> retainedIds.contains(
                                value.comparison().pipelineA())
                                && retainedIds.contains(
                                value.comparison().pipelineB()));
                codecs.add(new CodecSnapshot(codec.codec(), codec.state(),
                        codec.unsupportedReason(), pipelines, comparison));
            }

            Optional<SelectedPipeline> selectedPipeline = camera.selectedPipeline()
                    .filter(value -> selectedPipelineExists(camera.cameraId(), value, codecs));
            Optional<SelectedRecordingProfile> recordingProfile =
                    camera.selectedRecordingProfile()
                            .filter(value -> currentPipelines.contains(new PipelineIdentity(
                                    camera.cameraId(), value.codec(),
                                    value.verificationPipelineId())))
                            .filter(value -> selectedPipeline.isEmpty()
                                    || selectedPipeline.orElseThrow().pipelineId().equals(
                                    value.verificationPipelineId()));
            cameras.add(new CameraSnapshot(camera.cameraId(), camera.hardwareSignature(),
                    codecs, selectedPipeline, recordingProfile,
                    camera.sensorOrientationDegrees()));
        }

        TreeSet<PipelineIdentity> eligibleCurrentPipelines = new TreeSet<>();
        for (PipelineIdentity identity : currentPipelines) {
            if (!invalidatedCameras.contains(identity.cameraId())) {
                eligibleCurrentPipelines.add(identity);
            }
        }
        TreeSet<PipelineIdentity> invalidatedPipelines = new TreeSet<>(storedPipelines);
        invalidatedPipelines.removeAll(eligibleCurrentPipelines);
        TreeSet<PipelineIdentity> absentPipelines = new TreeSet<>(eligibleCurrentPipelines);
        absentPipelines.removeAll(storedPipelines);
        invalidatedPipelines.addAll(absentPipelines);

        List<CameraId> cameraOrder = rootHardwareMismatch
                || !invalidatedCameras.isEmpty()
                ? List.of() : snapshot.cameraOrderOverride();
        Snapshot filtered = new Snapshot(snapshot.format(), snapshot.initializationState(),
                freshness.hardwareSignature(), cameraOrder, cameras);
        return new FilteredSnapshot(filtered, invalidatedCameras, invalidatedPipelines);
    }

    private static boolean selectedPipelineExists(CameraId cameraId, SelectedPipeline selected,
            List<CodecSnapshot> codecs) {
        VerificationPipelineId expected = selected.pipelineId();
        for (CodecSnapshot codec : codecs) {
            for (PipelineEvidence pipeline : codec.pipelines()) {
                if (pipeline.cameraId().equals(cameraId)
                        && pipeline.verificationPipelineId().equals(expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    static byte[] readFully(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static ProductionParts production(File file) {
        SnapshotXmlCodec xmlCodec = new SnapshotXmlCodec();
        SnapshotFile snapshotFile = new AndroidSnapshotFile(file);
        WriteCoordinator coordinator = PROCESS_WRITERS.computeIfAbsent(
                fileKey(file), ignored -> new WriteCoordinator(
                        snapshotFile, xmlCodec, PROCESS_WRITER));
        return new ProductionParts(snapshotFile, xmlCodec, coordinator);
    }

    private static String fileKey(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException error) {
            return file.getAbsoluteFile().toPath().normalize().toString();
        }
    }

    interface SnapshotFile {
        byte[] read() throws IOException;
        void write(byte[] bytes) throws IOException;
    }

    private static String selectedImageQualities(Snapshot snapshot) {
        List<String> qualities = new ArrayList<>();
        for (var camera : snapshot.cameras()) {
            camera.selectedRecordingProfile().ifPresent(profile -> {
                var image = profile.tuple().imageMode().resolution();
                qualities.add("Camera " + camera.cameraId().value() + ": "
                        + image.label() + " (" + image.actual() + ")");
            });
        }
        return qualities.isEmpty() ? "none" : String.join("; ", qualities);
    }

    private static String savedImageQualityMessage(Snapshot snapshot) {
        List<String> messages = new ArrayList<>();
        for (var camera : snapshot.cameras()) {
            camera.selectedRecordingProfile().ifPresent(profile -> {
                var image = profile.tuple().imageMode().resolution();
                messages.add("Camera " + camera.cameraId().value()
                        + " image capture quality saved: " + image.label()
                        + " (" + image.actual() + ").");
            });
        }
        return String.join(" ", messages);
    }

    static final class WriteCoordinator {
        private final Object lock = new Object();
        private final SnapshotFile snapshotFile;
        private final SnapshotXmlCodec xmlCodec;
        private final Executor writer;
        private boolean writeRunning;
        private WriteRequest pending;
        private String lastSavedImageQualities = "none";

        WriteCoordinator(SnapshotFile snapshotFile, SnapshotXmlCodec xmlCodec,
                Executor writer) {
            this.snapshotFile = Objects.requireNonNull(snapshotFile, "snapshotFile");
            this.xmlCodec = Objects.requireNonNull(xmlCodec, "xmlCodec");
            this.writer = Objects.requireNonNull(writer, "writer");
        }

        void request(Snapshot snapshot, Logger logger) {
            enqueue(new WriteRequest(snapshot, logger, List.of()));
        }

        void writeNow(Snapshot snapshot, Logger logger) {
            WriteCompletion completion = new WriteCompletion();
            enqueue(new WriteRequest(snapshot, logger, List.of(completion)));
            completion.await();
        }

        private void enqueue(WriteRequest supplied) {
            WriteRequest request = supplied;
            synchronized (lock) {
                if (writeRunning) {
                    if (pending != null) request = request.supersede(pending);
                    pending = request;
                    return;
                }
                writeRunning = true;
            }
            WriteRequest scheduled = request;
            try {
                writer.execute(() -> writeLoop(scheduled));
            } catch (RuntimeException error) {
                WriteRequest dropped;
                synchronized (lock) {
                    writeRunning = false;
                    dropped = pending;
                    pending = null;
                }
                scheduled.complete(error);
                if (dropped != null) dropped.complete(error);
                scheduled.logger().error(
                        "Camera image quality persistence failed. Attempted image qualities: "
                                + selectedImageQualities(scheduled.snapshot()) + "."
                                + (dropped == null ? ""
                                        : " One pending update was also discarded."),
                        error);
            }
        }

        private void writeLoop(WriteRequest firstRequest) {
            WriteRequest current = firstRequest;
            while (true) {
                try {
                    snapshotFile.write(xmlCodec.encode(current.snapshot()));
                } catch (IOException | RuntimeException error) {
                    WriteRequest dropped;
                    synchronized (lock) {
                        writeRunning = false;
                        dropped = pending;
                        pending = null;
                    }
                    current.complete(error);
                    if (dropped != null) dropped.complete(error);
                    current.logger().error(
                            "Camera image quality persistence failed. Attempted image qualities: "
                                    + selectedImageQualities(current.snapshot()) + "."
                                    + (dropped == null ? ""
                                            : " One pending update was also discarded."),
                            error);
                    return;
                }
                current.complete(null);
                String imageQualities = selectedImageQualities(current.snapshot());
                if (!imageQualities.equals(lastSavedImageQualities)) {
                    String message = savedImageQualityMessage(current.snapshot());
                    if (!message.isEmpty()) current.logger().info(message);
                    lastSavedImageQualities = imageQualities;
                }
                synchronized (lock) {
                    if (pending == null) {
                        writeRunning = false;
                        return;
                    }
                    current = pending;
                    pending = null;
                }
            }
        }
    }

    private static final class AndroidSnapshotFile implements SnapshotFile {
        private final AtomicFile file;

        private AndroidSnapshotFile(File file) {
            this.file = new AtomicFile(Objects.requireNonNull(file, "file"));
        }

        @Override public byte[] read() throws IOException {
            try (InputStream input = file.openRead()) {
                return readFully(input);
            }
        }

        @Override public void write(byte[] bytes) throws IOException {
            FileOutputStream output = file.startWrite();
            try {
                output.write(bytes);
                output.flush();
                file.finishWrite(output);
            } catch (IOException | RuntimeException error) {
                file.failWrite(output);
                throw error;
            }
        }
    }

    private record ProductionParts(
            SnapshotFile snapshotFile,
            SnapshotXmlCodec xmlCodec,
            WriteCoordinator writeCoordinator) {}

    private record WriteRequest(Snapshot snapshot, Logger logger,
            List<WriteCompletion> completions) {
        private WriteRequest {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(logger, "logger");
            completions = List.copyOf(Objects.requireNonNull(completions, "completions"));
        }

        private WriteRequest supersede(WriteRequest previous) {
            List<WriteCompletion> merged = new ArrayList<>(previous.completions());
            merged.addAll(completions);
            return new WriteRequest(snapshot, logger, merged);
        }

        private void complete(Throwable error) {
            for (WriteCompletion completion : completions) completion.complete(error);
        }
    }

    private static final class WriteCompletion {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Throwable error;

        private void complete(Throwable value) {
            error = value;
            latch.countDown();
        }

        private void await() {
            try {
                latch.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("camera capability write interrupted", interrupted);
            }
            if (error != null) {
                throw new IllegalStateException("camera capability write failed", error);
            }
        }
    }

    private record FilteredSnapshot(
            Snapshot snapshot,
            Set<CameraId> invalidatedCameras,
            Set<PipelineIdentity> invalidatedPipelines) {}
}