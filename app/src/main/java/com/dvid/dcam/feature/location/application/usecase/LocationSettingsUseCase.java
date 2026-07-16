package com.dvid.dcam.feature.location.application.usecase;

import com.dvid.dcam.feature.location.domain.GpsMode;
import com.dvid.dcam.feature.location.domain.GpsSettings;
import java.util.List;

public interface LocationSettingsUseCase {
    GpsSettings currentSettings();
    List<GpsMode> supportedModes();
    List<Integer> supportedSamplingValues();
    void changeMode(GpsMode mode);
    void changeUpdateDistanceMeters(int meters);
    void changeReportIntervalSeconds(int seconds);
}
