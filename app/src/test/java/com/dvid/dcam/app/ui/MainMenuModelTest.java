package com.dvid.dcam.app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.app.ui.MainScreen;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MainMenuModelTest {
    @Test void visibleTilesUseScreenAvailabilityPredicate() {
        MainMenuModel model = new MainMenuModel();

        List<MainScreen> screens = model.visibleTiles(
                        (screen, gate) -> screen == MainScreen.FILES || screen == MainScreen.ABOUT)
                .stream()
                .map(MainMenuTile::getScreen)
                .toList();

        assertEquals(List.of(MainScreen.FILES, MainScreen.ABOUT), screens);
    }

    @Test void settingsTilesCanStayVisibleWhenTheirDirectGateIsDisabled() {
        MainMenuModel model = new MainMenuModel();

        List<MainScreen> screens = model.visibleTiles(
                        (screen, gate) -> screen == MainScreen.DEVICE_SETTINGS)
                .stream()
                .map(MainMenuTile::getScreen)
                .toList();

        assertEquals(List.of(MainScreen.DEVICE_SETTINGS), screens);
    }

    @Test void routesOutsideMenuWorkNormally() {
        MainMenuModel model = new MainMenuModel();

        assertEquals(MainScreen.CAMERA, model.safeScreen(MainScreen.CAMERA, (screen, gate) -> false));
        assertEquals(MainScreen.DEVELOPER_SETTINGS,
                model.safeScreen(MainScreen.DEVELOPER_SETTINGS, (screen, gate) -> false));
    }

    @Test void unavailableMenuRouteReturnsToMenu() {
        MainMenuModel model = new MainMenuModel();

        assertEquals(MainScreen.MENU,
                model.safeScreen(MainScreen.FILES, (screen, gate) -> false));
    }
}
