# Implementation ledger

Status terms: **implemented** means code exists, **tested** means automated tests ran successfully, and **runtime verified** is reserved for an actual installed/runtime exercise.

| Area | Status | Evidence and limits |
|---|---|---|
| Shared playback contracts | Implemented, tested | Platform-neutral engine/state/track/capability contracts and unit coverage. |
| macOS libmpv engine bridge | Implemented, tested; runtime verification required | In-process JNA engine plus native Cocoa/OpenGL render bridge. Unit tests cover capability and lifecycle seams; a clean-machine libmpv playback pass remains required. |
| Windows playback | Partial | Audio/network engine path exists. Embedded local video is disabled by capability reporting until a Windows render surface is implemented. |
| Linux playback | Partial | Compile/package foundation exists; installed runtime and native bundling are not verified. |
| Desktop folder selection and scanning | Implemented, tested | Native macOS picker, asynchronous recursive scanner, reconciliation, cancellation, exclusions, and SQLite persistence have tests. Security-scoped bookmarks are not yet implemented. |
| Desktop metadata | Partial, tested | Bounded Java Sound extraction, retry bookkeeping, schema v3 persistence, and library-query integration are tested. Tagged compressed audio, video metadata, artwork, and thumbnails still need a production backend. |
| Desktop library UI | Partial | Real indexed audio/video data, roots, search, scan controls, and playback launch are connected. Categories, paging, richer sorting, and collection detail views remain. |
| Personal media database | Implemented, tested | Playlists, favorites, history, positions, bookmarks, and settings persist with foreign keys and migration tests. Most operations are not yet exposed in desktop UI. |
| Playback history and resume | Implemented, tested | One play count per session, ten-second position checkpoints, completion handling, and conservative video/long-audio resume rules. Real process-restart playback verification remains. |
| Network URL playback | Partial | HTTP/HTTPS/RTSP URLs use the existing engine. Recent/favorite URL UI, credential storage, SMB/WebDAV/SFTP, and discovery are not implemented. |
| Desktop distribution | Partial | Compose packaging configuration exists for supported host targets. Native libmpv bundling, clean-machine installs, signatures, notarization, checksums, and Windows installer validation remain. |
| Android application | Existing target; unchanged by this checkpoint | Desktop work must not regress Android. Run Android gates when Android code/build files change. |

## Verified increments

- `fd44a6e` — bounded desktop audio metadata extraction.
- `8b50e77` — extracted metadata exposed through SQLite library queries and search.
- `e2f6369` — playback history and resume integrated with the desktop playback lifecycle.

## Quality record

On 2026-10-14, after `e2f6369`, these commands completed successfully:

- `./gradlew :desktopApp:desktopTest`
- `./gradlew :desktopApp:compileKotlinDesktop`
- `./gradlew :desktopApp:detekt`

This record does not claim installed-application or real-media runtime verification.
