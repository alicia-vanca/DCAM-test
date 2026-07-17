package com.dvid.dcam.app.feature;

import com.dvid.dcam.core.feature.application.usecase.FeatureGateSettingsUseCase;
import com.dvid.dcam.core.feature.domain.FeatureGate;
import com.dvid.dcam.feature.settings.presentation.SettingId;
import com.dvid.dcam.feature.settings.presentation.SettingItem;
import com.dvid.dcam.feature.settings.presentation.SettingsScreenModel;
import com.dvid.dcam.feature.settings.presentation.SettingsSection;
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

    private final FeatureGateSettingsUseCase gates;
    private final List<DeveloperFeatureToggle> toggles;
    private final Map<FeatureGate, DeveloperFeatureToggle> byGate =
            new EnumMap<>(FeatureGate.class);
    private final Map<SettingId, DeveloperFeatureToggle> bySetting =
            new EnumMap<>(SettingId.class);

    private DeveloperFeatureToggles(
            FeatureGateSettingsUseCase gates, List<DeveloperFeatureToggle> toggles) {
        this.gates = Objects.requireNonNull(gates);
        this.toggles = List.copyOf(toggles);
        indexAndValidate();
    }

    public static DeveloperFeatureToggles createDefault(FeatureGateSettingsUseCase gates) {
        List<DeveloperFeatureToggle> toggles = List.of(
                toggle(FeatureGate.IMAGE_CAPTURE, CAPTURE_COMMANDS, "Image capture",
                        SettingId.FEATURE_IMAGE_CAPTURE),
                toggle(FeatureGate.VIDEO_CAPTURE, CAPTURE_COMMANDS, "Video recording",
                        SettingId.FEATURE_VIDEO_CAPTURE),
                child(FeatureGate.VIDEO_MD5, FeatureGate.VIDEO_CAPTURE,
                        CAPTURE_COMMANDS, "MD5 sidecar", SettingId.FEATURE_VIDEO_MD5),
                child(FeatureGate.VIDEO_STREAMING, FeatureGate.VIDEO_CAPTURE,
                        CAPTURE_COMMANDS, "Video streaming", SettingId.FEATURE_VIDEO_STREAMING),
                toggle(FeatureGate.MEDIA_ENCRYPTION, CAPTURE_COMMANDS, "Media encryption",
                        SettingId.FEATURE_MEDIA_ENCRYPTION),
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
        DeveloperFeatureToggle toggle = requireInstalled(gate);
        return isEnabled(gate)
                && (toggle.getParentGate() == null || isEffectivelyEnabled(toggle.getParentGate()));
    }

    /** Runs a feature entry point only while its module is enabled. */
    public boolean runIfEnabled(FeatureGate gate, Runnable action) {
        Objects.requireNonNull(action);
        if (!isEffectivelyEnabled(gate)) return false;
        action.run();
        return true;
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
                    .withEnabled(toggle.getParentGate() == null
                            || isEffectivelyEnabled(toggle.getParentGate()))
                    .withIndentLevel(toggle.getParentGate() == null ? 0 : 1));
            if (toggle.getDeveloperSetting() == parent && child != null) items.add(child);
        }
        List<SettingsSection> models = new ArrayList<>();
        for (Map.Entry<String, List<SettingItem>> section : sections.entrySet()) {
            models.add(new SettingsSection(section.getKey(), section.getValue()));
        }
        return new SettingsScreenModel(models);
    }

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
        return new DeveloperFeatureToggle(gate, section, label, setting, null);
    }

    private static DeveloperFeatureToggle child(
            FeatureGate gate, FeatureGate parent, String section, String label, SettingId setting) {
        return new DeveloperFeatureToggle(gate, section, label, setting, parent);
    }
}
