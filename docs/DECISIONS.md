# Architecture decisions

## ADR-000A: One MediaLibraryService playback authority

**Decision:** Host one `MediaLibrarySession` in `VimivoPlaybackService`. Adapt the existing engine manager to Media3's `Player` contract instead of creating separate Media3 and fallback-engine services.

**Reason:** One session prevents simultaneous playback and duplicate system entries while preserving fallback compatibility. Media3 supplies notification, lock-screen, media-button, foreground-service, and browsing behavior. UI code communicates only through a `MediaController`. Persisted queue state restores paused after process death.

## ADR-000: Room-backed Android media index

**Decision:** Use Room 2.8.5 behind common repository contracts. MediaStore remains authoritative for shared media; SAF is limited to user-selected URIs and trees. Store metadata and stable references, never full media copies.

**Reason:** Room provides schema validation, indexed paging, transactions, and Flow invalidation while leaving future platform adapters replaceable. Vimivo does not request all-files access; broad audio/video permissions remain optional because system pickers provide a no-permission path.

## ADR-001: Two Gradle modules initially

**Decision:** Use `androidApp` and `shared`, expressing finer boundaries as packages.

**Reason:** This preserves clean dependency direction and future portability without paying configuration and build costs for many near-empty modules. Split modules when ownership, isolation, or build measurements justify it.

## ADR-002: API 26 minimum, API 36 target

**Decision:** Android 8.0 is the minimum. Compile and target API 36.

**Reason:** API 26 supports Media3 and the libmpv engine (originally LibVLC, replaced per ADR-031) and offers modern notification/background primitives while retaining broad device coverage.

## ADR-003: Engine abstraction in common code

**Decision:** Media3 and a fallback engine (LibVLC at first, libmpv since ADR-031) are Android adapters behind `PlayerEngine`.

**Reason:** Playback policy and UI remain testable and portable. Capability reporting prevents assuming that both engines support identical behavior.

## ADR-004: DataStore now, database later

**Decision:** Persist preferences with AndroidX DataStore. Keep playlist/library persistence behind repository contracts and defer the database choice until its schema is defined.

**Reason:** DataStore is the official fit for small settings. Premature database integration would create migrations without a validated model.

## ADR-005: Toolchain compatibility baseline

**Decision:** Kotlin 2.3.20, Compose Multiplatform 1.9.3, AGP 8.13.2, Gradle 8.13, JDK 17, Koin 4.2.2, Media3 1.11.1, Coil 3.4.0, and stable LibVLC 3.7.7.

**Reason:** AGP 8.13.2 explicitly supports Kotlin 2.3 and API 36; Compose 1.9.3 and Coil 3.4.0 share a compatible baseline. LibVLC 4 remains EAP, so the latest stable 3.x release is used. Version upgrades require dependency resolution, tests, and an Android build.

## ADR-006: Configurable pre-release application ID

**Decision:** Default to `dev.vimivo.app`, overridable with `vimivo.applicationId` until publication.

**Reason:** The working product name may change. After first publication the chosen ID must never change because Android/store upgrade identity depends on it.

## ADR-007: Bounded Media3-to-fallback-engine fallback

**Decision:** Prefer Media3. Fall back once to the fallback engine (libmpv since ADR-031; LibVLC before) only for unsupported-container, decoder-initialization, or decode failures.

**Reason:** Retrying missing, unauthorized, or unreachable sources in another engine cannot fix the source and can hide actionable errors. Tracking attempted engines eliminates loops. Switching closes the previous engine first, preventing concurrent playback.

## ADR-008: Native ABI and page-size policy

**Decision:** Package `arm64-v8a`, `armeabi-v7a`, and `x86_64`; exclude obsolete x86. The fallback engine's native libraries must be built for all three (LibVLC 3.7.7 until ADR-031, now the libmpv package).

