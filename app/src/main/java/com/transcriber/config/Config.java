package com.transcriber.config;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Central configuration for the Medical Transcriber application.
 */
public class Config {

    // Base paths - these will be initialized by the main Android activity
    public static File TRANSCRIPTIONS_DIR;
    public static File RECORDINGS_DIR;
    public static File AUDIT_LOG_DIR;

    // Google Cloud
    public static final String GCS_BUCKET = "transcribe_bucket9788";
    public static final String LANGUAGE_CODE = "en-US";
    public static final String GCS_MODEL = "medical_conversation";
    public static final int POLL_INTERVAL_SEC = 5;

    // Audio recording defaults
    public static final int SAMPLE_RATE = 16_000;
    public static final int CHANNELS = 1;
    public static final String AUDIO_SUBTYPE = "PCM_SIGNED";

    // Security / deletion
    public static final int SECURE_OVERWRITE_PASSES = 3;

    // Transcription cleaning - filler words to remove
    public static final List<String> FILLER_WORDS = Arrays.asList(
        "um",
        "umm",
        "uh",
        "er",
        "ah",
        "eh",
        "a",  // Standalone "a" (will be handled carefully to avoid removing valid uses)
        "like",
        "you know",
        "well",
        "so",
        "actually",
        "basically"
    );

    private Config() {
        // Utility class - prevent instantiation
    }
}
