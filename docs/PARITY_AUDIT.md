# Vimivo Parity Audit — VLC (Desktop) + MX Player (Mobile) + Completion Prompt

Audit date: 2026-10-27. Sources: `docs/VLC_FEATURE_GAP.md` (evidence-based rows, current HEAD) for desktop and mobile rows of the VLC list; a source-code scan of `androidApp/` and `iosApp/` for MX-only features (marked *src* = code exists, not verified on a device).

Status: ✅ verified · ⚠️ partial/broken · ❓ code exists, not verified · ❌ missing · ⛔ excluded by decision · — not applicable · 🔨 built (untested)

---

## PART 1 — Desktop vs VLC (macOS / Windows / Linux)

**Totals (66 rows):** ✅ 25 · ⚠️ 12 · ❓ 1 · ❌ 25 · other 3

### ✅ Done (25)
All formats · damaged/incomplete/growing files · speed 0.25–4× with pitch · frame step · A–B loop · resume · HW decoding · HDR · gapless · aspect/crop/black-bar/zoom/pan · deinterlace modes · brightness/contrast/saturation/hue/gamma · snapshot · multiple video tracks · 10-band EQ · volume 200% · audio track switching · audio/subtitle delay · normalization + ReplayGain · subtitle auto-load · drag & drop subtitles · playlists M3U/XSPF/PLS · shuffle/repeat/auto-next · history/recent · folder browsing

### ⚠️ Partial (12) — must be finished
| # | Feature | Status | What's missing / built |
|---|---|---|---|
| 15 | Video effects | 🔨 built (untested) | sharpen, mirror/flip, blur, grain, denoise, color threshold, negate, vignette, deband (LGPL filters) in Video panel |
| 17 | Always-on-top / fullscreen / wallpaper | 🔨 built (untested) | wallpaper mode built in Window / menu bar / state |
| 27 | Subtitle formats | 🔨 built (untested) | PGS/DVB/CEA-608 + Bangla/Arabic/CJK rendering options enabled in engine |
| 30 | Subtitle style | 🔨 built (untested) | font, outline, shadow, background, secondary subtitle, ASS override, encoding options in Subtitles panel |
| 31 | Sync panel | 🔨 built (untested) | secondary-sub delay, sub speed, sub FPS, duration factor, "Sync to now" in Sync panel |
| 33 | Network streams | 🔨 built (untested) | RTP/UDP/MMS allowed in StreamValidator and MpvPlayerEngine |
| 44 | Library search | 🔨 built (untested) | LIKE → FTS5 virtual table with rowid triggers and LIKE fallback |
| 46 | Bookmarks | 🔨 built (untested) | thumbnails in Bookmarks panel + timeline markers in VimivoTimeline |
| 50 | Hotkeys | 🔨 built (untested) | presets (Vimivo/VLC/mpv), conflict detection, hotkey switching in View menu |
| 55 | CLI + IPC | 🔨 built (untested) | `--end --volume --speed --sub --audio-track --loop --enqueue --audio-only --help`, JSON IPC socket |
| 57 | Five apps | ⚠️ | keep Windows/Linux CI green; real-hardware checks |
| 59 | Licence notices | 🔨 built (untested) | finish on all platforms (Desktop, Android, iOS) |

### ❓ Not verified (1)
| # | Feature | Status | Notes |
|---|---|---|---|
| 36 | Web URL playback | 🔨 built (untested) | direct HTTP/HLS/DASH/RTSP/RTP/UDP/MMS links enabled in StreamValidator & MpvPlayerEngine |

