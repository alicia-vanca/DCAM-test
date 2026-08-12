package com.dvid.dcam.feature.device.application.port;

import com.dvid.dcam.core.device.domain.DeviceInfo;
import com.dvid.dcam.feature.device.domain.DeviceStatus;

/** Repository boundary for device identity and capability status. */
public interface DeviceRepository {
    DeviceInfo readInfo();
    DeviceStatus readStatus();
}
