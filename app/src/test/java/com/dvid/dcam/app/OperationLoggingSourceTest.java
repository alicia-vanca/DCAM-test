package com.dvid.dcam.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class OperationLoggingSourceTest {

    @Test void navigationLogRunsAfterScreenRendering() throws IOException {
        String render = method(source(),
                "private void onDestinationReady(MainScreen previous, MainScreen screen)",
                "private void vibrateCaptureCommandStart");
        String router = source("com/dvid/dcam/app/ui/navigation/MainScreenRouter.java");
        int commitStart = router.indexOf("private void commitCameraLayer(MainScreen route)");

        int renderComplete = router.indexOf("committedRoute = route;", commitStart);
        int operationLog = router.indexOf(
                "readyListener.onReady(previous, route);", commitStart);
        assertTrue(renderComplete >= 0);
        assertTrue(operationLog > renderComplete);
        assertTrue(render.contains(
                "if (navigation != null) navigation.onDestinationReady(previous, screen);"));
    }

    @Test void settingLogsRunOnlyAfterAcceptedChanges() throws IOException {
        String coordinator = source(
                "com/dvid/dcam/app/ui/settings/SettingsScreenCoordinator.java");
        String cameraSetting = method(coordinator, "public void selectCameraSetting(",
                "public boolean canSelectDeveloperSetting");
        String pipelineSetting = method(coordinator, "private void selectCameraPipelineMode(",
                "static int cameraPipelineNotice");
        String booleanSetting = method(coordinator, "public void updateBooleanSetting(",
                "public void performSettingAction");
        String formatSetting = method(coordinator, "private void selectSetting(SettingId",
                "private boolean handleLocationSwitchChanged");
        String audioSettings = method(coordinator, "private SettingsScreenModel settingsModel(",
                "private SettingsScreenModel developerSettingsModel");
        String visibility = method(coordinator, "private boolean isSettingVisible(",
                "private SettingsScreenModel withLanguage");
        String alertVolume = method(coordinator, "private String alertVolumeLabel()",
                "private String[] visibleReadOnlySettings");

        assertTrue(cameraSetting.indexOf("return;")
                < cameraSetting.indexOf("logSelectedSettingChanged(item, stableId, selectedIndex);"));
        assertTrue(pipelineSetting.indexOf(
                "result == SettingsRuntime.CameraPipelineModeResult.APPLIED")
                < pipelineSetting.indexOf("logSelectedSettingChanged(item, stableId, selectedIndex);"));
        assertTrue(booleanSetting.contains("if (handleLocationSwitchChanged(checked))"));
        assertTrue(booleanSetting.contains("if (!platform.setWifiEnabled(checked))"));
        assertTrue(booleanSetting.contains("if (id == SettingId.FULL_SCREEN_DISPLAY)"));
        assertTrue(booleanSetting.contains(
                "platform.setFullScreenDisplayEnabled(checked);"));
        assertTrue(formatSetting.contains("if (id == SettingId.AUDIO_FILE_FORMAT)"));
        assertTrue(formatSetting.contains("runtime.setAudioFileFormat(formats[selectedIndex]);"));
        assertTrue(audioSettings.contains("screen == MainScreen.AUDIO_SETTINGS"));
        assertTrue(audioSettings.contains("runtime.audioFileFormat()"));
        assertTrue(audioSettings.contains("AudioCaptureSettings.SAMPLE_RATE_HZ"));
        assertTrue(audioSettings.contains("AudioCaptureSettings.BIT_RATE_BPS"));
        assertTrue(audioSettings.contains("AudioCaptureSettings.CHANNEL_COUNT"));
        assertTrue(audioSettings.contains("alertVolumeLabel()"));
        assertTrue(formatSetting.contains("id == SettingId.AUDIO_ALERT_VOLUME"));
        assertTrue(alertVolume.contains("AudioManager.STREAM_ALARM"));
        assertTrue(visibility.contains("item.getId() == SettingId.FULL_SCREEN_DISPLAY"));
        assertTrue(visibility.contains("!platform.isDeviceOwner()"));
        assertTrue(booleanSetting.contains("logBooleanSettingChanged(item, id, checked);"));
    }

    @Test void operationMessagesUseHumanLanguage() throws IOException {
        String activity = source();
        String coordinator = source(
                "com/dvid/dcam/app/ui/settings/SettingsScreenCoordinator.java");

        assertTrue(activity.contains("navigation.onDestinationReady(previous, screen);"));
        assertTrue(coordinator.contains(
                "logger.info(LogCategory.CONFIG, \"unspecified\", \"Changed setting \" + name"));
        assertTrue(coordinator.contains(
                "Started camera capability check from developer settings."));
        assertTrue(activity.contains("Completed camera capability check successfully."));
        assertFalse(activity.contains("camera_capability_recheck request"));
        assertFalse(activity.contains("camera_capability_recheck outcome=failed"));
    }

    private static String method(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0);
        assertTrue(endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }

    private static String source() throws IOException {
        return source("com/dvid/dcam/app/MainActivity.java");
    }

    private static String source(String relative) throws IOException {
        Path root = Files.exists(Path.of("app/src/main/java"))
                ? Path.of("app/src/main/java") : Path.of("src/main/java");
        return Files.readString(root.resolve(relative));
    }
}
