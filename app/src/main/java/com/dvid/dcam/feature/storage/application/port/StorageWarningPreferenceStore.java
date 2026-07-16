package com.dvid.dcam.feature.storage.application.port;

/** Persistence boundary for low-storage warning threshold. */
public interface StorageWarningPreferenceStore {
    int warningGb();
    void setWarningGb(int value);
}