### ❌ Missing (25)
| # | Feature | Status | Notes |
|---|---|---|---|
| 2b | Blu-ray (folder/ISO/drive) | 🔨 built (untested) | DesktopDiscMedia + LaunchPlanner (BDMV / index.bdmv / STREAM primary m2ts resolution + .iso image support) |
| 10 | 360° video | 🔨 built (untested) | 360° spherical pan directional pad + CCW/CW rotation in VideoPanel & VideoMenu |
| 20 | compressor/spatializer/stereo widener | 🔨 built (untested) | Dynamic range compressor (acompressor), spatializer (surround), stereo widener (extrastereo), mono downmix in Equalizer & DesktopAudioEffects |
| 25 | visualizations (spectrum/scope) | 🔨 built (untested) | Spectrum analyzer and waveform oscilloscope in DesktopAudioVisualizer |
| 26 | S/PDIF/HDMI passthrough | ❌ | *(decided: not built until hardware test)* |
| 32 | OpenSubtitles (opt-in) | 🔨 built (untested) | DesktopSubtitleSearchDialog (search by title/keyword/language, auto-download & apply) |
| 34 | SMB/FTP/SFTP/NFS/DLNA browsing | ❌ | |
| 35 | stream to network | ❌ | |
| 37 | internet radio + podcasts (opt-in) | 🔨 built (untested) | DesktopRadioPodcastDialog (curated high-res community radio + Radio Browser API search + podcast RSS episode stream) |
| 38 | Chromecast/DLNA casting | ❌ | |
| 39 | convert/transcode | 🔨 built (untested) | DesktopTranscodeDialog (lossless audio extraction & media conversion) |
| 40 | record | 🔨 built (untested) | Real-time stream recording / dump to file via mpv stream-record in MpvPlayerEngine, control bar & Tools menu |
| 41 | screen capture | ❌ | |
| 42 | webcam/capture card | ❌ | |
| 48 | themes | ❌ | |
| 49 | customizable toolbar | ❌ | |
| 51 | mouse gestures | 🔨 built (untested) | Mouse wheel volume/seek and middle-click play/pause gestures in Main.kt |
| 52 | mini/compact player + tray/menu-bar | 🔨 built (untested) | DesktopMiniPlayerView with seek bar & tray icon with playback controls |
| 53 | scripts folder | 🔨 built (untested) | DesktopScriptManager & DesktopScriptsDialog for loading user Lua/JS scripts from app support scripts directory |
| 54 | web remote | 🔨 built (untested) | DesktopWebRemoteServer embedded HTTP server (port 8085) with mobile glassmorphism web UI & live controls |
| 56 | command palette | 🔨 built (untested) | DesktopCommandPalette with Cmd+K / Ctrl+K search and quick actions |
| 61 | Wi-Fi file sharing | 🔨 built (untested) | DesktopWifiTransfer local HTTP server and dialog |
| 62 | PiP window | 🔨 built (untested) | DesktopPipOverlay floating compact player mode |
| 63 | audio-only mode for video | 🔨 built (untested) | Audio-only toggle in player control bar & video track disabling with visualizer/artwork stage |
| 65 | network share browsing | ❌ | |

⛔ Excluded by decision: encrypted DVD/Blu-ray, YouTube extraction, telnet, DVD/Audio CD/VCD (licence, deferred).

---

## PART 2 — Mobile vs MX Player (Android / iOS)

