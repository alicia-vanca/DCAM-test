package com.dvid.dcam.platform.storage;

import com.dvid.dcam.feature.storage.domain.DcamMediaFileState;
import java.io.File;
import java.util.List;

@FunctionalInterface
interface DcamMediaStateStore {
    void update(String mediaFileName, File storageRoot, DcamMediaFileState state);

    default List<TrackedState> findAll() { return List.of(); }

    default void delete(String mediaFileName) {}

    static DcamMediaStateStore noOp() {
        return (mediaFileName, storageRoot, state) -> { };
    }

    record TrackedState(
            String mediaFileName, File storageRoot, DcamMediaFileState state) {}
}
