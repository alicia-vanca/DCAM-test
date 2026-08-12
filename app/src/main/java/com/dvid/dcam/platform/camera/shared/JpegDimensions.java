package com.dvid.dcam.platform.camera.shared;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

public final class JpegDimensions {
    public record Size(int width, int height) {}

    private JpegDimensions() {}

    public static Optional<Size> read(File file) {
        if (file == null || !file.isFile()) return Optional.empty();
        try (FileInputStream input = new FileInputStream(file)) {
            if (readByte(input) != 0xff || readByte(input) != 0xd8) return Optional.empty();
            while (true) {
                int prefix;
                do { prefix = readByte(input); } while (prefix != 0xff);
                int marker;
                do { marker = readByte(input); } while (marker == 0xff);
                if (marker == 0xd9 || marker == 0xda) return Optional.empty();
                int length = (readByte(input) << 8) | readByte(input);
                if (length < 2) return Optional.empty();
                if (isStartOfFrame(marker)) {
                    if (length < 7) return Optional.empty();
                    readByte(input);
                    int height = (readByte(input) << 8) | readByte(input);
                    int width = (readByte(input) << 8) | readByte(input);
                    return width > 0 && height > 0
                            ? Optional.of(new Size(width, height)) : Optional.empty();
                }
                long remaining = length - 2L;
                while (remaining > 0L) {
                    long skipped = input.skip(remaining);
                    if (skipped <= 0L) {
                        readByte(input);
                        skipped = 1L;
                    }
                    remaining -= skipped;
                }
            }
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xc0 && marker <= 0xcf
                && marker != 0xc4 && marker != 0xc8 && marker != 0xcc;
    }

    private static int readByte(FileInputStream input) throws IOException {
        int value = input.read();
        if (value < 0) throw new EOFException();
        return value;
    }
}
