# Vimivo Security Threat Model & Audit

Audit date: 2026-10-15.
Reference standards: **OWASP MASVS / MASTG**, Desktop Application Security Best Practices.

---

## 1. Threat Model & Assets

Vimivo is an offline-first, privacy-first local media player. The core assets to protect are:
- User's local media files (read-only access; original files must NEVER be overwritten, corrupted, or deleted).
- Personal database (playlists, playback history, resume points, user settings).
- User credentials for authorized network storage (WebDAV, SMB, SFTP).
- Application execution integrity (prevent arbitrary code execution from malicious media files or playlist files).

---

## 2. Ingestion & File Processing Security

- **Shell Injection Prevention**:
  - Desktop media inspection runs in-process through the bundled libmpv (`MpvMediaInspector`); no `ffprobe`/`ffmpeg` subprocess is started. Remaining process launches (e.g. `Process()` on Apple platforms) use argument arrays, NEVER shell string concatenation (`/bin/sh -c ...`).
  - Inputs (file paths, stream URLs) are passed directly as distinct arguments.
  - Metadata extraction is time-bounded (5 seconds max per file) to prevent hangs on crafted malicious files.
- **Path Traversal Protection**:
  - Playlist imports (`M3U`, `JSON`, backup restores) sanitize relative and absolute paths.
  - No writes occur outside designated app data or cache directories (`%LOCALAPPDATA%\Vimivo`, `~/Library/Application Support/Vimivo`, `~/.local/share/vimivo`, Android internal storage, iOS app sandbox container).
- **Corrupt File Isolation**:
  - Parsing exceptions from malformed ID3, Vorbis, MP4, or Matroska headers are caught gracefully and logged via `SafeLogger`. The application marks the item as unreadable without crashing.

---

## 3. Credential Storage Security

Plaintext storage of passwords or tokens in SQLite, preferences, or logs is strictly prohibited.

- **Android**: Encrypted SharedPreferences / Android Keystore backed vault (`NetworkCredentialVault`).
- **iOS & macOS**: Apple Keychain Services (`kSecClassGenericPassword`) with accessibility `kSecAttrAccessibleAfterFirstUnlock`.
- **Windows**: Windows Credential Manager (`WindowsCredentialStore`) using Win32 `Advapi32` APIs (`CredWriteW`, `CredReadW`, `CredDeleteW`) with in-memory fallback.
- **Linux**: Freedesktop Secret Service (`LinuxCredentialStore`) via `libsecret-1.so.0` with in-memory fallback.
- **Backups**: JSON backup export explicitly filters out network credentials and authorization headers.

---

## 4. Network Security & TLS

- Direct network streams require valid TLS certificate verification for HTTPS streams.
- Cleartext HTTP traffic is restricted or flagged.
- Sensitive query parameters (`token`, `auth`, `key`, `password`, `secret`) and `Bearer` authorization headers are sanitized and redacted in any diagnostic output via `SafeLogger`.