**Reason:** The selected AAR officially publishes all four ABIs. ARM64 and x86_64 cover modern devices and emulator testing; ARMv7 remains supported by the application minimum. Inspection found 16 KB-aligned (`2**14`) ARM64 ELF load segments, and the generated APK passes `zipalign -P 16`. Runtime testing on a 16 KB emulator/device remains required.

## ADR-009: One music state path through the playback service

**Decision:** All music surfaces use the service-backed `PlaybackController` and `PlaybackViewModel`; collection screens resolve queue items through `MediaRepository` and never create an engine or player.

**Reason:** A single command and observation path keeps the mini player, Now Playing, notification, queue, and external media controls synchronized across recreation and backgrounding.

## ADR-010: Media schema version 2 records release year

**Decision:** Add nullable `releaseYear` to indexed media with a Room auto-migration from schema 1 to 2.

**Reason:** Album details can expose available local release metadata without inventing values. The nullable additive column preserves existing libraries safely, and the exported schema is committed for migration verification.

## ADR-011: Video surfaces remain session-owned

**Decision:** The full-screen video UI renders the existing service session player and uses a process-local diagnostics bridge only for engine-specific track/capability state. Media3 and LibVLC both attach through the session adapter; Compose never constructs an engine.

**Reason:** This preserves one playback lifecycle across fullscreen, PiP, backgrounding, configuration changes, and fallback while still allowing track selection and accurate decoder information.

## ADR-012: Capability-gated subtitle controls

**Decision:** Keep tracks, external subtitle references, and delay commands in shared playback contracts, but render and expose adjustments only when the active engine reports support. SAF tree grants are the sole source for sibling-file auto-detection.

**Reason:** Media3 and LibVLC have materially different subtitle renderers and timing APIs. Capability gating avoids ineffective controls and false claims, while SAF-only discovery respects scoped storage.

## ADR-013: Local-only organization and schema version 3

**Decision:** Add bookmarks, searchable filenames, playlist-name indexes, and duration/MIME indexes through an additive Room auto-migration from version 2 to 3. Keep playlists, favorites, history, positions, counts, and bookmarks solely on device. Preserve playlist references when media becomes unavailable, and cascade bookmarks only when a media record is explicitly removed.

**Reason:** Removable storage and revoked SAF grants are often temporary, so deleting organization data during reconciliation would be destructive. Indexed prefix queries remain responsive without introducing a second search database. Embedded metadata remains read-only because MediaStore and generic SAF URIs do not provide a universally reliable tag-writing transaction; Vimivo will not modify a user file without a provider-specific capability and explicit confirmation.

## ADR-014: Explicit-origin network streaming

**Decision:** Add Media3 HLS and DASH modules and keep network playback inside the single MediaLibraryService. HTTPS is preferred. Cleartext is enabled at the application network-policy boundary solely because user-authorized URLs may name arbitrary internet-radio or LAN hosts that cannot be represented by a static domain allowlist. Network requests begin only after an explicit play/retry/history action, URLs never go to an intermediary, embedded URL credentials are rejected, and automatic reconnect is limited to three transport-failure attempts.

**Reason:** A second network player would break the authoritative session, while a fixed cleartext domain list would make legitimate local-network sources impossible. Bounded retry avoids loops and deliberately excludes HTTP status, expired-link, access, and manifest errors.

## ADR-015: Explicit local backup instead of platform backup

**Decision:** Disable Android automatic application backup and provide a versioned JSON export/import selected through SAF. Backups contain settings and stable URI references for playlists, favorites, stream favorites, bookmarks, and history, but never media bytes, credentials, cached artwork, or persisted URI grants.

**Reason:** Automatic platform backup can move private library history to a cloud account without an in-app action. Explicit local files match Vimivo's privacy model, remain inspectable and portable, and allow missing references to be reported rather than silently corrupting relationships.

## ADR-016: Session-bound, capability-gated audio enhancement

