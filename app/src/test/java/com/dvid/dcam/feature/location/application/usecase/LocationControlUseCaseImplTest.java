package com.dvid.dcam.feature.location.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.location.application.port.GpsSettingsStore;
import com.dvid.dcam.feature.location.application.port.LocationControlGateway;
import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationSystemState;
import org.junit.jupiter.api.Test;

final class LocationControlUseCaseImplTest {
    @Test void readsAndPersistsTheActualSystemState() {
        FakeGateway gateway = new FakeGateway();
        FakeStore store = new FakeStore();
        LocationControlUseCase control = new LocationControlUseCaseImpl(gateway, store);

        gateway.state = LocationSystemState.ENABLED;
        assertEquals(LocationSystemState.ENABLED, control.currentState());
        assertEquals(LocationSystemState.ENABLED, store.settings.getSystemState());

        gateway.state = LocationSystemState.UNAVAILABLE;
        assertEquals(LocationSystemState.UNAVAILABLE, control.currentState());
        assertEquals(LocationSystemState.UNAVAILABLE, store.settings.getSystemState());
    }

    @Test void setEnabledRefreshesStateAfterGatewayCall() {
        FakeGateway gateway = new FakeGateway();
        FakeStore store = new FakeStore();
        LocationControlUseCase control = new LocationControlUseCaseImpl(gateway, store);

        assertEquals(true, control.setEnabledIfPermitted(true));
        assertEquals(LocationSystemState.ENABLED, store.settings.getSystemState());
        assertEquals(true, gateway.lastRequestedState);
    }

    private static final class FakeGateway implements LocationControlGateway {
        private LocationSystemState state = LocationSystemState.UNKNOWN;
        private boolean lastRequestedState;

        @Override public LocationSystemState currentState() { return state; }
        @Override public boolean isModeAvailable(GpsMode mode) { return true; }
        @Override public boolean setEnabled(boolean enabled) {
            lastRequestedState = enabled;
            state = enabled ? LocationSystemState.ENABLED : LocationSystemState.DISABLED;
            return true;
        }
    }

    private static final class FakeStore implements GpsSettingsStore {
        private GpsSettings settings = new GpsSettings(
                GpsMode.GPS, 1, 1, LocationSystemState.UNKNOWN);

        @Override public GpsSettings load() { return settings; }
        @Override public void save(GpsSettings value) { settings = value; }
        @Override public void saveSystemState(LocationSystemState state) {
            settings = settings.withSystemState(state);
        }
    }
}
