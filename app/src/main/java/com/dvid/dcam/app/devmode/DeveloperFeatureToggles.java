package com.dvid.dcam.app.devmode;

import com.dvid.dcam.core.featuregate.application.FeatureGates;
import com.dvid.dcam.core.featuregate.domain.FeatureGate;
import com.dvid.dcam.app.ui.MainScreen;
import com.dvid.dcam.app.ui.settings.SettingId;
import com.dvid.dcam.app.ui.settings.SettingItem;
import com.dvid.dcam.app.ui.settings.SettingsScreenModel;
import com.dvid.dcam.app.ui.settings.SettingsSection;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Developer-mode controls for features that explicitly opt in to runtime disablement.
 *
 * <p>Ordinary features do not register here and remain enabled normally. A declaration is added
 * only after the product decides that a feature may be disabled in developer mode.
 */
public final class DeveloperFeatureToggles {
    private static final String CAPTURE_COMMANDS = "Capture commands";
    private static final String LOCAL_SURFACES = "Local app surfaces";
    private static final String LATER_PHASE = "Later-phase features";

    private final FeatureGates gates;
    private final List<DeveloperFeatureToggle> toggles;
    private final Map<FeatureGate, DeveloperFeatureToggle> byGate =
            new EnumMap<>(FeatureGate.class);
    private final Map<SettingId, DeveloperFeatureToggle> bySetting =
            new EnumMap<>(SettingId.class);

    private DeveloperFeatureToggles(
            FeatureGates gates, List<DeveloperFeatureToggle> toggles) {
        this.gates = Objects.requireNonNull(gates);
        this.toggles = List.copyOf(toggles);
        indexAndValidate();
    }

    public static DeveloperFeatureToggles createDefault(FeatureGates gates) {
        List<DeveloperFeatureToggle> toggles = List.of(
                toggle(FeatureGate.IMAGE_CAPTURE, CAPTURE_COMMANDS, "Image capture",
                        SettingId.FEATURE_IMAGE_CAPTURE),
                toggle(FeatureGate.VIDEO_CAPTURE, CAPTURE_COMMANDS, "Video recording",
                        SettingId.FEATURE_VIDEO_CAPTURE),
                toggle(FeatureGate.VIDEO_MD5,
                        CAPTURE_COMMANDS, "MD5 sidecar", SettingId.FEATURE_VIDEO_MD5),
                toggle(FeatureGate.VIDEO_STREAMING,
                        CAPTURE_COMMANDS, "Video streaming", SettingId.FEATURE_VIDEO_STREAMING),
                toggle(FeatureGate.AUDIO_CAPTURE, CAPTURE_COMMANDS, "Audio recording",
                        SettingId.FEATURE_AUDIO_CAPTURE),
                toggle(FeatureGate.MEDIA_BROWSER, LOCAL_SURFACES, "Files browser",
                        SettingId.FEATURE_MEDIA_BROWSER),
                toggle(FeatureGate.AUTHENTICATION, LOCAL_SURFACES, "Login authentication",
                        SettingId.FEATURE_AUTHENTICATION),
                toggle(FeatureGate.STORAGE_SETTINGS, LOCAL_SURFACES, "Storage settings",
                        SettingId.FEATURE_STORAGE_SETTINGS),
                toggle(FeatureGate.DEVICE_SETTINGS, LOCAL_SURFACES, "Device settings",
                        SettingId.FEATURE_DEVICE_SETTINGS),
                toggle(FeatureGate.GPS, LATER_PHASE, "Location", SettingId.FEATURE_GPS),
                toggle(FeatureGate.SECURITY_SETTINGS, LATER_PHASE, "Security settings screen",
                        SettingId.FEATURE_SECURITY_SETTINGS),
                toggle(FeatureGate.MEDIA_ENCRYPTION, LATER_PHASE, "Media encryption",
                        SettingId.FEATURE_MEDIA_ENCRYPTION),
                toggle(FeatureGate.CLOUD_SETTINGS, LATER_PHASE, "Cloud / network screen",
                        SettingId.FEATURE_CLOUD_SETTINGS),
                toggle(FeatureGate.TRANSFER, LATER_PHASE, "Transfer",
                        SettingId.FEATURE_TRANSFER));
        return new DeveloperFeatureToggles(gates, toggles);
    }

    public boolean isEnabled(FeatureGate gate) {
        requireInstalled(gate);
        return gates.isEnabled(gate);
    }

    public boolean isEffectivelyEnabled(FeatureGate gate) {
        requireInstalled(gate);
        return gates.isEffectivelyEnabled(gate);
    }

    /** Runs a feature entry point only while its module is enabled. */
    public boolean runIfEnabled(FeatureGate gate, Runnable action) {
        Objects.requireNonNull(action);
        return gates.runIfEnabled(gate, action);
    }

    /**
     * Handles a developer toggle when the setting belongs to a feature module.
     *
     * @return true when the setting was a module toggle, false for ordinary app settings.
     */
    public boolean setEnabled(SettingId setting, boolean enabled) {
        DeveloperFeatureToggle toggle = bySetting.get(setting);
        if (toggle == null) return false;
        gates.setEnabled(toggle.getGate(), enabled);
        return true;
    }

