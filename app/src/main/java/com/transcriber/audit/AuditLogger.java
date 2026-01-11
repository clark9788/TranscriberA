package com.transcriber.audit;

import android.util.Log;
import com.transcriber.config.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * HIPAA-aligned audit logging for Android.
 */
public class AuditLogger {

    private static final String TAG = "AuditLogger";
    private static File LOG_FILE;
    private static final SimpleDateFormat ISO_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.UK);

    /**
     * Initialize the logger. Must be called once before logging.
     */
    public static void initialize() {
        if (Config.AUDIT_LOG_DIR != null) {
            Config.AUDIT_LOG_DIR.mkdirs();
            LOG_FILE = new File(Config.AUDIT_LOG_DIR, "audit_log.csv");
            ensureHeader();
        }
    }

    /**
     * Ensures the CSV file has a header. This is now checked on each log call.
     */
    private static void ensureHeader() {
        if (LOG_FILE == null || LOG_FILE.exists()) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE))) {
            writer.println("timestamp,action,file,patient,details");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write audit log header: ", e);
        }
    }

    /**
     * Append an audit row to the log file.
     */
    public static void log(String action, File file, String patient, String details) {
        if (LOG_FILE == null) {
            Log.e(TAG, "AuditLogger not initialized. Call AuditLogger.initialize() first.");
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = ISO_FORMATTER.format(new Date());
            String filePath = (file != null) ? file.getAbsolutePath() : "";

            String csvRow = String.format("%s,%s,%s,%s,%s",
                escapeCsv(timestamp),
                escapeCsv(action),
                escapeCsv(filePath),
                escapeCsv(patient != null ? patient : ""),
                escapeCsv(details != null ? details : ""));

            writer.println(csvRow);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to audit log: ", e);
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
}
