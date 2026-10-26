# VLC feature parity: V0 audit

Audit date: 2026-10-19. Baseline: branch `desktop/d0.5-d1` at `a0262fd` plus the uncommitted working tree (desktop
startup changes). This replaces the earlier source-only matrix: every ✅ below is backed by an entry in the
[evidence register](#evidence-register). Feature numbers follow the VLC-parity brief (65 items, phases V1–V8).

**Status key:** Deferred — license = decided out of v1 for licensing · ✅ verified (evidence listed) · ❓ present in source, no evidence yet · ⚠️ partial, or claimed done but
broken (root cause in [Done but broken](#done-but-broken-root-causes)) · ❌ not built · N/A OS limitation (closest
alternative named) · ⏳ later phase · ⛔ excluded by decision.

**Platforms:** D = desktop (macOS, Windows, Linux), A = Android, I = iOS. **A D cell describes macOS**, the only desktop
OS run locally. Windows and Linux share the libmpv code path, but CI is currently red on `main` and has never run on
this branch (E8). Treat every D ✅ as ❓ for Windows and Linux until `windows.yml` and `linux.yml` pass.

**Engines today:** D libmpv 0.41.0 through JNA · A Media3 with libmpv (own LGPL build) fallback through the shared
`PlaybackEngineManager` · I `AVPlayer` only. The decided iOS fallback (MPVKit/libmpv) does not exist yet.

## Evidence register

Raw logs: [docs/evidence/V0/](evidence/V0/README.md). Media pack: `scripts/generate_test_media.sh`.

| ID | What ran | Result |
|---|---|---|
| E1 | `./gradlew :shared:desktopTest --rerun :desktopApp:desktopTest --rerun :androidApp:testDebugUnitTest --rerun` | Pass. shared 158 tests / 26 suites, desktopApp 305 / 68, androidApp 27 / 10; 0 failures, 0 skipped |
| E2 | Live libmpv tests inside E1 (`MpvLiveEngineTest`, `MpvContinuousPlaybackTest`), real Homebrew libmpv 0.41.0 | Pass. 200 % volume accepted and 201 % rejected; sidecar `movie.en.srt` plus `Subs/movie.fr.srt` auto-loaded, unrelated `.srt` ignored, dropped file added; embedded tags published; starting at item 2 auto-advances to item 3 |
| E3 | Desktop `--smoke-test` (real window and renderer, macOS) on a generated pack: H.264 MP4, VP9/Opus WebM, AV1 MKV, HEVC 10-bit HDR10 MKV, interlaced MPEG-2 TS, MPEG-4 AVI, AVI with `idx1` stripped, MP4 truncated to 60 %, MKV with 3 audio tracks (eng/ben/jpn), AC3, FLAC 24-bit/96 kHz, FLV, Opus OGG, MJPEG MOV | 13/14 PASS (playback advanced ≥ 1.5 s, video track decoded). FAIL `truncated.mp4`: timed out after 20 s with no error state (moov atom missing; see B6) |
| E4 | `mpv --video-aspect-override=none` and the same property set at runtime | Rejected: "must be a floating point number or a ratio" |
| E5 | mpv 0.41 on the HDR10 file: `colormatrix`, `video-params/colormatrix`, `video-params/gamma` | `bt.2020-ncl`, `bt.2020-ncl`, `pq`: the transfer function is only in `video-params/gamma` |
| E6 | `mpv --list-protocols` (0.41.0 build) and a listing of the bundled `Vimivo.app/Contents/Frameworks` | `bd://`, `rtp://`, `udp://`, `mms://`, `ftp://`, `rtsp://` available; no `dvd://`, no `cdda://`, no `smb://`/`sftp://`. Bundle has libbluray, libass + HarfBuzz + FriBidi, uchardet, LuaJIT, mujs, **GPL** libx264/libx265; no libdvdnav/libdvdread/libcdio |
| E7 | `xcodebuild test` (iosApp scheme, iPhone simulator, iOS 26.5) after `:shared:linkDebugFrameworkIosSimulatorArm64` | `TEST SUCCEEDED`: 24 tests, 0 failures |
| E8 | `gh run list` | Last runs on `main` (`5e417ac`): Shared, Android, macOS, Windows, Linux **failed**, iOS passed. Windows failures include `DesktopDiagnosticExporterTest.refusesToExportToProtectedSystemLocations` and `DesktopVideoMetadataExtractorTest.extractsVideoWithoutAudio`. This branch is 5 commits ahead of origin with no CI runs |
| E9 | Android emulator pass (2026-10-19, [evidence](evidence/V1/android-emulator/README.md)) | Video ("Open with", colour bars) and audio play; library auto-advance `hires` → `Lunar Horizon`; still `PLAYING` after Home with a foreground media service. Instrumentation tests (`androidTest`) not run |
| E10 | Desktop smoke test with a screen pixel probe ([evidence](evidence/D1-black-video/README.md)) | Before the fix every video failed (`lit=0.00`); after it colour bars, `park.mp4`, audio → video, fullscreen enter/exit and resize all pass |
| E11 | Ubuntu 24.04 container, mpv 0.37, no display | MP3/FLAC with cover art exit 2 ("Error opening video_out"); with `audio-display=no` exit 0 |
| E12 | Ubuntu 24.04 container matching `linux.yml` ([evidence](evidence/V1/linux-black-video/README.md)) | Before: black videos in most runs, exit crash in 1–4 of 5. After: 5 consecutive CI smoke runs, 35/35 checks, no crash |
| S | Source reading | File references in the notes; never counted as ✅ on its own |

## Done but broken: root causes

| # | Claim (where) | What is actually true | Root cause |
|---|---|---|---|
| B1 | Gapless audio "Implemented, tested" on Android, macOS, Windows, Linux ([FEATURE_MATRIX.md](FEATURE_MATRIX.md)) | No platform is gapless across files | Every item is opened on its own after the previous one reports `Completed`. Desktop: `keep-open=yes` pauses on the last frame; a 250 ms poll sees `eof-reached`; then `loadfile … replace` with `pause=yes` and a later `play()`. `gapless-audio=yes` only works inside mpv's own playlist, which Vimivo never uses. Android: `Media3PlayerEngine.open` calls `setMediaItem` + `prepare()` per item through `SessionPlayerAdapter` → `PlaybackEngineManager`, so ExoPlayer never sees a playlist to pre-buffer. iOS: single `AVPlayer`, no `AVQueuePlayer`. The capability flag `gapless = true` in `MpvPlayerEngine.capabilitiesFor` is the only "evidence" |
| B2 | Aspect presets ✅ on desktop (**fixed** `8ffd6eb`: Fill maps to `panscan`, control failures go to the HUD and never skip the file; `MpvControlFailureTest`) | Choosing **Fill** (side panel, or pressing A three times) fails, and the queue then **skips the file** | `VideoAspectRatio.FILL` sends `video-aspect-override=none`, which libmpv rejects (E4). `MpvPlayerEngine.action()` routes every control failure to `publishFailure`, which sets `PlaybackStatus.Error` on the loaded media; `PlaybackQueueController` treats an error on the current item as unplayable and skips it. Any failing control (not only aspect) can skip a file |
| B3 | HDR ✅ on desktop diagnostics | HDR content is never reported as HDR | `getDiagnosticsSnapshot` reads the top-level `colormatrix` property and looks for `2084`/`pq`/`hlg`; the transfer function is in `video-params/gamma` (E5), so HDR10 and HLG map to `UNKNOWN`. No tone-mapping or `target-*` options are set either |
| B4 | Desktop zoom/pan ✅ "side panel" (previous matrix) | Engine supports `video-zoom`/`video-pan-*`; no control in any desktop UI | `DesktopVideoControlsPanel` exposes aspect, rotate, brightness, contrast, saturation only; gamma is engine-only too |
| B5 | "Windows and Linux are built and smoke-tested in CI" (previous matrix) | Not true at V0 | CI red on `main` since `5e417ac`. **Being fixed** on draft PR #14: test-step ordering, headless Linux test session, a 66-character fixture digest, `/tmp` on Windows, MSVC 14.51 C++20 |
| B12 | E3 "13/14 PASS" counted video as working | Since `a0262fd` (window split) every macOS video was **black** while audio played; the smoke test only checked that a track decoded | `MpvCocoaRenderer.mm` rendered into framebuffer 0 instead of the layer's bound IOSurface framebuffer → `GL_INVALID_FRAMEBUFFER_OPERATION`. **Fixed** `e8e2a9c`; the smoke test now checks screen pixels (E10) |
| B13 | Linux playback of music with cover art | Without a usable GPU context (headless, broken EGL) libmpv aborted the whole song | Embedded art became a video track whose output failed. **Fixed** `5fac7e7` (`audio-display=no`; Vimivo draws art itself), E11 |
| B14 | Linux/Windows video without GPU drivers (VMs, remote desktops, CI) | Black picture while audio plays, at random | libmpv's default scalers are too slow on CPU-only OpenGL and every frame is dropped as late (reproduced with the plain mpv CLI). **Fixed** `8289006`: built-in `fast` profile when libmpv reports a software renderer (E12) |
| B15 | Linux app exit and any X error after an audio file | Process killed by `X Error … BadWindow` | libmpv leaves Xlib's fatal default error handler installed when its video output stops. **Fixed** `8289006`: AWT's handler is reinstalled (E12) |
| B6 | Damaged/incomplete files "tolerated" | Truncated MP4 shows nothing for 20 s | libmpv fails with "moov atom not found", but no error reached the UI state within the smoke timeout; the load-failure path (`idle && !everLoaded` after 1.5 s) did not publish for this file. Needs a regression test |
| B7 | Android ReplayGain "Partial (metadata only)" | No ReplayGain code in `androidApp/src/main` | Matrix entry not backed by source |
| B8 | iOS lyrics, sleep timer (corrected earlier in FEATURE_MATRIX.md) | Still absent | No iOS implementation; shared `SleepTimer`/`LrcLyricsParser` are not used from Swift |
| B9 | Rule: native libraries pinned by version + SHA-256 | Only Windows is pinned (shinchiro `20261016`, SHA-256 checked) | macOS CI runs `brew install mpv` (whatever is current); Linux uses the runner's `libmpv2` apt package |
| B10 | Rule: LGPL/GPL obligations shown in About (see [ADR-004](adr/ADR-004-LGPL-MEDIA-STACK.md): today's desktop bundles are GPL works; LGPL rebuild not started) | Desktop "About Vimivo" opens Settings with no notices, while the macOS bundle ships GPL x264/x265 (E6) | Licence screen exists only on Android (`VimivoAndroidApp.kt`); not on desktop or iOS |
| B11 | AGENTS.md: iOS persistence and non-UI logic in `shared` | `iosApp/Storage/IOSDatabase.swift` (580 lines) is a Swift SQLite store; queue, shuffle and bookmarks live in `AVPlayerPlaybackEngine.swift` | iOS predates the shared-framework rule; only `AppRouter`, `MediaModels`, `PlaybackPreferences` import `VimivoShared` |

## Decisions in force

⛔ Encrypted DVD/Blu-ray (no DRM bypass); unencrypted DVD/VCD/Audio CD/Blu-ray in scope. ⛔ YouTube/site extraction;
direct HTTP(S), HLS, DASH, RTSP/RTP/UDP/MMS, podcast RSS in scope. Web remote and streaming server: off by default,
LAN-only, pairing code/QR + token. Telnet replaced by a desktop JSON IPC socket. OpenSubtitles, Discover (Radio Browser +
podcasts) opt-in only. Themes + customizable toolbar instead of skins. Desktop mpv Lua/JS scripts, off by default with a
warning. iOS fallback engine: MPVKit (libmpv). Automatic primary → fallback routing that **remembers the choice** per
file: routing exists on Android (`PlaybackEngineManager`), memory does not (`attemptedEngines` is cleared on every
open). tvOS, Android TV, ChromeOS ⏳ V7. Embeddable engine: backlog.

## Matrix

Priority: M = must-have, L = later. "UI" is today's location, then → the target from the brief where it differs.

### 2.1 Playback and formats

| # | Feature | D | A | I | Pri | UI | Evidence / notes |
|---|---|---|---|---|---|---|---|
| 1 | Nearly every A/V format | ✅ | ❓ | ⚠️ | M | Open file, library, OS "Open with" | D: E3 decode (13 containers/codecs incl. AV1, HDR10 HEVC, FLV, TS); picture on screen re-proven after B12 (E10). A: Media3 → LibVLC fallback via `PlaybackEngineManager` (fallback logic unit-tested, `PlaybackEngineManagerTest`, E1); MP4, FLAC and MP3 play on the emulator (E9), other containers not run; fallback choice not remembered. I: `AVPlayer` only; MKV/AVI/WebM/FLV/OGG/TS unsupported until MPVKit |
| 2a | DVD, Audio CD, VCD | Deferred — license | Deferred — license | Deferred — license | — | — | Decided 2026-10-19: dropped from v1. libdvdnav/libdvdread and libcdio-paranoia are GPL, and the desktop stack is LGPL-only ([ADR-004](adr/ADR-004-LGPL-MEDIA-STACK.md)). Possible later as a separate GPL helper process, or if Vimivo becomes GPL |
| 2b | Unencrypted Blu-ray (folder, ISO, drive) | ❌ | ❌ | ❌ | M (D) / L | → Open ▸ Disc | libbluray is LGPL and already in the macOS bundle (E6); `MpvPlayerEngine.SUPPORTED_SCHEMES` rejects `bd://`. A/I: ISO via fallback engines |
| 3 | Damaged, incomplete, growing files | ⚠️ | ❓ | ❌ | M | automatic → + Settings ▸ Playback ("Repair index") | D: index-less AVI plays (E3); truncated MP4 fails silently (B6); growing-file duration refresh and repair prompt not built |
| 4 | Speed 0.25–4× with pitch correction | ⚠️ | ⚠️ | ⚠️ | M | D: speed button, `[` `]`, side panel; A: player Speed panel; I: Speed sheet | Ladder `PlaybackSpeeds`/`PlaybackControlPolicy` unit-tested (E1). D `audio-pitch-correction=yes`; I `audioTimePitchAlgorithm`. ±0.05 fine steps and long-press 2× (A) missing; engine application not runtime-verified |
| 5 | Frame-by-frame | ❓ | ❌ | ❌ | M | D: E / Shift+E, Video menu, side panel | D: `frame-step`/`frame-back-step`. A/I: no step API wired (LibVLC `nextFrame`, `AVPlayerItem.step(byCount:)` unused) |
| 6 | A–B repeat | ✅ | ❓ | ❌ | M | D: Shift+L, Video menu, side panel; region on the timeline with draggable A and B caps; A: video options | D: the region is drawn on the timeline and its ends can be dragged (`AbRegionGeometryTest`, `PlaybackControlPolicyTest` for the drag clamping); `MpvAbLoopLiveTest` with real libmpv: playback stays inside the region, wraps to A repeatedly, moving B while playing moves the loop, no region lets playback run past B, a new file does not inherit it. libmpv's own `ab-loop-*` is not used: with a real audio output it left audio queued, so the reported position stayed at or below 0 while looping (found by the first run of this test); the engine now seeks to A itself (`AbRegionWatcher`, up to ~20 ms past B). A/I not started |
| 7 | Resume from last position | ✅ | ✅ | ❓ | M | D: "Start over" toast; A/I: automatic | Shared `ResumePolicyTest` + Android `VideoResumePolicyTest` (E1). Toast UI not screenshotted. I: own Swift store (B11) |
| 8 | Hardware decoding | ❓ | ❓ | ❓ | M | Settings; desktop diagnostics | D: `hwdec=auto-safe`, per-OS API in `setHardwareAccelerationMode`. A: MediaCodec/LibVLC; HW / HW+ / SW selector missing. I: implicit VideoToolbox |
| 9 | HDR playback | ⚠️ | ❓ | ❓ | M | automatic | D: HDR10 file plays (E3), detection broken (B3), no tone-mapping/passthrough config |
| 10 | 360° video | ❌ | ❌ | ❌ | L | → "360°" badge, reset view | Not built |
| 11 | Gapless audio | ⚠️ | ⚠️ | ❌ | M | automatic | B1. Crossfade not built anywhere |

### 2.2 Video controls

| # | Feature | D | A | I | Pri | UI | Evidence / notes |
|---|---|---|---|---|---|---|---|
| 12 | Aspect, crop presets + black-bar detection, zoom & pan | ⚠️ | ⚠️ | ⚠️ | M | D: A key, side panel; A: resize mode + pinch; I: fit/crop/stretch/16:9/4:3 + pinch | D: Fill broken (B2), zoom/pan no UI (B4), crop and auto-crop not built. A/I: no crop presets or black-bar detection LGPL build: black-bar detection uses `bbox` (+ `crop`); `cropdetect` is GPL-only and absent. |
| 13 | Deinterlacing modes | ⚠️ | ❌ | ❌ | M | D: D key, Video menu, side panel | D on/off only (`deinterlace` yes/no); no yadif/bwdif choice. A/I need LibVLC/MPVKit routing for interlaced files LGPL build: `yadif`, `bwdif`, `w3fdif`, `estdif` available; `mcdeint`/`kerndeint` are GPL-only and not offered. |
| 14 | Brightness, contrast, saturation, hue, gamma | ⚠️ | ❌ | ❌ | M | D: side panel | D UI has B/C/S; gamma engine-only; hue missing. Mobile "brightness" is the screen gesture LGPL build: B/C/S/gamma/hue come from libmpv's renderer (no `vf eq`, which is GPL-only); `colorbalance`, `colorlevels`, `curves`, `lutrgb` available for extras. |
| 15 | Effects (sharpen, mirror, blur, grain…) | ⚠️ | ❌ | ❌ | L | D: side panel Rotate | D rotate only LGPL build: blur = `gblur`/`avgblur` (not GPL `boxblur`/`smartblur`), denoise = `nlmeans`/`atadenoise` (not GPL `hqdn3d`), plus `unsharp`, `hflip`/`vflip`/`transpose`/`rotate`, `noise`, `edgedetect`, `negate`, `vignette`, `deband`. |
| 16 | Snapshot | ✅ | ❌ | ❌ | M | D: Shift+S, Video menu, side panel (format, subtitles, folder) → `~/Pictures/Vimivo` by default | D: PNG/JPEG, with or without subtitles, chosen folder; `MpvSnapshotLiveTest` (real libmpv), `DesktopPlayerCommandsTest`, and the smoke check `#snapshot-subtitles` saves one paused frame both ways in the real window and requires the subtitle pixels to differ (negative control: with no subtitle track selected, 0 pixels differ). Found and fixed on the way: with zero-copy VideoToolbox frames "Save frame" failed (`Input image format videotoolbox not supported by libswscale`), so macOS now decodes with `videotoolbox-copy` |
| 17 | Always-on-top, fullscreen, wallpaper | ⚠️ | ❓ | ❓ | M (wallpaper L) | D: F, double-click, View menu | D fullscreen in source; always-on-top and wallpaper not built. A/I equivalent = PiP (#62) |
| 18 | Multiple video tracks | ❓ | ❓ | ⚠️ | M | D: side panel | D `vid`; I limited to `AVMediaSelection` groups |

### 2.3 Audio

| # | Feature | D | A | I | Pri | UI | Evidence / notes |
|---|---|---|---|---|---|---|---|
| 19 | 10-band EQ + presets + preamp | ✅ | ⚠️ | ❌ | M | D: Equalizer panel, **library window only** → player side panel | D filter chain unit-tested (`DesktopAudioEffectsTest`, `MpvAdvancedPlaybackTest`, E1); preamp is attenuation-only. A: platform `Equalizer` effect, band count is device-defined (often 5); mapping tested (`AndroidAudioEnhancementTest`). I needs AVAudioEngine or MPVKit LGPL build: `equalizer`, `superequalizer`, `volume` verified. |
| 20 | Compressor, spatializer, widener | ❌ | ⚠️ | ❌ | L | — | A: BassBoost/Virtualizer only LGPL build: `acompressor`, `extrastereo`, `stereowiden`, `haas`, `crossfeed` available; `sofalizer` (HRTF) needs libmysofa, not built. |
| 21 | Volume boost to 200 % | ✅ | ❌ | ❌ | M | D: control-bar slider, red above 100 % → amber | D: boost above 100 % is a gain plus `alimiter` (ceiling 0.97) at the end of the audio filter chain, because mpv applies its own volume after the chain; `MpvLimiterLiveTest` decodes to WAV with real libmpv: peak ≤ ceiling with the limiter, ≥ 0.999 (clipped) without LGPL build: `alimiter` available for the missing limiter. |
| 22 | Audio track switching | ❓ | ❓ | ❓ | M | D: B key, Audio menu, side panel; I: Audio sheet | 3-track MKV plays (E3), switching not exercised. Labels show title/language/codec, not channels; no default-language preference on any platform |
| 23 | Audio/subtitle sync offset | ❓ | ❓ | ❌ | M | D: G/H, Shift+G/H, side panel; A: player sheet | D: `sub-delay`/`audio-delay`, delay steps unit-tested (`PlaybackControlPolicyTest`) |
| 24 | Normalization + ReplayGain | ✅ | ❌ | ❌ | M | D: Equalizer panel ▸ Normalize | D `dynaudnorm` chain unit-tested (E1); `replaygain` track/album in source only. A: B7 LGPL build: `dynaudnorm`, `loudnorm` verified. |
| 25 | Visualizations | ❌ | ⚠️ | ❌ | L | → Audio player ▸ Visualizer | A: settings switch and low-rate activity indicator only |
| 26 | S/PDIF/HDMI passthrough | ❌ | ❌ | N/A | M (D, A) | → Audio ▸ Output | No `audio-spdif`, no Media3 passthrough. I: N/A, AirPlay carries multichannel |

### 2.4 Subtitles

| # | Feature | D | A | I | Pri | UI | Evidence / notes |
|---|---|---|---|---|---|---|---|
| 27 | SRT, ASS, VTT, SUB/IDX, DVB, PGS, embedded, CEA-608/708, complex scripts | ⚠️ | ❓ | ⚠️ | M | D: Subtitle menu, V key, side panel; I: Subtitles sheet | D: SRT verified live (E2); libass + HarfBuzz/FriBidi bundled (E6) but no Bangla/Arabic/CJK render test; CEA-608 off (`sub-create-cc-track` unset). I: embedded legible tracks only |
| 28 | Auto-load matching files | ✅ | ✅ | ❌ | M | automatic | D: E2 (`name.lang.srt`, `Subs/`). A: `SubtitleMatchingTest` (E1) |
| 29 | Drag-and-drop / picker loading | ✅ | ❓ | ❌ | M | D: drop on player, side panel "Add File"; A: picker | D load path verified (E2); the drop gesture itself is not automated. Side-panel picker hard-codes the SRT MIME type for every file |
| 30 | Font, size, colour, outline, shadow, background, position, secondary, encoding | ⚠️ | ⚠️ | ⚠️ | M | D: side panel; A: subtitle settings; I: system caption style | D: size, colour, position only; no font, outline, shadow, secondary track or ASS override. Encoding auto-detect relies on bundled uchardet (untested) |
| 31 | Sync panel | ⚠️ | ⚠️ | ❌ | M | → Sync panel / sheet | Audio + subtitle delay only; no secondary delay, `sub-speed`, `sub-fps` or "Sync to now" |
| 32 | OpenSubtitles (opt-in) | ❌ | ❌ | ❌ | L | → Subtitles ▸ Search online | Not built |

### 2.5 Streaming and network

| # | Feature | D | A | I | Pri | UI | Evidence / notes |
|---|---|---|---|---|---|---|---|
| 33 | HTTP(S), HLS, DASH, RTSP, RTP, UDP, MMS | ⚠️ | ⚠️ | ⚠️ | M | D: File ▸ Open Network Stream (Cmd/Ctrl+U), Network page; A: Network page; I: Videos ▸ Open URL | Validation and session lifecycle unit-tested (`StreamValidatorTest`, `StreamSessionLifecycleTest` with a local test server, E1). RTP/UDP/MMS rejected by scheme allow-lists although libmpv supports them (E6). I: HTTP(S)/HLS only |
| 34 | SMB, FTP/SFTP, NFS, DLNA/UPnP | ❌ | ⚠️ | ❌ | M | A: Network page | A: SMB model present, transport "not packaged"; FTP deliberately off |
| 35 | Stream to the network | ❌ | ❌ | ❌ | L | → Tools ▸ Share on network | Not built |
| 36 | Web URL playback | ❓ | ❓ | ⚠️ | M | as #33 | No live URL run in this audit (`stream_urls.txt` not played) |
| 37 | Internet radio + podcasts (opt-in) | ❌ | ❌ | ❌ | L | → Library ▸ Discover | `DesktopDiscoveryPanel` is duplicate detection, not Discover |
| 38 | Chromecast / AirPlay / DLNA casting | ❌ | ❌ | ❌ | M | → Cast button | No AirPlay route picker on iOS either |

### 2.6 Conversion and recording

| # | Feature | D | A | I | Pri | UI | Evidence / notes |
|---|---|---|---|---|---|---|---|
| 39 | Convert/transcode | ❌ | ❌ | ❌ | L | → Tools ▸ Convert | Encoders already in the macOS bundle (E6), licence impact per B10 |
| 40 | Record playback/streams | ❌ | ❌ | ❌ | L | → Tools ▸ Record | — |
| 41 | Screen capture | ❌ | ❌ | ❌ | L | → Open ▸ Capture ▸ Screen | — |
| 42 | Webcam / capture card | ❌ | ❌ | ❌ | L | → Open ▸ Capture ▸ Device | — |

### 2.7 Library and playlists

| # | Feature | D | A | I | Pri | UI | Evidence / notes |
|---|---|---|---|---|---|---|---|
| 43 | Playlists: M3U/M3U8, XSPF, PLS, CUE | ✅ | ⚠️ | ⚠️ | M | D: File ▸ Import Playlist, Playlists page | Shared parsers tested (`PlaylistM3uHandlerTest`, `PlaylistXspfHandlerTest`, `PlaylistPlsHandlerTest`) and used by desktop only; CUE handed to libmpv (untested). A: M3U only (`PlaylistDocumentsTest`). I: playlists without import/export |
| 44 | Media library + search | ⚠️ | ✅ | ⚠️ | M | D: library window; A/I: tabs | Desktop library integration, migration, Bengali search tests pass (E1), but search is `LIKE`, not FTS5. A: `LibraryQueryBuilderTest`; DAO instrumentation not run. I: `IOSDatabaseTests` (E7) pass, persistence outside `shared` (B11) |
| 45 | Shuffle, repeat, sort, continuous auto-next | ✅ | ❓ | ✅ | M | Transport + queue | D: `PlaybackQueueControllerTest` + live auto-advance (E2). I: `ContinuousPlaybackTests` (E7). A: library auto-advance verified (E9); "Open with" now queues the folder in natural order like desktop (shared `SiblingQueues`, [evidence](evidence/V1/android-open-with-queue/README.md)); shuffle/repeat not run |
| 46 | Bookmarks | ⚠️ | ❓ | ❓ | M | D: player side panel ▸ Bookmarks; I: player sheet | No thumbnails or timeline markers on any platform |
| 47 | History / Recent | ✅ | ❓ | ❓ | M | D: File ▸ Open Recent, Home | `DesktopRecentMediaRepositoryTest`, `MediaInfoAndRecentMediaTest` (E1) |

### 2.8 Interface and customization

| # | Feature | D | A | I | Pri | UI | Evidence / notes |
|---|---|---|---|---|---|---|---|
| 48 | Themes (tokens import/export) | ❌ | ❌ | ❌ | L | Settings ▸ Appearance | Light/dark/AMOLED exist (`VimivoDesignSystemTest`); token import/export not built |
| 49 | Customizable toolbar | ❌ | ❌ | ❌ | M | → Settings ▸ Controls | — |
| 50 | Global + custom hotkeys | ⚠️ | ❌ | ❌ | M | → Settings ▸ Shortcuts | D: fixed map, `DesktopPlayerCommandsTest` (E1); no remapping, presets or global hotkeys. A/I: no hardware-key handling |
| 51 | Mouse gestures | ❌ | — | — | L | → Settings ▸ Mouse | — |
| 52 | Mini / compact player | ❌ | ❓ | ❓ | M | A/I: now-playing bar | D: no compact window, tray or menu-bar player |
| 53 | Scripts (D) / intents (A, I) | ❌ | ❌ | ❌ | L | → Settings ▸ Advanced ▸ Scripts | D: LuaJIT/mujs are bundled, but `config=no` and no script folder |
| 54 | Web remote / remote client | ❌ | ❌ | ❌ | L | → Settings ▸ Remote | — |
| 55 | CLI + JSON IPC | ⚠️ | — | — | M | Terminal | `--start`, `--fullscreen`, files, single-instance forwarding (`LaunchArgumentsTest`, `LaunchPlannerTest`, E1); no `--end`, `--volume`, `--speed`, `--sub`, `--audio-track`, `--loop`, `--enqueue`, `--audio-only`, `--help`; no IPC socket |
| 56 | Command palette | ❌ | — | — | M | → Ctrl/⌘+K | — |

### 2.9 Platform and mobile extras

| # | Feature | D | A | I | Pri | UI | Evidence / notes |
|---|---|---|---|---|---|---|---|
| 57 | Five apps | ⚠️ | ⚠️ | ✅ | M | — | macOS runs locally (E3). CI red for Android, Windows, Linux, Shared (E8). iOS CI green and local tests pass (E7) |
| 58 | tvOS, Android TV, ChromeOS | — | ⏳ | ⏳ | L | — | V7 |
| 59 | Free, no ads/tracking, licence notices | ⚠️ | ❓ | ⚠️ | M | About | No analytics or telemetry dependencies found; notices missing on desktop and iOS (B10) |
| 60 | Touch gestures | — | ❓ | ❓ | M | Player | A: `AdvancedVideoPlayer.kt`; I: `VideoPlayerGestures.swift`; long-press 2× not on Android |
| 61 | Wi-Fi file sharing | ❌ | ❌ | ❌ | M | → Settings ▸ Wi-Fi transfer | I: Files-app sharing of Documents is the current alternative |
| 62 | Picture-in-picture | ❌ | ❓ | ❓ | M | Player button | A: manifest + `enterPictureInPictureMode`; I: `AVPictureInPictureController` |
| 63 | Background audio / play video as audio | ❌ | ✅ | ❓ | M | → Player "Play as audio" | D: no audio-only mode for video; A: foreground media service verified in background (E9); I: `.playback` audio session |
| 64 | Folder browsing | ✅ | ❓ | ⚠️ | M | D: Add Folder…; A: SAF folders; I: document picker | D: `IncrementalMediaScannerTest`, `DesktopLibraryIntegrationTest` (E1). I: app Documents + picked files only |
| 65 | Network share browsing | ❌ | ⚠️ | ❌ | M | = #34 | — |

## Totals

| Status | D | A | I |
|---|---|---|---|
| ✅ verified | 10 | 3 | 2 |
| ❓ source only | 6 | 21 | 11 |
| ⚠️ partial / broken | 22 | 13 | 12 |
| ❌ not built | 25 | 24 | 35 |
| N/A, —, ⏳ | 2 | 4 | 5 |

Of the 49 M-items (counting "M (D) / L" rows), only these are verified: D #1, 7, 19, 24, 28, 29, 43, 45, 47, 64; A #7, 28, 44; I #45, 57.

## What V0 could not verify

- Android anything at runtime (E9). Next run: boot `Medium_Phone_API_36.1`, push the E3 pack, open each file with
  `am start -a android.intent.action.VIEW`, read state with `dumpsys media_session`.
- iOS playback at runtime (only unit tests ran). Route: `simctl launch … -VimivoOpenURL`, then `simctl io booted screenshot`.
- Windows and Linux, until CI is green (E8).
- Optical discs and ISO images, CEA-608 captions, PGS, Bangla SRT in two encodings, live HLS/RTSP URLs: no fixtures
  yet. Missing fixtures are added to `scripts/generate_test_media.sh` in V1.

## LGPL filter availability (ADR-004)

The desktop media stack is built LGPL-only (`scripts/build_media`). `verify.py` loads the built libavfilter on each OS
and fails CI unless every filter below exists and the GPL-only ones are absent. Last verified 2026-10-20 on the macOS
universal package (arm64 and x86_64).

| Item | LGPL filters (present) | GPL-only, absent → replacement |
|---|---|---|
| #12 | `bbox`, `crop` | `cropdetect` → `bbox` over sampled frames |
| #13 | `yadif`, `bwdif`, `w3fdif`, `estdif` | `mcdeint`, `kerndeint` → `bwdif` |
| #14 | `hue`, `colorbalance`, `colorlevels`, `curves`, `lutrgb` | `eq` → libmpv `brightness`/`contrast`/`saturation`/`gamma`/`hue` properties |
| #15 | `unsharp`, `gblur`, `avgblur`, `nlmeans`, `atadenoise`, `noise`, `edgedetect`, `negate`, `vignette`, `deband`, `hflip`, `vflip`, `transpose`, `rotate` | `boxblur`, `smartblur` → `gblur`/`avgblur`; `hqdn3d` → `nlmeans`/`atadenoise`; `stereo3d`, `pp7`, `spp` → not offered |
| #19 | `equalizer`, `superequalizer`, `volume` | — |
| #20 | `acompressor`, `extrastereo`, `stereowiden`, `haas`, `crossfeed` | — (`sofalizer` is LGPL but needs libmysofa: deferred) |
| #21 | `alimiter`, `volume` | — |
| #24 | `dynaudnorm`, `loudnorm` | — |

Code in use today (`DesktopAudioEffects`, `MpvThumbnailer`, the side panel) only uses LGPL filters and libmpv properties,
so the switch changed no shipped behaviour.
