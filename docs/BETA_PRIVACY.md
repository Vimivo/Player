# Vimivo Beta Privacy & Data Safety Pledge

## 1. Our Commitment to Your Privacy

Vimivo was conceived and engineered with a fundamental principle:
**Your media, listening habits, and device information belong solely to you.**

We reject the modern software trend of bundling invasive analytics, user trackers, crash reporting surveillance, and mandatory account systems into local media players.

---

## 2. Zero Telemetry & Zero Data Collection

- **No Analytics SDKs**: Vimivo contains zero third-party telemetry libraries (no Google Analytics, Firebase, Sentry, TelemetryDeck, or Mixpanel).
- **No Background Network Pings**: The application never connects to remote servers in the background to report heartbeats, active sessions, or device specs.
- **No Mandatory Accounts**: There is no login, registration, or profile creation.
- **No Advertising**: Vimivo is 100% ad-free and open-source.

---

## 3. What Stays on Your Device

All application state is stored strictly in local files on your physical machine or device storage:

| Data Type | Storage Location | Protection Guarantee |
| :--- | :--- | :--- |
| **Media Files** | Your local folders, external SSDs, USB drives | **Strictly Read-Only**. Never modified, converted, or deleted. |
| **Library Index** | Local SQLite database (`vimivo.db`) | Never uploaded or synced to external clouds without explicit user action. |
| **Playlists & Favorites** | Local SQLite database | Fully exportable via JSON/M3U; never transmitted to third parties. |
| **Playback History & Resume** | Local SQLite database | Maintained locally for your convenience; clearable at any time. |
| **User Settings** | Local settings file / DataStore | Preferences remain on-device. |

---

## 4. Privacy-Safe Diagnostic Reports

When troubleshooting issues during beta testing, Vimivo provides an exportable diagnostic report (`dev.vimivo.core.diagnostics.PrivacySafeDiagnostics`).

### Automatic Redaction Rules
1. **User Directories Redacted**: File paths containing your OS username (e.g., `/Users/alice/Music/...`, `/home/bob/...`, or `C:\Users\charlie\...`) are automatically sanitized to `~/user/...` or `C:\Users\user\...`.
2. **URL Tokens Stripped**: Sensitive URL query parameters (such as `token`, `auth`, `key`, `password`, `sig`) are stripped and replaced with `[REDACTED]`.
3. **No File Contents**: Media files, artwork binary streams, and playlist tracklists are strictly excluded from diagnostics.
4. **Bounded In-Memory Buffer**: Event logs are stored in a small, non-persistent in-memory ring buffer (maximum 150 events) and cleared upon application termination.

### User Consent & Verification
- Diagnostic generation is **strictly user-initiated** via Settings -> Export Diagnostics.
- Reports are saved to a local `.md` file chosen by the user.
- You can inspect the entire report in any text viewer before deciding whether to share it in a bug report.
- The exporter refuses to write to root system locations (`/System`, `/etc`, `C:\Windows`) for operating system safety.

---

## 5. How to Delete All Data

If you decide to leave the beta cohort, you can permanently wipe all Vimivo application data:
- **Android**: Clear Data in Android App Info, then uninstall.
- **macOS**: Remove `/Applications/Vimivo.app` and delete directory `~/Library/Application Support/Vimivo`.
- **Windows**: Uninstall via Add/Remove Programs and remove `%APPDATA%\Vimivo`.
- **Linux**: Remove package and delete `~/.local/share/vimivo` and `~/.config/vimivo`.

Your original music and video files will remain completely untouched.