| Area | MX Player feature | Android | iOS |
|---|---|---|---|
| **Decoding** | All common formats | ✅ | ⚠️ (no MKV until libmpv fallback) |
| | HW / HW+ / SW decoder switch, per-file memory | 🔨 built (untested) | 🔨 built (untested) |
| | Auto engine fallback that remembers per file | 🔨 built (untested) | 🔨 built (untested) |
| **Playback** | Resume | ✅ | ⚠️ |
| | Auto-play next / loop / shuffle | ❓ | ✅ |
| | Speed 0.25–4× | ✅ | ✅ |
| | A–B repeat | ✅ | ✅ |
| | Sleep timer (shared) | ✅ | ✅ |
| | Background play / play video as audio | ✅ | ❓ |
| | Popup floating player | 🔨 built (untested) | — (PiP) |
| | Picture-in-picture | 🔨 built (untested) | 🔨 built (untested) |
| | Network stream URL | ⚠️ | ⚠️ |
| | Chromecast / AirPlay | ❌ | ❌ |
| | Bookmarks with thumbnails | ❓ | ❓ |
| **Gestures** | Brightness / volume swipe | 🔨 built (untested) | 🔨 built (untested) |
| | Seek swipe with preview | 🔨 built (untested) | 🔨 built (untested) |
| | Double-tap ±N s (configurable, accumulating) | 🔨 built (untested) | 🔨 built (untested) |
| | Long-press 2× | 🔨 built (untested) | 🔨 built (untested) |
| | Pinch zoom + pan | 🔨 built (untested) | 🔨 built (untested) |
| | Subtitle gestures (move, size, line skip) | 🔨 built (untested) | 🔨 built (untested) |
| | Gesture toggles + sensitivity | 🔨 built (untested) | 🔨 built (untested) |
| **Locks** | Screen lock | 🔨 built (untested) | 🔨 built (untested) |
| | Kids lock | 🔨 built (untested) | 🔨 built (untested) |
| **Display** | Aspect modes (Fit, Crop, Stretch, 100%, 16:9, 4:3) | 🔨 built (untested) | ✅ |
| | Orientation modes + rotation lock | 🔨 built (untested) | 🔨 built (untested) |
| | Night mode / dim filter | 🔨 built (untested) | 🔨 built (untested) |
| | Status-bar clock/battery overlay | 🔨 built (untested) | 🔨 built (untested) |
| | Screenshot | ✅ | ✅ |
| | Seek thumbnail preview | 🔨 built (untested) | 🔨 built (untested) |
| | Colour controls | ⚠️ (debug only) | ✅ |
| **Audio** | Audio track switch | ✅ | ✅ |
| | Audio delay | 🔨 built (untested) | 🔨 built (untested) |
| | Volume boost 200% | ✅ | ✅ |
| | 10-band EQ + presets | ✅ | ✅ |
| | Bass boost / virtualizer / reverb | 🔨 built (untested) | 🔨 built (untested) |
| | Stereo / mono / downmix | 🔨 built (untested) | 🔨 built (untested) |
| | Headset single/double/triple press mapping | ❌ | ❌ |
| | Pause on unplug / resume on reconnect | 🔨 built (untested) | 🔨 built (untested) |
| **Subtitles** | Auto-load | ✅ | 🔨 built (untested) |
| | Load from file | 🔨 built (untested) | 🔨 built (untested) |
| | Secondary subtitle | 🔨 built (untested) | 🔨 built (untested) |
| | Delay / speed / FPS | 🔨 built (untested) | 🔨 built (untested) |
| | Full style (font, size, colour, outline, shadow, background, margin) | 🔨 built (untested) | 🔨 built (untested) |
| | Encoding detect, Bangla rendering | 🔨 built (untested) | 🔨 built (untested) |
| **Library** | Folder view / all videos | 🔨 built (untested) | 🔨 built (untested) |
| | List / grid toggle | 🔨 built (untested) | 🔨 built (untested) |
| | Sort options (name/date/size/duration) | 🔨 built (untested) | 🔨 built (untested) |
| | Thumbnails + duration chip + progress bar | 🔨 built (untested) | 🔨 built (untested) |
| | NEW tag + folder NEW count, last-played highlight | 🔨 built (untested) | 🔨 built (untested) |
| | Multi-select (share, delete, rename, move, playlist, properties) | 🔨 built (untested) | 🔨 built (untested) |
| | Rename / delete / properties | 🔨 built (untested) | 🔨 built (untested) |
| | Hide folders, show hidden | 🔨 built (untested) | 🔨 built (untested) |
| | Private folder (PIN/biometric) | 🔨 built (untested) | 🔨 built (untested) |
| | "Play last" floating button | 🔨 built (untested) | 🔨 built (untested) |
| | Search | ✅ | ⚠️ |
| | History | ❓ | ❓ |
| | Playlists (video + audio, M3U) | 🔨 built (untested) | 🔨 built (untested) |
| **Music** | Songs/albums/artists/folders/genres, mini player, Now Playing | 🔨 built (untested) | 🔨 built (untested) |
| | Lyrics (shared LRC) | ✅ | ✅ |
| | Queue drag/remove | 🔨 built (untested) | 🔨 built (untested) |
| | Home-screen widget | ❓ *src* | ❓ *src* |
| | Android Auto / CarPlay | ❓ | ❌ |
| | Set as ringtone | 🔨 built (untested) | — |
| **Tools** | Wi-Fi file transfer (offline, QR) | 🔨 built (untested) | 🔨 built (untested) |
| | Video → MP3 converter | 🔨 built (untested) | 🔨 built (untested) |
| **Settings** | MX-style categories, theme, EN + বাংলা | 🔨 built (untested) | 🔨 built (untested) |
