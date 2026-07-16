package com.dvid.dcam.feature.location.application.port;

import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import java.util.function.Consumer;

public interface LocationSource {
    LocationTrackingState start(GpsSettings settings, Consumer<GpsCoordinate> onCoordinate);
    default LocationTrackingState start(
            GpsSettings settings,
            Consumer<GpsCoordinate> onCoordinate,
            Consumer<LocationTrackingState> onStateChanged) {
        LocationTrackingState state = start(settings, onCoordinate);
        onStateChanged.accept(state);
        return state;
    }
    void stop();
    GpsCoordinate latestCoordinate();
}
