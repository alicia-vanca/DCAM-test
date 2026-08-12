package com.dvid.dcam.feature.input.application.usecase;

import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.feature.input.application.port.HardwareButtonSettings;
import java.util.List;

public final class ConfigureHardwareButtonsUseCase {
    private final HardwareButtonSettings settings;
    private final HardwareButtonLayout defaults;

    public ConfigureHardwareButtonsUseCase(
            HardwareButtonSettings settings, HardwareButtonLayout defaults) {
        if (settings == null) throw new IllegalArgumentException("settings is required");
        this.settings = settings;
        this.defaults = defaults == null ? HardwareButtonLayout.empty() : defaults;
    }

    public HardwareButtonLayout initialize() {
        settings.initialize(defaults);
        return settings.loadLayout();
    }

    public List<String> keyCodeLabels() { return settings.keyCodeLabels(); }
    public List<String> typeLabels() { return settings.typeLabels(); }
    public List<String> sourceLabels() { return settings.sourceLabels(); }
    public List<String> actionFamilyLabels() { return settings.actionFamilyLabels(); }
    public int selectedKeyCodeIndex(ButtonRole role) {
        return settings.selectedKeyCodeIndex(role);
    }
    public int selectedTypeIndex(ButtonRole role) { return settings.selectedTypeIndex(role); }
    public int selectedSourceIndex(ButtonRole role) {
        return settings.selectedSourceIndex(role);
    }
    public int selectedActionFamilyIndex(ButtonRole role) {
        return settings.selectedActionFamilyIndex(role);
    }
    public boolean hasDefaults() { return !defaults.isEmpty(); }

    public HardwareButtonLayout selectKeyCode(ButtonRole role, int selectedIndex) {
        settings.selectKeyCode(role, selectedIndex);
        return settings.loadLayout();
    }

    public HardwareButtonLayout selectType(ButtonRole role, int selectedIndex) {
        settings.selectType(role, selectedIndex);
        return settings.loadLayout();
    }

    public HardwareButtonLayout selectSource(ButtonRole role, int selectedIndex) {
        settings.selectSource(role, selectedIndex);
        return settings.loadLayout();
    }

    public HardwareButtonLayout selectActionFamily(ButtonRole role, int selectedIndex) {
        settings.selectActionFamily(role, selectedIndex);
        return settings.loadLayout();
    }

    public HardwareButtonLayout resetDefaults() {
        if (!settings.resetToDefaults(defaults)) return HardwareButtonLayout.empty();
        return settings.loadLayout();
    }
}
