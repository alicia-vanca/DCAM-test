package com.dvid.dcam.feature.location.application.port;

import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.LocationSystemState;

public interface LocationControlGateway {
    LocationSystemState currentState();
    boolean isModeAvailable(GpsMode mode);
    boolean setEnabled(boolean enabled);
}
