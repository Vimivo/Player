# Vimivo Privacy Audit

Audit date: 2026-10-15.

---

## 1. Privacy Principles & Guarantees

Vimivo is built from the ground up as a zero-surveillance, offline-first media player:
- **Zero Ads**: No ad networks, ad SDKs, or ad identifiers.
- **Zero Tracking & Analytics**: No Google Analytics, Firebase, Sentry, Mixpanel, Crashlytics, or Telemetry SDKs.
- **No Mandatory Account**: No sign-in, login, account creation, or backend user profiles.
- **Offline-First**: All core playback, metadata parsing, playlist storage, and history functions work completely offline without internet connectivity.
- **Zero Library Uploads**: Library metadata, song titles, folder structures, and media files are never transmitted to any external server.

---

## 2. Network Request Audit

An exhaustive code audit confirmed that external network requests only occur upon explicit user direction:
1. **User-Initiated Streaming**: When the user opens an explicit HTTP, HTTPS, or RTSP media URL (e.g., internet radio, direct video stream).
2. **Authorized Network Storage**: When the user explicitly configures a personal WebDAV, SMB, or SFTP server in Settings/Network to stream their own remote files.

No background "phone home" requests, licensing pings, or automatic update checks exist.

---

## 3. Diagnostic Log Redaction (`SafeLogger`)

To protect user privacy if logs or diagnostics are inspected:
- **Filesystem Paths**:
  - macOS: `/Users/<username>/...` -> `~/user/...`
  - Linux: `/home/<username>/...` -> `~/user/...`
  - Windows: `C:\Users\<username>\...` -> `C:\Users\user\...`
- **Network Credentials & Tokens**:
  - Sensitive URL parameters (`token`, `auth`, `key`, `secret`, `password`, `sig`) are replaced with `[REDACTED]`.
  - HTTP `Authorization: Bearer <token>` and `Basic <token>` headers are sanitized to `Bearer [REDACTED]` and `Basic [REDACTED]`.

---

## 4. Permissions & Sandboxing

- **Android**:
  - `READ_MEDIA_AUDIO` / `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` requested only in context with clear explanations of purpose.
  - `POST_NOTIFICATIONS` requested only on Android 13+ to support media playback notifications and lock screen media controls.
- **iOS**:
  - Operates inside the native iOS App Sandbox.
  - Media importing uses the system-managed Files picker (`UIDocumentPickerViewController`) and stores user consent via Security-Scoped Bookmarks.
- **macOS / Windows / Linux**:
  - Standard user-level permissions; persistent database stored strictly in per-user application support directories (`~/Library/Application Support/Vimivo`, `%LOCALAPPDATA%\Vimivo`, `~/.local/share/vimivo`).
