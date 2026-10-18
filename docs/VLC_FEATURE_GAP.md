# VLC feature parity: five-platform matrix

Matrix date: 2026-10-18. This compares Vimivo with the VLC feature list the product owner asked for, across the five
first-class apps, plus the later TV/ChromeOS targets. Every cell comes from reading the current source. It is **not**
runtime evidence: runtime proof per platform is tracked in [FINAL_PREMIUM_AUDIT_PHASE_7.md](FINAL_PREMIUM_AUDIT_PHASE_7.md)
and the phase reports. The desktop apps share one libmpv code path. Only macOS has been run locally; Windows and Linux are
built and smoke-tested in CI.

**Key:** ✅ in source · 🟡 partial (see note) · ❌ not built yet, in scope · ⛔ excluded by decision · 🔜 later phase
(TV/ChromeOS, after the five apps ship) · — does not apply to the platform.

**Engines:** Android uses Media3 (ExoPlayer) with a LibVLC 3.7 fallback. iOS uses AVFoundation (`AVPlayer`).
macOS, Windows and Linux use libmpv (FFmpeg) through JNA.

**TV / ChromeOS:** Android TV and ChromeOS reuse the Android app (leanback launcher entry, D-pad focus, large-screen
and keyboard/mouse layouts). tvOS comes last. The column is 🔜 throughout and is listed once per section.

## Decisions (2026-10-18)

| Topic | Decision |
|---|---|
| Encrypted DVD / Blu-ray | ⛔ No DRM bypass (AGENTS.md). |
| Unencrypted DVD, VCD, Audio CD, Blu-ray | In scope: every libmpv build ships libdvdnav/libdvdread, libcdio (+paranoia) and libbluray. |
| YouTube and other extraction scripts | ⛔ Excluded. In scope: direct HTTP(S), HLS, DASH, RTSP/RTP/UDP/MMS, podcast RSS. |
| Web remote and streaming server | In scope, off by default, LAN-only, pairing code/QR plus token auth, firewall prompt explained. Telnet is excluded; a local JSON IPC socket replaces it. |
| OpenSubtitles | In scope, opt-in: runs only on an explicit Search with the user's own API key or account, and shows exactly what is sent (hash, name, size, language). |
| Discover (internet radio, podcasts) | In scope as an opt-in section (Radio Browser open API plus a podcast directory), disabled until the user enables it. |
| Skins | A theme system (tokens JSON import/export, accent, glass, layout options) plus a customizable toolbar. No arbitrary skin engine. |
| Extensions | mpv's built-in Lua/JavaScript scripts from a user "Scripts" folder, disabled by default with a clear warning; the script API exposure is documented. |
| Visualizer | projectM as an optional module. |
| Embeddable engine (libVLC-like) | Backlog: expose VimivoCore and the engine as a library later. |

## Open questions found while building this matrix

1. **iOS container coverage.** `AVPlayer` cannot play MKV, AVI, FLV, WebM, OGG, MPEG-TS files, DASH, RTSP or most
   VLC-only codecs, and cannot host an equalizer. VLC parity on iOS needs a second engine (libmpv or MobileVLCKit,
   both LGPL and both shipped in App Store apps through dynamic frameworks). This needs a decision and an ADR.
2. **Conversion, recording, screen capture and webcam input** are on the requested list but have no decision yet.
   Screen and camera capture need OS permissions (macOS Screen Recording/Camera, Android MediaProjection/CAMERA, iOS
   ReplayKit/camera). The matrix marks them ❌ pending a decision.
3. **Equalizer in the player window.** Since ADR-030 the desktop equalizer panel is reachable only from the library
   window's playback bar. It should move into the player window's side panel (S2).

## Playback and formats

