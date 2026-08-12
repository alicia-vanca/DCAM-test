package com.dvid.dcam.platform.storage;

import java.util.Set;

public final class StagedMediaRecoveryReport {
    private final int recovered;
    private final int preserved;
    private final int duplicates;
    private final RuntimeException failure;
    private final Set<String> resolvedFileNames;

    StagedMediaRecoveryReport(int recovered, int preserved, int duplicates) {
        this(recovered, preserved, duplicates, Set.of());
    }

    StagedMediaRecoveryReport(
            int recovered, int preserved, int duplicates, Set<String> resolvedFileNames) {
        this(recovered, preserved, duplicates, null, resolvedFileNames);
    }

    private StagedMediaRecoveryReport(
            int recovered,
            int preserved,
            int duplicates,
            RuntimeException failure,
            Set<String> resolvedFileNames) {
        this.recovered = recovered;
        this.preserved = preserved;
        this.duplicates = duplicates;
        this.failure = failure;
        this.resolvedFileNames = Set.copyOf(resolvedFileNames);
    }

    static StagedMediaRecoveryReport failed(RuntimeException failure) {
        return new StagedMediaRecoveryReport(0, 0, 0, failure, Set.of());
    }

    public int getRecovered() { return recovered; }
    public int getPreserved() { return preserved; }
    public int getDuplicates() { return duplicates; }
    public boolean hasFailure() { return failure != null; }
    public RuntimeException getFailure() { return failure; }
    public boolean isResolved(String fileName) {
        return fileName != null && failure == null && resolvedFileNames.contains(fileName);
    }
}