**Decision:** Apply Android `Equalizer` and `BassBoost` only to the active Media3 audio session, release them whenever the session, route, or engine changes, and expose controls only from reported engine/device capabilities. LibVLC currently reports these controls unavailable. ReplayGain, crossfade, balance, mono routing, forced output selection, and bit-perfect output remain unexposed until an engine path can implement and verify them.

**Reason:** Android DSP support varies by device, route, and engine. Session ownership prevents effects leaking into later playback, while capability gating avoids settings that silently do nothing or imply output guarantees that Android's mixer cannot generally provide.

## ADR-017: Conservative remote-source transport policy

**Decision:** Use Media3's maintained RTSP module for RTSP/RTSPS playback and an HTTPS-only WebDAV browser implemented with platform networking. Persist remote endpoints without passwords and reject credentials embedded in URLs. SMB, SFTP, and DLNA ContentDirectory remain capability placeholders until their protocol transports, authentication, and playback data sources can be integrated end-to-end without insecure fallback.

**Reason:** A browser that cannot securely authenticate and provide the selected bytes to the active playback engine would be misleading. HTTPS WebDAV and RTSP offer useful, maintainable access today; SMB1, FTP, disabled SSH host-key validation, and plaintext credential persistence are explicitly excluded.

## ADR-018: macOS Compose Desktop with a JNA libmpv boundary

**Decision:** Add one `desktopApp` JVM target, reuse `shared/commonMain`, and access libmpv through JNA 5.19.1. The adapter uses `mpv_command` argument arrays, owns one native handle, polls bounded state at 250 ms, and always calls `mpv_terminate_destroy`. Missing libmpv is an explicit playback error. The first increment lets libmpv own its output window rather than shipping an unverified native rendering bridge.

**Reason:** Compose Desktop provides portable Windows-ready presentation and native macOS packaging, while libmpv provides real codec, subtitle, track, and network playback. Keeping JNA and file dialogs in `desktopApp` preserves the common boundary and avoids shell-command injection or desktop APIs in shared code.

## ADR-019: macOS uses a narrow JAWT render bridge

**Decision:** Keep libmpv controls in the JNA `MpvPlayerEngine`, but render its frames through a locally built Objective-C++ bridge. The bridge obtains Compose's AWT surface through JAWT, installs a `CAOpenGLLayer`, creates one libmpv OpenGL render context, and releases that context before the engine handle. It is arm64-tested against Homebrew libmpv 0.41.0.

**Reason:** Cocoa's supported `wid` embedding expects an `NSView`, whereas AWT's supported macOS embedding surface is Core Animation based. The libmpv render API is the supported way to render into that surface without launching a second player window. OpenGL is deprecated by macOS, so this is intentionally a feasibility bridge, not a promise of a future Metal renderer.

## ADR-020: iOS is a first-class release target with native SwiftUI over shared Kotlin logic

**Decision:** iOS ships alongside Android, macOS, Windows, and Linux. The iOS UI stays native SwiftUI; all non-UI logic moves into the `VimivoShared` framework and is consumed from Swift (thin Swift wrappers today; SKIE remains an option once Flows and suspend functions cross the boundary). Xcode links the framework from `shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)` via `embedAndSignAppleFrameworkForXcode`, never a hard-coded simulator path. The first shared pieces are `ResumePolicy` and `PlaybackSpeeds`, used by Android, desktop, and iOS.

**Reason:** The SwiftUI app already covers most of the mobile UX, so rewriting it in Compose Multiplatform would cost months without user benefit, while divergent business rules (three different resume rules existed) are a correctness problem. Sharing rules, persistence, and indexing removes that divergence without touching the native UI.

## ADR-021: Shared persistence uses Room KMP with the bundled SQLite driver (proposed)

**Decision (proposed, pending review):** Move playback history, resume positions, bookmarks, queue, playlists, smart-playlist rules, per-media preferences, and FTS search into a Room 2.8.5 database in `shared/commonMain`, using `androidx.sqlite:sqlite-bundled` 2.6.2 (the SQLite line Room 2.8.5 depends on) for iOS and JVM. Android keeps its existing Room database until its entities move, with a migration test per step. iOS imports its legacy `vimivo.db` rows once, non-destructively, then stops writing them.