| Feature | Android | iOS | macOS | Windows | Linux | Where in UI | Notes |
|---|---|---|---|---|---|---|---|
| Common containers and codecs (MKV, MP4, AVI, MOV, FLV, WebM, TS, OGG, FLAC, MP3, AAC) | ✅ | 🟡 | ✅ | ✅ | ✅ | Open file, library, OS "Open with" | Android: Media3 with LibVLC fallback. iOS: AVFoundation formats only (MP4/MOV/M4A/MP3/AAC/ALAC/FLAC/WAV, HLS); see open question 1 |
| DVD, VCD, Audio CD, unencrypted Blu-ray | — | — | ❌ | ❌ | ❌ | File ▸ Open Disc… (planned) | libmpv `dvd://`, `cdda://`, `bd://` need the decided libraries in every bundled build. Phones have no optical drive; disc images (ISO, VIDEO_TS) on Android via LibVLC are a possible follow-up |
| Damaged, incomplete or still-downloading files | 🟡 | ❌ | 🟡 | 🟡 | 🟡 | — | FFmpeg/LibVLC tolerate broken streams, but there are no fixtures or tests yet; `AVPlayer` refuses most damaged files |
| Speed control with pitch correction | ✅ | ✅ | ✅ | ✅ | ✅ | Desktop: control-bar speed button, `[` `]`; Android: player sheet; iOS: player Speed sheet, hold for 2× | Desktop 0.25–4× ladder (`PlaybackControlPolicy`); iOS uses `audioTimePitchAlgorithm` |
| Frame-by-frame stepping | ❌ | ❌ | ✅ | ✅ | ✅ | Desktop: E / Shift+E, Video menu, side panel | Media3 has no frame step; LibVLC `nextFrame` and AVFoundation `step(byCount:)` both exist |
| A-B repeat loop | ✅ | ❌ | ✅ | ✅ | ✅ | Desktop: Shift+L; Android: video player options | Android: `abRepeatStartMillis/EndMillis` in `PlaybackViewModel` |
| Resume where you left off | ✅ | ✅ | ✅ | ✅ | ✅ | Automatic; desktop shows a "Start over" toast | Shared `ResumePolicy`; desktop files opened outside the library resume too (`DesktopPlaybackStore`) |
| Hardware-accelerated decoding | ✅ | ✅ | ✅ | ✅ | ✅ | Settings (desktop diagnostics show the active decoder) | Android MediaCodec / LibVLC; iOS VideoToolbox (implicit); libmpv `hwdec=auto-safe` (VideoToolbox, D3D11/DXVA2, VA-API/VDPAU) |
| HDR playback | 🟡 | ✅ | 🟡 | 🟡 | 🟡 | — | Engines advertise HDR; tone-mapping is untested on desktop (the overlay ADR measures 4K HDR) |
| 360° video | ❌ | ❌ | ❌ | ❌ | ❌ | Player: drag to look around (planned) | LibVLC has viewpoints; libmpv needs the lavfi `v360` filter; iOS needs a custom renderer |
| Gapless audio | ✅ | ❌ | ✅ | ✅ | ✅ | Automatic | **Correction:** FEATURE_MATRIX.md lists iOS as implemented, but `AVPlayerPlaybackEngine` uses a single `AVPlayer` with no `AVQueuePlayer` or preroll |

TV / ChromeOS: 🔜 for every row.

## Video controls

