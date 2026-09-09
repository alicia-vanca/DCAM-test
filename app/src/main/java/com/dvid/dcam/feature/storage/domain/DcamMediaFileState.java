package com.dvid.dcam.feature.storage.domain;

/** Lifecycle state of a captured media file for publication and recovery. */
public enum DcamMediaFileState {
    IN_PROGRESS,
    FINALIZING,
    FINALIZED,
    RECOVERY_REQUIRED,
    RECOVERY_FAILED,
    BDMA_READY
}
