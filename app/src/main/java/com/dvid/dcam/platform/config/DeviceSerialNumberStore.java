package com.dvid.dcam.platform.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public final class DeviceSerialNumberStore {
    private static final String KEY = "serial_number";
    private final File target;
    public DeviceSerialNumberStore(File target) { this.target = target; }
    public String load() {
        return value(read(target), KEY);
    }
    public void save(String serial) throws IOException {
        if (!isValid(serial)) throw new IllegalArgumentException("serial number must be 6-10 characters");
        write(serial.trim());
    }
    public void saveDefault(String serial) throws IOException { write(serial == null ? "" : serial.trim()); }
    private void write(String serial) throws IOException {
        String text = read(target);
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        String updated = replace(text, KEY, serial);
        File partial = new File(parent, "." + target.getName() + "." + UUID.randomUUID());
        try (FileOutputStream output = new FileOutputStream(partial)) {
            output.write(updated.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            try {
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try (FileChannel directory = FileChannel.open(parent.toPath())) {
                directory.force(true);
            } catch (IOException ignored) { }
        } finally {
            Files.deleteIfExists(partial.toPath());
        }
    }
    public static boolean isValid(String serial) { return serial != null && serial.trim().matches("[A-Za-z0-9]{6,10}"); }
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
    private static String value(String text, String key) {
        String prefix = key + "=";
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (!value.startsWith(prefix)) continue;
            value = value.substring(prefix.length()).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            return value.trim();
        }
        return "";
    }
    private static String replace(String text, String key, String serial) {
        String prefix = key + "=";
        String replacement = prefix + "\"" + serial + "\"";
        String[] lines = text.split("\\R", -1);
        StringBuilder output = new StringBuilder(text.length() + replacement.length() + 1);
        boolean replaced = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (!replaced && line.trim().startsWith(prefix)) { output.append(line, 0, line.indexOf(line.trim())).append(replacement); replaced = true; } else output.append(line);
            if (index < lines.length - 1) output.append('\n');
        }
        if (!replaced) { if (!text.isEmpty() && !text.endsWith("\n")) output.append('\n'); output.append(replacement); }
        return output.toString();
    }
}
