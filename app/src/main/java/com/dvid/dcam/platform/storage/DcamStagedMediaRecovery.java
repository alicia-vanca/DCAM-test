package com.dvid.dcam.platform.storage;

import com.dvid.dcam.core.logging.application.port.Logger;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative startup recovery. Invalid, encrypted or ambiguous artifacts remain in Temp. */
final class DcamStagedMediaRecovery {
    private static final Pattern CONTRACT_NAME = Pattern.compile(
            "^DCAM_[^_]+_[^_]+_[0-9]{8}_[0-9]{6}(_IMP)?(_enc)?\\.(mp4|jpg|aac|m4a)$",
            Pattern.CASE_INSENSITIVE);

    private final DcamStorage storage;
    private final DcamMediaFinalizer finalizer;
    private final DcamMediaValidator validator;
    private final DcamInterruptedMp4Finalizer interruptedMp4Finalizer;
    private final BooleanSupplier createVideoMd5;
    private final Supplier<String> mediaEncryptionPassword;
    private final Md5RetryCallback md5RetryCallback;
    private final Logger logger;

    DcamStagedMediaRecovery(
            DcamStorage storage,
            DcamMediaFinalizer finalizer,
            DcamMediaValidator validator,
            Logger logger) {
        this(storage, finalizer, validator, null, () -> "", logger);
    }

    DcamStagedMediaRecovery(
            DcamStorage storage,
            DcamMediaFinalizer finalizer,
            DcamMediaValidator validator,
            BooleanSupplier createVideoMd5,
            Logger logger) {
        this(storage, finalizer, validator, createVideoMd5, () -> "", logger);
    }

    DcamStagedMediaRecovery(
            DcamStorage storage,
            DcamMediaFinalizer finalizer,
            DcamMediaValidator validator,
            BooleanSupplier createVideoMd5,
            Supplier<String> mediaEncryptionPassword,
            Logger logger) {
        this(storage, finalizer, validator, new DcamInterruptedMp4Finalizer(),
                createVideoMd5, mediaEncryptionPassword, logger);
    }

    DcamStagedMediaRecovery(
            DcamStorage storage,
            DcamMediaFinalizer finalizer,
            DcamMediaValidator validator,
            BooleanSupplier createVideoMd5,
            Supplier<String> mediaEncryptionPassword,
            Md5RetryCallback md5RetryCallback,
            Logger logger) {
        this(storage, finalizer, validator, new DcamInterruptedMp4Finalizer(),
                createVideoMd5, mediaEncryptionPassword, md5RetryCallback, logger);
    }

    DcamStagedMediaRecovery(
            DcamStorage storage,
            DcamMediaFinalizer finalizer,
            DcamMediaValidator validator,
            DcamInterruptedMp4Finalizer interruptedMp4Finalizer,
            BooleanSupplier createVideoMd5,
            Logger logger) {
        this(storage, finalizer, validator, interruptedMp4Finalizer,
                createVideoMd5, () -> "", logger);
    }

    DcamStagedMediaRecovery(
            DcamStorage storage,
            DcamMediaFinalizer finalizer,
            DcamMediaValidator validator,
            DcamInterruptedMp4Finalizer interruptedMp4Finalizer,
            BooleanSupplier createVideoMd5,
            Supplier<String> mediaEncryptionPassword,
            Logger logger) {
        this(storage, finalizer, validator, interruptedMp4Finalizer,
                createVideoMd5, mediaEncryptionPassword,
                DcamStagedMediaRecovery::writeMd5Immediately, logger);
    }

