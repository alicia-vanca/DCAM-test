package com.dvid.dcam.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.dvid.dcam.feature.device.application.usecase.DeviceSerialNumberUseCase;
import com.dvid.dcam.platform.database.dao.CloudStateDao;
import com.dvid.dcam.platform.database.entities.DeviceIdentityEntity;
import com.dvid.dcam.platform.database.entities.OperationalSettingEntity;
import com.dvid.dcam.platform.database.entities.RemoteConfigEntity;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

public class FileDeviceSerialNumberStoreTest {
    @Test public void loadsSavedSerialNumber() throws Exception {
        File dir = Files.createTempDirectory("serial").toFile();
        File target = new File(dir, "Config/dcam_config.cson");
        Files.createDirectories(target.getParentFile().toPath());
        Files.writeString(target.toPath(), "[device]\nserial_number=\"ABC123\"");
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(target);
        assertEquals("ABC123", store.load());
    }

    @Test public void missingConfigDoesNotPersistFallbackSerial() throws Exception {
        File dir = Files.createTempDirectory("serial-missing").toFile();
        File target = new File(dir, "Config/dcam_config.cson");

        assertEquals("", new FileDeviceSerialNumberStore(target).load());
        assertFalse(target.exists());
    }

    @Test public void rejectsRestoredSerialOutsideConfiguredLengthLimit() throws Exception {
        File dir = Files.createTempDirectory("serial-restored").toFile();
        File target = new File(dir, "dcam_config.cson");
        Files.writeString(target.toPath(), "serial_number=\"36NCC090114\"");
        assertEquals("", new FileDeviceSerialNumberStore(target).load());
        assertFalse(serialNumbers().isValid("36NCC090114"));
        assertFalse(serialNumbers().isConfigured("36NCC090114"));
    }

    @Test public void saveSynchronizesDisplayedAccountId() throws Exception {
        File dir = Files.createTempDirectory("serial-sync").toFile();
        File target = new File(dir, "dcam_config.cson");
        Files.writeString(target.toPath(), "serial_number=\"OLD123\"\n");
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(target);

        store.save("USER123");

        String text = Files.readString(target.toPath());
        assertTrue(text.contains("serial_number=\"USER123\""));
        assertFalse(text.contains("account.user_id"));
    }

    @Test public void saveReplacesConfigWithoutLeavingPartialFile() throws Exception {
        File dir = Files.createTempDirectory("serial-atomic").toFile();
        File target = new File(dir, "dcam_config.cson");
        Files.writeString(target.toPath(), "serial_number=\"OLD123\"\nother=\"keep\"\n");

        new FileDeviceSerialNumberStore(target).save("NEW123");

        assertEquals("serial_number=\"NEW123\"\nother=\"keep\"\n",
                Files.readString(target.toPath()));
        try (var files = Files.list(dir.toPath())) {
            assertEquals(1, files.count());
        }
    }

    @Test public void saveMirrorsSerialToDatabaseCsonAndSdBackup() throws Exception {
        File dir = Files.createTempDirectory("serial-mirrors").toFile();
        File target = new File(dir, "internal/Config/dcam_config.cson");
        File backup = new File(dir, "sd/DCAM_FACTORY/device_identity.json");
        FakeCloudStateDao dao = new FakeCloudStateDao();
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", () -> List.of(backup));

        new DeviceSerialNumberUseCase(store).save("USER123");

        assertEquals("HW123", dao.identity.hardwareId);
        assertEquals("USER123", dao.identity.serialNumber);
        assertEquals("USER123", store.load());
        assertTrue(Files.readString(backup.toPath()).contains("\"serial_number\": \"USER123\""));
    }

    @Test public void restoresMissingLocalIdentityFromSdBackup() throws Exception {
        File dir = Files.createTempDirectory("serial-restore").toFile();
        File target = new File(dir, "internal/Config/dcam_config.cson");
        File backup = new File(dir, "sd/DCAM_FACTORY/device_identity.json");
        Files.createDirectories(backup.getParentFile().toPath());
        Files.writeString(backup.toPath(), backupJson("SD1234"));
        FakeCloudStateDao dao = new FakeCloudStateDao();
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", () -> List.of(backup));

        assertTrue(store.restoreIfAvailable());

        assertEquals("SD1234", dao.identity.serialNumber);
        assertEquals("SD1234", store.load());
    }

    @Test public void restoreUsesValidSdWhenDatabaseAndCsonSerialsAreOutOfRange() throws Exception {
        File dir = Files.createTempDirectory("serial-invalid-local").toFile();
        File target = new File(dir, "internal/Config/dcam_config.cson");
        File backup = new File(dir, "sd/DCAM_FACTORY/device_identity.json");
        Files.createDirectories(target.getParentFile().toPath());
        Files.createDirectories(backup.getParentFile().toPath());
        Files.writeString(target.toPath(), "serial_number=\"ABCDEFGHIJK\"\n");
        Files.writeString(backup.toPath(), backupJson("SD1234"));
        FakeCloudStateDao dao = new FakeCloudStateDao();
        dao.identity = identity("ABCDEFGHIJK");
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", () -> List.of(backup));

        assertTrue(store.restoreIfAvailable());
        assertEquals("SD1234", dao.identity.serialNumber);
        assertEquals("SD1234", store.load());
    }

