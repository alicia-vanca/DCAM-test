package com.dvid.dcam.feature.settings.presentation;
import java.util.List;

/**
 * In-memory settings state used until operational settings persistence is connected.
 * The UI still follows normal get/update/render flow.
 */
public final class SettingsUiState {
    private List<String> recordResolutions = List.of("SD", "HD", "FHD");
    private static final List<String> STORAGE_OPTIONS = List.of("Internal", "External", "Auto");
    private static final List<String> USB_OPTIONS = List.of("Off", "Password", "Admin only");

    private String recordResolution = "FHD";
    private int segmentLengthMinutes = 5;
    private boolean videoEncryptionEnabled;
    private int defaultStorageIndex;
    private int lowStorageWarningGb = 2;
    private boolean protectSettingsMenu = true;
    private int usbAccessIndex;
    private boolean fullScreenDisplay;
    private boolean statusLightsEnabled = true;
    private boolean autoRotateEnabled;
    private boolean wifiEnabled;

    public SettingsUiState(boolean videoEncryptionEnabled, int defaultStorageIndex,
            boolean autoRotateEnabled, boolean wifiEnabled) {
        this.videoEncryptionEnabled = videoEncryptionEnabled;
        this.defaultStorageIndex = clamp(defaultStorageIndex, STORAGE_OPTIONS.size());
        this.autoRotateEnabled = autoRotateEnabled;
        this.wifiEnabled = wifiEnabled;
    }

    public void select(SettingId id, int selectedIndex) {
        switch (id) {
            case RECORD_RESOLUTION:
                recordResolution = recordResolutions.get(clamp(selectedIndex, recordResolutions.size()));
                break;
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
            case STATUS_LIGHTS:
                statusLightsEnabled = checked;
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

    public void setVideoEncryptionEnabled(boolean enabled) {
        videoEncryptionEnabled = enabled;
    }

    public void setSupportedRecordResolutions(List<String> supported) {
        if (supported == null || supported.isEmpty()) return;
        recordResolutions = List.copyOf(supported);
        if (!recordResolutions.contains(recordResolution)) recordResolution = recordResolutions.get(recordResolutions.size() - 1);
    }

    public void setRecordResolution(String resolution) {
        if (recordResolutions.contains(resolution)) recordResolution = resolution;
    }

    public String recordResolution() { return recordResolution; }

    public void setLowStorageWarningGb(int value) {
        lowStorageWarningGb = clamp(value, 1, 20);
    }

    public SettingsScreenModel recording(String sectionTitle, String resolutionLabel, String segmentLengthLabel, String minuteUnit) {
        return new SettingsScreenModel(List.of(new SettingsSection(sectionTitle, List.of(
                SettingItem.choice(SettingId.RECORD_RESOLUTION, resolutionLabel,
                        recordResolutions, recordResolutions.indexOf(recordResolution)),
                SettingItem.slider(SettingId.VIDEO_SEGMENT_LENGTH_MINUTES, segmentLengthLabel,
                        1, 30, segmentLengthMinutes, minuteUnit)))));
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

    public SettingsScreenModel security() {
        return new SettingsScreenModel(List.of(
                new SettingsSection("Operator account", List.of(
                        SettingItem.action(SettingId.CHANGE_OPERATOR_ID, "Change ID"),
                        SettingItem.action(SettingId.CHANGE_OPERATOR_PASSWORD, "Change password"),
                        SettingItem.action(SettingId.LOGOUT, "Log out"))),
                new SettingsSection("Security", List.of(
                        SettingItem.checkbox(SettingId.ENCRYPT_VIDEO_FILES,
                                "Encrypt media files", videoEncryptionEnabled),
                        SettingItem.checkbox(SettingId.PROTECT_SETTINGS_MENU,
                                "Protect settings menu", protectSettingsMenu),
                        SettingItem.choice(SettingId.USB_ACCESS_PROTECTION,
                                "USB access protection", USB_OPTIONS, usbAccessIndex)))));
    }

    public SettingsScreenModel device(String autoRotateLabel, String wifiLabel, String wifiConnectLabel) {
        return new SettingsScreenModel(List.of(new SettingsSection("Device", List.of(
                SettingItem.checkbox(SettingId.FULL_SCREEN_DISPLAY,
                        "Full-screen display", fullScreenDisplay),
                SettingItem.checkbox(SettingId.STATUS_LIGHTS,
                        "Status lights", statusLightsEnabled),
                SettingItem.checkbox(SettingId.AUTO_ROTATE,
                        autoRotateLabel, autoRotateEnabled),
                SettingItem.checkbox(SettingId.WIFI_ENABLED,
                        wifiLabel, wifiEnabled),
                SettingItem.action(SettingId.WIFI_CONNECT, wifiConnectLabel)))));
    }

    public SettingsScreenModel readOnly(String[] fallbackLabels) {
        SettingItem[] items = new SettingItem[fallbackLabels.length];
        for (int i = 0; i < fallbackLabels.length; i++) {
            items[i] = SettingItem.text(fallbackLabels[i], "Pending");
        }
        return new SettingsScreenModel(List.of(new SettingsSection("Available settings", List.of(items))));
    }

    private static int clamp(int selectedIndex, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(size - 1, selectedIndex));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
