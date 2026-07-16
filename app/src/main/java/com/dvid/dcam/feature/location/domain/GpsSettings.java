package com.dvid.dcam.feature.location.domain;

/** GPS configuration persisted independently from the operational database. */
public final class GpsSettings {
    private final GpsMode mode;
    private final int updateDistanceMeters;
    private final int reportIntervalSeconds;
    private final LocationSystemState systemState;

    public GpsSettings(GpsMode mode, int updateDistanceMeters, int reportIntervalSeconds,
            LocationSystemState systemState) {
        if (mode == null || systemState == null) throw new IllegalArgumentException("GPS values are required");
        if (updateDistanceMeters <= 0 || reportIntervalSeconds <= 0) {
            throw new IllegalArgumentException("GPS sampling values must be positive");
        }
        this.mode = mode;
        this.updateDistanceMeters = updateDistanceMeters;
        this.reportIntervalSeconds = reportIntervalSeconds;
        this.systemState = systemState;
    }

    public GpsMode getMode() { return mode; }
    public int getUpdateDistanceMeters() { return updateDistanceMeters; }
    public int getReportIntervalSeconds() { return reportIntervalSeconds; }
    public LocationSystemState getSystemState() { return systemState; }

    public GpsSettings withMode(GpsMode value) {
        return new GpsSettings(value, updateDistanceMeters, reportIntervalSeconds, systemState);
    }
    public GpsSettings withUpdateDistanceMeters(int value) {
        return new GpsSettings(mode, value, reportIntervalSeconds, systemState);
    }
    public GpsSettings withReportIntervalSeconds(int value) {
        return new GpsSettings(mode, updateDistanceMeters, value, systemState);
    }
    public GpsSettings withSystemState(LocationSystemState value) {
        return new GpsSettings(mode, updateDistanceMeters, reportIntervalSeconds, value);
    }
}
