package com.dvid.dcam.app.ui.settings;

import com.dvid.dcam.app.ui.settings.camera.CameraSettingsPresentationState;
import com.dvid.dcam.feature.capture.domain.AudioFileFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * In-memory settings state used until operational settings persistence is connected.
 * The UI still follows normal get/update/render flow.
 */
public final class SettingsUiState {
    private static final List<String> STORAGE_OPTIONS = List.of("Internal", "External", "Auto");
    private static final List<String> USB_OPTIONS = List.of("Off", "Password", "Admin only");
    private static final List<String> AUDIO_FILE_FORMAT_OPTIONS = List.of("AAC", "M4A");
    private int segmentLengthMinutes = 5;
    private boolean videoEncryptionEnabled;
    private int defaultStorageIndex;
    private int lowStorageWarningGb = 2;
    private boolean protectSettingsMenu = true;
    private int usbAccessIndex;
    private boolean fullScreenDisplay;
    private boolean autoRotateEnabled;
    private boolean wifiEnabled;

    public SettingsUiState(boolean videoEncryptionEnabled, int defaultStorageIndex,
            boolean autoRotateEnabled, boolean wifiEnabled) {
        this.videoEncryptionEnabled = videoEncryptionEnabled;
        this.defaultStorageIndex = clamp(defaultStorageIndex, STORAGE_OPTIONS.size());
        this.autoRotateEnabled = autoRotateEnabled;
        this.wifiEnabled = wifiEnabled;
    }

    public SettingsScreenModel recording(CameraSettingsPresentationState presentation,
            String videoResolutionLabel, String frameRateLabel) {
        return Objects.requireNonNull(presentation, "presentation").recordingScreen(
                videoResolutionLabel, frameRateLabel);
    }

    public SettingsScreenModel camera(CameraSettingsPresentationState presentation) {
        return Objects.requireNonNull(presentation, "presentation").cameraScreen(
                "Image resolution", "Photo quality may vary while recording.", "Status");
    }

    public SettingsScreenModel camera(CameraSettingsPresentationState presentation,
            String imageResolutionLabel, String photoQualityNotice, String statusLabel) {
        return Objects.requireNonNull(presentation, "presentation").cameraScreen(
                imageResolutionLabel, photoQualityNotice, statusLabel);
    }

    public void select(SettingId id, int selectedIndex) {
        switch (id) {
            case DEFAULT_STORAGE:
                defaultStorageIndex = clamp(selectedIndex, STORAGE_OPTIONS.size());
                break;
            case USB_ACCESS_PROTECTION:
                usbAccessIndex = clamp(selectedIndex, USB_OPTIONS.size());
                break;
            default:
                throw new IllegalArgumentException("Setting " + id + " is not a choice");
        }
    }

    public void updateNumber(SettingId id, int value) {
        switch (id) {
            case VIDEO_SEGMENT_LENGTH_MINUTES:
                segmentLengthMinutes = clamp(value, 1, 30);
                break;
            case LOW_STORAGE_WARNING_GB:
                lowStorageWarningGb = clamp(value, 1, 20);
                break;
            default:
                throw new IllegalArgumentException("Setting " + id + " is not numeric");
        }
    }

    public void updateBoolean(SettingId id, boolean checked) {
        switch (id) {
            case ENCRYPT_VIDEO_FILES:
                videoEncryptionEnabled = checked;
                break;
            case PROTECT_SETTINGS_MENU:
                protectSettingsMenu = checked;
                break;
            case FULL_SCREEN_DISPLAY:
                fullScreenDisplay = checked;
                break;
            case AUTO_ROTATE:
                autoRotateEnabled = checked;
                break;
            case WIFI_ENABLED:
                wifiEnabled = checked;
                break;
            default:
                throw new IllegalArgumentException("Setting " + id + " is not boolean");
        }
    }

    public boolean isFullScreenDisplayEnabled() { return fullScreenDisplay; }

    public void setVideoEncryptionEnabled(boolean enabled) {
        videoEncryptionEnabled = enabled;
    }

    public void setLowStorageWarningGb(int value) {
        lowStorageWarningGb = clamp(value, 1, 20);
    }

    public SettingsScreenModel storage() {
        return storage(STORAGE_OPTIONS, "Storage", "Default storage", "Low-storage warning");
    }

    public SettingsScreenModel storage(List<String> storageOptions) {
        return storage(storageOptions, "Storage", "Default storage", "Low-storage warning");
    }

    public SettingsScreenModel storageWithVolumes(List<StorageOptionUiState> storageOptions, String sectionTitle,
            String defaultStorageLabel, String lowStorageWarningLabel) {
        return new SettingsScreenModel(List.of(new SettingsSection(sectionTitle, List.of(
                SettingItem.storageRadio(SettingId.DEFAULT_STORAGE, defaultStorageLabel,
                        storageOptions, defaultStorageIndex),
                SettingItem.slider(SettingId.LOW_STORAGE_WARNING_GB, lowStorageWarningLabel,
                        1, 20, lowStorageWarningGb, "GB")))));
    }

