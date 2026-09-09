package com.dvid.dcam.app.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.app.ui.settings.SettingsScreenCatalog;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MainScreenRouterTest {
    @Test void stateSavedDefersCameraCreationAndCommitsOnlyLatestAuthoritativeRoute() {
        Harness harness = new Harness();
        harness.foreground.stateSaved = true;

        harness.router.render(MainScreen.FILES);
        harness.router.render(MainScreen.MENU);

        assertEquals(0, harness.camera.ensureCalls);
        assertEquals(List.of(), harness.ready);

        harness.foreground.stateSaved = false;
        harness.router.reconcileAfterStateSave(MainScreen.MENU);

        assertEquals(1, harness.camera.ensureCalls);
        assertEquals(List.of(MainScreen.MENU), harness.ready);
        assertEquals(MainScreen.MENU, harness.router.committedRoute());
    }

    @Test void cameraRouteClearsForegroundWithoutRemovingPersistentCameraLayer() {
        Harness harness = new Harness();
        harness.router.render(MainScreen.MENU);

        harness.router.render(MainScreen.CAMERA);

        assertEquals(2, harness.camera.ensureCalls);
        assertEquals(1, harness.camera.showCalls);
        assertEquals(1, harness.camera.coverCalls);
        assertEquals(List.of(MainScreen.MENU, MainScreen.CAMERA), harness.ready);
    }

    @Test void idempotentRenderDoesNotDuplicateReadiness() {
        Harness harness = new Harness();

        harness.router.render(MainScreen.MENU);
        harness.router.render(MainScreen.MENU);

        assertEquals(List.of(MainScreen.MENU), harness.ready);
    }

    private static final class Harness {
        final FakeForeground foreground = new FakeForeground();
        final FakeCameraLayer camera = new FakeCameraLayer();
        final List<MainScreen> ready = new ArrayList<>();
        final MainScreenRouter router = new MainScreenRouter(
                MainNavigationGraph.create(new SettingsScreenCatalog()),
                foreground, camera, (previous, current) -> ready.add(current));
    }

    private static final class FakeForeground implements RouteRenderer<MainScreen> {
        boolean stateSaved;
        MainScreen committed;

        @Override public RouteRenderResult render(
                NavigationGraph.RouteSpec<MainScreen> spec) {
            if (stateSaved) return RouteRenderResult.DEFERRED;
            if (committed == spec.route()) return RouteRenderResult.UNCHANGED;
            committed = spec.route();
            return RouteRenderResult.COMMITTED;
        }

        @Override public RouteRenderResult clear() {
            if (stateSaved) return RouteRenderResult.DEFERRED;
            if (committed == null) return RouteRenderResult.UNCHANGED;
            committed = null;
            return RouteRenderResult.COMMITTED;
        }

        @Override public boolean isStateSaved() { return stateSaved; }
        @Override public void close() {}
    }

    private static final class FakeCameraLayer implements MainScreenRouter.CameraLayer {
        int ensureCalls;
        int showCalls;
        int coverCalls;

        @Override public boolean ensureCamera() {
            ensureCalls++;
            return true;
        }

        @Override public void showCamera() { showCalls++; }
        @Override public void coverCamera() { coverCalls++; }
    }
}
