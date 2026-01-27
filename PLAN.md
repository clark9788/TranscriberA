# TranscriberA Security Enhancement Plan

## Project Overview
Medical transcription Android app requiring HIPAA compliance improvements through Firebase Authentication migration and file encryption implementation.

---

## TASK 1: FIREBASE AUTHENTICATION MIGRATION

### Current State Analysis
**Problem:** Google Cloud service account credentials (`google_credentials.json`) embedded directly in APK
- **Critical Security Issue:** Extractable via APK decompilation
- **Location:** `app/src/main/res/raw/google_credentials.json`
- **Contains:** Full RSA private key for service account `transcribe@transcriber-477917.iam.gserviceaccount.com`
- **Permissions:** Cloud Storage Admin + Speech-to-Text API User
- **Risk:** Unauthorized access to GCS bucket, potential financial impact

**Current Usage:**
- Loaded in `GCloudTranscriber.java:35`
- Used for Google Cloud Speech-to-Text API calls
- Used for Cloud Storage bucket operations

### Target State
**Solution:** Firebase Authentication with secure credential management
- **User Authentication:** Google Sign-In provider (DECISION: Skip Anonymous - HIPAA requires user accountability)
- **Rationale:** Medical app needs real user identity for audit trail; Anonymous auth doesn't provide accountability
- **Backend API:** Cloud Functions or Cloud Run to proxy Google Cloud API calls
- **Credential Storage:** Service account stays server-side only
- **Client Communication:** Firebase ID tokens for authentication

### Architecture Changes

#### Before (Current):
```
[Android App] → [Embedded Service Account] → [Google Cloud APIs]
   └─ google_credentials.json (INSECURE)
```

#### After (Target):
```
[Android App] → [Firebase Auth] → [Backend API] → [Google Cloud APIs]
      ↓                              (Cloud Functions/Run)
   Firebase ID Token                 └─ Service Account (SECURE)
```

### Implementation Steps

#### Phase 1: Firebase Setup (Client-Side)
1. **Add Firebase to Android App**
   - Add Firebase Android SDK dependencies to `app/build.gradle`
   - Download `google-services.json` from Firebase Console
   - Place in `app/` directory (NOT in version control - add to `.gitignore`)
   - Configure Firebase project: `transcriber-477917` (existing project)

2. **Implement Google Sign-In Authentication**
   - Enable in Firebase Console → Authentication → Sign-in method
   - Configure OAuth consent screen
   - Implement Google Sign-In flow with Firebase
   - **Session Persistence:** Firebase tokens last indefinitely (weeks/months)
     - ID token valid for 1 hour, auto-refreshed by SDK
     - Refresh token stored securely on device
     - Doctor signs in ONCE, stays authenticated across days/weeks
     - No timeout between patients - authentication persists until explicit sign-out

3. **User Session Management**
   - Listen to `FirebaseAuth.AuthStateListener` for session changes
   - Store current user state in `MainActivity`
   - On app launch: Check `FirebaseAuth.getInstance().getCurrentUser()`
     - If null → Prompt Google Sign-In 
     - If not null → Proceed with app (doctor already authenticated)
   - Handle sign-out and account switching (manual actions only)

#### Phase 2: Backend API Development
1. **Create Secure Backend - Cloud Functions (JavaScript)**
   **DECISION: Use Firebase Cloud Functions (Option A)**
   - 5-minute recordings fit well within 540s timeout
   - Simpler deployment than Cloud Run
   - If transcription proves too slow, can migrate to Cloud Run later

   **Important:** Cannot reuse existing `GCloudTranscriber.java` code server-side
   - Cloud Functions requires JavaScript/TypeScript
   - Will rewrite transcription logic in JavaScript for Cloud Functions
   - Android `GCloudTranscriber.java` becomes REST API client

   **Directory Structure:**
   ```
   functions/
   ├── index.js              # Cloud Functions endpoints
   ├── package.json          # Dependencies (Google Cloud SDK for Node)
   └── serviceAccountKey.json (server-side ONLY, use Secret Manager)
   ```0

2. **API Endpoints to Implement**
   - `POST /api/uploadAudio` - Upload audio to GCS, return GCS URI
   - `POST /api/transcribe` - Start transcription job, return operation ID
   - `GET /api/transcriptionStatus/:operationId` - Poll for completion
   - `DELETE /api/cleanup/:filename` - Clean up GCS audio file

   **Deletion Timing (Two-Stage Save Workflow):**
   - **Stage 1 - Auto-save on transcription return:**
     - Encrypted file saved to disk immediately (data protection)
     - GCS cleanup happens immediately (delete audio + transcription from cloud)
   - **Stage 2 - Manual save on button press:**
     - Doctor edits/confirms, presses "Save Transcription"
     - Overwrites local encrypted file only (no GCS interaction needed)
   - **Rationale:**
     - Immediate GCS cleanup prevents stranded cloud files if doctor doesn't press Save
     - Transcription already protected on disk, low risk of data loss
     - Recording can be remade if needed (fail-safe approach)

