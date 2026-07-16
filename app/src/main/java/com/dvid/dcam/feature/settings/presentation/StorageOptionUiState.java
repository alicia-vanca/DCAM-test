package com.dvid.dcam.feature.settings.presentation;

/** Localized storage option data prepared for settings rendering. */
public final class StorageOptionUiState {
    private final String label;
    private final String detail;
    private final int usedPercent;
    private final boolean available;

    public StorageOptionUiState(String label, String detail, int usedPercent, boolean available) {
        this.label = label;
        this.detail = detail;
        this.usedPercent = Math.max(0, Math.min(100, usedPercent));
        this.available = available;
    }

    public String getLabel() { return label; }
    public String getDetail() { return detail; }
    public int getUsedPercent() { return usedPercent; }
    public boolean isAvailable() { return available; }
}
