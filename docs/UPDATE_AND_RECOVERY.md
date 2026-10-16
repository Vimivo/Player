# Vimivo — Update, Recovery & Incident Response Runbook

**Milestone**: 50  
**Target Release**: `1.0.0-rc.1`  
**Git Commit SHA**: `3924266`  
**Date**: 2026-10-16  

---

## 1. Application Update Architecture

Vimivo provides an offline-first update checking mechanism that preserves privacy:
- **No Background Polling Without Consent**: Update checks occur only on user request or when explicitly enabled in Settings (`settings.check_updates = true`).
- **Cryptographic Manifest Verification**:
  - The update manifest is signed with the Vimivo release authority private key using Ed25519.
  - The public key is baked into the application binary (`UpdateSecurity.kt`).
  - Downloads are checked against SHA-256 before prompting the user to install.
  - Verified in `UpdateSecurityTest.kt` and `DesktopUpdateServiceTest.kt`.
- **Zero Sideload Execution on Mobile**:
  - On Android and iOS, the update checker directs the user to the official store page (Google Play / App Store) rather than downloading arbitrary APK/IPA files.

---

## 2. Failure Recovery & Rollback Protocols

### 2.1 Database Migration Failure Recovery
1. **Symptom**: Application fails to launch with `SQLException` during database upgrade.
2. **Built-in Protection**: All forward migrations run inside an atomic SQL transaction (`BEGIN TRANSACTION` ... `COMMIT`). If any step throws an error, `ROLLBACK` executes automatically.
3. **Automatic Pre-Migration Backup**:
   - Before executing schema migration, `DesktopDatabase` copies `<dbname>.sqlite` to `<dbname>.sqlite.v<old_version>.bak`.
4. **Manual Recovery Steps**:
   ```bash
   # On macOS:
   cd ~/Library/Application\ Support/Vimivo/
   cp vimivo.sqlite.v5.bak vimivo.sqlite
   # On Windows:
   cd %APPDATA%\Vimivo
   copy vimivo.sqlite.v5.bak vimivo.sqlite
   # On Linux:
   cd ~/.local/share/vimivo/
   cp vimivo.sqlite.v5.bak vimivo.sqlite
   ```

### 2.2 Missing External Drive / Renamed Folder Recovery
1. **Symptom**: Media files appear grayed out or show "File Not Found".
2. **Behavior**: Vimivo marks `available = 0` but **never deletes** playback history, playlists, or bookmarks.
3. **Relinking Protocol**:
   - Connect the external drive or navigate to **Settings -> Library Roots**.
   - Select **Relink Missing Drive** / **Update Folder Path**.
   - Vimivo invokes `DesktopMissingMedia.relink(...)`, matching items by relative path and checksum, restoring availability without duplicate entries.

### 2.3 Binary Rollback Limitations
> [!WARNING]
> **Schema Downgrade Incompatibility**:  
> SQLite databases migrated to Schema Version 6 cannot be opened by older application builds expecting Schema Version 1 through 5. When rolling back an application binary, restore the corresponding database backup file (`vimivo.sqlite.v<target>.bak`).

---

## 3. Emergency Incident Response Runbook

### Scenario A: Critical Security Vulnerability in Native Dependency (e.g., FFmpeg / libmpv)
1. **Triage**: Assess whether the vulnerability is exploitable via crafted local media files.
2. **Hotfix Preparation**:
   - Update `gradle/libs.versions.toml` or native dylib submodule.
   - Run `./scripts/verify_all.sh` to ensure zero regressions.
   - Increment patch version: `1.0.1`.
3. **Protected Release Execution**:
   - Build updated DMG, APK, AAB, and desktop packages.
   - Update `docs/SHA256SUMS.txt` and `docs/release-manifest.json`.
   - Submit for owner signing and store publication.

### Scenario B: Signing Key Compromise Procedure
1. Immediately revoke the compromised certificate via Apple Developer / Google Play / DigiCert.
2. Generate a new signing key pair.
3. Update the baked-in Ed25519 verification public key in `UpdateSecurity.kt`.
4. Issue an emergency update through distribution channels with key migration certificates.
