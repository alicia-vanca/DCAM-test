package com.dvid.dcam.platform.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Copies staging by default and uses same-filesystem move when low space makes copying unsafe. */
final class DcamMediaPublisher {
    private static final int COPY_BUFFER_BYTES = 64 * 1024;


    File publishClean(File staging, File target) throws IOException {
        validate(staging, "staging");
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Cannot create final media directory: " + parent);
        }
        synchronized (DcamMediaReservation.FILESYSTEM_LOCK) {
            if (target.exists()) throw new IOException("Final media already exists: " + target);
            move(staging.toPath(), target.toPath());
            return target;
        }
    }

    Publication publish(File staging, File target, boolean calculateMd5) throws IOException {
        validate(staging, "staging");
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Cannot create final media directory: " + parent);
        }
        if (target.exists()) throw new IOException("Final media already exists: " + target);

        if (parent.getUsableSpace() < staging.length() && sameFileStore(staging, parent)) {
            return atomicMove(staging, target, calculateMd5);
        }

        File partial = new File(parent, "." + target.getName() + ".publishing-" + UUID.randomUUID());
        try {
            String md5 = copyAndSync(staging, partial, calculateMd5);
            validate(partial, "publication copy");
            if (partial.length() != staging.length()) {
                throw new IOException("Publication copy size mismatch");
            }
            synchronized (DcamMediaReservation.FILESYSTEM_LOCK) {
                if (target.exists()) {
                    throw new IOException("Final media already exists: " + target);
                }
                move(partial.toPath(), target.toPath());
            }
            return new Publication(target, md5);
        } catch (IOException | RuntimeException failure) {
            try { Files.deleteIfExists(partial.toPath()); } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    static void validate(File file, String label) throws IOException {
        if (!file.isFile() || !file.canRead() || file.length() <= 0L) {
            throw new IOException("Invalid " + label + ": " + file);
        }
    }

    private static boolean sameFileStore(File source, File targetDirectory) {
        try {
            return Files.getFileStore(source.toPath()).equals(
                    Files.getFileStore(targetDirectory.toPath()));
        } catch (IOException failure) {
            return false;
        }
    }

    private static Publication atomicMove(File staging, File target, boolean calculateMd5)
            throws IOException {
        String md5 = null;
        if (calculateMd5) {
            MessageDigest digest = md5();
            try (var input = Files.newInputStream(staging.toPath())) {
                byte[] buffer = new byte[COPY_BUFFER_BYTES];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            md5 = hex(digest.digest());
        }
        synchronized (DcamMediaReservation.FILESYSTEM_LOCK) {
            if (target.exists()) throw new IOException("Final media already exists: " + target);
            move(staging.toPath(), target.toPath());
            return new Publication(target, md5);
        }
    }

    static void move(Path source, Path target, CopyOption... fallbackOptions)
            throws IOException {
        move(Files::move, source, target, fallbackOptions);
    }

    static void move(MoveOperation operation, Path source, Path target,
            CopyOption... fallbackOptions) throws IOException {
        CopyOption[] atomicOptions = new CopyOption[fallbackOptions.length + 1];
        atomicOptions[0] = StandardCopyOption.ATOMIC_MOVE;
        System.arraycopy(fallbackOptions, 0, atomicOptions, 1, fallbackOptions.length);
        try {
            operation.move(source, target, atomicOptions);
        } catch (AtomicMoveNotSupportedException unsupported) {
            operation.move(source, target, fallbackOptions);
        }
    }

    private static String copyAndSync(File source, File target, boolean calculateMd5)
            throws IOException {
        MessageDigest md5 = calculateMd5 ? md5() : null;
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[COPY_BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                if (md5 != null) md5.update(buffer, 0, read);
            }
            output.flush();
            output.getFD().sync();
        }
        return md5 == null ? null : hex(md5.digest());
    }

    private static MessageDigest md5() throws IOException {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("MD5 unavailable", impossible);
        }
    }

    private static String hex(byte[] digest) {
        StringBuilder hex = new StringBuilder(32);
        for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }

    @FunctionalInterface
    interface MoveOperation {
        Path move(Path source, Path target, CopyOption... options) throws IOException;
    }

    static final class Publication {
        final File file;
        final String md5;

        Publication(File file, String md5) {
            this.file = file;
            this.md5 = md5;
        }
    }
}
