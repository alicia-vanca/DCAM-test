package com.dvid.dcam.platform.device.capability.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Freshness;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.InitializationState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.LoadResult;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.LoadStatus;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.PipelineIdentity;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.RebuildReason;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

final class AtomicCameraCapabilityStoreTest {
    private static final Executor DIRECT = Runnable::run;

    @Test void androidSnapshotReaderSupportsStreamsLargerThanBuffer() throws Exception {
        byte[] expected = new byte[20_000];
        Arrays.fill(expected, (byte) 0xA5);

        byte[] actual = AtomicCameraCapabilityStore.readFully(
                new ByteArrayInputStream(expected));

        assertArrayEquals(expected, actual);
    }

    @Test void androidSnapshotReaderAvoidsUnavailableReadAllBytesApi() throws Exception {
        Path root = Files.exists(Path.of("app/src/main/java"))
                ? Path.of("app/src/main/java") : Path.of("src/main/java");
        String source = Files.readString(root.resolve(
                "com/dvid/dcam/platform/device/capability/store/"
                        + "AtomicCameraCapabilityStore.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("input.readAllBytes()"));
    }

    @Test void missingCorruptAndUnsupportedXmlRequestRebuild() {
        TestLogger logger = new TestLogger();

        assertEquals(RebuildReason.MISSING,
                store(new FakeAtomicFile(null), logger).load(
                        CapabilityStoreFixtures.freshness()).reason());
        assertEquals(RebuildReason.CORRUPT,
                store(new FakeAtomicFile("<capabilities".getBytes(StandardCharsets.UTF_8)),
                        logger).load(CapabilityStoreFixtures.freshness()).reason());
        assertEquals(RebuildReason.UNSUPPORTED_FORMAT,
                store(new FakeAtomicFile(("<capabilities format=\"2\""
                        + " hardwareSignature=\"signature-1\" />").getBytes(
                        StandardCharsets.UTF_8)), logger).load(
                        CapabilityStoreFixtures.freshness()).reason());
        assertTrue(logger.debugs.isEmpty());
    }

    @Test void currentFormatLoadsWithoutRewrite() {
        byte[] current = CapabilityStoreFixtures.FORMAT_1_XML.getBytes(StandardCharsets.UTF_8);
        FakeAtomicFile file = new FakeAtomicFile(current);
        TestLogger logger = new TestLogger();
        AtomicCameraCapabilityStore store = store(file, logger);

        LoadResult result = store.load(CapabilityStoreFixtures.freshness());

        assertEquals(LoadStatus.LOADED, result.status());
        assertEquals(0, file.writeAttempts);
        assertArrayEquals(current, file.durableBytes);
        assertEquals(List.of("Camera image qualities loaded. "
                + "Camera 0: FHD (1920x1080); Camera 1: FHD (1920x1080)."),
                logger.infos);
    }

    @Test void persistedLoadAcceptsFormatOneWithoutFreshnessScan() {
        byte[] current = CapabilityStoreFixtures.FORMAT_1_XML.getBytes(StandardCharsets.UTF_8);
        FakeAtomicFile file = new FakeAtomicFile(current);
        AtomicCameraCapabilityStore store = store(file, new TestLogger());

        LoadResult result = store.loadPersisted();

        assertEquals(LoadStatus.LOADED, result.status());
        assertEquals(1, result.snapshot().orElseThrow().format());
        assertEquals(0, file.writeAttempts);
        assertArrayEquals(current, file.durableBytes);
    }
    @Test void hardwareMismatchInvalidatesOnlyAffectedCamera() {
        FakeAtomicFile file = new FakeAtomicFile(
                CapabilityStoreFixtures.FORMAT_1_XML.getBytes(StandardCharsets.UTF_8));
        AtomicCameraCapabilityStore store = store(file, new TestLogger());
        Freshness current = CapabilityStoreFixtures.freshness();
        Freshness freshness = new Freshness("changed-hardware", Map.of(
                new CameraId("0"), "changed-camera-0",
                new CameraId("1"), "camera-signature-1"),
                current.pipelineIdentities());

        LoadResult result = store.load(freshness);

        assertEquals(LoadStatus.LOADED_WITH_INVALIDATED_EVIDENCE, result.status());
        assertEquals(RebuildReason.HARDWARE_MISMATCH, result.reason());
        assertEquals(Set.of(new CameraId("0")), result.invalidatedCameras());
        Snapshot filtered = result.snapshot().orElseThrow();
        assertEquals("changed-hardware", filtered.hardwareSignature());
        assertTrue(filtered.cameraOrderOverride().isEmpty());
        assertEquals(List.of(new CameraId("1")), filtered.cameras().stream()
                .map(camera -> camera.cameraId()).toList());
    }

    @Test void pipelineMismatchInvalidatesOnlyAffectedEvidence() {
        FakeAtomicFile file = new FakeAtomicFile(
                CapabilityStoreFixtures.FORMAT_1_XML.getBytes(StandardCharsets.UTF_8));
        AtomicCameraCapabilityStore store = store(file, new TestLogger());
        PipelineIdentity removed = new PipelineIdentity(new CameraId("0"),
                VideoCodec.H264, CapabilityStoreFixtures.PIPELINE_B);
        Set<PipelineIdentity> current = Set.of(
                new PipelineIdentity(new CameraId("0"), VideoCodec.H264,
                        CapabilityStoreFixtures.PIPELINE_A),
                new PipelineIdentity(new CameraId("1"), VideoCodec.H264,
                        CapabilityStoreFixtures.PIPELINE_A));

        LoadResult result = store.load(new Freshness("signature-1",
                CapabilityStoreFixtures.freshness().cameraHardwareSignatures(),
                current));

        assertEquals(LoadStatus.LOADED_WITH_INVALIDATED_EVIDENCE, result.status());
        assertEquals(Set.of(removed), result.invalidatedPipelines());
        Snapshot filtered = result.snapshot().orElseThrow();
        assertEquals(1, filtered.cameras().get(0).codecs().get(0).pipelines().size());
        assertTrue(filtered.cameras().get(0).codecs().get(0).comparison().isEmpty());
        assertTrue(filtered.cameras().get(0).selectedRecordingProfile().isPresent());
        assertEquals(1, filtered.cameras().get(1).codecs().get(0).pipelines().size());
    }

    @Test void partialWriteFailureKeepsOldDurableSnapshot() {
        FakeAtomicFile file = new FakeAtomicFile(
                CapabilityStoreFixtures.FORMAT_1_XML.getBytes(StandardCharsets.UTF_8));
        file.failNextWrite = true;
        AtomicCameraCapabilityStore store = store(file, new TestLogger());

        store.requestWrite(CapabilityStoreFixtures.snapshot("signature-2"));

        assertTrue(file.partialBytes.length > 0);
        LoadResult result = store.load(CapabilityStoreFixtures.freshness());
        assertEquals(LoadStatus.LOADED, result.status());
        assertEquals("signature-1", result.snapshot().orElseThrow().hardwareSignature());
    }

    @Test void writeNowIsDurableBeforeReturning() throws Exception {
        FakeAtomicFile file = new FakeAtomicFile(null);
        AtomicCameraCapabilityStore store = store(file, new TestLogger());

        store.writeNow(CapabilityStoreFixtures.snapshot("signature-sync"));

        assertEquals("signature-sync", new SnapshotXmlCodec().decode(
                file.durableBytes).hardwareSignature());
    }

    @Test void burstWritesCoalesceAndStaleWriteCannotOverwriteLatestState()
            throws Exception {
        FakeAtomicFile file = new FakeAtomicFile(null);
        AtomicCameraCapabilityStore store = store(file, new TestLogger());
        file.onFirstWrite = () -> {
            store.requestWrite(CapabilityStoreFixtures.snapshot("signature-2"));
            store.requestWrite(CapabilityStoreFixtures.snapshot("signature-3"));
        };

        store.requestWrite(CapabilityStoreFixtures.snapshot("signature-1"));

        assertEquals(2, file.writeAttempts);
        Snapshot durable = new SnapshotXmlCodec().decode(file.durableBytes);
        assertEquals("signature-3", durable.hardwareSignature());
    }

    @Test void storesForSameFileSharePendingStateAndCannotFinishStale()
            throws Exception {
        FakeAtomicFile file = new FakeAtomicFile(null);
        SnapshotXmlCodec codec = new SnapshotXmlCodec();
        AtomicCameraCapabilityStore.WriteCoordinator coordinator =
                new AtomicCameraCapabilityStore.WriteCoordinator(file, codec, DIRECT);
        AtomicCameraCapabilityStore first = new AtomicCameraCapabilityStore(
                file, codec, coordinator, new TestLogger());
        AtomicCameraCapabilityStore second = new AtomicCameraCapabilityStore(
                file, codec, coordinator, new TestLogger());
        file.onFirstWrite = () -> {
            second.requestWrite(CapabilityStoreFixtures.snapshot("signature-2"));
            first.requestWrite(CapabilityStoreFixtures.snapshot("signature-3"));
        };

        first.requestWrite(CapabilityStoreFixtures.snapshot("signature-1"));

        assertEquals(2, file.writeAttempts);
        assertEquals("signature-3", codec.decode(file.durableBytes).hardwareSignature());
    }
    @Test void writeFailureDropsPendingWithoutAutomaticRetry() throws Exception {
        FakeAtomicFile file = new FakeAtomicFile(
                CapabilityStoreFixtures.FORMAT_1_XML.getBytes(StandardCharsets.UTF_8));
        TestLogger logger = new TestLogger();
        AtomicCameraCapabilityStore store = store(file, logger);
        file.failNextWrite = true;
        file.onFirstWrite = () -> store.requestWrite(
                CapabilityStoreFixtures.snapshot("signature-2"));

        store.requestWrite(CapabilityStoreFixtures.snapshot("signature-failed"));

        assertEquals(1, file.writeAttempts);
        assertEquals(1, logger.errors.size());
        assertTrue(logger.errors.get(0).startsWith(
                "Camera image quality persistence failed. Attempted image qualities: "));
        assertTrue(logger.errors.get(0).contains(
                "Camera 0: FHD (1920x1080)"));
        assertTrue(logger.errors.get(0).contains(
                "Camera 1: FHD (1920x1080)"));
        assertTrue(logger.infos.isEmpty());
        assertTrue(logger.debugs.isEmpty());
        assertEquals("signature-1",
                new SnapshotXmlCodec().decode(file.durableBytes).hardwareSignature());

        Snapshot successful = CapabilityStoreFixtures.snapshot("signature-3");
        var selected = successful.cameras().get(0);
        var unselected = successful.cameras().get(1);
        successful = new Snapshot(successful.format(), successful.initializationState(),
                successful.hardwareSignature(), successful.cameraOrderOverride(),
                List.of(selected, new CameraSnapshot(unselected.cameraId(),
                        unselected.hardwareSignature(), unselected.codecs(),
                        unselected.selectedPipeline(), java.util.Optional.empty(),
                        unselected.sensorOrientationDegrees())))
                .withInitializationState(InitializationState.INCOMPLETE);
        store.requestWrite(successful);

        assertEquals(2, file.writeAttempts);
        assertEquals("signature-3",
                new SnapshotXmlCodec().decode(file.durableBytes).hardwareSignature());
        assertEquals(List.of(
                "Camera 0 image capture quality saved: FHD (1920x1080)."),
                logger.infos);

        store.requestWrite(successful.withInitializationState(
                InitializationState.READY_REUSABLE));

        assertEquals(3, file.writeAttempts);
        assertEquals(List.of(
                "Camera 0 image capture quality saved: FHD (1920x1080)."),
                logger.infos);
        assertTrue(logger.debugs.isEmpty());
    }

    private static AtomicCameraCapabilityStore store(
            FakeAtomicFile file, TestLogger logger) {
        return new AtomicCameraCapabilityStore(
                file, new SnapshotXmlCodec(), DIRECT, logger);
    }

    private static final class FakeAtomicFile
            implements AtomicCameraCapabilityStore.SnapshotFile {
        private byte[] durableBytes;
        private byte[] partialBytes = new byte[0];
        private int writeAttempts;
        private boolean failNextWrite;
        private Runnable onFirstWrite;

        private FakeAtomicFile(byte[] durableBytes) {
            this.durableBytes = durableBytes == null ? null : durableBytes.clone();
        }

        @Override public byte[] read() throws IOException {
            if (durableBytes == null) throw new FileNotFoundException("missing");
            return durableBytes.clone();
        }

        @Override public void write(byte[] bytes) throws IOException {
            writeAttempts++;
            if (writeAttempts == 1 && onFirstWrite != null) onFirstWrite.run();
            if (failNextWrite) {
                failNextWrite = false;
                partialBytes = Arrays.copyOf(bytes, Math.max(1, bytes.length / 2));
                throw new IOException("simulated write failure");
            }
            durableBytes = bytes.clone();
        }
    }

    private static final class TestLogger implements Logger {
        private final List<String> debugs = new ArrayList<>();
        private final List<String> infos = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        @Override public void debug(String message) { debugs.add(message); }
        @Override public void info(String message) { infos.add(message); }
        @Override public void info(String message, Throwable error) { infos.add(message); }
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) { errors.add(message); }
    }
}