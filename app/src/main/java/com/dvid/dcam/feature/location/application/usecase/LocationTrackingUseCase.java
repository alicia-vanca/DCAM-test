package com.dvid.dcam.feature.location.application.usecase;

import com.dvid.dcam.feature.location.domain.GpsCoordinate;
import com.dvid.dcam.feature.location.domain.LocationTrackingState;
import java.util.function.Consumer;

public interface LocationTrackingUseCase {
    LocationTrackingState start(Consumer<GpsCoordinate> onCoordinate);
    default LocationTrackingState start(
            Consumer<GpsCoordinate> onCoordinate,
            Consumer<LocationTrackingState> onStateChanged) {
        LocationTrackingState state = start(onCoordinate);
        onStateChanged.accept(state);
        return state;
    }
    LocationTrackingState restart();
    void stop();
    GpsCoordinate latestCoordinate();
    LocationTrackingState currentState();
}
