package com.dvid.dcam.platform.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.view.KeyEvent;
import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.FirmwareButtonActionFamily;
import com.dvid.dcam.core.input.domain.HardwareButtonBinding;
import com.dvid.dcam.core.input.domain.HardwareButtonInputSource;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.core.input.domain.PhysicalButtonType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SharedPreferencesHardwareButtonSettingsTest {
    @Test void freshAndResetDeviceDefaultsUseFirmwareBroadcastAndProfileType() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedPreferencesHardwareButtonSettings settings =
                new SharedPreferencesHardwareButtonSettings(preferences);
        HardwareButtonLayout defaults = profile("BWC");

        settings.initialize(defaults);

        assertEquals(List.of(
                "Unassigned",
                "ACTION_CAMERA",
                "ACTION_LASER",
                "ACTION_PTTKEY",
                "ACTION_RECORD",
                "ACTION_SOS",
                "ACTION_VIDEO"), settings.actionFamilyLabels());
        assertEquals(0, settings.selectedSourceIndex(ButtonRole.RECORD));
        assertEquals(1, settings.selectedTypeIndex(ButtonRole.RECORD));
        assertEquals("ACTION_VIDEO", selectedFamilyLabel(settings, ButtonRole.RECORD));
        HardwareButtonLayout active = settings.loadLayout();
        HardwareButtonBinding record = active.findByFirmwareBroadcastDownAction(
                FirmwareButtonActionFamily.ACTION_VIDEO.downAction());
        assertNotNull(record);
        assertEquals(ButtonRole.RECORD, record.role());
        assertEquals(PhysicalButtonType.SWITCH, record.type());
        assertEquals(HardwareButtonInputSource.FIRMWARE_BROADCAST, record.inputSource());
        assertNull(active.findByButtonKeyCode(KeyEvent.KEYCODE_F10));

        settings.selectSource(ButtonRole.RECORD, 1);
        assertNotNull(settings.loadLayout().findByButtonKeyCode(KeyEvent.KEYCODE_F10));
        assertTrue(settings.resetToDefaults(defaults));
        assertEquals(0, settings.selectedSourceIndex(ButtonRole.RECORD));
        assertEquals(1, settings.selectedTypeIndex(ButtonRole.RECORD));
    }

    @Test void legacyBindingWithoutMatchedProfileRemainsAKeyEventBinding() {
        MemoryPreferences preferences = new MemoryPreferences();
        preferences.values.put(
                ButtonRole.RECORD.name(),
                KeyEvent.KEYCODE_F5 + ":" + PhysicalButtonType.BUTTON.name());
        SharedPreferencesHardwareButtonSettings settings =
                new SharedPreferencesHardwareButtonSettings(preferences);

        settings.initialize(HardwareButtonLayout.empty());
        HardwareButtonLayout active = settings.loadLayout();

        assertEquals(1, settings.selectedSourceIndex(ButtonRole.RECORD));
        assertEquals(ButtonRole.RECORD,
                active.findByButtonKeyCode(KeyEvent.KEYCODE_F5).role());
        assertNull(active.findByFirmwareBroadcastDownAction(
                FirmwareButtonActionFamily.ACTION_VIDEO.downAction()));
    }

    @Test void legacyBindingWithMatchedProfileMigratesToFirmwareBroadcast() {
        MemoryPreferences preferences = new MemoryPreferences();
        preferences.values.put(
                ButtonRole.RECORD.name(),
                KeyEvent.KEYCODE_F12 + ":" + PhysicalButtonType.SWITCH.name());
        SharedPreferencesHardwareButtonSettings settings =
                new SharedPreferencesHardwareButtonSettings(preferences);

        settings.initialize(profile("BodyCamera"));
        HardwareButtonLayout active = settings.loadLayout();

        assertEquals(0, settings.selectedSourceIndex(ButtonRole.RECORD));
        assertEquals(1, settings.selectedTypeIndex(ButtonRole.RECORD));
        assertEquals(settings.keyCodeLabels().indexOf("KEYCODE_F12"),
                settings.selectedKeyCodeIndex(ButtonRole.RECORD));
        assertEquals("ACTION_VIDEO", selectedFamilyLabel(settings, ButtonRole.RECORD));
        HardwareButtonBinding record = active.findByFirmwareBroadcastDownAction(
                FirmwareButtonActionFamily.ACTION_VIDEO.downAction());
        assertNotNull(record);
        assertEquals(PhysicalButtonType.SWITCH, record.type());
        assertEquals(KeyEvent.KEYCODE_F12, record.buttonKeyCode());
        assertNull(active.findByButtonKeyCode(KeyEvent.KEYCODE_F12));
    }

    @Test void keycodeAndFirmwareFamilyPersistAcrossSourceChanges() {
        MemoryPreferences preferences = new MemoryPreferences();
        SharedPreferencesHardwareButtonSettings settings =
                new SharedPreferencesHardwareButtonSettings(preferences);
        settings.initialize(profile("BodyCamera"));
        int f12 = settings.keyCodeLabels().indexOf("KEYCODE_F12");
        int camera = settings.actionFamilyLabels().indexOf("ACTION_CAMERA");

        settings.selectKeyCode(ButtonRole.RECORD, f12);
        assertEquals("ACTION_VIDEO", selectedFamilyLabel(settings, ButtonRole.RECORD));
        settings.selectSource(ButtonRole.RECORD, 1);
        assertEquals(ButtonRole.RECORD,
                settings.loadLayout().findByButtonKeyCode(KeyEvent.KEYCODE_F12).role());

        settings.selectSource(ButtonRole.RECORD, 0);
        assertEquals(ButtonRole.RECORD, settings.loadLayout()
                .findByFirmwareBroadcastDownAction(
                        FirmwareButtonActionFamily.ACTION_VIDEO.downAction())
                .role());
        assertEquals(f12, settings.selectedKeyCodeIndex(ButtonRole.RECORD));

        settings.selectActionFamily(ButtonRole.RECORD, camera);
        assertEquals(0, settings.selectedActionFamilyIndex(ButtonRole.PHOTO_CAPTURE));
        assertEquals(ButtonRole.RECORD, settings.loadLayout()
                .findByFirmwareBroadcastDownAction(
                        FirmwareButtonActionFamily.ACTION_CAMERA.downAction())
                .role());
    }

    private static String selectedFamilyLabel(
            SharedPreferencesHardwareButtonSettings settings, ButtonRole role) {
        return settings.actionFamilyLabels().get(settings.selectedActionFamilyIndex(role));
    }

    private static HardwareButtonLayout profile(String model) {
        return HardwareButtonProfiles.resolve(
                new HardwareDeviceIdentity(model, "k69v1_64_k419", "mt6768"));
    }

    private static final class MemoryPreferences
            implements SharedPreferencesHardwareButtonSettings.PreferenceAccess {
        private final Map<String, String> values = new LinkedHashMap<>();

        @Override public String get(String key) { return values.get(key); }

        @Override public Map<String, String> all() {
            return new LinkedHashMap<>(values);
        }

        @Override public void replace(Map<String, String> replacements) {
            values.clear();
            values.putAll(replacements);
        }
    }
}
