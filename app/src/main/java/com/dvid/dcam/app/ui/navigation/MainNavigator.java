package com.dvid.dcam.app.ui.navigation;

import androidx.lifecycle.LifecycleOwner;
import com.dvid.dcam.app.ui.MainScreen;

/** Stable app-route API exposed to all destinations. */
public interface MainNavigator {
    enum BackResult { HANDLED, AT_ROOT }

    interface BackHandler {
        BackResult onBack();
    }

    interface Registration extends AutoCloseable {
        @Override void close();
    }

    void navigate(MainScreen screen);
    Registration registerBackHandler(
            MainScreen owner, LifecycleOwner lifecycleOwner, BackHandler handler);
}
