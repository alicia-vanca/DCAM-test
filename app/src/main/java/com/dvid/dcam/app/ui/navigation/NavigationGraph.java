package com.dvid.dcam.app.ui.navigation;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Complete, immutable destination and root-back registry for one typed route scope. */
public final class NavigationGraph<R extends Enum<R>> {
    public enum BackAction { IGNORE, TO_ROUTE }
    public enum DestinationKind { FRAGMENT, PERSISTENT_LAYER }

    public interface ArgumentsFactory<R> {
        Bundle create(R route);
    }

    public static final class BackRule<R> {
        private final BackAction action;
        private final R target;

        private BackRule(BackAction action, R target) {
            this.action = action;
            this.target = target;
        }

        public static <R> BackRule<R> ignore() {
            return new BackRule<>(BackAction.IGNORE, null);
        }

        public static <R> BackRule<R> to(R target) {
            return new BackRule<>(BackAction.TO_ROUTE,
                    Objects.requireNonNull(target, "target"));
        }

        public BackAction action() { return action; }
        public R target() { return target; }
    }

    public static final class RouteSpec<R> {
        private final R route;
        private final Class<? extends Fragment> fragmentClass;
        private final String tag;
        private final ArgumentsFactory<R> argumentsFactory;
        private final BackRule<R> backRule;
        private final DestinationKind kind;

        public RouteSpec(R route, Class<? extends Fragment> fragmentClass, String tag,
                ArgumentsFactory<R> argumentsFactory, BackRule<R> backRule) {
            this.route = Objects.requireNonNull(route, "route");
            this.fragmentClass = Objects.requireNonNull(fragmentClass, "fragmentClass");
            this.tag = requireTag(tag);
            this.argumentsFactory = Objects.requireNonNull(argumentsFactory, "argumentsFactory");
            this.backRule = Objects.requireNonNull(backRule, "backRule");
            this.kind = DestinationKind.FRAGMENT;
        }

        private RouteSpec(R route, String tag, BackRule<R> backRule) {
            this.route = Objects.requireNonNull(route, "route");
            this.fragmentClass = null;
            this.tag = requireTag(tag);
            this.argumentsFactory = ignored -> new Bundle();
            this.backRule = Objects.requireNonNull(backRule, "backRule");
            this.kind = DestinationKind.PERSISTENT_LAYER;
        }

        public static <R> RouteSpec<R> persistent(
                R route, String tag, BackRule<R> backRule) {
            return new RouteSpec<>(route, tag, backRule);
        }

        public R route() { return route; }
        public Class<? extends Fragment> fragmentClass() { return fragmentClass; }
        public String tag() { return tag; }
        public Bundle arguments() { return argumentsFactory.create(route); }
        public BackRule<R> backRule() { return backRule; }
        public DestinationKind kind() { return kind; }

        private static String requireTag(String tag) {
            if (tag == null || tag.trim().isEmpty()) {
                throw new IllegalArgumentException("Destination tag must not be blank");
            }
            return tag;
        }
    }

    public static final class Builder<R extends Enum<R>> {
        private final Set<R> expectedRoutes;
        private final EnumMap<R, RouteSpec<R>> specs;
        private final Set<String> tags = new HashSet<>();

        public Builder(Class<R> routeType, Set<R> expectedRoutes) {
            this.expectedRoutes = Set.copyOf(expectedRoutes);
            this.specs = new EnumMap<>(Objects.requireNonNull(routeType, "routeType"));
        }

        public Builder<R> register(RouteSpec<R> spec) {
            Objects.requireNonNull(spec, "spec");
            if (!expectedRoutes.contains(spec.route())) {
                throw new IllegalArgumentException("Unexpected route " + spec.route());
            }
            if (specs.putIfAbsent(spec.route(), spec) != null) {
                throw new IllegalArgumentException("Duplicate route " + spec.route());
            }
            if (!tags.add(spec.tag())) {
                specs.remove(spec.route());
                throw new IllegalArgumentException("Duplicate destination tag " + spec.tag());
            }
            return this;
        }

        public NavigationGraph<R> build() {
            if (!specs.keySet().equals(expectedRoutes)) {
                Set<R> missing = new HashSet<>(expectedRoutes);
                missing.removeAll(specs.keySet());
                throw new IllegalStateException("Navigation graph routes do not match. Missing: "
                        + missing);
            }
            for (RouteSpec<R> spec : specs.values()) {
                if (spec.backRule().action() == BackAction.TO_ROUTE
                        && !specs.containsKey(spec.backRule().target())) {
                    throw new IllegalStateException("Back target " + spec.backRule().target()
                            + " for " + spec.route() + " is not registered");
                }
            }
            return new NavigationGraph<>(specs);
        }
    }

    private final Map<R, RouteSpec<R>> specs;

    private NavigationGraph(EnumMap<R, RouteSpec<R>> specs) {
        this.specs = Collections.unmodifiableMap(new EnumMap<>(specs));
    }

    public RouteSpec<R> require(R route) {
        RouteSpec<R> spec = specs.get(route);
        if (spec == null) throw new IllegalArgumentException("Unregistered route " + route);
        return spec;
    }

    public Set<R> routes() {
        return specs.keySet();
    }
}
