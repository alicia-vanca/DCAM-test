package com.dvid.dcam.feature.storage.domain;

/** Android-free staged-media recovery summary. */
public final class StorageRecoveryResult {
    private final int recovered;
    private final int preserved;
    private final int duplicates;

    public StorageRecoveryResult(int recovered, int preserved, int duplicates) {
        this.recovered = Math.max(0, recovered);
        this.preserved = Math.max(0, preserved);
        this.duplicates = Math.max(0, duplicates);
    }

    public int getRecovered() { return recovered; }
    public int getPreserved() { return preserved; }
    public int getDuplicates() { return duplicates; }
}
