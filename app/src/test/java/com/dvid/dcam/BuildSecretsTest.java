package com.dvid.dcam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.Test;

final class BuildSecretsTest {
    @Test
    void generatedSecretsMatchConfiguredInputsWithoutPlaintextSource() throws Exception {
        Properties manifest = load(rootFile("build-secrets.properties"));
        Properties app = load(rootFile("application.properties"));
        Properties local = loadOptional(rootFile("application-local.properties"));
        String generatedSource = Files.readString(generatedSource(), StandardCharsets.UTF_8);

        for (String name : manifest.stringPropertyNames()) {
            assertFalse(app.containsKey(name), "BuildConfig contains build secret: " + name);
            String expected = System.getenv(name);
            if (expected == null || expected.isEmpty()) expected = local.getProperty(name, "");
            Method getter = BuildSecrets.class.getMethod(name);
            Method configured = BuildSecrets.class.getMethod(name + "_CONFIGURED");
            String actual = (String) getter.invoke(null);
            byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
            try {
                assertTrue(MessageDigest.isEqual(expectedBytes, actualBytes),
                        "Generated build secret mismatch: " + name);
                assertTrue((boolean) configured.invoke(null) == !expected.isBlank(),
                        "Generated build secret configured flag mismatch: " + name);
                if (!expected.isBlank()) {
                    assertTrue(expectedBytes.length >= 8,
                            "Configured build secret is too short: " + name);
                    assertFalse(generatedSource.contains(expected),
                            "Generated source contains plaintext build secret: " + name);
                }
            } finally {
                Arrays.fill(expectedBytes, (byte) 0);
                Arrays.fill(actualBytes, (byte) 0);
            }
        }
    }

    @Test
    void releaseWorkflowMapsEveryRegisteredSecretExactlyOnce() throws Exception {
        Properties manifest = load(rootFile("build-secrets.properties"));
        String workflow = Files.readString(rootFile(".github/workflows/build.yml"), StandardCharsets.UTF_8);
        String gitignore = Files.readString(rootFile(".gitignore"), StandardCharsets.UTF_8);

        assertTrue(workflow.contains("./gradlew --no-configuration-cache --no-daemon clean assembleRelease"));
        assertTrue(gitignore.contains("/application-local.properties"));

        for (String name : manifest.stringPropertyNames()) {
            String mapping = name + ": ${{ secrets." + name + " }}";
            assertEquals(1, count(workflow, mapping),
                    "Release workflow secret mapping mismatch: " + name);
        }
    }
    private static Properties load(Path path) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Properties loadOptional(Path path) throws Exception {
        return Files.isRegularFile(path) ? load(path) : new Properties();
    }

    private static Path rootFile(String name) {
        Path fromModule = Path.of("..", name);
        return Files.exists(fromModule) ? fromModule : Path.of(name);
    }

    private static int count(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }
    private static Path generatedSource() {
        Path fromModule = Path.of("build/generated/source/buildSecrets/main/com/dvid/dcam/BuildSecrets.java");
        if (Files.isRegularFile(fromModule)) return fromModule;
        return Path.of("app/build/generated/source/buildSecrets/main/com/dvid/dcam/BuildSecrets.java");
    }
}
