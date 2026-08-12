package com.dvid.dcam.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SettingsChoicePopupLifecycleTest {
    @Test void choicePopupDismissesWhenAnchorLeavesWindow() throws IOException {
        String renderer = source();

        int lifecycle = renderer.indexOf(
                "View.OnAttachStateChangeListener anchorLifecycle");
        int detached = renderer.indexOf("onViewDetachedFromWindow", lifecycle);
        int dismiss = renderer.indexOf("popup.dismiss();", detached);
        int remove = renderer.indexOf(
                "anchor.removeOnAttachStateChangeListener(anchorLifecycle)", dismiss);
        int show = renderer.indexOf("popup.showAsDropDown(", remove);

        assertTrue(lifecycle >= 0);
        assertTrue(detached > lifecycle);
        assertTrue(dismiss > detached);
        assertTrue(remove > dismiss);
        assertTrue(show > remove);
    }

    private static String source() throws IOException {
        Path root = Files.exists(Path.of("app/src/main/java"))
                ? Path.of("app/src/main/java") : Path.of("src/main/java");
        return Files.readString(root.resolve(
                "com/dvid/dcam/app/ui/settings/SettingsControlRenderer.java"),
                StandardCharsets.UTF_8);
    }
}