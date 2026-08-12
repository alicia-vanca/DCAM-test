package com.dvid.dcam.app.ui.settings;

import java.util.List;

/** A titled group of settings rows. */
public final class SettingsSection {
    private final String title;
    private final List<SettingItem> items;

    public SettingsSection(String title, List<SettingItem> items) {
        this.title = title;
        this.items = List.copyOf(items);
    }

    public String getTitle() { return title; }
    public List<SettingItem> getItems() { return items; }
}
