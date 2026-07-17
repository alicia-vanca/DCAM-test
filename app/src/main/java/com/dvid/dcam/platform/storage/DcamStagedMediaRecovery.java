package com.dvid.dcam.platform.storage;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/** Conservative startup recovery. Invalid, encrypted or ambiguous artifacts remain in Temp. */
final class DcamStagedMediaRecovery {
    private static final Pattern CONTRACT_NAME = Pattern.compile(
            "^DCAM_[^_]+_[^_]+_[0-9]{8}_[0-9]{6}(_IMP)?(_enc)?\\.(mp4|jpg|aac)$",
            Pattern.CASE_INSENSITIVE);

    private final DcamStorage storage;
    private final DcamMediaFinalizer finalizer;
    private final DcamMediaValidator validator;
    private final BooleanSupplier createVideoMd5;

    DcamStagedMediaRecovery(
            DcamStorage storage, DcamMediaFinalizer finalizer, DcamMediaValidator validator) {
        this(storage, finalizer, validator, null);
    }

    DcamStagedMediaRecovery(
            DcamStorage storage,
            DcamMediaFinalizer finalizer,
            DcamMediaValidator validator,
            BooleanSupplier createVideoMd5) {
        this.storage = storage;
        this.finalizer = finalizer;
        this.validator = validator;
        this.createVideoMd5 = createVideoMd5;
    }

    StagedMediaRecoveryReport recover() {
        int recovered = 0;
        int preserved = 0;
        int duplicates = 0;
        for (File temp : storage.recoveryTempDirectories()) {
            File[] candidates = temp.isDirectory() ? stagedFiles(temp) : null;
            if (candidates == null) continue;
            for (File candidate : candidates) {
                Matcher matcher = CONTRACT_NAME.matcher(candidate.getName());
                if (!matcher.matches()) continue;
                DcamFileType type = type(matcher, candidate.getName());
                DcamMediaFile mediaFile = new DcamMediaFile(
                        type, candidate.getName(), candidate, createdAt(candidate));
                File target = storage.finalFile(mediaFile);
                removeIncompletePublicationCopies(candidate, target);
                if (target.exists()) {
                    duplicates++;
                    continue;
                }
                // Encryption happens after CameraX closes. Without a journal, an _enc Temp file
                // may contain plaintext, ciphertext, or an interrupted transform; preserve it.
                if (matcher.group(2) != null || !validator.isPlayable(type, candidate)) {
                    preserved++;
                    continue;
                }
                try {
                    finalizer.finalizeMedia(mediaFile, shouldCreateMd5(type));
                    recovered++;
                } catch (Exception failure) {
                    preserved++;
                }
            }
        }
        return new StagedMediaRecoveryReport(recovered, preserved, duplicates);
    }

    private static File[] stagedFiles(File temp) {
        java.util.List<File> files = new java.util.ArrayList<>();
        File[] children = temp.listFiles();
        if (children == null) return new File[0];
        for (File child : children) {
            if (child.isFile() && !java.nio.file.Files.isSymbolicLink(child.toPath())) {
                files.add(child);
            } else if (child.isDirectory()
                    && !java.nio.file.Files.isSymbolicLink(child.toPath())
                    && child.getName().matches("\\d{4}-\\d{2}-\\d{2}")) {
                File[] datedFiles = child.listFiles(file -> file.isFile()
                        && !java.nio.file.Files.isSymbolicLink(file.toPath()));
                if (datedFiles != null) java.util.Collections.addAll(files, datedFiles);
            }
        }
        return files.toArray(new File[0]);
    }

    private static void removeIncompletePublicationCopies(File staging, File target) {
        File parent = target.getParentFile();
        if (!staging.isFile() || parent == null || !parent.isDirectory()) return;
        String prefix = "." + target.getName() + ".publishing-";
        File[] partials = parent.listFiles(file -> file.isFile() && file.getName().startsWith(prefix));
        if (partials == null) return;
        for (File partial : partials) {
            try { java.nio.file.Files.deleteIfExists(partial.toPath()); } catch (Exception ignored) { }
        }
    }

    private boolean shouldCreateMd5(DcamFileType type) {
        return "mp4".equals(type.getExtension())
                && createVideoMd5 != null
                && createVideoMd5.getAsBoolean();
    }

    private static DcamFileType type(Matcher matcher, String name) {
        String extension = matcher.group(3).toLowerCase(Locale.ROOT);
        if ("jpg".equals(extension)) return DcamFileType.IMAGE;
        if ("aac".equals(extension)) return DcamFileType.AUDIO;
        return matcher.group(1) == null ? DcamFileType.VIDEO : DcamFileType.SOS;
    }

    private static LocalDateTime createdAt(File file) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
    }
}
