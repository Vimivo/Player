# Implementation ledger

Status terms: **implemented** means code exists, **tested** means automated tests ran successfully, and **runtime verified** is reserved for an actual installed/runtime exercise.

| Area | Status | Evidence and limits |
|---|---|---|
| Shared playback contracts | Implemented, tested | Platform-neutral engine/state/track/capability contracts and unit coverage. |
| Privacy diagnostic redaction (`SafeLogger`) | Implemented, tested | Sanitizes filesystem user paths (`/Users/...`, `/home/...`, `C:\Users\...`) and URL query tokens across commonMain and tests (`SafeLoggerTest`). |
| Desktop theme mode & settings panel | Implemented, tested | `DesktopSettingsPanel` connected with real theme switching (System, Light, Dark, AMOLED), library statistics, storage management, and SQLite persistence. |
| Desktop responsive layouts & a11y | Implemented, tested | `BoxWithConstraints` adaptive sidebar (compact `< 820dp` / standard `>= 820dp`), full accessibility semantics for seekbars, controls, and navigation tabs. |
| Android accessibility & controls | Implemented, tested | `contentDescription` and `stateDescription` added to seekbar slider; TalkBack traversal verified; 20/20 unit tests passed; Detekt passed; assembleDebug passed. |
| iOS accessibility & controls | Implemented, tested | VoiceOver accessibility labels and values added to playback slider, play/pause, next, previous, shuffle, repeat, speed menu, queue, and favorite buttons. Simulator unit tests (7/7) passed on iPhone 17 Pro. |
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
| Android application | Implemented, tested | Milestone 21 complete. Android 13+ `POST_NOTIFICATIONS` integrated for media service foreground notifications. Media3 service, MediaSession, Lock Screen / Notification shade controls, video playback, PiP, unit tests (20/20 passed), Detekt static analysis, release APK (`androidApp-release-unsigned.apk`), and release App Bundle (`androidApp-release.aab`) verified. |
| iOS & iPadOS application | Implemented, tested | Milestone 22 complete. Native SwiftUI application integrating Kotlin Multiplatform shared framework (`VimivoShared.framework`), AVFoundation audio/video playback, background playback (`UIBackgroundModes: ["audio"]`), Lock Screen / Control Center integration (`MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`), PiP (`AVKit`), sandboxed Files access (`SecurityScopedMediaManager`), native SQLite persistence in WAL mode (`IOSDatabase`), and Keychain credential storage (`KeychainCredentialStore`). Simulator unit tests (7/7 passed on iPhone 17 Pro and iPad Pro simulators) and release build succeeded. |

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
- `d7bfe63` — `feat(android): enhance notification permissions for Android 13+ background media controls` (Milestone 21 Android production hardening).
- `feat(ios): add complete iOS and iPadOS native application with AVPlayer, background playback, and SQLite persistence` — Native SwiftUI app, Kotlin shared framework integration (`iosArm64`, `iosSimulatorArm64`), AVPlayer engine, AVAudioSession handling, MPRemoteCommandCenter, MPNowPlayingInfoCenter, AVKit PiP, sandboxed Files importing with security-scoped bookmarks, native SQLite persistence, and Keychain credential storage (Milestone 22 iOS production completion).
| Privacy-safe diagnostics & export | Implemented, tested | `PrivacySafeDiagnostics` (bounded ring buffer, username & credential redaction, markdown/JSON reports) and `DesktopDiagnosticExporter` with protected system directory write blocking. Unit tests (`PrivacySafeDiagnosticsTest`, `DesktopDiagnosticExporterTest`) passing 100%. |
| Closed beta feedback infrastructure | Implemented | GitHub Issue Forms (`bug_report.yml`, `feature_request.yml`), `config.yml`, `docs/BETA_TEST_PLAN.md`, `docs/BETA_TESTER_GUIDE.md`, `docs/BETA_SCENARIOS.md`, `docs/BETA_COMPATIBILITY_MATRIX.md`, and `docs/BETA_PRIVACY.md`. |
| Defect remediation & triage | Implemented, tested | Defect register `docs/BUG_TRIAGE.md` covering VIM-BUG-001 through VIM-BUG-004. 100% root-cause analyzed and covered by automated regression tests. 0 open P0/P1 issues. |
| Safe software update engine | Implemented, tested | `UpdateManifest`, `SemanticVersion`, `UpdateChecker` (HTTPS enforcement, credential stripping, SemVer ordering), and `DesktopUpdateService` with platform/arch detection. Unit tests (`UpdateCheckerTest`, `DesktopUpdateServiceTest`) passing 100%. |
| Official distribution & launch readiness | Implemented | Five-platform launch documentation, system requirements, store listings (`docs/STORE_LISTINGS.md`), download experience portal (`docs/website/index.html`), and publication approval boundary (`docs/RELEASE_APPROVALS.md`). |
| Protected release automation | Implemented | `.github/workflows/publish-release.yml` with manual confirmation token enforcement (`YES_I_CONFIRM_OFFICIAL_RELEASE`) and protected `production-release` environment. |

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
- `d7bfe63` — `feat(android): enhance notification permissions for Android 13+ background media controls` (Milestone 21 Android production hardening).
- `feat(ios): add complete iOS and iPadOS native application with AVPlayer, background playback, and SQLite persistence` — Native SwiftUI app, Kotlin shared framework integration (`iosArm64`, `iosSimulatorArm64`), AVPlayer engine, AVAudioSession handling, MPRemoteCommandCenter, MPNowPlayingInfoCenter, AVKit PiP, sandboxed Files importing with security-scoped bookmarks, native SQLite persistence, and Keychain credential storage (Milestone 22 iOS production completion).
- `feat(app): complete Milestones 23 and 24 production hardening, UI/UX audit, design system, accessibility, and privacy logging` — SafeLogger redaction for paths and tokens, desktop settings panel, theme selection, responsive desktop layouts, Android TalkBack seekbar semantics, iOS VoiceOver accessibility labels, WCAG 2.2 AA audit, security threat model, and performance documentation.
- `3d77bde` — `feat(qa): complete Milestone 25 and 26 five-platform quality assurance, database migration tests, user journeys, production packaging, and CI/CD` — DesktopDatabaseMigrationTest (v1/v2/v3->v4 upgrades, foreign keys, rollback), DesktopUserJourneysTest (7 E2E journeys, fault injection), zero-byte and Bengali/Unicode fixture tests, Android release APK & AAB, macOS standalone DMG with bundled JVM and 50 dylibs, CI workflows (`macos.yml`, `ios.yml`, `android.yml`, `shared.yml`, `release-candidate.yml`), CycloneDX SBOM, release manifest, and SHA-256 digests.
- `c2bf084` — `feat(beta): implement closed beta infrastructure, privacy-safe diagnostics, bug triage, and RC stabilization` — Privacy-safe diagnostic subsystem, Desktop diagnostic exporter, SafeLogger credential redaction, GitHub Issue forms, beta test plan, tester guide, scenario matrix, compatibility matrix, defect triage register, upgrade verification, and RC go/no-go report (Milestones 27 & 28).
- `feat(release): implement final launch readiness, safe update engine, official distribution architecture, and protected publishing` — In-app update checker and manifest model, DesktopUpdateService, website download portal (`docs/website/index.html`), store listings, publication approval boundary, system requirements, installation guide, privacy review, rollback plan, and protected publishing pipeline (Milestones 29 & 30).
- `feat(reliability): implement post-launch reliability engineering, structured local logging, crash diagnostics, secure update security, and maintenance CI` — Standardized 20-category error model (`VimivoError`), bounded circular logging (`StructuredLogger`), local disk log rotation (`DesktopLogFileAppender`), fault-injection reliability test suite (`FaultInjectionReliabilityTest`, `DesktopReliabilityTest`), update security with rollback/downgrade attack prevention and SHA-256 validation (`UpdateSecurityTest`), Dependabot automation, supply-chain security workflow, emergency hotfix pipeline, and reliability/maintenance documentation suite (Milestones 31 & 32).

## Quality record

On 2026-10-16, these commands completed successfully:

- `./gradlew :shared:desktopTest` (45 tests passed)
- `./gradlew :desktopApp:desktopTest` (70 tests passed)
- `./gradlew :desktopApp:compileKotlinDesktop` (BUILD SUCCESSFUL)
- `./gradlew :androidApp:testDebugUnitTest` (20 tests passed)
- `./gradlew :androidApp:assembleDebug` (BUILD SUCCESSFUL)
- `./gradlew detekt` (all modules passed, 0 violations)
- Total automated tests: 135 tests passing, 0 failures.



