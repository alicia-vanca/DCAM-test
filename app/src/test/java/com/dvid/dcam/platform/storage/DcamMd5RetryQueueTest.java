package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.application.port.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DcamMd5RetryQueueTest {
    private static final Logger LOGGER = new Logger() {
        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void info(String message, Throwable error) {}
        @Override public void warn(String message, Throwable error) {}
        @Override public void error(String message, Throwable error) {}
    };

    @TempDir Path root;

    @Test
    void persistsDeduplicatesAndRemovesEntries() throws Exception {
        DcamMd5RetryQueue queue = new DcamMd5RetryQueue(root.toFile());
        Path media = root.resolve("Media/video.mp4");
        Files.createDirectories(media.getParent());
        Files.writeString(media, "video");
        queue.add(media.toFile());
        queue.add(media.toFile());
        assertEquals(1, new DcamMd5RetryQueue(root.toFile()).pending().size());
        queue.remove(media.toFile());
        assertTrue(new DcamMd5RetryQueue(root.toFile()).pending().isEmpty());
    }

    @Test
    void processorCreatesMd5AndRemovesSuccessfulOrMissingEntries() throws Exception {
        DcamMd5RetryQueue queue = new DcamMd5RetryQueue(root.toFile());
        Path media = root.resolve("Media/video.mp4");
        Path missing = root.resolve("Media/missing.mp4");
        Files.createDirectories(media.getParent());
        Files.writeString(media, "abc");
        queue.add(media.toFile());
        queue.add(missing.toFile());

        assertTrue(DcamMd5RetryProcessor.process(queue, LOGGER));

        assertEquals("900150983cd24fb0d6963f7d28e17f72",
                Files.readString(root.resolve("Media/video.md5")).trim());
        assertTrue(queue.pending().isEmpty());
    }

    @Test
    void processorAcceptsExistingMatchingSidecarAfterCrashWindow() throws Exception {
        DcamMd5RetryQueue queue = new DcamMd5RetryQueue(root.toFile());
        Path media = root.resolve("Media/video.mp4");
        Files.createDirectories(media.getParent());
        Files.writeString(media, "abc");
        Files.writeString(root.resolve("Media/video.md5"), "900150983cd24fb0d6963f7d28e17f72\n");
        queue.add(media.toFile());

        assertTrue(DcamMd5RetryProcessor.process(queue, LOGGER));

        assertTrue(queue.pending().isEmpty());
    }

    @Test
    void processorKeepsEntryWhenStorageParentIsUnavailable() throws Exception {
        DcamMd5RetryQueue queue = new DcamMd5RetryQueue(root.toFile());
        Path media = root.resolve("unmounted/Media/video.mp4");
        queue.add(media.toFile());

        assertFalse(DcamMd5RetryProcessor.process(queue, LOGGER));

        assertEquals(1, queue.pending().size());
    }

    @Test
    void processorReportsFailureAndKeepsEntryForWorkerRetry() throws Exception {
        DcamMd5RetryQueue queue = new DcamMd5RetryQueue(root.toFile());
        Path media = root.resolve("Media/video.mp4");
        Path blockedSidecar = root.resolve("Media/video.md5");
        Files.createDirectories(blockedSidecar);
        Files.writeString(blockedSidecar.resolve("occupied"), "blocked");
        Files.writeString(media, "abc");
        queue.add(media.toFile());

        assertFalse(DcamMd5RetryProcessor.process(queue, LOGGER));

        assertEquals(1, queue.pending().size());
    }
}
