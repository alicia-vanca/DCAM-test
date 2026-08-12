package com.dvid.dcam.platform.location;

/** Selects first source within one tracking session and rejects stale callbacks. */
final class LocationSourceRace {
    enum Source {
        SATELLITE,
        NETWORK
    }

    private final LocationSessionGuard sessions = new LocationSessionGuard();
    private Source winner;

    synchronized long startNewSession() {
        winner = null;
        return sessions.startNewSession();
    }

    synchronized void stopSession() {
        sessions.stopSession();
        winner = null;
    }

    synchronized boolean tryWin(long candidateGeneration, Source source) {
        if (!isCurrent(candidateGeneration) || source == null || winner != null) return false;
        winner = source;
        return true;
    }

    synchronized boolean accepts(long candidateGeneration, Source source) {
        return isCurrent(candidateGeneration) && source != null && winner == source;
    }

    synchronized boolean isCurrent(long candidateGeneration) {
        return sessions.isCurrent(candidateGeneration);
    }

    synchronized Source winner() { return winner; }
}
