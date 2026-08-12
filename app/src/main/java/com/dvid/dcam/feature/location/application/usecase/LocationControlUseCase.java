package com.dvid.dcam.feature.location.application.usecase;

import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.application.port.LocationControlGateway;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.LocationSystemState;

public final class LocationControlUseCase {
    private final LocationControlGateway gateway;
    private final GpsSettingsStore store;

    public LocationControlUseCase(LocationControlGateway gateway, GpsSettingsStore store) {
        if (gateway == null || store == null) throw new IllegalArgumentException("GPS dependencies are required");
        this.gateway = gateway;
        this.store = store;
    }

    public LocationSystemState currentState() {
        LocationSystemState state = gateway.currentState();
        store.saveSystemState(state);
        return state;
    }

    public boolean isModeAvailable(GpsMode mode) { return gateway.isModeAvailable(mode); }

    public boolean setEnabledIfPermitted(boolean enabled) {
        boolean changed = gateway.setEnabled(enabled);
        currentState();
        return changed;
    }
}
