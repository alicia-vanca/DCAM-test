package com.dvid.dcam.platform.location;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class LocationSourceRaceTest {
    @Test void firstSourceWinsAndLosingSourceIsRejected() {
        LocationSourceRace race = new LocationSourceRace();
        long generation = race.startNewSession();

        assertTrue(race.tryWin(generation, LocationSourceRace.Source.NETWORK));
        assertTrue(race.accepts(generation, LocationSourceRace.Source.NETWORK));
        assertFalse(race.tryWin(generation, LocationSourceRace.Source.SATELLITE));
        assertFalse(race.accepts(generation, LocationSourceRace.Source.SATELLITE));
        assertSame(LocationSourceRace.Source.NETWORK, race.winner());
    }

    @Test void callbackFromPreviousSessionCannotWinNewRace() {
        LocationSourceRace race = new LocationSourceRace();
        long oldGeneration = race.startNewSession();
        long currentGeneration = race.startNewSession();

        assertFalse(race.tryWin(oldGeneration, LocationSourceRace.Source.NETWORK));
        assertFalse(race.accepts(oldGeneration, LocationSourceRace.Source.NETWORK));
        assertTrue(race.tryWin(currentGeneration, LocationSourceRace.Source.SATELLITE));
        assertTrue(race.accepts(currentGeneration, LocationSourceRace.Source.SATELLITE));
    }

    @Test void stoppedSessionRejectsLateCallbacks() {
        LocationSourceRace race = new LocationSourceRace();
        long generation = race.startNewSession();

        race.stopSession();

        assertFalse(race.tryWin(generation, LocationSourceRace.Source.NETWORK));
        assertFalse(race.accepts(generation, LocationSourceRace.Source.NETWORK));
    }
}
