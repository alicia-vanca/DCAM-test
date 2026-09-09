package com.dvid.dcam.app.ui;

import com.dvid.dcam.app.ui.navigation.MainNavigator;
import com.dvid.dcam.app.ui.settings.SettingsScreenCoordinator;
import com.dvid.dcam.feature.media.domain.MediaEntry;
import java.util.List;
import java.util.Objects;
import androidx.fragment.app.Fragment;

/** Immutable typed capability boundary shared by all root UI destinations. */
public final class MainUiScope {
    public interface Provider {
        MainUiScope mainUiScope();
    }

    public interface DatabaseResetter {
        void resetDatabaseAndRestart();
    }

    public interface MediaOpener {
        boolean openMedia(MediaEntry entry);
    }

    public interface MenuDependencies {
        List<MainMenuTile> visibleTiles();
    }

    public interface CameraActions {
        void onCameraViewAttached(CameraScreenView view);
        void onCameraViewDetached(CameraScreenView view);
        void onCameraSwitchRequested();
    }

    private final MainNavigator navigator;
    private final DatabaseResetter databaseResetter;
    private final MediaOpener mediaOpener;
    private final MenuDependencies menuDependencies;
    private final SettingsScreenCoordinator settings;
    private final CameraActions cameraActions;

    public MainUiScope(MainNavigator navigator, DatabaseResetter databaseResetter,
            MediaOpener mediaOpener, MenuDependencies menuDependencies,
            SettingsScreenCoordinator settings, CameraActions cameraActions) {
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.databaseResetter = Objects.requireNonNull(databaseResetter, "databaseResetter");
        this.mediaOpener = Objects.requireNonNull(mediaOpener, "mediaOpener");
        this.menuDependencies = Objects.requireNonNull(menuDependencies, "menuDependencies");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.cameraActions = Objects.requireNonNull(cameraActions, "cameraActions");
    }

    public MainNavigator navigator() {
        return navigator;
    }

    public DatabaseResetter databaseResetter() { return databaseResetter; }
    public MediaOpener mediaOpener() { return mediaOpener; }
    public MenuDependencies menuDependencies() { return menuDependencies; }
    public SettingsScreenCoordinator settings() { return settings; }
    public CameraActions cameraActions() { return cameraActions; }

    public static MainUiScope require(Fragment fragment) {
        Object host = fragment.requireActivity();
        if (!(host instanceof Provider)) {
            throw new IllegalStateException(host.getClass().getName()
                    + " must implement MainUiScope.Provider");
        }
        return ((Provider) host).mainUiScope();
    }
}
