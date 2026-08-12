package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.feature.device.application.port.DeviceRepository;
import com.dvid.dcam.feature.device.domain.DeviceStatus;

public final class RefreshDeviceStatusUseCase {
    private final DeviceRepository repository;

    public RefreshDeviceStatusUseCase(DeviceRepository repository) {
        this.repository = repository;
    }

    public DeviceStatus execute() {
        return repository.readStatus();
    }
}