| Feature | Android | iOS | macOS | Windows | Linux | Where in UI | Notes |
|---|---|---|---|---|---|---|---|
| Aspect-ratio presets | ✅ | ✅ | ✅ | ✅ | ✅ | Desktop: A key, side panel; Android: resize mode; iOS: fit/crop/stretch/16:9/4:3 | |
| Crop presets | 🟡 | 🟡 | ❌ | ❌ | ❌ | Side panel Video tab (planned) | Android/iOS have a fill-crop mode only; mpv `video-crop` needs mpv ≥ 0.38 in every bundled build |
| Zoom / pan | ✅ | ✅ | ✅ | ✅ | ✅ | Pinch on mobile; desktop side panel | |
| Deinterlacing (several modes) | ❌ | — | 🟡 | 🟡 | 🟡 | Desktop: D key, side panel | Desktop is on/off only; mode choice (yadif, bwdif) still to come. LibVLC on Android supports modes. AVFoundation deinterlaces automatically with no control |
| Brightness, contrast, saturation, hue, gamma | ❌ | ❌ | 🟡 | 🟡 | 🟡 | Desktop side panel Video tab | Desktop lacks hue. Mobile "brightness" is the screen-brightness gesture, not a video filter |
| Effects: sharpen, rotate/transform, mirror, blur, film grain, colour threshold | ❌ | ❌ | 🟡 | 🟡 | 🟡 | Side panel ▸ Effects (planned) | Desktop has rotate only; the rest via mpv `vf` lavfi. LibVLC video filters on Android |
| Snapshot | ❌ | ❌ | ✅ | ✅ | ✅ | Desktop: Shift+S, saved to `~/Pictures/Vimivo` | LibVLC `takeSnapshot`; iOS `AVPlayerItemVideoOutput` |
| Always on top | — | — | ❌ | ❌ | ❌ | View ▸ Always on Top (planned) | `ComposeWindow.isAlwaysOnTop`; mobile equivalent is PiP |
| Fullscreen | ✅ | ✅ | ✅ | ✅ | ✅ | Desktop: F, double-click, control bar | |
| Wallpaper mode | — | — | ❌ | ❌ | ❌ | View menu (planned) | Platform-specific; needs per-OS research before committing |
| Multiple video tracks | ✅ | 🟡 | ✅ | ✅ | ✅ | Desktop: Video menu, side panel | iOS selection is limited to what `AVMediaSelection` exposes |

## Audio

| Feature | Android | iOS | macOS | Windows | Linux | Where in UI | Notes |
|---|---|---|---|---|---|---|---|
| 10-band equalizer with presets | ✅ | ❌ | ✅ | ✅ | ✅ | Desktop: Equalizer panel (library window; move to player side panel, open question 3) | iOS blocked by `AVPlayer` (open question 1) |
| Compressor, spatializer, stereo widener | 🟡 | ❌ | ❌ | ❌ | ❌ | Equalizer panel ▸ Effects (planned) | Android has BassBoost/Virtualizer (`AndroidAudioEnhancementController`); desktop via lavfi `acompressor`, `extrastereo`, `haas` |
| Volume boost to 200% | ❌ | ❌ | ✅ | ✅ | ✅ | Desktop control-bar volume (red tint above 100%) | Android `LoudnessEnhancer` is possible; iOS has no supported route |
| Audio track switching | ✅ | ✅ | ✅ | ✅ | ✅ | Desktop: B key, Audio menu; iOS: Audio sheet | |
| Audio / subtitle sync offset | ✅ | ❌ | ✅ | ✅ | ✅ | Desktop: G/H and Shift+G/H; Android: player sheet | |
| Normalization | 🟡 | ❌ | ✅ | ✅ | ✅ | Desktop: Equalizer panel ▸ Normalize (`dynaudnorm`) | Android has ReplayGain metadata only |
| Visualizations (spectrum, scope, projectM) | 🟡 | ❌ | ❌ | ❌ | ❌ | Audio player layout (planned, S4) | Android shows a low-rate activity indicator, deliberately without the `Visualizer` permission; projectM is an optional module |
| Passthrough (S/PDIF, HDMI) for Dolby/DTS | ❌ | — | ❌ | ❌ | ❌ | Settings ▸ Audio output (planned) | mpv `audio-spdif`; Media3 passthrough via `AudioCapabilities`; iOS has no app control |

## Subtitles

