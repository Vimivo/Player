# Vimivo — Update Failure Recovery & Downgrade Safety

This document details the failure handling strategies, recovery procedures, and downgrade protection mechanisms for Vimivo application updates.

---

## 1. Failure Modes & Automatic Containment

| Failure Scenario | Immediate System Reaction | User-Facing State | Data Safety Guarantee |
| :--- | :--- | :--- | :--- |
| **Network Loss / Timeout** | Connection aborts gracefully after timeout (10s connect, 30s read). | `Check failed` / `Could not reach update server` | Active playback continues; zero state change. |
| **Tampered / Corrupt Binary** | Computed SHA-256 does not match manifest. `SecurityException` raised. | `Update verification failed` | Temporary `.part` file deleted immediately; no binary executed. |
| **Signature Failure** | Digital signature missing, key unknown, or signature invalid. | `Update signature verification failed` | Candidate rejected prior to staging or installation. |
| **Database Incompatibility** | Manifest requires higher schema than currently supported. | `Database migration required` | Prevents installation of versions that cannot read or migrate existing schema. |
| **Download Interrupted / Cancelled** | Download stream terminates or user cancels. `InterruptedException` handled. | `Download cancelled` | Partial `.part` file deleted; disk space restored immediately. |
| **Insufficient Disk Space** | Pre-flight check detects less than required size + 50 MB headroom. | `Insufficient disk space` | Download aborted before writing bytes to disk. |

---

## 2. Invariant: Local Playback Resiliency

Under no circumstance does an update failure impair local playback:
- Media files on disk are never locked or modified by update operations.
- The playback engine (Media3 / libmpv) operates in a decoupled thread.
- The SQLite database remains open in read/write WAL mode and is untouched during update checks.

---

## 3. Safe Downgrade & Rollback Procedures

### 3.1 The Schema Downgrade Constraint
SQLite schemas in Vimivo migrate monotonically forward (v1 through v6). Older application releases lack migration logic for newer database tables (e.g. `workflow_rules`, `dismissed_recommendations`, `media_hashes`). Consequently:
- Running an older Vimivo binary on a newer SQLite database will trigger a SQLite schema version incompatibility error.
- Vimivo explicitly rejects downgrade installations (`DowngradeBlocked`) when `preventDowngrade = true`.

### 3.2 Manual Rollback Procedure
If a user must revert to an earlier application release:
1. **Locate Automatic Database Backup**: Before major migrations, database backups are created at:
   - macOS: `~/Library/Application Support/Vimivo/vimivo_backup_v*.db`
   - Windows: `%LOCALAPPDATA%\Vimivo\vimivo_backup_v*.db`
   - Linux: `~/.local/share/vimivo/vimivo_backup_v*.db`
2. **Restore Database**: Replace `vimivo.db` with the pre-migration snapshot corresponding to the target version.
3. **Install Target Release**: Install the older verified application binary.