    @Test public void doesNotRestoreSerialFromLegacyAccountConfig() throws Exception {
        File dir = Files.createTempDirectory("serial-no-legacy-account").toFile();
        File target = new File(dir, "internal/Config/dcam_config.cson");
        File legacy = new File(dir, "internal/DCIM/configs.cson");
        Files.createDirectories(legacy.getParentFile().toPath());
        Files.writeString(legacy.toPath(), "account.user_id=\"36NCC090114\"\n");
        FakeCloudStateDao dao = new FakeCloudStateDao();
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", List::of);

        assertFalse(store.restoreIfAvailable());

        assertNull(dao.identity);
        assertFalse(target.exists());
    }

    @Test public void databaseIdentityReplacesStaleCsonAndSdBackup() throws Exception {
        File dir = Files.createTempDirectory("serial-database-wins").toFile();
        File target = new File(dir, "internal/Config/dcam_config.cson");
        File backup = new File(dir, "sd/DCAM_FACTORY/device_identity.json");
        Files.createDirectories(target.getParentFile().toPath());
        Files.writeString(target.toPath(), "serial_number=\"OLD123\"\n");
        Files.createDirectories(backup.getParentFile().toPath());
        Files.writeString(backup.toPath(), backupJson("SD1234"));
        FakeCloudStateDao dao = new FakeCloudStateDao();
        dao.identity = identity("DB1234");
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", () -> List.of(backup));

        assertTrue(store.restoreIfAvailable());

        assertEquals("DB1234", store.load());
        assertTrue(Files.readString(backup.toPath()).contains("\"serial_number\": \"DB1234\""));
    }

    @Test public void firstValidSdBackupWinsAndSynchronizesEveryCopy() throws Exception {
        File dir = Files.createTempDirectory("serial-sd-precedence").toFile();
        File invalid = new File(dir, "sd-one/DCAM_FACTORY/device_identity.json");
        File firstValid = new File(dir, "sd-two/DCAM_FACTORY/device_identity.json");
        File laterValid = new File(dir, "sd-three/DCAM_FACTORY/device_identity.json");
        Files.createDirectories(invalid.getParentFile().toPath());
        Files.createDirectories(firstValid.getParentFile().toPath());
        Files.createDirectories(laterValid.getParentFile().toPath());
        Files.writeString(invalid.toPath(), backupJson("ABCDE"));
        Files.writeString(firstValid.toPath(), backupJson("SD1234"));
        Files.writeString(laterValid.toPath(), backupJson("SD5678"));
        FakeCloudStateDao dao = new FakeCloudStateDao();
        File target = new File(dir, "internal/Config/dcam_config.cson");
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", () -> List.of(invalid, firstValid, laterValid));

        assertTrue(store.restoreIfAvailable());

        assertEquals("SD1234", dao.identity.serialNumber);
        assertEquals("SD1234", store.load());
        for (File backup : List.of(invalid, firstValid, laterValid)) {
            assertTrue(Files.readString(backup.toPath()).contains(
                    "\"serial_number\": \"SD1234\""));
        }
    }

    @Test public void csonFailureRollsDatabaseBack() throws Exception {
        File dir = Files.createTempDirectory("serial-cson-failure").toFile();
        File blockedParent = new File(dir, "blocked");
        Files.writeString(blockedParent.toPath(), "not a directory");
        FakeCloudStateDao dao = new FakeCloudStateDao();
        dao.identity = identity("OLD123");
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                new File(blockedParent, "dcam_config.cson"), dao, "HW123", List::of);

        assertThrows(IOException.class, () -> store.save("USER123"));

