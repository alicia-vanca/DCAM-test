package com.dvid.dcam.feature.device.domain.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class FrameRatePolicyTest {
    @Test void fractionalInputsCeilBeforePruning() {
        assertEquals(20, FrameRatePolicy.normalize(19.1));
        assertEquals(20, FrameRatePolicy.normalize(19.2));
        assertEquals(List.of(20), FrameRatePolicy.retainSupported(
                List.of(19, 19.2)));
    }

    @Test void allRatesAtLeastThirtyAreRetained() {
        assertEquals(List.of(30, 60), FrameRatePolicy.retainSupported(
                List.of(19, 29, 30, 60, 30)));
    }

    @Test void highestRateBetweenNineteenAndThirtyWinsWithoutPreferredRates() {
        assertEquals(List.of(29), FrameRatePolicy.retainSupported(
                List.of(20, 25, 29)));
        assertEquals(List.of(), FrameRatePolicy.retainSupported(List.of(19)));
    }

    @Test void nonFiniteAndNonPositiveInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FrameRatePolicy.normalize(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> FrameRatePolicy.normalize(0));
    }
}