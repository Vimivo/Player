# Vimivo Upgrade & Rollback Verification

## 1. Scope & Verification Strategy

Vimivo maintains strict local data durability across application versions.
When users update from closed beta builds to Release Candidates or stable releases, all personal user data (playlists, favorites, playback history, bookmarks, queue state, and settings) must be preserved without loss or corruption.

---

## 2. Schema Evolution & Migration Verification

The desktop and shared SQLite persistence layers support automated, non-destructive schema migrations:

```
[Schema Version 0] (Empty database file)
        │
        ▼ Migration V1 (Initial Tables: media_items, playlists, playlist_entries)
[Schema Version 1]
        │
        ▼ Migration V2 (Favorites & History tracking tables)
[Schema Version 2]
        │
        ▼ Migration V3 (Bookmarks & Media Resume timestamp offsets)
[Schema Version 3]
        │
        ▼ Migration V4 (Persistent Playback Queue & User Settings)
[Schema Version 4] (Current Release Candidate)
```

### Automated Migration Test Evidence
The migration pipeline is covered by comprehensive automated tests in `desktopApp/src/desktopTest/kotlin/dev/vimivo/desktop/persistence/DesktopDatabaseMigrationTest.kt`:

| Test Scenario | Source State | Target State | Preserved Data Verified | Result |
| :--- | :--- | :--- | :--- | :---: |
| `migratesEmptyDatabaseToLatestSchema` | Version 0 | Version 4 | All 7 tables created with indexes | ✅ PASS |
| `migratesFromV1ToLatestPreservingPlaylists` | Version 1 | Version 4 | Legacy playlists, entries, and tracks | ✅ PASS |
| `migratesFromV2ToLatestPreservingFavoritesAndHistory` | Version 2 | Version 4 | Historical play counts, favorites | ✅ PASS |
| `migratesFromV3ToLatestPreservingResumeAndBookmarks` | Version 3 | Version 4 | Exact resume millisecond offsets | ✅ PASS |
| `foreignKeyConstraintsAreEnforced` | Version 4 | Version 4 | Cascading deletes & orphan prevention | ✅ PASS |
| `migrationFailsAtomicallyOnCorruptInput` | Malformed DB | Rollback | Transaction rolled back safely | ✅ PASS |

---

## 3. Platform Upgrade Verification Procedures

### Android (APK / AAB)
- **Mechanism**: Standard package manager replacement (`adb install -r androidApp-release.apk`).
- **Data Location**: `/data/data/dev.vimivo/databases/vimivo.db`.
- **Test Procedure**:
  1. Install Beta candidate build with test library and 3 custom playlists.
  2. Install RC build with identical application ID (`dev.vimivo`) and higher `versionCode`.
  3. Launch application.
  4. Verify all playlists, track counts, and resume points are intact.

### macOS (.dmg / .app)
- **Mechanism**: Drag-and-drop replacement to `/Applications/Vimivo.app`.
- **Data Location**: `~/Library/Application Support/Vimivo/vimivo.db`.
- **Test Procedure**:
  1. Run Beta app, populate 10 favorites and 5 history items.
  2. Overwrite `/Applications/Vimivo.app` with candidate `Vimivo-1.0.0.dmg`.
  3. Launch newer version.
  4. Verify database schema automatically migrates to v4 on first connection open.

### Windows (NSIS / MSI)
- **Mechanism**: NSIS installer detected previous installation and updates binaries in `%LOCALAPPDATA%\Programs\Vimivo`.
- **Data Location**: `%APPDATA%\Vimivo\vimivo.db`.
- **Test Procedure**:
  1. Run previous installer, launch and populate queue.
  2. Run `Vimivo-Setup-1.0.0.exe`. Installer updates binaries without touching `%APPDATA%`.
  3. Launch application -> verify queue state restores cleanly.

### Linux (DEB / RPM)
- **Mechanism**: Package manager upgrade (`sudo dpkg -i vimivo_1.0.0_amd64.deb`).
- **Data Location**: `~/.local/share/vimivo/vimivo.db`.
- **Test Procedure**:
  1. Package manager replaces `/opt/vimivo` binaries.
  2. Launch app -> user configuration and SQLite database in XDG data home remain intact.

---

## 4. Rollback Policy & Limitations

- **Schema Forward-Compatibility**: SQLite migrations add tables, columns, and indexes non-destructively.
- **Binary Downgrades**: Downgrading an application binary to an older build that expects an older schema (e.g. downgrading from RC v4 to an older Alpha v1) is blocked by SQLite schema version checks to prevent crashes from missing tables.
- **Recommendation for Testers**: If testing developmental builds, back up `vimivo.db` from your platform's application data directory before switching major branches.
