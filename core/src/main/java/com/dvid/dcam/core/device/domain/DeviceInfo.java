package com.dvid.dcam.core.device.domain;

/** Stable device identity exposed to application and diagnostics layers. */
public final class DeviceInfo {
    private final String hardwareId;
    private final String model;

    public DeviceInfo(String hardwareId, String model) {
        this.hardwareId = hardwareId;
        this.model = model;
    }

    public String getHardwareId() { return hardwareId; }
    public String getModel() { return model; }
}
