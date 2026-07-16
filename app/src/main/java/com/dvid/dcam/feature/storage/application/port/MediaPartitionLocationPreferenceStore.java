package com.dvid.dcam.feature.storage.application.port;

import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;

/** Persistence boundary for the operator-selected media storage policy. */
public interface MediaPartitionLocationPreferenceStore {
    MediaPartitionLocation currentMediaPartitionLocation();
    void selectMediaPartitionLocation(MediaPartitionLocation mode);
}


