package com.dvid.dcam.app.ui.navigation;

import com.dvid.dcam.app.ui.MainScreen;
import java.util.Objects;

/** Composes the persistent camera layer with the replaceable foreground renderer. */
public final class MainScreenRouter implements MainNavigationCoordinator.Renderer {
    public interface ReadyListener {
        void onReady(MainScreen previous, MainScreen current);
    }

    public interface CameraLayer {
        boolean ensureCamera();
        void showCamera();
        void coverCamera();
    }

    private final NavigationGraph<MainScreen> graph;
    private final RouteRenderer<MainScreen> foreground;
    private final CameraLayer cameraLayer;
    private final ReadyListener readyListener;
    private MainScreen committedRoute;
    private boolean closed;

    public MainScreenRouter(NavigationGraph<MainScreen> graph,
            RouteRenderer<MainScreen> foreground, CameraLayer cameraLayer,
            ReadyListener readyListener) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.foreground = Objects.requireNonNull(foreground, "foreground");
        this.cameraLayer = Objects.requireNonNull(cameraLayer, "cameraLayer");
        this.readyListener = Objects.requireNonNull(readyListener, "readyListener");
    }

    @Override public void render(MainScreen route) {
        requireOpen();
        NavigationGraph.RouteSpec<MainScreen> destination = graph.require(route);
        if (foreground.isStateSaved()) {
            requestForeground(destination);
            return;
        }
        if (!cameraLayer.ensureCamera()) return;
        RouteRenderResult result = requestForeground(destination);
        if (result == RouteRenderResult.COMMITTED
                || result == RouteRenderResult.UNCHANGED && committedRoute != route) {
            commitCameraLayer(route);
        }
    }

    @Override public void reconcileAfterStateSave(MainScreen authoritativeRoute) {
        requireOpen();
        render(authoritativeRoute);
    }

    @Override public MainScreen committedRoute() { return committedRoute; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        foreground.close();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Main screen router is closed");
    }

    private RouteRenderResult requestForeground(
            NavigationGraph.RouteSpec<MainScreen> destination) {
        return destination.kind() == NavigationGraph.DestinationKind.PERSISTENT_LAYER
                ? foreground.clear() : foreground.render(destination);
    }

    private void commitCameraLayer(MainScreen route) {
        MainScreen previous = committedRoute;
        if (graph.require(route).kind() == NavigationGraph.DestinationKind.PERSISTENT_LAYER) {
            cameraLayer.showCamera();
        } else {
            cameraLayer.coverCamera();
        }
        committedRoute = route;
        readyListener.onReady(previous, route);
    }
}
