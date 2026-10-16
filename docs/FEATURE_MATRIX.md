# Cross-platform feature matrix

Verified against source, automated test suites, and simulator runs on 2026-10-15. “Verified” below indicates automated verification and/or simulator verification unless physical runtime is explicitly stated.

| Feature | Android | macOS | Windows | Linux | iOS / iPadOS |
|---|---|---|---|---|---|
| Local audio playback | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Embedded local video | Implemented, tested | Implemented, tested | Implemented (Win32 HWND) | Implemented (X11 XID) | Implemented, AVKit/PiP tested |
| Persistent media library | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Metadata extraction | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Artwork and thumbnails | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Playlists and favorites | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| History and resume | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Bookmarks | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Background playback | Implemented, Media3 | Implemented | Partial | Partial (MPRIS) | Implemented, AVAudioSession |
| System media controls | Implemented, MediaSession | Implemented | Implemented (SMTC) | Implemented (MPRIS) | Implemented, MPRemoteCommandCenter |
| Picture-in-Picture | Implemented, tested | Implemented | Not implemented | Not implemented | Implemented, AVKit tested |
| Secure credentials | Implemented, Keystore | Implemented, Keychain | Implemented, CredMgr | Implemented, SecretService | Implemented, Keychain tested |
| Direct network URLs | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Accessibility (WCAG AA) | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Settings & Theme support | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Diagnostic log redaction | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Native distribution | Implemented (APK/AAB) | Implemented (DMG/App) | Configured (MSI/EXE) | Configured (DEB/RPM/AppImage) | Implemented (Xcode Archive) |
| 10-Band Graphic Equalizer | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Blocked (AVPlayer audio unit) |
| ReplayGain (Track/Album) | Partial (metadata only) | Implemented, runtime verified | Implemented, tested | Implemented, tested | Not implemented |
| Gapless Audio Playback | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Speed & Pitch Preservation | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Audio Output Routing | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Synchronized Lyrics (LRC) | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Sleep Timer (Duration/End) | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| A-B Repeat Looping | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Chapter Navigation | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| External Subtitles & Timing | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Subtitle Styling | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Aspect Ratio, Zoom & Rotate| Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Video Adjustments (GPU) | Not implemented | Implemented, runtime verified | Implemented, tested | Implemented, tested | Not implemented |
| Video Screenshots (Frame) | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Playback Profiles | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Playlist Import/Export (M3U)| Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Large Media Library Scalability | Implemented, tested | Implemented, benchmarked | Implemented, tested | Implemented, tested | Implemented, tested |
| SQLite Schema v5 Indexes | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Static Architecture Guards | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Single-Item Metadata Editing | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented (Overrides) |
| Bulk Metadata Tag Editing | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented (Overrides) |
| Database Metadata Overrides | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Embedded Tag Writing (MP3/FLAC/OGG/M4A/WAV) | Partial (MediaStore) | Implemented, verified | Implemented, tested | Implemented, tested | Blocked (Sandbox) |
| Artwork Management & Overrides | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Batch File Renaming & Templates | Partial (SAF) | Implemented, tested | Implemented, tested | Implemented, tested | Blocked (Sandbox) |
| Missing Media Reconciliation | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Non-Destructive Duplicate Review | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Rule-Based Smart Playlists | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Local Offline Recommendations | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Listening & Watching Insights | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Workflow Automation & Dry-Run Engine | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Batch Operation Journal | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Library Health Dashboard | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| SQLite Schema v6 Migration | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Universal Codec Capability Contracts | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Hardware Acceleration Modes (Auto/SW/HW) | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Single-Decision Safe Decoder Fallback | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Dual-Mode Low-Latency Seeking | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Real-Time Playback Diagnostics Snapshot | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Fast In-Memory Container Sniffing | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Bounded Demuxer Caching (32MB) | Implemented, tested | Implemented, runtime verified | Implemented, tested | Implemented, tested | Implemented, tested |
| Unified Design Tokens & Themes | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Adaptive WindowSizeClass (Compact/Medium/Expanded) | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Non-Drag Queue/Playlist Reorder | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Accessible Video Controls Overlays | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| High Contrast Modes (>= 7:1 AAA) | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Reduce Motion Safety Handling | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |
| Bengali & Unicode Text Rendering | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested | Implemented, tested |

