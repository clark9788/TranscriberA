package com.transcriber.audio;

import android.media.MediaRecorder;
import com.transcriber.audit.AuditLogger;
import com.transcriber.config.Config;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Audio recording utilities using Android's MediaRecorder.
 */
public class AudioRecorder {

    private MediaRecorder mediaRecorder;
    private File currentFile;
    private boolean isRecording = false;

    /**
     * Get the current recording file path.
     */
    public File getCurrentFile() {
        return currentFile;
    }

    /**
     * Check if currently recording.
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Start recording audio from the microphone.
     *
     * @return Path to the recording file.
     * @throws IOException if recording setup fails.
     */
    public File start() throws IOException {
        if (isRecording) {
            throw new IllegalStateException("Recording is already in progress.");
        }

        if (Config.RECORDINGS_DIR == null || !Config.RECORDINGS_DIR.exists()) {
            if (!Config.RECORDINGS_DIR.mkdirs()) {
                throw new IOException("Could not create recordings directory.");
            }
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        currentFile = new File(Config.RECORDINGS_DIR, "recording_" + timestamp + ".3gp");

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
        mediaRecorder.setOutputFile(currentFile.getAbsolutePath());

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            AuditLogger.log("record_start", currentFile, "", "Recording started");
            return currentFile;
        } catch (IOException e) {
            stop(); // clean up
            throw new IOException("Failed to start recording", e);
        }
    }

    /**
     * Stop recording.
     *
     * @return Path to the recording file.
     */
    public File stop() {
        if (!isRecording || mediaRecorder == null) {
            return currentFile;
        }

        try {
            mediaRecorder.stop();
            AuditLogger.log("record_stop", currentFile, "", "Recording stopped");
        } catch (RuntimeException e) {
            // Can happen if stop() is called immediately after start()
            if (currentFile != null) {
                currentFile.delete(); // Clean up incomplete file
            }
            currentFile = null;
        } finally {
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
        }
        return currentFile;
    }
}
