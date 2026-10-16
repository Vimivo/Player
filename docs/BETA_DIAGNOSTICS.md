# Vimivo Privacy-Safe Diagnostics Architecture

## 1. Overview

Vimivo implements a native, privacy-safe diagnostic subsystem across shared Kotlin Multiplatform code and platform targets:
- `shared/src/commonMain/kotlin/dev/vimivo/core/diagnostics/PrivacySafeDiagnostics.kt`
- `desktopApp/src/desktopMain/kotlin/dev/vimivo/desktop/diagnostics/DesktopDiagnosticExporter.kt`

This infrastructure allows beta testers and engineers to capture actionable crash and runtime diagnostics without compromising personal privacy.

---

## 2. Architectural Design

```
┌────────────────────────────────────────────────────────┐
│               Application Components                   │
│   (Playback Engine, Queue Controller, Database Sync)   │
└──────────────────────────┬─────────────────────────────┘
                           │ recordEvent() / recordError()
                           ▼
┌────────────────────────────────────────────────────────┐
│                   DiagnosticBuffer                     │
│  - Thread-safe Bounded In-Memory Ring Buffer (150 max) │
│  - Automated SafeLogger Sanitization                   │
│  - Strips /Users/<name>, /home/<name>, tokens, keys    │
└──────────────────────────┬─────────────────────────────┘
                           │ snapshotEvents() / snapshotErrors()
                           ▼
┌────────────────────────────────────────────────────────┐
│                   DiagnosticReport                     │
│  - System Metadata (OS, Arch, Platform, Engine)        │
│  - Formats to GitHub-ready Markdown or JSON            │
└──────────────────────────┬─────────────────────────────┘
                           │ exportToPath()
                           ▼
┌────────────────────────────────────────────────────────┐
│              DesktopDiagnosticExporter                 │
│  - Validates Destination Path Safety                   │
│  - Blocks Root System Locations (/System, /etc, etc.)  │
│  - Writes User-Requested .md File                      │
└────────────────────────────────────────────────────────┘
```

---

## 3. Data Schema & Redaction Rules

### 3.1 Metadata Captured
- **`appVersion`**: SemVer identity (e.g. `1.0.0-rc.1`).
- **`buildCommit`**: Git commit SHA (e.g. `3d77bde`).
- **`platform`**: Target platform name (`Compose Desktop (JVM)`, `Android`, `iOS`).
- **`osVersion`**: Operating system name and kernel version (e.g. `Mac OS X 14.6`, `Android 15`).
- **`architecture`**: CPU architecture (`aarch64`, `x86_64`, `arm64-v8a`).
- **`playbackBackend`**: Active multimedia engine (e.g. `libmpv 2.0.0 (JNA)`, `Media3 ExoPlayer 1.11.1`).
- **`generatedTimestampEpochMs`**: Millisecond Unix timestamp of report creation.

### 3.2 Automated Redaction
The subsystem routes all messages and stack traces through `SafeLogger`:
- **Paths**:
  - macOS: `/Users/username/Music/track.flac` ➔ `~/user/Music/track.flac`
  - Linux: `/home/username/Videos/movie.mkv` ➔ `~/user/Videos/movie.mkv`
  - Windows: `C:\Users\username\Music\track.mp3` ➔ `C:\Users\user\Music\track.mp3`
- **Authentication & Query Tokens**:
  - `https://stream.com/live.m3u8?token=xyz123&sig=abc` ➔ `https://stream.com/live.m3u8?token=[REDACTED]&sig=[REDACTED]`
  - Key-value credentials (`token=supersecret`) in exceptions ➔ `token=[REDACTED]`
- **Stack Traces**:
  - Sanitized line-by-line before insertion into report.

---

## 4. Protected System Directories

The exporter verifies the user-specified destination path against a strict list of protected OS directories to prevent accidental overwrite of system files:
- `/System`
- `/etc`, `/private/etc`
- `/bin`, `/sbin`
- `/usr`
- `/var/root`, `/private/var/root`
- `/var/db`, `/private/var/db`
- `C:\Windows`
- `C:\Program Files`

Any export attempt targeting these locations immediately returns a `Result.failure` with an actionable security warning.

---

## 5. Sample Output Format

```markdown
# Vimivo Privacy-Safe Diagnostic Report

> [!NOTE]
> This report is generated locally and contains zero user credentials, passwords,
> private keys, personal media files, or raw media library records.
> All filesystem usernames and URL authentication parameters have been redacted.

## System Information
- **Application Version**: `1.0.0-rc.1`
- **Build Commit**: `3d77bde`
- **Platform Target**: `Compose Desktop (JVM)`
- **OS Version**: `Mac OS X 14.6`
- **Architecture**: `aarch64`
- **Playback Backend**: `libmpv 2.0.0 (JNA)`
- **Generated Epoch MS**: `1730000000000`

## Recent Events (2)
- `[1730000000100]` **[STARTUP]**: Desktop player engine initialized
- `[1730000000200]` **[PLAYBACK]**: Loading media item at ~/user/Music/Song.flac

## Error Summaries (0)
*(No errors recorded)*
```
