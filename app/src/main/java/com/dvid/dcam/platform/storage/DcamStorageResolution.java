package com.dvid.dcam.platform.storage;

import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import java.io.File;

/** Requested policy and physical media root selected for the next capture. */
public final class DcamStorageResolution {
    private final MediaPartitionLocation requestedMode;
    private final MediaPartitionLocation resolvedMode;
    private final File root;
    private final boolean fallback;

    public DcamStorageResolution(
            MediaPartitionLocation requestedMode, MediaPartitionLocation resolvedMode, File root, boolean fallback) {
        this.requestedMode = requestedMode;
        this.resolvedMode = resolvedMode;
        this.root = root;
        this.fallback = fallback;
    }

    public MediaPartitionLocation getRequestedMode() { return requestedMode; }
    public MediaPartitionLocation getResolvedMode() { return resolvedMode; }
    public File getRoot() { return root; }
    public boolean isFallback() { return fallback; }
}

