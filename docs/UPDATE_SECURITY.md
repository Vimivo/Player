# Vimivo Secure Update Architecture & Threat Model

> [!NOTE]
> This document formalizes Vimivo's software update security model, threat mitigation strategies, and platform-specific delivery pipelines in accordance with Milestone 32.

---

## 1. Threat Model & Security Controls

| Threat Vector | Attack Scenario | Vimivo Mitigation | Code Enforcement |
| :--- | :--- | :--- | :--- |
| **Rollback / Downgrade Attack** | Attacker serves an older, vulnerable release manifest to force application downgrade. | Client-side SemVer 2.0 comparison (`SemanticVersion.isNewerThan`) blocks older versions. | `UpdateChecker.evaluateManifest(..., preventDowngrade = true)` returns `DowngradeBlocked`. |
| **Payload Tampering** | Attacker substitutes binary artifact on transit network or CDN. | Mandatory SHA-256 digest validation matching exact 64-character hexadecimal format. | `UpdateChecker.validateSha256()`. Downloaded payload verified against manifest hash. |
| **Credential / Token Leakage** | Attacker crafts update URL with query parameter exfiltrating user session tokens. | Strict URL sanitization rejecting any URL with tokens, secrets, or basic auth. | `UpdateChecker.isSecureManifestUrl()` enforces HTTPS and bars `token=`, `password=`, etc. |
| **Wrong Architecture Injection** | Attacker feeds x64 binary to arm64 user, causing translation failure or crash. | Strict architecture matching (`arm64`, `x64`, `universal`) against client architecture. | `UpdateManifest.findArtifactFor()` filters platform and CPU architecture strictly. |
| **Insecure Transport** | Plaintext HTTP intercept allows man-in-the-middle manifest replacement. | Strict HTTPS enforcement; HTTP URLs are instantly rejected as security errors. | `UpdateChecker.isSecureManifestUrl()` enforces `https://` prefix. |

---

## 2. Platform Delivery Architectures

### 2.1 Android (Google Play & Sideload)
- **Google Play Distribution**: Leverages Google Play Core In-App Updates.
  - *Flexible Updates*: Non-critical updates download in the background while the user continues playing media.
  - *Immediate Updates*: Critical security patches prompt a full-screen blocking update flow.
- **Sideload Distribution**: Direct APK downloads verify developer signing certificate fingerprint and match SHA-256 against `release-manifest.json`.

### 2.2 iOS / iPadOS
- **App Store Exclusivity**: Updated exclusively via the official Apple App Store delivery pipeline.
- No dynamic code loading, arbitrary binary downloads, or sandbox bypasses.

### 2.3 macOS (Direct Distribution)
- **Code Signing & Notarization**: Every release DMG is signed with Apple Developer ID Application certificate and notarized by Apple (`stapler staple`).
- **Update Checking**: Desktop update engine queries `docs/release-manifest.json` over HTTPS, verifying SHA-256 before directing user to mount and install the disk image.

### 2.4 Windows (MSI & Inno Setup EXE)
- **Authenticode Signature**: Binaries and installers signed with an official EV Code Signing certificate.
- **Transactional Upgrades**: Upgrades execute over existing installations, preserving SQLite databases and user settings under `%LOCALAPPDATA%\Vimivo\`.

### 2.5 Linux (DEB, RPM, Flatpak)
- **Distribution Package Managers**: DEB and RPM packages distributed via signed repositories using GPG keys.
- **Flatpak**: Sandbox-isolated updates managed directly via Flathub and Flatpak runtimes (`flatpak update`).

---

## 3. Database Migration Safety & Upgrades

- **Schema Versioning**: SQLite persistence tracks schema versions via `PRAGMA user_version`.
- **Pre-Migration Snapshot**: Major migrations create a local backup copy of `vimivo.db` before applying migration statements.
- **Forward-Only Constraint**: Older application binaries encountering a newer database schema abort cleanly with `ERR_DB_MIGRATION_FAILED` rather than silently wiping tables.
