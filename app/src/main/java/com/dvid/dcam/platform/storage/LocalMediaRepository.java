package com.dvid.dcam.platform.storage;

import com.dvid.dcam.feature.media.application.port.MediaRepository;
import com.dvid.dcam.feature.media.domain.MediaEntry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Filesystem implementation of the managed-media repository. */
public final class LocalMediaRepository implements MediaRepository {
    private final DcamStorage storage;
    private final Set<String> allowedRoots = new LinkedHashSet<>();
    // ponytail: Session-only directory cache; persist after measured cold-start misses.
    private final Map<String, CachedDirectoryCount> directoryCounts = new ConcurrentHashMap<>();

    public LocalMediaRepository(DcamStorage storage) {
        this.storage = storage;
        for (DcamFileType type : DcamFileType.values()) allowedRoots.add(type.getFolder());
    }

    @Override public List<MediaEntry> list(String relativePath) throws Exception {
        return list(relativePath, true);
    }

    @Override public List<MediaEntry> listWithoutCounts(String relativePath) throws Exception {
        return list(relativePath, false);
    }

    private List<MediaEntry> list(String relativePath, boolean includeCounts) throws Exception {
        checkInterrupted();
        String safePath = normalize(relativePath);
        if (safePath.isEmpty()) return storageRoots();
        PathResolution resolution = resolve(safePath);
        if (resolution.mediaPath.isEmpty()) return mediaRoots(resolution, includeCounts);
        ensureAllowedTopLevel(resolution.mediaPath);
        File directory = resolveInsideRoot(resolution.root, resolution.mediaPath);
        if (!directory.exists() || !directory.isDirectory()) return Collections.emptyList();
        File[] children = directory.listFiles();
        if (children == null) return Collections.emptyList();
        List<MediaEntry> entries = new ArrayList<>();
        for (File child : children) {
            checkInterrupted();
            String childRelative = safePath + "/" + child.getName();
            MediaEntry mediaEntry = entry(child, childRelative, includeCounts);
            if (mediaEntry != null) entries.add(mediaEntry);
        }
        entries.sort(Comparator.comparing(MediaEntry::isDirectory).reversed().thenComparing(
                entry -> entry.getName().toLowerCase(Locale.ROOT)));
        return entries;
    }

