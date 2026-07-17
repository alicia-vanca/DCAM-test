package com.dvid.dcam.platform.storage;

import com.dvid.dcam.platform.logging.DcamLogger;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class DcamMd5RetryProcessor {
    private DcamMd5RetryProcessor() {}

    static boolean process(DcamMd5RetryQueue queue) throws IOException {
        boolean complete = true;
        for (File file : queue.pending()) {
            try {
                if (!file.isFile()) {
                    File parent = file.getParentFile();
                    if (parent != null && parent.isDirectory()) queue.remove(file);
                    else complete = false;
                }
                else {
                    DcamMd5Sidecar.write(file, digest(file));
                    queue.remove(file);
                }
            } catch (Exception failure) {
                complete = false;
                try {
                    DcamLogger.e("Video MD5 retry failed: " + file.getAbsolutePath(), failure);
                } catch (RuntimeException ignored) { }
            }
        }
        return complete;
    }

    static String digest(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream input = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            StringBuilder result = new StringBuilder(32);
            for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("MD5 unavailable", impossible);
        }
    }
}
