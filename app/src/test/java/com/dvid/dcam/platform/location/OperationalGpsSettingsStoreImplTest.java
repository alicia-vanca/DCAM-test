package com.dvid.dcam.platform.location;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.location.domain.GpsMode;
import org.junit.jupiter.api.Test;

final class OperationalGpsSettingsStoreImplTest {
    @Test void legacyAutomaticAndNetworkModesMigrateToFused() {
        assertEquals(GpsMode.FUSED, OperationalGpsSettingsStoreImpl.parseMode("AUTOMATIC"));
        assertEquals(GpsMode.FUSED, OperationalGpsSettingsStoreImpl.parseMode("NETWORK"));
        assertEquals(GpsMode.FUSED, OperationalGpsSettingsStoreImpl.parseMode("GPS_AGPS"));
        assertEquals(GpsMode.FUSED, OperationalGpsSettingsStoreImpl.parseMode("GMAP"));
    }

    @Test void legacySatelliteModesRemainSatellite() {
        assertEquals(GpsMode.SATELLITE,
                OperationalGpsSettingsStoreImpl.parseMode("SATELLITE"));
        assertEquals(GpsMode.SATELLITE, OperationalGpsSettingsStoreImpl.parseMode("GPS"));
    }

    @Test void unknownModeDefaultsToFused() {
        assertEquals(GpsMode.FUSED, OperationalGpsSettingsStoreImpl.parseMode("unknown"));
        assertEquals(GpsMode.FUSED, OperationalGpsSettingsStoreImpl.parseMode(null));
    }
}