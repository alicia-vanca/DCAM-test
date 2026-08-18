package com.dvid.dcam.platform.storage;

import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.os.Environment;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageMode;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DcamStorageTest {
    @TempDir Path root;

    @Test public void stagesVideoImageAndSosOutsideFinalMediaFolders() {
        DcamStorage storage = new DcamStorage(MediaPartitionLocation.INTERNAL, root.resolve("AppData").toFile());
        LocalDateTime at = LocalDateTime.of(2026, 6, 19, 10, 3, 24);

        assertEquals("AppData/Temp/2026-06-19/DCAM_CAM001_000000_20260619_100324.mp4",
                relativePath(storage.outputFile(DcamFileType.VIDEO, "CAM001", "000000", at, false)));
        assertEquals("AppData/Temp/2026-06-19/DCAM_CAM001_000000_20260619_100324.jpg",
                relativePath(storage.outputFile(DcamFileType.IMAGE, "CAM001", "000000", at, false)));
        assertEquals("AppData/Temp/2026-06-19/DCAM_CAM001_000000_20260619_100324_IMP_enc.mp4",
                relativePath(storage.outputFile(DcamFileType.IMP, "CAM001", "000000", at, true)));
    }

    @Test public void stagesAudioBeforeDurablePublication() {
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.INTERNAL, root.resolve("AppData").toFile());
        LocalDateTime at = LocalDateTime.of(2026, 6, 19, 10, 3, 24);
        File aac = storage.outputFile(
                DcamFileType.AUDIO, "CAM001", "000000", at, false);
        File m4a = storage.outputFile(
                DcamFileType.AUDIO_M4A, "CAM001", "000000", at, false);

        assertEquals("AppData/Temp/2026-06-19/DCAM_CAM001_000000_20260619_100324.aac",
                relativePath(aac));
        assertEquals("AppData/Temp/2026-06-19/DCAM_CAM001_000000_20260619_100324.m4a",
                relativePath(m4a));
    }

    @Test public void externalCheckingStateIsPreparingButTerminalStatesAreNot() {
        assertTrue(DcamStorage.isPreparingExternalState(Environment.MEDIA_CHECKING));
        assertFalse(DcamStorage.isPreparingExternalState(Environment.MEDIA_UNMOUNTED));
        assertFalse(DcamStorage.isPreparingExternalState(Environment.MEDIA_UNKNOWN));
        assertFalse(DcamStorage.isPreparingExternalState(Environment.MEDIA_REMOVED));
        assertFalse(DcamStorage.isPreparingExternalState(Environment.MEDIA_BAD_REMOVAL));
        assertFalse(DcamStorage.isPreparingExternalState(Environment.MEDIA_MOUNTED_READ_ONLY));
        assertFalse(DcamStorage.isPreparingExternalState(Environment.MEDIA_SHARED));
    }

    @Test public void unresolvedExternalRootWaitsOnlyForOnePreparingRemovableVolume() {
        assertTrue(DcamStorage.hasSinglePreparingRemovableVolume(1, 1));
        assertFalse(DcamStorage.hasSinglePreparingRemovableVolume(1, 0));
        assertFalse(DcamStorage.hasSinglePreparingRemovableVolume(2, 1));
    }
    @Test public void emptyExternalAppRootWaitsForCheckingRemovableVolume() {
        DcamStorage storage = new DcamStorage(MediaPartitionLocation.EXTERNAL,
                root.resolve("internal").toFile(), List.of(), () -> true);

        var check = storage.checkCaptureReady();

        assertTrue(check.isPreparing());
        assertEquals("SD card is being prepared. Please wait.", check.getReason());
    }

    @Test public void appSpecificFilesRootUsesPrimaryAndroidDataPath() {
        File volume = root.resolve("volume").toFile();

        assertEquals(root.resolve("volume/Android/data/com.dvid.dcam/files").toFile(),
                DcamStorage.appSpecificFilesRoot(volume, "com.dvid.dcam"));
    }

    @Test public void externalRootRefreshRetriesOnlyIncompleteEmptyEnumeration() {
        File internal = root.resolve("internal").toFile();
        File external = root.resolve("external").toFile();
        File[] incomplete = {internal, null};
        List<File> missing = DcamStorage.discoveredExternalRoots(incomplete);

        assertEquals(List.of(), missing);
        assertTrue(DcamStorage.shouldRetryExternalRootRefresh(incomplete, missing));

        File[] complete = {internal, external};
        List<File> discovered = DcamStorage.discoveredExternalRoots(complete);
        assertEquals(List.of(external), discovered);
        assertFalse(DcamStorage.shouldRetryExternalRootRefresh(complete, discovered));

        File[] noExternalSlot = {internal};
        assertFalse(DcamStorage.shouldRetryExternalRootRefresh(
                noExternalSlot, DcamStorage.discoveredExternalRoots(noExternalSlot)));
    }

    @Test public void capacityOnlyFailureRequiresWritableCandidate() {
        CaptureStorageCheck lowCapacity = CaptureStorageCheck.lowCapacity(
                1L, 2L, "Not enough free storage");
        DcamStorageCandidate writable = new DcamStorageCandidate(
                MediaPartitionLocation.EXTERNAL, root.resolve("writable").toFile(),
                true, true, 1L);
        DcamStorageCandidate notWritable = new DcamStorageCandidate(
                MediaPartitionLocation.EXTERNAL, root.resolve("not-writable").toFile(),
                true, false, 1L);

        assertTrue(DcamStorage.isCapacityOnlyFailure(writable, lowCapacity));
        assertFalse(DcamStorage.isCapacityOnlyFailure(notWritable, lowCapacity));
    }

    @Test public void writableCaptureProbeLeavesNoFileBehind() throws Exception {
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.INTERNAL, root.resolve("AppData").toFile());

        var check = storage.checkCaptureWritable();

        assertTrue(check.isReady());
        try (var files = Files.walk(root)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString()
                    .startsWith(".dcam-storage-probe-")));
        }
    }

    @Test public void mediaFileKeepsRequestedWallClockSecond() {
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.INTERNAL, root.resolve("AppData").toFile());
        LocalDateTime at = LocalDateTime.of(2026, 7, 21, 10, 3, 24, 900_000_000);

        DcamMediaFile media = storage.mediaFile(
                DcamFileType.IMAGE, "CAM001", "000000", at, false);

        assertEquals(at, media.getCreatedAt());
        assertEquals("DCAM_CAM001_000000_20260721_100324.jpg", media.getFileName());
    }

    @Test public void findsPublishedFileForInterruptedFinalizationReconciliation() throws Exception {
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.INTERNAL, root.resolve("AppData").toFile());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000000",
                LocalDateTime.of(2026, 7, 21, 10, 3, 24), false);
        File published = storage.finalFile(media);
        Files.createDirectories(published.toPath().getParent());
        Files.write(published.toPath(), new byte[] {1});

        assertTrue(storage.hasPublishedFile(media.getFileName()));
        assertFalse(storage.hasPublishedFile("../" + media.getFileName()));
    }

    @Test public void prepareFileCreatesStagingDirectory() {
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.INTERNAL, root.resolve("AppData").toFile());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000000",
                LocalDateTime.of(2026, 7, 21, 10, 3, 24), false);

        assertFalse(media.getFile().getParentFile().exists());
        assertEquals(media.getFile(), storage.prepareFile(media));
        assertTrue(media.getFile().getParentFile().isDirectory());
    }


    @Test public void keepsPreparedCurrentStagingDirectoryForNextCapture() {
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.INTERNAL, root.resolve("AppData").toFile());
        LocalDateTime now = LocalDateTime.now();

        assertTrue(storage.checkCaptureReady().isReady());
        DcamMediaFile media = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000000", now, false);
        File stagingDirectory = media.getFile().getParentFile();
        assertTrue(stagingDirectory.isDirectory());

        storage.deleteEmptyStagingDateDirectory(media);

        assertTrue(stagingDirectory.isDirectory());
    }

    @Test public void buildsDeviceOnlyConfigPath() {
        File file = new DcamStorage(MediaPartitionLocation.INTERNAL, root.resolve("AppData").toFile()).configsFile();

        assertEquals("AppData/Config/dcam_config.cson", relativePath(file));
    }

    @Test public void buildsIdentityBackupAtRemovableVolumeRoot() {
        File externalAppRoot = root.resolve("sd-card/Android/data/com.dvid.dcam/files").toFile();

        assertEquals(root.resolve("sd-card/DCAM_FACTORY/device_identity.json").toFile(),
                DcamStorage.identityBackupFile(externalAppRoot));
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
                root.resolve("InternalAppData").toFile(),
                root.resolve("ExternalAppData").toFile(),
                new CaptureStorageCapacityPolicy());
        LocalDateTime at = LocalDateTime.of(2026, 6, 19, 10, 3, 24);

        assertEquals("ExternalAppData/Temp/2026-06-19/DCAM_CAM001_000000_20260619_100324.mp4",
                relativePath(storage.outputFile(DcamFileType.VIDEO, "CAM001", "000000", at, false)));
        assertEquals("ExternalAppData/Temp/2026-06-19/DCAM_CAM001_000000_20260619_100324.jpg",
                relativePath(storage.outputFile(DcamFileType.IMAGE, "CAM001", "000000", at, false)));
        assertEquals("ExternalAppData/Temp/2026-06-19/DCAM_CAM001_000000_20260619_100324.aac",
                relativePath(storage.outputFile(DcamFileType.AUDIO, "CAM001", "000000", at, false)));
        assertEquals("InternalAppData/Config/dcam_config.cson", relativePath(storage.configsFile()));
    }


    @Test public void recordingRootStaysPinnedWhenPhotoFallsBackToInternal() {
        File internal = root.resolve("internal").toFile();
        File external = root.resolve("external").toFile();
        assertTrue(internal.mkdirs());
        assertTrue(external.mkdirs());
        long bitrateBitsPerSecond = 12_000_000L;
        CaptureStorageCapacityPolicy policy = new CaptureStorageCapacityPolicy();
        long recordingMinimum = policy.minimumRecordingStartFreeBytes(bitrateBitsPerSecond);
        Map<File, Long> freeBytes = new HashMap<>();
        freeBytes.put(internal, recordingMinimum + 128L * 1024L * 1024L);
        freeBytes.put(external, recordingMinimum + 64L * 1024L * 1024L);
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.AUTO, internal, List.of(external), () -> false,
                ignored -> Environment.MEDIA_MOUNTED,
                file -> freeBytes.getOrDefault(file, 0L));

        assertTrue(storage.checkRecordingReady(bitrateBitsPerSecond).isReady());
        assertEquals(MediaPartitionLocation.EXTERNAL, storage.getMode());

        freeBytes.put(external, CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES);
        assertTrue(storage.checkCaptureReady().isReady());
        assertEquals(MediaPartitionLocation.INTERNAL, storage.getMode());

        var recordingVolume = storage.activeStorageVolume(recordingMinimum, true);
        DcamMediaFile recordingFile = storage.mediaFile(
                DcamFileType.VIDEO, "CAM001", "000000",
                LocalDateTime.of(2026, 8, 13, 8, 0), false);

        assertEquals(MediaPartitionLocation.EXTERNAL, recordingVolume.getMode());
        assertEquals(CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES,
                recordingVolume.getFreeBytes());
        assertTrue(path(recordingFile.getFile()).contains("external/Temp/"));
        assertEquals(1L, storage.recordingFileSizeLimit());
    }

    @Test public void capturePrecheckRejectsBlockedPublicationTree() throws Exception {
        File appData = root.resolve("AppData").toFile();
        Files.createDirectories(appData.toPath());
        Files.write(appData.toPath().resolve("Media"), new byte[] {1});
        DcamStorage storage = new DcamStorage(MediaPartitionLocation.INTERNAL, appData);

        var check = storage.checkCaptureReady();

        assertFalse(check.isReady());
        assertEquals("Storage is not writable", check.getReason());
    }

    @Test public void durableAudioStagesAndPublishesOnSelectedExternal() throws Exception {
        File internal = root.resolve("internal").toFile();
        File external = root.resolve("external").toFile();
        DcamStorage storage = new DcamStorage(
                MediaPartitionLocation.AUTO, MediaPartitionLocation.EXTERNAL, internal, external,
                new CaptureStorageCapacityPolicy());
        DcamMediaFile media = storage.durableAudioMediaFile(
                DcamFileType.AUDIO_M4A, "CAM001", "000000",
                LocalDateTime.of(2026, 7, 14, 16, 22, 7), false);
        assertEquals(DcamFileType.AUDIO_M4A, media.getType());
        assertTrue(media.getFileName().endsWith(".m4a"));
        Files.write(media.getFile().toPath(), new byte[] {1, 2, 3});

        File published = new DcamMediaFinalizer(storage).finalizeMedia(media);

        assertTrue(path(media.getFile()).contains("external/Temp/"));
        assertTrue(path(published).contains("external/Media/Audio/2026-07-14/"));
        assertFalse(new File(media.getFile().getParent()).exists());
        assertTrue(new File(media.getFile().getParentFile().getParent()).isDirectory());
        assertEquals(3L, published.length());
        assertFalse(new File(media.getFile().getParentFile(),
                media.getFileName() + ".target").exists());
    }


    private String relativePath(File file) { return path(root.relativize(file.toPath()).toFile()); }
    private static String path(File file) { return file.getPath().replace('\\', '/'); }
    @Test public void publicationPolicyUsesStorageModeFlag() {
        assertFalse(new DcamStorage(StorageMode.APP_DATA, MediaPartitionLocation.INTERNAL,
                root.resolve("AppData").toFile()).isPublicDcim());
        assertTrue(new DcamStorage(StorageMode.PUBLIC_DCIM, MediaPartitionLocation.INTERNAL,
                root.resolve("AppData").toFile()).isPublicDcim());
    }
}