| Feature | Android | iOS | macOS | Windows | Linux | Where in UI | Notes |
|---|---|---|---|---|---|---|---|
| SRT, ASS/SSA, VTT, SUB/IDX, DVB, embedded | ✅ | 🟡 | ✅ | ✅ | ✅ | Desktop: Subtitle menu, V key | Media3 plus LibVLC; libmpv/libass. iOS shows embedded legible tracks only, with no external SRT loading |
| Auto-load matching subtitle files | ✅ | ❌ | ✅ | ✅ | ✅ | Automatic | Desktop `sub-auto=fuzzy` plus per-file `sub-file-paths`; Android `AndroidSubtitleRepository` |
| Drag-and-drop subtitle loading | — | — | ✅ | ✅ | ✅ | Drop on the player window | Mobile equivalent: "Load subtitle file" picker (Android ✅, iOS ❌) |
| Delay, font, size, colour, outline | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | Desktop: side panel; Android: subtitle settings | Desktop has delay, size, colour and position, with font and outline still to come. iOS follows system caption settings (the native alternative) |
| OpenSubtitles search | ❌ | ❌ | ❌ | ❌ | ❌ | Subtitle menu ▸ Search Online… (opt-in, planned) | Decided: explicit Search only, user's own key, show what is sent |

## Streaming and network

| Feature | Android | iOS | macOS | Windows | Linux | Where in UI | Notes |
|---|---|---|---|---|---|---|---|
| HTTP(S) media URLs | ✅ | ✅ | ✅ | ✅ | ✅ | Desktop: File ▸ Open Network Stream (Cmd/Ctrl+U), paste a link | |
| HLS | ✅ | ✅ | ✅ | ✅ | ✅ | Same | |
| DASH | ✅ | ❌ | ✅ | ✅ | ✅ | Same | `AVPlayer` has no DASH |
| RTSP / RTP / UDP / MMS | 🟡 | ❌ | 🟡 | 🟡 | 🟡 | Same | RTSP is in source on Android and desktop; RTP/UDP/MMS via FFmpeg/LibVLC are untested |
| SMB / FTP / DLNA-UPnP browsing | 🟡 | ❌ | ❌ | ❌ | ❌ | Network page | Android models SMB/UPnP/DLNA, but the SMB transport is not packaged; FTP is deliberately off in favour of SFTP/WebDAV |
| Stream to the network (streaming server) | ❌ | ❌ | ❌ | ❌ | ❌ | Settings ▸ Sharing (planned) | Decided: off by default, LAN-only, pairing plus token |
| YouTube and site extraction scripts | ⛔ | ⛔ | ⛔ | ⛔ | ⛔ | — | Excluded |
| Internet radio and podcast directory | ❌ | ❌ | ❌ | ❌ | ❌ | Opt-in "Discover" section (planned) | Radio Browser API, podcast RSS |
| Chromecast casting | ❌ | ❌ | ❌ | ❌ | ❌ | Player ▸ Cast (planned) | iOS's native alternative is AirPlay (`AVRoutePickerView`), not yet added |

## Conversion and recording

| Feature | Android | iOS | macOS | Windows | Linux | Where in UI | Notes |
|---|---|---|---|---|---|---|---|
| Convert / transcode | ❌ | ❌ | ❌ | ❌ | ❌ | File ▸ Convert… (planned) | Pending decision (open question 2); desktop could use FFmpeg via libmpv `encode` options |
| Record playback or streams | ❌ | ❌ | ❌ | ❌ | ❌ | Player ▸ Record (planned) | libmpv `stream-record` |
| Screen capture / desktop recording | ❌ | ❌ | ❌ | ❌ | ❌ | — | Pending decision; needs OS capture permissions |
| Webcam and capture-card input | ❌ | ❌ | ❌ | ❌ | ❌ | — | Pending decision; needs camera permission |

## Library and playlists