**Reason:** Room is already the Android persistence layer, is maintained by Google, and publishes iOS and JVM artifacts, so one schema with exported JSON migrations can serve every platform. A spike on 2026-10-18 compiled a Room entity, DAO, and `@ConstructedBy` database into the iOS simulator framework with Kotlin 2.3.20 and KSP 2.3.12, passed a JVM round-trip test with `BundledSQLiteDriver`, and exported its schema; it added about 4.7 MB to the unstripped debug static framework. SQLDelight was the alternative, but it would introduce a second persistence stack next to Android's Room. Desktop keeps its JDBC schema (ADR-003) until the shared schema reaches parity. Licenses: Room and androidx.sqlite are Apache-2.0; bundled SQLite is public domain.

**Known cost:** `shared/commonMain` exports Compose Material 3 and the extended icon set, which inflates the iOS debug framework (~322 MB unstripped) although the SwiftUI app does not use them. Moving shared Compose UI into a UI-only source set is tracked separately.

## ADR-022: macOS ships one signed, notarized DMG per architecture

**Decision:** CI builds `Vimivo-<version>-arm64.dmg` on `macos-15` and `Vimivo-<version>-x86_64.dmg` on `macos-15-intel`, each bundling that runner's Homebrew libmpv (prefix detected or set through `VIMIVO_MPV_PREFIX`). When the signing secrets exist, every bundled dylib, the launcher, and the app are signed inside-out with the Developer ID, the hardened runtime, and `desktopApp/macos/Vimivo.entitlements`; the DMG is then notarized and stapled.

**Reason:** A universal DMG is not technically feasible with Compose Desktop: `jpackage` bundles an architecture-specific Java runtime, and libmpv's ~50 Homebrew dependencies would each need a fat build. Two native DMGs is the supported Compose Desktop approach and keeps every binary tested on its own architecture. The entitlements are the minimum the JVM (JIT) and JNA (runtime-extracted dispatcher) need; the app is not sandboxed because it is distributed outside the App Store.

## ADR-023: libmpv renders lazily and receives filesystem paths

**Decision:** `MpvPlayerEngine` initializes libmpv without a renderer. A renderer is attached only when the video canvas is displayable: video waits up to five seconds after publishing its Preparing state (so the UI composes the surface), while audio and streams never block. Local `file:` URIs of any form are handed to libmpv as filesystem paths (`mpvTarget`), and a file that never loads is reported as a playback error instead of an idle "Ready" state. The macOS bridge creates and owns the CGL context it shares with Core Animation, makes it current while creating and freeing the libmpv render context, and is compiled with ARC.

**Reason:** Verification on 2026-10-18 showed four defects on the path users take when opening a file from Finder or the command line: JAWT crashed the JVM on a canvas without a peer, the render context could never be created because no OpenGL context was current (so in-window macOS video had never rendered), `File.toURI()`'s `file:/path` form is rejected by libmpv, and the non-ARC bridge sent messages to released objects on shutdown.

## ADR-024: Windows media controls use a C++/WinRT bridge

**Decision:** `WindowsMediaControlsService` drives the System Media Transport Controls (media flyout, lock screen, hardware media keys) through `native/windows/VimivoMediaControls.cpp`, compiled with MSVC in CI and bundled next to libmpv. It uses a `Windows.Media.Playback.MediaPlayer` with its command manager disabled, so no window handle is required, and is created only on Windows. Any failure leaves playback working and is recorded in the local diagnostic buffer.

**Reason:** SMTC is a WinRT API. C++/WinRT type-checks every call against the Windows SDK, which is far safer than hand-written COM vtables through JNA. The bridge is compiled in CI but has not yet been exercised on a Windows desktop; it stays in the release checklist until it has.

