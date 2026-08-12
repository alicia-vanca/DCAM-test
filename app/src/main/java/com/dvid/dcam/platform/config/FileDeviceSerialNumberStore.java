package com.dvid.dcam.platform.config;

import com.dvid.dcam.feature.cloud.domain.ProvisioningState;
import com.dvid.dcam.feature.device.application.port.DeviceSerialNumberStore;
import com.dvid.dcam.platform.database.dao.CloudStateDao;
import com.dvid.dcam.platform.database.entities.DeviceIdentityEntity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class FileDeviceSerialNumberStore implements DeviceSerialNumberStore {
    private static final int IDENTITY_ROW_ID = 1;
    private static final String KEY = "serial_number";
    private static final String IDENTITY_TYPE = "BODYCAMERA_SD_FACTORY_IDENTITY";
    private final File target;
    private final CloudStateDao identityDao;
    private final String hardwareId;
    private final Supplier<List<File>> identityBackupFiles;

    public FileDeviceSerialNumberStore(File target) {
        this(target, null, "", () -> List.of());
    }

    public FileDeviceSerialNumberStore(
            File target,
            CloudStateDao identityDao,
            String hardwareId,
            Supplier<List<File>> identityBackupFiles) {
        if (target == null) throw new IllegalArgumentException("target is required");
        this.target = target;
        this.identityDao = identityDao;
        this.hardwareId = hardwareId == null || hardwareId.isBlank() ? "unknown" : hardwareId.trim();
        this.identityBackupFiles = identityBackupFiles == null ? () -> List.of() : identityBackupFiles;
    }

    @Override public String load() {
        return normalizeSerial(value(read(target), KEY));
    }

    @Override public synchronized void save(String serial) throws IOException {
        persist(serial == null ? "" : serial.trim());
    }


    @Override public synchronized boolean restoreIfAvailable() throws IOException {
        String csonSerial = load();
        String databaseSerial = databaseSerial();
        if (!databaseSerial.isEmpty()) {
            if (!databaseSerial.equals(csonSerial)) {
                persist(databaseSerial);
                return true;
            }
            refreshBackupCopies(databaseSerial);
            return false;
        }
        if (!csonSerial.isEmpty()) {
            persist(csonSerial);
            return false;
        }
        String backupSerial = loadBackupSerial();
        if (backupSerial.isEmpty()) return false;
        persist(backupSerial);
        return true;
    }

    private void persist(String serial) throws IOException {
        DeviceIdentityEntity previousDatabase = snapshot(
                identityDao == null ? null : identityDao.deviceIdentity());
        FileState previousCson = FileState.capture(target);
        List<File> backupFiles = backupFiles();
        List<FileState> previousBackups = captureFiles(backupFiles);
        try {
            saveDatabase(serial);
            write(serial);
            writeBackupCopies(serial, backupFiles);
        } catch (IOException | RuntimeException error) {
            restoreFiles(previousBackups, error);
            restoreFile(previousCson, error);
            try {
                restoreDatabase(previousDatabase);
            } catch (RuntimeException rollbackError) {
                error.addSuppressed(rollbackError);
            }
            throw error;
        }
    }

    private void refreshBackupCopies(String serial) throws IOException {
        List<File> files = backupFiles();
        List<FileState> previous = captureFiles(files);
        try {
            writeBackupCopies(serial, files);
        } catch (IOException | RuntimeException error) {
            restoreFiles(previous, error);
            throw error;
        }
    }

    private List<File> backupFiles() {
        List<File> supplied = identityBackupFiles.get();
        if (supplied == null || supplied.isEmpty()) return List.of();
        List<File> files = new ArrayList<>();
        for (File file : supplied) {
            if (file != null && !files.contains(file)) files.add(file);
        }
        return List.copyOf(files);
    }

    private static List<FileState> captureFiles(List<File> files) throws IOException {
        List<FileState> states = new ArrayList<>();
        for (File file : files) states.add(FileState.capture(file));
        return List.copyOf(states);
    }

    private static void restoreFiles(List<FileState> states, Throwable error) {
        for (FileState state : states) restoreFile(state, error);
    }

    private static void restoreFile(FileState state, Throwable error) {
        try {
            state.restore();
        } catch (IOException rollbackError) {
            error.addSuppressed(rollbackError);
        }
    }

    private String databaseSerial() {
        if (identityDao == null) return "";
        DeviceIdentityEntity identity = identityDao.deviceIdentity();
        return identity == null ? "" : normalizeSerial(identity.serialNumber);
    }

    private void saveDatabase(String serial) {
        if (identityDao == null) return;
        DeviceIdentityEntity identity = identityDao.deviceIdentity();
        if (identity == null) {
            identity = new DeviceIdentityEntity(
                    IDENTITY_ROW_ID,
                    null,
                    hardwareId,
                    serial,
                    ProvisioningState.PROVISIONING_REQUIRED.name(),
                    null,
                    System.currentTimeMillis());
        } else {
            identity.hardwareId = identity.hardwareId == null || identity.hardwareId.isBlank()
                    ? hardwareId : identity.hardwareId;
            identity.serialNumber = serial;
            if (identity.provisioningState == null || identity.provisioningState.isBlank()) {
                identity.provisioningState = ProvisioningState.PROVISIONING_REQUIRED.name();
            }
            identity.updatedAt = System.currentTimeMillis();
        }
        identityDao.saveDeviceIdentity(identity);
    }

    private void restoreDatabase(DeviceIdentityEntity previous) {
        if (identityDao == null) return;
        if (previous == null) identityDao.deleteDeviceIdentity();
        else identityDao.saveDeviceIdentity(previous);
    }

    private void writeBackupCopies(String serial, List<File> files) throws IOException {
        if (serial == null || serial.isBlank()) return;
        String json = backupJson(serial);
        for (File file : files) {
            if (!serial.equals(parseBackupSerial(read(file)))) {
                writeUtf8Atomically(file, json);
            }
        }
    }

    private String loadBackupSerial() {
        for (File file : backupFiles()) {
            String serial = parseBackupSerial(read(file));
            if (!serial.isEmpty()) return serial;
        }
        return "";
    }
    private static String parseBackupSerial(String text) {
        try {
            return new BackupJsonReader(text).readSerial();
        } catch (IllegalArgumentException error) {
            return "";
        }
    }
    private static String backupJson(String serial) {
        String now = escapeJson(Instant.now().toString());
        return "{\n"
                + "  \"schema_version\": 1,\n"
                + "  \"identity_type\": \"" + IDENTITY_TYPE + "\",\n"
                + "  \"serial_number\": \"" + escapeJson(serial) + "\",\n"
                + "  \"created_by\": \"DCAM\",\n"
                + "  \"created_at\": \"" + now + "\",\n"
                + "  \"updated_at\": \"" + now + "\"\n"
                + "}\n";
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static DeviceIdentityEntity snapshot(DeviceIdentityEntity source) {
        if (source == null) return null;
        return new DeviceIdentityEntity(
                source.id,
                source.dcamCloudDeviceId,
                source.hardwareId,
                source.serialNumber,
                source.provisioningState,
                source.firebaseInstallationId,
                source.updatedAt);
    }

    private static void writeUtf8Atomically(File file, String text) throws IOException {
        writeAtomically(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeAtomically(File file, byte[] bytes) throws IOException {
        File parent = file.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("identity backup directory unavailable");
        }
        File partial = new File(parent, "." + file.getName() + "." + UUID.randomUUID());
        try (FileOutputStream output = new FileOutputStream(partial)) {
            output.write(bytes);
            output.getFD().sync();
        }
        try {
            try {
                Files.move(partial.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try (FileChannel directory = FileChannel.open(parent.toPath())) {
                directory.force(true);
            } catch (IOException ignored) { }
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }
    private void write(String serial) throws IOException {
        String text = read(target);
        String updated = replace(text, KEY, serial);
        writeUtf8Atomically(target, updated);
    }

    private static String read(File file) {
        try {
            if (file == null || !file.isFile()) return "";
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                int offset = 0;
                while (offset < bytes.length) {
                    int count = input.read(bytes, offset, bytes.length - offset);
                    if (count < 0) break;
                    offset += count;
                }
                return new String(bytes, 0, offset, StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException ignored) { return ""; }
    }

    private static String normalizeSerial(String serial) {
        if (serial == null) return "";
        String trimmed = serial.trim();
        return trimmed.matches("[A-Z0-9]{6,10}") ? trimmed : "";
    }

    private static String value(String text, String key) {
        String prefix = key + "=";
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (!value.startsWith(prefix)) continue;
            value = value.substring(prefix.length()).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return value.trim();
        }
        return "";
    }

    private static String replace(String text, String key, String serial) {
        String prefix = key + "=";
        String replacement = prefix + "\"" + escapeCson(serial) + "\"";
        String[] lines = text.split("\\R", -1);
        StringBuilder output = new StringBuilder(text.length() + replacement.length() + 1);
        boolean replaced = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (!replaced && trimmed.startsWith(prefix)) {
                output.append(line, 0, line.indexOf(trimmed)).append(replacement);
                replaced = true;
            } else {
                output.append(line);
            }
            if (index < lines.length - 1) output.append('\n');
        }
        if (!replaced) {
            if (!text.isEmpty() && !text.endsWith("\n")) output.append('\n');
            output.append(replacement);
        }
        return output.toString();
    }

    private static final class BackupJsonReader {
        private final String text;
        private final Set<String> keys = new HashSet<>();
        private int index;
        private Integer schemaVersion;
        private String identityType;
        private String serialNumber;

        private BackupJsonReader(String text) {
            this.text = text == null ? "" : text;
        }

        private String readSerial() {
            skipWhitespace();
            expect('{' );
            skipWhitespace();
            if (!consume('}')) {
                do {
                    String key = readString();
                    if (!keys.add(key)) throw invalid();
                    skipWhitespace();
                    expect(':' );
                    skipWhitespace();
                    readField(key);
                    skipWhitespace();
                } while (consume(','));
                expect('}' );
            }
            skipWhitespace();
            if (index != text.length()
                    || schemaVersion == null || schemaVersion != 1
                    || !IDENTITY_TYPE.equals(identityType)
                    || serialNumber == null) {
                throw invalid();
            }
            String serial = normalizeSerial(serialNumber);
            if (serial.isEmpty()) throw invalid();
            return serial;
        }

        private void readField(String key) {
            switch (key) {
                case "schema_version" -> schemaVersion = readInteger();
                case "identity_type" -> identityType = readString();
                case "serial_number" -> serialNumber = readString();
                default -> skipSimpleValue();
            }
        }

        private int readInteger() {
            int start = index;
            if (consume('-')) {
                if (index >= text.length()) throw invalid();
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
            if (index == start || index == start + 1 && text.charAt(start) == '-') throw invalid();
            try {
                return Integer.parseInt(text.substring(start, index));
            } catch (NumberFormatException error) {
                throw invalid();
            }
        }

        private void skipSimpleValue() {
            if (index >= text.length()) throw invalid();
            char next = text.charAt(index);
            if (next == '"') {
                readString();
                return;
            }
            if (next == '-' || Character.isDigit(next)) {
                readInteger();
                return;
            }
            if (consumeLiteral("true") || consumeLiteral("false") || consumeLiteral("null")) return;
            throw invalid();
        }

        private String readString() {
            expect('"' );
            StringBuilder value = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') return value.toString();
                if (current < 0x20) throw invalid();
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                if (index >= text.length()) throw invalid();
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicode());
                    default -> throw invalid();
                }
            }
            throw invalid();
        }

        private char readUnicode() {
            if (index + 4 > text.length()) throw invalid();
            int value = 0;
            for (int offset = 0; offset < 4; offset++) {
                int digit = Character.digit(text.charAt(index++), 16);
                if (digit < 0) throw invalid();
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private boolean consumeLiteral(String value) {
            if (!text.startsWith(value, index)) return false;
            index += value.length();
            return true;
        }

        private boolean consume(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) return false;
            index++;
            return true;
        }

        private void expect(char expected) {
            if (!consume(expected)) throw invalid();
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;
        }

        private static IllegalArgumentException invalid() {
            return new IllegalArgumentException("invalid SD identity backup");
        }
    }
    private static final class FileState {
        private final File file;
        private final boolean existed;
        private final byte[] bytes;

        private FileState(File file, boolean existed, byte[] bytes) {
            this.file = file;
            this.existed = existed;
            this.bytes = bytes;
        }

        private static FileState capture(File file) throws IOException {
            if (!file.exists()) return new FileState(file, false, new byte[0]);
            if (!file.isFile()) throw new IOException("identity path is not a file");
            return new FileState(file, true, Files.readAllBytes(file.toPath()));
        }

        private void restore() throws IOException {
            if (existed) writeAtomically(file, bytes);
            else Files.deleteIfExists(file.toPath());
        }
    }
    private static String escapeCson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
