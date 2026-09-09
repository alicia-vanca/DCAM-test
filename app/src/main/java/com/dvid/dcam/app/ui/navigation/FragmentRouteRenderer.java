package com.dvid.dcam.app.ui.navigation;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import java.util.Objects;

enum RouteRenderResult { COMMITTED, UNCHANGED, DEFERRED }

interface RouteRenderer<R extends Enum<R>> extends AutoCloseable {
    RouteRenderResult render(NavigationGraph.RouteSpec<R> spec);
    RouteRenderResult clear();
    boolean isStateSaved();
    @Override void close();
}

/** Idempotent, no-back-stack renderer for one Fragment container and typed graph. */
public final class FragmentRouteRenderer<R extends Enum<R>> implements RouteRenderer<R> {
    private final FragmentManager fragments;
    private final int containerId;
    private R requested;
    private R committed;
    private boolean closed;

    public FragmentRouteRenderer(FragmentManager fragments, int containerId) {
        this.fragments = Objects.requireNonNull(fragments, "fragments");
        this.containerId = containerId;
    }

    @Override public RouteRenderResult render(NavigationGraph.RouteSpec<R> spec) {
        requireOpen();
        requested = spec.route();
        if (fragments.isStateSaved()) return RouteRenderResult.DEFERRED;
        Fragment existing = fragments.findFragmentByTag(spec.tag());
        if (requested == committed && existing != null) {
            return RouteRenderResult.UNCHANGED;
        }
        if (existing != null && fragments.findFragmentById(containerId) == existing) {
            committed = requested;
            return RouteRenderResult.COMMITTED;
        }
        commit(spec);
        return RouteRenderResult.COMMITTED;
    }

    @Override public RouteRenderResult clear() {
        requireOpen();
        requested = null;
        if (fragments.isStateSaved()) return RouteRenderResult.DEFERRED;
        boolean changed = committed != null || fragments.findFragmentById(containerId) != null;
        commitClear();
        return changed ? RouteRenderResult.COMMITTED : RouteRenderResult.UNCHANGED;
    }

    @Override public boolean isStateSaved() { return fragments.isStateSaved(); }

    @Override public void close() {
        closed = true;
        requested = null;
        committed = null;
    }

    private void commit(NavigationGraph.RouteSpec<R> spec) {
        Fragment destination = fragments.findFragmentByTag(spec.tag());
        if (destination == null) {
            destination = fragments.getFragmentFactory().instantiate(
                    spec.fragmentClass().getClassLoader(), spec.fragmentClass().getName());
            destination.setArguments(spec.arguments());
        }
        fragments.beginTransaction().setReorderingAllowed(true)
                .replace(containerId, destination, spec.tag()).commitNow();
        if (closed || requested != spec.route()) return;
        committed = spec.route();
    }

    private void commitClear() {
        Fragment current = fragments.findFragmentById(containerId);
        if (current != null) fragments.beginTransaction().setReorderingAllowed(true)
                .remove(current).commitNow();
        committed = null;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Fragment route renderer is closed");
    }
}
