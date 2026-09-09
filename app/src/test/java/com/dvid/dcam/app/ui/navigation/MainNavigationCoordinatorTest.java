package com.dvid.dcam.app.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import com.dvid.dcam.app.ui.MainMenuModel;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.app.ui.settings.SettingsScreenCatalog;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class MainNavigationCoordinatorTest {
    @Test void validatesAndDelegatesAllUserRouteRequestsThroughSafePolicy() {
        Harness harness = new Harness();
        harness.menuRoutesVisible = false;

        harness.coordinator().navigate(MainScreen.FILES);
        harness.coordinator().navigate(MainScreen.DEVELOPER_USERS);

        assertEquals(List.of(MainScreen.MENU, MainScreen.DEVELOPER_USERS), harness.shown);
    }

    @Test void activeHandlerRunsBeforeExactGraphFallback() {
        Harness harness = new Harness();
        TestOwner owner = new TestOwner();
        owner.start();
        harness.current.set(MainScreen.FILES);
        harness.coordinator().registerBackHandler(MainScreen.FILES, owner,
                () -> MainNavigator.BackResult.HANDLED);

        harness.coordinator.back();
        assertEquals(List.of(), harness.shown);

        owner.destroy();
        harness.coordinator.back();
        assertEquals(List.of(MainScreen.MENU), harness.shown);
    }

    @Test void staleRegistrationCannotRemoveItsReplacement() {
        Harness harness = new Harness();
        TestOwner firstOwner = new TestOwner();
        TestOwner secondOwner = new TestOwner();
        firstOwner.start();
        secondOwner.start();
        harness.current.set(MainScreen.FILES);
        MainNavigator.Registration first = harness.coordinator().registerBackHandler(
                MainScreen.FILES, firstOwner, () -> MainNavigator.BackResult.AT_ROOT);
        harness.coordinator().registerBackHandler(MainScreen.FILES, secondOwner,
                () -> MainNavigator.BackResult.HANDLED);

        first.close();
        harness.coordinator.back();

        assertEquals(List.of(), harness.shown);
    }

    @Test void routeStateDrivesRendererAndStateSaveReconciliation() {
        Harness harness = new Harness();
        MainNavigationCoordinator coordinator = harness.coordinator();

        coordinator.onRouteChanged(MainScreen.ABOUT);
        coordinator.reconcileAfterStateSave();

        assertEquals(List.of(MainScreen.ABOUT), harness.renderer.routes);
        assertEquals(List.of(MainScreen.CAMERA), harness.renderer.reconciliations);
    }

    @Test void reconciliationUsesLatestAuthoritativeRouteInsteadOfPriorDeferredRequest() {
        Harness harness = new Harness();
        MainNavigationCoordinator coordinator = harness.coordinator();
        coordinator.onRouteChanged(MainScreen.FILES);
        harness.current.set(MainScreen.MENU);

        coordinator.reconcileAfterStateSave();

        assertEquals(List.of(MainScreen.MENU), harness.renderer.reconciliations);
    }

    @Test void routeAcceptanceRunsOncePerRequestedTransition() {
        Harness harness = new Harness();
        MainNavigationCoordinator coordinator = harness.coordinator();

        coordinator.onRouteChanged(MainScreen.MENU);
        coordinator.onRouteChanged(MainScreen.MENU);
        coordinator.onRouteChanged(MainScreen.FILES);

        assertEquals(List.of("null->MENU", "MENU->FILES"), harness.accepted);
    }

    @Test void postResumeDoesNotCreateARequestBeforeRouteStateIsAccepted() {
        Harness harness = new Harness();

        harness.coordinator().reconcileAfterStateSave();

        assertEquals(List.of(), harness.renderer.reconciliations);
        assertEquals(List.of(), harness.accepted);
    }

    @Test void destinationReadyLogsCentralizedHumanRouteNames() {
        Harness harness = new Harness();

        harness.coordinator().onDestinationReady(MainScreen.CAMERA, MainScreen.MENU);

        assertEquals(List.of(
                "Displayed main menu screen. Previous screen: camera."), harness.logger.info);
    }

    private static final class Harness {
        final AtomicReference<MainScreen> current = new AtomicReference<>(MainScreen.CAMERA);
        final List<MainScreen> shown = new ArrayList<>();
        final List<String> accepted = new ArrayList<>();
        final FakeRenderer renderer = new FakeRenderer();
        final CapturingLogger logger = new CapturingLogger();
        boolean menuRoutesVisible = true;
        MainNavigationCoordinator coordinator;

        MainNavigationCoordinator coordinator() {
            if (coordinator == null) coordinator = new MainNavigationCoordinator(
                    MainNavigationGraph.create(new SettingsScreenCatalog()), current::get,
                    route -> { shown.add(route); current.set(route); },
                    new MainMenuModel(), (screen, gate) -> menuRoutesVisible, renderer,
                    (previous, current) -> accepted.add(previous + "->" + current), logger);
            return coordinator;
        }
    }

    private static final class FakeRenderer implements MainNavigationCoordinator.Renderer {
        final List<MainScreen> routes = new ArrayList<>();
        final List<MainScreen> reconciliations = new ArrayList<>();
        @Override public void render(MainScreen route) { routes.add(route); }
        @Override public void reconcileAfterStateSave(MainScreen route) {
            reconciliations.add(route);
        }
        @Override public MainScreen committedRoute() {
            return routes.isEmpty() ? null : routes.get(routes.size() - 1);
        }
        @Override public void close() {}
    }

    private static final class TestOwner implements LifecycleOwner {
        private final LifecycleRegistry lifecycle = LifecycleRegistry.createUnsafe(this);
        @Override public Lifecycle getLifecycle() { return lifecycle; }
        void start() { lifecycle.setCurrentState(Lifecycle.State.STARTED); }
        void destroy() { lifecycle.setCurrentState(Lifecycle.State.DESTROYED); }
    }

    private static final class CapturingLogger implements Logger {
        final List<String> info = new ArrayList<>();

        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) { info.add(message); }
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) { info.add(message); }
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }
}
