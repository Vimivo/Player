# Implementation ledger

Status terms: **implemented** means code exists, **tested** means automated tests ran successfully, and **runtime verified** is reserved for an actual installed/runtime exercise.

| Area | Status | Evidence and limits |
|---|---|---|
| Shared playback contracts | Implemented, tested | Platform-neutral engine/state/track/capability contracts and unit coverage. |
| macOS libmpv engine bridge | Implemented, tested; runtime verification required | In-process JNA engine plus native Cocoa/OpenGL render bridge. Unit tests cover capability and lifecycle seams; a clean-machine libmpv playback pass remains required. |
| Windows playback | Partial | Audio/network engine path exists. Embedded local video is disabled by capability reporting until a Windows render surface is implemented. |
| Linux playback | Partial | Compile/package foundation exists; installed runtime and native bundling are not verified. |
| Desktop folder selection and scanning | Implemented, tested | Native macOS picker, asynchronous recursive scanner, reconciliation, cancellation, exclusions, and SQLite persistence have tests. Security-scoped bookmarks are not yet implemented. |
| Desktop metadata | Partial, tested | Jaudiotagger rich audio parsing, Java Sound fallback, extraction versioning, persistence, and library queries exist. WAV/cache tests pass; tagged-format fixtures and video metadata remain. |
| Artwork cache/UI | Implemented, tested | Embedded artwork is bounded, decoded/scaled off the UI thread, atomically cached, size-limited, persisted by URI, and shown in library, favorites/history, and now playing. Tagged real-media fixture verification remains. |
| Desktop library UI | Partial | Real indexed data, metadata/artwork, roots, search, scanning, playback, favorites, playlists, history, bookmarks, and queue are connected. Categories, paging, sorting, album/artist views, and video thumbnails remain. |
| Personal media database | Implemented, tested | Playlists, favorites, history, positions, bookmarks, and settings persist with foreign keys and migration tests. Desktop create/rename/delete/duplicate/add/remove/reorder/favorite/history/bookmark actions are connected. Import/export and full queue playback remain. |
| Playback history and resume | Implemented, tested | One play count per session, ten-second position checkpoints, completion handling, and conservative video/long-audio resume rules. Real process-restart playback verification remains. |
| Unified playback queue | Implemented, tested; persistence partial | One engine owns continuous play-all, next/previous, stable duplicate occurrences, missing-item skipping, reorder/remove/clear, shuffle, and repeat modes. Queue state is not yet restored after restart. |
| Network URL playback | Partial | HTTP/HTTPS/RTSP URLs use the existing engine. Recent/favorite URL UI, credential storage, SMB/WebDAV/SFTP, and discovery are not implemented. |
| Desktop distribution | Partial | Compose packaging configuration exists for supported host targets. Native libmpv bundling, clean-machine installs, signatures, notarization, checksums, and Windows installer validation remain. |
| Android application | Existing target; unchanged by this checkpoint | Desktop work must not regress Android. Run Android gates when Android code/build files change. |

## Verified increments

- `fd44a6e` — bounded desktop audio metadata extraction.
- `8b50e77` — extracted metadata exposed through SQLite library queries and search.
- `e2f6369` — playback history and resume integrated with the desktop playback lifecycle.
- `8fd548f` — persistent playlists, favorites, history, and bookmarks connected to desktop UI state.
- `90a6d4a` — arbitrary playlist selection, bookmark renaming, and destructive-action confirmations.
- `0a95609` — rich tagged audio metadata and bounded embedded-artwork cache.
- `bc7d3fb` — unified continuous desktop playback queue.
- `372cd28` — cached artwork connected to desktop media UI.

## Quality record

On 2026-10-15, after `372cd28`, these commands completed successfully:

- `./gradlew :desktopApp:desktopTest`
- `./gradlew :desktopApp:compileKotlinDesktop`
- `./gradlew :desktopApp:detekt`
- `./gradlew :shared:desktopTest` (9 tests)
- `./gradlew detekt`
- `./gradlew :androidApp:assembleDebug` (shared queue compatibility; completed in 1m 30s)

The desktop suite currently contains 25 tests.

This record does not claim installed-application or real-media runtime verification.
