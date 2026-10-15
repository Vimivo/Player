# Development continuation

Last verified on 2026-10-15 on macOS, branch `main`.

## Resume point

Implementation is verified through Milestone 15 (`feat(desktop): video metadata, thumbnails, album & artist library, and collection queue actions`).

The latest completed desktop increments are:

1. bounded Java Sound metadata extraction for supported audio files;
2. SQLite schema version 3, including persisted extracted titles and searchable artist/album metadata;
3. real metadata returned to the desktop music/video library UI and playback model;
4. playback history and conservative resume-position persistence for indexed video and long-form audio;
5. reactive desktop playlists, favorites, history, and bookmarks, including persistent mutations and destructive-action confirmations;
6. Jaudiotagger-backed rich audio metadata, extraction-version invalidation, bounded embedded-artwork caching, and artwork UI;
7. a shared unified queue with continuous playlist playback, next/previous, missing-item skipping, shuffle, repeat, and queue editing;
8. SQLite schema version 4 queue persistence, snapshot restoration without autoplay, and queue actions across app startup;
9. licensed generated test fixtures for tagged MP3, FLAC, M4A, Ogg, Opus, WAV, and AIFF verifying parser output, duration, Unicode/Bengali tags, and invalidation;
10. pure-Kotlin Opus metadata parser for Ogg Opus containers and embedded tags without external dependencies;
11. argument-safe video inspection and frame thumbnail generation using isolated tool locators (ffprobe/ffmpeg) with bounded memory/timeout;
12. video frame thumbnail caching with atomic writes, aspect-ratio preservation, and invalidation;
13. album and artist database projections with multi-disc/track ordering, album artist fallbacks, and compilation support;
14. real Compose screens for Albums, Artists, Album Details, and Artist Details with artwork/thumbnail rendering;
15. Play All, Shuffle Play, Play Next, and Add to Queue integration across all collection views (Songs, Albums, Artists, Playlists, Favorites, History);
16. continuous queue integration for multiple Finder open-file selections preserving ordering;
17. automated bundling of 50 native dylibs (libmpv, FFmpeg, etc.) into `Vimivo.app/Contents/Frameworks/` with `@rpath` rewriting and zero Homebrew runtime dependencies;
18. self-contained JVM packaging with required runtime modules and verified installable `Vimivo-1.0.0.dmg`.

Do not rebuild these components. Continue from the first executable item in the backlog below.

## Next executable work

1. Expand library queries with paging, sorting, categories, and recent/continue-watching projections.
2. Complete playlist import/export and backup/restore after a versioned portable format is defined.
3. Implement Windows native renderer (Direct3D/OpenGL surface) and bundle Windows native DLLs.
4. Implement Linux native renderer (Wayland/X11 surface) and bundle Linux native libraries.
5. Prepare Apple Developer ID signing and notarization workflow via `xcrun notarytool`.

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

