# Development continuation

Last verified on 2026-10-15 on macOS, branch `main`.

## Resume point

Implementation is verified through Milestone 17 and 18 foundations (`feat(windows): embedded video renderer, secure credentials, and standalone distribution packaging`).

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
18. self-contained JVM packaging with required runtime modules and verified installable `Vimivo-1.0.0.dmg`;
19. Windows embedded video renderer (`MpvWin32Renderer`) attaching AWT Canvas Win32 HWND directly to libmpv (`wid`) with `vo = gpu` and capability reporting;
20. Windows secure DLL directory resolution (`MpvNative.findBundledWindowsDllDir()`) with cross-platform JNA library path management;
21. Windows Credential Manager integration (`WindowsCredentialStore`) with Win32 `Advapi32` APIs and safe in-memory fallback;
22. Windows desktop integration including Drag-and-Drop (`DropTarget`), MenuBar keyboard shortcuts (`Ctrl+O`, `Ctrl+Shift+O`, `Space`, `Left`/`Right`), and `%LOCALAPPDATA%\Vimivo` persistence;
23. Windows standalone packaging configuration in `build.gradle.kts` for EXE and MSI formats, native dependency bundling script `bundle_windows_mpv.py`, official 256x256 `.ico` icon, and `.github/workflows/windows.yml` CI pipeline.

Do not rebuild these components. Continue from the first executable item in the backlog below.

## Next executable work

1. Execute Windows GitHub Actions CI pipeline on a Windows runner to generate genuine EXE/MSI binaries and SHA-256 checksums.
2. Run clean-machine Windows runtime playback verification for audio, embedded video, resizing, fullscreen, and track switching.
3. Prepare Authenticode signing configuration for Windows application executable and installers.
4. Expand library queries with paging, sorting, categories, and recent/continue-watching projections.
5. Complete playlist import/export and backup/restore after a versioned portable format is defined.
6. Implement Linux native renderer (Wayland/X11 surface) and bundle Linux native libraries.
7. Prepare Apple Developer ID signing and notarization workflow via `xcrun notarytool`.

## External or hardware blockers

- Windows packaging (`jpackage` creating EXE/MSI) requires a Windows host (e.g. GitHub Actions `windows-latest` runner). It cannot be generated on macOS.
- Real macOS audio/video playback, resize, fullscreen, sleep/wake, and cleanup require a machine with a compatible libmpv runtime and legal test media.
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

The desktop suite currently contains 56 tests and shared desktop test suite contains 14 tests.
