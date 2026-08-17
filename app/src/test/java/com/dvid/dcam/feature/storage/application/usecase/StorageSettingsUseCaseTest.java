package com.dvid.dcam.feature.storage.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.dvid.dcam.feature.storage.application.port.ActiveStorageSource;
import com.dvid.dcam.feature.storage.application.port.ActiveStoragePolicyGateway;
import com.dvid.dcam.feature.storage.application.port.MediaPartitionLocationPreferenceStore;
import com.dvid.dcam.feature.storage.application.port.StorageCapacitySource;
import com.dvid.dcam.feature.storage.application.port.StorageWarningPreferenceStore;
import com.dvid.dcam.feature.storage.domain.CaptureStorageCapacityPolicy;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StorageSettingsUseCaseTest {
    @Test void readsAndChangesOperatorStorageSelection() {
        FakePartitionPreferences preferences = new FakePartitionPreferences();
        StorageSettingsUseCase settings = settings(preferences,
                new FakeActiveStorageSource(new StorageVolumeStatus(
                        MediaPartitionLocation.INTERNAL, true, 10L, 5L)));

        assertEquals(MediaPartitionLocation.AUTO, settings.currentMode());
        settings.changeMode(MediaPartitionLocation.INTERNAL);
        assertEquals(MediaPartitionLocation.INTERNAL, settings.currentMode());
        assertEquals(MediaPartitionLocation.INTERNAL, preferences.selectedMode);
        assertEquals(MediaPartitionLocation.INTERNAL, preferences.runtimeMode);
    }

    @Test void warningUsesDynamicMinimumAndForwardsRecordingVolumeSelection() {
        FakePartitionPreferences preferences = new FakePartitionPreferences();
        long gib = 1024L * 1024L * 1024L;
        long dynamicMinimum = 3L * gib;
        FakeActiveStorageSource activeStorage = new FakeActiveStorageSource(
                new StorageVolumeStatus(MediaPartitionLocation.EXTERNAL, true,
                        10L * gib, 5L * gib / 2L));
        StorageSettingsUseCase settings = settings(preferences, activeStorage);

        assertTrue(settings.warningStatus(dynamicMinimum, true).isVisible());
        assertTrue(settings.warningStatus(dynamicMinimum, true).isRecordingBlocked());
        assertEquals(dynamicMinimum, activeStorage.requiredBytes);
        assertTrue(activeStorage.preserveRecordingVolume);
    }

    @Test void warningUsesConfiguredThresholdWhenItIsHigher() {
        FakePartitionPreferences preferences = new FakePartitionPreferences();
        long gib = 1024L * 1024L * 1024L;
        FakeActiveStorageSource activeStorage = new FakeActiveStorageSource(
                new StorageVolumeStatus(MediaPartitionLocation.INTERNAL, true,
                        10L * gib, 7L * gib / 2L));
        StorageSettingsUseCase settings = settings(preferences, activeStorage);
        settings.changeWarningGb(4);

        assertTrue(settings.warningStatus(3L * gib, false).isVisible());
        assertFalse(settings.warningStatus(3L * gib, false).isRecordingBlocked());
        assertFalse(activeStorage.preserveRecordingVolume);
    }

    @Test void exactDynamicMinimumStillAllowsRecording() {
        FakePartitionPreferences preferences = new FakePartitionPreferences();
        long dynamicMinimum = 3L * 1024L * 1024L * 1024L;
        FakeActiveStorageSource activeStorage = new FakeActiveStorageSource(
                new StorageVolumeStatus(MediaPartitionLocation.INTERNAL, true,
                        dynamicMinimum, dynamicMinimum));
        StorageSettingsUseCase settings = settings(preferences, activeStorage);

        assertTrue(settings.warningStatus(dynamicMinimum, false).isVisible());
        assertFalse(settings.warningStatus(dynamicMinimum, false).isRecordingBlocked());
    }

    @Test void warningCanRemainHiddenAboveBothThresholds() {
        FakePartitionPreferences preferences = new FakePartitionPreferences();
        long fiveGb = 5L * 1024L * 1024L * 1024L;
        FakeActiveStorageSource activeStorage = new FakeActiveStorageSource(
                new StorageVolumeStatus(MediaPartitionLocation.EXTERNAL, true,
                        10L * fiveGb, fiveGb));
        StorageSettingsUseCase settings = settings(preferences, activeStorage);

        assertEquals(MediaPartitionLocation.AUTO, settings.currentMode());
        assertFalse(settings.warningStatus(
                CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES, false).isVisible());
        assertFalse(settings.warningStatus(
                CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES, false)
                .isRecordingBlocked());
        assertEquals(fiveGb, settings.warningStatus(
                CaptureStorageCapacityPolicy.MIN_CAPTURE_FREE_BYTES, false).getFreeBytes());
    }

    private static StorageSettingsUseCase settings(
            FakePartitionPreferences preferences, FakeActiveStorageSource activeStorage) {
        StorageCapacitySource capacities = () -> List.of(activeStorage.activeStorageVolume());
        StorageWarningPreferenceStore warnings = new StorageWarningPreferenceStore() {
            private int warningGb = 2;
            @Override public int warningGb() { return warningGb; }
            @Override public void setWarningGb(int value) { warningGb = value; }
        };
        return new StorageSettingsUseCase(
                preferences, capacities, warnings, activeStorage, preferences);
    }

    private static final class FakeActiveStorageSource implements ActiveStorageSource {
        private final StorageVolumeStatus volume;
        private long requiredBytes;
        private boolean preserveRecordingVolume;

        private FakeActiveStorageSource(StorageVolumeStatus volume) {
            this.volume = volume;
        }

        @Override public StorageVolumeStatus activeStorageVolume() { return volume; }

        @Override public StorageVolumeStatus activeStorageVolume(
                long requiredBytes, boolean preserveRecordingVolume) {
            this.requiredBytes = requiredBytes;
            this.preserveRecordingVolume = preserveRecordingVolume;
            return volume;
        }
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




