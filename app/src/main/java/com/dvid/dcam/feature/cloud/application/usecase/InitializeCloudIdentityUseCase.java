package com.dvid.dcam.feature.cloud.application.usecase;

import com.dvid.dcam.feature.cloud.application.port.DeviceIdentityRepository;
import com.dvid.dcam.feature.cloud.domain.DeviceCloudIdentity;
import com.dvid.dcam.core.device.domain.DeviceInfo;

public final class InitializeCloudIdentityUseCase {
    private final DeviceIdentityRepository repository;

    public InitializeCloudIdentityUseCase(DeviceIdentityRepository repository) {
        this.repository = repository;
    }

    public DeviceCloudIdentity execute(DeviceInfo deviceInfo) {
        return repository.restoreOrCreate(deviceInfo);
    }
}
