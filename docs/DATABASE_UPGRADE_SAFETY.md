# Vimivo — Database Upgrade Safety & Migration History

This document documents the SQLite schema architecture, sequential migration path, transaction safety, and user-data preservation guarantees in Vimivo.

---

## 1. Schema Version Architecture

The authoritative SQLite database for Vimivo desktop is managed by `DesktopDatabase.kt` and currently implements **Schema Version 6**.

```
Version 1 (Core Media & Playlists)
    │
    ▼
Version 2 (Playback History, Resume Offsets, Favorites)
    │
    ▼
Version 3 (Metadata Overrides, Bookmarks, Audio Track Preferences)
    │
    ▼
Version 4 (Content Hashes & Duplicate Detection)
    │
    ▼
Version 5 (Batch Operation Journaling)
    │
    ▼
Version 6 (Workflow Automation, Personalization Preferences, Dismissals)
```

---

## 2. Migration Invariants & Transaction Guarantees

1. **Transactional Migrations**: Every migration step is wrapped inside an atomic SQL transaction:
   ```kotlin
   fun <T> transaction(block: (Connection) -> T): T {
       connection.autoCommit = false
       return try {
           block(connection).also { connection.commit() }
       } catch (error: Throwable) {
           connection.rollback()
           throw error
       } finally {
           connection.autoCommit = wasAutoCommit
       }
   }
   ```
2. **Write-Ahead Logging (WAL)**: `PRAGMA journal_mode = WAL` enables concurrent readers without blocking writes, preventing database locks during playback resume updates.
3. **Foreign Key Integrity**: `PRAGMA foreign_keys = ON` ensures cascading deletions (e.g. deleting a media item automatically cleans up associated hash entries and playlist associations without orphan rows).
4. **Zero Data Loss**: No migration step drops user tables or resets playback history. User-curated playlists, favorite marks, resume positions, and custom metadata overrides are preserved across all version transitions.

---

## 3. Schema Version Details

| Version | Tables Introduced | Key Columns & Indexes |
| :--- | :--- | :--- |
| **v1** | `schema_version`, `library_roots`, `media_items`, `playlists`, `playlist_items` | Primary keys, path lookups, playlist order index. |
| **v2** | `playback_history`, `resume_state`, `favorites`, `smart_playlists` | Media ID foreign keys, playback timestamp indexes. |
| **v3** | `metadata_overrides`, `bookmarks`, `audio_preferences` | User title overrides, bookmark offsets, preferred audio stream IDs. |
| **v4** | `media_hashes` | `content_hash`, `file_size_bytes`, index `media_hashes_lookup`. |
| **v5** | `batch_operation_journal` | `job_id`, `operation_type`, `stage`, `affected_count`, index `batch_journal_created`. |
| **v6** | `workflow_rules`, `dismissed_recommendations`, `personalization_preferences` | Automation rule definitions, recommendation suppression keys, key-value preferences. |

---

## 4. Test Verification

Database migrations are continuously verified in the test suite:
- `DesktopDatabaseMigrationTest.kt`: Tests fresh database initialization at v6, step-by-step upgrade from v1 through v6, and data integrity verification across migrations.
- `DesktopDatabaseTest.kt`: Validates transaction rollback on simulated constraint violations.
- `DesktopPersonalMediaRepositoryTest.kt`: Verifies repository operations against the migrated schema.
