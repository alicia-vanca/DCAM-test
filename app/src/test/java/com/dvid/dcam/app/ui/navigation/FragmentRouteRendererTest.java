package com.dvid.dcam.app.ui.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import com.dvid.dcam.app.ui.MainScreen;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 28)
public final class FragmentRouteRendererTest {
    private static final String MENU_TAG = "test:menu";
    private static final String FILES_TAG = "test:files";

    private ActivityController<TestActivity> controller;

    @Before public void setUp() {
        ProbeFragment.resetViewCreations();
        controller = Robolectric.buildActivity(TestActivity.class).setup();
    }

    @After public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void recreateAdoptsRestoredFragmentAndCommitNowCreatesViewBeforeReady() {
        TestActivity activity = controller.get();
        RendererHarness first = new RendererHarness(activity.getSupportFragmentManager());

        first.router.render(MainScreen.MENU);

        Fragment rendered = activity.getSupportFragmentManager().findFragmentByTag(MENU_TAG);
        assertNotNull(rendered);
        assertNotNull(rendered.getView());
        assertEquals(1, ProbeFragment.viewCreations());
        assertEquals(List.of(MainScreen.MENU), first.ready);
        assertEquals(List.of(1), first.viewCreationsAtReady);

        Bundle savedState = new Bundle();
        controller.pause().saveInstanceState(savedState).stop().destroy();
        controller = Robolectric.buildActivity(TestActivity.class)
                .create(savedState).start().resume().visible();
        activity = controller.get();
        FragmentManager fragments = activity.getSupportFragmentManager();
        Fragment restored = fragments.findFragmentByTag(MENU_TAG);
        assertNotNull(restored);
        assertNotNull(restored.getView());
        int creationsBeforeReconcile = ProbeFragment.viewCreations();
        RendererHarness restoredHarness = new RendererHarness(fragments);

        restoredHarness.router.reconcileAfterStateSave(MainScreen.MENU);

        assertSame(restored, fragments.findFragmentByTag(MENU_TAG));
        assertEquals(creationsBeforeReconcile, ProbeFragment.viewCreations());
        assertEquals(1, fragments.getFragments().size());
        assertEquals(MainScreen.MENU, restoredHarness.router.committedRoute());
        assertEquals(List.of(MainScreen.MENU), restoredHarness.ready);
        assertEquals(List.of(creationsBeforeReconcile),
                restoredHarness.viewCreationsAtReady);

        restoredHarness.router.render(MainScreen.MENU);

        assertSame(restored, fragments.findFragmentByTag(MENU_TAG));
        assertEquals(List.of(MainScreen.MENU), restoredHarness.ready);
    }

    @Test public void stateSavedRequestDefersUntilAuthoritativeReconcile() {
        TestActivity activity = controller.get();
        FragmentManager fragments = activity.getSupportFragmentManager();
        RendererHarness first = new RendererHarness(fragments);
        first.router.render(MainScreen.MENU);
        Bundle savedState = new Bundle();

        controller.pause().saveInstanceState(savedState);

        assertTrue(fragments.isStateSaved());
        first.router.render(MainScreen.FILES);
        assertNull(fragments.findFragmentByTag(FILES_TAG));
        assertNotNull(fragments.findFragmentByTag(MENU_TAG));
        assertEquals(MainScreen.MENU, first.router.committedRoute());
        assertEquals(List.of(MainScreen.MENU), first.ready);

        controller.stop().destroy();
        controller = Robolectric.buildActivity(TestActivity.class)
                .create(savedState).start().resume().visible();
        activity = controller.get();
        fragments = activity.getSupportFragmentManager();
        assertFalse(fragments.isStateSaved());
        assertNotNull(fragments.findFragmentByTag(MENU_TAG));
        RendererHarness restored = new RendererHarness(fragments);

        restored.router.reconcileAfterStateSave(MainScreen.FILES);

        Fragment files = fragments.findFragmentByTag(FILES_TAG);
        assertNotNull(files);
        assertNotNull(files.getView());
        assertNull(fragments.findFragmentByTag(MENU_TAG));
        assertEquals(1, fragments.getFragments().size());
        assertEquals(MainScreen.FILES, restored.router.committedRoute());
        assertEquals(List.of(MainScreen.FILES), restored.ready);
    }

    private static NavigationGraph<MainScreen> graph() {
        NavigationGraph.Builder<MainScreen> builder = new NavigationGraph.Builder<>(
                MainScreen.class, EnumSet.of(MainScreen.MENU, MainScreen.FILES));
        builder.register(spec(MainScreen.MENU, MENU_TAG));
        builder.register(spec(MainScreen.FILES, FILES_TAG));
        return builder.build();
    }

    private static NavigationGraph.RouteSpec<MainScreen> spec(MainScreen route, String tag) {
        return new NavigationGraph.RouteSpec<>(route, ProbeFragment.class, tag,
                ignored -> new Bundle(), NavigationGraph.BackRule.ignore());
    }

    public static final class TestActivity extends FragmentActivity {
        static final int CONTAINER_ID = 0x123456;

        @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            FrameLayout container = new FrameLayout(this);
            container.setId(CONTAINER_ID);
            setContentView(container);
        }
    }

    public static final class ProbeFragment extends Fragment {
        private static int viewCreations;

        public ProbeFragment() {}

        static void resetViewCreations() { viewCreations = 0; }
        static int viewCreations() { return viewCreations; }

        @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return new FrameLayout(requireContext());
        }

        @Override public void onViewCreated(@NonNull View view,
                @Nullable Bundle savedInstanceState) {
            viewCreations++;
        }
    }

    private static final class RendererHarness {
        final List<MainScreen> ready = new ArrayList<>();
        final List<Integer> viewCreationsAtReady = new ArrayList<>();
        final MainScreenRouter router;

        RendererHarness(FragmentManager fragments) {
            router = new MainScreenRouter(graph(), new FragmentRouteRenderer<>(fragments,
                    TestActivity.CONTAINER_ID), new NoOpCameraLayer(),
                    (previous, current) -> {
                        ready.add(current);
                        viewCreationsAtReady.add(ProbeFragment.viewCreations());
                    });
        }
    }

    private static final class NoOpCameraLayer implements MainScreenRouter.CameraLayer {
        @Override public boolean ensureCamera() { return true; }
        @Override public void showCamera() {}
        @Override public void coverCamera() {}
    }
}
