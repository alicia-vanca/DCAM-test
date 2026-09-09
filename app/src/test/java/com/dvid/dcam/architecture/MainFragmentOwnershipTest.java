package com.dvid.dcam.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class MainFragmentOwnershipTest {
    @Test void activityHasOneStableProviderBoundaryAndNoDestinationBindings() throws IOException {
        String activity = source("app/MainActivity.java");

        assertTrue(activity.contains(
                "extends FragmentActivity implements MainUiScope.Provider"));
        assertFalse(activity.contains("Fragment.Host"));
        assertFalse(activity.contains("interface Host"));
        for (String binding : List.of("ScreenCameraBinding", "ScreenLoginBinding",
                "ScreenMenuBinding", "ScreenFileExplorerBinding",
                "ScreenDeveloperUsersBinding", "ScreenSettingsDetailBinding")) {
            assertFalse(activity.contains(binding), binding + " must be Fragment-owned");
        }
        assertFalse(activity.contains("renderedSettingsList"));
        assertFalse(activity.contains("renderedSettingsScroll"));
        assertFalse(activity.contains("SettingsControlRenderer"));
        assertFalse(activity.contains("screenName(MainScreen"));
        for (String fragment : List.of("CameraFragment", "LoginFragment", "MenuFragment",
                "FileExplorerFragment", "DeveloperUsersFragment",
                "SettingsDetailFragment")) {
            assertFalse(activity.contains(fragment),
                    fragment + " registry/creation belongs outside MainActivity");
        }
    }

    @Test void fragmentsOwnBindingsAndClearThemWithTheirViewLifecycle() throws IOException {
        for (String fragment : List.of("CameraFragment.java", "LoginFragment.java",
                "MenuFragment.java", "FileExplorerFragment.java",
                "DeveloperUsersFragment.java", "SettingsDetailFragment.java")) {
            String source = source("app/ui/" + fragment);
            assertTrue(source.contains("onCreateView"), fragment);
            assertTrue(source.contains("onDestroyView"), fragment);
            assertTrue(source.contains("binding = null"), fragment);
            assertFalse(source.contains("interface Host"), fragment);
        }

        String activity = source("app/MainActivity.java");
        String camera = source("app/ui/CameraFragment.java");
        String cameraView = source("app/ui/CameraScreenView.java");
        assertFalse(activity.contains(".setOperator("));
        assertFalse(cameraView.contains("setOperator("));
        assertTrue(camera.contains("binding.operatorId.setText"));
    }

    @Test void navigationHasNoFragmentBackStackOrUntypedRouteApi() throws IOException {
        String ui = sourcesUnder("app/ui");

        assertFalse(ui.contains("addToBackStack("));
        assertFalse(ui.contains("popBackStack("));
        assertFalse(ui.contains("commitAllowingStateLoss("));
        assertFalse(ui.contains("navigate(Object"));
        assertFalse(ui.contains("navigate(String"));
        assertFalse(ui.contains("viewModel.show("));
        assertFalse(source("app/ui/navigation/MainNavigator.java").contains("void goBack()"));
        String renderer = source("app/ui/navigation/FragmentRouteRenderer.java");
        assertFalse(renderer.contains("ReadyListener"));
        assertTrue(renderer.contains(
                "fragments.findFragmentById(containerId) == existing"));
    }

    @Test void settingsCoordinatorRetainsNoFragmentViews() throws IOException {
        String coordinator = source("app/ui/settings/SettingsScreenCoordinator.java");
        String fragment = source("app/ui/SettingsDetailFragment.java");

        assertFalse(coordinator.contains("android.view.View"));
        assertFalse(coordinator.contains("TextView"));
        assertFalse(coordinator.contains("LinearLayout"));
        assertFalse(coordinator.contains("SettingsControlRenderer"));
        assertTrue(fragment.contains("new SettingsControlRenderer(requireContext())"));
        assertTrue(fragment.contains("observeInvalidations"));
    }

    @Test void mainScopeGroupsPlatformWorkByReusableCapability() throws IOException {
        String scope = source("app/ui/MainUiScope.java");

        assertTrue(scope.contains("interface DatabaseResetter"));
        assertTrue(scope.contains("interface MediaOpener"));
        assertFalse(scope.contains("interface PlatformActions"));
    }

    private static String sourcesUnder(String relative) throws IOException {
        StringBuilder result = new StringBuilder();
        try (var paths = Files.walk(root().resolve(relative))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> result.append(read(path)).append('\n'));
        }
        return result.toString();
    }

    private static String source(String relative) throws IOException {
        return read(root().resolve(relative));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private static Path root() {
        Path direct = Path.of("src/main/java/com/dvid/dcam");
        return Files.exists(direct) ? direct : Path.of("app/src/main/java/com/dvid/dcam");
    }
}
