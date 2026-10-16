# Vimivo Audio & Video Capabilities Matrix (Milestones 33 & 34)

> [!NOTE]
> This document establishes the authoritative technical capability matrix for Advanced Audio Engineering and Professional Video Playback across Android, iOS/iPadOS, macOS, Windows, and Linux. Statuses reflect actual native media engine support without fabricated claims.

---

## 1. Advanced Audio Playback Matrix (Milestone 33)

| Audio Capability | Android (Media3) | macOS (libmpv) | Windows (libmpv) | Linux (libmpv) | iOS / iPadOS (AVPlayer) | Native Technical Mechanism |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **10-Band Graphic EQ** | `IMPLEMENTED`<br>Android AudioEffect session EQ | `RUNTIME VERIFIED`<br>10 bands (31Hz–16kHz) via FFmpeg `lavfi` | `AUTOMATED TESTED`<br>10 bands via libmpv `lavfi` filter | `AUTOMATED TESTED`<br>10 bands via libmpv `lavfi` filter | `BLOCKED`<br>AVAudioUnitEQ requires custom audio engine graph | Hardware / FFmpeg audio filter graph |
| **Equalizer Presets** | `IMPLEMENTED`<br>10 presets + Custom | `RUNTIME VERIFIED`<br>10 presets + Custom | `AUTOMATED TESTED`<br>10 presets + Custom | `AUTOMATED TESTED`<br>10 presets + Custom | `NOT IMPLEMENTED` | FLAT, ROCK, POP, CLASSICAL, JAZZ, VOCAL, BASS, TREBLE, ACOUSTIC, CUSTOM |
| **Safety Preamp / Headroom**| `IMPLEMENTED` | `RUNTIME VERIFIED`<br>Auto-attenuating safety preamp | `AUTOMATED TESTED`<br>Auto-attenuating safety preamp | `AUTOMATED TESTED`<br>Auto-attenuating safety preamp | `NOT IMPLEMENTED` | Attenuation only (0 to -12 dB) to eliminate digital clipping |
| **ReplayGain (Track / Album)**| `PARTIAL`<br>Tags extracted | `RUNTIME VERIFIED`<br>Native `replaygain` & clipping control | `AUTOMATED TESTED`<br>Native `replaygain` & clipping control | `AUTOMATED TESTED`<br>Native `replaygain` & clipping control | `NOT IMPLEMENTED`<br>AVPlayer lacks ReplayGain tag processor | Native libmpv `replaygain=track/album` with `replaygain-clip=yes` |
| **Gapless Playback** | `RUNTIME VERIFIED`<br>Media3 audio priming | `RUNTIME VERIFIED`<br>`gapless-audio=yes` | `AUTOMATED TESTED`<br>`gapless-audio=yes` | `AUTOMATED TESTED`<br>`gapless-audio=yes` | `IMPLEMENTED`<br>AVQueuePlayer prebuffering | Decoder priming, encoder delay/padding trimming |
| **Crossfade** | `DISABLED`<br>Requires dual-player mixer | `DISABLED`<br>Disabled on live/video streams | `DISABLED`<br>Disabled on live/video streams | `DISABLED`<br>Disabled on live/video streams | `DISABLED`<br>AVPlayer does not overlap streams | True audio mixing requires dual active decoders; disabled by default to prevent audio popping |
| **Speed (0.25x – 4.0x)** | `RUNTIME VERIFIED`<br>Media3 playback parameters | `RUNTIME VERIFIED`<br>libmpv `speed` | `AUTOMATED TESTED`<br>libmpv `speed` | `AUTOMATED TESTED`<br>libmpv `speed` | `IMPLEMENTED`<br>AVPlayer `rate` | Real-time time-stretching |
| **Pitch Preservation** | `RUNTIME VERIFIED`<br>Sonic audio processor | `RUNTIME VERIFIED`<br>`audio-pitch-correction=yes` | `AUTOMATED TESTED`<br>`audio-pitch-correction=yes` | `AUTOMATED TESTED`<br>`audio-pitch-correction=yes` | `IMPLEMENTED`<br>Time pitch algorithm | Scaletempo / Sonic pitch-invariant time stretch |
| **Audio Output Selection** | `IMPLEMENTED`<br>AudioDeviceInfo / System | `RUNTIME VERIFIED`<br>libmpv `audio-device` | `AUTOMATED TESTED`<br>libmpv `audio-device` | `AUTOMATED TESTED`<br>libmpv `audio-device` | `IMPLEMENTED`<br>AVAudioSession route picker | OS audio routing (Speaker, Headset, Bluetooth, USB DAC, HDMI) |
| **Synchronized Lyrics (LRC)**| `RUNTIME VERIFIED`<br>LrcLyricsParser | `RUNTIME VERIFIED`<br>LrcLyricsParser | `AUTOMATED TESTED`<br>LrcLyricsParser | `AUTOMATED TESTED`<br>LrcLyricsParser | `IMPLEMENTED`<br>LrcLyricsParser | Multi-timestamp parsing, offset adjustment, binary-search active line lookup, Unicode/Bengali |
| **Sleep Timer** | `RUNTIME VERIFIED`<br>SleepTimerController | `RUNTIME VERIFIED`<br>SleepTimer | `AUTOMATED TESTED`<br>SleepTimer | `AUTOMATED TESTED`<br>SleepTimer | `IMPLEMENTED`<br>SleepTimer | Duration countdown (1-1440m), stop-after-current, gentle fade-out |
| **Advanced Queue Actions** | `RUNTIME VERIFIED` | `RUNTIME VERIFIED` | `AUTOMATED TESTED` | `AUTOMATED TESTED` | `IMPLEMENTED` | Play Next, Add to Queue, Reorder, Paused restoration, Duplicate occurrence stability |

