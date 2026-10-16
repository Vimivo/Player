# Vimivo Reliability & Diagnostics Architecture

> [!NOTE]
> Vimivo is a free, privacy-first, offline-first multimedia player. This architecture establishes post-launch reliability, structured local diagnostics, and crash handling without introducing telemetry, cloud logging, or account dependencies.

---

## 1. Core Reliability Principles

1. **Local-First & Offline-First**: Core media playback never depends on internet connectivity or remote authentication.
2. **Zero Telemetry / Zero Third-Party Trackers**: No analytics SDKs (Firebase, Sentry, TelemetryDeck, etc.) are embedded. Diagnostics remain strictly on-device until the user explicitly requests an export.
3. **Bounded Resource Footprint**: Local diagnostic logs and ring buffers have hard capacity ceilings to prevent disk space exhaustion or memory bloat.
4. **Resilient Native Integration**: Native multimedia engines (`libmpv`, `Media3`, `AVPlayer`, `LibVLC`) are treated as external resources with strict lifecycle ownership, isolation, and fallback handling.

---

## 2. Standardized Error Classification Model

Errors across all five platforms (Android, iOS, macOS, Windows, Linux) are classified into 20 standardized, non-leaking categories:

| Error Category | Code Identifier | Recoverable | Typical Component | User-Facing Action |
| :--- | :--- | :--- | :--- | :--- |
| `APPLICATION_STARTUP` | `ERR_STARTUP_*` | No | App Lifecycle | Restart application; check log |
| `MEDIA_NOT_FOUND` | `ERR_MEDIA_NOT_FOUND` | Yes | MediaLibrary | Check file path / remount drive |
| `MEDIA_UNSUPPORTED` | `ERR_MEDIA_UNSUPPORTED` | Yes | PlaybackEngine | Verify file codec compatibility |
| `MEDIA_CORRUPT` | `ERR_MEDIA_CORRUPT` | Yes | PlaybackEngine | Skip corrupted media container |
| `PLAYBACK_INITIALIZATION` | `ERR_PLAYBACK_INIT_FAILED`| Yes | PlaybackEngine | Automatic fallback or retry |
| `AUDIO_OUTPUT` | `ERR_AUDIO_OUTPUT_*` | Yes | AudioRenderer | Select alternative audio device |
| `VIDEO_RENDERING` | `ERR_VIDEO_RENDER_*` | Yes | VideoRenderer | Switch surface / software decode |
| `PLAYBACK_INTERRUPTED` | `ERR_PLAYBACK_INTERRUPT`| Yes | QueueController | Resume from saved checkpoint |
| `DATABASE` | `ERR_DATABASE_*` | Yes | SqliteDatabase | Retry transaction; verify lock |
| `DATABASE_MIGRATION` | `ERR_DB_MIGRATION_FAILED`| No | SqliteDatabase | Safe abort; preserve raw DB |
| `QUEUE_PERSISTENCE` | `ERR_QUEUE_*` | Yes | QueueRepository | Reload queue from last commit |
| `FILE_PERMISSION` | `ERR_FILE_PERMISSION_*` | Yes | FileSystem | Request user folder access grant |
| `NETWORK_TIMEOUT` | `ERR_NETWORK_TIMEOUT` | Yes | NetworkClient | Exponential backoff retry |
| `NETWORK_AUTHENTICATION`| `ERR_NETWORK_AUTH_*` | Yes | NetworkClient | Prompt for updated credentials |
| `NATIVE_DEPENDENCY` | `ERR_NATIVE_DEP_MISSING` | No | NativeAdapter | Install platform runtime/library |
| `UPDATE_CHECK` | `ERR_UPDATE_CHECK_*` | Yes | UpdateChecker | Verify connection; retry check |
| `UPDATE_VERIFICATION` | `ERR_UPDATE_VERIFY_*` | Yes | UpdateChecker | Abort update; notify maintainers |
| `UPDATE_INSTALLATION` | `ERR_UPDATE_INSTALL_*` | Yes | UpdateInstaller | Keep current running version |
| `BACKUP_RESTORE` | `ERR_BACKUP_RESTORE_*` | Yes | BackupManager | Rollback transaction on failure |
| `UNKNOWN` | `ERR_UNKNOWN` | Yes | Core | Record structured diagnostic log |

---

## 3. Structured Bounded Local Logging

Vimivo uses `StructuredLogger` (`dev.vimivo.core.logging.StructuredLogger`) and `DesktopLogFileAppender`:

- **Severity Levels**: `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`.
- **In-Memory Ring Buffer**: Bounded to 250 entries maximum (`StructuredLogBuffer`). Oldest entries are discarded when full.
- **Disk File Appender**:
  - Located in the platform-appropriate user diagnostic directory (never inside application bundle).
  - Maximum file size: 1 MB per log file.
  - Rotation: Circular backup index (`vimivo.log`, `vimivo.log.1`, `vimivo.log.2`). Oldest backup deleted when limit reached.
- **Spam Filtering**: Per-frame video presentation and high-frequency audio position updates are strictly barred from disk logging.

---

## 4. Privacy-Safe Redaction Engine

All log messages and metadata maps pass through `SafeLogger.sanitize()`:
- **Filesystem Paths**: Redacts home directory usernames across macOS (`/Users/<user>/`), Linux (`/home/<user>/`), and Windows (`C:\Users\<user>\`) to `~/user/...`.
- **Network URLs**: Strips query parameters containing `token`, `password`, `key`, `auth`, `signature`, `sig`, `api_key`.
- **Authorization Headers**: Redacts `Bearer <token>` and `Basic <token>` headers to `[REDACTED]`.
- **Metadata Filtering**: Replaces sensitive key-value pairs (`auth_token`, `api_key`, `secret`) with `[REDACTED]`.

---

## 5. Crash Diagnostics and Platform Boundaries

- **In-Process Boundary**: Fatal signals (`SIGSEGV`, `SIGBUS`, unhandled native aborts in `libmpv`) are handled by operating system crash dump mechanisms. In-process handlers avoid allocating memory or making complex native calls during crash handling.
- **Android Crash Handling**: Uncaught Java/Kotlin exceptions caught via `Thread.setDefaultUncaughtExceptionHandler`, flushing recent memory logs to local private sandbox storage before process exit.
- **Desktop Fatal Error Handling**: JVM fatal errors output standard crash logs (`hs_err_pid.log`) in user-owned diagnostic directories.
- **No Automatic Dump Uploads**: Crash logs and diagnostic exports are only exported when explicitly initiated by the user.
