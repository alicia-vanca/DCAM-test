package com.dvid.dcam.feature.storage.application.usecase;

import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import com.dvid.dcam.feature.storage.domain.StorageWarningStatus;
import java.util.List;

/** Application entry point for media storage selection. */
public interface StorageSettingsUseCase {
    MediaPartitionLocation currentMode();
    List<MediaPartitionLocation> supportedModes();
    void changeMode(MediaPartitionLocation mode);
    List<StorageVolumeStatus> storageVolumes();
    int warningGb();
    void changeWarningGb(int value);
    StorageWarningStatus warningStatus();
}
