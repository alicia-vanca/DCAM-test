package com.dvid.dcam.platform.logging.loggly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogglyCrashSpoolTest {
    @TempDir Path tempDir;

    @Test
    void enqueueUsesCompleteJsonFileWithoutLeavingTempFile() throws IOException {
        File spool = tempDir.resolve("crash-spool").toFile();
        String payload = "{\"message\":\"crash\"}";

        LogglyCrashSpool.enqueue(spool, payload);

        List<Path> files = jsonFiles(spool);
        assertEquals(1, files.size());
        assertEquals(payload, Files.readString(files.get(0), StandardCharsets.UTF_8));
        try (Stream<Path> entries = Files.list(spool.toPath())) {
            assertFalse(entries.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void failedUploadKeepsSpoolAndSuccessfulRetryDeletesIt() throws IOException {
        File spool = tempDir.resolve("crash-spool").toFile();
        LogglyCrashSpool.enqueue(spool, "{\"message\":\"crash\"}");
        List<String> sent = new ArrayList<>();

        Long retryAt = LogglyCrashSpool.uploadPending(
                spool, () -> false, payload -> {
                    sent.add(payload);
                    return "offline";
                });

        assertNotNull(retryAt);
        assertEquals(1, jsonFiles(spool).size());

        Long complete = LogglyCrashSpool.uploadPending(
                spool, () -> false, payload -> {
                    sent.add(payload);
                    return null;
                });

        assertNull(complete);
        assertEquals(2, sent.size());
        assertTrue(jsonFiles(spool).isEmpty());
    }

    private static List<Path> jsonFiles(File spool) throws IOException {
        if (!spool.isDirectory()) return List.of();
        try (Stream<Path> entries = Files.list(spool.toPath())) {
            return entries.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }
}