| Feature | Android | iOS | macOS | Windows | Linux | Where in UI | Notes |
|---|---|---|---|---|---|---|---|
| Create and save playlists | ✅ | ✅ | ✅ | ✅ | ✅ | Playlists page | |
| M3U / XSPF / PLS | 🟡 | 🟡 | ✅ | ✅ | ✅ | Desktop: File ▸ Import Playlist, open with | Shared `PlaylistFormats` parses all three, but only desktop uses it; Android and iOS handle M3U only |
| Media library | ✅ | ✅ | ✅ | ✅ | ✅ | Desktop: library window; mobile: tabs | |
| Shuffle, repeat, sort | ✅ | ✅ | ✅ | ✅ | ✅ | Queue and playback bar | iOS repeat/shuffle persistence is still open (Q1) |
| Bookmarks within media | ✅ | ✅ | ✅ | ✅ | ✅ | Desktop: player side panel ▸ Bookmarks; iOS: player sheet | |
| Recently played | ✅ | ✅ | ✅ | ✅ | ✅ | Desktop: File ▸ Open Recent, History page | |

## Interface and customization

| Feature | Android | iOS | macOS | Windows | Linux | Where in UI | Notes |
|---|---|---|---|---|---|---|---|
| Themes (light, dark, AMOLED) | ✅ | ✅ | ✅ | ✅ | ✅ | Settings ▸ Appearance | |
| Theme tokens import/export, accent, glass, layout | ❌ | ❌ | ❌ | ❌ | ❌ | Settings ▸ Appearance (planned, S6) | Decided replacement for skins |
| Customizable toolbar | ❌ | ❌ | ❌ | ❌ | ❌ | Player control bar ▸ Customize (planned) | |
| Hotkeys / customizable hotkeys | — | — | 🟡 | 🟡 | 🟡 | Settings ▸ Shortcuts (planned) | Fixed map in `DesktopPlayerShortcuts`; OS media keys work through Now Playing, SMTC and MPRIS |
| Mouse gestures | — | — | ❌ | ❌ | ❌ | Player window (planned) | |
| Touch gestures (volume, brightness, seek, zoom) | ✅ | ✅ | — | — | — | Video player | |
| Mini / compact player | ✅ | ✅ | ❌ | ❌ | ❌ | Desktop: View ▸ Mini Player (planned) | Mobile has a now-playing bar and PiP |
| Extensions (mpv Lua/JS scripts) | — | — | ❌ | ❌ | ❌ | Settings ▸ Scripts (opt-in, planned) | Desktop only, because mpv scripts exist only there; the mobile gap is documented, with no equivalent planned |
| Web interface and remote control | ❌ | ❌ | ❌ | ❌ | ❌ | Settings ▸ Remote (planned) | Decided: LAN-only, pairing plus token; desktop also gets a local JSON IPC socket; no telnet |
| Command-line interface | — | — | 🟡 | 🟡 | 🟡 | Terminal | `LaunchArguments`: files, start position, fullscreen, single instance; no playback commands yet (the IPC socket covers that) |

## Platform and mobile extras

| Feature | Android | iOS | macOS | Windows | Linux | Where in UI | Notes |
|---|---|---|---|---|---|---|---|
| Free, no ads, no tracking | ✅ | ✅ | ✅ | ✅ | ✅ | — | Product rule |
| Picture-in-picture | ✅ | ✅ | ❌ | ❌ | ❌ | Player | Desktop's closest native alternative is an always-on-top mini player |
| Background audio | ✅ | ✅ | ✅ | ✅ | ✅ | — | |
| Wi-Fi file sharing | ❌ | 🟡 | — | — | — | Settings ▸ Sharing (planned) | iOS exposes Documents to the Files app; there is no Wi-Fi upload server yet |
| Folder browsing | ✅ | ✅ | ✅ | ✅ | ✅ | Library | |
| Network share browsing | 🟡 | ❌ | ❌ | ❌ | ❌ | Network page | See SMB row |
| Sleep timer | ✅ | ❌ | ✅ | ✅ | ✅ | Playback bar | **Correction:** FEATURE_MATRIX.md lists iOS as implemented; no iOS source implements it |
| Synchronized lyrics | ✅ | ❌ | ✅ | ✅ | ✅ | Desktop: playback bar ▸ Lyrics | **Correction:** FEATURE_MATRIX.md lists iOS as implemented; no iOS source implements it |
| Embeddable engine | — | — | — | — | — | — | Backlog |

