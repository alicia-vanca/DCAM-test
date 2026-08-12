package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dvid.dcam.core.logging.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DcamStagedMediaRecoveryTest {
    @TempDir Path root;
    private final CapturingLogger logger = new CapturingLogger();

    @Test
    void publishesPlayableContractCandidateFoundAfterRestart() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> playable()).recover();

        assertTrue(storage.finalFile(media).isFile());
        assertFalse(media.getFile().exists());
        assertTrue(report.getRecovered() == 1);
        assertTrue(report.isResolved(media.getFileName()));
    }

    @Test
    void absentCandidateIsNotReportedResolved() {
        StagedMediaRecoveryReport report = recovery(
                new DcamStorage(root.toFile()), (type, file) -> playable()).recover();

        assertFalse(report.isResolved("missing.mp4"));
    }

    @Test
    void usesFilenameCaptureDateForFinalFolder() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        Files.setLastModifiedTime(media.getFile().toPath(),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));

        recovery(storage, (type, file) -> playable()).recover();

        assertTrue(storage.finalFile(media).getPath().contains("Media" + File.separator + "Video"
                + File.separator + "2026-07-11"));
    }

    @Test
    void preservesUnplayableCandidateForLaterRecoveryOrSupport() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> rejected()).recover();

        assertTrue(media.getFile().isFile());
        assertFalse(storage.finalFile(media).exists());
        assertTrue(report.getPreserved() == 1);
        assertFalse(report.isResolved(media.getFileName()));
        assertTrue(logger.warningMessages.stream().anyMatch(message ->
                message.contains(media.getFileName())
                        && message.contains("Playback validation failed after finalization: MP4 is missing moov metadata")));
    }

    @Test
    void preservesCompleteFragmentedMp4WhenDurationCannotBeRepaired() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), completeFragmentedMp4());

        StagedMediaRecoveryReport report = recovery(storage, (type, file) ->
                DcamMediaValidationResult.rejectedNonPositiveDuration("video", 0L)).recover();

        assertTrue(media.getFile().isFile());
        assertFalse(storage.finalFile(media).exists());
        assertTrue(report.getPreserved() == 1);
        assertFalse(report.isResolved(media.getFileName()));
    }

    @Test
    void completeFragmentsDoNotOverrideMissingVideoTrack() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), completeFragmentedMp4());

        StagedMediaRecoveryReport report = recovery(storage, (type, file) ->
                DcamMediaValidationResult.rejected(
                        "Android metadata reports no video track.")).recover();

        assertTrue(media.getFile().isFile());
        assertFalse(storage.finalFile(media).exists());
        assertTrue(report.getPreserved() == 1);
        assertFalse(report.isResolved(media.getFileName()));
    }

    @Test
    void preservesEncryptedNameUntilEncryptionJournalCompletes() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, true);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> playable()).recover();

        assertTrue(media.getFile().isFile());
        assertFalse(storage.finalFile(media).exists());
        assertTrue(report.getPreserved() == 1);
        assertFalse(report.isResolved(media.getFileName()));
    }

    @Test
    void publishesEncryptedNameAfterEncryptionJournalCompletes() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, true);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        DcamEncryptionJournal.markComplete(media);

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> rejected()).recover();

        assertTrue(storage.finalFile(media).isFile());
        assertFalse(media.getFile().exists());
        assertTrue(report.getRecovered() == 1);
        assertTrue(report.isResolved(media.getFileName()));
        assertFalse(logger.warningMessages.stream().anyMatch(message ->
                message.contains(".enc-complete")
                        && message.contains("does not match the DCAM media contract")));
    }

    @Test
    void recoversSegmentedGcmMp4WithoutLegacyEncryptionJournal() throws Exception {
        String password = "recording-pass";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, true);
        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(media.getFile(), password)) {
            writeFully(output,
                    DcamInterruptedMp4FinalizerTest.fragmentedVideoWithZeroDuration());
            output.checkpoint();
        }

        StagedMediaRecoveryReport report = recovery(
                storage, logicalPlayableValidator(), null, () -> password).recover();

        File published = storage.finalFile(media);
        assertTrue(published.isFile());
        assertFalse(media.getFile().exists());
        assertEquals(1, report.getRecovered());
        assertTrue(report.isResolved(media.getFileName()));
        assertFalse(DcamEncryptionJournal.isComplete(media));
        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(published, password)) {
            assertTrue(DcamInterruptedMp4FinalizerTest.movieDuration(
                    readAll(decrypted)) > 0L);
        }
    }

    @Test
    void removesHeaderOnlySegmentedGcmCandidate() throws Exception {
        String password = "recording-pass";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, true);
        try (DcamRecordingOutput ignored =
                     DcamRecordingOutput.openSegmentedAesGcm(media.getFile(), password)) {
        }
        assertEquals(DcamSegmentedGcmFormat.FILE_HEADER_BYTES, media.getFile().length());

        StagedMediaRecoveryReport report = recovery(
                storage, logicalPlayableValidator(), null, () -> password).recover();

        assertFalse(media.getFile().exists());
        assertFalse(storage.finalFile(media).exists());
        assertEquals(0, report.getPreserved());
        assertTrue(report.isResolved(media.getFileName()));
    }

    @Test
    void publishesFinalizedSegmentedGcmByMoveAndCreatesMd5() throws Exception {
        String password = "recording-pass";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, true);
        byte[] plaintext = new byte[] {1, 2, 3, 4};
        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(media.getFile(), password)) {
            writeFully(output, plaintext);
            output.finish();
        }
        Files.setLastModifiedTime(media.getFile().toPath(),
                java.nio.file.attribute.FileTime.fromMillis(1_600_000_000_000L));
        java.nio.file.attribute.FileTime stagedTime =
                Files.getLastModifiedTime(media.getFile().toPath());

        StagedMediaRecoveryReport report = recovery(
                storage, logicalPlayableValidator(), () -> true, () -> password).recover();

        File published = storage.finalFile(media);
        Path sidecar = published.toPath().resolveSibling(
                published.getName().replaceFirst("\\.mp4$", ".md5"));
        assertTrue(published.isFile());
        assertEquals(stagedTime, Files.getLastModifiedTime(published.toPath()));
        assertEquals(DcamMd5RetryProcessor.digest(published),
                Files.readString(sidecar).trim());
        assertEquals(1, report.getRecovered());
        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(published, password)) {
            assertArrayEquals(plaintext, readAll(decrypted));
        }
    }

    @Test
    void md5FailureAfterSegmentedMoveStillReportsRecovery() throws Exception {
        String password = "recording-pass";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, true);
        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(media.getFile(), password)) {
            writeFully(output, new byte[] {1, 2, 3, 4});
            output.finish();
        }

        StagedMediaRecoveryReport report = new DcamStagedMediaRecovery(
                storage, new DcamMediaFinalizer(storage), logicalPlayableValidator(),
                () -> true, () -> password,
                file -> { throw new IOException("md5 unavailable"); }, logger).recover();

        assertTrue(storage.finalFile(media).isFile());
        assertFalse(media.getFile().exists());
        assertEquals(1, report.getRecovered());
        assertEquals(0, report.getPreserved());
        assertTrue(report.isResolved(media.getFileName()));
    }

    @Test
    void trimsIncompleteAuthenticatedAacTailBeforeSegmentedGcmFinal() throws Exception {
        String password = "recording-pass";
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.AUDIO, "CAM001", "000001",
                LocalDateTime.of(2026, 8, 11, 10, 30), true);
        Files.createDirectories(media.getFile().toPath().getParent());
        byte[] complete = adtsFrame(new byte[] {1, 2, 3, 4});
        byte[] next = adtsFrame(new byte[] {5, 6, 7, 8, 9});
        try (DcamRecordingOutput output =
                     DcamRecordingOutput.openSegmentedAesGcm(media.getFile(), password)) {
            writeFully(output, complete);
            writeFully(output, java.util.Arrays.copyOf(next, next.length - 2));
            output.checkpoint();
        }

        StagedMediaRecoveryReport report = recovery(
                storage, logicalPlayableValidator(), null, () -> password).recover();

        File published = storage.finalFile(media);
        assertTrue(published.isFile());
        assertEquals(1, report.getRecovered());
        try (SegmentedAesGcmMediaStore decrypted =
                     SegmentedAesGcmMediaStore.openPublished(published, password)) {
            assertArrayEquals(complete, readAll(decrypted));
        }
    }
    @Test
    void neverOverwritesFinalMediaWhenStagingDuplicateExists() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        File target = storage.finalFile(media);
        Files.createDirectories(target.toPath().getParent());
        Files.write(target.toPath(), new byte[] {9});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> playable()).recover();

        assertTrue(media.getFile().isFile());
        assertTrue(target.length() == 1L);
        assertTrue(report.getDuplicates() == 1);
        assertFalse(report.isResolved(media.getFileName()));
    }

    @Test
    void removesByteIdenticalAudioDuplicate() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.AUDIO, "CAM001", "000001",
                LocalDateTime.of(2026, 8, 4, 10, 25, 13), false);
        Files.createDirectories(media.getFile().toPath().getParent());
        byte[] content = new byte[] {1, 2, 3};
        Files.write(media.getFile().toPath(), content);
        File target = storage.finalFile(media);
        Files.createDirectories(target.toPath().getParent());
        Files.write(target.toPath(), content);

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> playable()).recover();

        assertFalse(media.getFile().exists());
        assertTrue(target.isFile());
        assertTrue(report.getDuplicates() == 1);
        assertTrue(report.isResolved(media.getFileName()));
    }

    @Test
    void removesEmptyAudioCandidateAndDateDirectory() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.AUDIO, "CAM001", "000001",
                LocalDateTime.of(2026, 8, 4, 10, 25, 11), false);
        Files.createDirectories(media.getFile().toPath().getParent());
        Files.createFile(media.getFile().toPath());
        File dateDirectory = media.getFile().getParentFile();

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> rejected()).recover();

        assertFalse(media.getFile().exists());
        assertFalse(dateDirectory.exists());
        assertTrue(report.getPreserved() == 0);
        assertTrue(report.isResolved(media.getFileName()));
    }

    @Test
    void removesEmptyDatedDirectoryWithoutCandidates() throws Exception {
        File dateDirectory = root.resolve("Temp/2026-08-03").toFile();
        Files.createDirectories(dateDirectory.toPath());

        recovery(new DcamStorage(root.toFile()), (type, file) -> playable()).recover();

        assertFalse(dateDirectory.exists());
    }

    @Test
    void removesHiddenInterruptedCopyOnlyWhileOriginalStagingExists() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        File target = storage.finalFile(media);
        Files.createDirectories(target.toPath().getParent());
        File partial = new File(target.getParentFile(),
                "." + target.getName() + ".publishing-interrupted");
        Files.write(partial.toPath(), new byte[] {1});

        recovery(storage, (type, file) -> rejected()).recover();

        assertFalse(partial.exists());
        assertTrue(media.getFile().isFile());
    }

    @Test
    void logsPublicationFailureReasonWhenRecoveryCannotPublish() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        Files.write(root.resolve("Media"), new byte[] {9});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> playable()).recover();

        assertTrue(media.getFile().isFile());
        assertTrue(report.getPreserved() == 1);
        assertTrue(logger.errorMessages.stream().anyMatch(message ->
                message.contains(media.getFileName())
                        && message.contains("recovery publication failed")
                        && message.contains("Cannot create final media directory")));
        assertTrue(logger.errorCauses.stream().anyMatch(error -> error != null));
    }

    @Test
    void recoverySnapshotExcludesFilesCreatedAfterScheduling() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile interrupted = storage.mediaFile(
                DcamFileType.IMAGE, "CAM001", "000001",
                LocalDateTime.of(2026, 7, 11, 10, 30), false);
        Files.createDirectories(interrupted.getFile().toPath().getParent());
        Files.write(interrupted.getFile().toPath(), new byte[] {1, 2, 3});
        DcamStagedMediaRecovery recovery = recovery(storage, (type, file) -> playable());
        File[][] snapshot = recovery.snapshot();
        DcamMediaFile active = storage.mediaFile(
                DcamFileType.IMAGE, "CAM001", "000001",
                LocalDateTime.of(2026, 7, 11, 10, 31), false);
        Files.write(active.getFile().toPath(), new byte[] {4, 5, 6});

        StagedMediaRecoveryReport report = recovery.recover(snapshot);

        assertTrue(storage.finalFile(interrupted).isFile());
        assertTrue(active.getFile().isFile());
        assertFalse(storage.finalFile(active).exists());
        assertTrue(report.getRecovered() == 1);
    }
    @Test
    void recoverySnapshotExcludesActiveStagedCandidate() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile active = storage.mediaFile(
                DcamFileType.IMAGE, "CAM001", "000001",
                LocalDateTime.of(2026, 7, 11, 10, 32), false);
        Files.createDirectories(active.getFile().toPath().getParent());
        Files.write(active.getFile().toPath(), new byte[] {4, 5, 6});
        DcamStagedMediaRecovery recovery = recovery(storage, (type, file) -> playable());

        StagedMediaRecoveryReport report = recovery.recover(
                recovery.snapshot(java.util.Set.of(active.getFile().getAbsolutePath())));

        assertTrue(active.getFile().isFile());
        assertFalse(storage.finalFile(active).exists());
        assertTrue(report.getRecovered() == 0);
    }

    @Test
    void createsMd5ForRecoveredVideoWhenPolicyEnabled() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.writeString(media.getFile().toPath(), "abc");

        recovery(storage, (type, file) -> playable(), () -> true).recover();

        File published = storage.finalFile(media);
        Path sidecar = published.toPath().resolveSibling(
                published.getName().replaceFirst("\\.mp4$", ".md5"));
        assertTrue(sidecar.toFile().isFile());
        assertTrue(Files.readString(sidecar).trim()
                .equals("900150983cd24fb0d6963f7d28e17f72"));
    }

    @Test
    void skipsMd5ForRecoveredVideoWhenPolicyDisabled() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.writeString(media.getFile().toPath(), "abc");

        recovery(storage, (type, file) -> playable(), () -> false).recover();

        File published = storage.finalFile(media);
        Path sidecar = published.toPath().resolveSibling(
                published.getName().replaceFirst("\\.mp4$", ".md5"));
        assertFalse(sidecar.toFile().exists());
    }

    @Test
    void publishesPlayableAudioCandidateFoundAfterRestart() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.AUDIO, "CAM001", "000001",
                LocalDateTime.of(2026, 7, 11, 10, 30), false);
        Files.createDirectories(media.getFile().toPath().getParent());
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> playable()).recover();

        assertTrue(storage.finalFile(media).isFile());
        assertFalse(media.getFile().exists());
        assertTrue(report.getRecovered() == 1);
    }
    private DcamStagedMediaRecovery recovery(DcamStorage storage, DcamMediaValidator validator) {
        return recovery(storage, validator, null);
    }

    private DcamStagedMediaRecovery recovery(
            DcamStorage storage,
            DcamMediaValidator validator,
            java.util.function.BooleanSupplier createVideoMd5) {
        return recovery(storage, validator, createVideoMd5, () -> "");
    }

    private DcamStagedMediaRecovery recovery(
            DcamStorage storage,
            DcamMediaValidator validator,
            java.util.function.BooleanSupplier createVideoMd5,
            java.util.function.Supplier<String> mediaEncryptionPassword) {
        return new DcamStagedMediaRecovery(
                storage, new DcamMediaFinalizer(storage), validator, createVideoMd5,
                mediaEncryptionPassword, logger);
    }

    private static DcamMediaValidator logicalPlayableValidator() {
        return new DcamMediaValidator() {
            @Override public DcamMediaValidationResult validate(DcamFileType type, File file) {
                return DcamMediaValidationResult.rejected(
                        "Segmented-GCM recovery used the physical file validator.");
            }

            @Override public DcamMediaValidationResult validate(
                    DcamFileType type, DcamRandomAccessMedia media) {
                return playable();
            }
        };
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

    private static byte[] adtsFrame(byte[] payload) {
        int frameLength = payload.length + 7;
        byte[] frame = new byte[frameLength];
        frame[0] = (byte) 0xff;
        frame[1] = (byte) 0xf1;
        frame[2] = 0x4c;
        frame[3] = (byte) (0x80 | frameLength >>> 11);
        frame[4] = (byte) (frameLength >>> 3);
        frame[5] = (byte) ((frameLength & 7) << 5 | 0x1f);
        frame[6] = (byte) 0xfc;
        System.arraycopy(payload, 0, frame, 7, payload.length);
        return frame;
    }
    private static byte[] completeFragmentedMp4() throws IOException {
        ByteArrayOutputStream media = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(media)) {
            writeBox(output, "ftyp", new byte[0]);
            ByteArrayOutputStream moov = new ByteArrayOutputStream();
            try (DataOutputStream moovOutput = new DataOutputStream(moov)) {
                writeBox(moovOutput, "mvex", new byte[0]);
            }
            writeBox(output, "moov", moov.toByteArray());
            writeBox(output, "moof", new byte[0]);
            writeBox(output, "mdat", new byte[] {1});
        }
        return media.toByteArray();
    }

    private static void writeBox(DataOutputStream output, String type, byte[] content)
            throws IOException {
        output.writeInt(8 + content.length);
        output.writeBytes(type);
        output.write(content);
    }

    private static DcamMediaValidationResult playable() {
        return DcamMediaValidationResult.accepted("Test media is playable.");
    }

    private static DcamMediaValidationResult rejected() {
        return DcamMediaValidationResult.rejected("MP4 is missing moov metadata.");
    }

    private static DcamMediaFile staged(DcamStorage storage, boolean encrypted) throws Exception {
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001",
                LocalDateTime.of(2026, 7, 11, 10, 30), encrypted);
        Files.createDirectories(media.getFile().toPath().getParent());
        return media;
    }

    private static final class CapturingLogger implements Logger {
        private final List<String> warningMessages = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();
        private final List<Throwable> errorCauses = new ArrayList<>();

        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {
            warningMessages.add(message);
        }
        @Override public void error(String message, Throwable error) {
            errorMessages.add(message);
            errorCauses.add(error);
        }
    }
}
