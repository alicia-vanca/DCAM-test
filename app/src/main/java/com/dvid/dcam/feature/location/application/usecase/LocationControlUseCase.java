package com.dvid.dcam.feature.location.application.usecase;

import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.LocationSystemState;

public interface LocationControlUseCase {
    LocationSystemState currentState();
    boolean isModeAvailable(GpsMode mode);
    boolean setEnabledIfPermitted(boolean enabled);
}
