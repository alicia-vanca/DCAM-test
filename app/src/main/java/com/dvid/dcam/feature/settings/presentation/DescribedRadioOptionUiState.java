package com.dvid.dcam.feature.settings.presentation;

/** Localized label and description for one common radio option. */
public final class DescribedRadioOptionUiState {
    private final String label;
    private final String description;

    public DescribedRadioOptionUiState(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}