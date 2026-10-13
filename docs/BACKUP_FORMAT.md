# Vimivo local backup format

Vimivo backups are UTF-8 JSON documents selected through Android's Storage Access Framework. The root object contains:

- `format`: fixed identifier `dev.vimivo.backup`
- `version`: integer schema version, currently `1`
- `createdAtEpochMillis`: informational creation time
- `settings`: user preferences
- `favorites`: local media URI references
- `favoriteStreams`: authorized network URLs favorited by the user
- `playlists`: playlist identity, name, update time, and ordered media URI references
- `bookmarks`: bookmark identity, name, position, creation time, and media URI
- `history`: last position, last-played time, completion, play count, and media URI

Complete media files, artwork bytes, credentials, logs, and SAF grants are never included.

Restore matches media by its stable URI in the current authorized library. Missing references are skipped and counted in the restore report. Duplicate favorite and playlist-item references are collapsed. Existing playlist and bookmark IDs are updated, making repeated restore idempotent instead of producing unbounded duplicates. Existing data not represented by the backup is retained.

Future readers must reject unknown format identifiers and versions newer than they support. Additive fields may be ignored by older readers.