    public SettingsScreenModel storage(List<String> storageOptions, String sectionTitle,
            String defaultStorageLabel, String lowStorageWarningLabel) {
        return new SettingsScreenModel(List.of(new SettingsSection(sectionTitle, List.of(
                SettingItem.radio(SettingId.DEFAULT_STORAGE, defaultStorageLabel,
                        storageOptions, defaultStorageIndex),
                SettingItem.slider(SettingId.LOW_STORAGE_WARNING_GB, lowStorageWarningLabel,
                        1, 20, lowStorageWarningGb, "GB")))));
    }

    public SettingsScreenModel security(String operatorAccountSectionTitle,
            String securitySectionTitle) {
        return new SettingsScreenModel(List.of(
                new SettingsSection(operatorAccountSectionTitle, List.of(
                        SettingItem.action(SettingId.CHANGE_OPERATOR_ID, "Change ID"),
                        SettingItem.action(SettingId.CHANGE_OPERATOR_PASSWORD, "Change password"),
                        SettingItem.action(SettingId.LOGOUT, "Log out"))),
                new SettingsSection(securitySectionTitle, List.of(
                        SettingItem.checkbox(SettingId.ENCRYPT_VIDEO_FILES,
                                "Encrypt media files", videoEncryptionEnabled),
                        SettingItem.checkbox(SettingId.PROTECT_SETTINGS_MENU,
                                "Protect settings menu", protectSettingsMenu),
                        SettingItem.choice(SettingId.USB_ACCESS_PROTECTION,
                                "USB access protection", USB_OPTIONS, usbAccessIndex)))));
    }

    public SettingsScreenModel device(String sectionTitle, String autoRotateLabel,
            String wifiLabel, String wifiConnectLabel) {
        return new SettingsScreenModel(List.of(new SettingsSection(sectionTitle, List.of(
                SettingItem.checkbox(SettingId.FULL_SCREEN_DISPLAY,
                        "Full-screen display", fullScreenDisplay),
                SettingItem.checkbox(SettingId.AUTO_ROTATE,
                        autoRotateLabel, autoRotateEnabled),
                SettingItem.checkbox(SettingId.WIFI_ENABLED,
                        wifiLabel, wifiEnabled),
                SettingItem.action(SettingId.WIFI_CONNECT, wifiConnectLabel)))));
    }

    public SettingsScreenModel audio(String sectionTitle, String[] labels, AudioFileFormat format,
            String sampleRate, String bitRate, String channelCount, String alertVolume) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(sampleRate, "sampleRate");
        Objects.requireNonNull(bitRate, "bitRate");
        Objects.requireNonNull(channelCount, "channelCount");
        Objects.requireNonNull(alertVolume, "alertVolume");
        List<SettingItem> items = new ArrayList<>(5);
        if (labels.length > 0) {
            items.add(SettingItem.choice(SettingId.AUDIO_FILE_FORMAT, labels[0],
                    AUDIO_FILE_FORMAT_OPTIONS, format.ordinal()));
        }
        if (labels.length > 1) {
            items.add(SettingItem.choice(SettingId.AUDIO_SAMPLE_RATE, labels[1],
                    List.of(sampleRate), 0));
        }
        if (labels.length > 2) {
            items.add(SettingItem.choice(SettingId.AUDIO_BIT_RATE, labels[2],
                    List.of(bitRate), 0));
        }
        if (labels.length > 3) {
            items.add(SettingItem.choice(SettingId.AUDIO_CHANNEL_COUNT, labels[3],
                    List.of(channelCount), 0));
        }
        if (labels.length > 4) {
            items.add(SettingItem.choice(SettingId.AUDIO_ALERT_VOLUME, labels[4],
                    List.of(alertVolume), 0));
        }
        return new SettingsScreenModel(List.of(new SettingsSection(sectionTitle, items)));
    }
    public SettingsScreenModel readOnly(String sectionTitle, String unavailableValue,
            String[] fallbackLabels) {
        return readOnly(sectionTitle, unavailableValue, fallbackLabels, null);
    }

    public SettingsScreenModel readOnly(String sectionTitle, String unavailableValue,
            String[] fallbackLabels, String[] fallbackValues) {
        SettingItem[] items = new SettingItem[fallbackLabels.length];
        for (int i = 0; i < fallbackLabels.length; i++) {
            String value = fallbackValues != null && i < fallbackValues.length
                    ? fallbackValues[i] : unavailableValue;
            items[i] = SettingItem.text(fallbackLabels[i], value);
        }
        return new SettingsScreenModel(List.of(new SettingsSection(sectionTitle, List.of(items))));
    }

    private static int clamp(int selectedIndex, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(size - 1, selectedIndex));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}