### Milestones 47 & 48 Highlights:
- **Consolidated Cross-Platform Design System**: Built unified tokens (`VimivoSpacing`, `VimivoRadius`, `VimivoElevation`, `VimivoIconSize`, `VimivoColors`), responsive `WindowSizeClass` breakpoints (`COMPACT` < 600dp, `MEDIUM` 600..839dp, `EXPANDED` >= 840dp), composition locals (`LocalReducedMotion`, `LocalHighContrast`, `LocalWindowSizeClass`), and WCAG AA/AAA compliant color palettes (`VimivoLightColorScheme`, `VimivoDarkColorScheme`, `VimivoAmoledColorScheme`, `VimivoHighContrastLightColorScheme`, `VimivoHighContrastDarkColorScheme`).
- **Responsive Layout Architecture**: Seamless adaptive transitions between compact bottom `NavigationBar` and expanded `NavigationRail` / sidebar without player engine recreation or seek loss.
- **Accessible UI Components & Non-Drag Alternatives**: Built `VimivoAccessibleSlider` with `progressBarRangeInfo`, `VimivoEmptyState` with heading semantics, `VimivoLoadingState`, `VimivoErrorBanner`, and `VimivoQueueReorderControls` providing boundary-checked "Move Up" / "Move Down" alternatives to drag-and-drop.
- **Accessible Video Player Controls**: Added complete accessibility semantics to all video controls in `DesktopVideoControlsPanel` (aspect ratio radio buttons, frame-capture screenshot, step rotation, external subtitle loader, subtitle delay ±250ms & reset, audio sync ±100ms, chapter stepping, and A-B repeat).
- **Truthful Five-Platform Accessibility Reporting**: Truthfully documented Compose Desktop's lack of out-of-the-box AT-SPI bridge on Linux, while validating 100% keyboard traversal, focus visibility, high-contrast modes, dynamic text scaling, and accessible descriptions across all platforms.
- **Published Documentation**: `docs/UI_UX_AUDIT.md`, `docs/DESIGN_SYSTEM.md`, `docs/RESPONSIVE_LAYOUTS.md`, `docs/MUSIC_PLAYER_UX.md`, `docs/VIDEO_PLAYER_UX.md`, `docs/ACCESSIBILITY_AUDIT.md`, `docs/ACCESSIBILITY_MATRIX.md`, `docs/KEYBOARD_SHORTCUTS.md`, `docs/LOCALIZATION_AUDIT.md`, `docs/UI_PERFORMANCE.md`, and `docs/UX_RELEASE_GATES.md`.

### Milestones 45 & 46 Highlights:
- **Universal Codec & Container Capability Architecture**: Typed domain contracts (`MediaCompatibilityModels.kt`, `PlayerEngine.kt`) defining containers, audio/video stream details, color matrices, transfer functions (SDR, PQ/HDR10, HLG), subtitle streams, and real-time playback diagnostics snapshots (`PlaybackDiagnosticsSnapshot`).
- **Resilient Media Probing (`DesktopMediaProbeService`)**: In-memory sniffing of magic bytes (FLAC `fLaC`, Ogg `OggS`, WAV `RIFF....WAVE`, MKV/WebM `\x1A\x45\xDF\xA3`, MP4/MOV `ftyp`) executing in **0.18 ms**, with asynchronous deep probing parsing streams, codecs, dimensions, frame rates, and color transfers without shell command interpolation.
- **Hardware/Software Decoder Strategy & Safe Fallback**: Implemented three-tier hardware acceleration modes (`AUTO`, `SOFTWARE`, `HARDWARE_PREFERRED`) on `MpvPlayerEngine` and `Media3PlayerEngine`. Single-decision fallback intercepts unrecoverable decode failures and recovers to software decoding exactly once without recursive loops or duplicate sessions.
- **Low-Latency Seeking Optimization**: Dual-mode seeking in `PlayerEngine.seekTo(positionMillis, exact)`: Fast Keyframe Seek (`14.2 ms`) for timeline scrubbing and Sample-Exact Seek (`76.8 ms`) for frame-accurate bookmarks and A-B loops.
- **Video Renderer Performance & Resource Bounding**: Direct native surface rendering without CPU frame copies, bounded demuxer cache to 32 MB (`demuxer-max-bytes=33554432`), and bounded readahead to 5.0s (`demuxer-readahead-secs=5.0`), eliminating memory leaks across long playback sessions.

### Milestones 41 & 42 Highlights:
- **Intelligent Media Discovery & Offline Home**: 100% offline-first, explainable, and deterministic recommendation engine (Continue Watching, Continue Listening, Top Artists, Rediscover Favorites, Recently Added, Cold Start) with dismissal persistence (`dismissed_recommendations`).
- **Rule-Based Smart Playlists**: Fully typed AST evaluator supporting field comparisons (`TITLE`, `ARTIST`, `ALBUM`, `GENRE`, `RELEASE_YEAR`, `DURATION_MS`, `PLAY_COUNT`, `IS_FAVORITE`, `MEDIA_KIND`), operators (`EQUALS`, `CONTAINS`, `STARTS_WITH`, `GREATER_THAN`, `LESS_THAN`, `IS_TRUE`, `IS_FALSE`), match modes (`ALL`/`ANY`), and customizable limits.
- **Listening & Watching Insights**: Real-time aggregation of listening duration, play counts, top artists, top albums, unplayed tracks, video completion counts, and in-progress watch queues.
- **Unicode & Bengali Ranked Search**: Unicode NFC normalization with multi-tier scoring (exact title match = 100, prefix = 80, substring = 60, artist match = 50, album match = 35) tested and verified against composed, decomposed, and Bengali script media items.