3. **Authentication Middleware**
   - Verify Firebase ID token on every request
   - Extract `uid` from token
   - Implement rate limiting per user  
   - Log all API calls to audit trail

4. **Security Implementation**
   - Service account stored as environment variable (Cloud Secret Manager)
   - CORS configuration: Only allow app's domain
   - Input validation: File size limits, filename sanitization
   - Error handling: Don't leak internal details to client

#### Phase 3: Android Client Refactoring
1. **Rewrite GCloudTranscriber.java as REST API Client**
   - Remove `google_credentials.json` loading
   - Remove direct Google Cloud SDK dependencies
   - Replace with HTTP requests to Cloud Functions backend API
   - Use `OkHttp` or `Retrofit` for REST API calls
   - Attach Firebase ID token in `Authorization: Bearer <token>` header

   **Architecture Change:**
   ```java
   // OLD: Direct Google Cloud SDK calls
   SpeechClient speechClient = SpeechClient.create(credentials);

   // NEW: REST API calls to Cloud Functions
   OkHttpClient client = new OkHttpClient();
   Request request = new Request.Builder()
       .url(API_BASE_URL + "/api/transcribe")
       .addHeader("Authorization", "Bearer " + firebaseToken)
       .post(audioFileBody)
       .build();
   ```

2. **Error Handling**
   - Handle 401 Unauthorized → Prompt re-authentication
   - Handle 429 Rate Limited → Show user-friendly message
   - Handle 500 Server Error → Retry with exponential backoff
   - Network errors → Queue for retry when connection restored

3. **Update Config.java**
   - Remove `BUCKET_NAME` constant (server-side only now)
   - Add `API_BASE_URL` constant for backend endpoint
   - Add `API_TIMEOUT_MS` constant

#### Phase 4: Testing & Validation
1. **Unit Tests**
   - Mock Firebase Auth calls
   - Test token refresh logic
   - Test API client error handling

2. **Integration Tests**
   - End-to-end flow: Google Sign-In → Record → Upload → Transcribe → Auto-save → Edit → Manual save
   - Session persistence across app restarts
   - Token auto-refresh before expiration

3. **Migration Strategy**
   - Deploy backend API first (test with Postman)
   - Update Android app to use new API (keep credentials as fallback)
   - Monitor backend logs for errors
   - Remove embedded credentials after 2-week validation period

### Files to Modify
- `app/build.gradle` - Add Firebase and HTTP client dependencies
- `app/src/main/java/com/transcriber/cloud/GCloudTranscriber.java` - Replace with API client
- `app/src/main/java/com/transcriber/MainActivity.java` - Add Firebase Auth initialization
- `app/src/main/java/com/transcriber/config/Config.java` - Update constants
- `app/src/main/AndroidManifest.xml` - Add internet permission metadata
- `.gitignore` - Add `google-services.json`, `serviceAccountKey.json`

### Files to Create
- `functions/index.js` - Backend API (DECISION: Cloud Functions with JavaScript - simpler, 5-minute recordings fit well within 540s timeout)
- `app/src/main/java/com/transcriber/auth/AuthManager.java` - Firebase Auth wrapper
- `app/src/main/java/com/transcriber/api/TranscriptionApiClient.java` - REST API client

### Files to Delete
- `app/src/main/res/raw/google_credentials.json` - Remove embedded credentials

### Security Benefits
✅ Service account credentials no longer in APK
✅ User authentication and authorization
✅ Rate limiting per user
✅ Audit trail of API usage by user ID
✅ Ability to revoke access via Firebase Console
✅ Compliance with security best practices

---

## TASK 2: FILE ENCRYPTION AT REST ✅ COMPLETED

### Current State Analysis
**Problem:** All PHI (Protected Health Information) stored unencrypted
- **Transcription Files:** `.txt` files in `files/transcriptions/` - Full patient notes plaintext
- **Audio Recordings:** `.wav` files in `files/recordings/` - Patient conversations plaintext
- **Audit Logs:** `.csv` files in `files/audit_logs/` - Patient names and file paths plaintext
- **Filenames:** Contain PHI - `Smith_John_19850615_143022.txt`

**Current Storage:**
- Uses Android internal storage (`getFilesDir()`)
- App-sandboxed (not accessible by other apps)
- BUT NOT encrypted by default
- Vulnerable if device is rooted, via ADB backup, or during device-to-device transfer

**HIPAA Requirement:**
- ePHI on mobile devices MUST be encrypted at rest
- Encryption keys must be protected
- Decryption should require user authentication

