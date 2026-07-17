package com.dvid.dcam.platform.storage;

import com.dvid.dcam.feature.media.application.port.MediaRepository;
import com.dvid.dcam.feature.media.domain.MediaEntry;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Filesystem implementation of the managed-media repository. */
public final class LocalMediaRepositoryImpl implements MediaRepository {
    private final DcamStorage storage;
    private final Set<String> allowedRoots = new LinkedHashSet<>();

    public LocalMediaRepositoryImpl(DcamStorage storage) {
        this.storage = storage;
        for (DcamFileType type : DcamFileType.values()) allowedRoots.add(type.getFolder());
    }

    @Override public List<MediaEntry> list(String relativePath) throws Exception {
        String safePath = normalize(relativePath);
        if (safePath.isEmpty()) return storageRoots();
        PathResolution resolution = resolve(safePath);
        if (resolution.mediaPath.isEmpty()) return mediaRoots(resolution);
        ensureAllowedTopLevel(resolution.mediaPath);
        File directory = resolveInsideRoot(resolution.root, resolution.mediaPath);
        if (!directory.exists() || !directory.isDirectory()) return Collections.emptyList();
        File[] children = directory.listFiles();
        if (children == null) return Collections.emptyList();
        Arrays.sort(children, Comparator.comparing(File::isFile).thenComparing(
                file -> file.getName().toLowerCase(Locale.ROOT)));
        List<MediaEntry> entries = new ArrayList<>();
        for (File child : children) {
            if (isMd5Sidecar(child)) continue;
            String childRelative = safePath + "/" + child.getName();
            entries.add(entry(child, childRelative));
        }
        return entries;
    }

    private List<MediaEntry> storageRoots() {
        List<File> roots = storage.mediaRootDirectories();
        List<MediaEntry> entries = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            String name = index == 0 ? "Internal" : roots.size() == 2
                    ? "External" : "External " + index;
            entries.add(new MediaEntry(name, name, true, 0L,
                    roots.get(index).lastModified(), null, 0));
        }
        return entries;
    }

    private List<MediaEntry> mediaRoots(PathResolution resolution) throws Exception {
        List<MediaEntry> entries = new ArrayList<>();
        for (String folder : allowedRoots) {
            File file = resolveInsideRoot(resolution.root, folder);
            entries.add(new MediaEntry(folder, resolution.location + "/" + folder, true,
                    0L, file.exists() ? file.lastModified() : 0L, null,
                    descendantFileCount(file)));
        }
        entries.sort(Comparator.comparing(MediaEntry::getName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    private MediaEntry entry(File file, String relativePath) {
        return new MediaEntry(file.getName(), relativePath.replace('\\', '/'),
                file.isDirectory(), file.isFile() ? file.length() : 0L,
                file.lastModified(), file.isDirectory() ? null : mimeType(file.getName()),
                file.isDirectory() ? descendantFileCount(file) : 0);
    }

    private static int descendantFileCount(File directory) {
        File[] children = directory.listFiles();
        if (children == null) return 0;
        int files = 0;
        for (File child : children) {
            if (Files.isSymbolicLink(child.toPath())) continue;
            if (child.isDirectory()) files += descendantFileCount(child);
            else if (!isMd5Sidecar(child)) files++;
        }
        return files;
    }

    private static boolean isMd5Sidecar(File file) {
        return file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".md5");
    }

    private File resolveInsideRoot(File root, String relativePath) throws Exception {
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
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
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
