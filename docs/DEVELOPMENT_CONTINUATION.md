# Development continuation

Last verified on 2026-10-14 on macOS, branch `main`.

## Resume point

Implementation is verified through commit `90a6d4a` (`fix(desktop): safeguard personal media actions`).

The latest completed desktop increments are:

1. bounded Java Sound metadata extraction for supported audio files;
2. SQLite schema version 3, including persisted extracted titles and searchable artist/album metadata;
3. real metadata returned to the desktop music/video library UI and playback model;
4. playback history and conservative resume-position persistence for indexed video and long-form audio.
5. reactive desktop playlists, favorites, history, and bookmarks, including persistent mutations and destructive-action confirmations.

Do not rebuild these components. Continue from the first executable item in the backlog below.

## Next executable work

1. Add a production metadata backend for video and compressed/tagged audio. The current Java Sound extractor is intentionally bounded and does not claim MP3/FLAC/MKV coverage.
2. Add artwork/thumbnail extraction and a bounded cache.
3. Expand library queries with paging, sorting, categories, and recent/continue-watching projections.
4. Add queue ownership above the single `MpvPlayerEngine`; persist and restore the queue without auto-playing after restart. Playlist playback currently opens the first available item only and is labeled accordingly.
5. Complete playlist import/export and backup/restore after a versioned portable format is defined.
6. Finish Windows video embedding. Windows can compile and package, but the current renderer capability truthfully reports local video unavailable.
7. Bundle redistributable libmpv binaries per supported OS/architecture and validate installed applications on clean machines.

## External or hardware blockers

- Real macOS audio/video playback, resize, fullscreen, sleep/wake, and cleanup require a machine with a compatible libmpv runtime and legal test media.
- Windows embedded video and installer runtime verification require a Windows runner or machine.
- Production signing/notarization requires owner-managed Apple and Windows signing credentials. Never commit credentials.
- Cross-architecture native validation requires the corresponding hardware or runners.

## Required verification

For desktop changes run:

```shell
./gradlew :desktopApp:desktopTest
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :desktopApp:detekt
```

Before a broader handoff also run `./gradlew detekt`. Run Android assembly only when Android sources or build configuration changed. Never infer runtime playback success from compilation or unit tests.

