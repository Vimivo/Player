# Vimivo Privacy, Legal & Regulatory Review

## 1. Compliance Summary & Core Architecture

Vimivo is an uncompromisingly privacy-first, offline-first universal media player.
- **Zero Third-Party Trackers**: No Google Analytics, Firebase, Sentry, TelemetryDeck, Mixpanel, or advertising SDKs exist in the repository or compiled artifacts.
- **No Background Telemetry**: The application never transmits device telemetry, IP heartbeats, library contents, or listening habits to any remote server.
- **Zero Mandatory Accounts**: No registration, cloud synchronization, or user profiling.

---

## 2. Google Play Store Data Safety Declaration

Under Google Play Developer Policy requirements, the following answers represent Vimivo's verified behavior:

| Questionnaire Section | Official Declaration | Verification Evidence |
| :--- | :--- | :--- |
| **Does your app collect or share any user data?** | **No** | Verified in dependency graph and network audit. |
| **Is all user data encrypted in transit?** | **Yes** (when user explicitly authorizes HTTPS streams) | Network engine connects only on explicit user streaming. |
| **Do you provide a way for users to request data deletion?** | **Yes** | All data is stored locally in SQLite (`vimivo.db`) and deleted upon app uninstall or clearing app storage. |
| **Location Data** | **Not Collected** | Zero location APIs requested. |
| **Personal Info (Name, Email, Phone, Address)** | **Not Collected** | No account infrastructure exists. |
| **Financial / Payment Info** | **Not Collected** | Completely free and ad-free. |
| **Audio & Video Files** | **Not Collected / Not Uploaded** | Media files are indexed in a read-only manner on device. |
| **Device or other IDs** | **Not Collected** | No advertising IDs (`AD_ID`) requested. |

---

## 3. Apple App Store Privacy Nutrition Labels

Under Apple App Privacy requirements in App Store Connect:

| Category | Status | Details |
| :--- | :---: | :--- |
| **Data Used to Track You** | **None** | Vimivo does not link user data with third-party data for advertising or tracking. |
| **Data Linked to You** | **None** | No identity or account is associated with playback or device activity. |
| **Data Not Linked to You** | **None** | No diagnostics or telemetry are uploaded remotely. Diagnostics are strictly local and user-exported. |

---

## 4. Local Credential & Key Security

When users connect to authenticated network streams (HTTP/HTTPS with Basic/Bearer auth), credentials are saved using platform-native secure enclaves:
- **macOS / iOS**: Apple Keychain Services (`Security.framework`).
- **Windows**: Win32 Credential Manager via `Advapi32` (`CredWriteW`, `CredReadW`).
- **Linux**: Freedesktop Secret Service API via `libsecret-1.so.0`.
- **Android**: Android Keystore / Encrypted SharedPreferences.
- **Zero Plaintext Persistence**: Passwords and tokens are never stored in plain text in the SQLite database or configuration files.

---

## 5. Diagnostic Export Privacy Architecture

- Handled by `dev.vimivo.core.diagnostics.PrivacySafeDiagnostics`.
- Automatically strips filesystem usernames (`/Users/username` -> `~/user`).
- Automatically strips query parameters and URL tokens (`token=[REDACTED]`).
- Bounded in-memory ring buffer (150 entries maximum) cleared upon app exit.
- Strictly user-initiated export; blocks writing to root system locations (`/etc`, `/System`, `C:\Windows`).
