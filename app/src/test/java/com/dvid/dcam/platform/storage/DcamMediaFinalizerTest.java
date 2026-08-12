package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DcamMediaFinalizerTest {
    @TempDir Path root;

    @Test
    void flushesVerifiesAndPublishesBeforeRemovingStaging() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        byte[] bytes = new byte[] {1, 2, 3, 4};
        Files.write(media.getFile().toPath(), bytes);

        java.io.File published = new DcamMediaFinalizer(storage).finalizeMedia(media);

        assertTrue(published.isFile());
        assertArrayEquals(bytes, Files.readAllBytes(published.toPath()));
        assertFalse(media.getFile().exists());
        assertFalse(published.getName().startsWith("."));
    }

    @Test
    void cleanPublicationRenamesStagingWithoutPublishingCopy() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        byte[] bytes = new byte[] {1, 2, 3, 4};
        Files.write(media.getFile().toPath(), bytes);

        java.io.File published = new DcamMediaFinalizer(storage).finalizeCleanMedia(media);

        assertTrue(published.isFile());
        assertArrayEquals(bytes, Files.readAllBytes(published.toPath()));
        assertFalse(media.getFile().exists());
        try (var files = Files.walk(root)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().contains(".publishing-")));
        }
    }

    @Test
    void cleanPublicationCleanupCanRunAfterSuccessBoundary() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3, 4});
        java.io.File stagingDateDirectory = media.getFile().getParentFile();
        DcamMediaFinalizer finalizer = new DcamMediaFinalizer(storage);

        java.io.File published = finalizer.finalizeCleanMedia(media);

        assertTrue(published.isFile());
        assertTrue(stagingDateDirectory.isDirectory());
        finalizer.cleanupCleanMedia(media);
        assertFalse(stagingDateDirectory.exists());
        assertTrue(published.isFile());
    }

    @Test
    void cleanPublicationConflictPreservesStagingAndExistingFinal() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        java.io.File target = storage.finalFile(media);
        Files.createDirectories(target.toPath().getParent());
        Files.write(target.toPath(), new byte[] {9});

        assertThrows(IOException.class,
                () -> new DcamMediaFinalizer(storage).finalizeCleanMedia(media));

        assertTrue(media.getFile().isFile());
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(target.toPath()));
    }
    @Test
    void publicationConflictPreservesStagingAndExistingFinal() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        java.io.File target = storage.finalFile(media);
        Files.createDirectories(target.toPath().getParent());
        Files.write(target.toPath(), new byte[] {9});

        assertThrows(IOException.class,
                () -> new DcamMediaFinalizer(storage).finalizeMedia(media));

        assertTrue(media.getFile().isFile());
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(target.toPath()));
    }

    @Test
    void createsSameBasenameMd5ForVideoWhenEnabled() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.writeString(media.getFile().toPath(), "abc");

        java.io.File published = new DcamMediaFinalizer(storage).finalizeMedia(media, true);
        Path sidecar = published.toPath().resolveSibling(
                published.getName().replaceFirst("\\.mp4$", ".md5"));

        assertEquals("900150983cd24fb0d6963f7d28e17f72",
                Files.readString(sidecar).trim());
    }

    @Test
    void md5FailureDoesNotDeleteFinalMovedForLowSpacePublication() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});
        DcamMediaFinalizer finalizer = new DcamMediaFinalizer(
                storage, (staging, target, calculateMd5) -> {
                    Files.createDirectories(target.toPath().getParent());
                    DcamMediaPublisher.move(staging.toPath(), target.toPath());
                    return new DcamMediaPublisher.Publication(target, "digest");
                }, (file, md5) -> {
                    throw new IOException("md5 unavailable");
                });

        assertThrows(IOException.class, () -> finalizer.finalizeMedia(media, true));

        assertTrue(storage.finalFile(media).isFile());
        assertFalse(media.getFile().exists());
    }

    @Test
    void sameTypeSameSecondConflictPreservesBothFiles() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        LocalDateTime firstAt = LocalDateTime.of(2026, 8, 4, 10, 30, 15, 100_000_000);
        LocalDateTime secondAt = LocalDateTime.of(2026, 8, 4, 10, 30, 15, 900_000_000);
        DcamMediaFile first = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001", firstAt, false);
        Files.createDirectories(first.getFile().toPath().getParent());
        Files.write(first.getFile().toPath(), new byte[] {1});
        java.io.File published = new DcamMediaFinalizer(storage).finalizeCleanMedia(first);
        DcamMediaFile second = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001", secondAt, false);
        Files.createDirectories(second.getFile().toPath().getParent());
        Files.write(second.getFile().toPath(), new byte[] {2});

        assertEquals(first.getFileName(), second.getFileName());
        assertThrows(IOException.class,
                () -> new DcamMediaFinalizer(storage).finalizeCleanMedia(second));

        assertArrayEquals(new byte[] {1}, Files.readAllBytes(published.toPath()));
        assertArrayEquals(new byte[] {2}, Files.readAllBytes(second.getFile().toPath()));
    }
    @Test
    void invalidOrEmptyStagingIsNeverPublished() throws Exception {
        DcamStorage storage = new DcamStorage(root.toFile());
        DcamMediaFile media = staged(storage, false);
        Files.createFile(media.getFile().toPath());

        assertThrows(IOException.class,
                () -> new DcamMediaFinalizer(storage).finalizeMedia(media));

        assertTrue(media.getFile().exists());
        assertFalse(storage.finalFile(media).exists());
    }

    private static DcamMediaFile staged(DcamStorage storage, boolean encrypted) throws IOException {
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000001",
                LocalDateTime.of(2026, 7, 11, 10, 30), encrypted);
        Files.createDirectories(media.getFile().toPath().getParent());
        return media;
    }
}