    DcamStagedMediaRecovery(
            DcamStorage storage,
            DcamMediaFinalizer finalizer,
            DcamMediaValidator validator,
            DcamInterruptedMp4Finalizer interruptedMp4Finalizer,
            BooleanSupplier createVideoMd5,
            Supplier<String> mediaEncryptionPassword,
            Md5RetryCallback md5RetryCallback,
            Logger logger) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.finalizer = Objects.requireNonNull(finalizer, "finalizer");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.interruptedMp4Finalizer = Objects.requireNonNull(
                interruptedMp4Finalizer, "interruptedMp4Finalizer");
        this.createVideoMd5 = createVideoMd5;
        this.mediaEncryptionPassword = Objects.requireNonNull(
                mediaEncryptionPassword, "mediaEncryptionPassword");
        this.md5RetryCallback = Objects.requireNonNull(md5RetryCallback, "md5RetryCallback");
        this.logger = Objects.requireNonNull(logger, "logger");
    }
    File[][] snapshot() {
        return snapshot(Set.of());
    }

    File[][] snapshot(Set<String> excludedStagingPaths) {
        Objects.requireNonNull(excludedStagingPaths, "excludedStagingPaths");
        java.util.List<File[]> snapshots = new java.util.ArrayList<>();
        for (File temp : storage.recoveryTempDirectories()) {
            File[] staged = temp.isDirectory() ? stagedFiles(temp) : new File[0];
            if (excludedStagingPaths.isEmpty()) {
                snapshots.add(staged);
                continue;
            }
            java.util.List<File> eligible = new java.util.ArrayList<>(staged.length);
            for (File candidate : staged) {
                if (!excludedStagingPaths.contains(candidate.getAbsolutePath())) eligible.add(candidate);
            }
            snapshots.add(eligible.toArray(new File[0]));
        }
        return snapshots.toArray(new File[0][]);
    }

    StagedMediaRecoveryReport recover() {
        return recover(snapshot());
    }

    StagedMediaRecoveryReport recover(File[][] snapshots) {
        int recovered = 0;
        int preserved = 0;
        int duplicates = 0;
        Set<String> resolvedFileNames = new HashSet<>();
        for (File[] candidates : snapshots) {
            for (File candidate : candidates) {
                if (!candidate.isFile()) continue;
                if (DcamEncryptionJournal.isMarker(candidate)) {
                    logger.debug("Skipped encryption completion marker '"
                            + candidate.getAbsolutePath() + "'. It is recovery metadata, not media.");
                    continue;
                }
                Matcher matcher = CONTRACT_NAME.matcher(candidate.getName());
                if (!matcher.matches()) {
                    logger.warn("Skipped Temp file '" + candidate.getName()
                            + "'. Reason: filename does not match the DCAM media contract. "
                            + "File remains unchanged at '" + candidate.getAbsolutePath() + "'.", null);
                    continue;
                }
                DcamFileType type = type(matcher, candidate.getName());
                boolean encrypted = matcher.group(2) != null;
                DcamMediaFile mediaFile = new DcamMediaFile(
                        type, candidate.getName(), candidate, createdAt(candidate), encrypted);
                File target = storage.finalFile(mediaFile);
                removeIncompletePublicationCopies(candidate, target);
                if (target.exists()) {
                    duplicates++;
                    if (removeRedundantStaging(type, mediaFile, target)) {
                        resolvedFileNames.add(candidate.getName());
                        continue;
                    }
                    logger.warn("Skipped recovery for staged " + mediaKind(type) + " file "
                            + fileDescription(candidate) + ". Reason: final media already exists at '"
                            + target.getAbsolutePath() + "'. Staged duplicate differs from final media "
                            + "or could not be checked, so it remains in Temp and was not overwritten.",
                            null);
                    continue;
                }
                if (candidate.length() == 0L) {
                    if (removeEmptyStaging(mediaFile)) {
                        resolvedFileNames.add(candidate.getName());
                    } else {
                        preserved++;
                    }
                    continue;
                }
                boolean segmentedAesGcm;
                try {
                    segmentedAesGcm = encrypted
                            && SegmentedAesGcmMediaStore.hasFamilyMagic(candidate);
                } catch (IOException failure) {
                    preserved++;
                    logPreserved(type, candidate,
                            "Could not inspect encryption format: " + message(failure) + ".");
                    continue;
                }
                DcamMediaValidationResult validation;
                if (segmentedAesGcm) {
                    try (SegmentedAesGcmMediaStore store =
                                 SegmentedAesGcmMediaStore.openForRecovery(
                                         candidate, mediaEncryptionPassword.get())) {
                        if (store.size() == 0L) {
                            store.close();
                            if (removeEmptyStaging(mediaFile)) {
                                resolvedFileNames.add(candidate.getName());
                            } else {
                                preserved++;
                            }
                            continue;
                        }
                        if (!store.isFinalized()) {
                            if (usesFragmentedMp4Container(type)) {
                                logger.info("Found staged segmented-GCM " + mediaKind(type)
                                        + " file " + fileDescription(candidate)
                                        + ". Starting interrupted recording finalization.");
                                DcamInterruptedMp4Finalizer.Result mp4Finalization =
                                        interruptedMp4Finalizer.finalizeInterrupted(store);
                                if (mp4Finalization.finalized()) {
                                    logger.info("Finalized interrupted staged segmented-GCM "
                                            + mediaKind(type) + " file '" + candidate.getName()
                                            + "'. " + mp4Finalization.detail());
                                }
                            } else if (type == DcamFileType.AUDIO) {
                                finalizeInterruptedAdts(store);
                            }
                        }
                        validation = validator.validate(type, store);
                        if (!validation.playable()) {
                            preserved++;
                            logPreserved(type, candidate,
                                    "Playback validation failed after segmented-GCM recovery: "
                                            + validation.detail());
                            continue;
                        }
                        if (!store.isFinalized()) store.finish();
                    } catch (IOException failure) {
                        preserved++;
                        logger.error("Could not recover staged segmented-GCM "
                                + mediaKind(type) + " file " + fileDescription(candidate)
                                + ". File remains in Temp for later recovery. Reason: "
                                + message(failure) + ".", failure);
                        continue;
                    }
                } else if (encrypted) {
                    // Legacy encryption happens after capture writer closes. Without its journal,
                    // content may be plaintext, ciphertext, or a partial in-place transformation.
                    if (!DcamEncryptionJournal.isComplete(candidate)) {
                        preserved++;
                        logPreserved(type, candidate,
                                "Encryption completion was not recorded. Content may be plaintext, "
                                        + "ciphertext, or a partial in-place transformation.");
                        continue;
                    }
                    validation = DcamMediaValidationResult.accepted(
                            "Legacy encryption journal confirms transformation completed.");
                } else {
                    DcamInterruptedMp4Finalizer.Result mp4Finalization = null;
                    if (usesFragmentedMp4Container(type)) {
                        logger.info("Found staged " + mediaKind(type) + " file "
                                + fileDescription(candidate)
                                + ". Starting interrupted recording finalization.");
                        try {
                            mp4Finalization = interruptedMp4Finalizer.finalizeInterrupted(candidate);
                            if (mp4Finalization.finalized()) {
                                logger.info("Finalized interrupted staged " + mediaKind(type)
                                        + " file '" + candidate.getName() + "'. "
                                        + mp4Finalization.detail());
                            } else {
                                logger.info("Container finalization made no file change for staged "
                                        + mediaKind(type) + " file '" + candidate.getName() + "'. "
                                        + mp4Finalization.detail());
                            }
                        } catch (java.io.IOException failure) {
                            preserved++;
                            logger.error("Could not finalize staged " + mediaKind(type) + " file "
                                    + fileDescription(candidate) + ". Reason: " + message(failure)
                                    + ". File remains in Temp for later recovery.", failure);
                            continue;
                        }
                    }
                    validation = validator.validate(type, candidate);
                }
                if (!validation.playable()) {
                    preserved++;
                    logPreserved(type, candidate,
                            "Playback validation failed after finalization: " + validation.detail());
                    continue;
                }
                boolean createMd5 = segmentedAesGcm && shouldCreateMd5(type);
                try {
                    File published;
                    if (segmentedAesGcm) {
                        published = finalizer.finalizeCleanMedia(mediaFile);
                        finalizer.cleanupCleanMedia(mediaFile);
                    } else {
                        published = finalizer.finalizeMedia(mediaFile, shouldCreateMd5(type));
                    }
                    DcamEncryptionJournal.clear(mediaFile);
                    recovered++;
                    resolvedFileNames.add(candidate.getName());
                    logger.info("Recovered staged " + mediaKind(type) + " file '"
                            + candidate.getName() + "'. Published to '" + published.getAbsolutePath()
                            + "'. Validation: " + validation.detail());
                    if (createMd5) {
                        try {
                            md5RetryCallback.create(published);
                        } catch (Exception failure) {
                            logger.error("Video MD5 failed after staged media publication. Final media remains published at '"
                                    + published.getAbsolutePath() + "'.", failure);
                        }
                    }
                } catch (Exception failure) {
                    preserved++;
                    logger.error("Preserved staged " + mediaKind(type) + " file "
                            + fileDescription(candidate) + ". Reason: recovery publication failed: "
                            + message(failure) + ". File remains in Temp for support or later recovery.",
                            failure);
                }
            }
        }
        int deletedDirectories = storage.deleteEmptyRecoveryDateDirectories();
        if (deletedDirectories > 0) {
            logger.info("Removed empty staged-media date directories after recovery. Count: "
                    + deletedDirectories + ".");
        }
        return new StagedMediaRecoveryReport(recovered, preserved, duplicates, resolvedFileNames);
    }

    private static void finalizeInterruptedAdts(DcamRandomAccessMedia media) throws IOException {
        long length = media.size();
        long offset = 0L;
        while (offset < length) {
            long remaining = length - offset;
            if (remaining < 7L) break;
            byte[] header = new byte[7];
            media.position(offset);
            ByteBuffer bytes = ByteBuffer.wrap(header);
            while (bytes.hasRemaining()) {
                int read = media.read(bytes);
                if (read < 0) throw new IOException("Truncated AAC frame header.");
                if (read == 0) throw new IOException("AAC frame header read stopped.");
            }
            int first = header[0] & 0xff;
            int second = header[1] & 0xff;
            if (first != 0xff || (second & 0xf6) != 0xf0) {
                throw new IOException("AAC frame header is invalid after an authenticated frame.");
            }
            int headerBytes = (second & 1) == 0 ? 9 : 7;
            int frameLength = ((header[3] & 3) << 11)
                    | ((header[4] & 0xff) << 3)
                    | ((header[5] & 0xe0) >>> 5);
            if (frameLength < headerBytes || frameLength > remaining) break;
            offset += frameLength;
        }
        if (offset <= 0L) {
            throw new IOException("No complete AAC frame is available for recovery.");
        }
        if (offset < length) media.truncate(offset);
    }
    private boolean removeEmptyStaging(DcamMediaFile mediaFile) {
        File staged = mediaFile.getFile();
        try {
            if (!Files.deleteIfExists(staged.toPath()) && staged.exists()) return false;
            storage.deleteEmptyStagingDateDirectory(mediaFile);
            logger.info("Removed empty staged " + mediaKind(mediaFile.getType()) + " file '"
                    + staged.getName() + "'. No media data was available to recover.");
            return true;
        } catch (IOException failure) {
            logger.error("Could not remove empty staged " + mediaKind(mediaFile.getType())
                    + " file " + fileDescription(staged)
                    + ". File remains in Temp. Reason: " + message(failure) + ".", failure);
            return false;
        }
    }

    private boolean removeRedundantStaging(
            DcamFileType type, DcamMediaFile mediaFile, File target) {
        File staged = mediaFile.getFile();
        try {
            boolean empty = staged.length() == 0L;
            if (!empty && (!type.isAudio() || !sameContent(staged, target))) {
                return false;
            }
            if (!Files.deleteIfExists(staged.toPath()) && staged.exists()) return false;
            storage.deleteEmptyStagingDateDirectory(mediaFile);
            logger.info("Removed redundant staged " + mediaKind(type) + " file '"
                    + staged.getName() + "'. Reason: " + (empty
                    ? "staged file contained no media data"
                    : "staged audio exactly matched final media") + ". Final media remains at '"
                    + target.getAbsolutePath() + "'.");
            return true;
        } catch (IOException failure) {
            logger.warn("Could not verify or remove redundant staged " + mediaKind(type)
                    + " file " + fileDescription(staged) + ". Final media remains at '"
                    + target.getAbsolutePath() + "'. Reason: " + message(failure) + ".", failure);
            return false;
        }
    }

    private static boolean sameContent(File first, File second) throws IOException {
        if (!first.isFile() || !second.isFile() || first.length() != second.length()) return false;
        try (BufferedInputStream firstInput =
                     new BufferedInputStream(new FileInputStream(first));
             BufferedInputStream secondInput =
                     new BufferedInputStream(new FileInputStream(second))) {
            byte[] firstBuffer = new byte[64 * 1024];
            byte[] secondBuffer = new byte[firstBuffer.length];
            while (true) {
                int firstRead = firstInput.read(firstBuffer);
                int secondRead = secondInput.read(secondBuffer);
                if (firstRead != secondRead) return false;
                if (firstRead < 0) return true;
                for (int index = 0; index < firstRead; index++) {
                    if (firstBuffer[index] != secondBuffer[index]) return false;
                }
            }
        }
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

    private void removeIncompletePublicationCopies(File staging, File target) {
        File parent = target.getParentFile();
        if (!staging.isFile() || parent == null || !parent.isDirectory()) return;
        String prefix = "." + target.getName() + ".publishing-";
        File[] partials = parent.listFiles(file -> file.isFile() && file.getName().startsWith(prefix));
        if (partials == null) return;
        for (File partial : partials) {
            try {
                if (java.nio.file.Files.deleteIfExists(partial.toPath())) {
                    logger.info("Removed incomplete publication copy '"
                            + partial.getAbsolutePath() + "' before recovering staged file '"
                            + staging.getName() + "'.");
                }
            } catch (Exception failure) {
                logger.warn("Could not remove incomplete publication copy '"
                        + partial.getAbsolutePath() + "' before recovering staged file '"
                        + staging.getName() + "'. Recovery will continue. Reason: "
                        + message(failure) + ".", failure);
            }
        }
    }

    private void logPreserved(DcamFileType type, File candidate, String reason) {
        logger.warn("Preserved staged " + mediaKind(type) + " file "
                + fileDescription(candidate) + ". Reason: " + reason
                + " File remains in Temp for support or later recovery.", null);
    }

    private static boolean usesFragmentedMp4Container(DcamFileType type) {
        return type.usesFragmentedMp4Container();
    }

    private boolean shouldCreateMd5(DcamFileType type) {
        return type.isVideo() && createVideoMd5 != null && createVideoMd5.getAsBoolean();
    }

    private static String mediaKind(DcamFileType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static String fileDescription(File file) {
        return "'" + file.getName() + "' (" + file.length() + " bytes at '"
                + file.getAbsolutePath() + "')";
    }

    @FunctionalInterface
    interface Md5RetryCallback {
        void create(File file) throws IOException;
    }

    private static void writeMd5Immediately(File file) throws IOException {
        DcamMd5Sidecar.write(file, DcamMd5RetryProcessor.digest(file));
    }

    private static String message(Throwable failure) {
        String detail = failure.getMessage();
        return detail == null || detail.isBlank()
                ? failure.getClass().getSimpleName() : detail;
    }

    private static DcamFileType type(Matcher matcher, String name) {
        String extension = matcher.group(3).toLowerCase(Locale.ROOT);
        if ("jpg".equals(extension)) return DcamFileType.IMAGE;
        if ("aac".equals(extension)) return DcamFileType.AUDIO;
        if ("m4a".equals(extension)) return DcamFileType.AUDIO_M4A;
        return matcher.group(1) == null ? DcamFileType.VIDEO : DcamFileType.IMP;
    }

    private static LocalDateTime createdAt(File file) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
    }
}
