package com.dvid.dcam.platform.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BodycamMediaCryptoTest {
    @TempDir Path tempDir;

    @Test void encryptsAndDecryptsFileWithBodycamPasswordContract() throws Exception {
        byte[] clearBytes = "Bodycam media encryption sample\n".getBytes(StandardCharsets.UTF_8);
        File clear = tempDir.resolve("clear.bin").toFile();
        File encrypted = tempDir.resolve("clear_enc.bin").toFile();
        File decrypted = tempDir.resolve("clear_dec.bin").toFile();
        Files.write(clear.toPath(), clearBytes);

        BodycamMediaCrypto.encryptFile(clear, encrypted, "123456");

        byte[] encryptedBytes = Files.readAllBytes(encrypted.toPath());
        assertFalse(Arrays.equals(clearBytes, encryptedBytes));

        BodycamMediaCrypto.decryptFile(encrypted, decrypted, "123456");

        assertArrayEquals(clearBytes, Files.readAllBytes(decrypted.toPath()));
    }

    @Test void inPlaceEncryptionCanBeReversed() throws Exception {
        byte[] clearBytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};
        File media = tempDir.resolve("media.mp4").toFile();
        Files.write(media.toPath(), clearBytes);

        BodycamMediaCrypto.encryptFileInPlace(media, "123456");
        assertFalse(Arrays.equals(clearBytes, Files.readAllBytes(media.toPath())));

        BodycamMediaCrypto.decryptFileInPlace(media, "123456");
        assertArrayEquals(clearBytes, Files.readAllBytes(media.toPath()));
    }

    @Test void rejectsMissingPassword() throws Exception {
        File clear = tempDir.resolve("clear.bin").toFile();
        File encrypted = tempDir.resolve("clear_enc.bin").toFile();
        Files.write(clear.toPath(), new byte[] {1, 2, 3});

        assertThrows(java.io.IOException.class,
                () -> BodycamMediaCrypto.encryptFile(clear, encrypted, ""));
        assertThrows(java.io.IOException.class,
                () -> BodycamMediaCrypto.encryptFile(clear, encrypted, null));
    }
}
