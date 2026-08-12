package com.dvid.dcam.feature.device.application.usecase;

import com.dvid.dcam.feature.device.application.port.DeviceSerialNumberStore;
import java.io.IOException;

public final class DeviceSerialNumberUseCase {
    private final DeviceSerialNumberStore store;

    public DeviceSerialNumberUseCase(DeviceSerialNumberStore store) {
        if (store == null) throw new IllegalArgumentException("store is required");
        this.store = store;
    }

    public String load() { return store.load(); }

    public boolean restoreIfAvailable() throws IOException {
        return store.restoreIfAvailable();
    }

    public void save(String serial) throws IOException {
        if (!isValid(serial)) {
            throw new IllegalArgumentException("serial number must be 6-10 characters");
        }
        store.save(serial.trim());
    }


    // Manual and restored serials share same 6-10 character policy.
    public boolean isValid(String serial) {
        return serial != null && serial.trim().matches("[A-Z0-9]{6,10}");
    }

    public boolean isConfigured(String serial) {
        return isValid(serial);
    }
}