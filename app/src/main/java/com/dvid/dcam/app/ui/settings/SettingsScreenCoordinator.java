package com.dvid.dcam.app.ui.settings;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import com.dvid.dcam.BuildConfig;
import com.dvid.dcam.R;
import com.dvid.dcam.app.devmode.DeveloperFeatureToggles;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.app.ui.StorageSizeFormatter;
import com.dvid.dcam.app.ui.settings.camera.CameraDeveloperSettingsPresentation;
import com.dvid.dcam.app.ui.settings.camera.CameraSettingControlId;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.capture.domain.AudioCaptureSettings;
import com.dvid.dcam.feature.capture.domain.AudioFileFormat;
import com.dvid.dcam.feature.device.application.port.DeveloperSettingsStore;
import com.dvid.dcam.feature.location.application.usecase.LocationControlUseCase;
import com.dvid.dcam.feature.location.application.usecase.LocationSettingsUseCase;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import com.dvid.dcam.feature.settings.application.usecase.LanguageSettingsUseCase;
import com.dvid.dcam.feature.settings.application.usecase.MediaEncryptionSettingsUseCase;
import com.dvid.dcam.feature.settings.domain.AppLanguage;
import com.dvid.dcam.feature.storage.application.usecase.StorageSettingsUseCase;
import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;
import com.dvid.dcam.feature.storage.domain.StorageVolumeStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Activity-lifetime, view-independent owner of settings state, policy, and actions. */
public final class SettingsScreenCoordinator implements AutoCloseable {
    private static final int DEV_MODE_UNLOCK_TAPS = 7;
    private static final long DEV_MODE_UNLOCK_WINDOW_MS = 5_000L;
    private static final String UNKNOWN_VALUE = "unknown";
    private static final List<DeveloperSettingsStore.Mode> CAMERA_PIPELINE_MODES = List.of(
            DeveloperSettingsStore.Mode.AUTO, DeveloperSettingsStore.Mode.A,
            DeveloperSettingsStore.Mode.B);

    public enum Invalidation { ROWS, STORAGE_USAGE, CONSOLE }
    public enum RemoveDeviceOwnerResult { REMOVED, UNAVAILABLE, FAILED }

    public interface Listener {
        void onInvalidated(Invalidation invalidation);
    }

    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

    /** Activity/runtime operations that cannot be owned by a view-independent coordinator. */
    public interface PlatformActions {
        boolean isVideoRecording();
        boolean canOpenCameraSetting(String stableId);
        boolean selectCameraSetting(String stableId, int selectedIndex);
        boolean isCameraRecording();
        boolean hasRequiredLocationPermission();
        void requestLocationPermission();
        void openLocationSettings();
        void refreshLocationTracking();
        void stopLocationTracking();
        void onAuthenticationSettingChanged();
        void setResourceMonitorEnabled(boolean enabled);
        void onReleaseCameraWhenScreenOffChanged(boolean enabled);
        void setFullScreenDisplayEnabled(boolean enabled);
        boolean setAutoRotateEnabled(boolean enabled);
        boolean isAutoRotateEnabled();
        boolean canWriteSystemSettings();
        void applyAutoRotate(boolean enabled);
        void requestWriteSystemSettingsAccess();
        boolean setWifiEnabled(boolean enabled);
        boolean isWifiEnabled();
        void openWifiSettings();
        void logout();
        void recreateActivity();
        void navigate(MainScreen screen);
        void applyHardwareButtonLayout(HardwareButtonLayout layout);
        void showNotice(int messageResource);
        void showNotice(String message);
        boolean isDeviceOwner();
        RemoveDeviceOwnerResult removeDeviceOwner();
        void runOnUiThread(Runnable action);
        boolean isFinishingOrDestroyed();
    }

    private final Context context;
    private final SettingsScreenCatalog catalog;
    private final SettingsRuntime runtime;
    private final LanguageSettingsUseCase languageSettings;
    private final MediaEncryptionSettingsUseCase mediaEncryptionSettings;
    private final StorageSettingsUseCase storageSettings;
    private final LocationSettingsUseCase locationSettings;
    private final LocationControlUseCase locationControl;
    private final DeveloperFeatureToggles developerFeatureToggles;
    private final DeveloperButtonBindingsController buttonBindings;
    private final Logger logger;
    private final PlatformActions platform;
    private final SettingsUiState settingsUiState;
    private final List<Listener> listeners = new ArrayList<>();
    private final AtomicBoolean storageVolumesRefreshInFlight = new AtomicBoolean();
    private AppLanguage[] renderedLanguages = new AppLanguage[0];
    private List<StorageVolumeStatus> storageVolumes = List.of();
    private boolean storageVolumesStale = true;
    private long storageVolumesGeneration;
    private int devModeTapCount;
    private long devModeTapWindowStartedAtMs;
    private Boolean pendingAutoRotateValue;
    private Boolean pendingLocationEnabled;
    private Boolean lastVideoRecording;
    private MainScreen activeScreen;
    private boolean closed;

