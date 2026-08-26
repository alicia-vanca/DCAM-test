package com.dvid.dcam.platform.camera.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.application.port.CameraRuntimeOperations;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class SharedCameraPipelineContractTest {
    private static final Set<String> OPERATIONS = Set.of(
            "bindSession",
            "bindStandaloneImageSession",
            "previewProgress",
            "startEncoder",
            "stopEncoder",
            "finalizeEncoder",
            "captureJpeg",
            "release",
            "diagnostics");

    @Test void applicationPortExposesOnlySharedHardwareOperations() {
        assertTrue(CameraRuntimeOperations.class.isInterface());
        assertEquals(OPERATIONS, methodNames(CameraRuntimeOperations.class));
        assertNoAndroidTypes(CameraRuntimeOperations.class);
    }

    @Test void platformApiAddsOnlyPipelineIdentity() {
        assertTrue(SharedCameraPipeline.class.isInterface());
        Set<String> expected = new java.util.HashSet<>(OPERATIONS);
        expected.add("pipelineId");
        assertEquals(expected, methodNames(SharedCameraPipeline.class));
        assertNoAndroidTypes(SharedCameraPipeline.class);
    }

    private static Set<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    private static void assertNoAndroidTypes(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            assertFalseAndroid(method.getReturnType());
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalseAndroid(parameter);
            }
        }
    }

    private static void assertFalseAndroid(Class<?> type) {
        assertTrue(!type.getName().startsWith("android.")
                        && !type.getName().startsWith("androidx."),
                () -> "Android type leaked through contract: " + type.getName());
    }
}