### Target State
**Solution:** AES-256-GCM encryption for all PHI files
- **Encryption Algorithm:** AES-256-GCM (authenticated encryption)
- **Key Management:** Android Keystore System (hardware-backed if available)
- **Key Derivation:** Android KeyGenerator (hardware-backed)
- **Authentication:** Require user PIN/biometric on app launch to unlock encryption key (DECISION: Authenticate once on launch, 10-hour validity window for doctor's workday)

### Architecture Design

#### Encryption Strategy
```
Plaintext File → AES-256-GCM Encrypt → Encrypted File (.enc)
                      ↓
                  Key from Android Keystore
                      ↓
                  Protected by Biometric/PIN
```

#### Key Management Options

**Option 1: User-Derived Key (PBKDF2)**
- User sets PIN/password on first launch
- Key derived using PBKDF2 with salt
- Salt stored in SharedPreferences (encrypted)
- Pros: User controls key, can change password
- Cons: Requires user to remember password, password reset = data loss

**Option 2: Android Keystore (SELECTED)**
- App generates AES key via `KeyGenerator` on first launch
- Key stored in Android Keystore (hardware TEE if available)
- Requires biometric/PIN to unlock key each time app opens (after timeout)
- Pros: No password to remember, hardware-backed security, automatic setup
- Cons: Key tied to device (acceptable for short-term dictation storage)
- Note: Audit logs can be decrypted and exported when needed via biometric authentication

**Implementation:** Biometric prompt on app launch, key stays unlocked for 10 hours (36,000 seconds) - covers full doctor workday without interruption between patients

### Implementation Steps

#### Phase 1: Keystore Setup
1. **Create EncryptionManager.java**
   - Wrapper for Android Keystore operations
   - Generate or retrieve AES-256 key
   - Key alias: `transcriber_master_key`
   - Require biometric/PIN authentication for key access

2. **Key Generation**
   ```java
   KeyGenerator keyGen = KeyGenerator.getInstance(
       KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

   KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
       "transcriber_master_key",
       KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
       .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
       .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
       .setUserAuthenticationRequired(true)
       .setUserAuthenticationValidityDurationSeconds(36000) // 10 hours (doctor's workday)
       .build();

   keyGen.init(keySpec);
   SecretKey key = keyGen.generateKey();
   ```

3. **Biometric Prompt Integration**
   - Use `androidx.biometric.BiometricPrompt`
   - Fallback to device PIN/password
   - Lock encryption key after 10-hour timeout (overnight security)
   - Doctor authenticates once per workday (morning), no interruptions between patients

#### Phase 2: File Encryption Implementation
1. **Update FileManager.java**
   - Add `encryptFile(File plaintext, File encrypted)` method
   - Add `decryptFile(File encrypted, File plaintext)` method
   - Add `encryptedFilename(String original)` helper

2. **Encryption Method (AES-256-GCM)**
   ```java
   Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.ENCRYPT_MODE, secretKey);
   byte[] iv = cipher.getIV(); // 12 bytes for GCM

   // Encrypt data
   byte[] plaintext = readFile(plaintextFile);
   byte[] ciphertext = cipher.doFinal(plaintext);

   // File format: [IV_LENGTH(1 byte)][IV][CIPHERTEXT]
   writeEncryptedFile(encryptedFile, iv, ciphertext);
   ```

3. **Decryption Method**
   ```java
   // Read encrypted file
   byte[] fileData = readFile(encryptedFile);
   int ivLength = fileData[0];
   byte[] iv = Arrays.copyOfRange(fileData, 1, 1 + ivLength);
   byte[] ciphertext = Arrays.copyOfRange(fileData, 1 + ivLength, fileData.length);

   // Decrypt
   Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
   byte[] plaintext = cipher.doFinal(ciphertext);
   ```

4. **File Format**
   ```
   Encrypted File Structure:
   [1 byte: IV length] [12 bytes: IV] [N bytes: Encrypted data + Auth tag]
   ```

#### Phase 3: Encrypt Existing Operations
1. **Transcription Saving - Two-Stage Workflow**

   **Current Behavior:** Transcription kept in EditText memory until "Save Transcription" pressed

   **NEW Behavior: Two-stage save for data protection**

   **Stage 1: Auto-Save on Transcription Return**
   - Transcription comes back from Cloud Functions
   - ✅ **Immediately save encrypted file to disk** (UUID filename)
   - Display transcription in EditText for doctor review
   - Track `currentTranscriptionUUID` for updates
   - **Purpose:** Protect data if app crashes or doctor forgets to save

   **Stage 2: Manual Save on Button Press**
   - Doctor reviews/edits transcription text in EditText
   - Doctor presses "Save Transcription" button
   - ✅ **Overwrite same encrypted file** with edited content (same UUID)
   - **Purpose:** Allow corrections and updates to transcription content

   **Workflow:**
   ```java
   // Stage 1: In transcribeRecording()
   String transcriptionText = GCloudTranscriber.uploadAndTranscribe(...);

   // Save encrypted file to disk immediately
   currentTranscriptionUUID = UUID.randomUUID().toString();
   FileManager.saveTranscription(currentTranscriptionUUID, patientName, dob, transcriptionText);

   // Delete audio + transcription from GCS immediately (prevent stranded files)
   TranscriptionApiClient.cleanup(audioFilename);

   // Display for editing
   transcriptionEditText.setText(transcriptionText);
   Toast.makeText(this, "Transcription saved (editable)", Toast.LENGTH_SHORT).show();

   // Stage 2: In saveTranscription()
   String editedText = transcriptionEditText.getText().toString();

   // Just update local encrypted file (no GCS interaction needed)
   FileManager.saveTranscription(currentTranscriptionUUID, patientName, dob, editedText);
   Toast.makeText(this, "Transcription updated", Toast.LENGTH_SHORT).show();
   ```

   **File Format:**
   - Before: `PatientName_DOB_Timestamp.txt` (plaintext)
   - After: `uuid-123.enc` (encrypted, UUID filename for privacy)

   **Edge Cases Handled:**
   - **Doctor edits and saves:** Auto-save creates file + cleans GCS, manual save overwrites local file with edits ✅
   - **Doctor never presses Save (interrupted):** Auto-saved file already protected on disk, GCS already cleaned up (no stranded files) ✅
   - **App crashes between stages:** Auto-saved encrypted file persists on phone, doctor can find in file list later ✅
   - **Local file persistence:** Encrypted transcription (.enc) remains on phone until doctor manually deletes via UI ✅
   - **Doctor loads old file, edits, re-saves:** Track loaded UUID, manual save knows which file to overwrite ✅

   **Benefits:**
   - ✅ Data protected immediately (HIPAA compliance)
   - ✅ Edit capability preserved (unlimited edits on local file)
   - ✅ Single file per transcription (no orphans)
   - ✅ Immediate GCS cleanup (no stranded cloud files, cost savings)
   - ✅ Recording can be remade if needed (fail-safe approach)

2. **Transcription Loading (FileManager.loadTranscription)**
   - Read `.enc` file, decrypt, return plaintext String
   - Workflow:
     1. Retrieve encryption key from Keystore (biometric already authenticated on launch)
     2. Decrypt file to memory
     3. Return plaintext content
     4. Do NOT write decrypted data to disk
   - When loading file for editing: Track UUID for re-save

3. **Audio Recording (AudioRecorder.java)**
   - **DECISION: Encrypt immediately after recording completes**
     - Pro: Simple, no changes to recording logic
     - Con: Plaintext exists temporarily on disk (1-5 seconds)
     - **Risk Assessment:** LOW - Android app sandbox prevents other apps from accessing internal storage during brief exposure
     - **Mitigation:** Use UUID temp filename (no PHI), encrypt immediately, delete plaintext with standard `file.delete()` (see Secure Deletion section below)
   - Option B: Stream encryption during recording (complex, unnecessary for this risk level)

4. **Audit Logs (AuditLogger.java)**
   - Encrypt audit log file (`audit_log.csv.enc`)
   - Decrypt only when exporting via FileProvider for auditor review
   - Append operations:
     1. Decrypt existing log
     2. Append new entry
     3. Re-encrypt entire log
   - **Retention Policy:** Auto-delete entries older than 30 days (configurable) - HIPAA requires 6 years for formal records, but this is short-term dictation tool
   - **Export Function:** Allow doctor to decrypt and export audit log before cleanup (via email/USB to auditor)
   - **Performance:** Simple decrypt/append/re-encrypt is sufficient for 30-day retention (logs remain small)

#### Phase 4: Filename Obfuscation
**Current Issue:** Filenames contain PHI (patient name, DOB)
- Example: `Smith_John_19850615_143022.txt`

**Solution:** Use UUID filenames with encrypted metadata mapping (DECISION: Metadata file - doctor needs to see patient name/DOB when selecting file to review)
- Storage: `a3f2c8d9-4b1e-47a3-9c6f-8d2e4f5a6b7c.enc` (on disk)
- Display: `Smith, John - 06/15/1985 - 2:30 PM` (in UI from decrypted metadata)

**Implementation:**
1. Generate UUID for each new file
2. Store mapping: `{uuid: {patientName, dob, timestamp, displayName}}`
3. Save mapping as `file_metadata.json.enc` (encrypted with same key)
4. On app launch: Decrypt metadata, populate file list with user-friendly names
5. When doctor selects file: Lookup UUID from metadata, decrypt actual file

#### Phase 5: Secure Deletion Strategy

**Why 3-Pass Overwrite Doesn't Work on Flash Storage:**
- **Original Intent:** Designed for spinning hard drives (HDDs) where magnetic remnants could reveal overwritten data
- **Flash Reality (Android devices use eMMC/UFS flash memory):**
  - Wear leveling moves data to different physical NAND cells unpredictably
  - File system has no control over actual physical storage location
  - Multiple overwrites accelerate flash wear without improving security
  - Garbage collection may leave copies in unmapped blocks
  - **Conclusion:** 3-pass overwrite is security theater on flash storage

**Encryption-Based Deletion (Recommended Approach):**
- **For Encrypted Files (.enc):** Standard `file.delete()` is sufficient
  - Without the encryption key (stored in hardware Keystore), file data is cryptographically useless
  - AES-256-GCM with random IV means identical plaintext produces different ciphertext each time
  - Even if flash controller leaves remnants, they cannot be decrypted

- **For Temporary Plaintext Files (e.g., audio recording before encryption):**
  - **Risk Window:** 1-5 seconds between recording stop and encryption completion
  - **Mitigation:** Standard `file.delete()` immediately after encryption
  - **Why Acceptable:**
    - Android app sandbox prevents other apps from accessing internal storage
    - File system may immediately reuse the space for encrypted version
    - Android TRIM command eventually erases unmarked blocks
    - Brief exposure + sandboxing = acceptable risk
  - **Extra Paranoia (Optional):** Overwrite once with random data before delete - but provides minimal benefit given flash architecture

**Implementation:**
```java
// For encrypted files - simple delete
encryptedFile.delete();
auditLogger.log("DELETED", fileName, userId);

// For temp plaintext - delete immediately after encryption
File tempAudio = recordAudio(); // Plaintext WAV
File encrypted = encryptFile(tempAudio); // Create .enc
tempAudio.delete(); // Remove plaintext (1-5 second exposure)
```

**Key Rotation (Optional Enhancement):**
- Periodically regenerate encryption key in Keystore
- Re-encrypt all files with new key
- Provides defense-in-depth if key extraction ever occurs
- Not required for standard security posture

#### Phase 6: Migration of Existing Data
**DECISION: NOT NEEDED - Skip this phase**

**Rationale:**
- This app is not currently deployed to users
- Fresh installation only (no existing unencrypted files to migrate)
- All files will be encrypted from day one
- Short-term storage model (doctor dictates → transcribes → transfers to EMR → deletes)

**Implementation:** Remove migration logic from code - simplifies implementation and reduces complexity

### Files to Modify
- `app/src/main/java/com/transcriber/file/FileManager.java` - Add encryption/decryption methods, update `saveTranscription()` signature to accept UUID
- `app/src/main/java/com/transcriber/audio/AudioRecorder.java` - Encrypt recordings after stop
- `app/src/main/java/com/transcriber/audit/AuditLogger.java` - Encrypt audit logs
- `app/src/main/java/com/transcriber/MainActivity.java` - Changes for two-stage save:
  - Add `currentTranscriptionUUID` field to track current transcription
  - Modify `transcribeRecording()` to:
    - Auto-save encrypted file immediately
    - Trigger GCS cleanup (delete audio + transcription from cloud)
    - Display transcription for editing
  - Modify `saveTranscription()` to overwrite local encrypted file only (no GCS interaction)
  - Add biometric prompt on app launch (10-hour validity)
- `app/src/main/java/com/transcriber/config/Config.java` - Add encryption constants (key alias, timeout)
- `app/build.gradle` - Add `androidx.biometric` dependency
- `app/src/main/AndroidManifest.xml` - Add biometric permission
- `app/src/main/res/xml/backup_rules.xml` - Explicitly exclude encrypted files from backup

### Files to Create
- `app/src/main/java/com/transcriber/security/EncryptionManager.java` - Keystore and encryption logic (AES-256-GCM)
- `app/src/main/java/com/transcriber/security/BiometricAuthHelper.java` - Biometric prompt wrapper (10-hour validity)
- `app/src/main/java/com/transcriber/security/FileMetadataManager.java` - UUID to filename mapping (encrypted metadata storage)

### Files to Delete
None (encrypted files replace plaintext files)

### Security Benefits
✅ All PHI encrypted with AES-256-GCM (NIST approved)
✅ Hardware-backed encryption keys (if TEE available)
✅ Biometric/PIN required to access data
✅ Filenames obfuscated (no PHI exposure)
✅ Authenticated encryption prevents tampering
✅ HIPAA compliance for mobile ePHI
✅ Protection against rooted devices, ADB backup extraction

### Performance Considerations
- **Encryption overhead:** ~1-5ms per file (negligible for typical file sizes)
- **Audio files:** Larger files (1-10 MB) may take 10-100ms to encrypt (acceptable for between-appointment workflow)
- **Audit log appends:** Re-encryption needed on each append - acceptable for short-term logs with 30-day retention
- **Audit log retention:** 30 days (configurable) - HIPAA requires 6 years for formal medical records, but this is short-term dictation tool; export function allows saving to external system before auto-cleanup
- **UI responsiveness:** Perform encryption/decryption on background thread (AsyncTask or Coroutine)

### Implementation Summary (Completed 2026-01-27)

**Files Created:**
- ✅ `EncryptionManager.java` - AES-256-GCM encryption with Android Keystore
  - Separate binary and text file encryption methods
  - Hardware-backed encryption keys
- ✅ `BiometricAuthHelper.java` - Biometric authentication wrapper
- ✅ `FileMetadataManager.java` - UUID to filename mapping with encrypted metadata

**Files Modified:**
- ✅ `MainActivity.java` - Two-stage save workflow, biometric on launch, binary encryption for audio
- ✅ `FileManager.java` - Encrypted transcription save/load methods
- ✅ `AuditLogger.java` - Encrypted audit log with 30-day retention
- ✅ `Config.java` - Added encryption constants
- ✅ `build.gradle` - Added androidx.biometric dependency
- ✅ `AndroidManifest.xml` - Added biometric permission
- ✅ `backup_rules.xml` - Excluded PHI directories from backup
- ✅ `data_extraction_rules.xml` - Excluded PHI directories from device transfer
- ✅ `file_paths.xml` - Added cache-path for audit log export

**Key Features Implemented:**
- ✅ AES-256-GCM authenticated encryption for all PHI
- ✅ Biometric authentication on app launch (10-hour validity)
- ✅ UUID-based filenames with encrypted metadata mapping
- ✅ Two-stage save: auto-save on transcription return + manual save for edits
- ✅ Immediate GCS cleanup (Stage 1) prevents stranded files
- ✅ Binary encryption for audio files (preserves WAV format)
- ✅ Text encryption for transcriptions (UTF-8 safe)
- ✅ Encrypted audit log with export function
- ✅ Simple file deletion (encryption makes overwrite unnecessary)

**Testing Completed:**
- ✅ Audio recording encryption/decryption
- ✅ Transcription save/load with encryption
- ✅ File list display with patient names (from metadata)
- ✅ Two-stage save workflow (auto + manual)
- ✅ Audit log export via Android share chooser
- ✅ Biometric authentication flow

**Security Benefits Achieved:**
- ✅ All PHI encrypted with NIST-approved algorithm
- ✅ Hardware-backed encryption keys (Android Keystore)
- ✅ Filenames obfuscated (no PHI exposure in filesystem)
- ✅ Authenticated encryption prevents tampering
- ✅ HIPAA compliance for mobile ePHI storage
- ✅ Protection against ADB backup extraction

---

## SECURE DELETION: WHY SIMPLE DELETE IS SUFFICIENT

### The 3-Pass Overwrite Myth on Modern Storage

**Historical Context (HDD Era):**
- 3-pass overwrite (Gutmann method) designed for 1990s spinning hard drives
- Magnetic media could reveal "ghosts" of overwritten data using specialized equipment
- Multiple overwrites with different patterns reduced recovery probability

**Modern Reality (Flash Storage in Android):**
Android devices use eMMC/UFS flash memory, which fundamentally breaks the assumptions of overwrite-based deletion:

| Issue | Why 3-Pass Fails |
|-------|------------------|
| **Wear Leveling** | Flash controller distributes writes across cells to prevent wear; you don't control where data physically goes |
| **File System Abstraction** | Writing to same file path doesn't guarantee overwriting same physical NAND cells |
| **Garbage Collection** | Old data may remain in unmapped blocks even after "overwrite" |
| **Translation Layer** | FTL (Flash Translation Layer) makes logical-to-physical address mapping invisible to OS |
| **Increased Wear** | Multiple writes accelerate flash degradation without improving security |

**Conclusion:** 3-pass overwrite on flash storage is **security theater** - provides false sense of security while actually harmful (increases wear).

### Our Encryption-Based Approach

**For Encrypted Files (.enc):**
```java
// This is cryptographically sufficient
encryptedFile.delete();
```
- **Why it works:** Without the AES-256 key (stored in hardware Keystore), remnant data is cryptographically useless
- AES-256-GCM with random IV means same plaintext produces different ciphertext each time
- Even if flash controller leaves copies in unmapped blocks, they cannot be decrypted without the key
- **Attack scenario:** Attacker would need to:
  1. Extract device storage chip
  2. Use specialized equipment to read unmapped NAND blocks
  3. Reconstruct deleted ciphertext
  4. STILL can't decrypt without Keystore-protected key (hardware TEE makes this nearly impossible)

**For Temporary Plaintext Files (audio recording before encryption):**
```java
File tempWav = recordAudio();      // Plaintext exists for 1-5 seconds
File encrypted = encryptFile(tempWav);
tempWav.delete();                   // Simple delete is acceptable
```
- **Risk window:** Extremely brief (1-5 seconds between recording stop and encryption)
- **Android sandbox:** Other apps cannot access internal storage during this window
- **File system behavior:** May immediately reuse blocks for encrypted file
- **TRIM command:** Android periodically marks deleted blocks for erase
- **Acceptable risk:** Brief exposure + sandboxing = practical security for this use case

**Optional Enhancement (Minimal Benefit):**
```java
// Single overwrite with random data before delete
// Provides marginal benefit given flash architecture
FileOutputStream fos = new FileOutputStream(tempWav);
byte[] random = new byte[(int) tempWav.length()];
new SecureRandom().nextBytes(random);
fos.write(random);
fos.close();
tempWav.delete();
```
- **Benefit:** Slightly reduces recovery window if attacker has physical access during 1-5 second gap
- **Cost:** Extra I/O increases flash wear, adds latency
- **Recommendation:** Not necessary given app sandbox and brief exposure

### Summary

| File Type | Deletion Method | Security Rationale |
|-----------|----------------|-------------------|
| **Encrypted files (.enc)** | `file.delete()` | Encryption renders data useless; 3-pass overwrite ineffective on flash |
| **Temp plaintext (audio)** | `file.delete()` immediately after encryption | 1-5 second exposure + app sandbox = acceptable risk |
| **Audit logs** | Re-encrypt after deletion from log | Data never exists unencrypted on disk |

**Key Insight:** Encryption makes secure deletion simple - protect the key, not the deleted file remnants.

---

## TESTING STRATEGY

### Firebase Authentication Testing
- [ ] Google sign-in flow completes successfully on first launch
- [ ] User email displayed in UI after authentication
- [ ] Session persists after app restart
- [ ] Token refresh works before expiration
- [ ] Backend API rejects invalid/expired tokens
- [ ] Backend API logs requests with user email for audit trail
- [ ] Rate limiting prevents abuse per user
- [ ] Sign-out clears session properly

### File Encryption Testing
- [ ] New transcriptions saved as encrypted files
- [ ] Encrypted files decrypt correctly
- [ ] Biometric prompt appears when accessing encrypted data
- [ ] Wrong key/corrupted file shows appropriate error
- [ ] Migration encrypts all existing files
- [ ] Secure deletion removes files completely
- [ ] File listing shows user-friendly names (not UUIDs)
- [ ] Export audit log decrypts successfully

### Integration Testing
- [ ] End-to-end: Record → Upload (Firebase auth) → Transcribe → Save (encrypted)
- [ ] App works offline (no crashes from Firebase calls)
- [ ] Large audio files (>10 MB) process successfully
- [ ] Battery/memory usage acceptable with encryption

### Security Testing
- [ ] APK decompilation doesn't reveal credentials
- [ ] Encrypted files unreadable without key
- [ ] Android Keystore requires biometric/PIN
- [ ] Backend API logs all requests with user ID
- [ ] No PHI in filenames or logs

---

## DEPENDENCIES & PREREQUISITES

### Firebase Setup (Task 1)
- Firebase project created: `transcriber-477917`
- Authentication provider enabled: Google Sign-In (Anonymous skipped - not needed for medical accountability)
- Cloud Functions deployed (DECISION: JavaScript Cloud Functions - 5-minute recordings fit within 540s timeout, simpler than Cloud Run)
- Service account moved to server-side (stored in Cloud Secret Manager)
- `google-services.json` downloaded and added to `.gitignore`

### Android SDK Requirements (Task 2)
- `androidx.biometric:biometric:1.1.0` - Biometric authentication
- `androidx.security:security-crypto:1.1.0` - Optional: EncryptedSharedPreferences for metadata
- Minimum SDK: API 23 (Android 6.0) for Keystore with user authentication

### Gradle Dependencies to Add
```gradle
// Firebase
implementation platform('com.google.firebase:firebase-bom:32.7.0')
implementation 'com.google.firebase:firebase-auth'
implementation 'com.google.firebase:firebase-analytics'
implementation 'com.google.android.gms:play-services-auth:20.7.0' // Google Sign-In

// HTTP Client (for backend API calls)
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'

// Biometric
implementation 'androidx.biometric:biometric:1.1.0'

// Encrypted SharedPreferences (optional)
implementation 'androidx.security:security-crypto:1.1.0'
```

---

## IMPLEMENTATION ORDER

### Recommended Sequence
1. **Task 2 (File Encryption) FIRST**
   - Reason: Secures data immediately, no external dependencies
   - Can be developed and tested locally
   - Lower risk, smaller scope

2. **Task 1 (Firebase Auth) SECOND**
   - Reason: Requires backend development, more complex
   - Benefits from encrypted data already in place
   - User authentication can gate access to encryption keys

### Why This Order?
- Encrypting files first ensures PHI is protected even if Firebase migration takes longer
- Firebase auth can later be integrated with encryption (biometric unlocks tied to Firebase user)
- Independent tasks can be parallelized if multiple developers available

---

## ROLLBACK PLAN

### If Firebase Migration Fails
**Note:** Project directory backed up already (good practice)
- Keep embedded credentials in separate git branch as emergency fallback (DO NOT merge to main)
- Backend API down → Fallback to direct Google Cloud SDK calls (development/testing only, not for production)
- Monitor backend uptime (set up Cloud Monitoring alerts for Cloud Functions)

### If Encryption Causes Issues
- **During Testing:** Thoroughly test encryption/decryption on multiple devices before production
- **Encryption Errors:** Log all encryption failures to audit trail for debugging
- **Data Recovery:**
  - Short-term transcriptions: Not critical (already transferred to EMR)
  - Audit log: Export periodically before 30-day retention expires
  - No recovery utility needed - files are short-term storage only
- **Keystore Issues:** Rare but possible (device upgrade, OS update) - acceptable risk for short-term dictation tool

---

## COMPLIANCE CHECKLIST

### HIPAA Requirements
- [x] Encryption at rest (Task 2)
- [x] User authentication (Task 1)
- [x] Audit trail (already implemented, encrypt in Task 2)
- [x] Access controls (Firebase auth + biometric)
- [ ] Data backup encryption (update backup_rules.xml)
- [ ] Secure communication (already HTTPS to Google Cloud)
- [ ] Session timeout (implement in Task 1)

### Security Best Practices
- [x] No credentials in APK (Task 1)
- [x] Principle of least privilege (service account on server only)
- [x] Defense in depth (encryption + authentication + audit)
- [x] Secure key storage (Android Keystore)
- [x] Input validation (backend API)
- [x] Rate limiting (backend API)

---

## ESTIMATED EFFORT (Not Timeline)

### Task 1: Firebase Authentication
- Firebase setup: 2-4 hours
- Android client changes: 8-12 hours
- Backend API development: 16-24 hours (Cloud Functions) or 24-40 hours (Cloud Run)
- Testing: 8-12 hours
- **Total:** 34-88 hours depending on backend choice

### Task 2: File Encryption
- EncryptionManager implementation: 8-12 hours
- FileManager modifications: 6-10 hours
- Biometric integration: 4-6 hours
- Migration logic: 6-8 hours
- Testing: 8-12 hours
- **Total:** 32-48 hours

---

## SUCCESS CRITERIA

### Task 1 (Firebase Auth)
✅ APK does not contain `google_credentials.json`
✅ Users can authenticate anonymously or via Google
✅ Backend API successfully proxies transcription requests
✅ All API calls include valid Firebase ID token
✅ Security audit confirms no credential exposure

### Task 2 (File Encryption)
✅ All new files saved as `.enc` with AES-256-GCM
✅ Biometric/PIN required to decrypt files
✅ Existing unencrypted files migrated successfully
✅ No plaintext PHI on disk
✅ Audit logs confirm encryption events
✅ HIPAA security assessment passes

---

## DECISIONS MADE - READY FOR IMPLEMENTATION

All architectural decisions finalized:
1. ✅ **Implementation order:** Encryption first (Task 2), then Firebase authentication (Task 1)
2. ✅ **Backend platform:** Cloud Functions (JavaScript) - 5-minute recordings fit within 540s timeout, simpler deployment
3. ✅ **Authentication:** Google Sign-In only (skip Anonymous for medical accountability)
   - Session persists indefinitely (weeks/months) - no timeout between patients
4. ✅ **Key management:** Android Keystore with biometric authentication on app launch, 10-hour validity window (full workday)
5. ✅ **Filename strategy:** UUIDs with encrypted metadata file (doctor sees patient name/DOB in UI)
6. ✅ **Two-stage save workflow:**
   - Auto-save: Encrypted file saved immediately on transcription return + GCS cleanup (data protection)
   - Manual save: Doctor edits, presses "Save Transcription" to update local file only
   - GCS cleanup in Stage 1 prevents stranded cloud files, recording can be remade if needed
7. ✅ **Migration approach:** Not needed - fresh installation only, no existing data to migrate
8. ✅ **Secure deletion:** Standard `file.delete()` for encrypted files (3-pass overwrite ineffective on flash storage)
9. ✅ **Audit log retention:** 30 days with export function for auditor review (logs stay small)
10. ✅ **GCS cleanup timing:** Immediate (Stage 1) - prevents stranded files, reduces cloud storage costs

**Next Action:** Begin Task 2 (File Encryption) implementation with detailed code-level planning.
