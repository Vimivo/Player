# Vimivo — Database & Migration Architecture Guide

This guide details the SQLite schema version 6, core tables, relationships, foreign keys, performance indexes, and migration policies.

---

## 1. Storage Location & Driver Architecture

Vimivo uses SQLite with Write-Ahead Logging (WAL) and foreign keys enabled:
- **macOS**: `~/Library/Application Support/Vimivo/vimivo.sqlite`
- **Windows**: `%LOCALAPPDATA%\Vimivo\vimivo.sqlite`
- **Linux**: `$XDG_DATA_HOME/Vimivo/vimivo.sqlite` (defaults to `~/.local/share/Vimivo/vimivo.sqlite`)
- **Android**: Room Database stored in application internal data directory.
- **iOS**: SQLite database under `Library/Application Support/Vimivo/vimivo.sqlite`.

---

## 2. SQLite Schema Version 6 Tables & Relationships

```text
library_roots ───< (1:N) ───< media_items
                                  │
      ┌───────────────────────────┼───────────────────────────┐
      │ (1:1)                     │ (1:1)                     │ (1:1)
      ▼                           ▼                           ▼
media_metadata        user_metadata_overrides     user_artwork_overrides
      │                           │                           │
      ├───────────────────────────┼───────────────────────────┤
      │                           │                           │
      ▼                           ▼                           ▼
playback_queue_items        playback_history          playback_positions
      │                           │                           │
      ▼                           ▼                           ▼
playlist_items (N:M)       media_bookmarks (1:N)       media_hashes (1:1)
      ▲
      │
  playlists
```

### Table Definitions

1. **`schema_version`**: Stores the single integer version (`6`).
2. **`library_roots`**: User-selected indexed folders (`id`, `uri`, `display_name`, `added_at_ms`, `last_scan_ms`, `available`).
3. **`media_items`**: Authoritative indexed files:
   - Foreign key: `FOREIGN KEY(root_id) REFERENCES library_roots(id)`.
   - Columns: `id` (PRIMARY KEY), `root_id`, `uri` (UNIQUE), `file_name`, `title`, `kind` (`AUDIO`/`VIDEO`), `mime_type`, `size_bytes`, `modified_ms`, `indexed_ms`, `available`.
4. **`media_metadata`**: Extracted tags and technical specs (`artist`, `album`, `genre`, `duration_ms`, `bitrate`, `channels`, `video_width`, `video_height`, `artwork_uri`, etc.).
5. **`user_metadata_overrides`** *(Mode A Overrides)*: User-defined display overrides (`title`, `artist`, `album`, `album_artist`, `genre`, `release_year`, `track_number`, `disc_number`, `updated_at_ms`).
6. **`user_artwork_overrides`**: User-selected custom cover artwork (`media_id`, `artwork_uri`, `updated_at_ms`).
7. **`playlists` & `playlist_items`**: Custom playlists with sequential order positions.
8. **`smart_playlists`**: Dynamic rule definitions with serialized AST conditions and match mode (`ALL`/`ANY`).
9. **`playback_queue_items`**: Ordered persisted queue items (`queue_id`, `media_id`, `source_position`, `playback_position`).
10. **`playback_history`**: Play counts, last played timestamps, completed status.
11. **`playback_positions`**: Last playback checkpoint in milliseconds for video and audio resume.
12. **`media_bookmarks`**: Named time bookmarks within tracks or movies.
13. **`media_hashes`**: SHA-256 hashes and file sizes for duplicate media detection.
14. **`batch_operation_journal`**: Stage-tracked audit log of batch operations (`job_id`, `operation_type`, `stage`, `affected_count`, `details`, `created_at_ms`).
15. **`workflow_rules`**: User automation rules (`id`, `name`, `trigger_type`, `action_type`, `enabled`).
16. **`dismissed_recommendations`**: User-dismissed suggestions (`target_type`, `target_id`, `dismissed_at_ms`).
17. **`personalization_preferences`**: Key-value settings for personalization.
18. **`settings`**: Key-value application settings (theme, volume, replaygain mode, sleep timer, etc.).

---

## 3. Performance Indexes

- `media_items_root_available`: `(root_id, available)`
- `media_items_kind_available`: `(kind, available)`
- `media_items_kind_title`: `(kind, title COLLATE NOCASE)`
- `media_items_file_name`: `(file_name COLLATE NOCASE)`
- `media_metadata_artist`: `(artist COLLATE NOCASE)`
- `media_metadata_album`: `(album COLLATE NOCASE)`
- `media_metadata_album_artist`: `(album_artist COLLATE NOCASE)`
- `media_metadata_lookup`: `(album COLLATE NOCASE, artist COLLATE NOCASE)`
- `playlist_items_order`: `(playlist_id, position)`
- `playlist_items_media_id`: `(media_id)`
- `media_hashes_lookup`: `(content_hash, file_size_bytes)`
- `batch_journal_created`: `(created_at_ms DESC)`

---

## 4. Migration Policy (Schema Versioning)

- **Forward-Only, Non-Destructive**: Every migration is executed inside a transaction.
- **Never Reset Databases**: Migrations never drop tables, truncate user playlists, or delete playback history.
- **Foreign Key Safety**: Foreign key checks are enforced (`PRAGMA foreign_keys=ON`).
- **Migration History**:
  - `Version 1`: Initial tables (`library_roots`, `media_items`, `settings`).
  - `Version 2`: Playlists, favorites, history, positions, bookmarks, metadata.
  - `Version 3`: Title column in `media_metadata`, artist/album index.
  - `Version 4`: Unified playback queue persistence (`playback_queue_state`, `playback_queue_items`).
  - `Version 5`: 5 performance indexes for large library scalability (10k items).
  - `Version 6`: Metadata overrides, artwork overrides, smart playlists, duplicate content hashes, batch operation journal, workflow rules, dismissed recommendations.

---

## 5. Adding & Testing a Migration

When schema changes are required:
1. Increment `DesktopDatabase.SCHEMA_VERSION`.
2. Add a new `migrateToVersionN(statement)` method in `DesktopDatabase.kt`.
3. Add a test in `DesktopDatabaseMigrationTest.kt` verifying that upgrading an existing database from `N-1` to `N` preserves all pre-existing records and respects foreign keys.
4. Run `./gradlew :desktopApp:desktopTest` to confirm all migration tests pass.
