package com.dvid.dcam.platform.storage;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageMode;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class DcamStorage implements
        com.dvid.dcam.feature.storage.application.port.ActiveStorageSource,
        com.dvid.dcam.feature.storage.application.port.ActiveStoragePolicyGateway {
    private static final String MEDIA_DIRECTORY = "Media";
    private static final String EXTERNAL_PREPARING_MESSAGE =
            "SD card is being prepared. Please wait.";
    private static final String EXTERNAL_UNAVAILABLE_MESSAGE = "SD card unavailable.";

    private final StorageMode storageMode;
    private volatile MediaPartitionLocation requestedMode;
    private final Context context;
    private final File internalRoot;
    private final AtomicReference<List<File>> externalRoots;
    private volatile boolean externalRootsStale;
    private boolean retryIncompleteExternalRoots;
    private final CaptureStorageCapacityPolicy capacityPolicy;
    private final BooleanSupplier removableVolumePreparing;
    private final Function<File, String> externalStorageState;
    private final ToLongFunction<File> usableSpace;
    private volatile MediaPartitionLocation resolvedMode;
    private volatile File root;
    private volatile MediaPartitionLocation recordingMode;
    private volatile File recordingRoot;
    private volatile String preparedStagingDirectoryPath;

    public DcamStorage(File root) {
        this(null, StorageMode.APP_DATA, MediaPartitionLocation.INTERNAL, MediaPartitionLocation.INTERNAL, root, root,
                List.of(), new CaptureStorageCapacityPolicy());
    }

    public DcamStorage(MediaPartitionLocation mode, File root) {
        this(null, StorageMode.APP_DATA, mode, mode == MediaPartitionLocation.AUTO ? MediaPartitionLocation.INTERNAL : mode,
                root, root, List.of(), new CaptureStorageCapacityPolicy());
    }

    public DcamStorage(StorageMode storageMode, MediaPartitionLocation mode, File root) {
        this(null, storageMode, mode, mode == MediaPartitionLocation.AUTO ? MediaPartitionLocation.INTERNAL : mode,
                root, root, List.of(), new CaptureStorageCapacityPolicy());
    }

    public DcamStorage(
            MediaPartitionLocation requestedMode,
            MediaPartitionLocation resolvedMode,
            File internalRoot,
            File root,
            CaptureStorageCapacityPolicy capacityPolicy) {
        this(null, StorageMode.APP_DATA, requestedMode, resolvedMode, internalRoot, root, List.of(), capacityPolicy);
    }

    @SuppressWarnings("java:S107") // Maps production dependencies to the shared initializer.
    private DcamStorage(
            Context context,
            StorageMode storageMode,
            MediaPartitionLocation requestedMode,
            MediaPartitionLocation resolvedMode,
            File internalRoot,
            File root,
            List<File> externalRoots,
            CaptureStorageCapacityPolicy capacityPolicy) {
        this(context, storageMode, requestedMode, resolvedMode, internalRoot, root, externalRoots,
                capacityPolicy, context == null ? () -> false : () -> hasPreparingRemovableVolume(context),
                Environment::getExternalStorageState, File::getUsableSpace);
    }

    DcamStorage(
            MediaPartitionLocation requestedMode,
            File internalRoot,
            List<File> externalRoots,
            BooleanSupplier removableVolumePreparing) {
        this(null, StorageMode.APP_DATA, requestedMode, MediaPartitionLocation.INTERNAL,
                internalRoot, internalRoot, externalRoots, new CaptureStorageCapacityPolicy(),
                removableVolumePreparing, Environment::getExternalStorageState, File::getUsableSpace);
    }

    DcamStorage(
            MediaPartitionLocation requestedMode,
            File internalRoot,
            List<File> externalRoots,
            BooleanSupplier removableVolumePreparing,
            Function<File, String> externalStorageState,
            ToLongFunction<File> usableSpace) {
        this(null, StorageMode.APP_DATA, requestedMode, MediaPartitionLocation.INTERNAL,
                internalRoot, internalRoot, externalRoots, new CaptureStorageCapacityPolicy(),
                removableVolumePreparing, externalStorageState, usableSpace);
    }

    @SuppressWarnings("java:S107") // Keeps storage policy dependencies explicit for deterministic tests.
    private DcamStorage(
            Context context,
            StorageMode storageMode,
            MediaPartitionLocation requestedMode,
            MediaPartitionLocation resolvedMode,
            File internalRoot,
            File root,
            List<File> externalRoots,
            CaptureStorageCapacityPolicy capacityPolicy,
            BooleanSupplier removableVolumePreparing,
            Function<File, String> externalStorageState,
            ToLongFunction<File> usableSpace) {
        this.context = context;
        this.storageMode = storageMode == null ? StorageMode.APP_DATA : storageMode;
        this.requestedMode = requestedMode;
        this.resolvedMode = resolvedMode;
        this.internalRoot = internalRoot;
        this.root = root;
        this.externalRoots = new AtomicReference<>(List.copyOf(externalRoots));
        this.externalRootsStale = context != null;
        this.capacityPolicy = capacityPolicy;
        this.removableVolumePreparing = removableVolumePreparing;
        this.externalStorageState = externalStorageState;
        this.usableSpace = usableSpace;
    }

    public static DcamStorage from(Context context) {
        return from(context, StorageMode.from(BuildConfig.DEFAULT_STORAGE_MODE), MediaPartitionLocation.AUTO);
    }

    public static DcamStorage from(Context context, MediaPartitionLocation requestedMode) {
        return from(context, StorageMode.APP_DATA, requestedMode);
    }

    public static DcamStorage from(Context context, StorageMode storageMode,
            MediaPartitionLocation requestedMode) {
        File primaryRoot = Environment.getExternalStorageDirectory();
        File internalRoot = primaryRoot == null
                ? context.getFilesDir()
                : appSpecificFilesRoot(primaryRoot, context.getPackageName());
        DcamStorage storage = new DcamStorage(context.getApplicationContext(), storageMode,
                requestedMode, MediaPartitionLocation.INTERNAL, internalRoot, internalRoot,
                List.of(), new CaptureStorageCapacityPolicy());
        storage.prepareStagingDirectory(currentLocalDateTime());
        return storage;
    }

    static File appSpecificFilesRoot(File volumeRoot, String packageName) {
        return new File(new File(new File(new File(
                volumeRoot, "Android"), "data"), packageName), "files");
    }

    public MediaPartitionLocation getRequestedMode() { return requestedMode; }
    public MediaPartitionLocation getMode() { return resolvedMode; }

    @Override public synchronized void changeRequestedLocation(MediaPartitionLocation location) {
        requestedMode = location == null ? MediaPartitionLocation.AUTO : location;
        resolveRootForNextCapture();
        preparedStagingDirectoryPath = null;
        prepareStagingDirectory(currentLocalDateTime());
    }
    public boolean isFallback() { return requestedMode != MediaPartitionLocation.INTERNAL && resolvedMode == MediaPartitionLocation.INTERNAL; }
    public boolean isPublicDcim() { return storageMode == StorageMode.PUBLIC_DCIM; }

    @Override public synchronized StorageVolumeStatus activeStorageVolume() {
        return activeStorageVolume(
                CaptureStorageCapacityPolicy.MIN_NEW_CAPTURE_AVAILABLE_BYTES, false);
    }

    @Override public synchronized StorageVolumeStatus activeStorageVolume(
            long requiredBytes, boolean preserveRecordingVolume) {
        refreshExternalRoots();
        if (preserveRecordingVolume && recordingRoot != null && recordingMode != null) {
            return storageVolumeStatus(candidate(recordingMode, recordingRoot,
                    recordingMode == MediaPartitionLocation.INTERNAL));
        }
        if (requestedMode == MediaPartitionLocation.INTERNAL) {
            return storageVolumeStatus(MediaPartitionLocation.INTERNAL, internalRoot);
        }
        DcamStorageCandidate internal = candidate(
                MediaPartitionLocation.INTERNAL, internalRoot, true);
        List<DcamStorageCandidate> external = new ArrayList<>();
        for (File externalRoot : externalRoots()) {
            external.add(candidate(MediaPartitionLocation.EXTERNAL, externalRoot, false));
        }
        if (requestedMode == MediaPartitionLocation.EXTERNAL) {
            DcamStorageCandidate selected = bestExternalCandidate(external, requiredBytes);
            return selected == null
                    ? new StorageVolumeStatus(
                            MediaPartitionLocation.EXTERNAL, false, 0L, 0L)
                    : storageVolumeStatus(selected);
        }
        DcamStorageResolution resolution = new DcamStorageRootResolver(capacityPolicy).resolve(
                requestedMode, internal, external, requiredBytes);
        return storageVolumeStatus(resolution.getResolvedMode(), resolution.getRoot());
    }

    private DcamStorageCandidate bestExternalCandidate(
            List<DcamStorageCandidate> candidates, long requiredBytes) {
        DcamStorageCandidate best = null;
        for (DcamStorageCandidate candidate : candidates) {
            if (candidate.check(capacityPolicy, requiredBytes).isReady()) return candidate;
            if (best == null || candidate.getAvailableBytes() > best.getAvailableBytes()) {
                best = candidate;
            }
        }
        return best;
    }

    private StorageVolumeStatus storageVolumeStatus(
            MediaPartitionLocation mode, File volumeRoot) {
        boolean available = volumeRoot != null
                && (volumeRoot.exists() || volumeRoot.mkdirs());
        return new StorageVolumeStatus(mode, available,
                available ? volumeRoot.getTotalSpace() : 0L,
                available ? usableSpace.applyAsLong(volumeRoot) : 0L);
    }

    private static StorageVolumeStatus storageVolumeStatus(DcamStorageCandidate candidate) {
        boolean available = candidate != null && candidate.isMounted();
        File volumeRoot = candidate == null ? null : candidate.getRoot();
        return new StorageVolumeStatus(
                candidate == null ? MediaPartitionLocation.EXTERNAL : candidate.getMode(),
                available,
                available ? volumeRoot.getTotalSpace() : 0L,
                available ? candidate.getAvailableBytes() : 0L);
    }
    public File captureRoot() { return root; }
    public File rootDirectory() { return new File(root, MEDIA_DIRECTORY); }
    public File tempDirectory() { return new File(root, "Temp"); }

    public synchronized List<File> mediaRootDirectories() {
        refreshExternalRoots();
        List<File> directories = new ArrayList<>();
        addDistinct(directories, new File(internalRoot, MEDIA_DIRECTORY));
        addDistinct(directories, rootDirectory());
        for (File externalRoot : externalRoots()) {
            addDistinct(directories, new File(externalRoot, MEDIA_DIRECTORY));
        }
        return List.copyOf(directories);
    }

    public synchronized List<File> browsableMediaRootDirectories() {
        List<File> roots = mediaRootDirectories();
        List<File> browsable = new ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            File mediaRoot = roots.get(index);
            if (index == 0 || Environment.MEDIA_MOUNTED.equals(
                    externalStorageState.apply(mediaRoot.getParentFile()))) {
                browsable.add(mediaRoot);
            }
        }
        return List.copyOf(browsable);
    }

    public File mediaRootDirectory(String location) {
        List<File> roots = browsableMediaRootDirectories();
        int index;
        try {
            index = mediaRootIndex(location);
        } catch (NumberFormatException error) {
            throw new SecurityException("Unsupported media storage", error);
        }
        if (index < 0 || index >= roots.size()) {
            throw new SecurityException("Unsupported media storage");
        }
        return roots.get(index);
    }

    private static int mediaRootIndex(String location) {
        if ("Internal".equals(location)) return 0;
        if ("External".equals(location)) return 1;
        if (!location.startsWith("External ")) return -1;
        return Integer.parseInt(location.substring("External ".length()));
    }

    public void ensureFolders() {
        for (DcamFileType type : DcamFileType.values())
            new File(rootDirectory(), type.getFolder()).mkdirs();
        tempDirectory().mkdirs();
    }

    public File outputFile(DcamFileType type, String cameraId, String fileUserId,
                           LocalDateTime at, boolean encrypted) {
        return prepareFile(mediaFile(type, cameraId, fileUserId, at, encrypted));
    }

    public synchronized DcamMediaFile mediaFile(
            DcamFileType type, String cameraId, String fileUserId,
            LocalDateTime at, boolean encrypted) {
        boolean recording = type == DcamFileType.VIDEO || type == DcamFileType.IMP;
        File selectedRoot = recording && recordingRoot != null ? recordingRoot : root;
        File dir = stagesBeforePublication(type)
                ? new File(new File(selectedRoot, "Temp"), DcamFileName.dateFolder(at))
                : new File(new File(selectedRoot, MEDIA_DIRECTORY), type.getFolder());
        String fileName = DcamFileName.build(type, cameraId, fileUserId, at, encrypted);
        return new DcamMediaFile(type, fileName, new File(dir, fileName), at, encrypted);
    }


    boolean hasFinalMediaFile(DcamMediaFile mediaFile) {
        return finalFile(mediaFile).isFile();
    }

    boolean hasPublishedFile(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.startsWith("DCAM_")
                || !fileName.equals(new File(fileName).getName())) return false;
        for (File mediaRoot : mediaRootDirectories()) {
            if (containsPublishedFileForRoot(mediaRoot, fileName)) return true;
        }
        return false;
    }

    private static boolean containsPublishedFileForRoot(File mediaRoot, String fileName) {
        for (DcamFileType type : DcamFileType.values()) {
            if (containsPublishedFile(new File(mediaRoot, type.getFolder()), fileName)) return true;
        }
        return false;
    }

    private static boolean containsPublishedFile(File typeRoot, String fileName) {
        if (new File(typeRoot, fileName).isFile()) return true;
        File[] datedDirectories = typeRoot.listFiles(File::isDirectory);
        if (datedDirectories == null) return false;
        for (File dateDirectory : datedDirectories) {
            if (new File(dateDirectory, fileName).isFile()) return true;
        }
        return false;
    }


    public DcamMediaFile durableAudioMediaFile(
            String cameraId, String fileUserId, LocalDateTime at, boolean encrypted)
            throws IOException {
        return durableAudioMediaFile(
                DcamFileType.AUDIO_M4A, cameraId, fileUserId, at, encrypted);
    }

    public DcamMediaFile durableAudioMediaFile(
            DcamFileType type, String cameraId, String fileUserId, LocalDateTime at,
            boolean encrypted) throws IOException {
        if (type == null || !type.isAudio()) {
            throw new IllegalArgumentException("Audio media type required");
        }
        DcamMediaFile staging = mediaFile(type, cameraId, fileUserId, at, encrypted);
        File parent = staging.getFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Cannot create durable audio staging directory: " + parent);
        }
        return staging;
    }

    public synchronized File prepareFile(DcamMediaFile mediaFile) {
        File parent = mediaFile.getFile().getParentFile();
        if (parent != null && !isPreparedStagingDirectory(parent)
                && (parent.mkdirs() || parent.isDirectory())) {
            preparedStagingDirectoryPath = parent.getAbsolutePath();
        }
        return mediaFile.getFile();
    }


    public File finalFile(DcamMediaFile mediaFile) {
        File marker = targetMarker(mediaFile.getFile());
        if (marker.isFile()) return validatedMarkedTarget(mediaFile, marker);
        return finalFile(finalMediaRoot(mediaFile.getFile().getParentFile()), mediaFile);
    }

    private File finalMediaRoot(File stagingParent) {
        if (stagingParent == null) return rootDirectory();
        if (isDateStagingDirectory(stagingParent)) {
            return new File(stagingParent.getParentFile().getParentFile(), MEDIA_DIRECTORY);
        }
        if ("Temp".equals(stagingParent.getName())) {
            return new File(stagingParent.getParentFile(), MEDIA_DIRECTORY);
        }
        return rootDirectory();
    }

    private static File finalFile(File mediaRoot, DcamMediaFile mediaFile) {
        File typeDirectory = new File(mediaRoot, mediaFile.getType().getFolder());
        return new File(new File(typeDirectory, finalDateFolder(mediaFile)),
                mediaFile.getFileName());
    }

    private static String finalDateFolder(DcamMediaFile mediaFile) {
        File stagingDirectory = mediaFile.getFile().getParentFile();
        return isDateStagingDirectory(stagingDirectory)
                ? stagingDirectory.getName()
                : DcamFileName.dateFolder(mediaFile.getCreatedAt());
    }

    public List<File> recoveryTempDirectories() {
        refreshExternalRoots();
        List<File> directories = new ArrayList<>();
        addDistinct(directories, new File(internalRoot, "Temp"));
        File privateRoot = context == null ? internalRoot : context.getFilesDir();
        addDistinct(directories, new File(privateRoot, "DurableAudioTemp"));
        for (File externalRoot : externalRoots()) {
            addDistinct(directories, new File(externalRoot, "Temp"));
        }
        return List.copyOf(directories);
    }

    void deleteEmptyStagingDateDirectory(DcamMediaFile mediaFile) {
        File dateDirectory = mediaFile.getFile().getParentFile();
        if (!isDateStagingDirectory(dateDirectory)
                || isPreparedStagingDirectory(dateDirectory)) return;
        try {
            Files.deleteIfExists(dateDirectory.toPath());
        } catch (IOException ignored) {
            // Best-effort cleanup; recovery can retry a directory that remains inaccessible or non-empty.
        }
    }

    int deleteEmptyRecoveryDateDirectories() {
        int deleted = 0;
        for (File tempDirectory : recoveryTempDirectories()) {
            File[] children = tempDirectory.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (!isDateStagingDirectory(child) || isPreparedStagingDirectory(child)) continue;
                try {
                    if (Files.deleteIfExists(child.toPath())) deleted++;
                } catch (IOException ignored) {
                    // Best-effort cleanup; leave the staging directory for a later recovery pass.
                }
            }
        }
        return deleted;
    }

    private static boolean isDateStagingDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) return false;
        File parent = directory.getParentFile();
        if (parent == null || !("Temp".equals(parent.getName())
                || "DurableAudioTemp".equals(parent.getName()))) return false;
        try {
            LocalDate.parse(directory.getName(), java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    void deleteTargetMarker(DcamMediaFile mediaFile) {
        try {
            Files.deleteIfExists(targetMarker(mediaFile.getFile()).toPath());
        } catch (IOException ignored) {
            // Target-marker cleanup must not replace the media finalization result.
        }
    }

    private File validatedMarkedTarget(DcamMediaFile mediaFile, File marker) {
        try {
            File marked = new File(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
            String markedPath = marked.getCanonicalPath();
            for (File mediaRoot : mediaRootDirectories()) {
                File allowed = finalFile(mediaRoot, mediaFile);
                if (markedPath.equals(allowed.getCanonicalPath())) return marked;
            }
            throw new SecurityException("Unsupported durable audio target");
        } catch (IOException error) {
            throw new SecurityException("Invalid durable audio target", error);
        }
    }

    private static File targetMarker(File staging) {
        return new File(staging.getParentFile(), staging.getName() + ".target");
    }

    public synchronized CaptureStorageCheck checkCaptureReady() {
        return checkCaptureReady(CaptureStorageCapacityPolicy.MIN_NEW_CAPTURE_AVAILABLE_BYTES);
    }

    public synchronized CaptureStorageCheck checkRecordingReady(long bitrateBitsPerSecond) {
        CaptureStorageCheck check = checkCaptureReady(
                minimumRecordingStartFreeBytes(bitrateBitsPerSecond));
        if (check.isReady()) {
            recordingMode = resolvedMode;
            recordingRoot = root;
        }
        return check;
    }

    public long minimumRecordingStartFreeBytes(long bitrateBitsPerSecond) {
        return capacityPolicy.minimumRecordingStartFreeBytes(bitrateBitsPerSecond);
    }

    public synchronized CaptureStorageCheck checkCaptureReady(long requiredBytes) {
        refreshExternalRoots();
        CaptureStorageCheck requestedExternal = requestedExternalCaptureCheck(requiredBytes);
        if (requestedExternal != null && !requestedExternal.isReady()) return requestedExternal;
        if (requestedExternal == null) resolveRootForNextCapture(requiredBytes);
        boolean mounted = root != null && (root.exists() || root.mkdirs());
        boolean writable = mounted && prepareDirectory(tempDirectory())
                && prepareStagingDirectory(currentLocalDateTime())
                && tempDirectory().canWrite() && preparePublicationDirectories()
                && root.canWrite();
        long availableBytes = mounted ? usableSpace.applyAsLong(root) : 0L;
        return capacityPolicy.check(mounted, writable, availableBytes, requiredBytes);
    }

    public synchronized CaptureStorageCheck checkCaptureWritable() {
        CaptureStorageCheck check = checkCaptureReady();
        if (!check.isReady()) return check;
        java.nio.file.Path probe = null;
        try {
            probe = Files.createTempFile(
                    tempDirectory().toPath(), ".dcam-storage-probe-", ".tmp");
            Files.delete(probe);
            probe = null;
            return check;
        } catch (IOException error) {
            if (requestedMode == MediaPartitionLocation.EXTERNAL
                    && isOperationNotPermitted(error)) {
                return CaptureStorageCheck.preparing(check.getAvailableBytes(),
                        check.getRequiredBytes(), EXTERNAL_PREPARING_MESSAGE);
            }
            if (requestedMode == MediaPartitionLocation.EXTERNAL) {
                return CaptureStorageCheck.unavailable(check.getAvailableBytes(),
                        check.getRequiredBytes(), EXTERNAL_UNAVAILABLE_MESSAGE);
            }
            return CaptureStorageCheck.rejected(check.getAvailableBytes(),
                    check.getRequiredBytes(), "Storage is not writable");
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException ignored) {
                    // Probe cleanup must not replace the already determined storage result.
                }
            }
        }
    }

    private static boolean isOperationNotPermitted(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof FileSystemException failure
                    && "Operation not permitted".equals(failure.getReason())) return true;
        }
        return false;
    }

    private CaptureStorageCheck requestedExternalCaptureCheck(long requiredBytes) {
        if (requestedMode != MediaPartitionLocation.EXTERNAL) return null;
        List<File> currentExternalRoots = externalRoots();
        if (currentExternalRoots.isEmpty()) return noExternalRootCaptureCheck(requiredBytes);
        ExternalCaptureCheckState state = new ExternalCaptureCheckState();
        for (File externalRoot : currentExternalRoots) {
            CaptureStorageCheck ready = readyExternalRootCaptureCheck(
                    externalRoot, requiredBytes, state);
            if (ready != null) return ready;
        }
        return completedExternalCaptureCheck(requiredBytes, state);
    }

    private CaptureStorageCheck readyExternalRootCaptureCheck(
            File externalRoot, long requiredBytes, ExternalCaptureCheckState state) {
        String storageState = externalStorageState.apply(externalRoot);
        if (!Environment.MEDIA_MOUNTED.equals(storageState)) {
            state.preparing |= isPreparingExternalState(storageState);
            return null;
        }
        DcamStorageCandidate storageCandidate = candidate(
                MediaPartitionLocation.EXTERNAL, externalRoot, false);
        CaptureStorageCheck check = storageCandidate.check(capacityPolicy, requiredBytes);
        if (check.isReady()) {
            resolvedMode = MediaPartitionLocation.EXTERNAL;
            root = externalRoot;
            return check;
        }
        if (isCapacityOnlyFailure(storageCandidate, check)) state.capacityFailure = check;
        return null;
    }

    private CaptureStorageCheck noExternalRootCaptureCheck(long requiredBytes) {
        if (removableVolumePreparing.getAsBoolean()) {
            return CaptureStorageCheck.preparing(0L, requiredBytes, EXTERNAL_PREPARING_MESSAGE);
        }
        return CaptureStorageCheck.unavailable(0L, requiredBytes, EXTERNAL_UNAVAILABLE_MESSAGE);
    }

    private CaptureStorageCheck completedExternalCaptureCheck(
            long requiredBytes, ExternalCaptureCheckState state) {
        if (state.preparing || removableVolumePreparing.getAsBoolean()) {
            return CaptureStorageCheck.preparing(0L, requiredBytes, EXTERNAL_PREPARING_MESSAGE);
        }
        if (state.capacityFailure != null) return state.capacityFailure;
        return CaptureStorageCheck.unavailable(0L, requiredBytes, EXTERNAL_UNAVAILABLE_MESSAGE);
    }

    private static final class ExternalCaptureCheckState {
        private boolean preparing;
        private CaptureStorageCheck capacityFailure;
    }

    static boolean isCapacityOnlyFailure(
            DcamStorageCandidate candidate, CaptureStorageCheck check) {
        return candidate.isWritable()
                && check.getAvailableBytes() < check.getRequiredBytes();
    }

    static boolean isPreparingExternalState(String state) {
        return Environment.MEDIA_CHECKING.equals(state);
    }

    private static boolean hasPreparingRemovableVolume(Context context) {
        StorageManager storageManager = context.getSystemService(StorageManager.class);
        if (storageManager == null) return false;
        int removableVolumeCount = 0;
        int preparingVolumeCount = 0;
        for (StorageVolume volume : storageManager.getStorageVolumes()) {
            if (!volume.isRemovable()) continue;
            removableVolumeCount++;
            if (isPreparingExternalState(volume.getState())) preparingVolumeCount++;
        }
        // ponytail: bind selected storage-volume ID when settings support multiple removable volumes.
        return hasSinglePreparingRemovableVolume(removableVolumeCount, preparingVolumeCount);
    }

    static boolean hasSinglePreparingRemovableVolume(
            int removableVolumeCount, int preparingVolumeCount) {
        return removableVolumeCount == 1 && preparingVolumeCount == 1;
    }

    public synchronized long availableBytesForNextCapture() {
        refreshExternalRoots();
        resolveRootForNextCapture();
        return root == null ? 0L : Math.max(0L, usableSpace.applyAsLong(root));
    }

    public synchronized long recordingAvailableBytes() {
        File selectedRoot = recordingRoot == null ? root : recordingRoot;
        return selectedRoot == null ? 0L : Math.max(0L, usableSpace.applyAsLong(selectedRoot));
    }

    public synchronized long recordingFileSizeLimit() {
        return capacityPolicy.recordingFileSizeLimit(recordingAvailableBytes());
    }

    public File configsFile() {
        return new File(new File(internalRoot, "Config"), "dcam_config.cson");
    }

    public synchronized List<File> identityBackupFiles() {
        refreshExternalRoots();
        if (!externalRoots().isEmpty()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            return List.of();
        }
        List<File> backups = new ArrayList<>();
        for (File externalRoot : externalRoots()) {
            if (externalRoot == null
                    || !Environment.MEDIA_MOUNTED.equals(externalStorageState.apply(externalRoot))) {
                continue;
            }
            File backup = identityBackupFile(externalRoot);
            if (backup != null) backups.add(backup);
        }
        return List.copyOf(backups);
    }


    private boolean stagesBeforePublication(DcamFileType type) {
        return type == DcamFileType.VIDEO || type == DcamFileType.IMP
                || type == DcamFileType.IMAGE || type == DcamFileType.AUDIO
                || type == DcamFileType.AUDIO_M4A;
    }

    private void resolveRootForNextCapture() {
        resolveRootForNextCapture(CaptureStorageCapacityPolicy.MIN_NEW_CAPTURE_AVAILABLE_BYTES);
    }

    private void resolveRootForNextCapture(long requiredBytes) {
        if (requestedMode == MediaPartitionLocation.INTERNAL) {
            resolvedMode = MediaPartitionLocation.INTERNAL;
            root = internalRoot;
            return;
        }
        DcamStorageCandidate internal = candidate(MediaPartitionLocation.INTERNAL, internalRoot, true);
        List<DcamStorageCandidate> external = new ArrayList<>();
        for (File externalRoot : externalRoots()) {
            external.add(candidate(MediaPartitionLocation.EXTERNAL, externalRoot, false));
        }
        DcamStorageResolution resolution =
                new DcamStorageRootResolver(capacityPolicy).resolve(
                        requestedMode, internal, external, requiredBytes);
        resolvedMode = resolution.getResolvedMode();
        root = resolution.getRoot();
    }

    static File identityBackupFile(File externalAppRoot) {
        File volumeRoot = externalVolumeRoot(externalAppRoot);
        return volumeRoot == null
                ? null
                : new File(new File(volumeRoot, "DCAM_FACTORY"), "device_identity.json");
    }
    private static File externalVolumeRoot(File externalRoot) {
        File volumeRoot = externalRoot;
        for (int index = 0; index < 4 && volumeRoot != null; index++) {
            volumeRoot = volumeRoot.getParentFile();
        }
        return volumeRoot;
    }

    private synchronized void refreshExternalRoots() {
        if (context == null || !externalRootsStale
                || Looper.myLooper() == Looper.getMainLooper()) return;
        boolean retryIncomplete = retryIncompleteExternalRoots;
        try {
            File[] appRoots = context.getExternalFilesDirs(null);
            List<File> refreshed = discoveredExternalRoots(appRoots);
            boolean retry = retryIncomplete
                    && shouldRetryExternalRootRefresh(appRoots, refreshed);
            externalRoots.set(refreshed);
            externalRootsStale = retry;
            retryIncompleteExternalRoots = retry;
        } catch (RuntimeException ignored) {
            externalRootsStale = retryIncomplete;
        }
    }

    public synchronized void refreshExternalRootsAfterMount() {
        retryIncompleteExternalRoots = true;
        externalRootsStale = true;
        refreshExternalRoots();
    }

    static List<File> discoveredExternalRoots(File[] appRoots) {
        List<File> refreshed = new ArrayList<>();
        for (int index = 1; index < appRoots.length; index++) {
            if (appRoots[index] != null) addDistinct(refreshed, appRoots[index]);
        }
        return List.copyOf(refreshed);
    }

    static boolean shouldRetryExternalRootRefresh(
            File[] appRoots, List<File> refreshed) {
        if (!refreshed.isEmpty()) return false;
        for (int index = 1; index < appRoots.length; index++) {
            if (appRoots[index] == null) return true;
        }
        return false;
    }

    private List<File> externalRoots() {
        return externalRoots.get();
    }

    private static LocalDateTime currentLocalDateTime() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private DcamStorageCandidate candidate(MediaPartitionLocation mode, File root, boolean internal) {
        boolean mounted = root != null && (internal
                || Environment.MEDIA_MOUNTED.equals(externalStorageState.apply(root)));
        boolean writable = mounted && prepareDirectory(new File(root, "Temp")) && root.canWrite();
        long availableBytes = mounted ? usableSpace.applyAsLong(root) : 0L;
        return new DcamStorageCandidate(mode, root, mounted, writable, availableBytes);
    }

    // ponytail: prewarm next date before midnight if first capture after rollover must stay hot.
    private boolean prepareStagingDirectory(LocalDateTime at) {
        File directory = new File(tempDirectory(), DcamFileName.dateFolder(at));
        if (isPreparedStagingDirectory(directory)) return true;
        boolean prepared = prepareDirectory(directory);
        if (prepared) preparedStagingDirectoryPath = directory.getAbsolutePath();
        return prepared;
    }

    private boolean isPreparedStagingDirectory(File directory) {
        return directory != null && directory.getAbsolutePath().equals(
                preparedStagingDirectoryPath);
    }

    private boolean preparePublicationDirectories() {
        File mediaRoot = rootDirectory();
        if (!prepareDirectory(mediaRoot) || !mediaRoot.canWrite()) return false;
        for (DcamFileType type : DcamFileType.values()) {
            File directory = new File(mediaRoot, type.getFolder());
            if (!prepareDirectory(directory) || !directory.canWrite()) return false;
        }
        return true;
    }

    private static boolean prepareDirectory(File directory) {
        return directory.isDirectory() || directory.mkdirs();
    }

    private static void addDistinct(List<File> directories, File candidate) {
        String path = candidate.getAbsolutePath();
        for (File directory : directories) {
            if (directory.getAbsolutePath().equals(path)) return;
        }
        directories.add(candidate);
    }
}











