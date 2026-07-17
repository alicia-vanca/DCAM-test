package com.dvid.dcam.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

public class DeviceSerialNumberStoreTest {
    @Test public void loadsSavedSerialNumber() throws Exception {
        File dir = Files.createTempDirectory("serial").toFile();
        File target = new File(dir, "Config/dcam_config.cson");
        Files.createDirectories(target.getParentFile().toPath());
        Files.writeString(target.toPath(), "[device]\nserial_number=\"ABC123\"");
        DeviceSerialNumberStore store = new DeviceSerialNumberStore(target);
        assertEquals("ABC123", store.load());
    }

    @Test public void missingConfigDoesNotPersistFallbackSerial() throws Exception {
        File dir = Files.createTempDirectory("serial-missing").toFile();
        File target = new File(dir, "Config/dcam_config.cson");

        assertEquals("", new DeviceSerialNumberStore(target).load());
        assertFalse(target.exists());
    }

    @Test public void acceptsPreviouslySavedDefaultOutsideManualLengthLimit() throws Exception {
        File dir = Files.createTempDirectory("serial-default").toFile();
        File target = new File(dir, "dcam_config.cson");
        Files.writeString(target.toPath(), "serial_number=\"36NCC090114\"");
        assertEquals("36NCC090114", new DeviceSerialNumberStore(target).load());
    }

    @Test public void saveSynchronizesDisplayedAccountId() throws Exception {
        File dir = Files.createTempDirectory("serial-sync").toFile();
        File target = new File(dir, "dcam_config.cson");
        Files.writeString(target.toPath(), "serial_number=\"OLD123\"\n");
        DeviceSerialNumberStore store = new DeviceSerialNumberStore(target);

        store.save("USER123");

        String text = Files.readString(target.toPath());
        assertTrue(text.contains("serial_number=\"USER123\""));
        assertFalse(text.contains("account.user_id"));
    }

    @Test public void saveReplacesConfigWithoutLeavingPartialFile() throws Exception {
        File dir = Files.createTempDirectory("serial-atomic").toFile();
        File target = new File(dir, "dcam_config.cson");
        Files.writeString(target.toPath(), "serial_number=\"OLD123\"\nother=\"keep\"\n");

        new DeviceSerialNumberStore(target).save("NEW123");

        assertEquals("serial_number=\"NEW123\"\nother=\"keep\"\n",
                Files.readString(target.toPath()));
        try (var files = Files.list(dir.toPath())) {
            assertEquals(1, files.count());
        }
    }

    @Test public void validatesSixToTenAsciiAlphaNumericCharacters() {
        assertTrue(DeviceSerialNumberStore.isValid("ABC123"));
        assertFalse(DeviceSerialNumberStore.isValid("ABCDE"));
        assertFalse(DeviceSerialNumberStore.isValid("ABCDEFGHIJK"));
        assertFalse(DeviceSerialNumberStore.isValid("ABC-123"));
    }
}
