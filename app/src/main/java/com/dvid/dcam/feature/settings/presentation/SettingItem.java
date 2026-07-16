package com.dvid.dcam.feature.settings.presentation;

import java.util.List;

/** Immutable control description for one settings row. */
public final class SettingItem {
    public enum Type {
        TEXT,
        CHECKBOX,
        CHOICE,
        SLIDER,
        RADIO,
        DESCRIBED_RADIO,
        STORAGE_RADIO,
        ACTION
    }

    private final SettingId id;
    private final Type type;
    private final String label;
    private final String value;
    private final List<String> options;
    private final List<StorageOptionUiState> storageOptions;
    private final List<DescribedRadioOptionUiState> describedRadioOptions;
    private final int selectedIndex;
    private final boolean checked;
    private final int min;
    private final int max;
    private final int numberValue;
    private final String unit;
    private final boolean enabled;
    private final int indentLevel;

    private SettingItem(
            SettingId id, Type type, String label, String value, List<String> options,
            List<StorageOptionUiState> storageOptions,
            List<DescribedRadioOptionUiState> describedRadioOptions,
            int selectedIndex, boolean checked,
            int min, int max, int numberValue, String unit, boolean enabled, int indentLevel) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.value = value;
        this.options = options == null ? List.of() : List.copyOf(options);
        this.storageOptions = storageOptions == null ? List.of() : List.copyOf(storageOptions);
        this.describedRadioOptions = describedRadioOptions == null
                ? List.of() : List.copyOf(describedRadioOptions);
        this.selectedIndex = selectedIndex;
        this.checked = checked;
        this.min = min;
        this.max = max;
        this.numberValue = numberValue;
        this.unit = unit;
        this.enabled = enabled;
        this.indentLevel = indentLevel;
    }

    private static SettingItem item(SettingId id, Type type, String label, String value,
            List<String> options, int selectedIndex, boolean checked, int min, int max,
            int numberValue, String unit) {
        return new SettingItem(id, type, label, value, options, null, null, selectedIndex, checked,
                min, max, numberValue, unit, true, 0);
    }

    public static SettingItem text(String label, String value) {
        return item(null, Type.TEXT, label, value, null, 0, false, 0, 0, 0, null);
    }

    public static SettingItem checkbox(SettingId id, String label, boolean checked) {
        return item(id, Type.CHECKBOX, label, null, null, 0, checked, 0, 0, 0, null);
    }

    public static SettingItem choice(
            SettingId id, String label, List<String> options, int selectedIndex) {
        return item(id, Type.CHOICE, label, null, options, selectedIndex, false, 0, 0, 0, null);
    }

    public static SettingItem slider(
            SettingId id, String label, int min, int max, int value, String unit) {
        return item(id, Type.SLIDER, label, null, null, 0, false, min, max, value, unit);
    }

    public static SettingItem radio(
            SettingId id, String label, List<String> options, int selectedIndex) {
        return item(id, Type.RADIO, label, null, options, selectedIndex, false, 0, 0, 0, null);
    }

    public static SettingItem describedRadio(SettingId id, String label,
            List<DescribedRadioOptionUiState> options, int selectedIndex) {
        return new SettingItem(id, Type.DESCRIBED_RADIO, label, null, null, null, options,
                selectedIndex, false, 0, 0, 0, null, true, 0);
    }
    public static SettingItem storageRadio(SettingId id, String label,
            List<StorageOptionUiState> options, int selectedIndex) {
        return new SettingItem(id, Type.STORAGE_RADIO, label, null, null, options, null,
                selectedIndex, false, 0, 0, 0, null, true, 0);
    }

    public static SettingItem action(SettingId id, String label) {
        return item(id, Type.ACTION, label, null, null, 0, false, 0, 0, 0, null);
    }

    public SettingItem withEnabled(boolean enabled) {
        return new SettingItem(id, type, label, value, options, storageOptions,
                describedRadioOptions, selectedIndex,
                checked, min, max, numberValue, unit, enabled, indentLevel);
    }

    public SettingItem withIndentLevel(int indentLevel) {
        return new SettingItem(id, type, label, value, options, storageOptions,
                describedRadioOptions, selectedIndex,
                checked, min, max, numberValue, unit, enabled, Math.max(0, indentLevel));
    }

    public SettingId getId() { return id; }
    public Type getType() { return type; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public List<String> getOptions() { return options; }
    public List<StorageOptionUiState> getStorageOptions() { return storageOptions; }
    public List<DescribedRadioOptionUiState> getDescribedRadioOptions() {
        return describedRadioOptions;
    }
    public int getSelectedIndex() { return selectedIndex; }
    public boolean isChecked() { return checked; }
    public int getMin() { return min; }
    public int getMax() { return max; }
    public int getNumberValue() { return numberValue; }
    public String getUnit() { return unit; }
    public boolean isEnabled() { return enabled; }
    public int getIndentLevel() { return indentLevel; }
}