---

## 2. Professional Video Playback Matrix (Milestone 34)

| Video Capability | Android (Media3) | macOS (libmpv) | Windows (libmpv) | Linux (libmpv) | iOS / iPadOS (AVPlayer) | Native Technical Mechanism |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **A-B Repeat Looping** | `IMPLEMENTED`<br>Seek on loop boundary | `RUNTIME VERIFIED`<br>Native `ab-loop-a` / `ab-loop-b` | `AUTOMATED TESTED`<br>Native `ab-loop-a` / `ab-loop-b` | `AUTOMATED TESTED`<br>Native `ab-loop-a` / `ab-loop-b` | `IMPLEMENTED`<br>Periodic boundary seek | Native decoder boundary looping |
| **Chapter Navigation** | `IMPLEMENTED`<br>Container chapters | `RUNTIME VERIFIED`<br>libmpv `chapter-list` | `AUTOMATED TESTED`<br>libmpv `chapter-list` | `AUTOMATED TESTED`<br>libmpv `chapter-list` | `IMPLEMENTED`<br>Timed metadata chapters | MP4 / MKV chapter atoms / metadata parsing |
| **External Subtitles** | `RUNTIME VERIFIED`<br>SRT, VTT document URIs | `RUNTIME VERIFIED`<br>SRT, ASS/SSA, VTT, SUB | `AUTOMATED TESTED`<br>SRT, ASS/SSA, VTT, SUB | `AUTOMATED TESTED`<br>SRT, ASS/SSA, VTT, SUB | `IMPLEMENTED`<br>VTT / SRT file importing | Native subtitle demuxer & vector renderer |
| **Subtitle Styling** | `IMPLEMENTED`<br>CaptionStyleCompat | `RUNTIME VERIFIED`<br>`sub-font-size`, `sub-color` | `AUTOMATED TESTED`<br>`sub-font-size`, `sub-color` | `AUTOMATED TESTED`<br>`sub-font-size`, `sub-color` | `IMPLEMENTED`<br>System subtitle preferences | Font size, textColor, margin/positioning |
| **Subtitle Delay** | `IMPLEMENTED` | `RUNTIME VERIFIED`<br>libmpv `sub-delay` | `AUTOMATED TESTED`<br>libmpv `sub-delay` | `AUTOMATED TESTED`<br>libmpv `sub-delay` | `IMPLEMENTED` | Real-time millisecond offset |
| **Audio/Video Sync** | `IMPLEMENTED` | `RUNTIME VERIFIED`<br>libmpv `audio-delay` | `AUTOMATED TESTED`<br>libmpv `audio-delay` | `AUTOMATED TESTED`<br>libmpv `audio-delay` | `IMPLEMENTED` | Audio clock offset in milliseconds |
| **Aspect Ratio Modes** | `RUNTIME VERIFIED`<br>ResizeMode | `RUNTIME VERIFIED`<br>`video-aspect-override` | `AUTOMATED TESTED`<br>`video-aspect-override` | `AUTOMATED TESTED`<br>`video-aspect-override` | `IMPLEMENTED`<br>AVLayerVideoGravity | Original, Fit, Fill, 16:9, 4:3, 21:9 |
| **Video Zoom & Pan** | `PARTIAL`<br>Gesture zoom | `RUNTIME VERIFIED`<br>`video-zoom`, `video-pan-x/y` | `AUTOMATED TESTED`<br>`video-zoom`, `video-pan-x/y` | `AUTOMATED TESTED`<br>`video-zoom`, `video-pan-x/y` | `PARTIAL`<br>Pinch-to-zoom | Hardware rendering transform |
| **Video Rotation** | `IMPLEMENTED` | `RUNTIME VERIFIED`<br>`video-rotate` (0, 90, 180, 270) | `AUTOMATED TESTED`<br>`video-rotate` (0, 90, 180, 270) | `AUTOMATED TESTED`<br>`video-rotate` (0, 90, 180, 270) | `IMPLEMENTED` | Orientation matrix transform |
| **Video Adjustments** | `NOT IMPLEMENTED`<br>No GL shader graph | `RUNTIME VERIFIED`<br>Brightness, Contrast, Saturation, Gamma | `AUTOMATED TESTED`<br>Brightness, Contrast, Saturation, Gamma | `AUTOMATED TESTED`<br>Brightness, Contrast, Saturation, Gamma | `NOT IMPLEMENTED`<br>Requires CoreImage filter graph | GPU hardware color adjustments |
| **Video Screenshots** | `IMPLEMENTED`<br>PixelCopy API | `RUNTIME VERIFIED`<br>`screenshot-to-file` | `AUTOMATED TESTED`<br>`screenshot-to-file` | `AUTOMATED TESTED`<br>`screenshot-to-file` | `IMPLEMENTED`<br>AVAssetImageGenerator | Clean video frame capture without OSD text |
| **HDR & Color Handling**| `RUNTIME VERIFIED`<br>HDR10 display pipeline | `RUNTIME VERIFIED`<br>libmpv tone-mapping on Apple Silicon | `AUTOMATED TESTED`<br>libmpv tone-mapping | `AUTOMATED TESTED`<br>libmpv tone-mapping | `IMPLEMENTED`<br>Apple EDR / HDR10 | HDR10 / HLG playback with SDR tone-mapping fallback |
| **Playback Profiles** | `IMPLEMENTED` | `RUNTIME VERIFIED` | `AUTOMATED TESTED` | `AUTOMATED TESTED` | `IMPLEMENTED` | Default, Music Focus, Cinema, Podcast/Lecture |
| **Playlist Import / Export**| `IMPLEMENTED` | `RUNTIME VERIFIED`<br>M3U / M3U8 | `AUTOMATED TESTED`<br>M3U / M3U8 | `AUTOMATED TESTED`<br>M3U / M3U8 | `IMPLEMENTED`<br>M3U / M3U8 | Standard M3U / M3U8 with #EXTINF parsing and export |
