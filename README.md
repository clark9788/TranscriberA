# TranscriberA - Android Medical Transcriber

A HIPAA-compliant medical transcription application, ported from a Java desktop application to a native Android app.

## Features

- **Audio Recording** - Record audio from the device microphone.
- **Google Cloud Speech-to-Text** - Transcribe audio using Google's `medical_conversation` model.
- **Template System** - Apply templates with placeholders like `{{TRANSCRIPT}}`, `{{PATIENT}}`, and `{{DOB}}`.
- **File Management** - Save, load, and securely delete transcriptions on the device.
- **Secure Deletion** - HIPAA-compliant file deletion with multiple overwrite passes.
- **Audit Logging** - Log all critical file operations for compliance.

## Prerequisites

- **Android Studio** (latest version recommended)
- **Android Device or Emulator** (API 24 or higher)
- **Google Cloud Account** with:
  - Speech-to-Text API enabled
  - Cloud Storage API enabled
  - A service account with appropriate permissions.

## Setup

### 1. Google Cloud Credentials

This app uses a service account to authenticate with Google Cloud. 

1.  **Enable APIs:** In your Google Cloud project, enable the **Speech-to-Text API** and **Cloud Storage API**.
2.  **Create Service Account:** Create a service account with the "Cloud Storage Admin" and "Speech-to-Text API User" roles.
3.  **JSON Key:** Create and download a JSON key for the service account.
4.  **Add to Project:** Place the downloaded JSON key file in the `app/src/main/res/raw` directory and name it `google_credentials.json`. The app will use this file to authenticate.

### 2. Open in Android Studio

1.  Clone or copy the project to your local machine.
2.  Open Android Studio and select "Open an existing project."
3.  Navigate to the project's root folder (`TranscriberA`).
4.  Android Studio will automatically sync the Gradle project.

### 3. Build and Run

- Select a run configuration (usually `app`).
- Choose a target device (emulator or physical device).
- Click the "Run" button (or press `Shift` + `F10`).

## Project Structure

```
TranscriberA/
├── app/
│   ├── build.gradle                   # Module-level build script
│   └── src/
│       ├── main/
│       │   ├── java/com/transcriber/    # Main application source code
│       │   │   ├── MainActivity.java
│       │   │   ├── audio/AudioRecorder.java
│       │   │   ├── audit/AuditLogger.java
│       │   │   ├── cloud/GCloudTranscriber.java
│       │   │   ├── config/Config.java
│       │   │   ├── file/FileManager.java
│       │   │   ├── template/TemplateManager.java
│       │   │   └── text/TranscriptionCleaner.java
│       │   ├── res/                     # Android resources
│       │   │   ├── layout/activity_main.xml
│       │   │   ├── values/*.xml
│       │   │   └── raw/google_credentials.json
│       │   ├── assets/                  # Asset files
│       │   │   └── templates/default_template.txt
│       │   └── AndroidManifest.xml
├── build.gradle                     # Top-level build script
├── settings.gradle                  # Gradle settings
└── README.md
```

## Dependencies

- AndroidX AppCompat & Material Components
- Google Cloud Libraries for Java (Speech-to-Text, Cloud Storage)

This project is now a standard Gradle-based Android project. You can move the entire `TranscriberA` directory to your desired location.
