package com.transcriber.file;

import com.transcriber.audit.AuditLogger;
import com.transcriber.config.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Transcription file management and secure deletion helpers for Android.
 */
public class FileManager {

    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^A-Za-z0-9_-]+");
    private static final SimpleDateFormat TIMESTAMP_FORMATTER = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Sanitize a component (patient name or DOB) for use in filenames.
     */
    public static String sanitizeComponent(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        String cleaned = SANITIZE_PATTERN.matcher(value.trim()).replaceAll("_");
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    /**
     * Generate a filename for a transcription based on patient name and DOB.
     */
    public static File generateFilename(String patient, String dob) {
        String timestamp = TIMESTAMP_FORMATTER.format(new Date());
        String safePatient = sanitizeComponent(patient);
        String safeDob = sanitizeComponent(dob);
        String filename = String.format("%s_%s_%s.txt", safePatient, safeDob, timestamp);
        return new File(Config.TRANSCRIPTIONS_DIR, filename);
    }

    /**
     * List all transcription files, sorted by modification time (newest first).
     */
    public static List<File> listTranscriptions() {
        if (Config.TRANSCRIPTIONS_DIR == null || !Config.TRANSCRIPTIONS_DIR.exists()) {
            return new ArrayList<>();
        }
        File[] files = Config.TRANSCRIPTIONS_DIR.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) {
            return new ArrayList<>();
        }
        List<File> fileList = new ArrayList<>(Arrays.asList(files));
        Collections.sort(fileList, Comparator.comparingLong(File::lastModified).reversed());
        return fileList;
    }

    /**
     * Save transcription content to a file.
     */
    public static void saveTranscription(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        AuditLogger.log("save_transcription", file.toPath(), "", "Saved transcription");
    }

    /**
     * Load transcription content from a file.
     */
    public static String loadTranscription(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(isr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        }
        return content.toString();
    }

    /**
     * Securely delete a file by overwriting it multiple times with random data.
     */
    public static void secureDelete(File file, String patient) {
        if (file == null || !file.exists()) {
            return;
        }

        try {
            long length = file.length();
            if (length > 0) {
                byte[] buffer = new byte[4096];
                for (int i = 0; i < Config.SECURE_OVERWRITE_PASSES; i++) {
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        long written = 0;
                        while (written < length) {
                            secureRandom.nextBytes(buffer);
                            long toWrite = Math.min(buffer.length, length - written);
                            fos.write(buffer, 0, (int) toWrite);
                            written += toWrite;
                        }
                        fos.getFD().sync(); // Force write to physical device
                    }
                }
            }
            if (file.delete()) {
                AuditLogger.log("secure_delete", file.toPath(), patient != null ? patient : "",
                        String.format("Overwritten %d passes and deleted", Config.SECURE_OVERWRITE_PASSES));
            } else {
                throw new IOException("Failed to delete file after overwriting");
            }
        } catch (IOException e) {
            System.err.println("Failed to securely delete file: " + e.getMessage());
            AuditLogger.log("secure_delete_failed", file.toPath(), patient != null ? patient : "",
                    "Error: " + e.getMessage());
        }
    }
}
