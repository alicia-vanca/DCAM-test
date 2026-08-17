package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageMode;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DcamMediaOutputImplTest {
    @Test void sharedRuntimePreparationCreatesPublicStorageStagingDirectories(
            @TempDir Path root) {
        DcamStorage storage = new DcamStorage(
                StorageMode.PUBLIC_DCIM, MediaPartitionLocation.INTERNAL, root.toFile());
        CapturingLogger logger = new CapturingLogger();
        DcamMediaOutputImpl output =
                new DcamMediaOutputImpl(null, storage, () -> false, logger);
        LocalDateTime at = LocalDateTime.of(2026, 7, 28, 12, 0);
        DcamMediaFile video = storage.mediaFile(
                DcamFileType.VIDEO, "0", "operator", at, false);
        DcamMediaFile image = storage.mediaFile(
                DcamFileType.IMAGE, "0", "operator", at, false);

        output.prepareVideoFile(video);
        output.prepareImageFile(image);

        assertTrue(video.getFile().getParentFile().isDirectory());
        assertTrue(image.getFile().getParentFile().isDirectory());
        assertTrue(logger.infoMessages.contains(
                "media_stage type=video file=" + video.getFileName()));
        assertFalse(logger.infoMessages.stream().anyMatch(message ->
                message.contains(image.getFileName())));
    }

    @Test void mountedRecoverySkipsActiveStagedVideo(@TempDir Path root) throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaReservation reservation = new DcamMediaReservation();
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, new NoOpLogger(), reservation);
        DcamMediaFile active = output.mediaFile(DcamFileType.VIDEO, "0", "operator", true);
        Files.createDirectories(active.getFile().toPath().getParent());
        Files.write(active.getFile().toPath(), new byte[] {1, 2, 3});
        DcamEncryptionJournal.markComplete(active);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<StagedMediaRecoveryReport> report = new AtomicReference<>();

        output.recoverStaged(value -> {
            report.set(value);
            completed.countDown();
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertTrue(active.getFile().isFile());
        assertFalse(storage.finalFile(active).exists());
        assertEquals(0, report.get().getRecovered());
        reservation.releaseActiveStaging(active.getFile());
    }

    @Test void releasingReservationPreservesFailedStagedMedia(@TempDir Path root)
            throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaReservation reservation = new DcamMediaReservation();
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, new NoOpLogger(), reservation);
        DcamMediaFile failed = output.mediaFile(
                DcamFileType.VIDEO, "0", "operator", false);
        Files.createDirectories(failed.getFile().toPath().getParent());
        Files.write(failed.getFile().toPath(), new byte[] {1, 2, 3});

        output.releaseMediaReservation(failed);

        assertTrue(failed.getFile().isFile());
        assertFalse(reservation.activeStagingPaths().contains(
                failed.getFile().getAbsolutePath()));
    }

    @Test void cleanFinalizationPatchesAndPublishesWithoutCopyArtifacts(@TempDir Path root)
            throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output =
                new DcamMediaOutputImpl(null, storage, () -> false, new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "0", "operator",
                LocalDateTime.of(2026, 7, 31, 18, 29), false);
        output.prepareVideoFile(media);
        DcamRecordingOutput recordingOutput = output.openVideoOutput(media);
        writeFully(recordingOutput,
                DcamInterruptedMp4FinalizerTest.fragmentedVideoWithZeroDuration());
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<File> published = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicReference<Boolean> cleanupPendingAtSuccess = new AtomicReference<>();

        output.finalizeCleanVideo(null, media, recordingOutput, 2_000_000L,
                new DcamMediaOutput.FinalizationCallback() {
                    @Override public void onSuccess(File finalFile) {
                        published.set(finalFile);
                        cleanupPendingAtSuccess.set(
                                media.getFile().getParentFile().isDirectory());
                        completed.countDown();
                    }

                    @Override public void onFailure(Exception error) {
                        failure.set(error);
                        completed.countDown();
                    }
                });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertTrue(failure.get() == null, String.valueOf(failure.get()));
        assertTrue(published.get() != null && published.get().isFile());
        assertTrue(cleanupPendingAtSuccess.get());
        assertFalse(media.getFile().exists());
        assertEquals(20_000L, DcamInterruptedMp4FinalizerTest.movieDuration(
                Files.readAllBytes(published.get().toPath())));
        try (var files = Files.walk(root)) {
            assertTrue(files.noneMatch(path -> {
                String name = path.getFileName().toString();
                return name.contains(".repair-") || name.contains(".publishing-");
            }));
        }
    }
    @Test void encryptedCleanFinalizationPublishesSegmentedAesGcm(@TempDir Path root)
            throws Exception {
        String password = "123456";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, () -> password, new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "0", "operator",
                LocalDateTime.of(2026, 8, 4, 10, 15), true);
        output.prepareVideoFile(media);
        DcamRecordingOutput recordingOutput = output.openVideoOutput(media);
        writeFully(recordingOutput,
                DcamInterruptedMp4FinalizerTest.fragmentedVideoWithZeroDuration());
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<File> published = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();

        output.finalizeCleanVideo(null, media, recordingOutput, 2_000_000L,
                new DcamMediaOutput.FinalizationCallback() {
                    @Override public void onSuccess(File finalFile) {
                        published.set(finalFile);
                        completed.countDown();
                    }

                    @Override public void onFailure(Exception error) {
                        failure.set(error);
                        completed.countDown();
                    }
                });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertTrue(failure.get() == null, String.valueOf(failure.get()));
        assertTrue(SegmentedAesGcmMediaStore.hasFamilyMagic(published.get()));
        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(published.get(), password)) {
            assertEquals(20_000L, DcamInterruptedMp4FinalizerTest.movieDuration(
                    readAll(decrypted)));
        }
        assertFalse(media.getFile().exists());
        assertFalse(DcamEncryptionJournal.isComplete(media));
        try (var files = Files.walk(root)) {
            assertTrue(files.noneMatch(path -> {
                String name = path.getFileName().toString();
                return name.contains(".repair-") || name.contains(".publishing-")
                        || name.endsWith(".enc-complete");
            }));
        }
    }
    @Test void openPlainAudioM4aFinalizationPublishesGpsRoute(@TempDir Path root)
            throws Exception {
        verifyOpenAudioM4aFinalization(root, false, 2_000_000L);
    }

    @Test void openLiveEncryptedAudioM4aFinalizationPublishesGpsRoute(@TempDir Path root)
            throws Exception {
        verifyOpenAudioM4aFinalization(root, true, 2_000_000L);
    }

    @Test void openAudioM4aFinalizationFallsBackWhenDurationUnavailable(
            @TempDir Path root) throws Exception {
        verifyOpenAudioM4aFinalization(root, false, 0L);
    }
    @Test void encryptedPhotoPublishesByMoveWithoutPlaintextOrCopy(@TempDir Path root)
            throws Exception {
        String password = "123456";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, () -> password, new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.IMAGE, "0", "operator",
                LocalDateTime.of(2026, 8, 12, 10, 30), true);
        byte[] plaintext = jpeg(1920, 1080);
        output.prepareImageFile(media);
        output.openSegmentedAesGcmJpegOutput(media).write(plaintext);
        Files.setLastModifiedTime(media.getFile().toPath(),
                java.nio.file.attribute.FileTime.fromMillis(1_600_000_000_000L));
        java.nio.file.attribute.FileTime stagedTime =
                Files.getLastModifiedTime(media.getFile().toPath());
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<File> published = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();

        output.finalizeSaved(null, media, null, new DcamMediaOutput.FinalizationCallback() {
            @Override public void onSuccess(File finalFile) {
                published.set(finalFile);
                completed.countDown();
            }

            @Override public void onFailure(Exception error) {
                failure.set(error);
                completed.countDown();
            }
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertTrue(failure.get() == null, String.valueOf(failure.get()));
        assertTrue(published.get() != null && published.get().isFile());
        assertEquals(stagedTime, Files.getLastModifiedTime(published.get().toPath()));
        assertFalse(media.getFile().exists());
        assertTrue(SegmentedAesGcmMediaStore.hasFamilyMagic(published.get()));
        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(published.get(), password)) {
            assertArrayEquals(plaintext, readAll(decrypted));
        }
        try (var files = Files.walk(root)) {
            assertTrue(files.noneMatch(path -> {
                String name = path.getFileName().toString();
                return name.contains(".publishing-") || name.endsWith(".enc-complete")
                        || name.equals(media.getFileName().replace("_enc.jpg", ".jpg"));
            }));
        }
    }

    @Test void encryptedPhotoWithoutSegmentedMagicNeverPublishes(@TempDir Path root)
            throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, () -> "123456", new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.IMAGE, "0", "operator",
                LocalDateTime.of(2026, 8, 12, 10, 31), true);
        output.prepareImageFile(media);
        Files.write(media.getFile().toPath(), jpeg(640, 480));
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<File> published = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();

        output.finalizeSaved(null, media, null, new DcamMediaOutput.FinalizationCallback() {
            @Override public void onSuccess(File finalFile) {
                published.set(finalFile);
                completed.countDown();
            }

            @Override public void onFailure(Exception error) {
                failure.set(error);
                completed.countDown();
            }
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertTrue(failure.get() != null);
        assertTrue(failure.get().getMessage().contains("no Segmented AES-GCM envelope"));
        assertTrue(media.getFile().isFile());
        assertFalse(storage.finalFile(media).exists());
        assertTrue(published.get() == null);
    }

    @Test void completedLegacyAesCtrPhotoStillPublishes(@TempDir Path root)
            throws Exception {
        String password = "123456";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, () -> password, new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.IMAGE, "0", "operator",
                LocalDateTime.of(2026, 8, 12, 10, 32), true);
        byte[] plaintext = jpeg(640, 480);
        output.prepareImageFile(media);
        Files.write(media.getFile().toPath(), plaintext);

        output.encryptSaved(null, media, password);
        File published = output.finalizeSavedNow(null, media);
        File decrypted = root.resolve("legacy-photo.jpg").toFile();
        BodycamMediaCrypto.decryptFile(published, decrypted, password);

        assertTrue(published.isFile());
        assertFalse(media.getFile().exists());
        assertArrayEquals(plaintext, Files.readAllBytes(decrypted.toPath()));
    }

    @Test void encryptedAudioCheckpointsStayBelowDoubleStorage(@TempDir Path root)
            throws Exception {
        String password = "123456";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, () -> password, new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.AUDIO, "0", "operator",
                LocalDateTime.of(2026, 8, 11, 10, 20), true);
        byte[] second = new byte[8 * 1024];
        long logicalBytes;

        try (DcamRecordingOutput recordingOutput = output.openAudioOutput(media)) {
            for (int checkpoint = 0; checkpoint < 32; checkpoint++) {
                writeFully(recordingOutput, second);
                recordingOutput.checkpoint();
            }
            logicalBytes = recordingOutput.size();
            recordingOutput.finish();
        }

        byte[] physical = Files.readAllBytes(media.getFile().toPath());
        DcamSegmentedGcmFormat.Header header = DcamSegmentedGcmFormat.readHeader(
                java.util.Arrays.copyOf(physical, DcamSegmentedGcmFormat.FILE_HEADER_BYTES));
        assertEquals(SegmentedAesGcmMediaStore.AUDIO_RECORDING_BLOCK_BYTES,
                header.blockBytes);
        assertEquals(1_000, header.kdfIterations);
        assertTrue(media.getFile().length() < logicalBytes * 3L / 2L);
    }

    @Test void finalizedSegmentedAesGcmAudioPublishesByMove(@TempDir Path root)
            throws Exception {
        String password = "123456";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, () -> password, new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.AUDIO, "0", "operator",
                LocalDateTime.of(2026, 8, 11, 10, 21), true);
        byte[] plaintext = new byte[8 * 1024];

        try (DcamRecordingOutput recordingOutput = output.openAudioOutput(media)) {
            writeFully(recordingOutput, plaintext);
            recordingOutput.finish();
        }
        Files.setLastModifiedTime(media.getFile().toPath(),
                java.nio.file.attribute.FileTime.fromMillis(1_600_000_000_000L));
        java.nio.file.attribute.FileTime stagedTime =
                Files.getLastModifiedTime(media.getFile().toPath());

        File published = output.finalizeSavedNow(null, media);

        assertEquals(stagedTime, Files.getLastModifiedTime(published.toPath()));
        assertFalse(media.getFile().exists());
        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(published, password)) {
            assertArrayEquals(plaintext, readAll(decrypted));
        }
    }

    @Test void unfinalizedSegmentedAesGcmNeverPublishes(@TempDir Path root)
            throws Exception {
        String password = "123456";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, () -> password, new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.AUDIO, "0", "operator",
                LocalDateTime.of(2026, 8, 11, 10, 22), true);
        try (DcamRecordingOutput recordingOutput = output.openAudioOutput(media)) {
            writeFully(recordingOutput, new byte[] {1, 2, 3, 4});
        }

        assertThrows(IOException.class, () -> output.finalizeSavedNow(null, media));

        assertTrue(media.getFile().isFile());
        assertFalse(storage.finalFile(media).exists());
    }

    @Test void repairsMp4DurationBeforePlaintextPublication(@TempDir Path root) throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output =
                new DcamMediaOutputImpl(null, storage, () -> false, new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "0", "operator",
                LocalDateTime.of(2026, 7, 31, 18, 30), false);
        output.prepareVideoFile(media);
        Files.write(media.getFile().toPath(),
                DcamInterruptedMp4FinalizerTest.fragmentedVideoWithZeroDuration());

        File published = output.finalizeSavedNow(null, media);

        assertTrue(published.isFile());
        assertFalse(media.getFile().exists());
        assertEquals(1_000L, DcamInterruptedMp4FinalizerTest.movieDuration(
                Files.readAllBytes(published.toPath())));
    }

    @Test void repairsMp4DurationBeforeEncryptedPublication(@TempDir Path root) throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output =
                new DcamMediaOutputImpl(null, storage, () -> false, new NoOpLogger());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "0", "operator",
                LocalDateTime.of(2026, 7, 31, 18, 31), true);
        output.prepareVideoFile(media);
        Files.write(media.getFile().toPath(),
                DcamInterruptedMp4FinalizerTest.fragmentedVideoWithZeroDuration());

        output.encryptSaved(null, media, "123456");
        File published = output.finalizeSavedNow(null, media);
        File decrypted = root.resolve("decrypted.mp4").toFile();
        BodycamMediaCrypto.decryptFile(published, decrypted, "123456");

        assertTrue(published.isFile());
        assertFalse(media.getFile().exists());
        assertEquals(1_000L, DcamInterruptedMp4FinalizerTest.movieDuration(
                Files.readAllBytes(decrypted.toPath())));
    }
    @Test void finalizationFailureLogsFileStageAndReason(@TempDir Path root) throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        CapturingLogger logger = new CapturingLogger();
        DcamMediaOutputImpl output =
                new DcamMediaOutputImpl(null, storage, () -> false, logger);
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "0", "operator",
                LocalDateTime.of(2026, 7, 30, 10, 21, 52), false);
        output.prepareVideoFile(media);
        Files.createFile(media.getFile().toPath());
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Exception> callbackFailure = new AtomicReference<>();

        output.finalizeSaved(null, media, null, new DcamMediaOutput.FinalizationCallback() {
            @Override public void onSuccess(File finalFile) {
                completed.countDown();
            }

            @Override public void onFailure(Exception failure) {
                callbackFailure.set(failure);
                completed.countDown();
            }
        });

        assertTrue(completed.await(5, TimeUnit.SECONDS));
        assertTrue(callbackFailure.get() != null);
        assertTrue(media.getFile().isFile());
        assertTrue(logger.errorMessages.stream().anyMatch(message ->
                message.contains(media.getFileName())
                        && message.contains("Failed stage: container finalization")
                        && message.contains("MP4 duration finalization failed")
                        && message.contains("remains in Temp")));
    }

    @Test void stagedRecoveryFailureReturnsReportInsteadOfEscapingExecutor() {
        RuntimeException failure = new IllegalStateException("temp unavailable");

        StagedMediaRecoveryReport report = DcamMediaOutputImpl.recoverSafely(() -> {
            throw failure;
        });

        assertTrue(report.hasFailure());
        assertSame(failure, report.getFailure());
    }

    @Test void recoveryOnlyResolvesExplicitlyCompletedFiles() {
        StagedMediaRecoveryReport report =
                new StagedMediaRecoveryReport(1, 0, 0, Set.of("video.mp4"));

        assertTrue(report.isResolved("video.mp4"));
        assertFalse(report.isResolved("other.mp4"));
    }

    @Test void emptySuccessfulRecoveryResolvesNoFinalizationFile() {
        StagedMediaRecoveryReport report = new StagedMediaRecoveryReport(0, 0, 0);

        assertFalse(report.isResolved("video.mp4"));
    }

    @Test void failedRecoveryResolvesNoFinalizationFile() {
        StagedMediaRecoveryReport report =
                StagedMediaRecoveryReport.failed(new IllegalStateException("temp unavailable"));

        assertFalse(report.isResolved("video.mp4"));
    }

    @Test void successfulStagedRecoveryReportIsPreserved() {
        StagedMediaRecoveryReport expected = new StagedMediaRecoveryReport(1, 2, 3);

        StagedMediaRecoveryReport report = DcamMediaOutputImpl.recoverSafely(() -> expected);

        assertFalse(report.hasFailure());
        assertSame(expected, report);
    }

    private static void verifyOpenAudioM4aFinalization(
            Path root, boolean encrypted, long durationUs) throws Exception {
        String password = "audio-m4a-live-encryption";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaOutputImpl output = new DcamMediaOutputImpl(
                null, storage, () -> false, () -> password, new NoOpLogger());
        DcamMediaFile media = output.durableAudioMediaFile(
                "CAM001", "000001", encrypted);
        DcamRecordingOutput recordingOutput = output.openAudioOutput(media);
        writeFully(recordingOutput,
                DcamInterruptedMp4FinalizerTest.interruptedAudioM4aWithGpsRoute());

        File published = output.finalizeOpenAudioM4aNow(
                null, media, recordingOutput, durationUs);

        assertTrue(published.isFile());
        assertFalse(media.getFile().exists());
        assertEquals(encrypted, SegmentedAesGcmMediaStore.hasFamilyMagic(published));
        byte[] logicalBytes;
        if (encrypted) {
            try (SegmentedAesGcmMediaStore decrypted =
                         SegmentedAesGcmMediaStore.openPublished(published, password)) {
                logicalBytes = readAll(decrypted);
            }
        } else {
            logicalBytes = Files.readAllBytes(published.toPath());
        }
        assertTrue(DcamInterruptedMp4FinalizerTest.movieDuration(logicalBytes) > 0L);
        assertEquals(2, gpsRoutePointCount(logicalBytes));
        File[] sidecars = published.getParentFile().listFiles(
                file -> file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".md5"));
        assertTrue(sidecars == null || sidecars.length == 0);
    }

    private static int gpsRoutePointCount(byte[] data) {
        ByteBuffer bytes = ByteBuffer.wrap(data);
        int count = 0;
        int offset = 0;
        while (offset <= data.length - 8) {
            long boxBytes = Integer.toUnsignedLong(bytes.getInt(offset));
            if (boxBytes < 8L || boxBytes > data.length - offset) break;
            if (bytes.getInt(offset + 4) == DcamFragmentedMp4Layout.BOX_UUID
                    && boxBytes == DcamFragmentedMp4Layout.GPS_ROUTE_BOX_BYTES) {
                int content = offset + 8;
                if (bytes.getLong(content)
                                == DcamFragmentedMp4Layout.GPS_ROUTE_UUID_MOST_SIGNIFICANT_BITS
                        && bytes.getLong(content + 8)
                                == DcamFragmentedMp4Layout.GPS_ROUTE_UUID_LEAST_SIGNIFICANT_BITS
                        && bytes.getInt(content + 16) == 1) {
                    count++;
                }
            }
            offset += (int) boxBytes;
        }
        return count;
    }
    private static void writeFully(DcamRecordingOutput output, byte[] bytes) throws Exception {
        ByteBuffer source = ByteBuffer.wrap(bytes);
        while (source.hasRemaining()) assertTrue(output.write(source) > 0);
    }

    private static byte[] readAll(DcamRandomAccessMedia media) throws Exception {
        media.position(0L);
        ByteBuffer target = ByteBuffer.allocate(Math.toIntExact(media.size()));
        while (target.hasRemaining()) {
            int read = media.read(target);
            if (read < 0) break;
        }
        return target.array();
    }

    private static byte[] jpeg(int width, int height) {
        return new byte[] {
                (byte) 0xff, (byte) 0xd8,
                (byte) 0xff, (byte) 0xe0, 0x00, 0x04, 0x01, 0x02,
                (byte) 0xff, (byte) 0xc0, 0x00, 0x11, 0x08,
                (byte) (height >>> 8), (byte) height,
                (byte) (width >>> 8), (byte) width,
                0x03,
                0x01, 0x11, 0x00,
                0x02, 0x11, 0x00,
                0x03, 0x11, 0x00,
                (byte) 0xff, (byte) 0xda, 0x00, 0x08,
                0x01, 0x01, 0x00, 0x00, 0x3f, 0x00,
                0x00, (byte) 0xff, (byte) 0xd9
        };
    }
    private static final class CapturingLogger implements Logger {
        private final List<String> infoMessages = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();

        @Override public void debug(String message) {}
        @Override public void info(String message) { infoMessages.add(message); }
        @Override public void info(String message, Throwable error) { infoMessages.add(message); }
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {
            errorMessages.add(message);
        }
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    }
}
