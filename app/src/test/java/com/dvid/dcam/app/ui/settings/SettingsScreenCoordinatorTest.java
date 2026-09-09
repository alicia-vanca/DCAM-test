package com.dvid.dcam.app.ui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.content.ContextWrapper;
import com.dvid.dcam.R;
import com.dvid.dcam.app.devmode.DeveloperFeatureToggles;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.core.featuregate.application.FeatureGates;
import com.dvid.dcam.core.featuregate.application.port.FeatureGateStore;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.HardwareButtonBinding;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.core.input.domain.PhysicalButtonType;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.domain.AudioFileFormat;
import com.dvid.dcam.feature.input.application.port.HardwareButtonSettings;
import com.dvid.dcam.feature.input.application.usecase.ConfigureHardwareButtonsUseCase;
import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.application.port.LocationControlGateway;
import com.dvid.dcam.feature.location.application.usecase.LocationControlUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationSettingsUseCase;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import com.dvid.dcam.feature.settings.application.port.LanguagePreferenceStore;
import com.dvid.dcam.feature.settings.application.port.MediaEncryptionPreferenceStore;
import com.dvid.dcam.feature.settings.application.usecase.LanguageSettingsUseCase;
import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCase;
import com.dvid.dcam.feature.settings.domain.AppLanguage;
import com.dvid.dcam.feature.storage.application.port.ActiveStoragePolicyGateway;
import com.dvid.dcam.feature.storage.application.port.ActiveStorageSource;
import com.dvid.dcam.feature.storage.application.port.MediaPartitionLocationPreferenceStore;
import com.dvid.dcam.feature.storage.application.port.StorageCapacitySource;
import com.dvid.dcam.feature.storage.application.port.StorageWarningPreferenceStore;
import com.dvid.dcam.feature.storage.application.usecase.StorageSettingsUseCase;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class SettingsScreenCoordinatorTest {
    private static final int BACK = 4;
    private static final int CAMERA = 27;

    @Test void invalidationSubscriptionIsIdentitySafeAndCloseRejectsFurtherUse() {
        Harness harness = new Harness();
        List<SettingsScreenCoordinator.Invalidation> first = new ArrayList<>();
        List<SettingsScreenCoordinator.Invalidation> second = new ArrayList<>();
        SettingsScreenCoordinator.Subscription firstSubscription =
                harness.coordinator.observeInvalidations(first::add);
        harness.coordinator.observeInvalidations(second::add);

        assertTrue(harness.coordinator.onKeyDown(CAMERA));
        firstSubscription.close();
        firstSubscription.close();
        assertTrue(harness.coordinator.onKeyUp(CAMERA));

        assertEquals(List.of(SettingsScreenCoordinator.Invalidation.CONSOLE), first);
        assertEquals(List.of(SettingsScreenCoordinator.Invalidation.CONSOLE,
                SettingsScreenCoordinator.Invalidation.CONSOLE), second);

        harness.coordinator.close();
        assertThrows(IllegalStateException.class,
                () -> harness.coordinator.observeInvalidations(ignored -> {}));
        assertThrows(IllegalStateException.class,
                () -> harness.coordinator.onKeyDown(CAMERA));
    }

    @Test void developerKeyConsoleCapturesDownAndUpButBackPassesThrough() {
        Harness harness = new Harness();

        assertTrue(harness.coordinator.onKeyDown(CAMERA));
        assertTrue(harness.coordinator.onKeyUp(CAMERA));
        assertFalse(harness.coordinator.onKeyDown(BACK));
        assertFalse(harness.coordinator.onKeyUp(BACK));

        assertEquals("CAMERA DOWN\nCAMERA UP\nBACK DOWN\nBACK UP",
                harness.coordinator.buttonConsoleText());
    }

    @Test void buttonSelectionAndResetApplyTheControllerResult() {
        Harness harness = new Harness();

        assertEquals(6, harness.coordinator.model(
                MainScreen.DEVELOPER_BUTTON_BINDINGS).getSections().size());
        harness.coordinator.selectSetting(MainScreen.DEVELOPER_BUTTON_BINDINGS,
                SettingId.DEV_BUTTON_RECORD_TYPE, 1);

        assertEquals(1, harness.settings.typeSelection);
        assertEquals(harness.settings.layout, harness.platform.appliedLayouts.get(0));
        assertEquals("Button binding applied", harness.platform.notices.get(0));

        harness.coordinator.resetButtonDefaults();

        assertEquals(harness.defaults, harness.settings.layout);
        assertEquals(harness.defaults, harness.platform.appliedLayouts.get(1));
        assertEquals("Device button defaults restored", harness.platform.notices.get(1));
    }

    @Test void cameraPipelineResultsMapToStableNotices() {
        assertEquals(R.string.camera_pipeline_applied,
                SettingsScreenCoordinator.cameraPipelineNotice(
                        SettingsRuntime.CameraPipelineModeResult.APPLIED));
        assertEquals(R.string.camera_pipeline_busy,
                SettingsScreenCoordinator.cameraPipelineNotice(
                        SettingsRuntime.CameraPipelineModeResult.REJECTED_BUSY));
        assertEquals(R.string.camera_pipeline_unknown,
                SettingsScreenCoordinator.cameraPipelineNotice(
                        SettingsRuntime.CameraPipelineModeResult.REJECTED_UNAVAILABLE));
        assertEquals(R.string.camera_pipeline_failed,
                SettingsScreenCoordinator.cameraPipelineNotice(
                        SettingsRuntime.CameraPipelineModeResult.FAILED));
    }

    private static final class Harness {
        private final HardwareButtonLayout defaults = new HardwareButtonLayout(
                new HardwareButtonBinding(ButtonRole.RECORD, CAMERA,
                        PhysicalButtonType.BUTTON));
        private final FakeSettings settings = new FakeSettings();
        private final FakePlatformActions platform = new FakePlatformActions();
        private final SettingsScreenCoordinator coordinator;

        private Harness() {
            ConfigureHardwareButtonsUseCase useCase =
                    new ConfigureHardwareButtonsUseCase(settings, defaults);
            useCase.initialize();
            DeveloperButtonBindingsController bindings =
                    new DeveloperButtonBindingsController(useCase, 12,
                            keyCode -> keyCode == BACK ? "KEYCODE_BACK" : "KEYCODE_CAMERA");
            SettingsRuntime runtime = new FakeSettingsRuntime();
            LanguagePreferenceStore languageStore = new LanguagePreferenceStore() {
                private AppLanguage language = AppLanguage.SYSTEM;
                @Override public AppLanguage currentLanguage() { return language; }
                @Override public void selectLanguage(AppLanguage value) { language = value; }
            };
            MediaEncryptionPreferenceStore encryptionStore = new MediaEncryptionPreferenceStore() {
                private boolean enabled;
                @Override public boolean isMediaEncryptionEnabled() { return enabled; }
                @Override public void setMediaEncryptionEnabled(boolean value) { enabled = value; }
            };
            GpsSettingsStore gpsStore = new GpsSettingsStore() {
                private GpsSettings settings = new GpsSettings(GpsMode.FUSED, 1, 1,
                        LocationSystemState.DISABLED);
                @Override public GpsSettings load() { return settings; }
                @Override public void save(GpsSettings value) { settings = value; }
                @Override public void saveSystemState(LocationSystemState value) {
                    settings = settings.withSystemState(value);
                }
            };
            LocationControlGateway locationGateway = new LocationControlGateway() {
                private LocationSystemState state = LocationSystemState.DISABLED;
                @Override public LocationSystemState currentState() { return state; }
                @Override public boolean isModeAvailable(GpsMode mode) { return true; }
                @Override public boolean setEnabled(boolean enabled) {
                    LocationSystemState next = enabled
                            ? LocationSystemState.ENABLED : LocationSystemState.DISABLED;
                    boolean changed = state != next;
                    state = next;
                    return changed;
                }
            };
            MediaPartitionLocationPreferenceStore partitionStore =
                    new MediaPartitionLocationPreferenceStore() {
                        private MediaPartitionLocation mode = MediaPartitionLocation.INTERNAL;
                        @Override public MediaPartitionLocation currentMediaPartitionLocation() {
                            return mode;
                        }
                        @Override public void selectMediaPartitionLocation(
                                MediaPartitionLocation value) { mode = value; }
                    };
            StorageSettingsUseCase storage = new StorageSettingsUseCase(partitionStore,
                    (StorageCapacitySource) () -> List.of(),
                    new StorageWarningPreferenceStore() {
                        @Override public int warningGb() { return 2; }
                        @Override public void setWarningGb(int value) {}
                    },
                    new ActiveStorageSource() {
                        @Override public StorageVolumeStatus activeStorageVolume() { return null; }
                        @Override public StorageVolumeStatus activeStorageVolume(
                                long requiredBytes, boolean preserveRecordingVolume) { return null; }
                    },
                    (ActiveStoragePolicyGateway) value -> {});
            DeveloperFeatureToggles toggles = DeveloperFeatureToggles.createDefault(
                    new FeatureGates(new FeatureGateStore() {
                        private final EnumMap<FeatureGate, Boolean> values = new EnumMap<>(FeatureGate.class);
                        @Override public boolean isEnabled(FeatureGate gate) {
                            return values.getOrDefault(gate, true);
                        }
                        @Override public void setEnabled(FeatureGate gate, boolean enabled) {
                            values.put(gate, enabled);
                        }
                    }));
            coordinator = new SettingsScreenCoordinator(
                    new ContextWrapper(null), new SettingsScreenCatalog(), runtime,
                    new LanguageSettingsUseCase(languageStore),
                    new MediaEncryptionSettingsUseCase(encryptionStore, () -> true), storage,
                    new LocationSettingsUseCase(gpsStore, ignored -> true),
                    new LocationControlUseCase(locationGateway, gpsStore), toggles, bindings,
                    new NoOpLogger(), platform);
        }
    }

    private static final class FakeSettingsRuntime implements SettingsRuntime {
        @Override public AudioFileFormat audioFileFormat() { return AudioFileFormat.AAC; }
        @Override public void setAudioFileFormat(AudioFileFormat format) {}
        @Override public com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore.Mode
                cameraPipelineMode(String cameraId) {
            return com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore.Mode.AUTO;
        }
        @Override public boolean releaseCameraWhenScreenOff() { return false; }
        @Override public void setReleaseCameraWhenScreenOff(boolean enabled) {}
        @Override public boolean resourceMonitorEnabled() { return false; }
        @Override public void setResourceMonitorEnabled(boolean enabled) {}
        @Override public List<SettingsRuntime.CameraPipelineSelection> cameraPipelineSelections() {
            return List.of();
        }
        @Override public List<SettingsRuntime.CameraPipelineSelection> cameraPipelineAutoSelections() {
            return List.of();
        }
        @Override public boolean cameraCapabilitiesFullyVerified() { return false; }
        @Override public List<String> cameraPipelineCameraIds() { return List.of(); }
        @Override public OptionalInt cameraPipelineVerifiedTupleCount(String cameraId,
                com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore.Mode mode) {
            return OptionalInt.empty();
        }
        @Override public boolean canSelectCameraPipelineMode() { return false; }
        @Override public boolean canSelectCameraPipelineMode(String cameraId) { return false; }
        @Override public boolean cameraPipelineModeControlEnabled(String cameraId) { return false; }
        @Override public OptionalInt cameraPipelineCaptureTupleCount(String cameraId,
                com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore.Mode mode) {
            return OptionalInt.empty();
        }
        @Override public boolean selectCameraPipelineModeForSettings(
                com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore.Mode mode,
                Consumer<SettingsRuntime.CameraPipelineModeResult> completion) { return false; }
        @Override public boolean selectCameraPipelineModeForSettings(String cameraId,
                com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore.Mode mode,
                Consumer<SettingsRuntime.CameraPipelineModeResult> completion) { return false; }
        @Override public boolean resetCameraCapabilities() { return false; }
        @Override public boolean cameraCapabilityRecheckControlEnabled() { return false; }
        @Override public boolean cameraCapabilityRecheckInFlight() { return false; }
        @Override public void readStorageVolumes(Consumer<List<StorageVolumeStatus>> callback) {
            callback.accept(List.of());
        }
        @Override public com.dvid.dcam.app.ui.settings.camera.CameraSettingsPresentationState
                cameraSettings(boolean recording) { return null; }
    }

    private static final class NoOpLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }

    private static final class FakeSettings implements HardwareButtonSettings {
        private HardwareButtonLayout layout = HardwareButtonLayout.empty();
        private int typeSelection = -1;

        @Override public List<String> keyCodeLabels() {
            return List.of("Unassigned", "KEYCODE_CAMERA");
        }
        @Override public List<String> typeLabels() { return List.of("Button", "Switch"); }
        @Override public List<String> sourceLabels() {
            return List.of("Firmware broadcast", "Key event");
        }
        @Override public List<String> actionFamilyLabels() {
            return List.of("Unassigned", "ACTION_CAMERA");
        }
        @Override public int selectedKeyCodeIndex(ButtonRole role) { return 0; }
        @Override public int selectedTypeIndex(ButtonRole role) { return 0; }
        @Override public int selectedSourceIndex(ButtonRole role) { return 0; }
        @Override public int selectedActionFamilyIndex(ButtonRole role) { return 0; }
        @Override public void initialize(HardwareButtonLayout defaults) { layout = defaults; }
        @Override public boolean resetToDefaults(HardwareButtonLayout defaults) {
            if (defaults.isEmpty()) return false;
            layout = defaults;
            return true;
        }
        @Override public void selectKeyCode(ButtonRole role, int selectedIndex) {}
        @Override public void selectType(ButtonRole role, int selectedIndex) {
            typeSelection = selectedIndex;
        }
        @Override public void selectSource(ButtonRole role, int selectedIndex) {}
        @Override public void selectActionFamily(ButtonRole role, int selectedIndex) {}
        @Override public HardwareButtonLayout loadLayout() { return layout; }
    }

    private static final class FakePlatformActions
            implements SettingsScreenCoordinator.PlatformActions {
        private final List<HardwareButtonLayout> appliedLayouts = new ArrayList<>();
        private final List<String> notices = new ArrayList<>();

        @Override public boolean isVideoRecording() { return false; }
        @Override public boolean canOpenCameraSetting(String stableId) { return false; }
        @Override public boolean selectCameraSetting(String stableId, int selectedIndex) {
            return false;
        }
        @Override public boolean isCameraRecording() { return false; }
        @Override public boolean hasRequiredLocationPermission() { return false; }
        @Override public void requestLocationPermission() {}
        @Override public void openLocationSettings() {}
        @Override public void refreshLocationTracking() {}
        @Override public void stopLocationTracking() {}
        @Override public void onAuthenticationSettingChanged() {}
        @Override public void setResourceMonitorEnabled(boolean enabled) {}
        @Override public void onReleaseCameraWhenScreenOffChanged(boolean enabled) {}
        @Override public void setFullScreenDisplayEnabled(boolean enabled) {}
        @Override public boolean setAutoRotateEnabled(boolean enabled) { return false; }
        @Override public boolean isAutoRotateEnabled() { return false; }
        @Override public boolean canWriteSystemSettings() { return false; }
        @Override public void applyAutoRotate(boolean enabled) {}
        @Override public void requestWriteSystemSettingsAccess() {}
        @Override public boolean setWifiEnabled(boolean enabled) { return false; }
        @Override public boolean isWifiEnabled() { return false; }
        @Override public void openWifiSettings() {}
        @Override public void logout() {}
        @Override public void recreateActivity() {}
        @Override public void navigate(MainScreen screen) {}
        @Override public void applyHardwareButtonLayout(HardwareButtonLayout layout) {
            appliedLayouts.add(layout);
        }
        @Override public void showNotice(int messageResource) {
            notices.add(Integer.toString(messageResource));
        }
        @Override public void showNotice(String message) { notices.add(message); }
        @Override public boolean isDeviceOwner() { return false; }
        @Override public SettingsScreenCoordinator.RemoveDeviceOwnerResult removeDeviceOwner() {
            return SettingsScreenCoordinator.RemoveDeviceOwnerResult.UNAVAILABLE;
        }
        @Override public void runOnUiThread(Runnable action) { action.run(); }
        @Override public boolean isFinishingOrDestroyed() { return false; }
    }
}
