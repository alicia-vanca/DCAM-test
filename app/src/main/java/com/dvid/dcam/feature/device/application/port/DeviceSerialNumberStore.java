package com.dvid.dcam.feature.device.application.port;

import java.io.IOException;

public interface DeviceSerialNumberStore {
    String load();
    void save(String serial) throws IOException;

    boolean restoreIfAvailable() throws IOException;
}