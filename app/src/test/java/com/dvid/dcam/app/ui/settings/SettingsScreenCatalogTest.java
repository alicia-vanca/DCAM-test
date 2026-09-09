package com.dvid.dcam.app.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.R;
import com.dvid.dcam.app.ui.MainScreen;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SettingsScreenCatalogTest {
    private final SettingsScreenCatalog catalog = new SettingsScreenCatalog();

    @Test void containsExactlyTheSharedSettingsDetailRoutes() {
        assertEquals(Set.of(
                MainScreen.RECORD_SETTINGS,
                MainScreen.CAMERA_SETTINGS,
                MainScreen.VIDEO_STREAM_SETTINGS,
                MainScreen.AUDIO_SETTINGS,
                MainScreen.STORAGE_SETTINGS,
                MainScreen.GPS_SETTINGS,
                MainScreen.DEVICE_SETTINGS,
                MainScreen.USER_SETTINGS,
                MainScreen.SERVER_SETTINGS,
                MainScreen.TRANSFER_SETTINGS,
                MainScreen.ABOUT,
                MainScreen.DEVELOPER_SETTINGS,
                MainScreen.DEVELOPER_BUTTON_BINDINGS), catalog.screens());
        assertEquals(13, catalog.screens().size());
        assertFalse(catalog.isSettingsScreen(MainScreen.DEVELOPER_USERS));
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.screens().remove(MainScreen.ABOUT));
    }

    @Test void mapsEverySettingsRouteToItsTitleResource() {
        Map<MainScreen, Integer> expected = Map.ofEntries(
                Map.entry(MainScreen.RECORD_SETTINGS, R.string.record_settings),
                Map.entry(MainScreen.CAMERA_SETTINGS, R.string.camera_settings_short),
                Map.entry(MainScreen.VIDEO_STREAM_SETTINGS, R.string.video_stream_settings),
                Map.entry(MainScreen.AUDIO_SETTINGS, R.string.audio_settings),
                Map.entry(MainScreen.STORAGE_SETTINGS, R.string.storage_settings),
                Map.entry(MainScreen.GPS_SETTINGS, R.string.gps_settings),
                Map.entry(MainScreen.DEVICE_SETTINGS, R.string.device_settings_short),
                Map.entry(MainScreen.USER_SETTINGS, R.string.security_settings),
                Map.entry(MainScreen.SERVER_SETTINGS, R.string.network_settings),
                Map.entry(MainScreen.TRANSFER_SETTINGS, R.string.transfer_settings),
                Map.entry(MainScreen.ABOUT, R.string.about),
                Map.entry(MainScreen.DEVELOPER_SETTINGS, R.string.developer_mode),
                Map.entry(MainScreen.DEVELOPER_BUTTON_BINDINGS, R.string.button_role_bindings));

        expected.forEach((screen, resource) ->
                assertEquals(resource.intValue(), catalog.titleResource(screen)));
    }

    @Test void distinguishesResourceListsFromCustomDeveloperModels() {
        Map<MainScreen, Integer> expected = Map.ofEntries(
                Map.entry(MainScreen.RECORD_SETTINGS, R.array.record_settings_items),
                Map.entry(MainScreen.CAMERA_SETTINGS, R.array.camera_settings_items),
                Map.entry(MainScreen.VIDEO_STREAM_SETTINGS, R.array.video_stream_settings_items),
                Map.entry(MainScreen.AUDIO_SETTINGS, R.array.audio_settings_items),
                Map.entry(MainScreen.STORAGE_SETTINGS, R.array.storage_settings_items),
                Map.entry(MainScreen.GPS_SETTINGS, R.array.gps_settings_items),
                Map.entry(MainScreen.DEVICE_SETTINGS, R.array.device_settings_items),
                Map.entry(MainScreen.USER_SETTINGS, R.array.security_settings_items),
                Map.entry(MainScreen.SERVER_SETTINGS, R.array.network_settings_items),
                Map.entry(MainScreen.TRANSFER_SETTINGS, R.array.transfer_settings_items),
                Map.entry(MainScreen.ABOUT, R.array.about_settings_items));

        expected.forEach((screen, resource) -> {
            assertTrue(catalog.itemsResource(screen).isPresent());
            assertEquals(resource.intValue(), catalog.itemsResource(screen).orElseThrow());
        });
        assertTrue(catalog.itemsResource(MainScreen.DEVELOPER_SETTINGS).isEmpty());
        assertTrue(catalog.itemsResource(MainScreen.DEVELOPER_BUTTON_BINDINGS).isEmpty());
    }

    @Test void rejectsResourceAccessForNonSettingsRoutes() {
        IllegalArgumentException titleError = assertThrows(IllegalArgumentException.class,
                () -> catalog.titleResource(MainScreen.DEVELOPER_USERS));
        IllegalArgumentException itemsError = assertThrows(IllegalArgumentException.class,
                () -> catalog.itemsResource(MainScreen.CAMERA));

        assertTrue(titleError.getMessage().contains("DEVELOPER_USERS"));
        assertTrue(itemsError.getMessage().contains("CAMERA"));
    }
}
