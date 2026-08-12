package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class DcamMediaPublisherTest {
    @Test void publicationMoveRequestsAtomicRename()
            throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        CopyOption[][] observed = new CopyOption[1][];

        DcamMediaPublisher.move((source, target, options) -> {
            attempts.incrementAndGet();
            observed[0] = options;
            return target;
        }, Path.of("staging.mp4"), Path.of("final.mp4"));

        assertEquals(1, attempts.get());
        assertEquals(1, observed[0].length);
        assertEquals(StandardCopyOption.ATOMIC_MOVE, observed[0][0]);
    }

    @Test void cleanPublicationDoesNotProbeUnsupportedFileStore() throws IOException {
        Path sourcePath = Path.of(
                "app/src/main/java/com/dvid/dcam/platform/storage/DcamMediaPublisher.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of(
                    "src/main/java/com/dvid/dcam/platform/storage/DcamMediaPublisher.java");
        }
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int cleanStart = source.indexOf("File publishClean(File staging, File target)");
        int cleanEnd = source.indexOf("Publication publish(", cleanStart);

        assertTrue(cleanStart >= 0 && cleanEnd > cleanStart);
        String cleanPublication = source.substring(cleanStart, cleanEnd);
        assertFalse(cleanPublication.contains("sameFileStore"));
        assertFalse(cleanPublication.contains("validate(target"));
    }
}