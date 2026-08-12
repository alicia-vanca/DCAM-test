package com.dvid.dcam.app.ui.settings;

import java.util.List;

/** Presentation model for one settings screen. */
public final class SettingsScreenModel {
    private final List<SettingsSection> sections;

    public SettingsScreenModel(List<SettingsSection> sections) {
        this.sections = List.copyOf(sections);
    }

    public List<SettingsSection> getSections() { return sections; }
}
