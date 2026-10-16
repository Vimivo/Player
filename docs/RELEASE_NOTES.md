# Vimivo 1.0.0-rc.1 Release Notes

**Tagline**: *Every Sound. Every Frame.*  
**Release Version**: `1.0.0-rc.1` (Release Candidate 1)  
**Target Platforms**: Android | iOS / iPadOS | macOS | Windows | Linux  
**Licensing**: Apache-2.0 & LGPL-2.1-or-later  

---

## 1. Release Highlights

We are proud to present **Vimivo 1.0.0-rc.1**, a universal, login-free, ad-free, and privacy-first audio and video player built with Kotlin Multiplatform, Compose Multiplatform, and battle-tested native multimedia engines.

### Key Capabilities
- **Universal High-Fidelity Multimedia**:
  - **Audio**: Lossless bit-perfect playback for FLAC, ALAC, WAV, and AIFF up to 24-bit 192kHz; seamless playback for MP3, AAC, M4A, Ogg Vorbis, and Opus.
  - **Video**: Hardware-accelerated decoding for MP4 (H.264, HEVC, AV1), MKV, WebM (VP9, AV1), and MOV.
  - **Subtitles**: SubRip (`.srt`), WebVTT (`.vtt`), and Advanced SubStation Alpha (`.ass` / `.ssa`) with multi-stream switching.
- **Native Platform Multimedia Backends**:
  - **Android**: Google Media3 ExoPlayer with surface-backed `MediaCodec` hardware decoding.
  - **macOS Desktop**: Embedded libmpv 2.0.0 via JNA with Cocoa OpenGL surface and VideoToolbox acceleration.
  - **Windows Desktop**: Embedded libmpv 2.0.0 via JNA with direct Win32 HWND attachment and Direct3D 11 (`d3d11va`) acceleration.
  - **Linux Desktop**: Embedded libmpv 2.0.0 with X11/XWayland surface attachment and VA-API/VDPAU hardware decoding.
  - **iOS / iPadOS**: Native SwiftUI application with Apple `AVFoundation` (`AVPlayer`) and VideoToolbox.
- **Unified Continuous Playback Queue**:
  - Persistent queue saved to SQLite schema v4 across app restarts.
  - Restores paused state without intrusive autoplay.
  - Full queue management: Play All, Shuffle Play, Play Next, Add to Queue, and Drag-and-drop reordering.
- **Personal Media Collections**:
  - Custom playlists with instant reordering and duplicate prevention.
  - One-tap Favorites, playback history with reverse-chronological timestamps, and exact-millisecond bookmarks.
  - Intelligent resume tracking for videos and long audio tracks.
- **Background Playback & OS Media Controls**:
  - Android foreground service with lock screen and notification shade media controls.
  - macOS Control Center & Touch Bar transport controls (`MPRemoteCommandCenter`).
  - Windows System Media Transport Controls (SMTC) and keyboard media keys.
  - Linux MPRIS 2 D-Bus media provider integration.
  - iOS Lock Screen and Control Center `MPNowPlayingInfoCenter` integration.
- **Privacy by Design**:
  - 100% offline-first. Zero analytics SDKs, zero telemetry, zero trackers, zero accounts.
  - Bounded in-memory diagnostic buffer (`PrivacySafeDiagnostics`) with automatic redaction of usernames (`~/user/...`) and URL tokens.
  - Export security: Refuses to write to protected system directories (`/etc`, `/System`, `C:\Windows`).
- **Safe, User-Controlled Software Updates**:
  - Built-in update checking engine (`UpdateChecker`) verifying official HTTPS manifests without automatic background execution.

---

## 2. Verified Artifact Checksums

Every official package is verified against its cryptographic SHA-256 digest:

```text
3a123502d63bcff7cd46e52556ed01eb6048d2c1ed3214cd8160c9fcd2c0cf2b  androidApp-release-unsigned.apk (147,577,927 bytes)
b769278a8ae22b5bbf7681a16e447115288972215c66be600b66c71a4ec0df7f  androidApp-release.aab (76,079,193 bytes)
42e7784c1850fd2d20c94366d4d19c8c25a24b1895ac566af3ddc225b9153cc8  Vimivo-1.0.0.dmg (150,423,483 bytes)
```

---

## 3. Known Limitations & Technical Notices

1. **macOS Gatekeeper**: Unnotarized developer test builds require right-clicking the app and selecting **Open** or running `xattr -d com.apple.quarantine /Applications/Vimivo.app`.
2. **Windows SmartScreen**: Unsigned NSIS and MSI packages will display Windows Defender SmartScreen "Unknown Publisher" until an Authenticode reputation is established. Click **More info -> Run anyway**.
3. **Google Play Publishing**: New personal developer accounts require a 14-day closed testing period with 12 opted-in testers prior to production access.
4. **iOS App Store**: Distribution builds await owner Apple Developer Team enrollment.
