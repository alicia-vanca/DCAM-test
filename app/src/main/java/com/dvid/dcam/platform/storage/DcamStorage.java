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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class DcamStorage implements
        com.dvid.dcam.feature.storage.application.port.ActiveStorageSource,
        com.dvid.dcam.feature.storage.application.port.ActiveStoragePolicyGateway {
    private final StorageMode storageMode;
    private volatile MediaPartitionLocation requestedMode;
    private final Context context;
    private final File internalRoot;
    private volatile List<File> externalRoots;
    private volatile boolean externalRootsStale;
    private boolean retryIncompleteExternalRoots;
    private final CaptureStorageCapacityPolicy capacityPolicy;
    private final BooleanSupplier removableVolumePreparing;
    private volatile MediaPartitionLocation resolvedMode;
    private volatile File root;
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
                capacityPolicy, context == null ? () -> false : () -> hasPreparingRemovableVolume(context));
    }

    DcamStorage(
            MediaPartitionLocation requestedMode,
            File internalRoot,
            List<File> externalRoots,
            BooleanSupplier removableVolumePreparing) {
        this(null, StorageMode.APP_DATA, requestedMode, MediaPartitionLocation.INTERNAL,
                internalRoot, internalRoot, externalRoots, new CaptureStorageCapacityPolicy(),
                removableVolumePreparing);
    }

    private DcamStorage(
            Context context,
            StorageMode storageMode,
            MediaPartitionLocation requestedMode,
            MediaPartitionLocation resolvedMode,
            File internalRoot,
            File root,
            List<File> externalRoots,
            CaptureStorageCapacityPolicy capacityPolicy,
            BooleanSupplier removableVolumePreparing) {
        this.context = context;
        this.storageMode = storageMode == null ? StorageMode.APP_DATA : storageMode;
        this.requestedMode = requestedMode;
        this.resolvedMode = resolvedMode;
        this.internalRoot = internalRoot;
        this.root = root;
        this.externalRoots = List.copyOf(externalRoots);
        this.externalRootsStale = context != null;
        this.capacityPolicy = capacityPolicy;
        this.removableVolumePreparing = removableVolumePreparing;
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
        storage.prepareStagingDirectory(LocalDateTime.now());
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
        prepareStagingDirectory(LocalDateTime.now());
    }
    public boolean isFallback() { return requestedMode != MediaPartitionLocation.INTERNAL && resolvedMode == MediaPartitionLocation.INTERNAL; }
    public boolean isPublicDcim() { return storageMode == StorageMode.PUBLIC_DCIM; }

    @Override public synchronized StorageVolumeStatus activeStorageVolume() {
        refreshExternalRoots();
        resolveRootForNextCapture();
        boolean available = root != null && (root.exists() || root.mkdirs());
        return new StorageVolumeStatus(resolvedMode, available,
                available ? root.getTotalSpace() : 0L,
                available ? root.getUsableSpace() : 0L);
    }
    public File captureRoot() { return root; }
    public File rootDirectory() { return new File(root, "Media"); }
    public File tempDirectory() { return new File(root, "Temp"); }

    public synchronized List<File> mediaRootDirectories() {
        refreshExternalRoots();
        List<File> directories = new ArrayList<>();
        addDistinct(directories, new File(internalRoot, "Media"));
        addDistinct(directories, rootDirectory());
        for (File externalRoot : externalRoots) {
            addDistinct(directories, new File(externalRoot, "Media"));
        }
        return List.copyOf(directories);
    }

    public File mediaRootDirectory(String location) {
        List<File> roots = mediaRootDirectories();
        int index;
        try {
            index = "Internal".equals(location) ? 0 : "External".equals(location) ? 1
                    : location.startsWith("External ")
                    ? Integer.parseInt(location.substring("External ".length())) : -1;
        } catch (NumberFormatException error) {
            throw new SecurityException("Unsupported media storage", error);
        }
        if (index < 0 || index >= roots.size()) {
            throw new SecurityException("Unsupported media storage");
        }
        return roots.get(index);
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

    public DcamMediaFile mediaFile(DcamFileType type, String cameraId, String fileUserId,
                                   LocalDateTime at, boolean encrypted) {
        File dir = stagesBeforePublication(type)
                ? new File(tempDirectory(), DcamFileName.dateFolder(at))
                : new File(rootDirectory(), type.getFolder());
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
            for (DcamFileType type : DcamFileType.values()) {
                File typeRoot = new File(mediaRoot, type.getFolder());
                if (new File(typeRoot, fileName).isFile()) return true;
                File[] datedDirectories = typeRoot.listFiles(File::isDirectory);
                if (datedDirectories == null) continue;
                for (File dateDirectory : datedDirectories) {
                    if (new File(dateDirectory, fileName).isFile()) return true;
                }
            }
        }
        return false;
    }


    public DcamMediaFile durableAudioMediaFile(
            String cameraId, String fileUserId, LocalDateTime at, boolean encrypted)
            throws IOException {
        DcamMediaFile staging = mediaFile(
                DcamFileType.AUDIO, cameraId, fileUserId, at, encrypted);
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
        File stagingParent = mediaFile.getFile().getParentFile();
        File mediaRoot = stagingParent != null && isDateStagingDirectory(stagingParent)
                ? new File(stagingParent.getParentFile().getParentFile(), "Media")
                : stagingParent != null && "Temp".equals(stagingParent.getName())
                ? new File(stagingParent.getParentFile(), "Media")
                : rootDirectory();
        return finalFile(mediaRoot, mediaFile);
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
        for (File externalRoot : externalRoots) {
            addDistinct(directories, new File(externalRoot, "Temp"));
        }
        return List.copyOf(directories);
    }

    void deleteEmptyStagingDateDirectory(DcamMediaFile mediaFile) {
        File dateDirectory = mediaFile.getFile().getParentFile();
        if (!isDateStagingDirectory(dateDirectory)
                || isPreparedStagingDirectory(dateDirectory)) return;
        try { Files.deleteIfExists(dateDirectory.toPath()); } catch (IOException ignored) { }
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
                } catch (IOException ignored) { }
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
        try { Files.deleteIfExists(targetMarker(mediaFile.getFile()).toPath()); } catch (IOException ignored) { }
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
        refreshExternalRoots();
        CaptureStorageCheck requestedExternal = requestedExternalCaptureCheck();
        if (requestedExternal != null && !requestedExternal.isReady()) return requestedExternal;
        if (requestedExternal == null) resolveRootForNextCapture();
        boolean mounted = root != null && (root.exists() || root.mkdirs());
        boolean writable = mounted && prepareDirectory(tempDirectory())
                && prepareStagingDirectory(LocalDateTime.now())
                && tempDirectory().canWrite() && preparePublicationDirectories()
                && root.canWrite();
        long availableBytes = mounted ? root.getUsableSpace() : 0L;
        return capacityPolicy.check(mounted, writable, availableBytes);
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
                        check.getRequiredBytes(), "SD card is being prepared. Please wait.");
            }
            if (requestedMode == MediaPartitionLocation.EXTERNAL) {
                return CaptureStorageCheck.unavailable(check.getAvailableBytes(),
                        check.getRequiredBytes(), "SD card unavailable.");
            }
            return CaptureStorageCheck.rejected(check.getAvailableBytes(),
                    check.getRequiredBytes(), "Storage is not writable");
        } finally {
            if (probe != null) {
                try { Files.deleteIfExists(probe); } catch (IOException ignored) {}
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

    private CaptureStorageCheck requestedExternalCaptureCheck() {
        if (requestedMode != MediaPartitionLocation.EXTERNAL) return null;
        if (externalRoots.isEmpty()) {
            if (removableVolumePreparing.getAsBoolean()) {
                return CaptureStorageCheck.preparing(0L,
                        CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES,
                        "SD card is being prepared. Please wait.");
            }
            return CaptureStorageCheck.unavailable(0L,
                    CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES,
                    "SD card unavailable.");
        }
        boolean preparing = false;
        CaptureStorageCheck capacityFailure = null;
        for (File externalRoot : externalRoots) {
            String state = Environment.getExternalStorageState(externalRoot);
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                DcamStorageCandidate storageCandidate = candidate(
                        MediaPartitionLocation.EXTERNAL, externalRoot, false);
                CaptureStorageCheck check = storageCandidate.check(capacityPolicy);
                if (check.isReady()) {
                    resolvedMode = MediaPartitionLocation.EXTERNAL;
                    root = externalRoot;
                    return check;
                }
                if (isCapacityOnlyFailure(storageCandidate, check)) capacityFailure = check;
            } else if (isPreparingExternalState(state)) {
                preparing = true;
            }
        }
        if (!preparing) preparing = removableVolumePreparing.getAsBoolean();
        if (preparing) {
            return CaptureStorageCheck.preparing(0L,
                    CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES,
                    "SD card is being prepared. Please wait.");
        }
        if (capacityFailure != null) return capacityFailure;
        return CaptureStorageCheck.unavailable(0L,
                CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES,
                "SD card unavailable.");
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
        return root == null ? 0L : Math.max(0L, root.getUsableSpace());
    }

    public long recordingFileSizeLimit() {
        return capacityPolicy.recordingFileSizeLimit(Math.max(0L, root.getUsableSpace()));
    }

    public File configsFile() {
        return new File(new File(internalRoot, "Config"), "dcam_config.cson");
    }

    public synchronized List<File> identityBackupFiles() {
        refreshExternalRoots();
        if (!externalRoots.isEmpty()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            return List.of();
        }
        List<File> backups = new ArrayList<>();
        for (File externalRoot : externalRoots) {
            if (externalRoot == null
                    || !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(externalRoot))) {
                continue;
            }
            File backup = identityBackupFile(externalRoot);
            if (backup != null) backups.add(backup);
        }
        return List.copyOf(backups);
    }


    private boolean stagesBeforePublication(DcamFileType type) {
        return type == DcamFileType.VIDEO || type == DcamFileType.IMP
                || type == DcamFileType.IMAGE || type == DcamFileType.AUDIO;
    }

    private void resolveRootForNextCapture() {
        if (requestedMode == MediaPartitionLocation.INTERNAL) {
            resolvedMode = MediaPartitionLocation.INTERNAL;
            root = internalRoot;
            return;
        }
        DcamStorageCandidate internal = candidate(MediaPartitionLocation.INTERNAL, internalRoot, true);
        List<DcamStorageCandidate> external = new ArrayList<>();
        for (File externalRoot : externalRoots) {
            external.add(candidate(MediaPartitionLocation.EXTERNAL, externalRoot, false));
        }
        DcamStorageResolution resolution =
                new DcamStorageRootResolver(capacityPolicy).resolve(requestedMode, internal, external);
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
            externalRoots = refreshed;
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

    private static DcamStorageCandidate candidate(MediaPartitionLocation mode, File root, boolean internal) {
        boolean mounted = root != null && (internal
                || Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(root)));
        boolean writable = mounted && prepareDirectory(new File(root, "Temp")) && root.canWrite();
        long availableBytes = mounted ? root.getUsableSpace() : 0L;
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











