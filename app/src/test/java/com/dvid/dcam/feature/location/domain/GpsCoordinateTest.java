package com.dvid.dcam.feature.location.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class GpsCoordinateTest {
    @Test void formatsLatitudeAndLongitudeAsDms() {
        GpsCoordinate coordinate = new GpsCoordinate(10.7700944444, 106.6991055556);

        assertEquals("10\u00B046'12.34\"N 106\u00B041'56.78\"E", coordinate.toString());
    }

    @Test void formatsNegativeCoordinatesWithSouthernAndWesternDirections() {
        GpsCoordinate coordinate = new GpsCoordinate(-10.7700944444, -106.6991055556);

        assertEquals("10\u00B046'12.34\"S 106\u00B041'56.78\"W", coordinate.toString());
    }

    @Test void rejectsNonFiniteAndOutOfRangeCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new GpsCoordinate(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new GpsCoordinate(91, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new GpsCoordinate(0, -181));
    }
}
