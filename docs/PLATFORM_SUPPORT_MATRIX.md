# Vimivo — Cross-Platform Support Matrix

This document defines the official support baseline, operating system requirements, supported CPU architectures, native multimedia playback engines, and packaging models for Vimivo across all target platforms.

---

## 1. Operating System & Hardware Baseline

| Platform | Minimum OS | Recommended OS | Supported Architectures | Native Media Engine | Package Formats | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Android** | Android 8.0 (API 26) | Android 14–16 (API 34–36) | `arm64-v8a`, `armeabi-v7a`, `x86_64` | AndroidX Media3 1.11.1 / ExoPlayer | APK, AAB | **IMPLEMENTED / AUTOMATED TESTED** |
| **macOS** | macOS 12 Monterey | macOS 14 Sonoma / 15 Sequoia | `arm64` (Apple Silicon), `x86_64` (Intel) | Native libmpv (v0.38+) via JNA | DMG, APP Bundle | **RUNTIME VERIFIED** |
| **Windows** | Windows 10 (1809+) | Windows 11 (22H2+) | `x64` | Native libmpv (v0.38+) via JNA | MSI, Portable ZIP | **COMPILED / AUTOMATED TESTED** |
| **Linux** | Ubuntu 22.04 LTS / glibc 2.35+ | Ubuntu 24.04 LTS / Fedora 40+ | `x86_64` | System libmpv (v0.35+) via JNA | DEB, RPM, AppImage | **COMPILED / AUTOMATED TESTED** |
| **iOS / iPadOS** | iOS 16.0 / iPadOS 16.0 | iOS 17.0–18.0 | `arm64` (Devices), `arm64`/`x86_64` (Simulator) | AVFoundation / AVPlayer | IPA, TestFlight | **PARTIAL / ARCHITECTURE DESIGNED** |

---

## 2. Platform Capability Breakdown

### 2.1 Android (Media3 / ExoPlayer)
- **Status**: IMPLEMENTED & AUTOMATED TESTED (352 passed unit/integration tests).
- **Audio Codecs**: MP3, AAC, FLAC, Opus, Vorbis, WAV, Ogg.
- **Video Codecs**: H.264 (AVC), H.265 (HEVC), VP9, AV1 (on hardware-supported decoders).
- **Protocols**: Local media files, HTTP/HTTPS direct URLs, HLS (RFC 8216), DASH (ISO/IEC 23009-1), RTSP (RFC 2326/7826).
- **Platform Features**: MediaSessionService, lock-screen playback controls, audio focus handling, Picture-in-Picture, scoped storage via MediaStore / Storage Access Framework.
- **Limitations**: Background video playback requires PiP or audio-only mode.

### 2.2 macOS (Desktop libmpv)
- **Status**: RUNTIME VERIFIED (Verified on macOS host environment).
- **Audio Codecs**: Bit-perfect playback for MP3, AAC, ALAC, FLAC, Opus, Vorbis, WAV, AIFF, DSD/DSF.
- **Video Codecs**: H.264, HEVC, VP8, VP9, AV1, ProRes with hardware acceleration (`videotoolbox`).
- **Protocols**: Local files, HTTP/HTTPS direct URLs, HLS, DASH, RTSP, Icecast/Shoutcast internet radio.
- **Platform Features**: Retina rendering, borderless/fullscreen windowing, Media keys via macOS remote commands, JNA dynamic bridge with fallback detection.
- **Packaging**: Self-contained application bundle (`.app`) with bundled JDK 17 runtime image and universal native dylibs.

### 2.3 Windows (Desktop libmpv)
- **Status**: COMPILED & AUTOMATED TESTED.
- **Audio Codecs**: MP3, AAC, FLAC, Opus, Vorbis, WAV, WMA.
- **Video Codecs**: H.264, HEVC, VP9, AV1 with hardware acceleration (`d3d11va`, `nvdec`).
- **Protocols**: Local files, HTTP/HTTPS direct URLs, HLS, DASH, RTSP.
- **Platform Features**: Per-monitor DPI scaling, Windows Media Control keys, safe UNC and long-path handling, SQLite WAL database under `%LOCALAPPDATA%\Vimivo`.

### 2.4 Linux (Desktop libmpv)
- **Status**: COMPILED & AUTOMATED TESTED.
- **Audio Codecs**: MP3, AAC, FLAC, Opus, Vorbis, WAV.
- **Video Codecs**: H.264, HEVC, VP8, VP9, AV1 via VA-API / VDPAU.
- **Protocols**: Local files, HTTP/HTTPS direct URLs, HLS, DASH, RTSP.
- **Platform Features**: MPRIS v2 D-Bus interface for desktop media integration, X11 and Wayland (via XWayland) display support, XDG base directory specification (`~/.local/share/vimivo`, `~/.config/vimivo`).

### 2.5 iOS / iPadOS (AVFoundation)
- **Status**: PARTIAL / ARCHITECTURE DESIGNED.
- **Engine**: Apple AVFoundation (`AVPlayer`, `AVPlayerItem`, `AVURLAsset`).
- **Policy**: Per Vimivo project rules (AGENTS.md), iOS applications are kept in architectural readiness and not published until requested.

---

## 3. Product Boundary Invariants

Vimivo maintains strict boundaries across all operating systems:
1. **Offline-First**: All local audio and video playback, playlists, history, and search function 100% offline without requiring internet access.
2. **Zero Telemetry**: No background telemetry, crash uploaders, or user tracking are built into any platform build.
3. **No Mandatory Accounts**: No login screens, cloud subscriptions, or online catalogs.
4. **Update Decoupling**: Update checks are strictly optional, non-blocking, and never impede local playback.
