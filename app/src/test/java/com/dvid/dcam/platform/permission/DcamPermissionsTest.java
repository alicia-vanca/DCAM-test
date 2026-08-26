package com.dvid.dcam.platform.permission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.Manifest;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DcamPermissionsTest {
    @Test void cameraPermissionIsIndependentFromAudioCapturePermission() {
        PermissionContext cameraOnly = new PermissionContext(Manifest.permission.CAMERA);
        PermissionContext cameraAndAudio = new PermissionContext(
                Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO);

        assertTrue(DcamPermissions.cameraGranted(cameraOnly));
        assertFalse(DcamPermissions.captureRuntimeGranted(cameraOnly));
        assertTrue(DcamPermissions.cameraGranted(cameraAndAudio));
        assertTrue(DcamPermissions.captureRuntimeGranted(cameraAndAudio));
    }

    private static final class PermissionContext extends ContextWrapper {
        private final Set<String> granted;

        private PermissionContext(String... granted) {
            super(null);
            this.granted = new HashSet<>(Arrays.asList(granted));
        }

        @Override
        public int checkSelfPermission(String permission) {
            return granted.contains(permission)
                    ? PackageManager.PERMISSION_GRANTED
                    : PackageManager.PERMISSION_DENIED;
        }
    }
}
