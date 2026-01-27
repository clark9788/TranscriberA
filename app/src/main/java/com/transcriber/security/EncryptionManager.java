package com.transcriber.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import com.transcriber.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM encryption using Android Keystore for PHI protection.
 */
public class EncryptionManager {

    private static final String TAG = "EncryptionManager";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private EncryptionManager() {
        // Utility class - prevent instantiation
    }

    /**
     * Generate or retrieve the AES encryption key from Android Keystore.
     */
    public static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);

        if (keyStore.containsAlias(Config.KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(Config.KEY_ALIAS, null);
        }

        KeyGenerator keyGen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                Config.KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build();

        keyGen.init(keySpec);
        return keyGen.generateKey();
    }

    /**
     * Encrypt a string and return encrypted bytes.
     */
    public static byte[] encryptString(String plaintext) throws Exception {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }
        return encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encrypt raw bytes and return encrypted bytes.
     */
    public static byte[] encryptBytes(byte[] plaintext) throws Exception {
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext cannot be null");
        }

        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] result = new byte[1 + iv.length + ciphertext.length];
        result[0] = (byte) iv.length;
        System.arraycopy(iv, 0, result, 1, iv.length);
        System.arraycopy(ciphertext, 0, result, 1 + iv.length, ciphertext.length);

        return result;
    }

    /**
     * Decrypt encrypted bytes to a string.
     */
    public static String decryptString(byte[] encryptedData) throws Exception {
        byte[] plaintext = decryptBytes(encryptedData);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * Decrypt encrypted bytes to raw bytes.
     */
    public static byte[] decryptBytes(byte[] encryptedData) throws Exception {
        if (encryptedData == null || encryptedData.length < 2) {
            throw new IllegalArgumentException("Invalid encrypted data");
        }

        int ivLength = encryptedData[0] & 0xFF;
        if (encryptedData.length < 1 + ivLength) {
            throw new IllegalArgumentException("Encrypted data too short");
        }

        byte[] iv = new byte[ivLength];
        System.arraycopy(encryptedData, 1, iv, 0, ivLength);

        byte[] ciphertext = new byte[encryptedData.length - 1 - ivLength];
        System.arraycopy(encryptedData, 1 + ivLength, ciphertext, 0, ciphertext.length);

        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        return cipher.doFinal(ciphertext);
    }

    /**
     * Encrypt a file using AES-256-GCM (for text files).
     */
    public static void encryptFile(File plaintext, File encrypted) throws Exception {
        if (plaintext == null || !plaintext.exists()) {
            throw new IOException("Plaintext file does not exist: " + plaintext);
        }

        byte[] fileData = readFile(plaintext);
        String content = new String(fileData, StandardCharsets.UTF_8);
        byte[] encryptedData = encryptString(content);

        writeFile(encrypted, encryptedData);
        Log.i(TAG, "Encrypted file: " + encrypted.getAbsolutePath());
    }

    /**
     * Encrypt a binary file using AES-256-GCM (for audio files).
     */
    public static void encryptBinaryFile(File plaintext, File encrypted) throws Exception {
        if (plaintext == null || !plaintext.exists()) {
            throw new IOException("Plaintext file does not exist: " + plaintext);
        }

        byte[] fileData = readFile(plaintext);
        byte[] encryptedData = encryptBytes(fileData);

        writeFile(encrypted, encryptedData);
        Log.i(TAG, "Encrypted binary file: " + encrypted.getAbsolutePath());
    }

    /**
     * Decrypt a file to memory and return plaintext string.
     */
    public static String decryptFile(File encrypted) throws Exception {
        if (encrypted == null || !encrypted.exists()) {
            throw new IOException("Encrypted file does not exist: " + encrypted);
        }

        byte[] encryptedData = readFile(encrypted);
        String plaintext = decryptString(encryptedData);
        Log.i(TAG, "Decrypted file: " + encrypted.getAbsolutePath());
        return plaintext;
    }

    /**
     * Decrypt a file and write plaintext to another file.
     */
    public static void decryptFile(File encrypted, File plaintext) throws Exception {
        String content = decryptFile(encrypted);
        try (FileOutputStream fos = new FileOutputStream(plaintext)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Decrypt a binary file and write plaintext to another file.
     */
    public static void decryptBinaryFile(File encrypted, File plaintext) throws Exception {
        if (encrypted == null || !encrypted.exists()) {
            throw new IOException("Encrypted file does not exist: " + encrypted);
        }

        byte[] encryptedData = readFile(encrypted);
        byte[] plaintextData = decryptBytes(encryptedData);

        try (FileOutputStream fos = new FileOutputStream(plaintext)) {
            fos.write(plaintextData);
        }
        Log.i(TAG, "Decrypted binary file: " + plaintext.getAbsolutePath());
    }

    /**
     * Read entire file into byte array.
     */
    private static byte[] readFile(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead = fis.read(data);
            if (bytesRead != data.length) {
                throw new IOException("Failed to read entire file");
            }
        }
        return data;
    }

    /**
     * Write byte array to file.
     */
    private static void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }
}
