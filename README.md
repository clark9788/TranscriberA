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

## Developer Guide

This section contains instructions for developers for installing the app on a physical Android device.

### Running Directly from Android Studio (for Debugging)

This is the recommended method for development and debugging.

#### Step 1: Enable Developer Options on Your Phone
1. Go to **Settings** > **About phone**.
2. Scroll down and find the **Build number**.
3. Tap on the **Build number** seven (7) times in a row.
4. You'll see a pop-up message saying, "You are now a developer!" and you may be asked for your phone's PIN or password.

#### Step 2: Enable USB Debugging
1. Go back to the main **Settings** screen and look for a new menu called **System**, then **Developer options**.
2. Open **Developer options**.
3. Scroll down and find the **USB debugging** setting and turn it ON.

#### Step 3: Connect Your Phone to Your Laptop
1. Connect your phone to your laptop using a USB cable.
2. A pop-up will appear on your phone with the message "Allow USB debugging?".
3. Check the box that says **"Always allow from this computer"** and tap **Allow**.

#### Step 4: Install the App from Android Studio
1. In the Android Studio toolbar, find the device dropdown menu (next to the green 'Run' button).
2. Select your phone from the list.
3. Click the green **Run 'app'** button. Android Studio will build and install the app directly onto your phone.

### Manually Installing a Release APK

This method is for installing a pre-built `.apk` file, which is how end-users will install the app.

1. **Generate a Release APK:** Follow the deployment plan steps to build a signed `app-release.apk` file.
2. **Connect Your Phone:** Connect your phone to your computer via USB. On your phone, pull down the notification shade and in the USB options, select **"File Transfer"**.
3. **Copy the APK:** Your phone should appear as a drive on your computer. Copy the `app-release.apk` file from your project's `app/release/` folder into the **"Download"** folder on your phone.
4. **Install the App on Your Phone:**
    1. Disconnect the phone from the computer.
    2. Open a file manager app on your phone and navigate to the "Download" folder.
    3. Tap on `app-release.apk`. It may be scanned by Google Play Protect.
    4. You might be prompted to **"Allow installation from unknown sources"** for your file manager app. Enable this to proceed.
    5. Tap **Install** to complete the installation.
    6. Delete the .apk file from file manager.

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
