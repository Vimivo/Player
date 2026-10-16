# Vimivo Emergency Recovery & Rollback Plan

## 1. Scope & Core Architecture

In the event of an unexpected critical defect post-launch, this plan establishes immediate containment, diagnostic preservation, and recovery procedures across mobile and desktop distributions.

> [!CAUTION]
> **Database Invariant**: Binary package rollback is NOT equivalent to database rollback. SQLite schema migrations (e.g. v1 -> v4) are forward-compatible and non-destructive, but older application binaries cannot interpret tables added in newer schemas. Never downgrade application binaries without a database backup.

---

## 2. Emergency Triggers & Escalation Thresholds

An emergency recovery workflow is triggered immediately upon confirmation of any of the following P0 conditions:
1. **Application Launch Failure**: Crashes on startup affecting >1% of users on a supported platform.
2. **User Data Corruption**: Inadvertent deletion or truncation of playlists, favorites, playback history, or queue records.
3. **Security Vulnerability**: Exposure of local credentials, plaintext token persistence, or unredacted diagnostics leakage.
4. **Supply-Chain Compromise**: Tampering with release artifacts, CI runners, or signing keys.
5. **Native Multimedia Deadlock**: Complete audio or video playback freeze across common media containers (MP3, MP4, FLAC, MKV).

---

## 3. Emergency Incident Response Workflow

```
[Trigger Event Detected]
           │
           ▼
[1. Immediate Containment]
   - Halt Google Play Staged Rollout (Console)
   - Halt App Store Phased Release (App Store Connect)
   - Withdraw public download links / mark unverified
           │
           ▼
[2. Diagnostic Preservation]
   - Request sanitized diagnostic reports (dev.vimivo.core.diagnostics)
   - Isolate failing platform, OS build, and codec
           │
           ▼
[3. Root-Cause Analysis & Minimal Patch]
   - Formulate minimal deterministic reproduction
   - Write automated regression test in shared or platform suite
   - Implement minimal verified fix
           │
           ▼
[4. Hotfix Build & Verification]
   - Increment patch version (e.g., 1.0.0 -> 1.0.1)
   - Execute full test gate (./gradlew detekt test)
   - Re-verify SQLite migration integrity
           │
           ▼
[5. Authorized Hotfix Release]
   - Generate SHA-256 digests and updated release-manifest.json
   - Obtain release lead sign-off
   - Deploy hotfix build to affected channels
```

---

## 4. Platform-Specific Rollback Capabilities

### Android (Google Play)
- **Store Policy**: Google Play does not permit publishing lower `versionCode` builds over higher ones.
- **Remediation**:
  1. Immediately halt staged rollout in Play Console to freeze active user pool.
  2. Build hotfix candidate with incremented `versionCode` (e.g., versionCode 10001) containing the reverted or fixed code.
  3. Expedite hotfix to production.

### iOS (Apple App Store)
- **Store Policy**: App Store Connect does not allow re-releasing an older version without submitting a new build number.
- **Remediation**:
  1. Pause 7-day Phased Release immediately in App Store Connect.
  2. Create hotfix build with incremented build number (e.g., 1.0.1 build 2).
  3. Submit for expedited App Review citing P0 regression.

### Desktop (macOS, Windows, Linux)
- **Direct Distribution**:
  1. Replace candidate download link on official website with previous stable build or hotfix.
  2. Update `release-manifest.json` on the update channel.
  3. Provide explicit advisory in release notes.

---

## 5. Database Durability & User Data Recovery

1. **Automatic Backup Recommendation**: Testers and users are advised in `docs/BETA_TESTER_GUIDE.md` to maintain backups of their local application data directory prior to major version upgrades.
2. **Location of Database Files**:
   - Android: `/data/data/dev.vimivo/databases/vimivo.db`
   - macOS: `~/Library/Application Support/Vimivo/vimivo.db`
   - Windows: `%APPDATA%\Vimivo\vimivo.db`
   - Linux: `~/.local/share/vimivo/vimivo.db`
3. **Non-Destructive Recovery**: If a migration fails mid-flight, SQLite transactions automatically roll back changes to maintain database validity.