## ADR-025: Every desktop build is smoke-tested by playing real media

**Decision:** `--smoke-test <files…>` launches the real window and renderer with an in-memory database, plays each file until it advances 1.5 s (requiring a decoded video track for video), prints `VIMIVO_SMOKE PASS|FAIL` lines (also written to `VIMIVO_SMOKE_REPORT`), and exits non-zero on any failure. `scripts/verify_all.sh` runs it locally, and the macOS, Windows, and Linux workflows run it against the packaged app. iOS exposes a Debug-only `-VimivoOpenURL` launch argument for the same purpose.

**Reason:** Unit tests passed while launch-time playback was broken on macOS. Exercising the packaged binary catches bundling, loader, and renderer defects that only appear in a real process, without screen-recording permissions or user data.

## ADR-026: Desktop player layer with Koin, typed HUD and localized strings

**Decision:** `PlayerViewModel` owns playback, queue, launch handling, HUD feedback and OS media-control updates for the desktop app; the UI renders a single immutable `PlayerState` and sends `PlayerIntent`s. Koin (`desktopPlayerModule`, koin-core only) builds the graph; `isolated = true` swaps in in-memory stores for verification runs. HUD messages are typed (`HudMessage`) and rendered from Compose resources in English and Bangla (`composeResources/values`, `values-bn`); a test keeps both languages in sync. `kotlinx-coroutines-swing` makes `Dispatchers.Main` the Swing event thread.

**Reason:** Business logic lived in `Main.kt` composables, which made launch, single-instance and queue behaviour untestable. koin-compose 4.2.2 is excluded because it upgrades Compose to 1.10 and mismatches Skiko's 1.9.3 natives (verified: `UnsatisfiedLinkError` in `MetalApiKt`).

## ADR-027: Files from the OS open in one instance, straight into the player

**Decision:** The first desktop process holds `instance.lock` and listens on a Unix-domain socket (macOS, Linux, Windows 10+) in the per-user data folder, or in the temp folder when that path exceeds 100 bytes. Later launches forward `files`, `--start` and `--fullscreen` as JSON and exit. Opened video goes directly to the player stage, audio to the Now Playing card; one file brings its same-kind folder siblings in natural order ("ep2" before "ep10"); several files keep their order; M3U/M3U8/PLS/XSPF expand; CUE goes to libmpv. Local files resume through the shared `ResumePolicy` (schema v7 stores positions for files outside the library) with a 4-second "Start over" toast. File types come from one table, `media-types.tsv`, used by Gradle (macOS UTIs and imported types, Windows/Linux associations) and at runtime. Windows registers per user under HKCU (ProgIDs, OpenWithProgids, Capabilities, RegisteredApplications) on every start; `--unregister-file-types` removes it. "Make Vimivo the default player" opens Default Apps on Windows, runs `xdg-mime` after confirmation on Linux, and shows Finder steps on macOS.

**Reason:** Operating systems never let apps take default-handler status silently, and per-user registration needs no administrator rights. Verified: macOS forwarding (second launch exits 0, running app opens the file, socket removed on quit); Linux forwarding through `playerctl` in Docker; macOS "Open With" lists Vimivo for mkv/mp4/mp3/flac/opus/srt/m3u8.

## ADR-028: libmpv runtime requirements found on Linux

**Decision:** Before every `mpv_create` the engine resets `LC_NUMERIC` to "C" through libc (`MpvLocale`), sets `audio-fallback-to-null=yes`, keeps the video surface composed (collapsed) for the engine's lifetime, stops video output instead of changing `wid` on detach, and closes the player before any window is disposed. Embedded tags (`metadata/by-key/*`) are published as `PlaybackState.metadata` so Now Playing, SMTC and MPRIS show artist/album for files opened outside the library.

**Reason:** Each was a reproduced failure in a Linux container: libmpv refuses non-C numeric locales (every user with e.g. `en_US.UTF-8`), playback stalled without an audio device, and destroying the embedded X11 window under libmpv aborted the process with `BadWindow`.