        assertEquals("OLD123", dao.identity.serialNumber);
    }

    @Test public void sdFailureRollsDatabaseAndCsonBack() throws Exception {
        File dir = Files.createTempDirectory("serial-sd-failure").toFile();
        File target = new File(dir, "internal/Config/dcam_config.cson");
        Files.createDirectories(target.getParentFile().toPath());
        Files.writeString(target.toPath(), "serial_number=\"OLD123\"\n");
        File blockedParent = new File(dir, "blocked");
        Files.writeString(blockedParent.toPath(), "not a directory");
        File backup = new File(blockedParent, "device_identity.json");
        FakeCloudStateDao dao = new FakeCloudStateDao();
        dao.identity = identity("OLD123");
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", () -> List.of(backup));

        assertThrows(IOException.class, () -> store.save("USER123"));

        assertEquals("OLD123", dao.identity.serialNumber);
        assertEquals("serial_number=\"OLD123\"\n", Files.readString(target.toPath()));
        assertFalse(backup.exists());
    }

    @Test public void rejectsMalformedDuplicateAndUnsafeBackupJson() throws Exception {
        File dir = Files.createTempDirectory("serial-invalid-json").toFile();
        File malformed = new File(dir, "one/device_identity.json");
        File duplicate = new File(dir, "two/device_identity.json");
        File unsafe = new File(dir, "three/device_identity.json");
        Files.createDirectories(malformed.getParentFile().toPath());
        Files.createDirectories(duplicate.getParentFile().toPath());
        Files.createDirectories(unsafe.getParentFile().toPath());
        Files.writeString(malformed.toPath(), "prefix " + backupJson("SD1234"));
        Files.writeString(duplicate.toPath(), "{\"schema_version\":1,"
                + "\"identity_type\":\"BODYCAMERA_SD_FACTORY_IDENTITY\","
                + "\"serial_number\":\"SD1234\",\"serial_number\":\"SD5678\"}");
        Files.writeString(unsafe.toPath(), backupJson("BAD-123"));
        FakeCloudStateDao dao = new FakeCloudStateDao();
        File target = new File(dir, "internal/Config/dcam_config.cson");
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", () -> List.of(malformed, duplicate, unsafe));

        assertFalse(store.restoreIfAvailable());
        assertNull(dao.identity);
        assertFalse(target.exists());
    }

    @Test public void serialMutationsAreSynchronizedAtStoreBoundary() throws Exception {
        assertTrue(Modifier.isSynchronized(
                FileDeviceSerialNumberStore.class.getMethod("save", String.class).getModifiers()));
        assertTrue(Modifier.isSynchronized(FileDeviceSerialNumberStore.class
                .getMethod("restoreIfAvailable").getModifiers()));
    }
    private static String backupJson(String serial) {
        return "{\n"
                + "  \"schema_version\": 1,\n"
                + "  \"identity_type\": \"BODYCAMERA_SD_FACTORY_IDENTITY\",\n"
                + "  \"serial_number\": \"" + serial + "\"\n"
                + "}\n";
    }

    private static DeviceIdentityEntity identity(String serial) {
        return new DeviceIdentityEntity(
                1, null, "HW123", serial, "PROVISIONING_REQUIRED", null, 1L);
    }

    private static final class FakeCloudStateDao implements CloudStateDao {
        private DeviceIdentityEntity identity;

        @Override public DeviceIdentityEntity deviceIdentity() { return identity; }
        @Override public void saveDeviceIdentity(DeviceIdentityEntity identity) { this.identity = identity; }
        @Override public void deleteDeviceIdentity() { identity = null; }
        @Override public RemoteConfigEntity remoteConfig() { return null; }
        @Override public void saveRemoteConfig(RemoteConfigEntity config) {}
        @Override public String operationalSetting(String key) { return null; }
        @Override public void saveOperationalSetting(OperationalSettingEntity setting) {}
    }

    @Test public void ignoresSdBackupsOutsideConfiguredLengthLimit() throws Exception {
        File dir = Files.createTempDirectory("serial-invalid-length-backup").toFile();
        File shortBackup = new File(dir, "short/DCAM_FACTORY/device_identity.json");
        File longBackup = new File(dir, "long/DCAM_FACTORY/device_identity.json");
        Files.createDirectories(shortBackup.getParentFile().toPath());
        Files.createDirectories(longBackup.getParentFile().toPath());
        Files.writeString(shortBackup.toPath(), backupJson("ABCDE"));
        Files.writeString(longBackup.toPath(), backupJson("ABCDEFGHIJK"));
        FakeCloudStateDao dao = new FakeCloudStateDao();
        File target = new File(dir, "internal/Config/dcam_config.cson");
        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", () -> List.of(shortBackup, longBackup));

        assertFalse(store.restoreIfAvailable());
        assertNull(dao.identity);
        assertFalse(target.exists());
    }

    @Test public void ignoresInvalidSdBackup() throws Exception {
        File dir = Files.createTempDirectory("serial-invalid-backup").toFile();
        File backup = new File(dir, "sd/DCAM_FACTORY/device_identity.json");
        Files.createDirectories(backup.getParentFile().toPath());
        Files.writeString(backup.toPath(), "{\"schema_version\": 1, \"serial_number\": \"BAD123\"}");
        FakeCloudStateDao dao = new FakeCloudStateDao();
        File target = new File(dir, "internal/Config/dcam_config.cson");

        FileDeviceSerialNumberStore store = new FileDeviceSerialNumberStore(
                target, dao, "HW123", () -> List.of(backup));

        assertFalse(store.restoreIfAvailable());
        assertNull(dao.identity);
        assertFalse(target.exists());
    }

    private static DeviceSerialNumberUseCase serialNumbers() {
        return new DeviceSerialNumberUseCase(new FileDeviceSerialNumberStore(new File("unused")));
    }

    @Test public void validatesSixToTenUppercaseAsciiAlphaNumericCharacters() {
        assertTrue(serialNumbers().isValid("ABC123"));
        assertFalse(serialNumbers().isValid("abc123"));
        assertFalse(serialNumbers().isValid("ABCDE"));
        assertFalse(serialNumbers().isValid("ABCDEFGHIJK"));
        assertFalse(serialNumbers().isValid("ABC-123"));
    }
}
