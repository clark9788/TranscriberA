# Project Plan: TranscriberA

This file tracks the development plan and future work for the TranscriberA application.

---

### Phase 1: Project Setup & Core Functionality (✓ - Complete)

- [✓] Port project to Android Studio.
- [✓] Fix all Gradle build and dependency issues.
- [✓] Implement audio recording in `.wav` format.
- [✓] Implement transcription via Google Cloud.
- [✓] Implement saving and loading of transcriptions.
- [✓] Implement deletion of audio and transcription files.
- [✓] Fix all runtime crashes (`NoSuchMethodError`, etc.).

---

### Phase 2: Data & File Management (Current Focus)

- [ ] **Export Audit Log:** Add a feature to allow the user to securely export the `audit_log.csv` file from the app's private internal storage (e.g., using a Share Sheet).
- [ ] **Template Management:** Design a mechanism for the user to add or manage their own templates without needing to rebuild the app.
- [ ] **Auto-delete Recordings:** Automatically delete local audio recordings after a successful transcription to improve data hygiene.

---

### Phase 3: UI/UX Polish

- [ ] **Tablet UI:** Discuss and adapt the user interface to make better use of the larger screen space on tablets.

---

### Phase 4: Deployment

- [ ] **Deploy to Phone:** Prepare a "release" build of the app and guide through the process of installing it on a physical Android phone.
