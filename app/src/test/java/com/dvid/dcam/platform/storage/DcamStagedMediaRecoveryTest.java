package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DcamStagedMediaRecoveryTest {
    @TempDir Path root;

    @Test
    void publishesPlayableContractCandidateFoundAfterRestart() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> true).recover();

        assertTrue(storage.finalFile(media).isFile());
        assertFalse(media.getFile().exists());
        assertTrue(report.getRecovered() == 1);
    }

    @Test
    void usesFilenameCaptureDateForFinalFolder() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        Files.setLastModifiedTime(media.getFile().toPath(),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));

        recovery(storage, (type, file) -> true).recover();

        assertTrue(storage.finalFile(media).getPath().contains("Media" + File.separator + "Video"
                + File.separator + "2026-07-11"));
    }

    @Test
    void preservesUnplayableCandidateForLaterRecoveryOrSupport() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> false).recover();

        assertTrue(media.getFile().isFile());
        assertFalse(storage.finalFile(media).exists());
        assertTrue(report.getPreserved() == 1);
    }

    @Test
    void preservesEncryptedNameBecauseFilesystemCannotProveTransformState() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, true);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> true).recover();

        assertTrue(media.getFile().isFile());
        assertFalse(storage.finalFile(media).exists());
        assertTrue(report.getPreserved() == 1);
    }

    @Test
    void neverOverwritesFinalMediaWhenStagingDuplicateExists() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        File target = storage.finalFile(media);
        Files.createDirectories(target.toPath().getParent());
        Files.write(target.toPath(), new byte[] {9});

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> true).recover();

        assertTrue(media.getFile().isFile());
        assertTrue(target.length() == 1L);
        assertTrue(report.getDuplicates() == 1);
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

        recovery(storage, (type, file) -> false).recover();

        assertFalse(partial.exists());
        assertTrue(media.getFile().isFile());
    }

    @Test
    void createsMd5ForRecoveredVideoWhenPolicyEnabled() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.writeString(media.getFile().toPath(), "abc");

        recovery(storage, (type, file) -> true, () -> true).recover();

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

        recovery(storage, (type, file) -> true, () -> false).recover();

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

        StagedMediaRecoveryReport report = recovery(storage, (type, file) -> true).recover();

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
        return new DcamStagedMediaRecovery(
                storage, new DcamMediaFinalizer(storage), validator, createVideoMd5);
    }

    private static DcamMediaFile staged(DcamStorage storage, boolean encrypted) throws Exception {
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001",
                LocalDateTime.of(2026, 7, 11, 10, 30), encrypted);
        Files.createDirectories(media.getFile().toPath().getParent());
        return media;
    }
}
