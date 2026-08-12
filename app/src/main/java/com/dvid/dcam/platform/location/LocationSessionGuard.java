package com.dvid.dcam.platform.location;

/** Issues session tokens and rejects callbacks after restart or stop. */
final class LocationSessionGuard {
    private long generation;
    private boolean active;

    synchronized long startNewSession() {
        active = true;
        return ++generation;
    }

    synchronized void stopSession() {
        active = false;
        generation++;
    }

    synchronized boolean isCurrent(long candidateGeneration) {
        return active && candidateGeneration == generation;
    }

    synchronized long currentSession() {
        return active ? generation : -1L;
    }
}