package com.dvid.dcam.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.nio.file.Files;
import com.dvid.dcam.core.config.domain.DcamConfig;
import org.junit.jupiter.api.Test;

public class CsonConfigStoreTest {
    @Test public void loadsIdsAndEncryption() throws Exception {
        File file = File.createTempFile("configs", ".cson");
        Files.writeString(file.toPath(), "[video]\nfile.encryption=\"1\"\nfile.encrypt_password=\"custom-password\"\n"
                + "[device]\nserial_number=\"CAM001\"\npolice.user_id=\"000000\"");
        DcamConfig config = new CsonConfigStore(file).load();
        assertEquals("CAM001", config.getAccountUserId());
        assertEquals("000000", config.getPoliceUserId());
        assertTrue(config.isVideoEncrypted());
        assertEquals("custom-password", config.getMediaEncryptionPassword());
    }

    @Test public void loadsBodycamStylePrefixedEncryptionKeys() throws Exception {
        File file = File.createTempFile("configs", ".cson");
        Files.writeString(file.toPath(), "video.file.encryption=\"1\"\n"
                + "video.file.encrypt_password=\"prefixed-password\"\n");
        DcamConfig config = new CsonConfigStore(file).load();
        assertTrue(config.isVideoEncrypted());
        assertEquals("prefixed-password", config.getMediaEncryptionPassword());
    }

    @Test public void generatedConfigKeepsOperationalSettingsOutOfDeviceCson() throws Exception {
        File directory = Files.createTempDirectory("dcam-config").toFile();
        File file = new File(directory, "configs.cson");
        DcamConfig config = new CsonConfigStore(file, "CAMDEFAULT", "build-password").load();

        assertEquals("CAMDEFAULT", config.getAccountUserId());
        assertEquals("build-password", config.getMediaEncryptionPassword());
        String generated = Files.readString(file.toPath());
        assertFalse(config.isVideoEncrypted());
        assertFalse(generated.contains("file.encrypt_password"));
        assertFalse(generated.contains("file.encryption"));
    }

    @Test public void usesSerialFallbackWhenUnset() throws Exception {
        File file = File.createTempFile("configs", ".cson");
        Files.writeString(file.toPath(), "[device]\nserial_number=\"\"");

        DcamConfig config = new CsonConfigStore(file, "KF5OF2126040802193").load();

        assertEquals("KF5OF2126040802193", config.getAccountUserId());
        assertFalse(Files.readString(file.toPath()).contains("account.user_id"));
    }
}
