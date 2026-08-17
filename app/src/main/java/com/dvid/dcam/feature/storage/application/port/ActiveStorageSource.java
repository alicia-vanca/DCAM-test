package com.dvid.dcam.feature.storage.application.port;

import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;

/** Supplies volume currently selected by capture storage resolver. */
public interface ActiveStorageSource {
    StorageVolumeStatus activeStorageVolume();
    StorageVolumeStatus activeStorageVolume(
            long requiredBytes, boolean preserveRecordingVolume);
}
