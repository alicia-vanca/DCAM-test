package com.dvid.dcam.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DcamKioskLifecycleTest {
    @Test void runtimePermissionsUseActivityRequestsNotDevicePolicyGrantCalls() throws IOException {
        String controller = source("platform/device/DcamKioskController.java");
        String activity = source("app/MainActivity.java");

        assertTrue(controller.contains("PERMISSION_POLICY_AUTO_GRANT"));
        assertFalse(controller.contains("setPermissionGrantState("));
        assertTrue(activity.contains("permissionLauncher.launch(missingCorePermissions)"));
        assertTrue(activity.contains("locationPermissionLauncher.launch(missingLocationPermissions)"));
    }

    @Test void launcherRedirectsIntoHomeTaskWithoutSecondMainActivity() throws IOException {
        String manifest = manifest();
        String launcher = source("app/DcamLauncherActivity.java");
        int launcherStart = manifest.indexOf("android:name=\".app.DcamLauncherActivity\"");
        int launcherEnd = manifest.indexOf("</activity>", launcherStart);
        int mainStart = manifest.indexOf("android:name=\".app.MainActivity\"");
        int mainEnd = manifest.indexOf("</activity>", mainStart);
        String launcherBlock = manifest.substring(launcherStart, launcherEnd);
        String mainBlock = manifest.substring(mainStart, mainEnd);

        assertTrue(launcherBlock.contains("android.intent.category.LAUNCHER"));
        assertFalse(mainBlock.contains("android.intent.category.LAUNCHER"));
        assertTrue(mainBlock.contains("android.intent.category.HOME"));
        assertTrue(launcher.contains("addCategory(Intent.CATEGORY_HOME)"));
        assertTrue(launcher.contains("new Intent(this, MainActivity.class)"));
        assertTrue(launcher.contains("Intent.FLAG_ACTIVITY_NEW_TASK"));
    }

    @Test void coldNonHomeMainActivityLaunchRedirectsBeforeComposition()
            throws IOException {
        String activity = source("app/MainActivity.java");
        int onCreateStart = activity.indexOf(
                "protected void onCreate(Bundle savedInstanceState)");
        int compositionStart = activity.indexOf(
                "composition = AppComposition.create(this)", onCreateStart);
        String startup = activity.substring(onCreateStart, compositionStart);

        assertTrue(startup.contains("if (redirectToHomeTaskIfNeeded()) return;"));
        int redirectStart = activity.indexOf(
                "private boolean redirectToHomeTaskIfNeeded()");
        int redirectEnd = activity.indexOf("@Override", redirectStart);
        String redirect = activity.substring(redirectStart, redirectEnd);
        assertTrue(redirect.contains("DcamLauncherActivity.class"));
        assertTrue(redirect.contains("redirectingToHomeTask = true"));
        assertTrue(redirect.contains("startActivity(new Intent"));
        assertTrue(redirect.contains("finish()"));
        int onDestroyStart = activity.indexOf("protected void onDestroy()");
        int onDestroyEnd = activity.indexOf("private static String identity", onDestroyStart);
        String onDestroy = activity.substring(onDestroyStart, onDestroyEnd);
        assertTrue(onDestroy.contains("if (redirectingToHomeTask)"));
    }

    @Test void homeReentryOpensCameraScreen() throws IOException {
        String activity = source("app/MainActivity.java");
        int methodStart = activity.indexOf("protected void onNewIntent(Intent intent)");
        int methodEnd = activity.indexOf("private void updateHardwareButtonLayout", methodStart);
        String onNewIntent = activity.substring(methodStart, methodEnd);

        assertTrue(onNewIntent.contains("if (!isHomeLaunch(intent)) return;"));
        assertTrue(onNewIntent.contains("setIntent(intent)"));
        assertTrue(activity.contains("intent.hasCategory(Intent.CATEGORY_HOME)"));
        assertTrue(onNewIntent.contains("Home button requested camera preview"));
        assertTrue(onNewIntent.contains("navigation.navigate(MainScreen.CAMERA)"));
    }

    @Test void receiversSchedulePolicyOutsideTheirMainThread() throws IOException {
        String admin = source("platform/device/DcamDeviceAdminReceiver.java");
        String boot = source("platform/device/DcamBootReceiver.java");

        assertFalse(admin.contains("goAsync()"));
        assertFalse(admin.contains("applyActiveKioskPolicyAsync"));
        assertTrue(boot.contains("goAsync()"));
        assertTrue(boot.contains("applyActiveKioskPolicyAsync"));
    }

    @Test void expectedPolicyEntryDoesNotAttachSyntheticException() throws IOException {
        String controller = source("platform/device/DcamKioskController.java");
        int entryStart = controller.indexOf("private void logPolicyEntry(");
        int entryEnd = controller.indexOf("private static void complete(", entryStart);
        String entry = controller.substring(entryStart, entryEnd);

        assertTrue(entry.contains("logger.info("));
        assertFalse(entry.contains("stackTrace("));
    }

    @Test void activityAppliesPolicyOnceAndResumeOnlyChecksLockTask() throws IOException {
        String activity = source("app/MainActivity.java");
        int onResumeStart = activity.indexOf("protected void onResume()");
        int onResumeEnd = activity.indexOf("@Override", onResumeStart + 1);
        String onResume = activity.substring(onResumeStart, onResumeEnd);

        assertTrue(activity.contains("applyKioskPolicyThenRequestCorePermissions()"));
        assertFalse(onResume.contains("applyActiveKioskPolicy"));
        assertTrue(onResume.contains("enterLockTaskIfAllowed(this"));
    }

    @Test void developerModeCanReleaseDeviceOwnerWithConfirmation() throws IOException {
        String activity = source("app/MainActivity.java");
        String settingsFragment = source("app/ui/SettingsDetailFragment.java");
        String controller = source("platform/device/DcamKioskController.java");

        int users = settingsFragment.indexOf("users.setText(R.string.manage_developer_users)");
        int remove = settingsFragment.indexOf(
                "removeDeviceOwner.setText(R.string.remove_device_owner)");
        assertTrue(remove > users);
        assertTrue(settingsFragment.contains("remove_device_owner_title"));
        assertTrue(activity.contains("androidRuntime.removeDeviceOwner()"));

        int release = controller.indexOf("public boolean removeDeviceOwner()");
        int unlock = controller.indexOf("setLockTaskPackages(admin, new String[0])", release);
        int clear = controller.indexOf("clearDeviceOwnerApp(packageName)", release);
        assertTrue(release >= 0);
        assertTrue(unlock >= release);
        assertTrue(clear > unlock);
    }

    private static String source(String relative) throws IOException {
        Path root = existingPath(Path.of("app/src/main/java"), Path.of("src/main/java"));
        return Files.readString(root.resolve("com/dvid/dcam").resolve(relative),
                StandardCharsets.UTF_8);
    }

    private static String manifest() throws IOException {
        return Files.readString(existingPath(
                Path.of("app/src/main/AndroidManifest.xml"),
                Path.of("src/main/AndroidManifest.xml")), StandardCharsets.UTF_8);
    }

    private static Path existingPath(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate.normalize();
        }
        throw new IllegalStateException("Source root not found: " + List.of(candidates));
    }
}
