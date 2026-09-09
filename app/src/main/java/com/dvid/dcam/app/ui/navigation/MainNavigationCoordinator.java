package com.dvid.dcam.app.ui.navigation;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.dvid.dcam.app.ui.MainMenuModel;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/** Sole command/back coordinator for app-level routes. */
public final class MainNavigationCoordinator implements MainNavigator, AutoCloseable {
    public interface Renderer {
        void render(MainScreen route);
        void reconcileAfterStateSave(MainScreen authoritativeRoute);
        MainScreen committedRoute();
        void close();
    }

    private final NavigationGraph<MainScreen> graph;
    private final Supplier<MainScreen> currentRoute;
    private final Consumer<MainScreen> showRoute;
    private final MainMenuModel menuModel;
    private final BiPredicate<MainScreen, FeatureGate> menuVisibility;
    private final Renderer renderer;
    private final BiConsumer<MainScreen, MainScreen> routeAccepted;
    private final Logger logger;
    private ActiveHandler activeHandler;
    private MainScreen requestedRoute;
    private boolean closed;

    public MainNavigationCoordinator(NavigationGraph<MainScreen> graph,
            Supplier<MainScreen> currentRoute, Consumer<MainScreen> showRoute,
            MainMenuModel menuModel, BiPredicate<MainScreen, FeatureGate> menuVisibility,
            Renderer renderer,
            BiConsumer<MainScreen, MainScreen> routeAccepted, Logger logger) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.currentRoute = Objects.requireNonNull(currentRoute, "currentRoute");
        this.showRoute = Objects.requireNonNull(showRoute, "showRoute");
        this.menuModel = Objects.requireNonNull(menuModel, "menuModel");
        this.menuVisibility = Objects.requireNonNull(menuVisibility, "menuVisibility");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.routeAccepted = Objects.requireNonNull(routeAccepted, "routeAccepted");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override public void navigate(MainScreen screen) {
        requireOpen();
        graph.require(screen);
        MainScreen safe = safeRoute(screen);
        graph.require(safe);
        showRoute.accept(safe);
    }

    public boolean onRouteChanged(MainScreen screen) {
        requireOpen();
        graph.require(screen);
        MainScreen safe = safeRoute(screen);
        if (safe != screen) {
            navigate(safe);
            return false;
        }
        acceptAndRender(screen, false);
        return true;
    }

    public void reconcileAfterStateSave() {
        requireOpen();
        if (requestedRoute == null) return;
        MainScreen route = Objects.requireNonNull(currentRoute.get(), "currentRoute result");
        graph.require(route);
        MainScreen safe = safeRoute(route);
        graph.require(safe);
        if (safe != route) {
            showRoute.accept(safe);
            return;
        }
        acceptAndRender(route, true);
    }

    public void back() {
        requireOpen();
        MainScreen current = renderer.committedRoute();
        if (current == null) current = Objects.requireNonNull(
                currentRoute.get(), "currentRoute result");
        ActiveHandler handler = activeHandler;
        if (handler != null && handler.owner == current
                && handler.lifecycleOwner.getLifecycle().getCurrentState()
                        .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                && handler.handler.onBack() == BackResult.HANDLED) {
            return;
        }
        NavigationGraph.BackRule<MainScreen> rule = graph.require(current).backRule();
        if (rule.action() == NavigationGraph.BackAction.TO_ROUTE) navigate(rule.target());
    }

    public MainScreen committedRoute() { return renderer.committedRoute(); }

    /** Called from the router only after the destination transaction is committed. */
    public void onDestinationReady(MainScreen previous, MainScreen current) {
        requireOpen();
        if (previous != current) {
            logger.info(LogCategory.APP, "unspecified", "Displayed " + MainNavigationGraph.logName(current)
                    + " screen. Previous screen: "
                    + (previous == null ? "none" : MainNavigationGraph.logName(previous)) + ".");
        }
    }

    @Override public Registration registerBackHandler(MainScreen owner,
            LifecycleOwner lifecycleOwner, BackHandler handler) {
        requireOpen();
        graph.require(owner);
        ActiveHandler registration = new ActiveHandler(owner, lifecycleOwner, handler);
        ActiveHandler previous = activeHandler;
        if (previous != null) previous.detach();
        activeHandler = registration;
        lifecycleOwner.getLifecycle().addObserver(registration);
        return registration;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (activeHandler != null) activeHandler.detach();
        activeHandler = null;
        renderer.close();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Main navigation coordinator is closed");
    }

    private void acceptAndRender(MainScreen route, boolean reconciling) {
        if (requestedRoute != route) {
            MainScreen previous = requestedRoute == null ? renderer.committedRoute() : requestedRoute;
            requestedRoute = route;
            routeAccepted.accept(previous, route);
        }
        if (reconciling) renderer.reconcileAfterStateSave(route);
        else renderer.render(route);
    }

    private MainScreen safeRoute(MainScreen screen) {
        return menuModel.safeScreen(screen, menuVisibility::test);
    }

    private final class ActiveHandler implements Registration, DefaultLifecycleObserver {
        private final MainScreen owner;
        private final LifecycleOwner lifecycleOwner;
        private final BackHandler handler;
        private boolean detached;

        private ActiveHandler(MainScreen owner, LifecycleOwner lifecycleOwner,
                BackHandler handler) {
            this.owner = owner;
            this.lifecycleOwner = Objects.requireNonNull(lifecycleOwner, "lifecycleOwner");
            this.handler = Objects.requireNonNull(handler, "handler");
        }

        @Override public void onDestroy(LifecycleOwner owner) { close(); }

        @Override public void close() {
            if (detached) return;
            detach();
            if (activeHandler == this) activeHandler = null;
        }

        private void detach() {
            if (detached) return;
            detached = true;
            lifecycleOwner.getLifecycle().removeObserver(this);
        }
    }
}