**Known issue:** after video was shown, process exit under Xvfb still prints an X11 `BadWindow` and exits with status 1. libmpv is already destroyed at that point (traced), and the error persists with Skiko's software renderer and with `vo=null` before teardown, so the remaining X client is not yet identified. Playback, persistence and shutdown ordering are unaffected; CI judges smoke tests by their PASS lines. Needs a real Linux desktop (X11 and Wayland) to confirm.

## ADR-029: Startup measurement and the base CDS archive

**Decision:** `VIMIVO_STARTUP_TRACE=1` prints `window` and `firstFrame` milestones relative to process start. Packaging runs `-Xshare:dump` through the app's launcher (`dumpBaseCdsArchive`) to restore the JDK base archive jlink drops; no speculative heap or GC flags are added.

**Reason:** JDK 17 application archives (`-XX:ArchiveClassesAtExit`) record absolute classpath paths and are silently ignored once the app is installed elsewhere; automatic, relocatable archives need JDK 19+, which is a toolchain change for a later decision. On 2026-10-18 the development Mac was under a load average of 16–20 from other applications, so timings varied 2–3× between runs and are not reported as measurements.

## ADR-030: Separate player and library windows on desktop

**Decision:** Desktop has two window types. The player window opens for files (OS "Open with", command line, second launch, drag and drop, Open File, links) and shows only the player: video on a black stage, or the audio layout, with a control bar and an optional Video/Bookmarks side panel. The library window opens on a plain launch, from the player's Library button, or from View ▸ Library (primary+L) and Navigate. The player window is always composed and only hidden when idle, so its native video surface (one `Canvas`, black, attached to libmpv once at startup) lives as long as the engine (ADR-028). Player persistence (resume, history, queue, bookmarks, settings) goes through `DesktopPlaybackStore`, which shares the database with the library but never indexes or scans; `DesktopLibraryController` is a lazy Koin single created only by the library window, and it refreshes itself from the store's change stream. Closing the player stops playback and hides it; closing the last window quits on Windows and Linux, while macOS stays in the Dock and reopens the library when the Dock icon is clicked. A second launch routes its files to the running player (Replace or Add to queue, per Launch settings). "Open in a new window" is not offered: there is one playback engine per process, and a second engine would need a second libmpv context and audio device arbitration.

**Reason:** Opening a file previously composed the full library shell: Home (its default destination) rendered beneath the audio card, the bookmarks panel appeared on every page, the library controller opened and refreshed the database, and the video `SwingPanel` used its default white background, which showed as a white box before the first frame and for audio. A unit test (`DesktopPlayerModuleTest.playerGraphNeverCreatesTheLibrary`) and the startup trace (`library` milestone absent when a file is opened) guard the split.

## ADR-031: libmpv replaces LibVLC as the Android fallback engine

**Decision:** Android's fallback engine is Vimivo's own LGPL-only libmpv 0.41.0 + FFmpeg 9.0.2 (`scripts/build_media` release `media-2`: arm64-v8a, armeabi-v7a, x86_64), behind the same `PlayerEngine` contract, selected by the unchanged engine manager. LibVLC is removed from the build, the settings enum (`PreferredEngine.LIBVLC` becomes `LIBMPV`; stored values migrate on read), the notices and the ProGuard rules. Details and the iOS plan: [ADR-005](adr/ADR-005-MOBILE-MEDIA-ENGINE.md).

**Reason:** LibVLC 3.7.7 contains GPL modules (x264, dvdnav/dvdread, postproc), so the Android app could not ship under MIT. One libmpv/FFmpeg stack with one verified licence switch and one filter set now serves desktop and Android. Automatic decoding uses software because FFmpeg's MediaCodec path stalled on the emulator's H.264 decoder; MediaCodec remains the explicit hardware choice until real-device testing says otherwise.
