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
            "^DCAM_[^_]+_[^_]+_\\d{8}_\\d{6}(_IMP)?(_enc)?\\.(mp4|jpg|aac|m4a)$",
            Pattern.CASE_INSENSITIVE);
    private static final String FILE_NOUN = " file ";
    private static final String QUOTED_FILE_NOUN = " file '";

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

    // Each argument is a distinct recovery collaborator used by tests and production composition.
    @SuppressWarnings("java:S107")
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
        RecoveryState state = new RecoveryState();
        for (File[] candidates : snapshots) {
            for (File candidate : candidates) {
                recoverCandidate(candidate, state);
            }
        }
        int deletedDirectories = storage.deleteEmptyRecoveryDateDirectories();
        if (deletedDirectories > 0) {
            logger.info("Removed empty staged-media date directories after recovery. Count: "
                    + deletedDirectories + ".");
        }
        return state.report();
    }

    private void recoverCandidate(File candidate, RecoveryState state) {
        if (!candidate.isFile()) return;
        if (DcamEncryptionJournal.isMarker(candidate)) {
            logger.debug("Skipped encryption completion marker '"
                    + candidate.getAbsolutePath() + "'. It is recovery metadata, not media.");
            return;
        }
        Matcher matcher = CONTRACT_NAME.matcher(candidate.getName());
        if (!matcher.matches()) {
            logger.warn("Skipped Temp file '" + candidate.getName()
                    + "'. Reason: filename does not match the DCAM media contract. "
                    + "File remains unchanged at '" + candidate.getAbsolutePath() + "'.", null);
            return;
        }
        DcamFileType type = type(matcher);
        DcamMediaFile mediaFile = new DcamMediaFile(
                type, candidate.getName(), candidate, createdAt(candidate), matcher.group(2) != null);
        File target = storage.finalFile(mediaFile);
        removeIncompletePublicationCopies(candidate, target);
        if (target.exists()) {
            recoverDuplicateCandidate(type, mediaFile, target, state);
            return;
        }
        if (candidate.length() == 0L) {
            recoverEmptyCandidate(mediaFile, state);
            return;
        }
        recoverNonEmptyCandidate(type, mediaFile, state);
    }

    private void recoverDuplicateCandidate(
            DcamFileType type, DcamMediaFile mediaFile, File target, RecoveryState state) {
        File candidate = mediaFile.getFile();
        state.duplicates++;
        if (removeRedundantStaging(type, mediaFile, target)) {
            state.markResolved(candidate);
            return;
        }
        logger.warn("Skipped recovery for staged " + mediaKind(type) + FILE_NOUN
                + fileDescription(candidate) + ". Reason: final media already exists at '"
                + target.getAbsolutePath() + "'. Staged duplicate differs from final media "
                + "or could not be checked, so it remains in Temp and was not overwritten.", null);
    }

    private void recoverEmptyCandidate(DcamMediaFile mediaFile, RecoveryState state) {
        if (removeEmptyStaging(mediaFile)) {
            state.markResolved(mediaFile.getFile());
        } else {
            state.preserved++;
        }
    }

    private void recoverNonEmptyCandidate(
            DcamFileType type, DcamMediaFile mediaFile, RecoveryState state) {
        File candidate = mediaFile.getFile();
        boolean segmentedAesGcm;
        try {
            segmentedAesGcm = mediaFile.isEncrypted()
                    && SegmentedAesGcmMediaStore.hasFamilyMagic(candidate);
        } catch (IOException failure) {
            state.preserved++;
            logPreserved(type, candidate,
                    "Could not inspect encryption format: " + message(failure) + ".");
            return;
        }
        if (segmentedAesGcm) {
            recoverSegmentedCandidate(type, mediaFile, state);
            return;
        }
        if (mediaFile.isEncrypted()) {
            recoverLegacyEncryptedCandidate(type, mediaFile, state);
            return;
        }
        recoverPlainCandidate(type, mediaFile, state);
    }

    private void recoverSegmentedCandidate(
            DcamFileType type, DcamMediaFile mediaFile, RecoveryState state) {
        File candidate = mediaFile.getFile();
        DcamMediaValidationResult validation;
        try (SegmentedAesGcmMediaStore store = SegmentedAesGcmMediaStore.openForRecovery(
                candidate, mediaEncryptionPassword.get())) {
            if (store.size() == 0L) {
                store.close();
                recoverEmptyCandidate(mediaFile, state);
                return;
            }
            finalizeSegmentedContainerIfNeeded(type, candidate, store);
            validation = validator.validate(type, store);
            if (!validation.playable()) {
                state.preserved++;
                logPreserved(type, candidate,
                        "Playback validation failed after segmented-GCM recovery: "
                                + validation.detail());
                return;
            }
            if (!store.isFinalized()) store.finish();
        } catch (IOException failure) {
            state.preserved++;
            logger.error("Could not recover staged segmented-GCM " + mediaKind(type) + FILE_NOUN
                    + fileDescription(candidate) + ". File remains in Temp for later recovery. Reason: "
                    + message(failure) + ".", failure);
            return;
        }
        publishRecoveredCandidate(type, mediaFile, true, validation, state);
    }

    private void finalizeSegmentedContainerIfNeeded(
            DcamFileType type, File candidate, SegmentedAesGcmMediaStore store) throws IOException {
        if (store.isFinalized()) return;
        if (usesFragmentedMp4Container(type)) {
            logger.info("Found staged segmented-GCM " + mediaKind(type) + FILE_NOUN
                    + fileDescription(candidate) + ". Starting interrupted recording finalization.");
            DcamInterruptedMp4Finalizer.Result mp4Finalization =
                    interruptedMp4Finalizer.finalizeInterrupted(store);
            if (mp4Finalization.finalized()) {
                logger.info("Finalized interrupted staged segmented-GCM " + mediaKind(type)
                        + QUOTED_FILE_NOUN + candidate.getName() + "'. "
                        + mp4Finalization.detail());
            }
        } else if (type == DcamFileType.AUDIO) {
            finalizeInterruptedAdts(store);
        }
    }

    private void recoverLegacyEncryptedCandidate(
            DcamFileType type, DcamMediaFile mediaFile, RecoveryState state) {
        File candidate = mediaFile.getFile();
        // Legacy encryption happens after capture writer closes. Without its journal,
        // content may be plaintext, ciphertext, or a partial in-place transformation.
        if (!DcamEncryptionJournal.isComplete(candidate)) {
            state.preserved++;
            logPreserved(type, candidate,
                    "Encryption completion was not recorded. Content may be plaintext, "
                            + "ciphertext, or a partial in-place transformation.");
            return;
        }
        publishRecoveredCandidate(type, mediaFile, false, DcamMediaValidationResult.accepted(
                "Legacy encryption journal confirms transformation completed."), state);
    }

    private void recoverPlainCandidate(
            DcamFileType type, DcamMediaFile mediaFile, RecoveryState state) {
        File candidate = mediaFile.getFile();
        if (usesFragmentedMp4Container(type) && !finalizePlainContainer(type, candidate, state)) {
            return;
        }
        recoverValidatedCandidate(type, mediaFile, false, validator.validate(type, candidate), state);
    }

    private boolean finalizePlainContainer(DcamFileType type, File candidate, RecoveryState state) {
        logger.info("Found staged " + mediaKind(type) + FILE_NOUN + fileDescription(candidate)
                + ". Starting interrupted recording finalization.");
        try {
            DcamInterruptedMp4Finalizer.Result mp4Finalization =
                    interruptedMp4Finalizer.finalizeInterrupted(candidate);
            logPlainContainerFinalization(type, candidate, mp4Finalization);
            return true;
        } catch (IOException failure) {
            state.preserved++;
            logger.error("Could not finalize staged " + mediaKind(type) + FILE_NOUN
                    + fileDescription(candidate) + ". Reason: " + message(failure)
                    + ". File remains in Temp for later recovery.", failure);
            return false;
        }
    }

    private void logPlainContainerFinalization(
            DcamFileType type, File candidate, DcamInterruptedMp4Finalizer.Result finalization) {
        if (finalization.finalized()) {
            logger.info("Finalized interrupted staged " + mediaKind(type) + QUOTED_FILE_NOUN
                    + candidate.getName() + "'. " + finalization.detail());
        } else {
            logger.info("Container finalization made no file change for staged " + mediaKind(type)
                    + QUOTED_FILE_NOUN + candidate.getName() + "'. " + finalization.detail());
        }
    }

    private void recoverValidatedCandidate(
            DcamFileType type, DcamMediaFile mediaFile, boolean segmentedAesGcm,
            DcamMediaValidationResult validation, RecoveryState state) {
        if (!validation.playable()) {
            state.preserved++;
            String stage = segmentedAesGcm ? "segmented-GCM recovery" : "finalization";
            logPreserved(type, mediaFile.getFile(),
                    "Playback validation failed after " + stage + ": " + validation.detail());
            return;
        }
        publishRecoveredCandidate(type, mediaFile, segmentedAesGcm, validation, state);
    }

    private void publishRecoveredCandidate(
            DcamFileType type, DcamMediaFile mediaFile, boolean segmentedAesGcm,
            DcamMediaValidationResult validation, RecoveryState state) {
        File candidate = mediaFile.getFile();
        boolean createMd5 = segmentedAesGcm && shouldCreateMd5(type);
        try {
            File published = segmentedAesGcm
                    ? publishCleanRecoveredMedia(mediaFile)
                    : finalizer.finalizeMedia(mediaFile, shouldCreateMd5(type));
            DcamEncryptionJournal.clear(mediaFile);
            state.recovered++;
            state.markResolved(candidate);
            logger.info("Recovered staged " + mediaKind(type) + QUOTED_FILE_NOUN
                    + candidate.getName() + "'. Published to '" + published.getAbsolutePath()
                    + "'. Validation: " + validation.detail());
            if (createMd5) createMd5AfterPublication(published);
        } catch (Exception failure) {
            state.preserved++;
            logger.error("Preserved staged " + mediaKind(type) + FILE_NOUN
                    + fileDescription(candidate) + ". Reason: recovery publication failed: "
                    + message(failure) + ". File remains in Temp for support or later recovery.",
                    failure);
        }
    }

    private File publishCleanRecoveredMedia(DcamMediaFile mediaFile) throws IOException {
        File published = finalizer.finalizeCleanMedia(mediaFile);
        finalizer.cleanupCleanMedia(mediaFile);
        return published;
    }

    private void createMd5AfterPublication(File published) {
        try {
            md5RetryCallback.create(published);
        } catch (Exception failure) {
            logger.error("Video MD5 failed after staged media publication. Final media remains published at '"
                    + published.getAbsolutePath() + "'.", failure);
        }
    }

    private static void finalizeInterruptedAdts(DcamRandomAccessMedia media) throws IOException {
        long length = media.size();
        long recoveredLength = completeAdtsFrameLength(media, length);
        if (recoveredLength <= 0L) {
            throw new IOException("No complete AAC frame is available for recovery.");
        }
        if (recoveredLength < length) media.truncate(recoveredLength);
    }

    private static long completeAdtsFrameLength(DcamRandomAccessMedia media, long length)
            throws IOException {
        long offset = 0L;
        while (offset < length) {
            int frameLength = completeAdtsFrameLength(media, offset, length);
            if (frameLength == 0) return offset;
            offset += frameLength;
        }
        return offset;
    }

    private static int completeAdtsFrameLength(DcamRandomAccessMedia media, long offset, long length)
            throws IOException {
        long remaining = length - offset;
        if (remaining < 7L) return 0;
        byte[] header = new byte[7];
        readAdtsHeader(media, offset, header);
        int first = header[0] & 0xff;
        int second = header[1] & 0xff;
        if (first != 0xff || (second & 0xf6) != 0xf0) {
            throw new IOException("AAC frame header is invalid after an authenticated frame.");
        }
        int headerBytes = (second & 1) == 0 ? 9 : 7;
        int frameLength = ((header[3] & 3) << 11)
                | ((header[4] & 0xff) << 3)
                | ((header[5] & 0xe0) >>> 5);
        return frameLength < headerBytes || frameLength > remaining ? 0 : frameLength;
    }

    private static void readAdtsHeader(DcamRandomAccessMedia media, long offset, byte[] header)
            throws IOException {
        media.position(offset);
        ByteBuffer bytes = ByteBuffer.wrap(header);
        while (bytes.hasRemaining()) {
            int read = media.read(bytes);
            if (read < 0) throw new IOException("Truncated AAC frame header.");
            if (read == 0) throw new IOException("AAC frame header read stopped.");
        }
    }

    private boolean removeEmptyStaging(DcamMediaFile mediaFile) {
        File staged = mediaFile.getFile();
        try {
            if (!Files.deleteIfExists(staged.toPath()) && staged.exists()) return false;
            storage.deleteEmptyStagingDateDirectory(mediaFile);
            logger.info("Removed empty staged " + mediaKind(mediaFile.getType()) + QUOTED_FILE_NOUN
                    + staged.getName() + "'. No media data was available to recover.");
            return true;
        } catch (IOException failure) {
            logger.error("Could not remove empty staged " + mediaKind(mediaFile.getType())
                    + FILE_NOUN + fileDescription(staged)
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
            logger.info("Removed redundant staged " + mediaKind(type) + QUOTED_FILE_NOUN
                    + staged.getName() + "'. Reason: " + (empty
                    ? "staged file contained no media data"
                    : "staged audio exactly matched final media") + ". Final media remains at '"
                    + target.getAbsolutePath() + "'.");
            return true;
        } catch (IOException failure) {
            logger.warn("Could not verify or remove redundant staged " + mediaKind(type)
                    + FILE_NOUN + fileDescription(staged) + ". Final media remains at '"
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
        logger.warn("Preserved staged " + mediaKind(type) + FILE_NOUN
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

    private static DcamFileType type(Matcher matcher) {
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

    private static final class RecoveryState {
        private int recovered;
        private int preserved;
        private int duplicates;
        private final Set<String> resolvedFileNames = new HashSet<>();

        private void markResolved(File candidate) {
            resolvedFileNames.add(candidate.getName());
        }

        private StagedMediaRecoveryReport report() {
            return new StagedMediaRecoveryReport(
                    recovered, preserved, duplicates, resolvedFileNames);
        }
    }
}
