package com.dvid.dcam.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class LayerDependencyTest {
    private static final Pattern IMPORT = Pattern.compile("^import\\s+([^;]+);", Pattern.MULTILINE);
    private static final Pattern PUBLIC_INTERFACE = Pattern.compile("\\bpublic\\s+interface\\s+");
    private static final Pattern PUBLIC_INTERFACE_DECLARATION =
            Pattern.compile("\\bpublic\\s+interface\\s+(\\w+)\\s*\\{", Pattern.MULTILINE);
    private static final Pattern INTERFACE_METHOD = Pattern.compile(
            "(?:^|\\n)\\s*(?:[\\w<>\\[\\].?,]+\\s+)+\\w+\\s*\\([^;{}]*\\)"
                    + "\\s*(?:throws\\s+[\\w\\s,.]+)?;");
    private static final Pattern PROJECT_INTERFACE_IMPLEMENTATION = Pattern.compile(
            "\\bclass\\s+(\\w+)\\s+implements\\s+[^\\{;]*\\b"
                    + "(UseCase|Repository|Gateway|Recorder|Opener|Store|Source|Sink|Output)\\b");
    private static final Pattern CORE_FORBIDDEN = Pattern.compile(
            "^(android\\.|androidx\\.|com\\.google\\.|com\\.dvid\\.dcam\\.(app|feature|platform)\\.)");
    private static final Pattern DOMAIN_FORBIDDEN = Pattern.compile(
            "^(android\\.|androidx\\.|com\\.google\\.|com\\.dvid\\.dcam\\.(app|platform)\\.)"
                    + "|^com\\.dvid\\.dcam\\.(feature|core)\\..*\\.(application|adapter)\\.");
    private static final Pattern APPLICATION_FORBIDDEN = Pattern.compile(
            "^(android\\.|androidx\\.|com\\.google\\.|com\\.dvid\\.dcam\\.(app|platform)\\.)"
                    + "|^com\\.dvid\\.dcam\\.(feature|core)\\..*\\.adapter\\.");
    private static final Pattern APP_PRESENTATION_FORBIDDEN = Pattern.compile(
            "^(android\\.|com\\.google\\.|com\\.dvid\\.dcam\\.platform\\.)");
    private static final Pattern PLATFORM_FORBIDDEN = Pattern.compile(
            "^com\\.dvid\\.dcam\\.app\\.");
    private static final Pattern DIRECT_LOGGING = Pattern.compile(
            "import\\s+android\\.util\\.Log|\\bLog\\.(v|d|i|w|e|wtf)\\s*\\(|"
                    + "System\\.(out|err)\\.(print|println)\\s*\\(|printStackTrace\\s*\\(");

    @Test void dependenciesPointInwardAcrossCleanArchitectureLayers() throws IOException {
        Path root = mainJavaRoot().resolve("com/dvid/dcam");
        Path coreRoot = coreJavaRoot().resolve("com/dvid/dcam/core");
        List<String> violations = new ArrayList<>();
        collectViolations(coreRoot, CORE_FORBIDDEN, violations);
        collectCapabilityLayerViolations(coreRoot, violations);
        collectCapabilityLayerViolations(root.resolve("feature"), violations);
        collectViolations(root.resolve("app/presentation"), APP_PRESENTATION_FORBIDDEN, violations);
        collectViolations(root.resolve("platform"), PLATFORM_FORBIDDEN, violations);
        assertTrue(violations.isEmpty(),
                "Clean Architecture dependency violations:\n" + String.join("\n", violations));
    }

    @Test void featureAndCoreFilesUseCanonicalLayerPackages() throws IOException {
        Path featureRoot = mainJavaRoot().resolve("com/dvid/dcam/feature");
        Path coreRoot = coreJavaRoot().resolve("com/dvid/dcam/core");
        List<String> violations = new ArrayList<>();
        collectNonCanonicalPaths(featureRoot, "feature/", violations);
        collectNonCanonicalPaths(coreRoot, "core/", violations);
        assertTrue(violations.isEmpty(),
                "Non-canonical feature/core packages:\n" + String.join("\n", violations));
    }

    @Test void featureAndCoreInterfacesAreExplicitBoundaries() throws IOException {
        Path root = mainJavaRoot().resolve("com/dvid/dcam");
        List<String> violations = new ArrayList<>();
        collectMisplacedInterfaces(root.resolve("feature"), violations);
        collectMisplacedInterfaces(coreJavaRoot().resolve("com/dvid/dcam/core"), violations);
        assertTrue(violations.isEmpty(),
                "Interfaces must be application use cases or named application boundaries:\n"
                        + String.join("\n", violations));
    }

    @Test void publicInterfacesDeclareCallableBehavior() throws IOException {
        List<String> violations = new ArrayList<>();
        collectEmptyInterfaces(mainJavaRoot().resolve("com/dvid/dcam"), violations);
        collectEmptyInterfaces(coreJavaRoot().resolve("com/dvid/dcam"), violations);
        assertTrue(violations.isEmpty(),
                "Public interfaces must describe current callable behavior, not placeholders:\n"
                        + String.join("\n", violations));
    }

    @Test void projectInterfaceImplementationsEndWithImpl() throws IOException {
        List<String> violations = new ArrayList<>();
        collectMisnamedImplementations(mainJavaRoot().resolve("com/dvid/dcam"), violations);
        collectMisnamedImplementations(coreJavaRoot().resolve("com/dvid/dcam"), violations);
        assertTrue(violations.isEmpty(),
                "Classes implementing project interfaces must end with Impl:\n" + String.join("\n", violations));
    }

    @Test void productionCodeUsesOnlyApprovedTopLevelPackages() throws IOException {
        Path root = mainJavaRoot().resolve("com/dvid/dcam");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                Path relative = root.relativize(file);
                String topLevel = relative.getName(0).toString();
                if (!List.of("app", "feature", "platform").contains(topLevel)) {
                    violations.add(relative.toString());
                }
            }
        }
        Path coreRoot = coreJavaRoot().resolve("com/dvid/dcam");
        try (Stream<Path> files = Files.walk(coreRoot)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                Path relative = coreRoot.relativize(file);
                if (!"core".equals(relative.getName(0).toString())) {
                    violations.add("core module: " + relative);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "Production classes outside app/core/feature/platform:\n" + String.join("\n", violations));
    }

    @Test void productionLoggingUsesDcamLoggerBoundary() throws IOException {
        List<String> violations = new ArrayList<>();
        collectDirectLoggingViolations(mainJavaRoot().resolve("com/dvid/dcam"), violations);
        assertTrue(violations.isEmpty(),
                "Production code must use DcamLogger; direct logging found:\n"
                        + String.join("\n", violations));
    }

    private static void collectDirectLoggingViolations(Path root, List<String> violations)
            throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                if (normalized(file).endsWith("platform/logging/DcamLogger.java")) continue;
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (DIRECT_LOGGING.matcher(source).find()) violations.add(normalized(file));
            }
        }
    }

    @Test void uiLayersDoNotReadFilesystemCapacityDirectly() throws IOException {
        String filesystemCapacity =
                "android\\.os\\.StatFs|java\\.io\\.File|getExternalFilesDirs|getUsableSpace\\(";
        List<String> violations = new ArrayList<>();
        collectSourceTextViolations(mainJavaRoot().resolve("com/dvid/dcam/app"),
                filesystemCapacity, violations);
        collectSourceTextViolations(mainJavaRoot().resolve("com/dvid/dcam/feature"),
                filesystemCapacity, violations);
        assertTrue(violations.isEmpty(),
                "UI/application code must obtain storage capacity through a boundary:\n"
                        + String.join("\n", violations));
    }
    @Test void mainActivityRemainsDeveloperBindingScreenAgnostic() throws IOException {
        Path mainActivity = mainJavaRoot().resolve("com/dvid/dcam/app/MainActivity.java");
        String source = Files.readString(mainActivity, StandardCharsets.UTF_8);
        assertFalse(source.contains("DEV_BUTTON_"),
                "Developer button setting IDs belong in DeveloperButtonBindingsScreen");
        assertFalse(source.contains("Reset device defaults"),
                "Developer button reset UI belongs in DeveloperButtonBindingsScreen");
    }

    @Test void alwaysOnInfrastructureDoesNotDependOnDeveloperFeatureGates() throws IOException {
        Path platform = mainJavaRoot().resolve("com/dvid/dcam/platform");
        Pattern developerFeatureGate = Pattern.compile("^com\\.dvid\\.dcam\\.core\\.feature\\.");
        List<String> violations = new ArrayList<>();
        for (String capability : List.of("logging", "database", "config", "storage")) {
            collectViolations(platform.resolve(capability), developerFeatureGate, violations);
        }
        assertTrue(violations.isEmpty(),
                "Always-on infrastructure must not be controlled by developer feature gates:\n"
                        + String.join("\n", violations));
    }

    @Test void productionIdentityDoesNotUseAndroidId() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String forbidden : List.of(
                "ANDROID_ID",
                "Settings.Secure",
                "androidIdHash",
                "android_id_hash",
                "getAndroidIdHash",
                "Build.SERIAL",
                "device_lookup")) {
            collectSourceTextViolations(mainJavaRoot().resolve("com/dvid/dcam"), forbidden, violations);
            collectSourceTextViolations(coreJavaRoot().resolve("com/dvid/dcam"), forbidden, violations);
        }
        assertTrue(violations.isEmpty(),
                "Production identity must not use Android ID or android_id_hash:\n"
                        + String.join("\n", violations));
    }

    private static int findMatchingBrace(String source, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') depth++;
            else if (current == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static void collectNonCanonicalPaths(
            Path capabilityRoot, String prefix, List<String> violations) throws IOException {
        try (Stream<Path> files = Files.walk(capabilityRoot)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                String relative = normalized(capabilityRoot.relativize(file));
                if (!isCanonicalCapabilityPath(relative.split("/"))) {
                    violations.add(prefix + relative);
                }
            }
        }
    }

    private static boolean isCanonicalCapabilityPath(String[] parts) {
        if (parts.length < 3) return false;
        String layer = parts[1];
        if ("domain".equals(layer)) return true;
        if ("presentation".equals(layer)) return true;
        if ("application".equals(layer)) {
            if (parts.length < 4) return false;
            if ("usecase".equals(parts[2])) return true;
            if ("repository".equals(parts[2])) return true;
            return "port".equals(parts[2]);
        }
        return false;
    }

    private static void collectCapabilityLayerViolations(
            Path capabilityRoot, List<String> violations) throws IOException {
        try (Stream<Path> files = Files.walk(capabilityRoot)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                String relative = normalized(capabilityRoot.relativize(file));
                if (relative.contains("/domain/")) {
                    collectFileViolations(file, DOMAIN_FORBIDDEN, violations);
                } else if (relative.contains("/application/")) {
                    collectFileViolations(file, APPLICATION_FORBIDDEN, violations);
                }
            }
        }
    }

    private static void collectMisplacedInterfaces(
            Path packageRoot, List<String> violations) throws IOException {
        try (Stream<Path> files = Files.walk(packageRoot)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (!PUBLIC_INTERFACE.matcher(source).find()) continue;
                String path = normalized(packageRoot.relativize(file));
                boolean useCaseInterface = path.contains("/application/usecase/")
                        && file.getFileName().toString().endsWith("UseCase.java")
                        && !file.getFileName().toString().endsWith("UseCaseImpl.java");
                boolean portPackage = path.contains("/application/port/");
                boolean boundaryName = fileNameEndsWith(file,
                        "Repository.java", "Gateway.java", "Recorder.java", "Opener.java",
                        "Store.java", "Source.java", "Sink.java");
                if (!useCaseInterface && (!portPackage || !boundaryName)) violations.add(path);
            }
        }
    }

    private static boolean fileNameEndsWith(Path file, String... suffixes) {
        String fileName = file.getFileName().toString();
        for (String suffix : suffixes) {
            if (fileName.endsWith(suffix)) return true;
        }
        return false;
    }

    private static void collectViolations(
            Path layer, Pattern forbidden, List<String> violations) throws IOException {
        try (Stream<Path> files = Files.walk(layer)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                collectFileViolations(file, forbidden, violations);
            }
        }
    }

    private static void collectFileViolations(
            Path file, Pattern forbidden, List<String> violations) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Matcher imports = IMPORT.matcher(source);
        while (imports.find()) {
            String imported = imports.group(1);
            if (forbidden.matcher(imported).find()) {
                violations.add(file.getFileName() + " imports " + imported);
            }
        }
    }

    private static void collectEmptyInterfaces(
            Path root, List<String> violations) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher declaration = PUBLIC_INTERFACE_DECLARATION.matcher(source);
                while (declaration.find()) {
                    int bodyEnd = findMatchingBrace(source, declaration.end() - 1);
                    String body = bodyEnd < 0 ? "" : source.substring(declaration.end(), bodyEnd);
                    if (!INTERFACE_METHOD.matcher(body).find()) {
                        violations.add(normalized(root.relativize(file)) + " declares empty interface "
                                + declaration.group(1));
                    }
                }
            }
        }
    }

    private static void collectMisnamedImplementations(
            Path root, List<String> violations) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher implementation = PROJECT_INTERFACE_IMPLEMENTATION.matcher(source);
                if (implementation.find() && !implementation.group(1).endsWith("Impl")) {
                    violations.add(normalized(root.relativize(file)));
                }
            }
        }
    }

    private static void collectSourceTextViolations(
            Path root, String forbidden, List<String> violations) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(LayerDependencyTest::isJava).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains(forbidden)) {
                    violations.add(normalized(root.relativize(file)) + " contains " + forbidden);
                }
            }
        }
    }

    private static boolean isJava(Path path) {
        return path.toString().endsWith(".java");
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Path mainJavaRoot() {
        return existingPath(Path.of("app/src/main/java"), Path.of("src/main/java"));
    }

    private static Path coreJavaRoot() {
        return existingPath(Path.of("core/src/main/java"), Path.of("../core/src/main/java"));
    }

    private static Path existingPath(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) return candidate.normalize();
        }
        throw new IllegalStateException("Source root not found: " + List.of(candidates));
    }
}