    public SettingsScreenCoordinator(Context context, SettingsScreenCatalog catalog,
            SettingsRuntime runtime, LanguageSettingsUseCase languageSettings,
            MediaEncryptionSettingsUseCase mediaEncryptionSettings,
            StorageSettingsUseCase storageSettings, LocationSettingsUseCase locationSettings,
            LocationControlUseCase locationControl,
            DeveloperFeatureToggles developerFeatureToggles,
            DeveloperButtonBindingsController buttonBindings, Logger logger,
            PlatformActions platform) {
        this.context = Objects.requireNonNull(context, "context");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.languageSettings = Objects.requireNonNull(languageSettings, "languageSettings");
        this.mediaEncryptionSettings = Objects.requireNonNull(
                mediaEncryptionSettings, "mediaEncryptionSettings");
        this.storageSettings = Objects.requireNonNull(storageSettings, "storageSettings");
        this.locationSettings = Objects.requireNonNull(locationSettings, "locationSettings");
        this.locationControl = Objects.requireNonNull(locationControl, "locationControl");
        this.developerFeatureToggles = Objects.requireNonNull(
                developerFeatureToggles, "developerFeatureToggles");
        this.buttonBindings = Objects.requireNonNull(buttonBindings, "buttonBindings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.platform = Objects.requireNonNull(platform, "platform");
        settingsUiState = new SettingsUiState(
                mediaEncryptionSettings.isMediaEncryptionEnabled(),
                storageSettings.supportedModes().indexOf(storageSettings.currentMode()),
                platform.isAutoRotateEnabled(), platform.isWifiEnabled());
        settingsUiState.setLowStorageWarningGb(storageSettings.warningGb());
    }

    public SettingsScreenCatalog catalog() { return catalog; }

    public boolean isFullScreenDisplayEnabled() {
        requireOpen();
        return settingsUiState.isFullScreenDisplayEnabled();
    }

    public SettingsScreenModel model(MainScreen screen) {
        requireSettingsScreen(screen);
        if (screen == MainScreen.DEVELOPER_BUTTON_BINDINGS) return buttonBindings.model();
        requireOpen();
        return settingsModel(screen);
    }

    public Subscription observeInvalidations(Listener listener) {
        requireOpen();
        Listener checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        return new Subscription() {
            private boolean removed;
            @Override public void close() {
                if (removed) return;
                removed = true;
                listeners.remove(checked);
            }
        };
    }

    public void onScreenEntered(MainScreen screen) {
        requireOpen();
        activeScreen = Objects.requireNonNull(screen, "screen");
        if (screen == MainScreen.STORAGE_SETTINGS) markStorageVolumesStale();
    }

    public void onCaptureStateChanged(boolean videoRecording) {
        requireOpen();
        if (lastVideoRecording != null && lastVideoRecording == videoRecording) return;
        lastVideoRecording = videoRecording;
        if (activeScreen == MainScreen.DEVELOPER_SETTINGS
                || activeScreen == MainScreen.RECORD_SETTINGS
                || activeScreen == MainScreen.CAMERA_SETTINGS) {
            notifyListeners(Invalidation.ROWS);
        }
    }

    public void onWifiStateChanged(boolean enabled) {
        requireOpen();
        settingsUiState.updateBoolean(SettingId.WIFI_ENABLED, enabled);
        if (activeScreen == MainScreen.DEVICE_SETTINGS) notifyListeners(Invalidation.ROWS);
    }

    public void onLocationSystemStateChanged() {
        requireOpen();
        if (activeScreen == MainScreen.GPS_SETTINGS
                || activeScreen == MainScreen.DEVELOPER_SETTINGS) {
            notifyListeners(Invalidation.ROWS);
        }
    }

    public void onStorageMounted() {
        requireOpen();
        markStorageVolumesStale();
        if (activeScreen == MainScreen.STORAGE_SETTINGS) notifyListeners(Invalidation.ROWS);
    }

    public void onAutoRotateStateChanged(boolean enabled) {
        requireOpen();
        settingsUiState.updateBoolean(SettingId.AUTO_ROTATE, enabled);
        platform.applyAutoRotate(enabled);
        if (activeScreen == MainScreen.DEVICE_SETTINGS) notifyListeners(Invalidation.ROWS);
    }

    public void onCameraCapabilityChanged() {
        requireOpen();
        if (activeScreen == MainScreen.DEVELOPER_SETTINGS
                || activeScreen == MainScreen.RECORD_SETTINGS
                || activeScreen == MainScreen.CAMERA_SETTINGS) {
            notifyListeners(Invalidation.ROWS);
        }
    }

    public void onResume() {
        requireOpen();
        if (pendingAutoRotateValue != null && platform.canWriteSystemSettings()) {
            boolean requested = pendingAutoRotateValue;
            SettingItem item = currentSetting(SettingId.AUTO_ROTATE);
            if (platform.setAutoRotateEnabled(requested)) {
                pendingAutoRotateValue = null;
                logSettingChanged(item, SettingId.AUTO_ROTATE, enabledValue(requested));
            }
        }
        onAutoRotateStateChanged(platform.isAutoRotateEnabled());
        onWifiStateChanged(platform.isWifiEnabled());
        if (pendingLocationEnabled != null) {
            boolean requested = pendingLocationEnabled;
            pendingLocationEnabled = null;
            LocationSystemState state = locationControl.currentState();
            if (state == (requested
                    ? LocationSystemState.ENABLED : LocationSystemState.DISABLED)) {
                logSettingChanged(currentSetting(SettingId.GPS_LOCATION_ENABLED),
                        SettingId.GPS_LOCATION_ENABLED, enabledValue(requested));
            }
            onLocationSystemStateChanged();
        }
    }

    public boolean canOpenCameraSetting(String stableId) {
        requireOpen();
        if (platform.canOpenCameraSetting(stableId)) return true;
        platform.showNotice(cameraSettingBlockedNotice());
        return false;
    }

    public void selectCameraSetting(String stableId, int selectedIndex) {
        requireOpen();
        SettingItem item = currentSetting(stableId);
        if (!platform.selectCameraSetting(stableId, selectedIndex)) {
            notifyListeners(Invalidation.ROWS);
            platform.showNotice(cameraSettingBlockedNotice());
            return;
        }
        notifyListeners(Invalidation.ROWS);
        logSelectedSettingChanged(item, stableId, selectedIndex);
    }

    public boolean canSelectDeveloperSetting(String stableId) {
        requireOpen();
        Optional<String> cameraId = CameraDeveloperSettingsPresentation
                .CameraPipelineSelectionUiState.cameraId(stableId);
        if (cameraId.isEmpty()
                || runtime.canSelectCameraPipelineMode(cameraId.orElseThrow())) return true;
        platform.showNotice(R.string.camera_pipeline_busy);
        return false;
    }

    public void selectDeveloperSetting(String stableId, int selectedIndex) {
        requireOpen();
        Optional<String> cameraId = CameraDeveloperSettingsPresentation
                .CameraPipelineSelectionUiState.cameraId(stableId);
        if (cameraId.isPresent()) {
            selectCameraPipelineMode(cameraId.orElseThrow(), selectedIndex);
            return;
        }
        SettingId id = settingId(stableId);
        if (id != null) selectSetting(MainScreen.DEVELOPER_SETTINGS, id, selectedIndex);
    }

    public void updateDeveloperNumberSetting(String stableId, int value) {
        requireOpen();
        SettingId id = settingId(stableId);
        if (id != null) updateNumberSetting(id, value);
    }

    public void updateDeveloperBooleanSetting(String stableId, boolean checked) {
        requireOpen();
        SettingId id = settingId(stableId);
        if (id != null) updateBooleanSetting(id, checked);
    }

    public void performDeveloperSettingAction(String stableId) {
        requireOpen();
        SettingId id = settingId(stableId);
        if (id != null) performSettingAction(id);
    }

    public boolean canSelectSetting(SettingId id) {
        requireOpen();
        if (id != SettingId.DEV_PIPELINE_MODE || runtime.canSelectCameraPipelineMode()) {
            return true;
        }
        platform.showNotice(R.string.camera_pipeline_busy);
        return false;
    }

    public void selectSetting(MainScreen screen, SettingId id, int selectedIndex) {
        requireSettingsScreen(screen);
        if (screen == MainScreen.DEVELOPER_BUTTON_BINDINGS) {
            platform.applyHardwareButtonLayout(buttonBindings.select(id, selectedIndex));
            platform.showNotice("Button binding applied");
            notifyListeners(Invalidation.ROWS);
            return;
        }
        requireOpen();
        selectSetting(id, selectedIndex);
    }

    public void updateNumberSetting(SettingId id, int value) {
        requireOpen();
        SettingItem item = currentSetting(id);
        if (id == SettingId.GPS_UPDATE_DISTANCE_METERS) {
            locationSettings.changeUpdateDistanceMeters(value);
            platform.refreshLocationTracking();
            logNumberSettingChanged(item, id, value);
            return;
        }
        if (id == SettingId.GPS_REPORT_INTERVAL_SECONDS) {
            locationSettings.changeReportIntervalSeconds(value);
            platform.refreshLocationTracking();
            logNumberSettingChanged(item, id, value);
            return;
        }
        settingsUiState.updateNumber(id, value);
        if (id == SettingId.LOW_STORAGE_WARNING_GB) storageSettings.changeWarningGb(value);
        logNumberSettingChanged(item, id, value);
    }

    public void updateBooleanSetting(SettingId id, boolean checked) {
        requireOpen();
        SettingItem item = currentSetting(id);
        if (developerFeatureToggles.setEnabled(id, checked)) {
            if (id == SettingId.FEATURE_AUTHENTICATION) {
                platform.onAuthenticationSettingChanged();
                logBooleanSettingChanged(item, id, checked);
                return;
            }
            if (id == SettingId.FEATURE_GPS) {
                if (checked) platform.refreshLocationTracking();
                else platform.stopLocationTracking();
            }
            notifyListeners(Invalidation.ROWS);
            logBooleanSettingChanged(item, id, checked);
            return;
        }
        if (id == SettingId.RESOURCE_MONITOR) {
            runtime.setResourceMonitorEnabled(checked);
            platform.setResourceMonitorEnabled(checked);
            logBooleanSettingChanged(item, id, checked);
            return;
        }
        if (id == SettingId.DEV_RELEASE_CAMERA_WHEN_SCREEN_OFF) {
            runtime.setReleaseCameraWhenScreenOff(checked);
            platform.onReleaseCameraWhenScreenOffChanged(checked);
            logBooleanSettingChanged(item, id, checked);
            return;
        }
        if (id == SettingId.GPS_LOCATION_ENABLED) {
            if (handleLocationSwitchChanged(checked)) {
                logBooleanSettingChanged(item, id, checked);
            }
            return;
        }
        if (id == SettingId.FULL_SCREEN_DISPLAY) {
            settingsUiState.updateBoolean(id, checked);
            platform.setFullScreenDisplayEnabled(checked);
            logBooleanSettingChanged(item, id, checked);
            return;
        }
        settingsUiState.updateBoolean(id, checked);
        if (id == SettingId.ENCRYPT_VIDEO_FILES) {
            mediaEncryptionSettings.setMediaEncryptionEnabled(checked);
        }
        if (id == SettingId.AUTO_ROTATE) {
            if (platform.setAutoRotateEnabled(checked)) {
                platform.applyAutoRotate(checked);
                logBooleanSettingChanged(item, id, checked);
            } else {
                pendingAutoRotateValue = checked;
                onAutoRotateStateChanged(platform.isAutoRotateEnabled());
                platform.requestWriteSystemSettingsAccess();
            }
            return;
        }
        if (id == SettingId.WIFI_ENABLED) {
            if (!platform.setWifiEnabled(checked)) {
                settingsUiState.updateBoolean(id, platform.isWifiEnabled());
                notifyListeners(Invalidation.ROWS);
                platform.showNotice(context.getString(R.string.wifi_requires_device_owner));
                return;
            }
            logBooleanSettingChanged(item, id, checked);
            return;
        }
        logBooleanSettingChanged(item, id, checked);
    }

    public void performSettingAction(SettingId id) {
        requireOpen();
        if (id == SettingId.RECHECK_CAMERA_CAPABILITIES) {
            if (!runtime.resetCameraCapabilities()) {
                platform.showNotice(R.string.camera_capabilities_recheck_busy);
                return;
            }
            logger.info(LogCategory.CAPABILITY, "unspecified", "Started camera capability check from developer settings.");
            return;
        }
        if (id == SettingId.WIFI_CONNECT) {
            platform.openWifiSettings();
            logger.info(LogCategory.CONFIG, "unspecified", "Opened Android Wi-Fi settings from device settings.");
            return;
        }
        if (id == SettingId.LOGOUT) {
            platform.logout();
            logger.info(LogCategory.AUTH, "operator_logged_out", "Logged out current operator from security settings.");
            return;
        }
        if (id == SettingId.CHANGE_OPERATOR_ID || id == SettingId.CHANGE_OPERATOR_PASSWORD) {
            platform.showNotice(R.string.account_change_pending);
            return;
        }
        throw new IllegalArgumentException("Setting " + id + " is not an action");
    }

    public boolean hasButtonDefaults() {
        requireOpen();
        return buttonBindings.hasDefaults();
    }

    public void resetButtonDefaults() {
        requireOpen();
        platform.applyHardwareButtonLayout(buttonBindings.resetDefaults());
        platform.showNotice("Device button defaults restored");
        notifyListeners(Invalidation.ROWS);
    }

    public String buttonConsoleText() {
        requireOpen();
        return buttonBindings.consoleText();
    }

    public boolean onKeyDown(int keyCode) {
        requireOpen();
        boolean consumed = buttonBindings.onKeyDown(keyCode);
        notifyListeners(Invalidation.CONSOLE);
        return consumed;
    }

    public boolean onKeyUp(int keyCode) {
        requireOpen();
        boolean consumed = buttonBindings.onKeyUp(keyCode);
        notifyListeners(Invalidation.CONSOLE);
        return consumed;
    }

    public void onAboutSecretTap() {
        requireOpen();
        long now = System.currentTimeMillis();
        if (now - devModeTapWindowStartedAtMs > DEV_MODE_UNLOCK_WINDOW_MS) {
            devModeTapWindowStartedAtMs = now;
            devModeTapCount = 0;
        }
        devModeTapCount++;
        if (devModeTapCount < DEV_MODE_UNLOCK_TAPS) return;
        devModeTapCount = 0;
        devModeTapWindowStartedAtMs = 0L;
        platform.showNotice(R.string.developer_mode_unlocked);
        platform.navigate(MainScreen.DEVELOPER_SETTINGS);
    }

    public boolean isDeviceOwner() {
        requireOpen();
        return platform.isDeviceOwner();
    }

    public RemoveDeviceOwnerResult removeDeviceOwner() {
        requireOpen();
        return platform.removeDeviceOwner();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        storageVolumesGeneration++;
        storageVolumesStale = true;
        listeners.clear();
    }

    private SettingsScreenModel settingsModel(MainScreen screen) {
        if (screen == MainScreen.DEVELOPER_SETTINGS) return developerSettingsModel();
        SettingsScreenModel model;
        if (screen == MainScreen.RECORD_SETTINGS || screen == MainScreen.CAMERA_SETTINGS) {
            var cameraPresentation = runtime.cameraSettings(platform.isVideoRecording());
            model = screen == MainScreen.RECORD_SETTINGS
                    ? settingsUiState.recording(cameraPresentation,
                            context.getString(R.string.record_resolution),
                            context.getString(R.string.frame_rate))
                    : settingsUiState.camera(cameraPresentation,
                            context.getString(R.string.photo_resolution),
                            context.getString(R.string.photo_recording_quality_notice),
                            context.getString(R.string.camera_status));
        } else if (screen == MainScreen.STORAGE_SETTINGS) {
            model = settingsUiState.storageWithVolumes(storageOptions(),
                    context.getString(R.string.storage_settings),
                    context.getString(R.string.default_storage),
                    context.getString(R.string.low_storage_warning));
        } else if (screen == MainScreen.USER_SETTINGS) {
            settingsUiState.setVideoEncryptionEnabled(
                    mediaEncryptionSettings.isMediaEncryptionEnabled());
            model = settingsUiState.security(
                    context.getString(R.string.operator_account),
                    context.getString(R.string.security_settings));
        } else if (screen == MainScreen.AUDIO_SETTINGS) {
            model = settingsUiState.audio(context.getString(R.string.available_settings),
                    visibleReadOnlySettings(screen), runtime.audioFileFormat(),
                    AudioCaptureSettings.SAMPLE_RATE_HZ / 1_000 + " kHz",
                    AudioCaptureSettings.BIT_RATE_BPS / 1_000 + " kbps",
                    AudioCaptureSettings.CHANNEL_COUNT == 1 ? "Mono"
                            : Integer.toString(AudioCaptureSettings.CHANNEL_COUNT),
                    alertVolumeLabel());
        } else if (screen == MainScreen.DEVICE_SETTINGS) {
            model = withLanguage(settingsUiState.device(
                    context.getString(R.string.device_settings_short),
                    context.getString(R.string.auto_rotate),
                    context.getString(R.string.wifi),
                    context.getString(R.string.connect_wifi)));
        } else if (screen == MainScreen.GPS_SETTINGS) {
            model = locationSettingsModel();
        } else if (screen == MainScreen.ABOUT) {
            model = aboutSettingsModel();
        } else {
            model = settingsUiState.readOnly(context.getString(R.string.available_settings),
                    context.getString(R.string.settings_value_unavailable),
                    visibleReadOnlySettings(screen));
        }
        return filterUnavailableSettings(screen, model);
    }

    private SettingsScreenModel developerSettingsModel() {
        GpsSettings current = locationSettings.currentSettings();
        List<GpsMode> modes = locationSettings.supportedModes();
        List<DescribedRadioOptionUiState> options = new ArrayList<>();
        for (GpsMode mode : modes) {
            options.add(gpsModeOption(mode, locationControl.isModeAvailable(mode)));
        }
        SettingItem provider = SettingItem.describedRadio(SettingId.GPS_POSITIONING_MODE,
                context.getString(R.string.gps_positioning_mode), options,
                Math.max(0, modes.indexOf(current.getMode())))
                .withEnabled(developerFeatureToggles.isEffectivelyEnabled(FeatureGate.GPS))
                .withIndentLevel(1);
        SettingsScreenModel base = developerFeatureToggles.developerSettings(
                SettingId.FEATURE_GPS, provider);
        List<SettingsSection> sections = new ArrayList<>(base.getSections());
        sections.add(new SettingsSection(context.getString(R.string.resource_monitor_section),
                List.of(SettingItem.checkbox(SettingId.RESOURCE_MONITOR,
                        context.getString(R.string.resource_monitor),
                        runtime.resourceMonitorEnabled())
                        .withDescription(context.getString(
                                R.string.resource_monitor_description)))));
        sections.add(new SettingsSection(context.getString(R.string.camera_lifecycle_section),
                List.of(SettingItem.checkbox(SettingId.DEV_RELEASE_CAMERA_WHEN_SCREEN_OFF,
                        context.getString(R.string.release_camera_when_screen_off),
                        runtime.releaseCameraWhenScreenOff())
                        .withDescription(context.getString(
                                R.string.release_camera_when_screen_off_description)))));
        List<CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState> selections =
                runtime.cameraPipelineCameraIds().stream()
                        .map(this::cameraPipelineSelection)
                        .collect(java.util.stream.Collectors.toList());
        boolean recheckBlocked = !runtime.cameraCapabilityRecheckControlEnabled()
                && !runtime.cameraCapabilityRecheckInFlight();
        sections.addAll(CameraDeveloperSettingsPresentation.screen(
                context.getString(R.string.camera_pipeline_section), selections,
                context.getString(R.string.recheck_camera_capabilities),
                recheckBlocked).getSections());
        return new SettingsScreenModel(sections);
    }

    private CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
            cameraPipelineSelection(String cameraId) {
        DeveloperSettingsStore.Mode selectedMode = runtime.cameraPipelineMode(cameraId);
        boolean fullyVerified = runtime.cameraCapabilitiesFullyVerified();
        List<DescribedRadioOptionUiState> options = List.of(
                cameraPipelineAutoOption(cameraId),
                cameraPipelineOption(cameraId, DeveloperSettingsStore.Mode.A,
                        R.string.camera_pipeline_a),
                cameraPipelineOption(cameraId, DeveloperSettingsStore.Mode.B,
                        R.string.camera_pipeline_b));
        Optional<CameraPipelineSelection> selected = runtime.cameraPipelineSelections()
                .stream().filter(value -> value.cameraId().equals(cameraId)).findFirst()
                .map(value -> new CameraPipelineSelection(
                        cameraPipelineLabel(value.pipelineId()), value.tupleCount()));
        String detail = selected.map(value -> cameraPipelineTupleDescription(
                value.tupleCount(), fullyVerified))
                .orElseGet(() -> context.getString(R.string.camera_pipeline_unknown));
        return new CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState(
                cameraId,
                selected.map(CameraPipelineSelection::label)
                        .orElseGet(() -> context.getString(R.string.camera_pipeline_unknown)),
                detail, options, Math.max(0, CAMERA_PIPELINE_MODES.indexOf(selectedMode)),
                !fullyVerified || runtime.cameraCapabilityRecheckInFlight()
                        || runtime.cameraPipelineModeControlEnabled(cameraId));
    }

    private DescribedRadioOptionUiState cameraPipelineAutoOption(String cameraId) {
        Optional<CameraPipelineSelection> selected = runtime.cameraPipelineAutoSelections()
                .stream().filter(value -> value.cameraId().equals(cameraId)).findFirst()
                .map(value -> new CameraPipelineSelection(
                        cameraPipelineLabel(value.pipelineId()), value.tupleCount()));
        String description = selected.map(CameraPipelineSelection::label)
                .orElseGet(() -> context.getString(R.string.camera_pipeline_auto_description));
        return new DescribedRadioOptionUiState(
                context.getString(R.string.camera_pipeline_auto), description);
    }

    private DescribedRadioOptionUiState cameraPipelineOption(String cameraId,
            DeveloperSettingsStore.Mode mode, int labelResource) {
        boolean fullyVerified = runtime.cameraCapabilitiesFullyVerified();
        OptionalInt verifiedTupleCount =
                runtime.cameraPipelineVerifiedTupleCount(cameraId, mode);
        OptionalInt fastTupleCount = runtime.cameraPipelineCaptureTupleCount(cameraId, mode);
        OptionalInt displayedTupleCount = fullyVerified ? verifiedTupleCount : fastTupleCount;
        String description = displayedTupleCount.isPresent()
                ? cameraPipelineTupleDescription(displayedTupleCount.getAsInt(), fullyVerified)
                : context.getString(R.string.camera_pipeline_unknown);
        boolean optionEnabled = !fullyVerified || verifiedTupleCount.isPresent()
                && verifiedTupleCount.getAsInt() > 0;
        return new DescribedRadioOptionUiState(
                context.getString(labelResource), description, optionEnabled);
    }

    private String cameraPipelineLabel(String pipelineId) {
        if (pipelineId != null && pipelineId.startsWith("a-")) {
            return context.getString(R.string.camera_pipeline_a);
        }
        if (pipelineId != null && pipelineId.startsWith("b-")) {
            return context.getString(R.string.camera_pipeline_b);
        }
        return pipelineId == null || pipelineId.isBlank()
                ? context.getString(R.string.camera_pipeline_unknown) : pipelineId;
    }

    private String cameraPipelineTupleDescription(int tupleCount, boolean fullyVerified) {
        return context.getString(fullyVerified ? R.string.camera_pipeline_selection_detail
                : R.string.camera_pipeline_fast_build_detail, tupleCount);
    }

    private SettingsScreenModel locationSettingsModel() {
        GpsSettings current = locationSettings.currentSettings();
        LocationSystemState systemState = locationControl.currentState();
        boolean systemStateKnown = systemState == LocationSystemState.ENABLED
                || systemState == LocationSystemState.DISABLED;
        List<SettingItem> items = List.of(
                SettingItem.checkbox(SettingId.GPS_LOCATION_ENABLED,
                        context.getString(R.string.gps_use_location),
                        systemState == LocationSystemState.ENABLED
                                && platform.hasRequiredLocationPermission())
                        .withEnabled(systemStateKnown),
                SettingItem.slider(SettingId.GPS_UPDATE_DISTANCE_METERS,
                        context.getString(R.string.gps_update_distance), 1, 30,
                        current.getUpdateDistanceMeters(), "m"),
                SettingItem.slider(SettingId.GPS_REPORT_INTERVAL_SECONDS,
                        context.getString(R.string.gps_report_interval), 1, 30,
                        current.getReportIntervalSeconds(), "s"));
        return new SettingsScreenModel(List.of(new SettingsSection(
                context.getString(R.string.gps_sampling_section), items)));
    }

    private DescribedRadioOptionUiState gpsModeOption(GpsMode mode, boolean enabled) {
        return switch (mode) {
            case FUSED -> new DescribedRadioOptionUiState(
                    context.getString(R.string.location_mode_fused),
                    context.getString(R.string.location_mode_fused_description), enabled);
            case SATELLITE -> new DescribedRadioOptionUiState(
                    context.getString(R.string.location_mode_gnss),
                    context.getString(R.string.location_mode_gnss_description), enabled);
        };
    }

    private List<StorageOptionUiState> storageOptions() {
        refreshStorageVolumes();
        List<StorageOptionUiState> options = new ArrayList<>();
        for (MediaPartitionLocation mode : storageSettings.supportedModes()) {
            if (mode == MediaPartitionLocation.AUTO) {
                options.add(new StorageOptionUiState(
                        context.getString(R.string.storage_auto),
                        context.getString(R.string.storage_auto_description), 0, true));
            } else {
                int label = mode == MediaPartitionLocation.INTERNAL
                        ? R.string.storage_internal : R.string.storage_external;
                options.add(storageOption(context.getString(label),
                        storageVolume(storageVolumes, mode)));
            }
        }
        return List.copyOf(options);
    }

    private StorageOptionUiState storageOption(String label, StorageVolumeStatus volume) {
        if (volume == null || !volume.isAvailable()) {
            return new StorageOptionUiState(label,
                    context.getString(R.string.storage_unavailable), 0, false);
        }
        long total = volume.getTotalBytes();
        int usedPercent = total <= 0L ? 0
                : (int) Math.round(volume.getUsedBytes() * 100.0 / total);
        String detail = context.getString(R.string.storage_usage,
                StorageSizeFormatter.gibibytes(volume.getUsedBytes()),
                StorageSizeFormatter.gibibytes(total));
        return new StorageOptionUiState(label, detail, usedPercent, true);
    }

    private SettingsScreenModel aboutSettingsModel() {
        String[] labels = visibleReadOnlySettings(MainScreen.ABOUT);
        String[] values = {BuildConfig.VERSION_NAME, Build.DISPLAY};
        return settingsUiState.readOnly(context.getString(R.string.available_settings),
                context.getString(R.string.settings_value_unavailable), labels, values);
    }

    private String alertVolumeLabel() {
        AudioManager audioManager = context.getSystemService(AudioManager.class);
        if (audioManager == null) {
            return context.getString(R.string.settings_value_unavailable);
        }
        return audioManager.getStreamVolume(AudioManager.STREAM_ALARM) + "/"
                + audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
    }

    private String[] visibleReadOnlySettings(MainScreen screen) {
        int itemsResource = catalog.itemsResource(screen).orElseThrow(
                () -> new IllegalArgumentException("No settings list for " + screen));
        String[] labels = context.getResources().getStringArray(itemsResource);
        List<String> visible = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            if (developerFeatureToggles.isReadOnlySettingEnabled(screen, i)) {
                visible.add(labels[i]);
            }
        }
        return visible.toArray(new String[0]);
    }

