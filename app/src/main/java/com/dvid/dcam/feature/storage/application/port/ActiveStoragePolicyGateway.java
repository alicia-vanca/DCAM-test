package com.dvid.dcam.feature.storage.application.port;

import com.dvid.dcam.feature.storage.domain.MediaPartitionLocation;

/** Updates storage policy used by the next capture without process restart. */
public interface ActiveStoragePolicyGateway {
    void changeRequestedLocation(MediaPartitionLocation location);
}
