package com.dvid.dcam.platform.database;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class DcamDatabaseExportManifestTest {
    @Test
    void keepsExportAsProtectedExplicitReceiverWithoutSettingsAction() throws IOException {
        String manifest = Files.readString(path("src/main/AndroidManifest.xml"),
                StandardCharsets.UTF_8);
        assertTrue(manifest.contains(
                "android:name=\".platform.database.DcamDatabaseExportReceiver\""));
        assertTrue(manifest.contains("android:permission=\"android.permission.DUMP\""));

        String settingsSource = Files.readString(
                path("src/main/java/com/dvid/dcam/app/ui/settings/SettingId.java"),
                StandardCharsets.UTF_8)
                + Files.readString(
                path("src/main/java/com/dvid/dcam/app/ui/settings/SettingsScreenCoordinator.java"),
                StandardCharsets.UTF_8);
        assertFalse(settingsSource.contains("EXPORT_DATABASE"));
    }

    private static Path path(String moduleRelativePath) {
        Path modulePath = Path.of(moduleRelativePath);
        return Files.isRegularFile(modulePath)
                ? modulePath
                : Path.of("app").resolve(moduleRelativePath);
    }
}