    private List<MediaEntry> storageRoots() {
        List<File> roots = storage.browsableMediaRootDirectories();
        List<MediaEntry> entries = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            String name = storageRootName(index, roots.size());
            entries.add(new MediaEntry(name, name, true, 0L,
                    roots.get(index).lastModified(), null, 0));
        }
        return entries;
    }

    private static String storageRootName(int index, int rootCount) {
        if (index == 0) return "Internal";
        return rootCount == 2 ? "External" : "External " + index;
    }

    private List<MediaEntry> mediaRoots(PathResolution resolution, boolean includeCounts)
            throws Exception {
        List<MediaEntry> entries = new ArrayList<>();
        for (String folder : allowedRoots) {
            checkInterrupted();
            File file = resolveInsideRoot(resolution.root, folder);
            entries.add(new MediaEntry(folder, resolution.location + "/" + folder, true,
                    0L, file.exists() ? file.lastModified() : 0L, null,
                    directoryFileCount(file, includeCounts)));
        }
        entries.sort(Comparator.comparing(MediaEntry::getName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private MediaEntry entry(File file, String relativePath, boolean includeCounts)
            throws Exception {
        boolean directory;
        boolean regularFile;
        long sizeBytes;
        long modifiedAtMillis;
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    file.toPath(), BasicFileAttributes.class);
            directory = attributes.isDirectory();
            regularFile = attributes.isRegularFile();
            sizeBytes = regularFile ? attributes.size() : 0L;
            modifiedAtMillis = attributes.lastModifiedTime().toMillis();
        } catch (IOException ignored) {
            directory = file.isDirectory();
            regularFile = file.isFile();
            sizeBytes = regularFile ? file.length() : 0L;
            modifiedAtMillis = file.lastModified();
        }
        if (regularFile && isMd5Sidecar(file.getName())) return null;
        int childFileCount = directory ? directoryFileCount(file, includeCounts) : 0;
        return new MediaEntry(file.getName(), relativePath, directory,
                sizeBytes, modifiedAtMillis, directory ? null : mimeType(file.getName()),
                childFileCount);
    }

    private int directoryFileCount(File directory, boolean includeCounts)
            throws InterruptedException {
        if (includeCounts) return descendantFileCount(directory);
        Integer cachedCount = cachedDirectoryFileCount(directory);
        return cachedCount == null ? -1 : cachedCount;
    }

    private Integer cachedDirectoryFileCount(File directory) throws InterruptedException {
        return cachedDirectoryFileCount(directory, directoryAttributes(directory));
    }

    private Integer cachedDirectoryFileCount(
            File directory, BasicFileAttributes attributes) throws InterruptedException {
        checkInterrupted();
        CachedDirectoryCount cached = directoryCounts.get(directory.getAbsolutePath());
        if (cached == null || !cached.matches(attributes)) return null;
        int files = cached.directFileCount;
        for (String childName : cached.childDirectoryNames) {
            File child = new File(directory, childName);
            Integer childCount = cachedDirectoryFileCount(
                    child, directoryAttributes(child));
            if (childCount == null) return null;
            files += childCount;
        }
        return files;
    }

    private int descendantFileCount(File directory) throws InterruptedException {
        return descendantFileCount(directory, null);
    }

    private int descendantFileCount(File directory, BasicFileAttributes knownAttributes)
            throws InterruptedException {
        checkInterrupted();
        String cacheKey = directory.getAbsolutePath();
        BasicFileAttributes before = knownAttributes == null
                ? directoryAttributes(directory) : knownAttributes;
        Integer cachedCount = cachedDirectoryFileCount(directory, before);
        if (cachedCount != null) return cachedCount;
        File[] children = directory.listFiles();
        if (children == null) {
            directoryCounts.remove(cacheKey);
            return 0;
        }
        List<String> childDirectoryNames = new ArrayList<>();
        int directFiles = 0;
        int files = 0;
        for (File child : children) {
            checkInterrupted();
            ChildFileCount childCount = childFileCount(child, childDirectoryNames);
            directFiles += childCount.directFileCount;
            files += childCount.fileCount;
        }
        BasicFileAttributes after = directoryAttributes(directory);
        if (sameDirectory(before, after)) {
            directoryCounts.put(cacheKey,
                    new CachedDirectoryCount(after, directFiles, childDirectoryNames));
        } else {
            directoryCounts.remove(cacheKey);
        }
        return files;
    }

    private ChildFileCount childFileCount(
            File child, List<String> childDirectoryNames) throws InterruptedException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    child.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attributedChildFileCount(child, attributes, childDirectoryNames);
        } catch (IOException ignored) {
            return fallbackChildFileCount(child, childDirectoryNames);
        }
    }

    private ChildFileCount attributedChildFileCount(
            File child, BasicFileAttributes attributes, List<String> childDirectoryNames)
            throws InterruptedException {
        if (attributes.isSymbolicLink()) return ChildFileCount.NONE;
        if (attributes.isDirectory()) {
            childDirectoryNames.add(child.getName());
            return new ChildFileCount(0, descendantFileCount(child, attributes));
        }
        return !attributes.isRegularFile() || !isMd5Sidecar(child.getName())
                ? ChildFileCount.DIRECT_FILE : ChildFileCount.NONE;
    }

    private ChildFileCount fallbackChildFileCount(
            File child, List<String> childDirectoryNames) throws InterruptedException {
        if (child.isDirectory()) {
            childDirectoryNames.add(child.getName());
            return new ChildFileCount(0, descendantFileCount(child));
        }
        return isMd5Sidecar(child) ? ChildFileCount.NONE : ChildFileCount.DIRECT_FILE;
    }
    private static BasicFileAttributes directoryAttributes(File directory) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    directory.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attributes.isDirectory() ? attributes : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static boolean sameDirectory(
            BasicFileAttributes first, BasicFileAttributes second) {
        return first != null && second != null
                && first.lastModifiedTime().toMillis() == second.lastModifiedTime().toMillis()
                && first.size() == second.size()
                && Objects.equals(first.fileKey(), second.fileKey());
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Media listing cancelled");
        }
    }

    private static boolean isMd5Sidecar(File file) {
        return file.isFile() && isMd5Sidecar(file.getName());
    }

    private static boolean isMd5Sidecar(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".md5");
    }

    private File resolveInsideRoot(File root, String relativePath) throws IOException {
        File canonicalRoot = root.getCanonicalFile();
        File candidate = new File(canonicalRoot, relativePath).getCanonicalFile();
        String rootPath = canonicalRoot.getPath() + File.separator;
        if (!candidate.getPath().startsWith(rootPath)) {
            throw new SecurityException("Media path escapes DCAM root");
        }
        return candidate;
    }

    private PathResolution resolve(String relativePath) {
        int slash = relativePath.indexOf('/');
        String location = slash < 0 ? relativePath : relativePath.substring(0, slash);
        String mediaPath = slash < 0 ? "" : relativePath.substring(slash + 1);
        return new PathResolution(location, mediaPath, storage.mediaRootDirectory(location));
    }

    private void ensureAllowedTopLevel(String relativePath) {
        String top = relativePath.contains("/")
                ? relativePath.substring(0, relativePath.indexOf('/')) : relativePath;
        if (!allowedRoots.contains(top)) throw new SecurityException("Unsupported media folder");
    }

    private static String normalize(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return "";
        String normalized = relativePath.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private static String mimeType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }

    private static final class CachedDirectoryCount {
        private final long modifiedAtMillis;
        private final long sizeBytes;
        private final Object fileKey;
        private final int directFileCount;
        private final List<String> childDirectoryNames;

        private CachedDirectoryCount(
                BasicFileAttributes attributes,
                int directFileCount,
                List<String> childDirectoryNames) {
            modifiedAtMillis = attributes.lastModifiedTime().toMillis();
            sizeBytes = attributes.size();
            fileKey = attributes.fileKey();
            this.directFileCount = directFileCount;
            this.childDirectoryNames = List.copyOf(childDirectoryNames);
        }

        private boolean matches(BasicFileAttributes attributes) {
            return attributes != null
                    && modifiedAtMillis == attributes.lastModifiedTime().toMillis()
                    && sizeBytes == attributes.size()
                    && Objects.equals(fileKey, attributes.fileKey());
        }
    }
    private static final class ChildFileCount {
        private static final ChildFileCount NONE = new ChildFileCount(0, 0);
        private static final ChildFileCount DIRECT_FILE = new ChildFileCount(1, 1);

        private final int directFileCount;
        private final int fileCount;

        private ChildFileCount(int directFileCount, int fileCount) {
            this.directFileCount = directFileCount;
            this.fileCount = fileCount;
        }
    }
    private static final class PathResolution {
        private final String location;
        private final String mediaPath;
        private final File root;

        private PathResolution(String location, String mediaPath, File root) {
            this.location = location;
            this.mediaPath = mediaPath;
            this.root = root;
        }
    }
}
