package com.dvid.dcam.feature.location.application.port;

import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;

public interface GpsSettingsStore {
    GpsSettings load();
    void save(GpsSettings settings);
    void saveSystemState(LocationSystemState state);
}
