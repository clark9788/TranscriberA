package com.transcriber.audit;

import android.util.Log;
import com.transcriber.config.Config;
import com.transcriber.security.EncryptionManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * HIPAA-aligned audit logging for Android.
 */
public class AuditLogger {

    private static final String TAG = "AuditLogger";
    private static File LOG_FILE;
    private static final SimpleDateFormat ISO_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.UK);

    static {
        ISO_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Initialize the logger. Must be called once before logging.
     */
    public static void initialize() {
        if (Config.AUDIT_LOG_DIR != null) {
            Config.AUDIT_LOG_DIR.mkdirs();
            LOG_FILE = new File(Config.AUDIT_LOG_DIR, "audit_log.csv.enc");
        }
    }

    /**
     * Append an audit row to the encrypted log file.
     */
    public static void log(String action, File file, String patient, String details) {
        if (LOG_FILE == null) {
            Log.e(TAG, "AuditLogger not initialized. Call AuditLogger.initialize() first.");
            return;
        }

        try {
            String timestamp = ISO_FORMATTER.format(new Date());
            String filePath = (file != null) ? file.getAbsolutePath() : "";

            String csvRow = String.format("%s,%s,%s,%s,%s",
                escapeCsv(timestamp),
                escapeCsv(action),
                escapeCsv(filePath),
                escapeCsv(patient != null ? patient : ""),
                escapeCsv(details != null ? details : ""));

            String existingContent = "";
            if (LOG_FILE.exists()) {
                existingContent = EncryptionManager.decryptFile(LOG_FILE);
            } else {
                existingContent = "timestamp,action,file,patient,details\n";
            }

            String updatedContent = existingContent + csvRow + "\n";

            File tempPlaintext = new File(Config.AUDIT_LOG_DIR, ".temp_audit.csv");
            try {
                try (PrintWriter writer = new PrintWriter(new FileWriter(tempPlaintext))) {
                    writer.print(updatedContent);
                }
                EncryptionManager.encryptFile(tempPlaintext, LOG_FILE);
            } finally {
                tempPlaintext.delete();
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to write to encrypted audit log: ", e);
        }
    }

    /**
     * Overload for compatibility with code still using java.nio.file.Path.
     */
    public static void log(String action, Path filePath, String patient, String details) {
        log(action, filePath != null ? filePath.toFile() : null, patient, details);
    }

    /**
     * Overload for logging with a String file path.
     */
    public static void log(String action, String filePath, String patient, String details) {
        log(action, filePath != null ? new File(filePath) : null, patient, details);
    }

    /**
     * Escape characters in a string for CSV format.
     */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Clean up audit log entries older than specified days.
     */
    public static void cleanupOldEntries(int retentionDays) {
        if (LOG_FILE == null || !LOG_FILE.exists()) {
            return;
        }

        try {
            String content = EncryptionManager.decryptFile(LOG_FILE);
            String[] lines = content.split("\n");

            if (lines.length <= 1) {
                return;
            }

            long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
            StringBuilder newContent = new StringBuilder();
            newContent.append(lines[0]).append("\n");

            int removedCount = 0;
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] fields = line.split(",", 2);
                if (fields.length > 0) {
                    try {
                        Date entryDate = ISO_FORMATTER.parse(fields[0].replace("\"", ""));
                        if (entryDate != null && entryDate.getTime() >= cutoffTime) {
                            newContent.append(line).append("\n");
                        } else {
                            removedCount++;
                        }
                    } catch (Exception e) {
                        newContent.append(line).append("\n");
                    }
                }
            }

            if (removedCount > 0) {
                File tempPlaintext = new File(Config.AUDIT_LOG_DIR, ".temp_audit_cleanup.csv");
                try {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(tempPlaintext))) {
                        writer.print(newContent.toString());
                    }
                    EncryptionManager.encryptFile(tempPlaintext, LOG_FILE);
                    Log.i(TAG, "Cleaned up " + removedCount + " old audit log entries");
                } finally {
                    tempPlaintext.delete();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to cleanup old audit log entries", e);
        }
    }
}
