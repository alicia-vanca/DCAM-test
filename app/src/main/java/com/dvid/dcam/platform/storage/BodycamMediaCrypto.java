package com.dvid.dcam.platform.storage;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Bodycam/BDMA-compatible media encryption: AES-256-CTR with SHA-256 password key. */
public final class BodycamMediaCrypto {
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String KEY_ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CTR/NoPadding";
    private static final int IV_BYTES = 16;
    private static final int BUFFER_BYTES = 8192;

    private BodycamMediaCrypto() {}

    public static void encryptFileInPlace(File file, String password) throws IOException {
        replaceWithTransformedFile(file, password, Cipher.ENCRYPT_MODE);
    }

    public static void decryptFileInPlace(File file, String password) throws IOException {
        replaceWithTransformedFile(file, password, Cipher.DECRYPT_MODE);
    }

    public static void encryptFile(File source, File target, String password) throws IOException {
        transformFile(source, target, password, Cipher.ENCRYPT_MODE);
    }

    public static void decryptFile(File source, File target, String password) throws IOException {
        transformFile(source, target, password, Cipher.DECRYPT_MODE);
    }

    public static void encryptContentUri(ContentResolver resolver, Uri uri, File tempDirectory, String password)
            throws IOException {
        File temp = createTempFile(tempDirectory);
        try {
            try (InputStream input = resolver.openInputStream(uri);
                 OutputStream output = new FileOutputStream(temp)) {
                if (input == null) throw new IOException("Cannot open media for encryption: " + uri);
                transform(input, output, password, Cipher.ENCRYPT_MODE);
            }
            try (InputStream input = new FileInputStream(temp);
                 OutputStream output = resolver.openOutputStream(uri, "rwt")) {
                if (output == null) throw new IOException("Cannot overwrite encrypted media: " + uri);
                copy(input, output);
            }
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void replaceWithTransformedFile(File file, String password, int mode) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Media file is missing");
        File parent = file.getParentFile();
        if (parent == null) throw new IOException("Media file has no parent directory");
        File temp = createTempFile(parent);
        try {
            transformFile(file, temp, password, mode);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void transformFile(File source, File target, String password, int mode) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            transform(input, output, password, mode);
        }
    }

    private static void transform(InputStream input, OutputStream output, String password, int mode)
            throws IOException {
        try (CipherInputStream cipherInput = new CipherInputStream(input, cipher(password, mode))) {
            copy(cipherInput, output);
        }
    }

    private static Cipher cipher(String password, int mode) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] keyBytes = digest.digest(requirePassword(password).getBytes(StandardCharsets.UTF_8));
            SecretKeySpec key = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, key, new IvParameterSpec(new byte[IV_BYTES]));
            return cipher;
        } catch (GeneralSecurityException error) {
            throw new IOException("Cannot initialize media encryption", error);
        }
    }

    private static String requirePassword(String password) throws IOException {
        if (password == null || password.isBlank()) {
            throw new IOException("Media encryption password is required");
        }
        return password;
    }

    private static File createTempFile(File directory) throws IOException {
        if (directory != null) directory.mkdirs();
        return File.createTempFile("dcam-media-", ".tmp", directory);
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) file.delete();
    }
}
