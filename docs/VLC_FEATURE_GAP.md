# Desktop VLC feature-gap audit

Audit date: 2026-10-18. Scope: the Compose Desktop app (`desktopApp/`, libmpv engine through JNA) compared with VLC 3.x desktop features a user switching from VLC expects. All playback controls go through the documented libmpv client API (`mpv_command`, `mpv_set_property_string`, `mpv_get_property`); nothing shells out.

Status key: **Done** = available before this audit · **Added** = implemented in this pass · **Partial** · **Gap** = not implemented.

## Playback

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

## Tracks and synchronization

| Feature | VLC | Vimivo desktop | Status | Implementation / note |
|---|---|---|---|---|
| Audio / video / subtitle track selection | ✓ | ✓ | Done | `aid`, `vid`, `sid` |
| Cycle audio / subtitle track | B / V | ✓ | Added | `PlaybackControlPolicy.nextTrack`; subtitle cycle passes through "off" |
| External subtitles | ✓ | ✓ | Done | `sub-add … select`; sibling `.srt/.ass/.vtt` detection |
| Subtitle delay | G / H | ✓ | Added shortcut | G/H ±100 ms; delay now lives in `PlaybackState.subtitleDelayMillis`, so panel and shortcuts agree; reset on new file |
| Audio delay | ✓ | ✓ | Added shortcut | Shift+G/Shift+H ±50 ms; `PlaybackState.audioDelayMillis`; reset on new file |
| Subtitle styling (size, colour, position) | ✓ | ✓ | Done | `sub-font-size`, `sub-color`, `sub-pos` |
| Subtitle font / outline choice | ✓ | – | Partial | Size and colour only |

## Audio

| Feature | VLC | Vimivo desktop | Status | Implementation / note |
|---|---|---|---|---|
| 10-band equalizer + presets | ✓ | ✓ | Done | FFmpeg `equalizer` chain via mpv `af` |
| Volume normalizer | ✓ | ✓ | Added | `dynaudnorm` stage, independent of the EQ switch ("Normalize" in the equalizer panel); `AudioEnhancementSettings.volumeNormalization` |
| ReplayGain, balance, output device | partial | ✓ | Done | `replaygain`, `balance`, `audio-device` |
| Mute that restores the previous volume | ✓ | ✓ | Fixed | M / menu remember the pre-mute level (menu previously restored to 100%) |

## Playlists, media, and windows

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
| Convert / stream out / record | ✓ | – | Out of scope | Not a player feature for Vimivo |

## Where the logic lives

- `shared/commonMain`: `PlaybackControlPolicy` (speed ladder, volume/seek/delay steps, track cycling, A-B steps), `MediaInfoFormatter`, `RecentMediaList`, `PlaylistXspfHandler`, and new `PlayerEngine.frameStep` / `setDeinterlace` contract methods (default: explicit unsupported failure) with `EngineCapabilities.frameStepping` / `deinterlacing`.
- `desktopApp`: `DesktopPlayerShortcuts` + `DesktopPlayerCommandExecutor` (key → command → engine, with failures shown on screen instead of being swallowed), `DesktopMediaInfoDialog` (a real `DialogWindow`, because an in-window popup would be painted over by the video canvas), `DesktopRecentMediaRepository`, and the libmpv calls in `MpvPlayerEngine`.

## Verification

- `./gradlew :shared:desktopTest :desktopApp:desktopTest :desktopApp:compileKotlinDesktop` and `./gradlew detekt` pass.
- New tests: `PlaybackControlPolicyTest`, `MediaInfoAndRecentMediaTest`, `PlaylistXspfHandlerTest`, `DesktopPlayerCommandsTest`, `DesktopRecentMediaRepositoryTest`.
- Not verified: an interactive run against real media. Frame stepping, deinterlacing, `dynaudnorm`, fullscreen re-layout of the video canvas, and canvas double-click (which may not reach AWT on macOS, where the Cocoa renderer owns the view) still need a manual check on each OS.
