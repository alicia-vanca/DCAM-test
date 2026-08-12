package com.dvid.dcam.platform.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import com.dvid.dcam.core.input.domain.ButtonRole;
import com.dvid.dcam.core.input.domain.FirmwareButtonActionFamily;
import com.dvid.dcam.core.input.domain.HardwareButtonBinding;
import com.dvid.dcam.core.input.domain.HardwareButtonInputSource;
import com.dvid.dcam.core.input.domain.HardwareButtonLayout;
import com.dvid.dcam.core.input.domain.PhysicalButtonType;
import com.dvid.dcam.feature.input.application.port.HardwareButtonSettings;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Persisted developer hardware-button bindings. */
public final class SharedPreferencesHardwareButtonSettings implements HardwareButtonSettings {
    private static final String PREFERENCES = "developer_hardware_buttons";
    private static final int UNASSIGNED_KEY_CODE = -1;
    private static final int[] KEY_CODES = {
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3,
            KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6,
            KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9,
            KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12,
            KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_HEADSETHOOK
    };
    private static final String[] KEY_CODE_NAMES = {
            "KEYCODE_F1", "KEYCODE_F2", "KEYCODE_F3",
            "KEYCODE_F4", "KEYCODE_F5", "KEYCODE_F6",
            "KEYCODE_F7", "KEYCODE_F8", "KEYCODE_F9",
            "KEYCODE_F10", "KEYCODE_F11", "KEYCODE_F12",
            "KEYCODE_CAMERA", "KEYCODE_VOLUME_DOWN", "KEYCODE_HEADSETHOOK"
    };
    private static final FirmwareButtonActionFamily[] ACTION_FAMILIES = {
            FirmwareButtonActionFamily.UNASSIGNED,
            FirmwareButtonActionFamily.ACTION_CAMERA,
            FirmwareButtonActionFamily.ACTION_LASER,
            FirmwareButtonActionFamily.ACTION_PTTKEY,
            FirmwareButtonActionFamily.ACTION_RECORD,
            FirmwareButtonActionFamily.ACTION_SOS,
            FirmwareButtonActionFamily.ACTION_VIDEO
    };

    private final PreferenceAccess preferences;
    private final List<String> keyCodeLabels = buildKeyCodeLabels();
    private final List<String> actionFamilyLabels = buildActionFamilyLabels();

    public SharedPreferencesHardwareButtonSettings(Context context) {
        Objects.requireNonNull(context, "context");
        Context applicationContext = context.getApplicationContext();
        Context checkedContext = applicationContext == null ? context : applicationContext;
        SharedPreferences sharedPreferences = checkedContext.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        preferences = new SharedPreferencesAccess(sharedPreferences);
    }

