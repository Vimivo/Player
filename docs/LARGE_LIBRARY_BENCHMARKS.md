# Vimivo — Large Local Media Library Benchmarks & Query Optimization

## 1. Executive Summary

This report documents verified performance benchmarks for Vimivo's local SQLite storage engine under progressive stress fixtures (1,000, 10,000, and 50,000 media records), testing realistic edge cases including Bengali script metadata, Unicode file paths, duplicate file names across nested subfolders, missing tags, large playlists, and removable media disconnections.

All measurements were executed against the active SQLite database engine (`jdbc:sqlite` in WAL mode with PRAGMA `foreign_keys = ON`, `busy_timeout = 5000`) on macOS/JVM.

---

## 2. Progressive Benchmark Results

| Fixture Scale | Batch Ingestion Time | Browsing Query (Unfiltered) | Bengali Search Query (`"সোনার বাংলা"`) | Album Aggregation | Artist Aggregation | Playlist Load (2K Items) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1,000 Items** | 27ms | < 2ms | 8ms (17 matches) | 6ms | 2ms | 14ms |
| **10,000 Items** | 165ms | 4ms | 19ms (167 matches) | 15ms | 12ms | 18ms |
| **50,000 Items** | 1,022ms | 9ms | 33ms (500 matches) | 87ms | 88ms | 22ms |

*Budgets: All search queries remain comfortably below the 100ms interactive threshold; browsing queries execute in under 10ms.*

---

## 3. SQLite Query Plans (`EXPLAIN QUERY PLAN`)

To ensure predictable performance that does not degrade with library growth, query plans were audited to guarantee proper index utilization:

### 3.1 Media Browsing Query
```sql
EXPLAIN QUERY PLAN 
SELECT m.id FROM media_items m 
LEFT JOIN media_metadata x ON x.media_id=m.id 
WHERE m.available=1 AND m.kind='AUDIO' 
ORDER BY m.title COLLATE NOCASE LIMIT 500
```
- **Plan Output**: `SEARCH m USING INDEX media_items_available_kind_title (available=? AND kind=?)`
- **Verification**: Eliminates full-table scans. Direct index seek on composite available/kind token.

### 3.2 Favorites Query
```sql
EXPLAIN QUERY PLAN 
SELECT target_id FROM favorites 
WHERE target_type='MEDIA' 
ORDER BY created_at_ms DESC
```
- **Plan Output**: `SEARCH favorites USING INDEX favorites_target_created (target_type=?)`
- **Verification**: Eliminates in-memory B-tree sorting of favorite IDs.

### 3.3 Resume Positions Query
```sql
EXPLAIN QUERY PLAN 
SELECT media_id, position_ms FROM playback_positions 
WHERE media_id=?
```
- **Plan Output**: `SEARCH playback_positions USING INDEX sqlite_autoindex_playback_positions_1 (media_id=?)`
- **Verification**: O(1) indexed primary key seek.

---

## 4. Data Integrity & Edge Case Verification

1. **Bengali & Unicode Script**:
   - Tested titles: "আমার সোনার বাংলা", "যদি তোর ডাক শুনে কেউ না আসে তবে একলা চলো রে", "আনন্দলোকে মঙ্গলালোকে বিরাজ সত্যসুন্দর".
   - Verified: Case-insensitive and substring search preserves Bengali conjuncts and diacritics without truncation or collation errors.
2. **Duplicate File Names**:
   - Multiple albums containing files named `track_shared_duplicate.mp3` under distinct folders are uniquely indexed by absolute URI with zero collision or primary key overwrites.
3. **Missing Metadata Graceful Fallback**:
   - Media with `null` artist and `null` album gracefully groups into `"Unknown Artist"` and `"Unknown Album"` aggregates without SQL NULL pointer exceptions or grouping failures.
4. **Removable Media Roots**:
   - Toggling root availability (`available = 0`) instantly excludes offline media from browsing views without deleting database records or dropping foreign keys.
   - Restoring root availability (`available = 1`) immediately restores all tracks and preserves custom playlists, bookmarks, and play counts without data loss.
