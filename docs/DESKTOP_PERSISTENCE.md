# Desktop persistence

Vimivo desktop stores its SQLite database in the current user's macOS Application Support directory. The database is opened in WAL mode with foreign keys enabled and a bounded busy timeout. Initialization errors are surfaced; the application never deletes or recreates a database automatically.

## Schema migration

Schema version 2 is a forward-only migration from the original library index. It preserves version-1 library roots, media items, and settings while adding:

- rich media metadata extraction state and cached artwork/thumbnail references;
- ordered mixed-media playlists;
- favorites for media, playlists, and supported streams;
- playback history and play counts;
- one authoritative resume position per media item;
- timestamp bookmarks.

Migration and repository tests always use temporary database files. Reopening tests verify that user data survives process restart.

## Record lifetime

Stable media IDs are referenced by playlists, history, positions, and bookmarks. Temporary drive unavailability must mark indexed media unavailable rather than delete user-created records. Removing a configured library root remains an explicit operation; it deletes that root's index records but never touches original media files.

Playlist item changes run in transactions. Reordering uses temporary positions to preserve the unique ordered index without transient collisions. Favorites use a compound key to prevent duplicates. Settings are key/value records with upsert semantics until typed shared settings contracts are connected.

## Pending integration

The schema is ready for rich metadata, artwork, and thumbnail workers, but native extraction is not considered implemented until separate libmpv inspection contexts are integrated and tested. Backup/restore must serialize logical records in a versioned portable format; it must not copy a live WAL database or include media files, caches, credentials, or absolute-path assumptions as the only identity.
