package com.dvid.dcam.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class LayerDependencyTest {
    private static final Pattern IMPORT = Pattern.compile("^import\\s+([^;]+);", Pattern.MULTILINE);
    private static final Pattern DIRECT_LOGGING = Pattern.compile(
            "import\\s+android\\.util\\.Log|\\bLog\\.(v|d|i|w|e|wtf)\\s*\\(|"
                    + "System\\.(out|err)\\.(print|println)\\s*\\(|printStackTrace\\s*\\(");

    @Test void featureAndCoreStayPureJava() throws IOException {
        List<String> violations = new ArrayList<>();
        collectForbiddenImports(appRoot().resolve("feature"),
                List.of("android.", "androidx.", "com.google.",
                        "com.dvid.dcam.app.", "com.dvid.dcam.platform."), violations);
        collectForbiddenImports(coreRoot().resolve("core"),
                List.of("android.", "androidx.", "com.google.",
                        "com.dvid.dcam.app.", "com.dvid.dcam.feature.",
                        "com.dvid.dcam.platform."), violations);
        assertNoViolations("Feature/core must stay pure Java", violations);
    }

    @Test void featureSlicesDoNotDependOnEachOther() throws IOException {
        List<String> violations = new ArrayList<>();
        Path featureRoot = appRoot().resolve("feature");
        Pattern featureImport = Pattern.compile(
                "import\\s+com\\.dvid\\.dcam\\.feature\\.([^.]+)\\.");
        try (Stream<Path> files = Files.walk(featureRoot)) {
            files.filter(LayerDependencyTest::isJava).forEach(file -> {
                String relative = normalized(featureRoot.relativize(file));
                String owner = relative.substring(0, relative.indexOf('/'));
                Matcher imports = featureImport.matcher(read(file));
                while (imports.find()) {
                    if (!owner.equals(imports.group(1))) {
                        violations.add(normalized(file) + " imports feature " + imports.group(1));
                    }
                }
            });
        }
        assertNoViolations("Feature slices must share through core or app boundaries", violations);
    }

    @Test void platformDoesNotDependOnApp() throws IOException {
        List<String> violations = new ArrayList<>();
        collectForbiddenImports(appRoot().resolve("platform"),
                List.of("com.dvid.dcam.app."), violations);
        assertNoViolations("Platform must not depend on app", violations);
    }

    @Test void uiAndMainActivityDoNotImportPlatform() throws IOException {
        List<String> violations = new ArrayList<>();
        collectForbiddenImports(appRoot().resolve("app/ui"),
                List.of("com.dvid.dcam.platform."), violations);
        collectForbiddenImports(appRoot().resolve("app/MainActivity.java"),
                List.of("com.dvid.dcam.platform."), violations);
        assertNoViolations("UI must call use cases or app-owned Android collaborators", violations);
    }

    @Test void uiDoesNotDependOnCompositionRoot() {
        List<String> violations = new ArrayList<>();
        collectForbiddenImports(appRoot().resolve("app/ui"), List.of(
                "com.dvid.dcam.app.AppComposition",
                "com.dvid.dcam.app.CameraPipelineModeController"), violations);
        assertNoViolations("UI must depend on typed capabilities instead of app composition",
                violations);
    }

    @Test void onlyCompositionAndFrameworkEntriesImportPlatformFromApp() throws IOException {
        Set<String> allowed = Set.of("app/AppComposition.java", "app/DcamApplication.java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(appRoot().resolve("app"))) {
            files.filter(LayerDependencyTest::isJava).forEach(file -> {
                String relative = normalized(appRoot().relativize(file));
                if (allowed.contains(relative)) return;
                collectForbiddenImports(file, List.of("com.dvid.dcam.platform."), violations);
            });
        }
        assertNoViolations("Only composition and framework entry points may select platform code",
                violations);
    }

    @Test void useCasesAreConcreteClasses() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(appRoot(), coreRoot())) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(LayerDependencyTest::isJava).forEach(file -> {
                    String name = file.getFileName().toString();
                    String source = read(file);
                    if (name.endsWith("UseCaseImpl.java")) {
                        violations.add(normalized(file) + " uses forbidden UseCaseImpl suffix");
                    }
                    if (!name.endsWith("UseCase.java")) return;
                    String type = name.substring(0, name.length() - ".java".length());
                    if (!Pattern.compile("\\bpublic\\s+final\\s+class\\s+" + Pattern.quote(type) + "\\b")
                            .matcher(source).find()) {
                        violations.add(normalized(file) + " must declare public final class " + type);
                    }
                });
            }
        }
        assertNoViolations("Use cases are concrete; ports and callbacks carry interfaces", violations);
    }

    @Test void applicationPortsAreInterfaces() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(appRoot(), coreRoot())) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(LayerDependencyTest::isJava).forEach(file -> {
                    String path = normalized(file);
                    if (!path.contains("/application/port/")) return;
                    String name = file.getFileName().toString();
                    String type = name.substring(0, name.length() - ".java".length());
                    if (!Pattern.compile("\\bpublic\\s+interface\\s+" + Pattern.quote(type) + "\\b")
                            .matcher(read(file)).find()) {
                        violations.add(path + " must declare public interface " + type);
                    }
                });
            } catch (IOException error) {
                throw new RuntimeException(error);
            }
        }
        assertNoViolations("Application ports must be interfaces", violations);
    }

    @Test void featureInterfacesUsePortsOrExplicitCommandNames() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(appRoot().resolve("feature"), coreRoot().resolve("core"))) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(LayerDependencyTest::isJava).forEach(file -> {
                    String path = normalized(file);
                    String source = read(file);
                    if (!Pattern.compile("\\bpublic\\s+interface\\s+").matcher(source).find()) return;
                    if (path.contains("/application/port/")) return;
                    String name = file.getFileName().toString();
                    if (path.contains("/feature/")
                            && (name.endsWith("Commands.java") || name.endsWith("Events.java"))) {
                        return;
                    }
                    violations.add(path + " must be an application port or explicit Commands/Events seam");
                });
            }
        }
        assertNoViolations("Feature interfaces need a named boundary", violations);
    }

    @Test void mainActivityDoesNotOwnFeatureGateMappings() {
        String activity = read(appRoot().resolve("app/MainActivity.java"));

        assertTrue(!activity.contains("requiredGatesForSetting")
                && !activity.contains("requiredGatesForReadOnlySetting"),
                "Feature gate mappings belong outside MainActivity");
    }

    @Test void directAndroidLoggingStaysInsidePlatformLogging() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(appRoot(), coreRoot())) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(LayerDependencyTest::isJava).forEach(file -> {
                    String path = normalized(file);
                    if (path.contains("/platform/logging/")) return;
                    if (DIRECT_LOGGING.matcher(read(file)).find()) violations.add(path);
                });
            }
        }
        assertNoViolations("Production code must use core Logger", violations);
    }

    private static void collectForbiddenImports(
            Path root, List<String> prefixes, List<String> violations) {
        if (!Files.exists(root)) return;
        if (Files.isRegularFile(root)) {
            collectFileImports(root, prefixes, violations);
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(LayerDependencyTest::isJava)
                    .forEach(file -> collectFileImports(file, prefixes, violations));
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private static void collectFileImports(
            Path file, List<String> prefixes, List<String> violations) {
        Matcher imports = IMPORT.matcher(read(file));
        while (imports.find()) {
            String imported = imports.group(1);
            for (String prefix : prefixes) {
                if (imported.startsWith(prefix)) {
                    violations.add(normalized(file) + " imports " + imported);
                    break;
                }
            }
        }
    }

    private static void assertNoViolations(String message, List<String> violations) {
        assertTrue(violations.isEmpty(), message + ":\n" + String.join("\n", violations));
    }

    private static boolean isJava(Path file) {
        return Files.isRegularFile(file) && file.getFileName().toString().endsWith(".java");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private static String normalized(Path path) { return path.toString().replace('\\', '/'); }

    private static Path appRoot() {
        return existingPath(Path.of("src/main/java/com/dvid/dcam"),
                Path.of("app/src/main/java/com/dvid/dcam"));
    }

    private static Path coreRoot() {
        return existingPath(Path.of("../core/src/main/java/com/dvid/dcam"),
                Path.of("core/src/main/java/com/dvid/dcam"));
    }

    private static Path existingPath(Path first, Path second) {
        if (Files.exists(first)) return first;
        if (Files.exists(second)) return second;
        throw new IllegalStateException("Source root not found: " + first + " or " + second);
    }
}
