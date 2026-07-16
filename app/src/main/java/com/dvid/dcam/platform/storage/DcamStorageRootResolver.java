package com.dvid.dcam.platform.storage;

import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;

import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import java.util.List;

/** External-first media-root resolver. Fallback is decided before capture, never mid-file. */
public final class DcamStorageRootResolver {
    private final CaptureStorageCapacityPolicy capacityPolicy;

    public DcamStorageRootResolver(CaptureStorageCapacityPolicy capacityPolicy) {
        this.capacityPolicy = capacityPolicy;
    }

    public DcamStorageResolution resolve(
            MediaPartitionLocation requestedMode,
            DcamStorageCandidate internal,
            List<DcamStorageCandidate> externalCandidates) {
        MediaPartitionLocation requested = requestedMode == null ? MediaPartitionLocation.AUTO : requestedMode;
        if (requested == MediaPartitionLocation.INTERNAL) {
            return new DcamStorageResolution(requested, MediaPartitionLocation.INTERNAL, internal.getRoot(), false);
        }

        for (DcamStorageCandidate external : externalCandidates) {
            if (external.getMode() == MediaPartitionLocation.EXTERNAL
                    && external.check(capacityPolicy).isReady()) {
                return new DcamStorageResolution(requested, MediaPartitionLocation.EXTERNAL,
                        external.getRoot(), false);
            }
        }

        return new DcamStorageResolution(
                requested, MediaPartitionLocation.INTERNAL, internal.getRoot(), true);
    }
}
