package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.feature.device.application.port.DeviceSerialNumberStore;
import org.junit.jupiter.api.Test;

final class DeviceSerialNumberUseCaseTest {
    @Test void validatesAndTrimsManualSerial() throws Exception {
        FakeStore store = new FakeStore();
        DeviceSerialNumberUseCase useCase = new DeviceSerialNumberUseCase(store);

        useCase.save(" ABC123 ");

        assertEquals("ABC123", store.saved);
        assertTrue(useCase.isConfigured(store.saved));
        assertFalse(useCase.isValid("ABC-123"));
        assertFalse(useCase.isConfigured("ABCDEFGHIJK"));
        assertThrows(IllegalArgumentException.class, () -> useCase.save("ABCDE"));
    }


    private static final class FakeStore implements DeviceSerialNumberStore {
        private String saved = "";
        @Override public String load() { return saved; }
        @Override public boolean restoreIfAvailable() { return false; }
        @Override public void save(String serial) { saved = serial; }
    }
}