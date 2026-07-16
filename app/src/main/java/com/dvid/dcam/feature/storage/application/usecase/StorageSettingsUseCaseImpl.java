package com.dvid.dcam.feature.storage.application.usecase;

import com.dvid.dcam.feature.storage.application.port.ActiveStorageSource;
import com.dvid.dcam.feature.storage.application.port.ActiveStoragePolicyGateway;
import com.dvid.dcam.feature.storage.application.port.MediaPartitionLocationPreferenceStore;
import com.dvid.dcam.feature.storage.application.port.StorageCapacitySource;
import com.dvid.dcam.feature.storage.application.port.StorageWarningPreferenceStore;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import com.dvid.dcam.feature.storage.domain.StorageWarningStatus;
import java.util.List;

public final class StorageSettingsUseCaseImpl implements StorageSettingsUseCase {
    private static final List<MediaPartitionLocation> SUPPORTED_MODES = List.of(
            MediaPartitionLocation.INTERNAL, MediaPartitionLocation.EXTERNAL,
            MediaPartitionLocation.AUTO);
    private final MediaPartitionLocationPreferenceStore preferences;
    private final StorageCapacitySource capacityProvider;
    private final StorageWarningPreferenceStore warningPreferences;
    private final ActiveStorageSource activeStorage;
    private final ActiveStoragePolicyGateway activePolicy;

    public StorageSettingsUseCaseImpl(MediaPartitionLocationPreferenceStore preferences,
            StorageCapacitySource capacityProvider,
            StorageWarningPreferenceStore warningPreferences,
            ActiveStorageSource activeStorage,
            ActiveStoragePolicyGateway activePolicy) {
        this.preferences = preferences;
        this.capacityProvider = capacityProvider;
        this.warningPreferences = warningPreferences;
        this.activeStorage = activeStorage;
        this.activePolicy = activePolicy;
    }

    @Override public MediaPartitionLocation currentMode() {
        return preferences.currentMediaPartitionLocation();
    }

    @Override public List<MediaPartitionLocation> supportedModes() { return SUPPORTED_MODES; }

    @Override public void changeMode(MediaPartitionLocation mode) {
        MediaPartitionLocation selected = mode == null ? MediaPartitionLocation.AUTO : mode;
        activePolicy.changeRequestedLocation(selected);
        preferences.selectMediaPartitionLocation(selected);
    }

    @Override public List<StorageVolumeStatus> storageVolumes() {
        return capacityProvider.readStorageVolumes();
    }

    @Override public int warningGb() { return warningPreferences.warningGb(); }

    @Override public void changeWarningGb(int value) { warningPreferences.setWarningGb(value); }

    @Override public StorageWarningStatus warningStatus() {
        StorageVolumeStatus volume = activeStorage.activeStorageVolume();
        long freeBytes = volume == null || !volume.isAvailable() ? 0L : volume.getFreeBytes();
        long thresholdBytes = warningGb() * 1024L * 1024L * 1024L;
        return new StorageWarningStatus(freeBytes <= thresholdBytes, freeBytes);
    }
}

