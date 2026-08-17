package com.dvid.dcam.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class StorageUnavailableFlowSourceTest {
    @Test void storageChecksStayOffUiThreadAndCoverAllCaptureCommands() throws IOException {
        String activity = source("app/MainActivity.java");
        String compositionSource = source("app/AppComposition.java");
        String composition = compositionSource.replaceAll("\\s+", " ");
        String storage = source("platform/storage/DcamStorage.java");
        String warningRead = section(compositionSource,
                "public void readStorageWarning",
                "private CaptureStorageNoticeMonitor.Status captureStorageNoticeStatus");
        String minimumRefresh = section(compositionSource,
                "static boolean recordingMinimumRefreshRequired",
                "private void completeCameraTransition");
        String warning = section(activity,
                "private void updateStorageWarning()",
                "private String gpsCoordinatesText()");
        String storageOptions = section(activity,
                "private List<StorageOptionUiState> storageOptions()",
                "private void requestDefaultHomeIfNeeded()");
        String storageReceiver = section(activity,
                "private final BroadcastReceiver storageMountedReceiver",
                "private final BroadcastReceiver screenStateReceiver");
        String storageCreation = section(storage,
                "public static DcamStorage from(Context context, StorageMode storageMode",
                "public MediaPartitionLocation getRequestedMode()");
        String configPath = section(storage,
                "public File configsFile()",
                "public synchronized List<File> identityBackupFiles()");
        String rootRefresh = section(storage,
                "private synchronized void refreshExternalRoots()",
                "private DcamStorageCandidate candidate");

        assertTrue(activity.contains("Intent.ACTION_MEDIA_SHARED"));
        assertTrue(activity.contains("composition.refreshCaptureStorageNotice();"));
        assertTrue(warning.contains("composition.readStorageWarning"));
        assertFalse(warning.contains("storageSettings.warningStatus()"));
        assertTrue(storageOptions.contains("refreshStorageVolumes();"));
        assertFalse(storageOptions.contains("storageSettings.storageVolumes()"));
        assertTrue(storageReceiver.contains("viewModel.openMediaFolder("));
        assertTrue(storageReceiver.contains("getMediaBrowser().getRelativePath()"));
        assertTrue(composition.contains("volumes = storageSettings.storageVolumes();"));
        assertTrue(warningRead.contains("cachedMinimumRecordingStartFreeBytes"));
        assertFalse(warningRead.contains("recordingBitrateBitsPerSecond()"));
        assertFalse(warningRead.contains("mediaOutput.minimumRecordingStartFreeBytes("));
        assertTrue(compositionSource.contains("recordingCamera.observeRuntimeState("));
        assertTrue(compositionSource.contains(
                "this::refreshCachedMinimumRecordingStartFreeBytes"));
        assertTrue(minimumRefresh.contains("snapshot.state() == CameraRuntimeState.READY"));
        assertTrue(minimumRefresh.contains(
                "snapshot.transitionGeneration() != lastGeneration"));
        assertTrue(minimumRefresh.contains("recordingBitrateBitsPerSecond()"));
        assertTrue(minimumRefresh.contains("mediaOutput.minimumRecordingStartFreeBytes("));
        assertTrue(composition.contains("captureStorageNoticeMonitor.invalidate();"));
        assertTrue(composition.contains(
                "captureIoExecutor.execute(captureStorageNoticeMonitor::evaluate);"));
        assertTrue(composition.contains("&& !captureStorageUnavailable(), recordingCoordinator"));
        assertTrue(composition.contains("&& !captureStorageUnavailable();"));
        assertTrue(composition.contains("|| captureStorageUnavailable();"));
        assertTrue(composition.contains("captureStorageNoticeMonitor::pause"));
        assertTrue(composition.contains("storage.refreshExternalRootsAfterMount();"));
        assertFalse(storageCreation.contains("getExternalFilesDirs("));
        assertFalse(configPath.contains("getExternalFilesDirs("));
        assertTrue(rootRefresh.contains(
                "Looper.myLooper() == Looper.getMainLooper()"));
        assertTrue(rootRefresh.contains("shouldRetryExternalRootRefresh"));
        assertFalse(storage.contains("registerReceiver("));
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) throw new IllegalStateException("Source section missing");
        return source.substring(from, to);
    }

    private static String source(String suffix) throws IOException {
        try (var paths = Files.walk(sourceRoot())) {
            Path path = paths.filter(value -> value.toString().replace('\\', '/').endsWith(suffix))
                    .findFirst().orElseThrow();
            return Files.readString(path);
        }
    }

    private static Path sourceRoot() {
        Path app = Path.of("app/src/main/java");
        return Files.exists(app) ? app : Path.of("src/main/java");
    }
}
