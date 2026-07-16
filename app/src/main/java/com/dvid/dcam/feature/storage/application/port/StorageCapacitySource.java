package com.dvid.dcam.feature.storage.application.port;

import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import java.util.List;

/** Supplies storage capacity without exposing filesystem APIs. */
public interface StorageCapacitySource {
    List<StorageVolumeStatus> readStorageVolumes();
}
