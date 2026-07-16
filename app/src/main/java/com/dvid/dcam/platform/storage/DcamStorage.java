package com.dvid.dcam.platform.storage;

import android.content.Context;
import android.os.Environment;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageMode;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class DcamStorage implements
        com.dvid.dcam.feature.storage.application.port.ActiveStorageSource,
        com.dvid.dcam.feature.storage.application.port.ActiveStoragePolicyGateway {
    private final StorageMode storageMode;
    private volatile MediaPartitionLocation requestedMode;
    private final Context context;
    private final File internalRoot;
    private volatile List<File> externalRoots;
    private final CaptureStorageCapacityPolicy capacityPolicy;
    private volatile MediaPartitionLocation resolvedMode;
    private volatile File root;

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
        this.context = context;
        this.storageMode = storageMode == null ? StorageMode.APP_DATA : storageMode;
        this.requestedMode = requestedMode;
        this.resolvedMode = resolvedMode;
        this.internalRoot = internalRoot;
        this.root = root;
        this.externalRoots = List.copyOf(externalRoots);
        this.capacityPolicy = capacityPolicy;
    }

    public static DcamStorage from(Context context) {
        return from(context, StorageMode.from(BuildConfig.DEFAULT_STORAGE_MODE), MediaPartitionLocation.AUTO);
    }

    public static DcamStorage from(Context context, MediaPartitionLocation requestedMode) {
        return from(context, StorageMode.APP_DATA, requestedMode);
    }

    public static DcamStorage from(Context context, StorageMode storageMode,
            MediaPartitionLocation requestedMode) {
        File[] appRoots = context.getExternalFilesDirs(null);
        File internalRoot = appRoots.length > 0 && appRoots[0] != null
                ? appRoots[0]
                : context.getFilesDir();
        CaptureStorageCapacityPolicy policy = new CaptureStorageCapacityPolicy();
        DcamStorageCandidate internal = candidate(MediaPartitionLocation.INTERNAL, internalRoot, true);
        List<DcamStorageCandidate> external = new ArrayList<>();
        for (int i = 1; i < appRoots.length; i++) {
            if (appRoots[i] != null) {
                external.add(candidate(MediaPartitionLocation.EXTERNAL, appRoots[i], false));
            }
        }
        DcamStorageResolution resolution =
                new DcamStorageRootResolver(policy).resolve(requestedMode, internal, external);
        List<File> externalRootFiles = new ArrayList<>();
        for (DcamStorageCandidate candidate : external) {
            externalRootFiles.add(candidate.getRoot());
        }
        return new DcamStorage(context.getApplicationContext(), storageMode, requestedMode, resolution.getResolvedMode(),
                internalRoot, resolution.getRoot(), externalRootFiles, policy);
    }

    public MediaPartitionLocation getRequestedMode() { return requestedMode; }
    public MediaPartitionLocation getMode() { return resolvedMode; }

    @Override public synchronized void changeRequestedLocation(MediaPartitionLocation location) {
        requestedMode = location == null ? MediaPartitionLocation.AUTO : location;
        refreshExternalRoots();
        resolveRootForNextCapture();
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

    public File outputFile(DcamFileType type, String accountUserId, String policeUserId,
                           LocalDateTime at, boolean encrypted) {
        return prepareFile(mediaFile(type, accountUserId, policeUserId, at, encrypted));
    }

    public DcamMediaFile mediaFile(DcamFileType type, String accountUserId, String policeUserId,
                                   LocalDateTime at, boolean encrypted) {
        File dir = stagesBeforePublication(type)
                ? tempDirectory()
                : new File(rootDirectory(), type.getFolder());
        String fileName = DcamFileName.build(type, accountUserId, policeUserId, at, encrypted);
        return new DcamMediaFile(type, fileName, new File(dir, fileName), at);
    }

    public DcamMediaFile durableAudioMediaFile(
            String accountUserId, String policeUserId, LocalDateTime at, boolean encrypted)
            throws IOException {
        DcamMediaFile staging = mediaFile(
                DcamFileType.AUDIO, accountUserId, policeUserId, at, encrypted);
        File parent = staging.getFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Cannot create durable audio staging directory: " + parent);
        }
        return staging;
    }

    public File prepareFile(DcamMediaFile mediaFile) {
        File parent = mediaFile.getFile().getParentFile();
        if (parent != null) parent.mkdirs();
        return mediaFile.getFile();
    }

    public File finalFile(DcamMediaFile mediaFile) {
        File marker = targetMarker(mediaFile.getFile());
        if (marker.isFile()) return validatedMarkedTarget(mediaFile, marker);
        File stagingParent = mediaFile.getFile().getParentFile();
        File mediaRoot = stagingParent != null && "Temp".equals(stagingParent.getName())
                ? new File(stagingParent.getParentFile(), "Media")
                : rootDirectory();
        return new File(new File(mediaRoot, mediaFile.getType().getFolder()),
                mediaFile.getFileName());
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

    void deleteTargetMarker(DcamMediaFile mediaFile) {
        try { Files.deleteIfExists(targetMarker(mediaFile.getFile()).toPath()); } catch (IOException ignored) { }
    }

    private File validatedMarkedTarget(DcamMediaFile mediaFile, File marker) {
        try {
            File marked = new File(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
            String markedPath = marked.getCanonicalPath();
            for (File mediaRoot : mediaRootDirectories()) {
                File allowed = new File(new File(mediaRoot, mediaFile.getType().getFolder()),
                        mediaFile.getFileName());
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
        resolveRootForNextCapture();
        boolean mounted = root != null && (root.exists() || root.mkdirs());
        boolean writable = mounted && prepareDirectory(tempDirectory()) && root.canWrite();
        long availableBytes = mounted ? root.getUsableSpace() : 0L;
        return capacityPolicy.check(mounted, writable, availableBytes);
    }

    public synchronized long availableBytesForNextCapture() {
        refreshExternalRoots();
        resolveRootForNextCapture();
        return root == null ? 0L : Math.max(0L, root.getUsableSpace());
    }

    public long recordingFileSizeLimit() {
        return capacityPolicy.recordingFileSizeLimit(Math.max(0L, root.getUsableSpace()));
    }

    public File configsFile() { return new File(new File(internalRoot, "Config"), "dcam_config.cson"); }

    public File legacyConfigsFile() { return new File(internalRoot, "configs.cson"); }

    private boolean stagesBeforePublication(DcamFileType type) {
        return type == DcamFileType.VIDEO || type == DcamFileType.SOS
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

    private void refreshExternalRoots() {
        if (context == null) return;
        File[] appRoots = context.getExternalFilesDirs(null);
        List<File> refreshed = new ArrayList<>();
        for (int i = 1; i < appRoots.length; i++) {
            if (appRoots[i] != null) addDistinct(refreshed, appRoots[i]);
        }
        externalRoots = List.copyOf(refreshed);
    }

    private static DcamStorageCandidate candidate(MediaPartitionLocation mode, File root, boolean internal) {
        boolean mounted = root != null && (internal
                || Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(root)));
        boolean writable = mounted && prepareDirectory(new File(root, "Temp")) && root.canWrite();
        long availableBytes = mounted ? root.getUsableSpace() : 0L;
        return new DcamStorageCandidate(mode, root, mounted, writable, availableBytes);
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











