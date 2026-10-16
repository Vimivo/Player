# Metadata Editing Architecture

Vimivo establishes a strict, privacy-first architectural separation between display metadata overrides and embedded file tag mutations. This model guarantees that user media files are never unexpectedly modified, corrupted, or touched unless explicitly confirmed by the user.

---

## 1. Dual-Mode Editing Model

```
+-------------------------------------------------------------+
|                      User / UI Layer                        |
|   (Single-Item Editor, Bulk Editor, Album Editor, CSV)      |
+-------------------------------------------------------------+
                              |
                     MetadataChangeset
                 (FieldAction: Set/Clear/Unchanged)
                              |
               +--------------+--------------+
               |                             |
               v                             v
     [ Mode A: Database Override ]   [ Mode B: Embedded File Tag ]
               |                             |
               v                             v
    SQLite Schema v6 Tables        Capability Preflight & Backup
    - user_metadata_overrides      - Format validation (MP3/FLAC/OGG/M4A/WAV)
    - user_artwork_overrides       - Backup creation in scratch directory
               |                   - Jaudiotagger commit
               |                   - Post-write header verification
               |                   - Rollback on failure
               |                             |
               +--------------+--------------+
                              |
                              v
             Effective Metadata Resolution in SQLite
         COALESCE(u.field, meta.field, items.default)
```

### Mode A: Database-Only Overrides (Default & Safe)
- **Persistence Target**: SQLite table `user_metadata_overrides` and `user_artwork_overrides`.
- **Physical Media**: Zero byte modifications to original files on disk.
- **Use Cases**:
  - Read-only media or write-protected filesystems.
  - Video formats (MKV, MP4, AVI, WebM) where audio tag writing is unsafe or unsupported.
  - Network sources and read-only cloud/document providers.
  - Quick organizational corrections without touching file timestamps or hashes.

### Mode B: Embedded File Tag Writing (Explicit & Staged)
- **Persistence Target**: Physical audio container tags via format-specific native writers (`AudioFileIO`).
- **Safety Pipeline**:
  1. **Preflight Validation**: Validates container format and write permissions.
  2. **Automatic Backup**: Copies original file to backup directory before mutation.
  3. **Tag Commit**: Modifies requested fields while preserving unselected tags.
  4. **Post-Write Verification**: Reopens and validates audio stream headers.
  5. **Atomic Rollback**: Restores original from backup if commit or verification fails.

---

## 2. Portable Metadata Contracts

Defined in [`MetadataEditModels.kt`](file:///Users/ta-shanto/Coding/products/Vimivo/shared/src/commonMain/kotlin/dev/vimivo/core/model/MetadataEditModels.kt):

- **`FieldAction<T>`**:
  - `FieldAction.Unchanged`: Leaves the existing field exactly as-is.
  - `FieldAction.Clear`: Explicitly deletes/clears the field from the database or tag.
  - `FieldAction.Set(value)`: Updates the field to the specified value.
- **`MetadataChangeset`**:
  Encapsulates field mutations across `title`, `artist`, `album`, `albumArtist`, `genre`, `releaseYear`, `trackNumber`, and `discNumber`.
- **`MetadataEditItemPlan`**:
  Pairs a target item with original values, proposed changes, validation status, and write mode.

---

## 3. Effective Metadata Resolution

All UI queries, search indexes, album groupings, and smart playlists resolve metadata hierarchically:

$$\text{Effective Value} = \text{User Override} \succ \text{Extracted Metadata} \succ \text{Filesystem Default}$$

In SQLite:
```sql
SELECT 
    mi.id,
    COALESCE(u.title, meta.title, mi.title) AS effective_title,
    COALESCE(u.artist, meta.artist) AS effective_artist,
    COALESCE(u.album, meta.album) AS effective_album,
    COALESCE(u.album_artist, meta.album_artist, u.artist, meta.artist) AS effective_album_artist,
    COALESCE(u.genre, meta.genre) AS effective_genre,
    COALESCE(u.release_year, meta.release_year) AS effective_year,
    COALESCE(u.track_number, meta.track_number) AS effective_track
FROM media_items mi
LEFT JOIN media_metadata meta ON mi.id = meta.media_id
LEFT JOIN user_metadata_overrides u ON mi.id = u.media_id
WHERE mi.available = 1;
```

---

## 4. Concurrency & Playback Protection

- Tag writes occur strictly outside active playback locks.
- If a file is currently playing in the native engine, file modification waits or prompts the user rather than corrupting active decoding.
- Database transactions wrap override mutations to ensure atomic cache updates.
