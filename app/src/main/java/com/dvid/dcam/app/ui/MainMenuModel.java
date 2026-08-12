package com.dvid.dcam.app.ui;

import com.dvid.dcam.R;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Normal app navigation catalog.
 *
 * <p>A menu entry is enabled by default. It references a developer gate only when that screen
 * explicitly opts in to runtime disablement.
 */
public final class MainMenuModel {
    private static final List<MainMenuTile> TILES = List.of(
            gatedTile(R.id.files, MainScreen.FILES, FeatureGate.MEDIA_BROWSER),
            gatedTile(R.id.record_settings, MainScreen.RECORD_SETTINGS,
                    FeatureGate.VIDEO_CAPTURE),
            gatedTile(R.id.camera_settings, MainScreen.CAMERA_SETTINGS,
                    FeatureGate.IMAGE_CAPTURE),
            gatedTile(R.id.video_stream_settings, MainScreen.VIDEO_STREAM_SETTINGS,
                    FeatureGate.VIDEO_STREAMING),
            gatedTile(R.id.audio_settings, MainScreen.AUDIO_SETTINGS, FeatureGate.AUDIO_CAPTURE),
            gatedTile(R.id.storage_settings, MainScreen.STORAGE_SETTINGS,
                    FeatureGate.STORAGE_SETTINGS),
            gatedTile(R.id.gps_settings, MainScreen.GPS_SETTINGS, FeatureGate.GPS),
            gatedTile(R.id.device_settings, MainScreen.DEVICE_SETTINGS,
                    FeatureGate.DEVICE_SETTINGS),
            gatedTile(R.id.user_settings, MainScreen.USER_SETTINGS,
                    FeatureGate.SECURITY_SETTINGS),
            gatedTile(R.id.server_settings, MainScreen.SERVER_SETTINGS,
                    FeatureGate.CLOUD_SETTINGS),
            gatedTile(R.id.transfer_settings, MainScreen.TRANSFER_SETTINGS, FeatureGate.TRANSFER),
            tile(R.id.about, MainScreen.ABOUT));

    public List<MainMenuTile> visibleTiles(BiPredicate<MainScreen, FeatureGate> isVisible) {
        List<MainMenuTile> visible = new ArrayList<>();
        for (MainMenuTile tile : TILES) {
            if (isVisible.test(tile.getScreen(), tile.getOptionalDeveloperGate())) visible.add(tile);
        }
        return List.copyOf(visible);
    }

    public MainScreen safeScreen(MainScreen requested, BiPredicate<MainScreen, FeatureGate> isVisible) {
        for (MainMenuTile tile : TILES) {
            if (tile.getScreen() == requested) {
                return isVisible.test(requested, tile.getOptionalDeveloperGate())
                        ? requested : MainScreen.MENU;
            }
        }
        // Routes not opted in to developer disablement work normally.
        return requested;
    }

    /** Default for normal features: no developer-disable behavior. */
    private static MainMenuTile tile(int viewId, MainScreen screen) {
        return new MainMenuTile(viewId, screen, null);
    }

    private static MainMenuTile gatedTile(
            int viewId, MainScreen screen, FeatureGate optionalDeveloperGate) {
        return new MainMenuTile(viewId, screen, optionalDeveloperGate);
    }
}
