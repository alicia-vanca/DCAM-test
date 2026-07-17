package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.dvid.dcam.feature.media.domain.MediaEntry;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LocalMediaRepositoryImplTest {
    @Test void listsOnlyDcamRootsAndMediaInsideThem() throws Exception {
        Path root = Files.createTempDirectory("dcam-media");
        Path video = Files.createDirectories(root.resolve("Media/Video/2026-06-19"));
        Files.write(video.resolve("clip.mp4"), new byte[] {1, 2, 3});
        Files.write(video.resolve("clip.mp4.md5"), new byte[] {1, 2, 3});
        Path secondDate = Files.createDirectories(root.resolve("Media/Video/2026-06-20/nested"));
        Files.write(secondDate.resolve("second.mp4"), new byte[] {1, 2, 3});
        LocalMediaRepositoryImpl browser = new LocalMediaRepositoryImpl(new DcamStorage(root.toFile()));

        List<MediaEntry> roots = browser.list("");
        List<MediaEntry> folders = browser.list("Internal");
        List<MediaEntry> dates = browser.list("Internal/Video");
        List<MediaEntry> files = browser.list("Internal/Video/2026-06-19");

        assertEquals(List.of("Internal"),
                roots.stream().map(MediaEntry::getName).toList());
        assertEquals(List.of("Audio", "Image", "IMP", "Video"),
                folders.stream().map(MediaEntry::getName).toList());
        assertEquals(0, roots.get(0).getChildFileCount());
        assertEquals(2, folders.get(3).getChildFileCount());
        assertEquals(List.of("2026-06-19", "2026-06-20"),
                dates.stream().map(MediaEntry::getName).toList());
        assertEquals(List.of(1, 1), dates.stream().map(MediaEntry::getChildFileCount).toList());
        assertEquals(List.of("clip.mp4"), files.stream().map(MediaEntry::getName).toList());
        assertEquals("clip.mp4", files.get(0).getName());
        assertEquals("video/mp4", files.get(0).getMimeType());
    }

    @Test void rejectsPathsOutsideMediaRoots() throws Exception {
        Path root = Files.createTempDirectory("dcam-media");
        LocalMediaRepositoryImpl browser = new LocalMediaRepositoryImpl(new DcamStorage(root.toFile()));
        assertThrows(SecurityException.class, () -> browser.list("../private"));
        assertThrows(SecurityException.class, () -> browser.list("Internal/Logs"));
        assertThrows(SecurityException.class, () -> browser.list("External nope/Video"));
    }

    @Test void recursiveCountsIgnoreSymbolicLinks() throws Exception {
        Path root = Files.createTempDirectory("dcam-media");
        Path date = Files.createDirectories(root.resolve("Media/Video/2026-06-19"));
        Files.write(date.resolve("clip.mp4"), new byte[] {1});
        try {
            Files.createSymbolicLink(date.resolve("loop"), date);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException error) {
            assumeTrue(false, "Symbolic links unavailable");
        }

        LocalMediaRepositoryImpl browser = new LocalMediaRepositoryImpl(new DcamStorage(root.toFile()));

        assertEquals(1, browser.list("Internal/Video").get(0).getChildFileCount());
    }

    @Test void stagedCaptureIsAbsentFromFinalMediaAndMediaBrowsing() throws Exception {
        Path root = Files.createTempDirectory("dcam-media");
        DcamStorage storage = new DcamStorage(MediaPartitionLocation.INTERNAL, root.toFile());
        Path staged = storage.outputFile(DcamFileType.VIDEO, "CAM001", "000000",
                LocalDateTime.of(2026, 6, 19, 10, 3, 24), false).toPath();
        Files.write(staged, new byte[] {1, 2, 3});
        LocalMediaRepositoryImpl browser = new LocalMediaRepositoryImpl(storage);

        assertFalse(Files.exists(root.resolve("Media/Video/2026-06-19").resolve(staged.getFileName())));
        assertEquals(List.of(), browser.list("Internal/Video"));
        assertThrows(SecurityException.class, () -> browser.list("Internal/Temp"));
    }
}
