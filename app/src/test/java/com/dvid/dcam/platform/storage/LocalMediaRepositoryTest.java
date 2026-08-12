package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.dvid.dcam.feature.media.domain.MediaEntry;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LocalMediaRepositoryTest {
    @Test void listsOnlyDcamRootsAndMediaInsideThem() throws Exception {
        Path root = Files.createTempDirectory("dcam-media");
        Path video = Files.createDirectories(root.resolve("Media/Video/2026-06-19"));
        Files.write(video.resolve("clip.mp4"), new byte[] {1, 2, 3});
        Files.write(video.resolve("clip.mp4.md5"), new byte[] {1, 2, 3});
        Path secondDate = Files.createDirectories(root.resolve("Media/Video/2026-06-20/nested"));
        Files.write(secondDate.resolve("second.mp4"), new byte[] {1, 2, 3});
        LocalMediaRepository browser = new LocalMediaRepository(new DcamStorage(root.toFile()));

        List<MediaEntry> roots = browser.list("");
        List<MediaEntry> foldersWithoutCounts = browser.listWithoutCounts("Internal");
        List<MediaEntry> datesWithoutCounts = browser.listWithoutCounts("Internal/Video");
        List<MediaEntry> folders = browser.list("Internal");
        List<MediaEntry> dates = browser.list("Internal/Video");
        List<MediaEntry> cachedFolders = browser.listWithoutCounts("Internal");
        List<MediaEntry> cachedDates = browser.listWithoutCounts("Internal/Video");
        List<MediaEntry> files = browser.list("Internal/Video/2026-06-19");

        assertEquals(List.of("Internal"),
                roots.stream().map(MediaEntry::getName).toList());
        assertEquals(List.of("Audio", "Image", "IMP", "Video"),
                folders.stream().map(MediaEntry::getName).toList());
        assertEquals(0, roots.get(0).getChildFileCount());
        assertFalse(foldersWithoutCounts.get(3).hasChildFileCount());
        assertFalse(datesWithoutCounts.get(0).hasChildFileCount());
        assertEquals(2, folders.get(3).getChildFileCount());
        assertEquals(List.of("2026-06-19", "2026-06-20"),
                dates.stream().map(MediaEntry::getName).toList());
        assertEquals(List.of(1, 1), dates.stream().map(MediaEntry::getChildFileCount).toList());
        assertTrue(cachedFolders.get(3).hasChildFileCount());
        assertEquals(2, cachedFolders.get(3).getChildFileCount());
        assertTrue(cachedDates.get(0).hasChildFileCount());
        assertEquals(1, cachedDates.get(0).getChildFileCount());
        assertTrue(cachedDates.get(1).hasChildFileCount());
        assertEquals(1, cachedDates.get(1).getChildFileCount());
        assertEquals(List.of("clip.mp4"), files.stream().map(MediaEntry::getName).toList());
        assertEquals("clip.mp4", files.get(0).getName());
        assertEquals("video/mp4", files.get(0).getMimeType());
    }

    @Test void invalidatesCachedLeafCountWhenDirectoryChanges() throws Exception {
        Path root = Files.createTempDirectory("dcam-media");
        Path date = Files.createDirectories(root.resolve("Media/Video/2026-06-19"));
        Files.write(date.resolve("first.mp4"), new byte[] {1});
        LocalMediaRepository browser = new LocalMediaRepository(new DcamStorage(root.toFile()));

        assertEquals(1, browser.list("Internal/Video").get(0).getChildFileCount());
        MediaEntry cached = browser.listWithoutCounts("Internal/Video").get(0);
        assertTrue(cached.hasChildFileCount());

        long modifiedAtMillis = Files.getLastModifiedTime(date).toMillis();
        Files.write(date.resolve("second.mp4"), new byte[] {2});
        Files.setLastModifiedTime(date, FileTime.fromMillis(modifiedAtMillis + 2_000L));

        assertFalse(browser.listWithoutCounts("Internal/Video").get(0).hasChildFileCount());
        assertEquals(2, browser.list("Internal/Video").get(0).getChildFileCount());
    }

    @Test void invalidatesCachedAggregateWhenNestedDirectoryChanges() throws Exception {
        Path root = Files.createTempDirectory("dcam-media");
        Path nested = Files.createDirectories(root.resolve("Media/Video/2026-06-19/nested"));
        Files.write(nested.resolve("first.mp4"), new byte[] {1});
        LocalMediaRepository browser = new LocalMediaRepository(new DcamStorage(root.toFile()));

        assertEquals(1, browser.list("Internal").get(3).getChildFileCount());
        assertEquals(1, browser.listWithoutCounts("Internal").get(3).getChildFileCount());

        long modifiedAtMillis = Files.getLastModifiedTime(nested).toMillis();
        Files.write(nested.resolve("second.mp4"), new byte[] {2});
        Files.setLastModifiedTime(nested, FileTime.fromMillis(modifiedAtMillis + 2_000L));

        assertFalse(browser.listWithoutCounts("Internal").get(3).hasChildFileCount());
        assertEquals(2, browser.list("Internal").get(3).getChildFileCount());
    }
    @Test void rejectsPathsOutsideMediaRoots() throws Exception {
        Path root = Files.createTempDirectory("dcam-media");
        LocalMediaRepository browser = new LocalMediaRepository(new DcamStorage(root.toFile()));
        assertThrows(SecurityException.class, () -> browser.list("../private"));
        assertThrows(SecurityException.class, () -> browser.list("Internal/Logs"));
        assertThrows(SecurityException.class, () -> browser.list("External nope/Video"));
    }

    @Test void stagedCaptureIsAbsentFromFinalMediaAndMediaBrowsing() throws Exception {
        Path root = Files.createTempDirectory("dcam-media");
        DcamStorage storage = new DcamStorage(MediaPartitionLocation.INTERNAL, root.toFile());
        Path staged = storage.outputFile(DcamFileType.VIDEO, "CAM001", "000000",
                LocalDateTime.of(2026, 6, 19, 10, 3, 24), false).toPath();
        Files.write(staged, new byte[] {1, 2, 3});
        LocalMediaRepository browser = new LocalMediaRepository(storage);

        assertFalse(Files.exists(root.resolve("Media/Video/2026-06-19").resolve(staged.getFileName())));
        assertEquals(List.of(), browser.list("Internal/Video"));
        assertThrows(SecurityException.class, () -> browser.list("Internal/Temp"));
    }
}