## Appendix: desktop audit detail (first pass, 2026-10-18)

Status key for this appendix: **Done** = available before the audit · **Added** = implemented in that pass ·
**Partial** · **Gap** = not implemented.

### Playback

| Feature | VLC | Vimivo desktop | Status | Implementation / note |
|---|---|---|---|---|
| Play, pause, stop, seek | ✓ | ✓ | Done | `pause`, `stop`, `seek absolute+exact` |
| Jump 5 s / 10 s / 30 s | ✓ | ✓ | Added | ←/→ (5 s), J/L (10 s), Shift+←/→ (30 s); seeks are clamped by `PlaybackControlPolicy.seekTarget` |
| Frame step forward / back | E | ✓ | Added | mpv `frame-step` / `frame-back-step`; keys E / Shift+E, Video menu, buttons in the video panel |
| A-B loop | ✓ | ✓ | Done + fixed | `ab-loop-a/b`. Added a single Shift+L command (A → B → clear) and reset loop points on every new file (they are global mpv options and previously leaked into the next file) |
| Speed 0.25–4× | ✓ | ✓ | Fixed | Menu was capped at 3×; now `[`/`]` walk the 0.25…4× ladder in `PlaybackControlPolicy`, ⌫/= resets, panel chips cover 0.25–4× |
| Snapshot | Shift+S | ✓ | Added shortcut | `screenshot-to-file … video`; Shift+S saves to `~/Pictures/Vimivo` (home directory fallback) |
| Deinterlace | ✓ | ✓ | Added | mpv `deinterlace` property; D key, Video menu, panel chip |
| Video adjustments (brightness, contrast, saturation, gamma) | ✓ | ✓ | Done | mpv video equalizer properties |
| Zoom / pan / rotate | ✓ | ✓ | Done | `video-zoom`, `video-pan-*`, `video-rotate` |
| Aspect ratio | ✓ | ✓ | Done + shortcut | `video-aspect-override`; A cycles ratios |
| Crop | ✓ | – | Gap | mpv `video-crop` needs mpv ≥ 0.38 on every platform build; deferred until the bundled libmpv version is pinned |
| Video filters (sharpen, denoise, etc.) | ✓ | – | Gap | Possible through mpv `vf` with lavfi; not exposed |
| Chapters | ✓ | ✓ | Done | `chapter`, `add chapter ±1` |

### Tracks and synchronization

| Feature | VLC | Vimivo desktop | Status | Implementation / note |
|---|---|---|---|---|
| Audio / video / subtitle track selection | ✓ | ✓ | Done | `aid`, `vid`, `sid` |
| Cycle audio / subtitle track | B / V | ✓ | Added | `PlaybackControlPolicy.nextTrack`; subtitle cycle passes through "off" |
| External subtitles | ✓ | ✓ | Done | `sub-add … select`; sibling `.srt/.ass/.vtt` detection |
| Subtitle delay | G / H | ✓ | Added shortcut | G/H ±100 ms; delay now lives in `PlaybackState.subtitleDelayMillis`, so panel and shortcuts agree; reset on new file |
| Audio delay | ✓ | ✓ | Added shortcut | Shift+G/Shift+H ±50 ms; `PlaybackState.audioDelayMillis`; reset on new file |
| Subtitle styling (size, colour, position) | ✓ | ✓ | Done | `sub-font-size`, `sub-color`, `sub-pos` |
| Subtitle font / outline choice | ✓ | – | Partial | Size and colour only |

### Audio

| Feature | VLC | Vimivo desktop | Status | Implementation / note |
|---|---|---|---|---|
| 10-band equalizer + presets | ✓ | ✓ | Done | FFmpeg `equalizer` chain via mpv `af` |
| Volume normalizer | ✓ | ✓ | Added | `dynaudnorm` stage, independent of the EQ switch ("Normalize" in the equalizer panel); `AudioEnhancementSettings.volumeNormalization` |
| ReplayGain, balance, output device | partial | ✓ | Done | `replaygain`, `balance`, `audio-device` |
| Mute that restores the previous volume | ✓ | ✓ | Fixed | M / menu remember the pre-mute level (menu previously restored to 100%) |

