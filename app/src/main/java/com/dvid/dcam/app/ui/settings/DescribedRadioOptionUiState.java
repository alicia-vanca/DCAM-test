package com.dvid.dcam.app.ui.settings;

/** Localized label and description for one common radio option. */
public final class DescribedRadioOptionUiState {
    private final String label;
    private final String description;
    private final boolean enabled;
    private final SettingItem nestedChoice;

    public DescribedRadioOptionUiState(String label, String description) {
        this(label, description, true, null);
    }

    public DescribedRadioOptionUiState(String label, String description, boolean enabled) {
        this(label, description, enabled, null);
    }

    private DescribedRadioOptionUiState(
            String label, String description, boolean enabled, SettingItem nestedChoice) {
        this.label = label;
        this.description = description;
        this.enabled = enabled;
        this.nestedChoice = nestedChoice;
    }

    public DescribedRadioOptionUiState withNestedChoice(SettingItem choice) {
        if (choice == null || choice.getType() != SettingItem.Type.CHOICE) {
            throw new IllegalArgumentException("Nested radio control must be a choice");
        }
        return new DescribedRadioOptionUiState(label, description, enabled, choice);
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public SettingItem getNestedChoice() { return nestedChoice; }
}
