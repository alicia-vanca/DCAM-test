package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class DcamMd5Sidecar {
    private DcamMd5Sidecar() {}

    static File write(File media, String digest) throws IOException {
        File sidecar = fileFor(media);
        if (sidecar.isFile()
                && digest.equals(new String(Files.readAllBytes(sidecar.toPath()), StandardCharsets.US_ASCII).trim())) {
            return sidecar;
        }
        File partial = new File(media.getParentFile(), "." + sidecar.getName()
                + ".publishing-" + UUID.randomUUID());
        byte[] content = (digest + System.lineSeparator()).getBytes(StandardCharsets.US_ASCII);
        try {
            try (FileOutputStream output = new FileOutputStream(partial)) {
                output.write(content);
                output.flush();
                output.getFD().sync();
            }
            Files.move(partial.toPath(), sidecar.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(sidecar);
            return sidecar;
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(partial.toPath(), sidecar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(sidecar);
            return sidecar;
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }

    static boolean exists(File media) {
        return fileFor(media).isFile();
    }

    private static File fileFor(File media) {
        return new File(media.getParentFile(), baseName(media.getName()) + ".md5");
    }

    private static String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static void forceDirectory(File file) {
        try (var directory = java.nio.channels.FileChannel.open(file.getParentFile().toPath())) {
            directory.force(true);
        } catch (IOException ignored) { }
    }
}