    public boolean isSettingEnabled(MainScreen screen, SettingItem item) {
        for (FeatureGate gate : requiredGatesForSetting(screen, item)) {
            if (!isEffectivelyEnabled(gate)) return false;
        }
        return true;
    }

    public boolean isReadOnlySettingEnabled(MainScreen screen, int index) {
        for (FeatureGate gate : requiredGatesForReadOnlySetting(screen, index)) {
            if (!isEffectivelyEnabled(gate)) return false;
        }
        return true;
    }

    public SettingsScreenModel developerSettings() {
        return developerSettings(null, null);
    }

    public SettingsScreenModel developerSettings(SettingId parent, SettingItem child) {
        Map<String, List<SettingItem>> sections = new LinkedHashMap<>();
        for (DeveloperFeatureToggle toggle : toggles) {
            List<SettingItem> items = sections.computeIfAbsent(
                    toggle.getDeveloperSection(), ignored -> new ArrayList<>());
            items.add(SettingItem.checkbox(
                    toggle.getDeveloperSetting(),
                    toggle.getDeveloperLabel(),
                    isEnabled(toggle.getGate()))
                    .withEnabled(toggle.getGate().parent() == null
                            || gates.isEffectivelyEnabled(toggle.getGate().parent()))
                    .withIndentLevel(toggle.getGate().parent() == null ? 0 : 1));
            if (toggle.getDeveloperSetting() == parent && child != null) items.add(child);
        }
        List<SettingsSection> models = new ArrayList<>();
        for (Map.Entry<String, List<SettingItem>> section : sections.entrySet()) {
            models.add(new SettingsSection(section.getKey(), section.getValue()));
        }
        return new SettingsScreenModel(models);
    }

    private static FeatureGate[] requiredGatesForSetting(MainScreen screen, SettingItem item) {
        if (screen == MainScreen.DEVELOPER_SETTINGS
                && item.getId() == SettingId.GPS_POSITIONING_MODE) return noGates();
        if (item.getId() == SettingId.GPS_LOCATION_ENABLED
                || item.getId() == SettingId.GPS_POSITIONING_MODE
                || item.getId() == SettingId.GPS_UPDATE_DISTANCE_METERS
                || item.getId() == SettingId.GPS_REPORT_INTERVAL_SECONDS) {
            return gates(FeatureGate.GPS);
        }
        if (item.getId() == SettingId.LANGUAGE
                || item.getId() == SettingId.AUTO_ROTATE
                || item.getId() == SettingId.WIFI_ENABLED
                || item.getId() == SettingId.WIFI_CONNECT) return noGates();
        if (item.getId() == SettingId.ENCRYPT_VIDEO_FILES) {
            return gates(FeatureGate.MEDIA_ENCRYPTION);
        }
        switch (screen) {
            case RECORD_SETTINGS: return gates(FeatureGate.VIDEO_CAPTURE);
            case CAMERA_SETTINGS: return gates(FeatureGate.IMAGE_CAPTURE);
            case STORAGE_SETTINGS: return gates(FeatureGate.STORAGE_SETTINGS);
            case USER_SETTINGS: return gates(FeatureGate.SECURITY_SETTINGS);
            case DEVICE_SETTINGS: return gates(FeatureGate.DEVICE_SETTINGS);
            default: return noGates();
        }
    }

    private static FeatureGate[] requiredGatesForReadOnlySetting(MainScreen screen, int index) {
        switch (screen) {
            case CAMERA_SETTINGS: return gates(FeatureGate.IMAGE_CAPTURE);
            case VIDEO_STREAM_SETTINGS: return gates(FeatureGate.VIDEO_STREAMING);
            case AUDIO_SETTINGS: return gates(FeatureGate.AUDIO_CAPTURE);
            case GPS_SETTINGS: return gates(FeatureGate.GPS);
            case SERVER_SETTINGS: return gates(FeatureGate.CLOUD_SETTINGS);
            case TRANSFER_SETTINGS:
                return index < 4
                        ? gates(FeatureGate.VIDEO_STREAMING)
                        : gates(FeatureGate.TRANSFER);
            default: return noGates();
        }
    }

    private static FeatureGate[] gates(FeatureGate... gates) { return gates; }

    private static FeatureGate[] noGates() { return new FeatureGate[0]; }

    private void indexAndValidate() {
        for (DeveloperFeatureToggle toggle : toggles) {
            if (byGate.put(toggle.getGate(), toggle) != null) {
                throw new IllegalArgumentException("Duplicate feature gate " + toggle.getGate());
            }
            if (bySetting.put(toggle.getDeveloperSetting(), toggle) != null) {
                throw new IllegalArgumentException(
                        "Duplicate feature setting " + toggle.getDeveloperSetting());
            }
        }
        for (FeatureGate gate : FeatureGate.values()) requireInstalled(gate);
    }

    private DeveloperFeatureToggle requireInstalled(FeatureGate gate) {
        if (!byGate.containsKey(gate)) {
            throw new IllegalArgumentException("Developer feature toggle is not declared: " + gate);
        }
        return byGate.get(gate);
    }

    private static DeveloperFeatureToggle toggle(
            FeatureGate gate, String section, String label, SettingId setting) {
        return new DeveloperFeatureToggle(gate, section, label, setting);
    }

    }
