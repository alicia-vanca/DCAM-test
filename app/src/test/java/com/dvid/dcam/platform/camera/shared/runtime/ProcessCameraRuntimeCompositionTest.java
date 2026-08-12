package com.dvid.dcam.platform.camera.shared.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProcessCameraRuntimeCompositionTest {
    @Test void capabilityServiceOwnsOnlyProductionRuntimeOwnerInstance() throws IOException {
        Path root = sourceRoot();
        String composition = source("app/AppComposition.java");
        String service = source("platform/device/capability/CameraCapabilityService.java");
        String owner = source("platform/camera/shared/runtime/ProcessCameraRuntimeOwner.java");
        List<Path> productionOwners;
        try (var files = Files.walk(root)) {
            productionOwners = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("new ProcessCameraRuntimeOwner("))
                    .toList();
        }

        assertEquals(1, productionOwners.size());
        assertTrue(productionOwners.get(0).toString().replace('\\', '/')
                .endsWith("app/AppComposition.java"));
        assertTrue(composition.contains(
                "private static CameraCapabilityService cameraCapabilityService;"));
        assertFalse(service.contains("static CameraCapabilityService processInstance"));
        assertTrue(service.contains("private final ProcessCameraRuntimeOwner runtimeOwner;"));
        assertTrue(service.contains("public ProcessCameraRuntimeOwner runtimeOwner()"));
        assertFalse(owner.contains("static ProcessCameraRuntimeOwner"));
    }

    @Test void ownerHasNoAndroidUiOrLegacyGatewayDependency() throws IOException {
        String owner = source("platform/camera/shared/runtime/ProcessCameraRuntimeOwner.java");
        String backend = source("platform/camera/shared/runtime/ProcessCameraRuntimeBackend.java");

        assertFalse(owner.contains("import android."));
        assertFalse(owner.contains("app.ui"));
        assertFalse(owner.contains("CameraXCameraGatewayImpl"));
        assertFalse(backend.contains("import android."));
        assertFalse(backend.contains("Surface"));
    }

    private static String source(String relative) throws IOException {
        return read(sourceRoot().resolve("com/dvid/dcam").resolve(relative));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Path sourceRoot() {
        Path app = Path.of("app/src/main/java");
        return Files.exists(app) ? app : Path.of("src/main/java");
    }
}