    private SettingsScreenModel filterUnavailableSettings(
            MainScreen screen, SettingsScreenModel model) {
        List<SettingsSection> sections = new ArrayList<>();
        for (SettingsSection section : model.getSections()) {
            List<SettingItem> visible = new ArrayList<>();
            for (SettingItem item : section.getItems()) {
                if (isSettingVisible(screen, item)) visible.add(item);
            }
            if (!visible.isEmpty()) sections.add(new SettingsSection(section.getTitle(), visible));
        }
        return new SettingsScreenModel(sections);
    }

    private boolean isSettingVisible(MainScreen screen, SettingItem item) {
        if (item.getId() == SettingId.FULL_SCREEN_DISPLAY && !platform.isDeviceOwner()) {
            return false;
        }
        if ((item.getId() == SettingId.WIFI_ENABLED
                || item.getId() == SettingId.WIFI_CONNECT) && !platform.isDeviceOwner()) {
            return false;
        }
        return developerFeatureToggles.isSettingEnabled(screen, item);
    }

    private SettingsScreenModel withLanguage(SettingsScreenModel base) {
        List<SettingsSection> sections = new ArrayList<>(base.getSections());
        sections.add(languageSection());
        return new SettingsScreenModel(sections);
    }

    private SettingsSection languageSection() {
        AppLanguage current = languageSettings.currentLanguage();
        renderedLanguages = languageSettings.supportedLanguages();
        List<String> labels = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < renderedLanguages.length; i++) {
            labels.add(languageName(renderedLanguages[i]));
            if (renderedLanguages[i] == current) selectedIndex = i;
        }
        return new SettingsSection(context.getString(R.string.language_section_title),
                List.of(SettingItem.choice(SettingId.LANGUAGE,
                        context.getString(R.string.language_section_title),
                        labels, selectedIndex)));
    }

    private void selectSetting(SettingId id, int selectedIndex) {
        SettingItem item = currentSetting(id);
        if (id == SettingId.DEV_PIPELINE_MODE) {
            selectCameraPipelineMode(null, selectedIndex);
            return;
        }
        if (id == SettingId.LANGUAGE) {
            if (selectedIndex >= 0 && selectedIndex < renderedLanguages.length) {
                changeLanguage(renderedLanguages[selectedIndex]);
            }
            return;
        }
        if (id == SettingId.AUDIO_FILE_FORMAT) {
            AudioFileFormat[] formats = AudioFileFormat.values();
            if (selectedIndex < 0 || selectedIndex >= formats.length) return;
            runtime.setAudioFileFormat(formats[selectedIndex]);
            logSelectedSettingChanged(item, id, selectedIndex);
            return;
        }
        if (id == SettingId.AUDIO_SAMPLE_RATE || id == SettingId.AUDIO_BIT_RATE
                || id == SettingId.AUDIO_CHANNEL_COUNT
                || id == SettingId.AUDIO_ALERT_VOLUME) return;
        if (id == SettingId.DEFAULT_STORAGE) {
            List<MediaPartitionLocation> modes = storageSettings.supportedModes();
            if (selectedIndex >= 0 && selectedIndex < modes.size()) {
                MediaPartitionLocation mode = modes.get(selectedIndex);
                if (mode != MediaPartitionLocation.INTERNAL) {
                    notifyListeners(Invalidation.ROWS);
                    platform.showNotice(R.string.external_storage_not_supported);
                    return;
                }
                storageSettings.changeMode(mode);
                settingsUiState.select(id, selectedIndex);
                logSelectedSettingChanged(item, id, selectedIndex);
                markStorageVolumesStale();
                refreshStorageVolumes();
                notifyListeners(Invalidation.STORAGE_USAGE);
            }
            return;
        }
        if (id == SettingId.GPS_POSITIONING_MODE) {
            List<GpsMode> modes = locationSettings.supportedModes();
            if (selectedIndex >= 0 && selectedIndex < modes.size()) {
                GpsMode mode = modes.get(selectedIndex);
                if (!locationControl.isModeAvailable(mode)) return;
                locationSettings.changeMode(mode);
                logSelectedSettingChanged(item, id, selectedIndex);
                if (!platform.hasRequiredLocationPermission()) {
                    platform.requestLocationPermission();
                } else {
                    platform.refreshLocationTracking();
                }
            }
            return;
        }
        settingsUiState.select(id, selectedIndex);
        logSelectedSettingChanged(item, id, selectedIndex);
    }

    private boolean handleLocationSwitchChanged(boolean enabled) {
        if (enabled && !platform.hasRequiredLocationPermission()) {
            platform.requestLocationPermission();
            notifyListeners(Invalidation.ROWS);
            return false;
        }
        boolean changed = locationControl.setEnabledIfPermitted(enabled);
        notifyListeners(Invalidation.ROWS);
        if (!changed) {
            pendingLocationEnabled = enabled;
            platform.openLocationSettings();
            return false;
        }
        platform.refreshLocationTracking();
        return true;
    }

    private void selectCameraPipelineMode(String cameraId, int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= CAMERA_PIPELINE_MODES.size()) return;
        if (cameraId == null && !runtime.canSelectCameraPipelineMode()) {
            notifyListeners(Invalidation.ROWS);
            platform.showNotice(R.string.camera_pipeline_busy);
            return;
        }
        String stableId = cameraId == null
                ? "developer:pipeline-mode"
                : CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
                        .stableId(cameraId);
        SettingItem item = currentSetting(stableId);
        DeveloperSettingsStore.Mode mode = CAMERA_PIPELINE_MODES.get(selectedIndex);
        Consumer<SettingsRuntime.CameraPipelineModeResult> completion = result ->
                platform.runOnUiThread(() -> {
                    if (closed || platform.isFinishingOrDestroyed()) return;
                    notifyListeners(Invalidation.ROWS);
                    if (result == SettingsRuntime.CameraPipelineModeResult.APPLIED) {
                        logSelectedSettingChanged(item, stableId, selectedIndex);
                    }
                    platform.showNotice(cameraPipelineNotice(result));
                });
        if (cameraId == null) runtime.selectCameraPipelineModeForSettings(mode, completion);
        else runtime.selectCameraPipelineModeForSettings(cameraId, mode, completion);
    }

    static int cameraPipelineNotice(SettingsRuntime.CameraPipelineModeResult result) {
        return switch (result) {
            case APPLIED -> R.string.camera_pipeline_applied;
            case REJECTED_BUSY -> R.string.camera_pipeline_busy;
            case REJECTED_UNAVAILABLE -> R.string.camera_pipeline_unknown;
            case FAILED -> R.string.camera_pipeline_failed;
        };
    }

    private void changeLanguage(AppLanguage language) {
        if (language == languageSettings.currentLanguage()) return;
        SettingItem item = currentSetting(SettingId.LANGUAGE);
        languageSettings.changeLanguage(language);
        logSettingChanged(item, SettingId.LANGUAGE, languageName(language));
        platform.showNotice(R.string.language_changed);
        platform.recreateActivity();
    }

    private String languageName(AppLanguage language) {
        return switch (language) {
            case SYSTEM -> context.getString(R.string.language_system);
            case ENGLISH -> context.getString(R.string.language_english);
            case VIETNAMESE -> context.getString(R.string.language_vietnamese);
        };
    }

    private int cameraSettingBlockedNotice() {
        return platform.isCameraRecording()
                ? R.string.recording_camera_setting_change_blocked
                : R.string.camera_capabilities_recheck_busy;
    }

    private SettingId settingId(String stableId) {
        if ("developer:recheck-camera-capabilities".equals(stableId)) {
            return SettingId.RECHECK_CAMERA_CAPABILITIES;
        }
        if (stableId == null || stableId.isBlank()) return null;
        try {
            return SettingId.valueOf(stableId);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private SettingItem currentSetting(SettingId id) { return currentSetting(id, null); }
    private SettingItem currentSetting(String stableId) { return currentSetting(null, stableId); }

    private SettingItem currentSetting(SettingId id, String stableId) {
        if (activeScreen == null || !catalog.isSettingsScreen(activeScreen)
                || activeScreen == MainScreen.DEVELOPER_BUTTON_BINDINGS) return null;
        for (SettingsSection section : settingsModel(activeScreen).getSections()) {
            for (SettingItem item : section.getItems()) {
                if (id != null && item.getId() == id
                        || stableId != null && stableId.equals(item.getStableId())) return item;
            }
        }
        return null;
    }

    private void refreshStorageVolumes() {
        if (closed || !storageVolumesStale
                || !storageVolumesRefreshInFlight.compareAndSet(false, true)) return;
        long expectedGeneration = storageVolumesGeneration;
        runtime.readStorageVolumes(volumes -> {
            storageVolumesRefreshInFlight.set(false);
            if (closed) return;
            if (expectedGeneration != storageVolumesGeneration) {
                refreshStorageVolumes();
                return;
            }
            storageVolumes = volumes;
            storageVolumesStale = false;
            if (activeScreen == MainScreen.STORAGE_SETTINGS
                    && !platform.isFinishingOrDestroyed()) {
                notifyListeners(Invalidation.STORAGE_USAGE);
            }
        });
    }

    private void markStorageVolumesStale() {
        storageVolumesGeneration++;
        storageVolumesStale = true;
    }

    private void logSelectedSettingChanged(
            SettingItem item, SettingId id, int selectedIndex) {
        if (item != null && item.getSelectedIndex() == selectedIndex) return;
        logSettingChanged(item, id, selectedSettingValue(item, selectedIndex));
    }

    private void logSelectedSettingChanged(
            SettingItem item, String stableId, int selectedIndex) {
        if (item != null && item.getSelectedIndex() == selectedIndex) return;
        logSettingChanged(item, stableId, selectedSettingValue(item, selectedIndex));
    }

    private void logNumberSettingChanged(SettingItem item, SettingId id, int value) {
        if (item != null && item.getNumberValue() == value) return;
        logSettingChanged(item, id, numberSettingValue(item, value));
    }

    private void logBooleanSettingChanged(SettingItem item, SettingId id, boolean checked) {
        if (item != null && item.isChecked() == checked) return;
        logSettingChanged(item, id, enabledValue(checked));
    }

    private void logSettingChanged(SettingItem item, SettingId id, String value) {
        logSettingChanged(item, item == null ? null : item.getStableId(), settingName(id), value);
    }

    private void logSettingChanged(SettingItem item, String stableId, String value) {
        logSettingChanged(item, stableId, settingName(stableId), value);
    }

    private void logSettingChanged(
            SettingItem item, String stableId, String fallbackName, String value) {
        String name = item == null ? fallbackName : item.getLabel();
        String previousValue = currentSettingValue(item);
        logger.info(LogCategory.CONFIG, "unspecified", "Changed setting " + name + cameraSettingContext(stableId)
                + (previousValue == null || previousValue.equals(value)
                        ? "" : " from " + previousValue)
                + " to " + value + ".");
    }

    private static String currentSettingValue(SettingItem item) {
        if (item == null) return null;
        return switch (item.getType()) {
            case CHECKBOX -> enabledValue(item.isChecked());
            case CHOICE, RADIO, DESCRIBED_RADIO, STORAGE_RADIO ->
                    selectedSettingValue(item, item.getSelectedIndex());
            case SLIDER -> numberSettingValue(item, item.getNumberValue());
            default -> null;
        };
    }

    private static String selectedSettingValue(SettingItem item, int selectedIndex) {
        if (item == null) return "option " + selectedIndex;
        if (item.getType() == SettingItem.Type.DESCRIBED_RADIO
                && selectedIndex >= 0
                && selectedIndex < item.getDescribedRadioOptions().size()) {
            return item.getDescribedRadioOptions().get(selectedIndex).getLabel();
        }
        if (item.getType() == SettingItem.Type.STORAGE_RADIO
                && selectedIndex >= 0
                && selectedIndex < item.getStorageOptions().size()) {
            return item.getStorageOptions().get(selectedIndex).getLabel();
        }
        if (selectedIndex >= 0 && selectedIndex < item.getOptions().size()) {
            return item.getOptions().get(selectedIndex);
        }
        return "option " + selectedIndex;
    }

    private static String numberSettingValue(SettingItem item, int value) {
        if (item == null || item.getUnit() == null || item.getUnit().isBlank()) {
            return Integer.toString(value);
        }
        return value + " " + item.getUnit();
    }

    private static String enabledValue(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private static String settingName(SettingId id) {
        return settingName(id == null ? null : id.name());
    }

    private static String settingName(String stableId) {
        if (stableId == null || stableId.isBlank()) return UNKNOWN_VALUE;
        return stableId.toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace('-', ' ').replace(':', ' ');
    }

    private static String cameraSettingContext(String stableId) {
        if (stableId == null) return "";
        try {
            return " for camera " + CameraSettingControlId.parse(stableId).cameraId();
        } catch (IllegalArgumentException ignored) {
            // Fall back to the pipeline-selection parser for non-camera setting IDs.
        }
        return CameraDeveloperSettingsPresentation.CameraPipelineSelectionUiState
                .cameraId(stableId)
                .map(cameraId -> " for camera " + cameraId)
                .orElse("");
    }

    private static StorageVolumeStatus storageVolume(
            List<StorageVolumeStatus> volumes, MediaPartitionLocation mode) {
        for (StorageVolumeStatus volume : volumes) {
            if (volume.getMode() == mode) return volume;
        }
        return null;
    }

    private void notifyListeners(Invalidation invalidation) {
        if (closed) return;
        for (Listener listener : List.copyOf(listeners)) listener.onInvalidated(invalidation);
    }

    private void requireSettingsScreen(MainScreen screen) {
        requireOpen();
        if (!catalog.isSettingsScreen(screen)) {
            throw new IllegalArgumentException("Route is not a settings screen: " + screen);
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Settings screen coordinator is closed");
    }

    private record CameraPipelineSelection(String label, int tupleCount) {}
}
