package com.transcriber;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.transcriber.audio.AudioRecorder;
import com.transcriber.audit.AuditLogger;
import com.transcriber.cloud.GCloudTranscriber;
import com.transcriber.config.Config;
import com.transcriber.file.FileManager;
import com.transcriber.security.BiometricAuthHelper;
import com.transcriber.security.EncryptionManager;
import com.transcriber.security.FileMetadataManager;
import com.transcriber.template.TemplateManager;
import com.transcriber.text.TranscriptionCleaner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 200;

    private EditText patientNameEditText, dobEditText, transcriptionEditText;
    private Spinner templateSpinner, fileSpinner;
    private Button recordButton, stopButton, sendToGoogleButton, cleanButton, saveButton, deleteTranscriptionButton, deleteRecordingButton;
    private MaterialButton exportLogButton;
    private TextView statusTextView;

    private AudioRecorder audioRecorder;
    private Map<String, String> templates;
    private List<File> transcriptionFiles;
    private Map<String, FileMetadataManager.FileMetadata> transcriptionMetadata;
    private List<String> transcriptionUUIDs;
    private File currentRecordingFile;
    private String currentTranscriptionUUID;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize components
        initializeViews();

        initializePaths();
        AuditLogger.initialize();
        audioRecorder = new AudioRecorder(this);

        // Request permissions
        if (!checkPermissions()) {
            requestPermissions();
        } else {
            showBiometricPrompt();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void showBiometricPrompt() {
        BiometricAuthHelper authHelper = new BiometricAuthHelper(this);
        authHelper.authenticate(
                "Unlock TranscriberA",
                "Authenticate to access encrypted patient data",
                new BiometricAuthHelper.AuthCallback() {
                    @Override
                    public void onAuthSuccess() {
                        try {
                            EncryptionManager.getOrCreateKey();
                            initializeApp();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to initialize encryption key", e);
                            Toast.makeText(MainActivity.this, "Encryption setup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }

                    @Override
                    public void onAuthFailure(String error) {
                        Toast.makeText(MainActivity.this, "Authentication required to proceed", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
        );
    }

    private void initializeViews() {
        patientNameEditText = findViewById(R.id.patientNameEditText);
        dobEditText = findViewById(R.id.dobEditText);
        transcriptionEditText = findViewById(R.id.transcriptionEditText);
        templateSpinner = findViewById(R.id.templateSpinner);
        fileSpinner = findViewById(R.id.fileSpinner);
        recordButton = findViewById(R.id.recordButton);
        stopButton = findViewById(R.id.stopButton);
        sendToGoogleButton = findViewById(R.id.sendToGoogleButton);
        cleanButton = findViewById(R.id.cleanButton);
        saveButton = findViewById(R.id.saveButton);
        deleteTranscriptionButton = findViewById(R.id.deleteTranscriptionButton);
        deleteRecordingButton = findViewById(R.id.deleteRecordingButton);
        exportLogButton = findViewById(R.id.exportLogButton);
        statusTextView = findViewById(R.id.statusTextView);
    }

    private void initializePaths() {
        Config.TRANSCRIPTIONS_DIR = new File(getFilesDir(), "transcriptions");
        if (!Config.TRANSCRIPTIONS_DIR.exists()) {
            if (!Config.TRANSCRIPTIONS_DIR.mkdirs()) {
                Log.e(TAG, "Could not create transcriptions directory");
            }
        }
        Config.RECORDINGS_DIR = new File(getFilesDir(), "recordings");
        if (!Config.RECORDINGS_DIR.exists()) {
            if (!Config.RECORDINGS_DIR.mkdirs()) {
                Log.e(TAG, "Could not create recordings directory");
            }
        }
        Config.AUDIT_LOG_DIR = new File(getFilesDir(), "audit_logs");
        if (!Config.AUDIT_LOG_DIR.exists()) {
            if (!Config.AUDIT_LOG_DIR.mkdirs()) {
                Log.e(TAG, "Could not create audit_logs directory");
            }
        }
    }

    private boolean checkPermissions() {
        int recordAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return recordAudioPermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && checkPermissions()) {
                initializeApp();
            } else {
                Toast.makeText(this, "Permissions are required to use the app.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeApp() {
        try {
            GCloudTranscriber.initialize(this);
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize Google Cloud Transcriber", e);
            Toast.makeText(this, "Failed to initialize Google Cloud Transcriber. Check credentials.", Toast.LENGTH_LONG).show();
            sendToGoogleButton.setEnabled(false);
        }
        loadTemplates();
        loadTranscriptionFiles();
        setupListeners();
    }

    private void loadTemplates() {
        templates = TemplateManager.loadTemplates(this);
        List<String> templateNames = new ArrayList<>(templates.keySet());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, templateNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        templateSpinner.setAdapter(adapter);
    }

    private void loadTranscriptionFiles() {
        try {
            transcriptionFiles = FileManager.listEncryptedTranscriptions();
            transcriptionMetadata = FileMetadataManager.getAllMetadata();
            transcriptionUUIDs = new ArrayList<>();
            List<String> displayNames = new ArrayList<>();

            for (File file : transcriptionFiles) {
                String filename = file.getName();
                if (filename.endsWith(".enc")) {
                    String uuid = filename.substring(0, filename.length() - 4);
                    FileMetadataManager.FileMetadata metadata = transcriptionMetadata.get(uuid);
                    if (metadata != null) {
                        transcriptionUUIDs.add(uuid);
                        displayNames.add(metadata.displayName);
                    } else {
                        transcriptionUUIDs.add(uuid);
                        displayNames.add(uuid);
                    }
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            fileSpinner.setAdapter(adapter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load transcription files", e);
            Toast.makeText(this, "Failed to load files: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupListeners() {
        recordButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());
        sendToGoogleButton.setOnClickListener(v -> transcribeRecording());
        cleanButton.setOnClickListener(v -> cleanTranscription());
        saveButton.setOnClickListener(v -> saveTranscription());
        deleteTranscriptionButton.setOnClickListener(v -> deleteTranscription());
        deleteRecordingButton.setOnClickListener(v -> deleteAllRecordings());
        exportLogButton.setOnClickListener(v -> exportAuditLog());

        fileSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadFile(transcriptionFiles.get(position));
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void startRecording() {
        executor.execute(() -> {
            try {
                currentRecordingFile = audioRecorder.start();
                runOnUiThread(() -> {
                    statusTextView.setText("Recording...");
                    recordButton.setEnabled(false);
                    stopButton.setEnabled(true);
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void stopRecording() {
        executor.execute(() -> {
            File recordingFile = audioRecorder.stop();

            // Encrypt the audio file immediately after recording
            if (recordingFile != null && recordingFile.exists()) {
                try {
                    File encryptedAudio = new File(recordingFile.getAbsolutePath() + ".enc");
                    EncryptionManager.encryptBinaryFile(recordingFile, encryptedAudio);
                    recordingFile.delete();
                    currentRecordingFile = encryptedAudio;
                    Log.i(TAG, "Audio recording encrypted: " + encryptedAudio.getName());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to encrypt audio recording", e);
                    currentRecordingFile = recordingFile;
                }
            }

            runOnUiThread(() -> {
                statusTextView.setText("Recording stopped.");
                recordButton.setEnabled(true);
                stopButton.setEnabled(false);
            });
        });
    }

    private void transcribeRecording() {
        if (currentRecordingFile == null) {
            Toast.makeText(this, "No recording to transcribe.", Toast.LENGTH_SHORT).show();
            return;
        }

        statusTextView.setText("Transcribing...");
        executor.execute(() -> {
            File tempWavFile = null;
            try {
                String patient = patientNameEditText.getText().toString();
                String dob = dobEditText.getText().toString();

                File audioFileForUpload = currentRecordingFile;

                // If audio is encrypted, decrypt to temporary file for GCS upload
                if (currentRecordingFile.getName().endsWith(".enc")) {
                    Log.i(TAG, "Encrypted audio file: " + currentRecordingFile.getAbsolutePath() + " (" + currentRecordingFile.length() + " bytes)");
                    tempWavFile = new File(Config.RECORDINGS_DIR, ".temp_upload_" + System.currentTimeMillis() + ".wav");
                    EncryptionManager.decryptBinaryFile(currentRecordingFile, tempWavFile);
                    audioFileForUpload = tempWavFile;
                    Log.i(TAG, "Decrypted audio for GCS upload: " + tempWavFile.getName());
                    Log.i(TAG, "Decrypted audio size: " + tempWavFile.length() + " bytes");

                    // Verify WAV header
                    if (tempWavFile.length() < 44) {
                        throw new IOException("Decrypted audio file too small (< 44 bytes) - may be corrupted");
                    }
                }

                File finalTempWav = tempWavFile;

                // Log audio file details before upload
                Log.i(TAG, "Audio file for upload: " + audioFileForUpload.getAbsolutePath());
                Log.i(TAG, "Audio file size: " + audioFileForUpload.length() + " bytes");
                Log.i(TAG, "Audio file exists: " + audioFileForUpload.exists());

                String transcript = GCloudTranscriber.uploadAndTranscribe(audioFileForUpload, patient, this::updateStatus);

                // Log transcription result
                Log.i(TAG, "Transcription result length: " + (transcript != null ? transcript.length() : 0));
                Log.i(TAG, "Transcription content: " + (transcript != null ? transcript.substring(0, Math.min(100, transcript.length())) : "NULL"));

                if (transcript == null || transcript.trim().isEmpty()) {
                    Log.w(TAG, "Transcription is empty or null - possible audio quality issue");
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Transcription returned empty. Check audio quality or microphone.", Toast.LENGTH_LONG).show();
                        statusTextView.setText("Transcription empty - check audio");
                    });
                    // Clean up temp file
                    if (finalTempWav != null && finalTempWav.exists()) {
                        finalTempWav.delete();
                    }
                    return;
                }

                String finalTranscript = transcript;
                runOnUiThread(() -> {
                    try {
                        String templateName = (String) templateSpinner.getSelectedItem();
                        String processedText;
                        if (templateName != null) {
                            String templateContent = templates.get(templateName);
                            Map<String, String> context = new HashMap<>();
                            context.put("PATIENT", patient);
                            context.put("DOB", dob);
                            processedText = TemplateManager.applyTemplate(templateContent, finalTranscript, context);
                        } else {
                            processedText = finalTranscript;
                        }

                        Log.i(TAG, "Processed text length: " + processedText.length());

                        // STAGE 1: Auto-save encrypted transcription immediately
                        currentTranscriptionUUID = java.util.UUID.randomUUID().toString();
                        FileManager.saveEncryptedTranscription(currentTranscriptionUUID, patient, dob, processedText);

                        // TODO: GCS cleanup - delete audio + transcription from cloud
                        // TranscriptionApiClient.cleanup(audioFilename);

                        // Display for editing
                        transcriptionEditText.setText(processedText);
                        statusTextView.setText("Transcription saved (editable)");
                        Toast.makeText(MainActivity.this, "Transcription auto-saved. Press Save to update.", Toast.LENGTH_SHORT).show();

                        // Delete encrypted recording file
                        if (currentRecordingFile.delete()) {
                            Log.i(TAG, "Encrypted recording deleted: " + currentRecordingFile.getName());
                        }

                        // Delete temporary decrypted WAV if it exists
                        if (finalTempWav != null && finalTempWav.exists()) {
                            finalTempWav.delete();
                            Log.i(TAG, "Temporary WAV deleted: " + finalTempWav.getName());
                        }

                        currentRecordingFile = null;

                        loadTranscriptionFiles();

                        // Select the newly created transcription in the spinner
                        String newUuid = currentTranscriptionUUID;
                        if (newUuid != null && transcriptionUUIDs != null) {
                            int position = transcriptionUUIDs.indexOf(newUuid);
                            if (position >= 0) {
                                fileSpinner.setSelection(position);
                            }
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save transcription", e);
                        Toast.makeText(MainActivity.this, "Auto-save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                // Clean up temp file on error
                if (tempWavFile != null && tempWavFile.exists()) {
                    tempWavFile.delete();
                }
                Log.e(TAG, "Transcription failed", e);
                runOnUiThread(() -> {
                    statusTextView.setText("Transcription failed.");
                    Toast.makeText(MainActivity.this, "Transcription failed: " + e.toString(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void cleanTranscription() {
        String currentText = transcriptionEditText.getText().toString();
        String cleanedText = TranscriptionCleaner.removeFillerWords(currentText);
        transcriptionEditText.setText(cleanedText);
        Toast.makeText(this, "Transcription cleaned.", Toast.LENGTH_SHORT).show();
    }

    private void saveTranscription() {
        String patient = patientNameEditText.getText().toString();
        String dob = dobEditText.getText().toString();

        if (TextUtils.isEmpty(patient) || TextUtils.isEmpty(dob)) {
            Toast.makeText(this, "Patient Name and DOB cannot be empty.", Toast.LENGTH_LONG).show();
            return;
        }

        String content = transcriptionEditText.getText().toString();
        executor.execute(() -> {
            try {
                // STAGE 2: Update existing encrypted file (or create new if no auto-save happened)
                if (currentTranscriptionUUID == null) {
                    currentTranscriptionUUID = java.util.UUID.randomUUID().toString();
                }

                String savedUuid = currentTranscriptionUUID;
                FileManager.saveEncryptedTranscription(currentTranscriptionUUID, patient, dob, content);

                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Transcription updated.", Toast.LENGTH_SHORT).show();
                    loadTranscriptionFiles();

                    // Keep the current file selected after save
                    if (savedUuid != null && transcriptionUUIDs != null) {
                        int position = transcriptionUUIDs.indexOf(savedUuid);
                        if (position >= 0) {
                            fileSpinner.setSelection(position);
                        }
                    }

                    currentTranscriptionUUID = savedUuid;
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to save transcription", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void deleteTranscription() {
        int selectedPosition = fileSpinner.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= transcriptionUUIDs.size()) {
            Toast.makeText(this, "No file selected to delete.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uuid = transcriptionUUIDs.get(selectedPosition);
        String patient = patientNameEditText.getText().toString();
        executor.execute(() -> {
            if (FileManager.deleteEncryptedTranscription(uuid, patient)) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Transcription deleted.", Toast.LENGTH_SHORT).show();
                    loadTranscriptionFiles();
                    transcriptionEditText.setText("");
                    if (uuid.equals(currentTranscriptionUUID)) {
                        currentTranscriptionUUID = null;
                    }
                });
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to delete transcription.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteAllRecordings() {
        new AlertDialog.Builder(this)
                .setTitle("Delete All Recordings")
                .setMessage("Are you sure you want to permanently delete all .wav recording files?")
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    executor.execute(() -> {
                        int deletedCount = FileManager.deleteAllRecordings();
                        Log.d(TAG, "deleteAllRecordings completed. Count: " + deletedCount);
                        runOnUiThread(() -> {
                            if (deletedCount > 0) {
                                Toast.makeText(MainActivity.this, deletedCount + " recordings deleted.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "No recordings found to delete.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                })
                .setNegativeButton(android.R.string.no, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void exportAuditLog() {
        executor.execute(() -> {
            try {
                Log.i(TAG, "Export audit log started");
                File encryptedLog = new File(Config.AUDIT_LOG_DIR, "audit_log.csv.enc");
                if (!encryptedLog.exists()) {
                    Log.w(TAG, "Audit log file does not exist");
                    runOnUiThread(() -> Toast.makeText(this, "Audit log is empty.", Toast.LENGTH_SHORT).show());
                    return;
                }

                Log.i(TAG, "Decrypting audit log: " + encryptedLog.getAbsolutePath());
                File tempPlaintextLog = new File(getCacheDir(), "audit_log_export.csv");
                EncryptionManager.decryptFile(encryptedLog, tempPlaintextLog);
                Log.i(TAG, "Audit log decrypted to: " + tempPlaintextLog.getAbsolutePath() + " (" + tempPlaintextLog.length() + " bytes)");

                runOnUiThread(() -> {
                    try {
                        String authority = getApplicationContext().getPackageName() + ".provider";
                        Log.i(TAG, "Creating FileProvider URI with authority: " + authority);
                        Uri logUri = FileProvider.getUriForFile(this, authority, tempPlaintextLog);
                        Log.i(TAG, "FileProvider URI created: " + logUri.toString());

                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/csv");
                        shareIntent.putExtra(Intent.EXTRA_STREAM, logUri);
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        Log.i(TAG, "Starting share chooser");
                        startActivity(Intent.createChooser(shareIntent, "Export Audit Log"));

                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (tempPlaintextLog.exists()) {
                                tempPlaintextLog.delete();
                                Log.i(TAG, "Temporary audit log export file deleted");
                            }
                        }, 300000); // 5 minutes
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to create share intent", e);
                        Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to export audit log", e);
                runOnUiThread(() -> Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void loadFile(File file) {
        executor.execute(() -> {
            try {
                String filename = file.getName();
                if (filename.endsWith(".enc")) {
                    String uuid = filename.substring(0, filename.length() - 4);
                    String content = FileManager.loadEncryptedTranscription(uuid);

                    FileMetadataManager.FileMetadata metadata = transcriptionMetadata.get(uuid);
                    if (metadata != null) {
                        String patientName = metadata.patientName;
                        String dob = metadata.dob;
                        runOnUiThread(() -> {
                            patientNameEditText.setText(patientName);
                            dobEditText.setText(dob);
                            transcriptionEditText.setText(content);
                            currentTranscriptionUUID = uuid;
                        });
                    } else {
                        runOnUiThread(() -> {
                            transcriptionEditText.setText(content);
                            currentTranscriptionUUID = uuid;
                        });
                    }
                } else {
                    String content = FileManager.loadTranscription(file);
                    runOnUiThread(() -> transcriptionEditText.setText(content));
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to load file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> statusTextView.setText(status));
    }
}
