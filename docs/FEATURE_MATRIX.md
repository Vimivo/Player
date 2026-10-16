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

### Milestones 33 & 34 Highlights:
- **Advanced Audio Engineering**: 10-band equalizer with 10 presets (Flat, Bass Boost, Treble Boost, Vocal, Classical, Rock, Jazz, Pop, Acoustic, Custom) and auto-attenuating safety preamp headroom (0 to -12 dB). Native ReplayGain (Track & Album mode) with peak clipping protection. Verified gapless playback transitions (`gapless-audio=yes`) and pitch-invariant real-time time-stretching (`audio-pitch-correction=yes`). Safe cross-platform output device routing.
- **Synchronized LRC Lyrics & Sleep Timer**: Millisecond-accurate multi-timestamp LRC parser with offset adjustment, Unicode/Bengali script support, and binary search active line indexing. Platform-neutral sleep timer with duration countdown (1-1440m), stop-after-current-track, and gentle volume fade-out.
- **Professional Video Controls**: Native A-B repeat looping, MKV/MP4 container chapter discovery and stepping, external subtitle demuxing with real-time delay (±5000ms) and appearance customization (size, color, margin). A/V synchronization clock offset. Multi-mode aspect ratio overrides (Fit, Fill, 16:9, 4:3, 21:9), hardware zoom/pan, 90-degree step rotation, and real-time GPU video adjustments (brightness, contrast, saturation, gamma). Native frame screenshot capture to user-designated destination.
- **Playback Profiles & Playlist Portability**: Default, Music Focus, Cinema, and Podcast/Lecture profiles adapting playback speed, aspect ratio, EQ, and ReplayGain. Robust non-destructive M3U/M3U8 import/export with `#EXTINF` metadata parsing and HLS stream manifest filtering.
- **Capability Architecture**: Strict zero-fake-controls policy; platforms without verified DSP or shader graphs (e.g., iOS EQ or Android GPU video filters) explicitly reflect their actual capability state without misleading user toggles.

