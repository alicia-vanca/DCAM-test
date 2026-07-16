package com.dvid.dcam.feature.location.domain;

import java.util.Locale;

/** A latitude/longitude pair rendered in the DMS format used by the camera HUD. */
public final class GpsCoordinate {
    private final double latitude;
    private final double longitude;

    public GpsCoordinate(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid GPS coordinate");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    @Override public String toString() {
        return dms(latitude, true) + " " + dms(longitude, false);
    }

    private static String dms(double value, boolean latitude) {
        double absolute = Math.abs(value);
        int degrees = (int) absolute;
        double minuteValue = (absolute - degrees) * 60.0;
        int minutes = (int) minuteValue;
        double seconds = (minuteValue - minutes) * 60.0;
        if (seconds >= 59.995) {
            seconds = 0.0;
            minutes++;
            if (minutes == 60) {
                minutes = 0;
                degrees++;
            }
        }
        String direction = latitude
                ? (value < 0 ? "S" : "N")
                : (value < 0 ? "W" : "E");
        return String.format(Locale.ROOT, "%d\u00B0%02d'%05.2f\"%s",
                degrees, minutes, seconds, direction);
    }
}
