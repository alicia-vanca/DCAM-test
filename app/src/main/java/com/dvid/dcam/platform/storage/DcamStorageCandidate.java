package com.dvid.dcam.platform.storage;

import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCheck;

import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import java.io.File;

/** One Android app-specific media root and its capture health snapshot. */
public final class DcamStorageCandidate {
    private final MediaPartitionLocation mode;
    private final File root;
    private final boolean mounted;
    private final boolean writable;
    private final long availableBytes;

    public DcamStorageCandidate(
            MediaPartitionLocation mode, File root, boolean mounted, boolean writable, long availableBytes) {
        if (mode == MediaPartitionLocation.AUTO) throw new IllegalArgumentException("Candidate mode cannot be AUTO");
        this.mode = mode;
        this.root = root;
        this.mounted = mounted;
        this.writable = writable;
        this.availableBytes = availableBytes;
    }

    public MediaPartitionLocation getMode() { return mode; }
    public File getRoot() { return root; }
    public boolean isMounted() { return mounted; }
    public boolean isWritable() { return writable; }
    public long getAvailableBytes() { return availableBytes; }

    public CaptureStorageCheck check(
            CaptureStorageCapacityPolicy policy, long requiredBytes) {
        return policy.check(mounted, writable, availableBytes, requiredBytes);
    }
}
