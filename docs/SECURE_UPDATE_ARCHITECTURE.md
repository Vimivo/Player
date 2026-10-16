# Vimivo — Secure Update Architecture

This document specifies the technical architecture, security guarantees, cryptographic verification pipeline, and download staging mechanisms for application updates in Vimivo.

---

## 1. Core Principles & Privacy Guarantees

1. **User Control**: Updates are user-initiated via the UI ("Check for Updates") or governed by explicit user settings. Vimivo never performs silent background installations.
2. **Offline-First Playback**: Local playback, playlists, and settings operate entirely offline. Update checking failures (offline network, DNS failure, server downtime) never impede media playback.
3. **Zero Telemetry**: Update checks do not send device identifiers, media library contents, listening habits, or telemetry tokens.
4. **No Untrusted Code Execution**: Vimivo never executes arbitrary downloaded shell scripts or binaries without multi-layered cryptographic and identity validation.

---

## 2. Release Manifest Schema

The official release metadata is published as a JSON manifest signed by authorized release engineering keys.

```json
{
  "product": "Vimivo",
  "version": "1.0.0",
  "releaseDate": "2026-10-16",
  "releaseNotesUrl": "https://raw.githubusercontent.com/Vimivo/Player/main/docs/RELEASE_NOTES_1.0.0.md",
  "changelogSummary": "Vimivo 1.0.0 release with verified multiplatform playback.",
  "minSupportedDatabaseVersion": 1,
  "signature": "30450221008f...",
  "signingKeyId": "vimivo-release-key-2026",
  "artifacts": [
    {
      "platform": "macos",
      "format": "dmg",
      "filename": "Vimivo-1.0.0.dmg",
      "downloadUrl": "https://github.com/Vimivo/Player/releases/download/v1.0.0/Vimivo-1.0.0.dmg",
      "sizeBytes": 150423483,
      "sha256": "42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8",
      "architecture": "arm64",
      "minOsVersion": "12.0",
      "signature": "3045022100...",
      "signingKeyId": "vimivo-release-key-2026"
    }
  ]
}
```

---

## 3. Multi-Layered Verification Pipeline

Every update candidate is subjected to a strict 6-stage verification gate before any binary is accepted:

```
[ Manifest Retrieval (HTTPS) ]
              │
              ▼
   Stage 1: URL & Credential Audit
     - Must begin with https://
     - Rejects query tokens, passwords, credentials
              │
              ▼
   Stage 2: Version Ordering & Downgrade Prevention
     - Semantic version ordering (SemanticVersion.isNewerThan)
     - Rejects older releases (DowngradeBlocked)
              │
              ▼
   Stage 3: Database Compatibility Pre-Flight
     - Checks manifest.minSupportedDatabaseVersion <= currentDatabaseVersion
     - Rejects breaking schema jumps (IncompatibleDatabaseVersion)
              │
              ▼
   Stage 4: Platform & Strict Architecture Matching
     - Matches OS (macos, windows, linux, android)
     - Strict CPU architecture match (arm64, x64)
     - Prevents cross-architecture emulation failures (ArchitectureMismatch)
              │
              ▼
   Stage 5: Cryptographic Digest & Signature Audit
     - Enforces 64-character hexadecimal SHA-256 format
     - Validates signingKeyId against trusted key set
     - Cryptographically verifies digital signature against artifact metadata
              │
              ▼
   Stage 6: Safe Download Staging & Atomic Finalization
     - Bounded file size (<= 500 MB)
     - Free disk space check (+50 MB headroom)
     - Staged in temporary `.part` file
     - Streaming SHA-256 calculation during download
     - Atomic rename (`Files.move` with ATOMIC_MOVE)
```

---

## 4. Download Staging & Failure Recovery

Managed downloads on desktop platforms are handled by `DesktopUpdateDownloadManager`:
- **Staging**: Artifacts download to `<filename>.part` in a designated staging directory.
- **Verification**: SHA-256 is computed concurrently with network reading.
- **Tamper Detection**: If computed SHA-256 does not match `artifact.sha256`, a `SecurityException` is thrown, the `.part` file is deleted immediately, and no final file is created.
- **Interruption & Cancellation**: If interrupted or cancelled by the user, the `.part` file is purged. No corrupted or incomplete artifacts remain on disk.
- **Atomic Move**: On digest verification success, the file is atomically renamed to its final target path.
