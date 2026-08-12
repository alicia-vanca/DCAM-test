package com.dvid.dcam.feature.capture.application.port;

public interface AudioPreparationEvents {
    interface Binding extends AutoCloseable {
        @Override void close();
    }

    void onPreparing(String message);
    void onCleared();
    void onUnavailable(String message);
}