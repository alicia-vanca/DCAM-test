package com.dvid.dcam.app.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

final class NavigationGraphTest {
    private enum ChildRoute { ROOT, STEP }

    @Test void sameGraphTypeValidatesACompleteChildFlow() {
        NavigationGraph.Builder<ChildRoute> builder = new NavigationGraph.Builder<>(
                ChildRoute.class, EnumSet.allOf(ChildRoute.class));
        builder.register(spec(ChildRoute.ROOT, "child:root",
                NavigationGraph.BackRule.ignore()));
        builder.register(spec(ChildRoute.STEP, "child:step",
                NavigationGraph.BackRule.to(ChildRoute.ROOT)));

        NavigationGraph<ChildRoute> graph = builder.build();

        assertEquals(EnumSet.allOf(ChildRoute.class), graph.routes());
        assertEquals(ChildRoute.ROOT, graph.require(ChildRoute.STEP).backRule().target());
        assertThrows(UnsupportedOperationException.class,
                () -> graph.routes().remove(ChildRoute.ROOT));
    }

    @Test void rejectsMissingDuplicateAndUnexpectedRoutes() {
        NavigationGraph.Builder<ChildRoute> missing = new NavigationGraph.Builder<>(
                ChildRoute.class, EnumSet.allOf(ChildRoute.class));
        missing.register(spec(ChildRoute.ROOT, "child:root",
                NavigationGraph.BackRule.ignore()));
        assertThrows(IllegalStateException.class, missing::build);

        NavigationGraph.Builder<ChildRoute> duplicate = new NavigationGraph.Builder<>(
                ChildRoute.class, EnumSet.allOf(ChildRoute.class));
        duplicate.register(spec(ChildRoute.ROOT, "child:root",
                NavigationGraph.BackRule.ignore()));
        assertThrows(IllegalArgumentException.class, () -> duplicate.register(
                spec(ChildRoute.ROOT, "child:other", NavigationGraph.BackRule.ignore())));
    }

    @Test void rejectsDuplicateTagsAndUnregisteredBackTargets() {
        NavigationGraph.Builder<ChildRoute> duplicateTag = new NavigationGraph.Builder<>(
                ChildRoute.class, EnumSet.allOf(ChildRoute.class));
        duplicateTag.register(spec(ChildRoute.ROOT, "child:same",
                NavigationGraph.BackRule.ignore()));
        assertThrows(IllegalArgumentException.class, () -> duplicateTag.register(
                spec(ChildRoute.STEP, "child:same",
                        NavigationGraph.BackRule.to(ChildRoute.ROOT))));

        NavigationGraph.Builder<ChildRoute> invalidTarget = new NavigationGraph.Builder<>(
                ChildRoute.class, EnumSet.of(ChildRoute.ROOT));
        invalidTarget.register(spec(ChildRoute.ROOT, "child:root",
                NavigationGraph.BackRule.to(ChildRoute.STEP)));
        assertThrows(IllegalStateException.class, invalidTarget::build);
    }

    private static NavigationGraph.RouteSpec<ChildRoute> spec(ChildRoute route, String tag,
            NavigationGraph.BackRule<ChildRoute> backRule) {
        return new NavigationGraph.RouteSpec<>(route, Fragment.class, tag,
                ignored -> new Bundle(), backRule);
    }
}
