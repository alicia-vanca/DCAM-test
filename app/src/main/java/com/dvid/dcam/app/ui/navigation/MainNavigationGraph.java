package com.dvid.dcam.app.ui.navigation;

import android.os.Bundle;
import com.dvid.dcam.app.ui.DeveloperUsersFragment;
import com.dvid.dcam.app.ui.FileExplorerFragment;
import com.dvid.dcam.app.ui.LoginFragment;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.app.ui.MenuFragment;
import com.dvid.dcam.app.ui.SettingsDetailFragment;
import com.dvid.dcam.app.ui.settings.SettingsScreenCatalog;
import java.util.EnumSet;
import java.util.Locale;

/** Complete root app navigation registry. */
public final class MainNavigationGraph {
    private MainNavigationGraph() {}

    public static NavigationGraph<MainScreen> create(SettingsScreenCatalog settings) {
        NavigationGraph.Builder<MainScreen> builder = new NavigationGraph.Builder<>(
                MainScreen.class, EnumSet.allOf(MainScreen.class));
        builder.register(NavigationGraph.RouteSpec.persistent(MainScreen.CAMERA,
                tag(MainScreen.CAMERA), NavigationGraph.BackRule.to(MainScreen.MENU)));
        builder.register(spec(MainScreen.LOGIN, LoginFragment.class,
                NavigationGraph.BackRule.ignore()));
        builder.register(spec(MainScreen.MENU, MenuFragment.class,
                NavigationGraph.BackRule.to(MainScreen.CAMERA)));
        builder.register(spec(MainScreen.FILES, FileExplorerFragment.class,
                NavigationGraph.BackRule.to(MainScreen.MENU)));
        builder.register(spec(MainScreen.DEVELOPER_USERS, DeveloperUsersFragment.class,
                NavigationGraph.BackRule.to(MainScreen.DEVELOPER_SETTINGS)));
        for (MainScreen screen : settings.screens()) {
            MainScreen target = screen == MainScreen.DEVELOPER_BUTTON_BINDINGS
                    ? MainScreen.DEVELOPER_SETTINGS : MainScreen.MENU;
            builder.register(new NavigationGraph.RouteSpec<>(screen,
                    SettingsDetailFragment.class, tag(screen),
                    SettingsDetailFragment::arguments, NavigationGraph.BackRule.to(target)));
        }
        return builder.build();
    }

    private static NavigationGraph.RouteSpec<MainScreen> spec(MainScreen route,
            Class<? extends androidx.fragment.app.Fragment> fragmentClass,
            NavigationGraph.BackRule<MainScreen> backRule) {
        return new NavigationGraph.RouteSpec<>(route, fragmentClass, tag(route),
                ignored -> new Bundle(), backRule);
    }

    private static String tag(MainScreen route) {
        return "main:" + route.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Stable human-readable route name used only in operation logs. */
    public static String logName(MainScreen route) {
        return switch (route) {
            case MENU -> "main menu";
            case RECORD_SETTINGS -> "recording settings";
            case VIDEO_STREAM_SETTINGS -> "video streaming settings";
            case GPS_SETTINGS -> "GPS settings";
            default -> route.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }
}
