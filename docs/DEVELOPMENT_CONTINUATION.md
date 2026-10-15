# Development continuation

Last verified on 2026-10-15 on macOS, branch `main`.

## Resume point

Implementation is verified through commit `372cd28` (`feat(desktop): integrate cached artwork UI`).

The latest completed desktop increments are:

1. bounded Java Sound metadata extraction for supported audio files;
2. SQLite schema version 3, including persisted extracted titles and searchable artist/album metadata;
3. real metadata returned to the desktop music/video library UI and playback model;
4. playback history and conservative resume-position persistence for indexed video and long-form audio.
5. reactive desktop playlists, favorites, history, and bookmarks, including persistent mutations and destructive-action confirmations.
6. Jaudiotagger-backed rich audio metadata, extraction-version invalidation, bounded embedded-artwork caching, and artwork UI.
7. a shared unified queue with continuous playlist playback, next/previous, missing-item skipping, shuffle, repeat, and queue editing.
8. SQLite schema version 4 queue persistence, snapshot restoration without autoplay, and queue actions across app startup.

Do not rebuild these components. Continue from the first executable item in the backlog below.

## Next executable work

1. Add licensed generated tagged MP3/FLAC/M4A/Ogg/Opus test fixtures and verify extraction parser output, Unicode/Bengali tags, and extraction-version invalidation.
2. Implement safe desktop video metadata and thumbnail extraction (MP4, MKV, MOV, WebM) without invoking the active player or requiring a user-installed command-line tool.
3. Add thumbnail caching with stable keys, size bounds, and atomic writes.
4. Add album and artist database projections and connect them to real Compose screens (Albums, Artists, Album Details, Artist Details).
5. Add Play Next / Add to Queue / Play All / Shuffle Play actions throughout library and playlist UI.
5. Expand library queries with paging, sorting, categories, and recent/continue-watching projections.
6. Complete playlist import/export and backup/restore after a versioned portable format is defined.
7. Finish Windows/Linux video embedding and bundle redistributable libmpv binaries per supported OS/architecture.

## External or hardware blockers

- Real macOS audio/video playback, resize, fullscreen, sleep/wake, and cleanup require a machine with a compatible libmpv runtime and legal test media.
- Windows embedded video and installer runtime verification require a Windows runner or machine.
- Production signing/notarization requires owner-managed Apple and Windows signing credentials. Never commit credentials.
- Cross-architecture native validation requires the corresponding hardware or runners.

## Required verification

For shared queue or desktop changes run:

```shell
./gradlew :desktopApp:desktopTest
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :desktopApp:detekt
./gradlew :shared:desktopTest
```

Before a broader handoff also run `./gradlew detekt`. Run Android assembly only when Android sources or build configuration changed. Never infer runtime playback success from compilation or unit tests.

The shared queue change was validated against `:androidApp:assembleDebug` on 2026-10-15; the build completed successfully, including KSP and Kotlin compilation.