### Playlists, media, and windows

| Feature | VLC | Vimivo desktop | Status | Implementation / note |
|---|---|---|---|---|
| Shuffle / repeat | ✓ | ✓ | Done + shortcuts | S / R as documented |
| Save/load M3U | ✓ | ✓ | Done | `PlaylistM3uHandler` |
| Save/load XSPF | ✓ | ✓ | Added | `PlaylistXspfHandler` (shared); import/export picks the format by extension. DTD/entity declarations are rejected to avoid entity-expansion attacks |
| Open Network Stream | Ctrl+N | ✓ | Done | File ▸ Open Network Stream (Cmd/Ctrl+U), redacted stream history |
| Media information / codec details | Ctrl+I | ✓ | Added | File ▸ Media Information (Cmd/Ctrl+I, or I): container, size, duration, per-track codec/resolution/fps/channels/sample rate, output video/audio params, container metadata via `file-format`, `file-size`, `metadata/list/*`, `video-params/*`, `audio-params/*` |
| Recent media | ✓ | ✓ | Added | File ▸ Open Recent (15 local files, on-device JSON, clearable). Streams stay in the redacted stream history |
| Drag and drop | ✓ | ✓ | Done | AWT `DropTarget` on the window |
| Fullscreen | F / double-click | ✓ | Added | F toggles, Esc exits, double-click on the video toggles. Fullscreen video now hides sidebar, header, pages, and panels and gives the video all the space above the playback bar |
| Auto-hiding fullscreen controls overlay | ✓ | – | Gap | The libmpv canvas is a heavyweight AWT component, so Compose cannot draw over it; controls stay docked below the video |
| Keyboard shortcuts | ✓ | ✓ | Fixed | Most entries in `KEYBOARD_SHORTCUTS.md` were not wired (only Space/J/K/L/Esc worked). All documented keys now work; menu shortcuts use Cmd on macOS as documented (they used Control before) |
| Customizable hotkeys | ✓ | – | Gap | Fixed map in `DesktopPlayerShortcuts` |
| Convert / stream out / record | ✓ | – | Superseded | Was out of scope; now on the requested list, see "Conversion and recording" above |

### Where the logic lives

- `shared/commonMain`: `PlaybackControlPolicy` (speed ladder, volume/seek/delay steps, track cycling, A-B steps), `MediaInfoFormatter`, `RecentMediaList`, `PlaylistXspfHandler`, and new `PlayerEngine.frameStep` / `setDeinterlace` contract methods (default: explicit unsupported failure) with `EngineCapabilities.frameStepping` / `deinterlacing`.
- `desktopApp`: `DesktopPlayerShortcuts` + `DesktopPlayerCommandExecutor` (key → command → engine, with failures shown on screen instead of being swallowed), `DesktopMediaInfoDialog` (a real `DialogWindow`, because an in-window popup would be painted over by the video canvas), `DesktopRecentMediaRepository`, and the libmpv calls in `MpvPlayerEngine`.

### Verification

- `./gradlew :shared:desktopTest :desktopApp:desktopTest :desktopApp:compileKotlinDesktop` and `./gradlew detekt` pass.
- New tests: `PlaybackControlPolicyTest`, `MediaInfoAndRecentMediaTest`, `PlaylistXspfHandlerTest`, `DesktopPlayerCommandsTest`, `DesktopRecentMediaRepositoryTest`.
- Not verified: an interactive run against real media. Frame stepping, deinterlacing, `dynaudnorm`, fullscreen re-layout of the video canvas, and canvas double-click (which may not reach AWT on macOS, where the Cocoa renderer owns the view) still need a manual check on each OS.
