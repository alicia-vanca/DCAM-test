package com.dvid.dcam.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class OperationLoggingSourceTest {

    @Test void navigationLogRunsAfterScreenRendering() throws IOException {
        String render = method(source(), "private void render(MainUiState state)",
                "private void updateSavingNotice");

        int renderComplete = render.indexOf("renderSettingsDetail(screen);");
        int operationLog = render.indexOf("logScreenNavigation(operationLoggedScreen, screen);");
        assertTrue(renderComplete >= 0);
        assertTrue(operationLog > renderComplete);
        assertTrue(render.contains("if (operationLoggedScreen != screen)"));
        assertTrue(render.contains("operationLoggedScreen = screen;"));
    }

    @Test void settingLogsRunOnlyAfterAcceptedChanges() throws IOException {
        String activity = source();
        String cameraSetting = method(activity, "private void selectCameraSetting(",
                "private boolean canSelectDeveloperSetting");
        String pipelineSetting = method(activity, "private void selectCameraPipelineMode(",
                "static int cameraPipelineNotice");
        String booleanSetting = method(activity, "private void updateBooleanSetting(",
                "private void applyAutoRotate");

        assertTrue(cameraSetting.indexOf("return;")
                < cameraSetting.indexOf("logSelectedSettingChanged(item, stableId, selectedIndex);"));
        assertTrue(pipelineSetting.indexOf("result == CameraPipelineModeController.Result.APPLIED")
                < pipelineSetting.indexOf("logSelectedSettingChanged(item, stableId, selectedIndex);"));
        assertTrue(booleanSetting.contains("if (handleLocationSwitchChanged(checked))"));
        assertTrue(booleanSetting.contains("if (!androidRuntime.setWifiEnabled(checked))"));
        assertTrue(booleanSetting.contains("logBooleanSettingChanged(item, id, checked);"));
    }

    @Test void operationMessagesUseHumanLanguage() throws IOException {
        String activity = source();

        assertTrue(activity.contains("logger.info(\"Displayed \" + screenName(current)"));
        assertTrue(activity.contains("logger.info(\"Changed setting \" + name"));
        assertTrue(activity.contains("Started camera capability check from developer settings."));
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
        Path root = Files.exists(Path.of("app/src/main/java"))
                ? Path.of("app/src/main/java") : Path.of("src/main/java");
        return Files.readString(root.resolve("com/dvid/dcam/app/MainActivity.java"));
    }
}