    SharedPreferencesHardwareButtonSettings(PreferenceAccess preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    @Override public List<String> keyCodeLabels() { return keyCodeLabels; }

    @Override public List<String> typeLabels() { return List.of("Button", "Switch"); }

    @Override public List<String> sourceLabels() {
        return List.of("Firmware broadcast", "Key event");
    }

    @Override public List<String> actionFamilyLabels() { return actionFamilyLabels; }

    @Override public int selectedKeyCodeIndex(ButtonRole role) {
        int keyCode = stored(role).keyCode;
        if (keyCode == UNASSIGNED_KEY_CODE) return 0;
        for (int index = 0; index < KEY_CODES.length; index++) {
            if (KEY_CODES[index] == keyCode) return index + 1;
        }
        return 0;
    }

    @Override public int selectedTypeIndex(ButtonRole role) {
        return stored(role).type == PhysicalButtonType.SWITCH ? 1 : 0;
    }

    @Override public int selectedSourceIndex(ButtonRole role) {
        return stored(role).source == HardwareButtonInputSource.KEY_EVENT ? 1 : 0;
    }

    @Override public int selectedActionFamilyIndex(ButtonRole role) {
        FirmwareButtonActionFamily selected = stored(role).family;
        for (int index = 0; index < ACTION_FAMILIES.length; index++) {
            if (ACTION_FAMILIES[index] == selected) return index;
        }
        return 0;
    }

    @Override public void initialize(HardwareButtonLayout defaults) {
        if (defaults == null || defaults.isEmpty()) return;
        Map<String, String> values = new LinkedHashMap<>(preferences.all());
        if (values.isEmpty()) {
            writeDefaults(defaults);
        } else {
            migrateLegacyBindings(values, defaults);
        }
    }

    private void migrateLegacyBindings(
            Map<String, String> values, HardwareButtonLayout defaults) {
        Map<ButtonRole, FirmwareButtonActionFamily> defaultFamilies =
                new EnumMap<>(ButtonRole.class);
        for (HardwareButtonBinding binding : defaults.bindings()) {
            if (binding.hasFirmwareBroadcast()) {
                defaultFamilies.putIfAbsent(binding.role(), binding.firmwareActionFamily());
            }
        }
        boolean changed = false;
        for (ButtonRole role : ButtonRole.values()) {
            String encoded = values.get(role.name());
            FirmwareButtonActionFamily family = defaultFamilies.get(role);
            if (!isLegacy(encoded) || family == null) continue;
            StoredBinding legacy = decode(encoded);
            values.put(role.name(), encode(new StoredBinding(
                    legacy.keyCode,
                    legacy.type,
                    HardwareButtonInputSource.FIRMWARE_BROADCAST,
                    family)));
            changed = true;
        }
        if (changed) preferences.replace(values);
    }

    @Override public boolean resetToDefaults(HardwareButtonLayout defaults) {
        if (defaults == null || defaults.isEmpty()) return false;
        writeDefaults(defaults);
        return true;
    }

    private void writeDefaults(HardwareButtonLayout defaults) {
        Map<String, String> values = new LinkedHashMap<>();
        Set<ButtonRole> configuredRoles = new HashSet<>();
        for (HardwareButtonBinding binding : defaults.bindings()) {
            if (!configuredRoles.add(binding.role())) continue;
            values.put(binding.role().name(), encode(new StoredBinding(
                    binding.buttonKeyCode(),
                    binding.type(),
                    HardwareButtonInputSource.FIRMWARE_BROADCAST,
                    binding.firmwareActionFamily())));
        }
        preferences.replace(values);
    }

    @Override public void selectKeyCode(ButtonRole role, int selectedIndex) {
        requireRole(role);
        if (selectedIndex < 0 || selectedIndex > KEY_CODES.length) {
            throw new IllegalArgumentException(
                    "Invalid button key code selection " + selectedIndex);
        }
        int keyCode = selectedIndex == 0
                ? UNASSIGNED_KEY_CODE : KEY_CODES[selectedIndex - 1];
        Map<String, String> values = mutableValues();
        if (keyCode != UNASSIGNED_KEY_CODE) {
            for (ButtonRole otherRole : ButtonRole.values()) {
                if (otherRole == role) continue;
                String encoded = values.get(otherRole.name());
                if (encoded == null) continue;
                StoredBinding other = decode(encoded);
                if (other.keyCode == keyCode) {
                    values.put(otherRole.name(), encode(other.withKeyCode(UNASSIGNED_KEY_CODE)));
                }
            }
        }
        values.put(role.name(), encode(stored(values, role).withKeyCode(keyCode)));
        preferences.replace(values);
    }

    @Override public void selectType(ButtonRole role, int selectedIndex) {
        requireRole(role);
        if (selectedIndex < 0 || selectedIndex >= typeLabels().size()) {
            throw new IllegalArgumentException("Invalid button type selection " + selectedIndex);
        }
        Map<String, String> values = mutableValues();
        values.put(role.name(), encode(stored(values, role).withType(
                selectedIndex == 1 ? PhysicalButtonType.SWITCH : PhysicalButtonType.BUTTON)));
        preferences.replace(values);
    }

    @Override public void selectSource(ButtonRole role, int selectedIndex) {
        requireRole(role);
        if (selectedIndex < 0 || selectedIndex >= sourceLabels().size()) {
            throw new IllegalArgumentException("Invalid input source selection " + selectedIndex);
        }
        Map<String, String> values = mutableValues();
        values.put(role.name(), encode(stored(values, role).withSource(
                selectedIndex == 1
                        ? HardwareButtonInputSource.KEY_EVENT
                        : HardwareButtonInputSource.FIRMWARE_BROADCAST)));
        preferences.replace(values);
    }

    @Override public void selectActionFamily(ButtonRole role, int selectedIndex) {
        requireRole(role);
        if (selectedIndex < 0 || selectedIndex >= ACTION_FAMILIES.length) {
            throw new IllegalArgumentException(
                    "Invalid firmware action family selection " + selectedIndex);
        }
        FirmwareButtonActionFamily family = ACTION_FAMILIES[selectedIndex];
        Map<String, String> values = mutableValues();
        if (family.isAssigned()) {
            for (ButtonRole otherRole : ButtonRole.values()) {
                if (otherRole == role) continue;
                String encoded = values.get(otherRole.name());
                if (encoded == null) continue;
                StoredBinding other = decode(encoded);
                if (other.family == family) {
                    values.put(otherRole.name(), encode(
                            other.withFamily(FirmwareButtonActionFamily.UNASSIGNED)));
                }
            }
        }
        values.put(role.name(), encode(stored(values, role).withFamily(family)));
        preferences.replace(values);
    }

    @Override public HardwareButtonLayout loadLayout() {
        List<HardwareButtonBinding> bindings = new ArrayList<>();
        Set<Integer> usedKeyCodes = new HashSet<>();
        Set<FirmwareButtonActionFamily> usedFamilies = new HashSet<>();
        for (ButtonRole role : ButtonRole.values()) {
            String encoded = preferences.get(role.name());
            if (encoded == null) continue;
            StoredBinding stored = decode(encoded);
            if (stored.source == HardwareButtonInputSource.KEY_EVENT) {
                if (stored.keyCode == UNASSIGNED_KEY_CODE
                        || !usedKeyCodes.add(stored.keyCode)) continue;
            } else if (!stored.family.isAssigned() || !usedFamilies.add(stored.family)) {
                continue;
            }
            bindings.add(new HardwareButtonBinding(
                    role, stored.keyCode, stored.type, stored.source, stored.family));
        }
        return new HardwareButtonLayout(bindings.toArray(new HardwareButtonBinding[0]));
    }

    private StoredBinding stored(ButtonRole role) {
        requireRole(role);
        return decode(preferences.get(role.name()));
    }

    private static StoredBinding stored(Map<String, String> values, ButtonRole role) {
        return decode(values.get(role.name()));
    }

    private Map<String, String> mutableValues() {
        return new LinkedHashMap<>(preferences.all());
    }

    private static List<String> buildKeyCodeLabels() {
        List<String> values = new ArrayList<>();
        values.add("Unassigned");
        for (String keyCodeName : KEY_CODE_NAMES) values.add(keyCodeName);
        return List.copyOf(values);
    }

    private static List<String> buildActionFamilyLabels() {
        List<String> values = new ArrayList<>();
        for (FirmwareButtonActionFamily family : ACTION_FAMILIES) {
            values.add(family.label());
        }
        return List.copyOf(values);
    }

    private static String encode(StoredBinding binding) {
        return binding.keyCode + ":" + binding.type.name() + ":"
                + binding.source.name() + ":" + binding.family.name();
    }

    private static boolean isLegacy(String encoded) {
        return encoded != null && encoded.split(":", -1).length <= 2;
    }

    private static StoredBinding decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return StoredBinding.defaults();
        String[] parts = encoded.split(":", -1);
        int keyCode = parseKeyCode(parts[0]);
        PhysicalButtonType type = parts.length > 1
                ? parseType(parts[1]) : PhysicalButtonType.BUTTON;
        HardwareButtonInputSource source = parts.length > 2
                ? parseSource(parts[2]) : HardwareButtonInputSource.KEY_EVENT;
        FirmwareButtonActionFamily family = parts.length > 3
                ? parseFamily(parts[3]) : FirmwareButtonActionFamily.UNASSIGNED;
        return new StoredBinding(keyCode, type, source, family);
    }

