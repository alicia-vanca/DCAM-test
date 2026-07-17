package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

final class DcamMd5RetryQueue {
    private static final String FILE_NAME = "md5-retry.list";
    private static final Object FILE_LOCK = new Object();
    private final File file;

    DcamMd5RetryQueue(File root) { this.file = new File(new File(root, "Config"), FILE_NAME); }

    void add(File media) throws IOException {
        synchronized (FILE_LOCK) {
            Set<String> paths = new LinkedHashSet<>(read());
            paths.add(media.getCanonicalPath());
            write(new ArrayList<>(paths));
        }
    }

    void remove(File media) throws IOException {
        synchronized (FILE_LOCK) {
            String path = media.getCanonicalPath();
            List<String> remaining = read().stream().filter(item -> !item.equals(path)).collect(Collectors.toList());
            if (remaining.isEmpty()) {
                Files.deleteIfExists(file.toPath());
                forceDirectory();
            } else write(remaining);
        }
    }

    List<File> pending() throws IOException {
        synchronized (FILE_LOCK) {
            List<File> result = new ArrayList<>();
            for (String path : read()) result.add(new File(path));
            return result;
        }
    }

    private List<String> read() throws IOException {
        if (!file.isFile()) return List.of();
        return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8).stream()
                .map(String::trim).filter(item -> !item.isEmpty()).distinct().collect(Collectors.toList());
    }

    private void write(List<String> paths) throws IOException {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create MD5 retry directory: " + parent);
        }
        File partial = new File(parent, "." + FILE_NAME + "." + UUID.randomUUID());
        try {
            try (FileOutputStream output = new FileOutputStream(partial)) {
                output.write(String.join(System.lineSeparator(), paths).getBytes(StandardCharsets.UTF_8));
                output.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            try {
                Files.move(partial.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectory();
        } finally { Files.deleteIfExists(partial.toPath()); }
    }

    private void forceDirectory() throws IOException {
        try (var directory = java.nio.channels.FileChannel.open(file.getParentFile().toPath())) {
            directory.force(true);
        } catch (IOException unsupported) { }
    }
}
