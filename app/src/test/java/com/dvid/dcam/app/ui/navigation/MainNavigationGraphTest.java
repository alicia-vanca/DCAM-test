package com.dvid.dcam.app.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.app.ui.SettingsDetailFragment;
import com.dvid.dcam.app.ui.settings.SettingsScreenCatalog;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

final class MainNavigationGraphTest {
    @Test void registersEveryAppRouteExactlyOnceWithExactRootBackRules() {
        SettingsScreenCatalog settings = new SettingsScreenCatalog();
        NavigationGraph<MainScreen> graph = MainNavigationGraph.create(settings);

        assertEquals(EnumSet.allOf(MainScreen.class), graph.routes());
        assertEquals(MainScreen.MENU, target(graph, MainScreen.CAMERA));
        assertEquals(MainScreen.CAMERA, target(graph, MainScreen.MENU));
        assertEquals(MainScreen.MENU, target(graph, MainScreen.FILES));
        assertEquals(NavigationGraph.BackAction.IGNORE,
                graph.require(MainScreen.LOGIN).backRule().action());
        assertEquals(MainScreen.DEVELOPER_SETTINGS,
                target(graph, MainScreen.DEVELOPER_USERS));
        assertEquals(MainScreen.DEVELOPER_SETTINGS,
                target(graph, MainScreen.DEVELOPER_BUTTON_BINDINGS));
        for (MainScreen screen : settings.screens()) {
            assertEquals(SettingsDetailFragment.class, graph.require(screen).fragmentClass());
            if (screen != MainScreen.DEVELOPER_BUTTON_BINDINGS) {
                assertEquals(MainScreen.MENU, target(graph, screen));
            }
        }
    }

    @Test void operationLogNamesRemainHumanReadableWithoutActivityMapping() {
        assertEquals("main menu", MainNavigationGraph.logName(MainScreen.MENU));
        assertEquals("recording settings",
                MainNavigationGraph.logName(MainScreen.RECORD_SETTINGS));
        assertEquals("video streaming settings",
                MainNavigationGraph.logName(MainScreen.VIDEO_STREAM_SETTINGS));
        assertEquals("GPS settings", MainNavigationGraph.logName(MainScreen.GPS_SETTINGS));
        assertEquals("developer button bindings",
                MainNavigationGraph.logName(MainScreen.DEVELOPER_BUTTON_BINDINGS));
    }

    private static MainScreen target(NavigationGraph<MainScreen> graph, MainScreen route) {
        return graph.require(route).backRule().target();
    }
}
