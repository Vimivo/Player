# Feature roadmap

## Foundation delivered

- Single foreground `MediaLibraryService` with notification, lock-screen, Bluetooth, headset, and external controller support
- Service-owned Media3/LibVLC engine lifecycle through one MediaSession-compatible adapter
- Mutable queue operations, next/previous, repeat-one/all, shuffle, completion advancement, and paused process restoration
- Audio focus, transient interruption resume, ducking, and noisy-route disconnection handling

- Room-backed local library with indexed paging, search, favorites, history/resume, playlists, exclusions, and scan state
- MediaStore discovery across available volumes plus optional persisted SAF file/folder imports
- Permission-aware songs, albums, artists, genres, folders, videos, recently added, and recently watched views
- Incremental observation, manual reconciliation, duplicate-safe upserts, and bounded thumbnail caching

- Shared media, playlist, settings, playback state, error, capability, and engine models
- Android MediaStore audio/video discovery adapter
- Android DataStore settings persistence
- Adaptive-ready Compose scaffold and typed navigation for Home, Music, Video, and Settings
- Light, dark, and AMOLED color foundations
- Media3 engine for local and HTTP(S) audio/video, state, tracks, metadata, errors, seeking, speed, volume, and video output
- LibVLC fallback engine with hardware/software decoding, track selection, video output, and explicit audio focus
- Queue-aware engine manager with deterministic cleanup and bounded fallback
- Development playback console for Storage Access Framework documents and authorized HTTP(S) URLs
- Complete local music browsing for songs, albums, artists, album artists, genres, folders, playlists, recent media, and favorites
- Sortable music results, collection-level play/shuffle/queue actions, and persistent service-backed mini player
- Full-screen Now Playing with seek, queue editing/reordering, repeat, shuffle, speed, favorites, safe sharing, and media information
- Album, artist, folder, and playlist details backed by real indexed media; no sample media is injected
- Video library filters for all videos, folders, recent, favorites, and in-progress playback
- Immersive video player with buffered seeking, queue navigation, screen/orientation lock, PiP, speed, track selection, aspect modes, and honest media diagnostics
- Configurable edge-safe gestures for double-tap seeking, swipe seeking, brightness/volume, pinch zoom, and control visibility

## Planned

- Equalizer and platform audio-effects evaluation
- Playlist persistence and import/export
- Embedded/external subtitles, synchronization, style, audio/subtitle track selection
- Authorized HTTP/HLS/DASH/SMB/WebDAV sources with credential-safe storage
- Picture-in-picture, casting evaluation, Android Auto, and TV evaluation

Desktop and iOS applications remain outside the current scope.

# Personal organization

- Local mixed audio/video playlists support create, rename, delete, reorder, duplicate, playback, shuffle, queueing, and M3U/M3U8 SAF import/export.
- Songs, videos, and local media remain favoritable; stream favorites use the existing local settings store.
- Playback history records last-played time, resume position, completion, and play count without analytics or remote logging.
- Timestamp bookmarks can be added, renamed, deleted, and opened through the shared playback session.
- Unified indexed media search covers title, artist, album, folder, and filename; playlist names use an indexed Room query. Duration, MIME type, folder, kind, favorites, and recency are repository-level filters.
- Embedded file metadata is currently read-only. Editing is withheld where Android cannot guarantee a reliable writable URI transaction and confirmation flow.