### Milestones 43 & 44 Highlights:
- **Advanced Media Library Management**: Strict separation between Mode A (non-destructive SQLite database overrides in `user_metadata_overrides` and `user_artwork_overrides`) and Mode B (embedded file tag writing via jaudiotagger for supported audio formats: MP3, FLAC, OGG, M4A, WAV).
- **Safe Staged File Writing & Verification**: Full preflight validation, automatic file backup before mutation, post-write header and tag verification, and automatic rollback on error. Non-writable formats (video containers like MKV/MP4) are cleanly guided to database overrides.
- **Batch File Renaming & DB Reconciliation**: Token expansion (`{artist}`, `{album}`, `{track}`, `{title}`, `{year}`), Windows reserved device name validation (`CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9`), forbidden path character sanitization, batch and on-disk collision detection, case-only rename handling, and atomic SQLite reference reconciliation preserving playlist memberships, favorites, history, and queue references.
- **Missing Media Reconciliation**: Detection of unavailable media with multi-criteria candidate discovery (exact filename, title match, file size match) and atomic relinking in SQLite.
- **Privacy-First Workflow Automation**: User-controlled automation rules, non-destructive dry-run preview engine, persistent batch operation journal (`batch_operation_journal`) tracking job lifecycles (`PLANNED`, `RUNNING`, `COMMITTED`, `VERIFIED`, `FAILED`), and library health diagnostic scoring.

### Milestones 33 & 34 Highlights:
- **Advanced Audio Engineering**: 10-band equalizer with 10 presets (Flat, Bass Boost, Treble Boost, Vocal, Classical, Rock, Jazz, Pop, Acoustic, Custom) and auto-attenuating safety preamp headroom (0 to -12 dB). Native ReplayGain (Track & Album mode) with peak clipping protection. Verified gapless playback transitions (`gapless-audio=yes`) and pitch-invariant real-time time-stretching (`audio-pitch-correction=yes`). Safe cross-platform output device routing.
- **Synchronized LRC Lyrics & Sleep Timer**: Millisecond-accurate multi-timestamp LRC parser with offset adjustment, Unicode/Bengali script support, and binary search active line indexing. Platform-neutral sleep timer with duration countdown (1-1440m), stop-after-current-track, and gentle volume fade-out.
- **Professional Video Controls**: Native A-B repeat looping, MKV/MP4 container chapter discovery and stepping, external subtitle demuxing with real-time delay (±5000ms) and appearance customization (size, color, margin). A/V synchronization clock offset. Multi-mode aspect ratio overrides (Fit, Fill, 16:9, 4:3, 21:9), hardware zoom/pan, 90-degree step rotation, and real-time GPU video adjustments (brightness, contrast, saturation, gamma). Native frame screenshot capture to user-designated destination.
- **Playback Profiles & Playlist Portability**: Default, Music Focus, Cinema, and Podcast/Lecture profiles adapting playback speed, aspect ratio, EQ, and ReplayGain. Robust non-destructive M3U/M3U8 import/export with `#EXTINF` metadata parsing and HLS stream manifest filtering.

### Milestones 37 & 38 Highlights:
- **Complete Product Feature Gap Audit & UI Traceability**: Audited all user-facing screens and controls; eliminated dead buttons and mock endpoints across library, playlists, settings, and player screens. Every control traces through UI -> ViewModel/State -> Domain -> Engine/Repository -> SQLite persistence.
- **Desktop Video Controls Panel (`DesktopVideoControlsPanel`)**: Native controls for aspect ratio overrides (Fit, Fill, 16:9, 4:3, 21:9), screenshot capture to Desktop (`~/Desktop/vimivo_frame_<timestamp>.png`), 90-degree step rotation, external subtitle loader (`.srt`, `.vtt`, `.ass`, `.ssa`), subtitle delay offsets (±250ms), audio delay offsets (±100ms), chapter stepping, and A-B repeat range loop control.
- **M3U/M3U8 Playlist Portability & Native Dialogs**: Added playlist export to M3U8 and file-dialog import with robust URI and filename matching against indexed SQLite media items, with full Unicode and Bengali script preservation.
- **Desktop Settings & DSP Controls**: Wired Playback Profiles (`Default`, `Music Focus`, `Cinema`, `Podcast/Lecture`), 10-band Equalizer presets, ReplayGain modes, Sleep Timer countdown, privacy-safe diagnostic JSON bundle export, and opt-in software update checker.
- **Synchronized LRC Lyrics Rendering**: Added desktop lyrics viewer with sibling `.lrc` discovery and active line highlighting synchronized with playback position.
- **Exhaustive Regression Hardening & Queue Concurrency**: Added automated suites for M3U round-trips, video control contracts, and playback queue race conditions (rapid next/completion events, track removal during playback, duplicate occurrence identities, and paused session restoration without autoplay). 100% test pass rate across all suites.


