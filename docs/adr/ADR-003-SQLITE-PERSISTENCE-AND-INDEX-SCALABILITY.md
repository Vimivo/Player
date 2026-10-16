# ADR-003: SQLite Persistence, Non-Destructive Migrations & Large-Library Index Scalability

## Status
Accepted

## Context
Personal media libraries range from small playlists (100 items) to extensive collections (10,000+ to 50,000+ items). In naive database designs, large libraries suffer from exponential UI degradation caused by full-table scans, unindexed `LIKE` searches, and unindexed `GROUP BY` operations for albums and artists. Furthermore, user data (playlists, favorites, playback history, resume positions, bookmarks) must never be lost or reset across application updates.

## Decision
1. **Strictly Non-Destructive Migrations**: The database schema uses an explicit integer versioning table (`schema_version`). Migrations run sequentially (`v1 -> v2 -> v3 -> v4 -> v5`). If a database is opened with a newer version than supported, it fails fast rather than resetting or corrupting user files.
2. **Schema Version 5 Performance Indexes**:
   - `media_items_kind_available ON media_items(kind, available)`: Enables instant filtering of available audio and video items without full-table scans.
   - `media_items_file_name ON media_items(file_name COLLATE NOCASE)`: Speeds up filename fallback lookups.
   - `media_metadata_album_artist ON media_metadata(album_artist COLLATE NOCASE)`: Optimizes album artist grouping.
   - `media_metadata_lookup ON media_metadata(album COLLATE NOCASE, artist COLLATE NOCASE)`: Composite index enabling fast album/artist track discovery.
   - `playlist_items_media_id ON playlist_items(media_id)`: Accelerates cascade deletion checks and playlist item lookups.
3. **Query Fast-Paths**:
   - `media(kind, query)`: When `query.isBlank()`, the query bypasses expensive `LIKE` comparisons across title, filename, artist, and album columns.
   - Generation-aware caching in `DesktopLibraryRepository`: In-memory caching for `albums()` and `artists()` invalidated only when repository generation changes. Reduces `albumDetails()` and `artistDetails()` from O(N) aggregate scans to O(1) in-memory lookups.
4. **Bounded Batch Indexing**: `DesktopMediaScanner` inserts newly discovered media items in bounded chunks of 500 rows, preventing SQLite statement buffer overflow and keeping peak heap consumption low.

## Consequences & Tradeoffs
- **Positive**: Query latency on a 10,000-item library drops to 16ms for full library browsing, 7ms for search, 9ms for album aggregation, and 3ms for album details lookup. Database file size remains compact (~4.7 MB for 10,000 tracks).
- **Negative**: Additional indexes increase database disk footprint slightly (less than 15% increase) during initial indexing.

## Validation
Verified by `DesktopDatabaseMigrationTest` (7 tests covering v1->v5, v2->v5, v3->v5, v4->v5 upgrades) and `LargeMediaLibraryBenchmarkTest` (100, 1,000, 10,000 items benchmark).
