package com.transcriber.security;

import android.util.Log;
import com.transcriber.config.Config;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Manages encrypted metadata mapping UUID filenames to patient information.
 */
public class FileMetadataManager {

    private static final String TAG = "FileMetadataManager";
    private static final SimpleDateFormat DISPLAY_DATE_FORMATTER = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
    private static final SimpleDateFormat DISPLAY_TIME_FORMATTER = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private FileMetadataManager() {
        // Utility class - prevent instantiation
    }

    /**
     * Update metadata for a transcription file.
     */
    public static void updateMetadata(String uuid, String patientName, String dob, long timestamp) throws Exception {
        Map<String, FileMetadata> allMetadata = loadAllMetadata();

        String displayName = formatDisplayName(patientName, dob, timestamp);
        FileMetadata metadata = new FileMetadata(patientName, dob, timestamp, displayName);
        allMetadata.put(uuid, metadata);

        saveAllMetadata(allMetadata);
        Log.i(TAG, "Updated metadata for UUID: " + uuid);
    }

    /**
     * Get metadata for a specific UUID.
     */
    public static FileMetadata getMetadata(String uuid) throws Exception {
        Map<String, FileMetadata> allMetadata = loadAllMetadata();
        return allMetadata.get(uuid);
    }

    /**
     * Get all metadata entries.
     */
    public static Map<String, FileMetadata> getAllMetadata() throws Exception {
        return loadAllMetadata();
    }

    /**
     * Delete metadata for a specific UUID.
     */
    public static void deleteMetadata(String uuid) throws Exception {
        Map<String, FileMetadata> allMetadata = loadAllMetadata();
        allMetadata.remove(uuid);
        saveAllMetadata(allMetadata);
        Log.i(TAG, "Deleted metadata for UUID: " + uuid);
    }

    /**
     * Load all metadata from encrypted file.
     */
    private static Map<String, FileMetadata> loadAllMetadata() throws Exception {
        Map<String, FileMetadata> result = new HashMap<>();
        File metadataFile = new File(Config.TRANSCRIPTIONS_DIR, Config.METADATA_FILENAME);

        if (!metadataFile.exists()) {
            return result;
        }

        try {
            String json = EncryptionManager.decryptFile(metadataFile);
            JSONObject root = new JSONObject(json);

            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String uuid = keys.next();
                JSONObject obj = root.getJSONObject(uuid);
                FileMetadata metadata = new FileMetadata(
                        obj.getString("patientName"),
                        obj.getString("dob"),
                        obj.getLong("timestamp"),
                        obj.getString("displayName")
                );
                result.put(uuid, metadata);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse metadata JSON", e);
            throw new Exception("Failed to parse metadata", e);
        }

        return result;
    }

    /**
     * Save all metadata to encrypted file.
     */
    private static void saveAllMetadata(Map<String, FileMetadata> allMetadata) throws Exception {
        JSONObject root = new JSONObject();

        for (Map.Entry<String, FileMetadata> entry : allMetadata.entrySet()) {
            JSONObject obj = new JSONObject();
            FileMetadata metadata = entry.getValue();
            obj.put("patientName", metadata.patientName);
            obj.put("dob", metadata.dob);
            obj.put("timestamp", metadata.timestamp);
            obj.put("displayName", metadata.displayName);
            root.put(entry.getKey(), obj);
        }

        String json = root.toString();
        File metadataFile = new File(Config.TRANSCRIPTIONS_DIR, Config.METADATA_FILENAME);

        File tempPlaintext = new File(Config.TRANSCRIPTIONS_DIR, ".temp_metadata.json");
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempPlaintext);
            fos.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();

            EncryptionManager.encryptFile(tempPlaintext, metadataFile);
        } finally {
            tempPlaintext.delete();
        }
    }

    /**
     * Format a display name for the UI.
     */
    private static String formatDisplayName(String patientName, String dob, long timestamp) {
        return String.format("%s - %s", patientName, formatDOB(dob));
    }

    /**
     * Format DOB from YYYYMMDD to MM/DD/YYYY.
     */
    private static String formatDOB(String dob) {
        if (dob == null || dob.length() != 8) {
            return dob;
        }
        return dob.substring(4, 6) + "/" + dob.substring(6, 8) + "/" + dob.substring(0, 4);
    }

    /**
     * Metadata container class.
     */
    public static class FileMetadata {
        public final String patientName;
        public final String dob;
        public final long timestamp;
        public final String displayName;

        public FileMetadata(String patientName, String dob, long timestamp, String displayName) {
            this.patientName = patientName;
            this.dob = dob;
            this.timestamp = timestamp;
            this.displayName = displayName;
        }
    }
}
