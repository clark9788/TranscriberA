package com.transcriber;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
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

import com.transcriber.audio.AudioRecorder;
import com.transcriber.audit.AuditLogger;
import com.transcriber.cloud.GCloudTranscriber;
import com.transcriber.config.Config;
import com.transcriber.file.FileManager;
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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 200;

    private EditText patientNameEditText, dobEditText, transcriptionEditText;
    private Spinner templateSpinner, fileSpinner;
    private Button recordButton, stopButton, sendToGoogleButton, cleanButton, saveButton, deleteTranscriptionButton, deleteRecordingButton;
    private TextView statusTextView;

    private AudioRecorder audioRecorder;
    private Map<String, String> templates;
    private List<File> transcriptionFiles;
    private File currentRecordingFile;

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
            initializeApp();
        }
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
        int internetPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET);
        int recordAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return internetPermission == PackageManager.PERMISSION_GRANTED && recordAudioPermission == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET, Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSIONS);
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
        transcriptionFiles = FileManager.listTranscriptions();
        List<String> fileNames = new ArrayList<>();
        for (File file : transcriptionFiles) {
            fileNames.add(file.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fileNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fileSpinner.setAdapter(adapter);
    }

    private void setupListeners() {
        recordButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());
        sendToGoogleButton.setOnClickListener(v -> transcribeRecording());
        cleanButton.setOnClickListener(v -> cleanTranscription());
        saveButton.setOnClickListener(v -> saveTranscription());
        deleteTranscriptionButton.setOnClickListener(v -> deleteTranscription());
        deleteRecordingButton.setOnClickListener(v -> deleteAllRecordings());

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
            audioRecorder.stop();
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
            try {
                String patient = patientNameEditText.getText().toString();
                String transcript = GCloudTranscriber.uploadAndTranscribe(currentRecordingFile, patient, this::updateStatus);
                runOnUiThread(() -> {
                    String templateName = (String) templateSpinner.getSelectedItem();
                    if (templateName != null) {
                        String templateContent = templates.get(templateName);
                        Map<String, String> context = new HashMap<>();
                        context.put("PATIENT", patientNameEditText.getText().toString());
                        context.put("DOB", dobEditText.getText().toString());
                        String processedText = TemplateManager.applyTemplate(templateContent, transcript, context);
                        transcriptionEditText.setText(processedText);
                    } else {
                        transcriptionEditText.setText(transcript);
                    }
                    statusTextView.setText("Transcription complete.");
                });
            } catch (IOException | RuntimeException e) {
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

        File file = FileManager.generateFilename(patient, dob);
        String content = transcriptionEditText.getText().toString();
        executor.execute(() -> {
            try {
                FileManager.saveTranscription(file, content);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Transcription saved.", Toast.LENGTH_SHORT).show();
                    loadTranscriptionFiles();
                });
            } catch (IOException e) {
                Log.e(TAG, "Failed to save transcription", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save failed: " + e.toString(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void deleteTranscription() {
        int selectedPosition = fileSpinner.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= transcriptionFiles.size()) {
            Toast.makeText(this, "No file selected to delete.", Toast.LENGTH_SHORT).show();
            return;
        }
        File fileToDelete = transcriptionFiles.get(selectedPosition);
        String patient = patientNameEditText.getText().toString();
        executor.execute(() -> {
            if (FileManager.secureDelete(fileToDelete, patient)) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Transcription deleted.", Toast.LENGTH_SHORT).show();
                    loadTranscriptionFiles();
                    transcriptionEditText.setText("");
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

    private void loadFile(File file) {
        executor.execute(() -> {
            try {
                String content = FileManager.loadTranscription(file);
                runOnUiThread(() -> transcriptionEditText.setText(content));
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to load file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> statusTextView.setText(status));
    }
}
