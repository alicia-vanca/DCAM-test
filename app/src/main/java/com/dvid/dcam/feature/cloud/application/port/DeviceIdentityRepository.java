package com.dvid.dcam.feature.cloud.application.port;

import com.dvid.dcam.feature.cloud.domain.DeviceCloudIdentity;
import com.dvid.dcam.core.device.domain.DeviceInfo;

/** Local identity store for cloud/provisioning identity state. */
public interface DeviceIdentityRepository {
    DeviceCloudIdentity restoreOrCreate(DeviceInfo deviceInfo);
    DeviceCloudIdentity current();
}