    private static int parseKeyCode(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return UNASSIGNED_KEY_CODE;
        }
    }

    private static PhysicalButtonType parseType(String value) {
        try {
            return PhysicalButtonType.valueOf(value);
        } catch (IllegalArgumentException error) {
            return PhysicalButtonType.BUTTON;
        }
    }

    private static HardwareButtonInputSource parseSource(String value) {
        try {
            return HardwareButtonInputSource.valueOf(value);
        } catch (IllegalArgumentException error) {
            return HardwareButtonInputSource.KEY_EVENT;
        }
    }

    private static FirmwareButtonActionFamily parseFamily(String value) {
        try {
            return FirmwareButtonActionFamily.valueOf(value);
        } catch (IllegalArgumentException error) {
            return FirmwareButtonActionFamily.UNASSIGNED;
        }
    }

    private static void requireRole(ButtonRole role) {
        if (role == null) throw new IllegalArgumentException("role is required");
    }

    interface PreferenceAccess {
        String get(String key);
        Map<String, String> all();
        void replace(Map<String, String> values);
    }

    private static final class SharedPreferencesAccess implements PreferenceAccess {
        private final SharedPreferences preferences;

        private SharedPreferencesAccess(SharedPreferences preferences) {
            this.preferences = Objects.requireNonNull(preferences, "preferences");
        }

        @Override public String get(String key) {
            return preferences.getString(key, null);
        }

        @Override public Map<String, String> all() {
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                if (entry.getValue() instanceof String) {
                    values.put(entry.getKey(), (String) entry.getValue());
                }
            }
            return values;
        }

        @Override public void replace(Map<String, String> values) {
            SharedPreferences.Editor editor = preferences.edit().clear();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                editor.putString(entry.getKey(), entry.getValue());
            }
            editor.apply();
        }
    }

    private static final class StoredBinding {
        private final int keyCode;
        private final PhysicalButtonType type;
        private final HardwareButtonInputSource source;
        private final FirmwareButtonActionFamily family;

        private StoredBinding(
                int keyCode,
                PhysicalButtonType type,
                HardwareButtonInputSource source,
                FirmwareButtonActionFamily family) {
            this.keyCode = keyCode;
            this.type = type;
            this.source = source;
            this.family = family;
        }

        private StoredBinding withKeyCode(int keyCode) {
            return new StoredBinding(keyCode, type, source, family);
        }

        private StoredBinding withType(PhysicalButtonType type) {
            return new StoredBinding(keyCode, type, source, family);
        }

        private StoredBinding withSource(HardwareButtonInputSource source) {
            return new StoredBinding(keyCode, type, source, family);
        }

        private StoredBinding withFamily(FirmwareButtonActionFamily family) {
            return new StoredBinding(keyCode, type, source, family);
        }

        private static StoredBinding defaults() {
            return new StoredBinding(
                    UNASSIGNED_KEY_CODE,
                    PhysicalButtonType.BUTTON,
                    HardwareButtonInputSource.FIRMWARE_BROADCAST,
                    FirmwareButtonActionFamily.UNASSIGNED);
        }
    }
}
