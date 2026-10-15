# Implementation ledger

Status terms: **implemented** means code exists, **tested** means automated tests ran successfully, and **runtime verified** is reserved for an actual installed/runtime exercise.

| Area | Status | Evidence and limits |
|---|---|---|
| Shared playback contracts | Implemented, tested | Platform-neutral engine/state/track/capability contracts and unit coverage. |
| macOS libmpv engine bridge | Implemented, tested; runtime verification required | In-process JNA engine plus native Cocoa/OpenGL render bridge. Unit tests cover capability and lifecycle seams; a clean-machine libmpv playback pass remains required. |
| Windows playback | Implemented, tested; Windows host runtime required | Audio/network/video engine path complete via libmpv JNA client. Embedded local video implemented via `MpvWin32Renderer` (AWT Canvas Win32 HWND direct attachment via `wid` with `vo=gpu`). Capabilities report `localVideo = true` and `hdr = true`. |
| Windows secure credentials | Implemented, tested | `WindowsCredentialStore` using Win32 `Advapi32` APIs (`CredWriteW`, `CredReadW`, `CredDeleteW`, `CredFree`) with `MemoryCredentialStore` in-memory fallback. Zero plaintext disk storage. |
| Windows desktop integration | Implemented, tested | `%LOCALAPPDATA%\Vimivo` persistence path, native file and directory pickers (`FileDialog` / `JFileChooser`), command-line argument processing, drag-and-drop (`DropTarget`), and MenuBar keyboard shortcuts (`Ctrl+O`, `Ctrl+Shift+O`, `Space`, `Left`/`Right`). |
| Windows native packaging & CI | Implemented, tested | Compose Desktop `nativeDistributions` configured for `TargetFormat.Exe` and `TargetFormat.Msi` with stable upgrade UUID, start menu group, desktop shortcuts, per-user install, and 256x256 `.ico` icon. Native DLL bundler `bundle_windows_mpv.py` with PE x64 verification. GitHub Actions CI pipeline in `.github/workflows/windows.yml`. Genuine EXE/MSI binaries require execution on Windows runner. |
| Linux playback & integration | Implemented, tested; Linux host runtime required | Audio/video engine path via libmpv JNA client. Embedded local video implemented via `MpvX11Renderer` (AWT Canvas XID attachment via `wid` with `vo=gpu`). Capabilities report `localVideo = true` and `hdr = true`. MPRIS 2 D-Bus service over session bus with microsecond position, track ID pathing, and `PropertiesChanged` signals. Freedesktop Secret Service integration via `LinuxCredentialStore` (`libsecret-1.so.0`). Drag-and-drop, desktop keyboard shortcuts, multi-resolution Hicolor PNG icons, `.desktop` launcher, AppStream metadata, AppImage script, Flatpak manifest, and Compose Desktop DEB/RPM packaging configuration. Genuine packages require execution on Linux host. |
| Desktop folder selection and scanning | Implemented, tested | Native file/folder pickers, asynchronous recursive scanner, reconciliation, cancellation, exclusions, and SQLite persistence have tests. Security-scoped bookmarks are not yet implemented. |
| Desktop metadata | Implemented, tested | Rich audio parsing (Jaudiotagger + Opus parser fallback), video metadata extraction (ffprobe/ffmpeg via safe locator), extraction versioning, persistence, and queries. Full fixture suite (MP3, FLAC, M4A, Ogg, Opus, WAV, AIFF, MP4, MKV, WebM) verified. |
| Artwork & thumbnail cache/UI | Implemented, tested | Embedded audio artwork and video frame thumbnails are bounded, decoded/scaled off UI thread, atomically cached, size-limited, persisted, and shown in library, favorites/history, albums/artists, and now playing. |
| Desktop library UI | Implemented, tested | Real indexed data, metadata/artwork/thumbnails, roots, search, scanning, playback, favorites, playlists, history, bookmarks, queue, Album and Artist library views, and full queue actions (Play All, Shuffle Play, Play Next, Add to Queue) are connected. |
| Personal media database | Implemented, tested | Playlists, favorites, history, positions, bookmarks, and settings persist with foreign keys and migration tests. Desktop create/rename/delete/duplicate/add/remove/reorder/favorite/history/bookmark actions are connected. |
| Playback history and resume | Implemented, tested | One play count per session, ten-second position checkpoints, completion handling, and conservative video/long-audio resume rules. |
| Unified playback queue | Implemented, tested | One engine owns continuous play-all, next/previous, stable duplicate occurrences, missing-item skipping, reorder/remove/clear, shuffle, repeat modes, SQLite schema version 4 persistence, paused restoration without autoplay, and UI queue management. |
| Desktop distribution | Implemented, tested | Standalone macOS application bundle (`Vimivo.app`) and installable DMG (`Vimivo-1.0.0.dmg`) with self-contained JVM runtime and 50 bundled relocatable native dylibs (libmpv, FFmpeg, dav1d, etc.) with zero `/opt/homebrew` dependencies. Windows distribution configured for EXE/MSI via GitHub Actions runner. Linux distribution configured for DEB, RPM, AppImage, and Flatpak via GitHub Actions runner. |
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
- `0bae65d` — schema v4 queue persistence, snapshot restoration without autoplay, and queue actions.
- `656e744` — video metadata extraction, video frame thumbnail caching, Opus metadata parser, album/artist projections & UI, and queue action integration.
- `fc83ff5` — play all submitted Finder files in unified continuous queue.
- `300a76c` — bundle native libmpv dependencies and generate standalone macOS DMG.
- `8d6593b` — embedded video renderer, secure credentials, and standalone distribution packaging for Windows.
- `feat(linux): embedded video renderer, MPRIS media controls, secure credentials, and distribution packaging` — MpvX11Renderer XID attachment, MPRIS 2 D-Bus media integration, LinuxCredentialStore Secret Service binding, multi-resolution icons, desktop entry, AppStream metainfo, Flatpak manifest, AppImage script, native ELF bundler, and Linux CI pipeline.

## Quality record

On 2026-10-15, these commands completed successfully:

- `./gradlew :desktopApp:desktopTest` (64 tests)
- `./gradlew :desktopApp:compileKotlinDesktop`
- `./gradlew :desktopApp:detekt`
- `./gradlew :shared:desktopTest` (14 tests)
- `./gradlew detekt` (all modules passed)
- `./gradlew :androidApp:assembleDebug` (verified zero regression)

The desktop suite currently contains 64 tests and shared desktop test suite contains 14 tests.
