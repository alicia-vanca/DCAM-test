package com.dvid.dcam.platform.storage;

import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageMode;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DcamStorageTest {
    @Test public void stagesVideoImageAndSosOutsideFinalMediaFolders() {
        DcamStorage storage = new DcamStorage(MediaPartitionLocation.INTERNAL, new File("AppData"));
        LocalDateTime at = LocalDateTime.of(2026, 6, 19, 10, 3, 24);

        assertEquals("AppData/Temp/DCAM_CAM001_000000_20260619_100324.mp4",
                path(storage.outputFile(DcamFileType.VIDEO, "CAM001", "000000", at, false)));
        assertEquals("AppData/Temp/DCAM_CAM001_000000_20260619_100324.jpg",
                path(storage.outputFile(DcamFileType.IMAGE, "CAM001", "000000", at, false)));
        assertEquals("AppData/Temp/DCAM_CAM001_000000_20260619_100324_IMP_enc.mp4",
                path(storage.outputFile(DcamFileType.SOS, "CAM001", "000000", at, true)));
    }

    @Test public void stagesAudioBeforeDurablePublication() {
        File file = new DcamStorage(MediaPartitionLocation.INTERNAL, new File("AppData")).outputFile(
                DcamFileType.AUDIO, "CAM001", "000000",
                LocalDateTime.of(2026, 6, 19, 10, 3, 24), false);

        assertEquals("AppData/Temp/DCAM_CAM001_000000_20260619_100324.aac", path(file));
    }

    @Test public void buildsDeviceOnlyConfigPath() {
        File file = new DcamStorage(MediaPartitionLocation.INTERNAL, new File("AppData")).configsFile();

        assertEquals("AppData/Config/dcam_config.cson", file.getPath().replace('\\', '/'));
    }

    @Test public void defaultsMissingStorageSettingToAutoAndReadsLegacyInternalAlias() {
        assertEquals(MediaPartitionLocation.AUTO, MediaPartitionLocation.from(null));
        assertEquals(MediaPartitionLocation.AUTO, MediaPartitionLocation.from(""));
        assertEquals(MediaPartitionLocation.AUTO, MediaPartitionLocation.from("APP_DATA"));
    }

    @Test public void externalResolutionUsesOneMediaRootAndKeepsConfigInternal() {
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.AUTO,
                MediaPartitionLocation.EXTERNAL,
                new File("InternalAppData"),
                new File("ExternalAppData"),
                new CaptureStorageCapacityPolicy());
        LocalDateTime at = LocalDateTime.of(2026, 6, 19, 10, 3, 24);

        assertEquals("ExternalAppData/Temp/DCAM_CAM001_000000_20260619_100324.mp4",
                path(storage.outputFile(DcamFileType.VIDEO, "CAM001", "000000", at, false)));
        assertEquals("ExternalAppData/Temp/DCAM_CAM001_000000_20260619_100324.jpg",
                path(storage.outputFile(DcamFileType.IMAGE, "CAM001", "000000", at, false)));
        assertEquals("ExternalAppData/Temp/DCAM_CAM001_000000_20260619_100324.aac",
                path(storage.outputFile(DcamFileType.AUDIO, "CAM001", "000000", at, false)));
        assertEquals("InternalAppData/Config/dcam_config.cson", path(storage.configsFile()));
    }

    @Test public void durableAudioStagesAndPublishesOnSelectedExternal(
            @TempDir Path root) throws Exception {
        File internal = root.resolve("internal").toFile();
        File external = root.resolve("external").toFile();
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.AUTO, MediaPartitionLocation.EXTERNAL, internal, external,
                new CaptureStorageCapacityPolicy());
        DcamMediaFile media = storage.durableAudioMediaFile(
                "CAM001", "000000", LocalDateTime.of(2026, 7, 14, 16, 22, 7), false);
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});

        File published = new DcamMediaFinalizer(storage).finalizeMedia(media);

        assertTrue(path(media.getFile()).contains("external/Temp/"));
        assertTrue(path(published).contains("external/Media/Audio/"));
        assertEquals(3L, published.length());
        assertFalse(new File(media.getFile().getParentFile(),
                media.getFileName() + ".target").exists());
    }

    private static String path(File file) { return file.getPath().replace('\\', '/'); }
    @Test public void publicationPolicyUsesStorageModeFlag() {
        assertFalse(new DcamStorage(StorageMode.APP_DATA, MediaPartitionLocation.INTERNAL,
                new File("AppData")).isPublicDcim());
        assertTrue(new DcamStorage(StorageMode.PUBLIC_DCIM, MediaPartitionLocation.INTERNAL,
                new File("AppData")).isPublicDcim());
    }
}



