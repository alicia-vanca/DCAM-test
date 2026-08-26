package com.dvid.dcam.platform.camera.shared;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dvid.dcam.platform.storage.DcamFileType;
import com.dvid.dcam.platform.storage.DcamMediaFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SharedCameraMediaLifecycleTest {
    @Test void plainPhotoDiscardDeletesExistingOutput(@TempDir Path root) throws IOException {
        Path output = root.resolve("capture.jpg");
        Files.write(output, new byte[] { 1 });

        plainPhoto(output).discard();

        assertFalse(Files.exists(output));
    }

    @Test void plainPhotoDiscardToleratesMissingOutput(@TempDir Path root) {
        assertDoesNotThrow(() -> plainPhoto(root.resolve("missing.jpg")).discard());
    }

    private static SharedCameraMediaLifecycle.PhotoCapture plainPhoto(Path output) {
        DcamMediaFile mediaFile = new DcamMediaFile(
                DcamFileType.IMAGE, output.getFileName().toString(), output.toFile(),
                LocalDateTime.of(2026, 8, 25, 0, 0));
        return new SharedCameraMediaLifecycle.PhotoCapture(mediaFile, false);
    }
}