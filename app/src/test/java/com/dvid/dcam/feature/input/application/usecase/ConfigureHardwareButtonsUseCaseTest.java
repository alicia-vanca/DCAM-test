package com.dvid.dcam.feature.input.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.HardwareButtonBinding;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.core.input.domain.PhysicalButtonType;
import com.dvid.dcam.feature.input.application.port.HardwareButtonSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ConfigureHardwareButtonsUseCaseTest {
    @Test void initializesAndForwardsAllBindingSelections() {
        HardwareButtonLayout defaults = new HardwareButtonLayout(
                new HardwareButtonBinding(
                        ButtonRole.RECORD, 11, PhysicalButtonType.BUTTON));
        FakeSettings settings = new FakeSettings();
        ConfigureHardwareButtonsUseCase useCase =
                new ConfigureHardwareButtonsUseCase(settings, defaults);

        assertEquals(defaults, useCase.initialize());
        assertTrue(useCase.hasDefaults());
        assertEquals(defaults, useCase.selectType(ButtonRole.RECORD, 1));
        assertEquals(defaults, useCase.selectSource(ButtonRole.RECORD, 0));
        assertEquals(defaults, useCase.selectActionFamily(ButtonRole.RECORD, 2));
        assertEquals(defaults, useCase.selectKeyCode(ButtonRole.RECORD, 3));
        assertEquals(1, settings.typeSelection);
        assertEquals(0, settings.sourceSelection);
        assertEquals(2, settings.actionFamilySelection);
        assertEquals(3, settings.keyCodeSelection);
        assertEquals(defaults, useCase.resetDefaults());
    }

    @Test void hasNoDefaultsWhenProfileIsEmpty() {
        ConfigureHardwareButtonsUseCase useCase = new ConfigureHardwareButtonsUseCase(
                new FakeSettings(), HardwareButtonLayout.empty());
        assertFalse(useCase.hasDefaults());
    }

    private static final class FakeSettings implements HardwareButtonSettings {
        private HardwareButtonLayout layout = HardwareButtonLayout.empty();
        private int keyCodeSelection = -1;
        private int typeSelection = -1;
        private int sourceSelection = -1;
        private int actionFamilySelection = -1;

        @Override public List<String> keyCodeLabels() {
            return List.of("Unassigned", "KEYCODE_F1");
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
        @Override public void initialize(HardwareButtonLayout defaults) {
            layout = defaults;
        }
        @Override public boolean resetToDefaults(HardwareButtonLayout defaults) {
            if (defaults.isEmpty()) return false;
            layout = defaults;
            return true;
        }
        @Override public void selectKeyCode(ButtonRole role, int selectedIndex) {
            keyCodeSelection = selectedIndex;
        }
        @Override public void selectType(ButtonRole role, int selectedIndex) {
            typeSelection = selectedIndex;
        }
        @Override public void selectSource(ButtonRole role, int selectedIndex) {
            sourceSelection = selectedIndex;
        }
        @Override public void selectActionFamily(ButtonRole role, int selectedIndex) {
            actionFamilySelection = selectedIndex;
        }
        @Override public HardwareButtonLayout loadLayout() { return layout; }
    }
}
