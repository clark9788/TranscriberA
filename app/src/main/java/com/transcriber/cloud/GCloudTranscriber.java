package com.transcriber.cloud;

import android.content.Context;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.transcriber.R;
import com.transcriber.audit.AuditLogger;
import com.transcriber.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Google Cloud Storage + Speech-to-Text integration.
 */
public class GCloudTranscriber {

    private static SpeechClient speechClient;
    private static Storage storageClient;

    public static void initialize(Context context) throws IOException {
        if (speechClient == null || storageClient == null) {
            try (InputStream credentialsStream = context.getResources().openRawResource(R.raw.google_credentials)) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
                SpeechSettings speechSettings = SpeechSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build();
                speechClient = SpeechClient.create(speechSettings);
                storageClient = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
            }
        }
    }

    /**
     * Upload an audio file, run transcription, return the transcript text.
     */
    public static String uploadAndTranscribe(File audioFile, String patient, Consumer<String> statusCallback)
            throws IOException {
        if (!audioFile.exists()) {
            throw new FileNotFoundException("Audio file not found: " + audioFile.getAbsolutePath());
        }

        setStatus(statusCallback, "Uploading…");

        Bucket bucket = storageClient.get(Config.GCS_BUCKET);
        if (bucket == null) {
            throw new RuntimeException("Bucket not found: " + Config.GCS_BUCKET);
        }

        // Read file into byte array
        byte[] fileData = new byte[(int) audioFile.length()];
        try (FileInputStream fis = new FileInputStream(audioFile)) {
            fis.read(fileData);
        }

        Blob blob = bucket.create(audioFile.getName(), fileData, "audio/amr-wb");
        AuditLogger.log("gcs_upload", audioFile.toPath(), patient != null ? patient : "", "Uploaded to GCS");

        String gcsUri = "gs://" + Config.GCS_BUCKET + "/" + audioFile.getName();

        RecognitionConfig config = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.AMR_WB)
                .setSampleRateHertz(16000)
                .setLanguageCode(Config.LANGUAGE_CODE)
                .setModel(Config.GCS_MODEL)
                .setEnableAutomaticPunctuation(true)
                .build();

        RecognitionAudio audio = RecognitionAudio.newBuilder().setUri(gcsUri).build();

        setStatus(statusCallback, "Transcribing…");

        OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> operation =
                speechClient.longRunningRecognizeAsync(config, audio);

        try {
            LongRunningRecognizeResponse response = operation.get();
            StringBuilder transcript = new StringBuilder();
            for (SpeechRecognitionResult result : response.getResultsList()) {
                if (result.getAlternativesCount() > 0) {
                    transcript.append(result.getAlternatives(0).getTranscript());
                }
            }
            return transcript.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get transcription result", e);
        } finally {
            blob.delete();
            AuditLogger.log("gcs_delete", audioFile.toPath(), patient != null ? patient : "", "Deleted blob from GCS");
            setStatus(statusCallback, "Completed");
        }
    }

    private static void setStatus(Consumer<String> callback, String message) {
        if (callback != null) {
            callback.accept(message);
        }
    }
}