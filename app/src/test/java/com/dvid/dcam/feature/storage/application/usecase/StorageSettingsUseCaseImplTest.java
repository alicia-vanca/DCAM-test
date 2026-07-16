package com.dvid.dcam.feature.storage.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.dvid.dcam.feature.storage.application.port.ActiveStorageSource;
import com.dvid.dcam.feature.storage.application.port.ActiveStoragePolicyGateway;
import com.dvid.dcam.feature.storage.application.port.MediaPartitionLocationPreferenceStore;
import com.dvid.dcam.feature.storage.application.port.StorageCapacitySource;
import com.dvid.dcam.feature.storage.application.port.StorageWarningPreferenceStore;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StorageSettingsUseCaseImplTest {
    @Test void readsAndChangesOperatorStorageSelection() {
        FakePartitionPreferences preferences = new FakePartitionPreferences();
        StorageSettingsUseCase settings = settings(preferences,
                new StorageVolumeStatus(MediaPartitionLocation.INTERNAL, true, 10L, 5L));

        assertEquals(MediaPartitionLocation.AUTO, settings.currentMode());
        settings.changeMode(MediaPartitionLocation.INTERNAL);
        assertEquals(MediaPartitionLocation.INTERNAL, settings.currentMode());
        assertEquals(MediaPartitionLocation.INTERNAL, preferences.selectedMode);
        assertEquals(MediaPartitionLocation.INTERNAL, preferences.runtimeMode);
    }

    @Test void warningUsesResolvedActiveVolumeInsteadOfReinterpretingAuto() {
        FakePartitionPreferences preferences = new FakePartitionPreferences();
        long fiveGb = 5L * 1024L * 1024L * 1024L;
        StorageSettingsUseCase settings = settings(preferences,
                new StorageVolumeStatus(MediaPartitionLocation.EXTERNAL, true, 10L * fiveGb, fiveGb));

        assertEquals(MediaPartitionLocation.AUTO, settings.currentMode());
        assertFalse(settings.warningStatus().isVisible());
        assertEquals(fiveGb, settings.warningStatus().getFreeBytes());
    }

    private static StorageSettingsUseCase settings(
            FakePartitionPreferences preferences, StorageVolumeStatus activeVolume) {
        StorageCapacitySource capacities = () -> List.of(activeVolume);
        StorageWarningPreferenceStore warnings = new StorageWarningPreferenceStore() {
            private int warningGb = 2;
            @Override public int warningGb() { return warningGb; }
            @Override public void setWarningGb(int value) { warningGb = value; }
        };
        ActiveStorageSource activeStorage = () -> activeVolume;
        return new StorageSettingsUseCaseImpl(
                preferences, capacities, warnings, activeStorage, preferences);
    }

    private static final class FakePartitionPreferences
            implements MediaPartitionLocationPreferenceStore, ActiveStoragePolicyGateway {
        private MediaPartitionLocation mode = MediaPartitionLocation.AUTO;
        private MediaPartitionLocation selectedMode = MediaPartitionLocation.AUTO;
        private MediaPartitionLocation runtimeMode = MediaPartitionLocation.AUTO;

        @Override public MediaPartitionLocation currentMediaPartitionLocation() { return mode; }
        @Override public void changeRequestedLocation(MediaPartitionLocation location) {
            runtimeMode = location;
        }
        @Override public void selectMediaPartitionLocation(MediaPartitionLocation mode) {
            this.mode = mode;
            selectedMode = mode;
        }
    }
}




